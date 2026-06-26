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

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据同步专用 Schema 向量召回：语义检索相关表/列，并按外键补全关联表。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SyncSchemaRecallService {

	private final SchemaService schemaService;

	/**
	 * 基于用户输入召回同步任务相关的 Schema。
	 * @param datasourceId 数据源 ID
	 * @param agentId Agent ID（用于 buildSchemaFromDocuments）
	 * @param userInput 用户同步需求描述
	 * @return 召回结果；表 Document 为空时表示未命中
	 */
	public SyncSchemaRecallResult recall(Integer datasourceId, Long agentId, String userInput) {
		List<Document> tableDocuments = new ArrayList<>(
				schemaService.searchTableDocumentsByQuery(datasourceId, userInput));
		List<String> recalledTableNames = extractTableNames(tableDocuments);
		log.info("Sync schema recall for datasource {}: {} tables {}", datasourceId, recalledTableNames.size(),
				recalledTableNames);

		if (tableDocuments.isEmpty()) {
			return SyncSchemaRecallResult.builder().build();
		}

		List<Document> columnDocuments = new ArrayList<>(
				schemaService.getColumnDocumentsByTableName(datasourceId, recalledTableNames));

		SchemaDTO schemaDTO = new SchemaDTO();
		schemaService.buildSchemaFromDocuments(String.valueOf(agentId), columnDocuments, tableDocuments, schemaDTO);

		return SyncSchemaRecallResult.builder()
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
