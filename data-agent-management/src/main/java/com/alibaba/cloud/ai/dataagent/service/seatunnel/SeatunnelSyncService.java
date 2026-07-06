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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SeatunnelConfGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SeatunnelTableResolveDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskResult;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.enums.DatabaseDialectEnum;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.sync.SyncSchemaBuilder;
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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.SEATUNNEL_SCHEMA_RECALL_EMPTY_MSG;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SEATUNNEL_TABLE_RESOLVE_FAILED_MSG;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SEATUNNEL_UNSUPPORTED_DATASOURCE_MSG;

/**
 * SeaTunnel conf 生成服务：独立 Schema 召回 + 表名消歧，映射数据源并生成 HOCON 配置（模板/LLM 分流）。
 * @deprecated M1 起 Graph 走 {@link com.alibaba.cloud.ai.dataagent.service.sync.SyncOrchestrator}。
 */
@Deprecated
@Slf4j
@Service
@AllArgsConstructor
public class SeatunnelSyncService {

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	private final SeatunnelSchemaRecallService seatunnelSchemaRecallService;

	private final SeatunnelTableResolveService seatunnelTableResolveService;

	private final SeatunnelRelatedTableExpander seatunnelRelatedTableExpander;

	private final SeatunnelConfigBuilder seatunnelConfigBuilder;

	private final SyncSchemaBuilder syncSchemaBuilder;

	private final SeatunnelSyncComplexityRouter complexityRouter;

	private final SeatunnelConfGenerateService seatunnelConfGenerateService;

	private final SeatunnelConfPostProcessor seatunnelConfPostProcessor;

	public SeatunnelTaskResult generateConf(Long agentId, String userInput, String multiTurn) {
		return generateConf(agentId, userInput, multiTurn, null, null);
	}

	/**
	 * 生成 SeaTunnel HOCON 配置（独立 Schema RAG 召回 → LLM 表名消歧 → 关联表扩展 → conf 生成）。
	 * @param agentId Agent ID
	 * @param userInput 原始用户输入，用于关联表扩展与 conf 生成（保留过滤/JOIN 等口语语义）
	 * @param multiTurn 多轮对话上下文
	 * @param canonicalQuery 规范化查询，用于 Schema 召回与表名消歧；为空时使用 {@code userInput}
	 * @param evidence 业务知识 Evidence，供表名消歧参考；可为 null 或「无」
	 * @return SeaTunnel conf 或错误信息
	 */
	public SeatunnelTaskResult generateConf(Long agentId, String userInput, String multiTurn, String canonicalQuery,
			String evidence) {
		try {
			String recallQuery = StringUtils.hasText(canonicalQuery) ? canonicalQuery.trim() : userInput;

			AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			Integer datasourceId = agentDatasource.getDatasourceId();
			Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				return SeatunnelTaskResult.error("Agent 关联的数据源不存在，无法生成 SeaTunnel conf");
			}

			if (!isMysqlDialect(datasource)) {
				return SeatunnelTaskResult.error(SEATUNNEL_UNSUPPORTED_DATASOURCE_MSG);
			}

			SeatunnelSchemaRecallResult recallResult = seatunnelSchemaRecallService.recall(datasourceId, agentId,
					recallQuery);
			if (recallResult.getTableDocuments().isEmpty()) {
				return SeatunnelTaskResult.error(SEATUNNEL_SCHEMA_RECALL_EMPTY_MSG);
			}

			SeatunnelTableResolveDTO resolved = seatunnelTableResolveService.resolve(recallQuery, multiTurn,
					recallResult.getSchemaDTO(), evidence);
			if (resolved == null) {
				return SeatunnelTaskResult.error(SEATUNNEL_TABLE_RESOLVE_FAILED_MSG);
			}

			String sourceTable = resolveTableName(datasourceId, resolved.getSourceTable().trim());
			if (sourceTable == null) {
				return SeatunnelTaskResult.error("源表 %s 不存在，无法同步".formatted(resolved.getSourceTable().trim()));
			}

			String requestedTarget = resolved.getTargetTable().trim();
			List<String> relatedRequested = normalizeRelatedTables(
					seatunnelRelatedTableExpander.expand(datasourceId, sourceTable, userInput,
							resolved.getRelatedTables()),
					sourceTable, requestedTarget);
			Map<String, String> resolvedRelated = new LinkedHashMap<>();
			for (String related : relatedRequested) {
				String physicalRelated = resolveTableName(datasourceId, related);
				if (physicalRelated == null) {
					return SeatunnelTaskResult.error("关联表 %s 不存在，无法同步".formatted(related));
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

			SeatunnelSyncMode mode = complexityRouter.resolve(userInput, relatedRequested, targetExists);
			DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);

			String jobConfig;
			SeatunnelTaskResult.GenerationMode generationMode;
			if (mode == SeatunnelSyncMode.TEMPLATE) {
				jobConfig = seatunnelConfigBuilder.build(datasource, sourceTable,
						targetExists ? targetTable : requestedTarget);
				generationMode = SeatunnelTaskResult.GenerationMode.TEMPLATE;
			}
			else {
				String relatedTablesDisplay = relatedRequested.isEmpty() ? "无"
						: String.join(", ", relatedRequested);
				SeatunnelConfGenerationDTO generationDTO = SeatunnelConfGenerationDTO.builder()
					.userInput(userInput)
					.multiTurn(multiTurn)
					.schemaDTO(syncSchemaBuilder.build(tableColumns))
					.sourceTable(sourceTable)
					.targetTable(targetExists ? targetTable : requestedTarget)
					.relatedTables(relatedTablesDisplay)
					.targetTableExists(targetExists)
					.build();
				String rawConf = seatunnelConfGenerateService.generate(generationDTO);
				jobConfig = seatunnelConfPostProcessor.injectCredentials(rawConf, dbConfig);
				generationMode = SeatunnelTaskResult.GenerationMode.LLM;
			}

			log.info("Generated SeaTunnel conf for agent {} from {} to {} via {}", agentId, sourceTable,
					targetExists ? targetTable : requestedTarget, generationMode);
			return SeatunnelTaskResult.ok(jobConfig, sourceTable, targetExists ? targetTable : requestedTarget,
					datasourceId, datasourceId, generationMode);
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
