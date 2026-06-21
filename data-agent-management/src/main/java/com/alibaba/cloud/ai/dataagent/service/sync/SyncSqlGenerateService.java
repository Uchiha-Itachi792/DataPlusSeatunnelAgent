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

import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncSqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 LLM 的灵活同步 SQL 生成服务。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SyncSqlGenerateService {

	private final LlmService llmService;

	private final Nl2SqlService nl2SqlService;

	private final SyncSqlValidator syncSqlValidator;

	public String generate(SyncSqlGenerationDTO dto) {
		String prompt = PromptHelper.buildSyncSqlGeneratePrompt(dto);
		log.debug("Sync SQL generate prompt:\n{}", prompt);

		String llmOutput = llmService.blockToString(llmService.callUser(prompt));
		if (!StringUtils.hasText(llmOutput)) {
			throw new IllegalStateException("未能生成同步 SQL，请补充表名与同步规则后重试");
		}

		String sql = nl2SqlService.sqlTrim(llmOutput);
		if (!StringUtils.hasText(sql)) {
			throw new IllegalStateException("未能生成有效的同步 SQL，请重新描述同步需求");
		}

		syncSqlValidator.validate(sql);
		log.info("Generated sync SQL script, length={}", sql.length());
		return sql;
	}

}
