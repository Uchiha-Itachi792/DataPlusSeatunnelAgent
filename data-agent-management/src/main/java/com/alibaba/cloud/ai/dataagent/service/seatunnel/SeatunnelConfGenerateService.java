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
package com.alibaba.cloud.ai.dataagent.service.seatunnel;

import com.alibaba.cloud.ai.dataagent.dto.prompt.SeatunnelConfGenerationDTO;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.MarkdownParserUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 LLM 的灵活 SeaTunnel conf 生成服务。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SeatunnelConfGenerateService {

	private final LlmService llmService;

	private final SeatunnelConfValidator seatunnelConfValidator;

	public String generate(SeatunnelConfGenerationDTO dto) {
		String prompt = PromptHelper.buildSeatunnelConfGeneratePrompt(dto);
		log.debug("SeaTunnel conf generate prompt:\n{}", prompt);

		String llmOutput = llmService.blockToString(llmService.callUser(prompt));
		if (!StringUtils.hasText(llmOutput)) {
			throw new IllegalStateException("未能生成 SeaTunnel conf，请补充表名与同步规则后重试");
		}

		String conf = MarkdownParserUtil.extractRawText(llmOutput).trim();
		if (!StringUtils.hasText(conf)) {
			throw new IllegalStateException("未能生成有效的 SeaTunnel conf，请重新描述同步需求");
		}

		seatunnelConfValidator.validate(conf, dto.getUserInput());
		log.info("Generated SeaTunnel conf via LLM, length={}", conf.length());
		return conf;
	}

}
