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
package com.alibaba.cloud.ai.dataagent.service.sync.resolve;

import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.DataPointer;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmObjectResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSystemResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveRequest;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.enums.ResolvePhase;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.SeatunnelSchemaRecallService;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import com.alibaba.cloud.ai.dataagent.service.sync.fastpath.FastPathDetector;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
/**
 * L2 对象定位：Fast Path 规则表名，或 Schema 召回/勾选表候选 + LLM。
 */
@Slf4j
@Service
@AllArgsConstructor
public class ObjectResolveService {

	private final SyncCatalogService syncCatalogService;

	private final SeatunnelSchemaRecallService seatunnelSchemaRecallService;

	private final AgentDatasourceService agentDatasourceService;

	private final FastPathDetector fastPathDetector;

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	public LlmObjectResolve resolve(SyncResolveRequest request, LlmSystemResolve system) {
		String sourceRef = system.getSourceRef();
		String targetRef = system.getTargetRef();
		String query = resolveQuery(request);

		// 表名清晰时规则直出（即使含复杂语义，也先定表，由 L3 决定是否 clarify）
		Optional<String[]> ruleTables = fastPathDetector.parseClearTableNames(query);
		if (ruleTables.isPresent()) {
			String sourceTable = ruleTables.get()[0];
			String sinkTable = ruleTables.get()[1];
			if (syncCatalogService.objectExists(request.getAgentId(), sourceRef, sourceTable)
					&& syncCatalogService.objectExists(request.getAgentId(), targetRef, sinkTable)) {
				return buildObject(sourceRef, targetRef, sourceTable, sinkTable);
			}
		}

		String candidates = buildObjectCandidates(request.getAgentId(), sourceRef, targetRef, query);
		if (!StringUtils.hasText(candidates)) {
			return clarify("未找到可用表候选。请先在 Agent 中勾选表并初始化 Schema，或直接给出源表与目标表物理名。");
		}

		String prompt = PromptHelper.buildSyncL2ObjectPrompt(request.getMultiTurn(), query, sourceRef, targetRef,
				candidates);
		String llmOutput = llmService.blockToString(llmService.callUser(prompt));
		if (!StringUtils.hasText(llmOutput)) {
			return clarify("无法解析源表与目标表，请说明物理表名（例如 order_items 到 order_items_back）。");
		}
		LlmObjectResolve resolved = jsonParseUtil.tryConvertToObject(llmOutput, LlmObjectResolve.class);
		if (resolved == null) {
			return clarify("对象定位结果解析失败，请说明源表与目标表。");
		}
		if (!StringUtils.hasText(resolved.getPhase())) {
			resolved.setPhase(ResolvePhase.OBJECT.name());
		}
		if (resolved.isNeedClarify()) {
			return resolved;
		}
		if (resolved.getSource() == null || !StringUtils.hasText(resolved.getSource().getObject())
				|| resolved.getSink() == null || !StringUtils.hasText(resolved.getSink().getObject())) {
			return clarify("请说明源表和目标表的物理表名。");
		}
		if (!StringUtils.hasText(resolved.getSource().getRef())) {
			resolved.getSource().setRef(sourceRef);
		}
		if (!StringUtils.hasText(resolved.getSink().getRef())) {
			resolved.getSink().setRef(targetRef);
		}
		if (resolved.getOthers() == null) {
			resolved.setOthers(new ArrayList<>());
		}
		return resolved;
	}

	private String buildObjectCandidates(Long agentId, String sourceRef, String targetRef, String query) {
		Set<String> lines = new LinkedHashSet<>();
		addRecallCandidates(agentId, sourceRef, query, lines);
		if (!sourceRef.equals(targetRef)) {
			addRecallCandidates(agentId, targetRef, query, lines);
		}
		if (lines.isEmpty()) {
			addSelectTableCandidates(agentId, sourceRef, lines);
			if (!sourceRef.equals(targetRef)) {
				addSelectTableCandidates(agentId, targetRef, lines);
			}
		}
		return String.join("\n", lines);
	}

	private void addRecallCandidates(Long agentId, String ref, String query, Set<String> lines) {
		Optional<Integer> datasourceId = syncCatalogService.resolveDatasourceId(ref);
		if (datasourceId.isEmpty()) {
			return;
		}
		try {
			SeatunnelSchemaRecallResult recall = seatunnelSchemaRecallService.recall(datasourceId.get(), agentId,
					query);
			if (recall == null || recall.getSchemaDTO() == null
					|| CollectionUtils.isEmpty(recall.getSchemaDTO().getTable())) {
				return;
			}
			for (TableDTO table : recall.getSchemaDTO().getTable()) {
				String desc = StringUtils.hasText(table.getDescription()) ? table.getDescription() : "";
				lines.add(table.getName() + (StringUtils.hasText(desc) ? "（" + desc + "）" : "") + " [ref=" + ref + "]");
			}
		}
		catch (Exception ex) {
			log.debug("Schema recall failed for ref {}: {}", ref, ex.getMessage());
		}
	}

	private void addSelectTableCandidates(Long agentId, String ref, Set<String> lines) {
		Optional<Integer> datasourceId = syncCatalogService.resolveDatasourceId(ref);
		if (datasourceId.isEmpty()) {
			return;
		}
		List<AgentDatasource> bindings = agentDatasourceService.getAgentDatasource(agentId);
		for (AgentDatasource binding : bindings) {
			if (!datasourceId.get().equals(binding.getDatasourceId())) {
				continue;
			}
			List<String> tables = binding.getSelectTables();
			if (CollectionUtils.isEmpty(tables)) {
				return;
			}
			for (String table : tables) {
				lines.add(table + " [ref=" + ref + "]");
			}
			return;
		}
	}

	private static LlmObjectResolve buildObject(String sourceRef, String targetRef, String sourceTable,
			String sinkTable) {
		return LlmObjectResolve.builder()
			.phase(ResolvePhase.OBJECT.name())
			.source(DataPointer.builder().ref(sourceRef).object(sourceTable).build())
			.sink(DataPointer.builder().ref(targetRef).object(sinkTable).build())
			.others(new ArrayList<>())
			.build();
	}

	private static LlmObjectResolve clarify(String question) {
		return LlmObjectResolve.builder()
			.phase(ResolvePhase.OBJECT.name())
			.needClarify(true)
			.clarifyQuestions(List.of(question))
			.others(new ArrayList<>())
			.build();
	}

	private static String resolveQuery(SyncResolveRequest request) {
		return StringUtils.hasText(request.getCanonicalQuery()) ? request.getCanonicalQuery() : request.getUserInput();
	}

}
