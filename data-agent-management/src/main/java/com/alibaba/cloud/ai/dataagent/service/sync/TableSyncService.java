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
import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncSqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncTableResolveDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.enums.DatabaseDialectEnum;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_SCHEMA_RECALL_EMPTY_MSG;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_TABLE_RESOLVE_FAILED_MSG;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_UNSUPPORTED_DATASOURCE_MSG;

/**
 * 表同步 SQL 生成服务：Schema RAG 召回 + LLM 表名消歧，基于 Schema + LLM 生成灵活同步脚本。
 */
@Slf4j
@Service
@AllArgsConstructor
public class TableSyncService {

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	private final SyncSchemaRecallService syncSchemaRecallService;

	private final SyncTableResolveService syncTableResolveService;

	private final SyncSchemaBuilder syncSchemaBuilder;

	private final SyncSqlGenerateService syncSqlGenerateService;

	private final SyncRelatedTableExpander syncRelatedTableExpander;

	public SyncTaskResult generateSyncSql(Long agentId, String userInput, String multiTurn) {
		try {
			AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			Integer datasourceId = agentDatasource.getDatasourceId();
			Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				return SyncTaskResult.error("Agent 关联的数据源不存在，无法同步");
			}

			if (!isMysqlDialect(datasource)) {
				return SyncTaskResult.error(SYNC_UNSUPPORTED_DATASOURCE_MSG);
			}

			SyncSchemaRecallResult recallResult = syncSchemaRecallService.recall(datasourceId, agentId, userInput);
			if (recallResult.getTableDocuments().isEmpty()) {
				return SyncTaskResult.error(SYNC_SCHEMA_RECALL_EMPTY_MSG);
			}

			SyncTableResolveDTO resolved = syncTableResolveService.resolve(userInput, multiTurn,
					recallResult.getSchemaDTO());
			if (resolved == null) {
				return SyncTaskResult.error(SYNC_TABLE_RESOLVE_FAILED_MSG);
			}

			String sourceTable = resolveTableName(datasourceId, resolved.getSourceTable().trim());
			if (sourceTable == null) {
				return SyncTaskResult.error("源表 %s 不存在，无法同步".formatted(resolved.getSourceTable().trim()));
			}

			String requestedTarget = resolved.getTargetTable().trim();
			List<String> relatedRequested = normalizeRelatedTables(
					syncRelatedTableExpander.expand(datasourceId, sourceTable, userInput, resolved.getRelatedTables()),
					sourceTable, requestedTarget);
			Map<String, String> resolvedRelated = new LinkedHashMap<>();
			for (String related : relatedRequested) {
				String physicalRelated = resolveTableName(datasourceId, related);
				if (physicalRelated == null) {
					return SyncTaskResult.error("关联表 %s 不存在，无法同步".formatted(related));
				}
				resolvedRelated.put(related, physicalRelated);
			}

			String targetTable = resolveTableName(datasourceId, requestedTarget);
			boolean targetExists = targetTable != null;

			Map<String, List<ColumnInfoBO>> tableColumns = new LinkedHashMap<>();
			tableColumns.put(sourceTable, loadColumns(datasourceId, sourceTable, sourceTable));
			for (Map.Entry<String, String> entry : resolvedRelated.entrySet()) {
				tableColumns.put(entry.getValue(), loadColumns(datasourceId, entry.getValue(), entry.getKey()));
			}
			if (targetExists) {
				tableColumns.put(targetTable, loadColumns(datasourceId, targetTable, requestedTarget));
			}

			String relatedTablesDisplay = relatedRequested.isEmpty() ? "无"
					: String.join(", ", relatedRequested);

			SyncSqlGenerationDTO generationDTO = SyncSqlGenerationDTO.builder()
				.userInput(userInput)
				.multiTurn(multiTurn)
				.schemaDTO(syncSchemaBuilder.build(tableColumns))
				.sourceTable(sourceTable)
				.targetTable(targetExists ? targetTable : requestedTarget)
				.relatedTables(relatedTablesDisplay)
				.targetTableExists(targetExists)
				.dialect(DatabaseDialectEnum.MYSQL.getCode())
				.build();

			String sql = syncSqlGenerateService.generate(generationDTO);
			log.info("Generated sync SQL for agent {} from {} to {}", agentId, sourceTable,
					targetExists ? targetTable : requestedTarget);
			return SyncTaskResult.syncSql(sql, sourceTable, targetExists ? targetTable : requestedTarget, datasourceId);
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			log.warn("Sync SQL generation failed for agent {}: {}", agentId, ex.getMessage());
			return SyncTaskResult.error(ex.getMessage());
		}
		catch (Exception ex) {
			log.error("Unexpected error generating sync SQL for agent {}", agentId, ex);
			return SyncTaskResult.error("生成同步 SQL 失败：" + ex.getMessage());
		}
	}

	private List<ColumnInfoBO> loadColumns(Integer datasourceId, String tableName, String displayName) throws Exception {
		List<ColumnInfoBO> columns = datasourceService.getTableColumnMetadata(datasourceId, tableName);
		if (columns.isEmpty()) {
			throw new IllegalStateException("表 %s 无可用字段，无法同步".formatted(displayName));
		}
		return columns;
	}

	private List<String> normalizeRelatedTables(List<String> relatedTables, String sourceTable, String targetTable) {
		if (relatedTables == null || relatedTables.isEmpty()) {
			return List.of();
		}
		Set<String> excluded = Set.of(sourceTable.toLowerCase(), targetTable.toLowerCase());
		return relatedTables.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.filter(name -> !excluded.contains(name.toLowerCase()))
			.collect(Collectors.toCollection(ArrayList::new));
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
