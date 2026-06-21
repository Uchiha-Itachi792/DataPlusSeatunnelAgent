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
import com.alibaba.cloud.ai.dataagent.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 将多表列元数据组装为 LLM 可用的 SchemaDTO。
 */
@Component
public class SyncSchemaBuilder {

	public SchemaDTO build(Map<String, List<ColumnInfoBO>> tableColumns) {
		SchemaDTO schemaDTO = new SchemaDTO();
		List<TableDTO> tables = new ArrayList<>();

		tableColumns.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
			.forEach(entry -> tables.add(toTableDto(entry.getKey(), entry.getValue())));

		schemaDTO.setTable(tables);
		schemaDTO.setForeignKeys(new ArrayList<>());
		return schemaDTO;
	}

	private TableDTO toTableDto(String tableName, List<ColumnInfoBO> columns) {
		TableDTO tableDTO = new TableDTO();
		tableDTO.setName(tableName);

		List<ColumnInfoBO> sorted = columns.stream()
			.filter(c -> StringUtils.hasText(c.getName()))
			.sorted(Comparator.comparing(ColumnInfoBO::getName, String.CASE_INSENSITIVE_ORDER))
			.toList();

		List<String> primaryKeys = new ArrayList<>();
		for (ColumnInfoBO column : sorted) {
			ColumnDTO columnDTO = new ColumnDTO();
			columnDTO.setName(column.getName());
			columnDTO.setType(StringUtils.hasText(column.getType()) ? column.getType() : "VARCHAR(255)");
			if (column.isPrimary()) {
				primaryKeys.add(column.getName());
			}
			tableDTO.getColumn().add(columnDTO);
		}
		if (!primaryKeys.isEmpty()) {
			tableDTO.setPrimaryKeys(primaryKeys);
		}
		return tableDTO;
	}

}
