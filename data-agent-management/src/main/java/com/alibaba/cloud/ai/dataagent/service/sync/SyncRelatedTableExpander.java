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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 根据外键、逻辑外键与用户过滤描述，自动扩展同步所需的关联表。
 */
@Slf4j
@Component
@AllArgsConstructor
public class SyncRelatedTableExpander {

	private final DatasourceService datasourceService;

	private final AccessorFactory accessorFactory;

	/**
	 * 在 LLM 解析的 relatedTables 基础上，补充过滤条件可能依赖的关联表。
	 */
	public List<String> expand(Integer datasourceId, String sourceTable, String userInput,
			List<String> parsedRelatedTables) {
		Set<String> expanded = new LinkedHashSet<>();
		if (parsedRelatedTables != null) {
			parsedRelatedTables.stream()
				.filter(StringUtils::hasText)
				.map(String::trim)
				.forEach(expanded::add);
		}

		if (!StringUtils.hasText(userInput) || !hasFilterIntent(userInput)) {
			return new ArrayList<>(expanded);
		}

		String sourceLower = sourceTable.toLowerCase(Locale.ROOT);
		for (ForeignKeyInfoBO fk : loadForeignKeys(datasourceId, sourceTable)) {
			if (sourceLower.equalsIgnoreCase(fk.getTable()) && shouldIncludeReferencedTable(userInput, fk)) {
				expanded.add(fk.getReferencedTable());
				log.info("Auto-included related table {} via FK {}.{} -> {}.{}", fk.getReferencedTable(), fk.getTable(),
						fk.getColumn(), fk.getReferencedTable(), fk.getReferencedColumn());
			}
		}

		for (LogicalRelation relation : datasourceService.getLogicalRelations(datasourceId)) {
			if (relation.getIsDeleted() != null && relation.getIsDeleted() == 1) {
				continue;
			}
			if (sourceLower.equalsIgnoreCase(relation.getSourceTableName())
					&& shouldIncludeLogicalTarget(userInput, relation)) {
				expanded.add(relation.getTargetTableName());
				log.info("Auto-included related table {} via logical relation from {}", relation.getTargetTableName(),
						relation.getSourceTableName());
			}
		}

		expanded.removeIf(name -> name.equalsIgnoreCase(sourceTable));
		return new ArrayList<>(expanded);
	}

	private boolean hasFilterIntent(String userInput) {
		String lower = userInput.toLowerCase(Locale.ROOT);
		return lower.contains("不要") || lower.contains("排除") || lower.contains("过滤") || lower.contains("不同步")
				|| lower.contains("跳过") || lower.contains("满足") || lower.contains("条件") || lower.contains("仅")
				|| lower.contains("只") || lower.contains("where") || lower.contains("不等于") || lower.contains("!= ");
	}

	private boolean shouldIncludeReferencedTable(String userInput, ForeignKeyInfoBO fk) {
		String lower = userInput.toLowerCase(Locale.ROOT);
		String refTable = fk.getReferencedTable().toLowerCase(Locale.ROOT);
		if (lower.contains(refTable) || lower.contains(singularTableName(refTable))) {
			return true;
		}
		return matchesForeignKeySemantics(lower, fk.getColumn(), refTable);
	}

	private boolean shouldIncludeLogicalTarget(String userInput, LogicalRelation relation) {
		String lower = userInput.toLowerCase(Locale.ROOT);
		String targetTable = relation.getTargetTableName().toLowerCase(Locale.ROOT);
		if (lower.contains(targetTable) || lower.contains(singularTableName(targetTable))) {
			return true;
		}
		return matchesForeignKeySemantics(lower, relation.getSourceColumnName(), targetTable);
	}

	private boolean matchesForeignKeySemantics(String lowerUserInput, String fkColumn, String referencedTable) {
		if (!StringUtils.hasText(fkColumn)) {
			return false;
		}
		String column = fkColumn.toLowerCase(Locale.ROOT);
		if (column.contains("product") || referencedTable.contains("product")) {
			return lowerUserInput.contains("商品") || lowerUserInput.contains("产品") || lowerUserInput.contains("product")
					|| lowerUserInput.contains("t恤") || lowerUserInput.contains("shirt");
		}
		if (column.contains("user") || referencedTable.contains("user")) {
			return lowerUserInput.contains("用户") || lowerUserInput.contains("客户") || lowerUserInput.contains("user");
		}
		if (column.contains("category") || referencedTable.contains("categor")) {
			return lowerUserInput.contains("分类") || lowerUserInput.contains("category");
		}
		if (column.contains("order") && referencedTable.contains("order")) {
			return lowerUserInput.contains("订单") && !column.equals("order_id");
		}
		return false;
	}

	private String singularTableName(String tableName) {
		if (tableName.endsWith("ies")) {
			return tableName.substring(0, tableName.length() - 3) + "y";
		}
		if (tableName.endsWith("s") && tableName.length() > 1) {
			return tableName.substring(0, tableName.length() - 1);
		}
		return tableName;
	}

	private List<ForeignKeyInfoBO> loadForeignKeys(Integer datasourceId, String sourceTable) {
		try {
			DbConfigBO dbConfig = datasourceService.getDbConfig(datasourceService.getDatasourceById(datasourceId));
			Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);
			DbQueryParameter param = DbQueryParameter.from(dbConfig)
				.setSchema(dbConfig.getSchema())
				.setTables(List.of(sourceTable));
			return accessor.showForeignKeys(dbConfig, param);
		}
		catch (Exception ex) {
			log.warn("Failed to load foreign keys for table {} in datasource {}: {}", sourceTable, datasourceId,
					ex.getMessage());
			return List.of();
		}
	}

}
