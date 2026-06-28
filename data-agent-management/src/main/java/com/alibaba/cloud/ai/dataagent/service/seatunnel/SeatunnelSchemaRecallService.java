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

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SeaTunnel 专用 Schema 向量召回：语义检索相关表/列（与 SQL 同步轨 {@code SyncSchemaRecallService} 隔离，独立 TopK/阈值）。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SeatunnelSchemaRecallService {

	private final SchemaService schemaService;

	private final AgentVectorStoreService agentVectorStoreService;

	private final SeatunnelProperties seatunnelProperties;

	/**
	 * 基于用户输入召回 SeaTunnel 同步任务相关的 Schema。
	 * @param datasourceId 数据源 ID
	 * @param agentId Agent ID（用于 buildSchemaFromDocuments）
	 * @param userInput 用户同步需求描述
	 * @return 召回结果；表 Document 为空时表示未命中
	 */
	public SeatunnelSchemaRecallResult recall(Integer datasourceId, Long agentId, String userInput) {
		int tableTopK = seatunnelProperties.getSchemaRecall().getTableTopkLimit();
		double tableThreshold = seatunnelProperties.getSchemaRecall().getTableSimilarityThreshold();

		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		List<Filter.Expression> conditions = new ArrayList<>();
		conditions.add(builder.eq(Constant.DATASOURCE_ID, datasourceId.toString()).build());
		conditions.add(builder.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE).build());
		Filter.Expression filterExpression = DynamicFilterService.combineWithAnd(conditions);

		List<Document> tableDocuments = new ArrayList<>(agentVectorStoreService.searchByFilterAndQuery(filterExpression,
				userInput, tableTopK, tableThreshold));
		List<String> recalledTableNames = extractTableNames(tableDocuments);
		log.info("SeaTunnel schema recall for datasource {} (topK={}, threshold={}): {} tables {}", datasourceId,
				tableTopK, tableThreshold, recalledTableNames.size(), recalledTableNames);

		if (tableDocuments.isEmpty()) {
			return SeatunnelSchemaRecallResult.builder().build();
		}

		List<Document> columnDocuments = new ArrayList<>(
				schemaService.getColumnDocumentsByTableName(datasourceId, recalledTableNames));

		SchemaDTO schemaDTO = new SchemaDTO();
		schemaService.buildSchemaFromDocuments(String.valueOf(agentId), columnDocuments, tableDocuments, schemaDTO);

		return SeatunnelSchemaRecallResult.builder()
			.tableDocuments(tableDocuments)
			.columnDocuments(columnDocuments)
			.schemaDTO(schemaDTO)
			.recalledTableNames(recalledTableNames)
			.build();
	}

	private List<String> extractTableNames(List<Document> tableDocuments) {
		List<String> tableNames = new ArrayList<>();
		for (Document document : tableDocuments) {
			String name = (String) document.getMetadata().get("name");
			if (StringUtils.hasText(name)) {
				tableNames.add(name);
			}
		}
		return tableNames;
	}

}
