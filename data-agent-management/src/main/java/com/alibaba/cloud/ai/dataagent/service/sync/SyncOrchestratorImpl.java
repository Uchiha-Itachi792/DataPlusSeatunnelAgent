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

import com.alibaba.cloud.ai.dataagent.dto.syncjob.MockSyncLayerResult;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveRequest;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveResult;
import com.alibaba.cloud.ai.dataagent.enums.SyncMode;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.CompiledJobConfig;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.SingleStepCompilerRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * M0 同步编排：Mock L1～L4 → 校验 → 汇编 → 编译 conf。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SyncOrchestratorImpl implements SyncOrchestrator {

	private final SyncModeRouter syncModeRouter;

	private final MockSyncLayerResolver mockSyncLayerResolver;

	private final SyncPlanAssembler syncPlanAssembler;

	private final SingleStepCompilerRegistry singleStepCompilerRegistry;

	private final SeatunnelProperties seatunnelProperties;

	@Override
	public SyncResolveResult resolve(SyncResolveRequest request) {
		try {
			if (syncModeRouter.route(request) != SyncMode.SINGLE) {
				return SyncResolveResult.error("M0 仅支持单步同步");
			}

			MockSyncLayerResult layers = mockSyncLayerResolver.resolve(request);
			SyncPlanAssembler.AssemblyResult assembly = syncPlanAssembler.assemble(request.getAgentId(), layers);
			if (assembly.needsClarify()) {
				return SyncResolveResult.clarify(assembly.clarifyQuestions());
			}

			if (!seatunnelProperties.isSyncKindEnabled(assembly.plan().getSyncKind().name())) {
				return SyncResolveResult.error("当前配置未启用同步类型：" + assembly.plan().getSyncKind());
			}

			CompiledJobConfig compiled = singleStepCompilerRegistry.compile(assembly.plan());
			log.info("Compiled sync plan for agent {}: {} -> {}", request.getAgentId(), compiled.getSourceTable(),
					compiled.getTargetTable());
			return SyncResolveResult.success(assembly.plan(), compiled.getJobConfig(), assembly.trace());
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
		return SyncResolveResult.error("多轮澄清续跑将在 M1 实现");
	}

}
