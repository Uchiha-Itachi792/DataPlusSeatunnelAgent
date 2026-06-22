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

import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncIntentParseDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskResult;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.enums.DatabaseDialectEnum;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.SEATUNNEL_UNSUPPORTED_DATASOURCE_MSG;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_PARSE_FAILED_MSG;

/**
 * SeaTunnel conf 生成服务：解析同步意图，映射数据源并生成 HOCON 配置。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SeatunnelSyncService {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	private final SeatunnelConfigBuilder seatunnelConfigBuilder;

	public SeatunnelTaskResult generateConf(Long agentId, String userInput, String multiTurn) {
		try {
			SyncIntentParseDTO parsed = parseTables(userInput, multiTurn);
			if (parsed == null || !StringUtils.hasText(parsed.getSourceTable())
					|| !StringUtils.hasText(parsed.getTargetTable())) {
				return SeatunnelTaskResult.error(SYNC_PARSE_FAILED_MSG);
			}

			AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			Integer datasourceId = agentDatasource.getDatasourceId();
			Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				return SeatunnelTaskResult.error("Agent 关联的数据源不存在，无法生成 SeaTunnel conf");
			}

			if (!isMysqlDialect(datasource)) {
				return SeatunnelTaskResult.error(SEATUNNEL_UNSUPPORTED_DATASOURCE_MSG);
			}

			String requestedSource = parsed.getSourceTable().trim();
			String requestedTarget = parsed.getTargetTable().trim();
			String sourceTable = resolveTableName(datasourceId, requestedSource);
			if (sourceTable == null) {
				return SeatunnelTaskResult.error("源表 %s 不存在，无法同步".formatted(requestedSource));
			}

			String targetTable = resolveTableName(datasourceId, requestedTarget);
			if (targetTable == null) {
				return SeatunnelTaskResult.error("目标表 %s 不存在，无法同步".formatted(requestedTarget));
			}

			String jobConfig = seatunnelConfigBuilder.build(datasource, sourceTable, targetTable);
			log.info("Generated SeaTunnel conf for agent {} from {} to {}", agentId, requestedSource, requestedTarget);
			return SeatunnelTaskResult.ok(jobConfig, requestedSource, requestedTarget, datasourceId, datasourceId);
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			log.warn("SeaTunnel conf generation failed for agent {}: {}", agentId, ex.getMessage());
			return SeatunnelTaskResult.error(ex.getMessage());
		}
		catch (Exception ex) {
			log.error("Unexpected error generating SeaTunnel conf for agent {}", agentId, ex);
			return SeatunnelTaskResult.error("生成 SeaTunnel conf 失败：" + ex.getMessage());
		}
	}

	private SyncIntentParseDTO parseTables(String userInput, String multiTurn) {
		String prompt = PromptHelper.buildSyncIntentParsePrompt(multiTurn, userInput);
		String llmOutput = llmService.blockToString(llmService.callUser(prompt));
		if (!StringUtils.hasText(llmOutput)) {
			return null;
		}
		return jsonParseUtil.tryConvertToObject(llmOutput, SyncIntentParseDTO.class);
	}

	private boolean isMysqlDialect(Datasource datasource) {
		String type = datasource.getType();
		if (type != null && type.toLowerCase().contains("mysql")) {
			return true;
		}
		try {
			return DatabaseDialectEnum.MYSQL.getCode()
				.equalsIgnoreCase(datasourceService.getDbConfig(datasource).getDialectType());
		}
		catch (Exception ex) {
			return false;
		}
	}

	private String resolveTableName(Integer datasourceId, String requestedName) throws Exception {
		if (!StringUtils.hasText(requestedName)) {
			return null;
		}
		List<String> tables = datasourceService.getDatasourceTables(datasourceId);
		return tables.stream().filter(t -> t.equalsIgnoreCase(requestedName.trim())).findFirst().orElse(null);
	}

}
