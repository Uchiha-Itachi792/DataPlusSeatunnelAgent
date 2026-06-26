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

import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncTableResolveDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于召回 Schema 与用户输入，由 LLM 决策同步任务的物理表名。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SyncTableResolveService {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	/**
	 * 解析源表、目标表及关联表的物理表名。
	 * @param userInput 用户同步需求
	 * @param multiTurn 多轮上下文
	 * @param schemaDTO 召回后的 Schema
	 * @return 解析结果；字段缺失或 LLM 无输出时返回 null
	 */
	public SyncTableResolveDTO resolve(String userInput, String multiTurn, SchemaDTO schemaDTO) {
		if (schemaDTO == null || schemaDTO.getTable() == null || schemaDTO.getTable().isEmpty()) {
			return null;
		}
		String prompt = PromptHelper.buildSyncTableResolvePrompt(multiTurn, userInput, schemaDTO);
		String llmOutput = llmService.blockToString(llmService.callUser(prompt));
		if (!StringUtils.hasText(llmOutput)) {
			return null;
		}
		SyncTableResolveDTO resolved = jsonParseUtil.tryConvertToObject(llmOutput, SyncTableResolveDTO.class);
		if (resolved == null || !StringUtils.hasText(resolved.getSourceTable())
				|| !StringUtils.hasText(resolved.getTargetTable())) {
			log.warn("Sync table resolve incomplete for input: {}", userInput);
			return null;
		}
		log.info("Resolved sync tables: source={}, target={}, related={}", resolved.getSourceTable(),
				resolved.getTargetTable(), resolved.getRelatedTables());
		return resolved;
	}

}
