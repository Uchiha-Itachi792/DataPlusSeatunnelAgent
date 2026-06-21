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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL 表同步 SQL 构建器（INSERT ... SELECT / CREATE TABLE）。
 */
@Component
public class MysqlSyncSqlBuilder {

	public String buildInsertSelect(String sourceTable, String targetTable, List<ColumnInfoBO> sourceColumns) {
		List<String> columnNames = sortedColumnNames(sourceColumns);
		String columnList = columnNames.stream().map(this::quote).collect(Collectors.joining(", "));
		return """
				INSERT INTO %s (%s)
				SELECT %s
				FROM %s;""".formatted(quote(targetTable), columnList, columnList, quote(sourceTable));
	}

	public String buildCreateTable(String targetTable, List<ColumnInfoBO> sourceColumns) {
		List<ColumnInfoBO> sorted = sourceColumns.stream()
			.filter(c -> StringUtils.hasText(c.getName()))
			.sorted(Comparator.comparing(ColumnInfoBO::getName, String.CASE_INSENSITIVE_ORDER))
			.toList();

		StringBuilder ddl = new StringBuilder();
		ddl.append("CREATE TABLE ").append(quote(targetTable)).append(" (\n");

		for (int i = 0; i < sorted.size(); i++) {
			ColumnInfoBO col = sorted.get(i);
			ddl.append("  ").append(quote(col.getName())).append(" ");
			ddl.append(StringUtils.hasText(col.getType()) ? col.getType() : "VARCHAR(255)");
			ddl.append(col.isNotnull() ? " NOT NULL" : " NULL");
			if (i < sorted.size() - 1) {
				ddl.append(",");
			}
			ddl.append("\n");
		}

		List<String> primaryKeys = sorted.stream()
			.filter(ColumnInfoBO::isPrimary)
			.map(ColumnInfoBO::getName)
			.toList();
		if (!primaryKeys.isEmpty()) {
			ddl.append(",  PRIMARY KEY (")
				.append(primaryKeys.stream().map(this::quote).collect(Collectors.joining(", ")))
				.append(")\n");
		}

		ddl.append(");");
		return ddl.toString();
	}

	private List<String> sortedColumnNames(List<ColumnInfoBO> columns) {
		return columns.stream()
			.map(ColumnInfoBO::getName)
			.filter(StringUtils::hasText)
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.toList();
	}

	private String quote(String identifier) {
		return "`" + identifier.replace("`", "``") + "`";
	}

}
