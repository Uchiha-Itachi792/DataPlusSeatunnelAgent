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

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncIntentParseDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
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
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_COLUMN_MISMATCH_MSG;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_PARSE_FAILED_MSG;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_UNSUPPORTED_DATASOURCE_MSG;

/**
 * 表同步 SQL 生成服务：解析源/目标表、比对列元数据并生成 MySQL DDL/DML。
 */
@Slf4j
@Service
@AllArgsConstructor
public class TableSyncService {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	private final MysqlSyncSqlBuilder mysqlSyncSqlBuilder;

	public SyncTaskResult generateSyncSql(Long agentId, String userInput, String multiTurn) throws Exception {
		SyncIntentParseDTO parsed = parseTables(userInput, multiTurn);
		if (parsed == null || !StringUtils.hasText(parsed.getSourceTable())
				|| !StringUtils.hasText(parsed.getTargetTable())) {
			return SyncTaskResult.error(SYNC_PARSE_FAILED_MSG);
		}

		AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
		Integer datasourceId = agentDatasource.getDatasourceId();
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null) {
			return SyncTaskResult.error("Agent 关联的数据源不存在，无法同步");
		}

		if (!isMysqlDialect(datasource)) {
			return SyncTaskResult.error(SYNC_UNSUPPORTED_DATASOURCE_MSG);
		}

		String sourceTable = resolveTableName(datasourceId, parsed.getSourceTable());
		String targetTable = resolveTableName(datasourceId, parsed.getTargetTable());
		String requestedSource = parsed.getSourceTable().trim();
		String requestedTarget = parsed.getTargetTable().trim();

		if (sourceTable == null) {
			return SyncTaskResult.error("源表 %s 不存在，无法同步".formatted(requestedSource));
		}

		List<ColumnInfoBO> sourceColumns = datasourceService.getTableColumnMetadata(datasourceId, sourceTable);
		if (sourceColumns.isEmpty()) {
			return SyncTaskResult.error("源表 %s 无可用字段，无法同步".formatted(requestedSource));
		}

		if (targetTable == null) {
			String ddl = mysqlSyncSqlBuilder.buildCreateTable(requestedTarget, sourceColumns);
			log.info("Target table {} not found, generated CREATE TABLE SQL", requestedTarget);
			return SyncTaskResult.createSql(ddl);
		}

		List<ColumnInfoBO> targetColumns = datasourceService.getTableColumnMetadata(datasourceId, targetTable);
		if (!columnNamesEqual(sourceColumns, targetColumns)) {
			log.warn("Column mismatch between {} and {}", sourceTable, targetTable);
			return SyncTaskResult.error(SYNC_COLUMN_MISMATCH_MSG);
		}

		String insertSql = mysqlSyncSqlBuilder.buildInsertSelect(sourceTable, targetTable, sourceColumns);
		log.info("Generated INSERT SELECT SQL from {} to {}", sourceTable, targetTable);
		return SyncTaskResult.insertSql(insertSql);
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

	private boolean columnNamesEqual(List<ColumnInfoBO> sourceColumns, List<ColumnInfoBO> targetColumns) {
		Set<String> sourceNames = toColumnNameSet(sourceColumns);
		Set<String> targetNames = toColumnNameSet(targetColumns);
		return sourceNames.equals(targetNames);
	}

	private Set<String> toColumnNameSet(List<ColumnInfoBO> columns) {
		return columns.stream()
			.map(ColumnInfoBO::getName)
			.filter(StringUtils::hasText)
			.map(name -> name.toLowerCase())
			.collect(Collectors.toSet());
	}

}
