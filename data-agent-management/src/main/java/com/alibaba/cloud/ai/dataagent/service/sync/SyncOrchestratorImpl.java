/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.sync;

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmObjectResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncIntent;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncSql;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSystemResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.MockSyncLayerResult;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveRequest;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveResult;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveState;
import com.alibaba.cloud.ai.dataagent.enums.ResolvePhase;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.enums.SyncMode;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.CompiledJobConfig;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.SingleStepCompilerRegistry;
import com.alibaba.cloud.ai.dataagent.service.sync.fastpath.FastPathDetector;
import com.alibaba.cloud.ai.dataagent.service.sync.resolve.ObjectResolveService;
import com.alibaba.cloud.ai.dataagent.service.sync.resolve.SyncIntentService;
import com.alibaba.cloud.ai.dataagent.service.sync.resolve.SyncSqlService;
import com.alibaba.cloud.ai.dataagent.service.sync.resolve.SystemResolveService;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.ObjectResolveValidator;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SystemResolveValidator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * M1 同步编排：L1～L4 真解析 + Fast Path + 澄清续跑。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SyncOrchestratorImpl implements SyncOrchestrator {

	private final SyncModeRouter syncModeRouter;

	private final SystemResolveService systemResolveService;

	private final ObjectResolveService objectResolveService;

	private final SyncIntentService syncIntentService;

	private final SyncSqlService syncSqlService;

	private final FastPathDetector fastPathDetector;

	private final SystemResolveValidator systemResolveValidator;

	private final ObjectResolveValidator objectResolveValidator;

	private final SyncPlanAssembler syncPlanAssembler;

	private final SingleStepCompilerRegistry singleStepCompilerRegistry;

	private final SeatunnelProperties seatunnelProperties;

	private final SyncResolveStateStore syncResolveStateStore;

	@Override
	public SyncResolveResult resolve(SyncResolveRequest request) {
		try {
			if (syncModeRouter.route(request) != SyncMode.SINGLE) {
				return SyncResolveResult.error("M1 仅支持单步同步");
			}
			return runFromPhase(request, ResolvePhase.SYSTEM, MockSyncLayerResult.builder().build(), false);
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			log.warn("Sync resolve failed for agent {}: {}", request.getAgentId(), ex.getMessage());
			return SyncResolveResult.error(ex.getMessage());
		}
		catch (Exception ex) {
			log.error("Unexpected sync resolve error for agent {}", request.getAgentId(), ex);
			return SyncResolveResult.error("同步解析失败：" + ex.getMessage());
		}
	}

	@Override
	public SyncResolveResult resume(SyncResolveRequest request, String userAnswer) {
		try {
			Optional<SyncResolveState> pending = syncResolveStateStore.get(request.getThreadId());
			if (pending.isEmpty()) {
				return resolve(request);
			}
			SyncResolveState state = pending.get();
			String mergedQuery = mergeQuery(state.getOriginalQuery(), userAnswer);
			String multiTurn = appendMultiTurn(state.getMultiTurn(), state.getOriginalQuery(), userAnswer);
			SyncResolveRequest resumed = SyncResolveRequest.builder()
				.agentId(request.getAgentId() != null ? request.getAgentId() : state.getAgentId())
				.userInput(userAnswer)
				.canonicalQuery(mergedQuery)
				.multiTurn(multiTurn)
				.threadId(request.getThreadId())
				.build();
			MockSyncLayerResult layers = state.getLayers() != null ? state.getLayers()
					: MockSyncLayerResult.builder().build();
			return runFromPhase(resumed, state.getPendingPhase(), layers, true);
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			log.warn("Sync resume failed for agent {}: {}", request.getAgentId(), ex.getMessage());
			return SyncResolveResult.error(ex.getMessage());
		}
		catch (Exception ex) {
			log.error("Unexpected sync resume error for agent {}", request.getAgentId(), ex);
			return SyncResolveResult.error("同步续跑失败：" + ex.getMessage());
		}
	}

	/**
	 * L1～L4 渐进式解析主流程：从 {@code startPhase} 起逐层执行，已完成的层在续跑时跳过。
	 * <p>
	 * 任一层 needClarify 或校验失败则保存状态并返回追问，不编译 conf；全部通过后 assemble → compile。
	 *
	 * @param request 同步解析请求（含 userInput、canonicalQuery、threadId）
	 * @param startPhase 起始阶段（首次 resolve 为 SYSTEM；resume 为 pendingPhase）
	 * @param layers 已解析层快照，续跑时复用避免重算
	 * @param fromResume 是否来自多轮澄清续跑（仅日志）
	 * @return SUCCESS（plan + conf）、CLARIFY（追问）或 ERROR
	 */
	private SyncResolveResult runFromPhase(SyncResolveRequest request, ResolvePhase startPhase,
			MockSyncLayerResult layers, boolean fromResume) {
		LlmSystemResolve system = layers.getSystem();
		LlmObjectResolve object = layers.getObject();
		LlmSyncIntent intent = layers.getIntent();
		LlmSyncSql sql = layers.getSql();

		// L1 系统定位：确定 sourceRef / targetRef（哪个数据源读、写到哪个）
		if (ResolvePhase.SYSTEM.shouldRunFrom(startPhase)) {
			system = systemResolveService.resolve(request);
			layers.setSystem(system);
			if (system.isNeedClarify()) {
				return clarifyAndStore(request, ResolvePhase.SYSTEM, layers, system.getClarifyQuestions());
			}
			try {
				systemResolveValidator.validate(request.getAgentId(), system);
			}
			catch (IllegalArgumentException ex) {
				return clarifyAndStore(request, ResolvePhase.SYSTEM, layers, List.of(ex.getMessage()));
			}
		}

		// L2 对象定位：确定 source / sink 表（依赖 L1 的 ref）
		if (ResolvePhase.OBJECT.shouldRunFrom(startPhase)) {
			object = objectResolveService.resolve(request, system);
			layers.setObject(object);
			if (object.isNeedClarify()) {
				return clarifyAndStore(request, ResolvePhase.OBJECT, layers, object.getClarifyQuestions());
			}
			try {
				objectResolveValidator.validate(request.getAgentId(), object);
			}
			catch (IllegalArgumentException ex) {
				return clarifyAndStore(request, ResolvePhase.OBJECT, layers, List.of(ex.getMessage()));
			}
		}

		// Fast Path：句式标准且无复杂语义时，L3 可规则直出 TABLE_COPY（跳过 L3 LLM）
 		String query = StringUtils.hasText(request.getCanonicalQuery()) ? request.getCanonicalQuery()
				: request.getUserInput();
		boolean fastPath = fastPathDetector.isEnabled() && !fastPathDetector.hasComplexSemantics(query)
				&& fastPathDetector.parseClearTableNames(query).isPresent();

		// L3 同步意图：判定 syncKind（M1 仅 TABLE_COPY）；含过滤/JOIN 等则 needClarify
		if (ResolvePhase.INTENT.shouldRunFrom(startPhase)) {
			intent = syncIntentService.resolve(request, object, fastPath);
			layers.setIntent(intent);
			if (intent.isNeedClarify()) {
				return clarifyAndStore(request, ResolvePhase.INTENT, layers, intent.getClarifyQuestions());
			}
		}

		// L4 SQL：TABLE_COPY 时 sql 为空，由 Compiler 补 SELECT *；M2+ 复杂 syncKind 才调 LLM 写 SQL
		if (ResolvePhase.SQL.shouldRunFrom(startPhase)) {
			sql = syncSqlService.resolve(intent.getSyncKind() != null ? intent.getSyncKind() : SyncKind.TABLE_COPY);
			layers.setSql(sql);
			if (sql.isNeedClarify()) {
				return clarifyAndStore(request, ResolvePhase.SQL, layers, sql.getClarifyQuestions());
			}
		}

		// 四层结果汇总为 sync_plan + resolve_trace
		SyncPlanAssembler.AssemblyResult assembly = syncPlanAssembler.assemble(request.getAgentId(), layers);
		if (assembly.needsClarify()) {
			return clarifyAndStore(request, ResolvePhase.SQL, layers, assembly.clarifyQuestions());
		}

		if (!seatunnelProperties.isSyncKindEnabled(assembly.plan().getSyncKind().name())) {
			return SyncResolveResult.error("当前配置未启用同步类型：" + assembly.plan().getSyncKind());
		}

		// 编译 SeaTunnel HOCON；成功后清除澄清态，供审批页落库
		CompiledJobConfig compiled = singleStepCompilerRegistry.compile(assembly.plan());
		log.info("Compiled sync plan for agent {}: {} -> {} (resume={})", request.getAgentId(),
				compiled.getSourceTable(), compiled.getTargetTable(), fromResume);
		if (StringUtils.hasText(request.getThreadId())) {
			syncResolveStateStore.remove(request.getThreadId());
		}
		return SyncResolveResult.success(assembly.plan(), compiled.getJobConfig(), assembly.trace());
	}

	private SyncResolveResult clarifyAndStore(SyncResolveRequest request, ResolvePhase phase,
			MockSyncLayerResult layers, List<String> questions) {
		List<String> qs = questions != null ? questions : List.of("请补充同步任务信息。");
		if (StringUtils.hasText(request.getThreadId())) {
			syncResolveStateStore.save(SyncResolveState.builder()
				.threadId(request.getThreadId())
				.agentId(request.getAgentId())
				.pendingPhase(phase)
				.originalQuery(StringUtils.hasText(request.getCanonicalQuery()) ? request.getCanonicalQuery()
						: request.getUserInput())
				.canonicalQuery(request.getCanonicalQuery())
				.multiTurn(request.getMultiTurn())
				.layers(copyLayers(layers))
				.build());
		}
		return SyncResolveResult.clarify(qs);
	}

	private static MockSyncLayerResult copyLayers(MockSyncLayerResult layers) {
		return MockSyncLayerResult.builder()
			.system(layers.getSystem())
			.object(layers.getObject())
			.intent(layers.getIntent())
			.sql(layers.getSql())
			.build();
	}

	private static String mergeQuery(String original, String answer) {
		if (!StringUtils.hasText(original)) {
			return answer;
		}
		if (!StringUtils.hasText(answer)) {
			return original;
		}
		return original + " " + answer;
	}

	private static String appendMultiTurn(String multiTurn, String previous, String answer) {
		String base = StringUtils.hasText(multiTurn) ? multiTurn : "(无)";
		return base + "\n用户补充: " + (answer != null ? answer : "") + "\n原需求: " + (previous != null ? previous : "");
	}

}
