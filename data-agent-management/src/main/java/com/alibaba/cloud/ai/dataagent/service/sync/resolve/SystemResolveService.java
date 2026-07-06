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

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSystemResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveRequest;
import com.alibaba.cloud.ai.dataagent.enums.ResolvePhase;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogEntry;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * L1 系统定位：单 ref 规则直出，多 ref 调 LLM。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SystemResolveService {

	private final SyncCatalogService syncCatalogService;

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	public LlmSystemResolve resolve(SyncResolveRequest request) {
		List<SyncCatalogEntry> refs = syncCatalogService.listRefs(request.getAgentId());
		if (refs == null || refs.isEmpty()) {
			return LlmSystemResolve.builder()
				.phase(ResolvePhase.SYSTEM.name())
				.needClarify(true)
				.clarifyQuestions(List.of("当前 Agent 未绑定任何数据源，请先在管理台授权数据源。"))
				.build();
		}
		if (refs.size() == 1) {
			String ref = refs.get(0).getRef();
			return LlmSystemResolve.builder()
				.phase(ResolvePhase.SYSTEM.name())
				.sourceRef(ref)
				.targetRef(ref)
				.build();
		}

		String catalogSummary = refs.stream()
			.map(e -> e.getRef() + " " + e.getConnectorType() + " " + e.getDescription())
			.collect(Collectors.joining("\n"));
		String query = resolveQuery(request);
		String prompt = PromptHelper.buildSyncL1CatalogPrompt(request.getMultiTurn(), query, catalogSummary);
		String llmOutput = llmService.blockToString(llmService.callUser(prompt));
		if (!StringUtils.hasText(llmOutput)) {
			return clarify("无法解析源/目标数据源，请明确要从哪个库同步到哪个库。");
		}
		LlmSystemResolve resolved = jsonParseUtil.tryConvertToObject(llmOutput, LlmSystemResolve.class);
		if (resolved == null) {
			return clarify("系统定位结果解析失败，请说明源数据源与目标数据源。");
		}
		if (!StringUtils.hasText(resolved.getPhase())) {
			resolved.setPhase(ResolvePhase.SYSTEM.name());
		}
		if (resolved.isNeedClarify()) {
			return resolved;
		}
		if (!StringUtils.hasText(resolved.getSourceRef()) || !StringUtils.hasText(resolved.getTargetRef())) {
			return clarify("请说明源数据源与目标数据源的 ref。");
		}
		return resolved;
	}

	private static LlmSystemResolve clarify(String question) {
		return LlmSystemResolve.builder()
			.phase(ResolvePhase.SYSTEM.name())
			.needClarify(true)
			.clarifyQuestions(List.of(question))
			.build();
	}

	private static String resolveQuery(SyncResolveRequest request) {
		return StringUtils.hasText(request.getCanonicalQuery()) ? request.getCanonicalQuery() : request.getUserInput();
	}

}
