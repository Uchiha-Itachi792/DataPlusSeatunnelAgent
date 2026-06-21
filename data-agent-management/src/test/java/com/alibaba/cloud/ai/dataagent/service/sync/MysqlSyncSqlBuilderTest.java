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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;

class MysqlSyncSqlBuilderTest {

	private MysqlSyncSqlBuilder builder;

	@BeforeEach
	void setUp() {
		builder = new MysqlSyncSqlBuilder();
	}

	@Test
	void buildInsertSelect_generatesExpectedSql() {
		List<ColumnInfoBO> columns = List.of(column("id", true), column("name", false));

		String sql = builder.buildInsertSelect("order", "A", columns);

		assertTrue(sql.contains("INSERT INTO `A` (`id`, `name`)"));
		assertTrue(sql.contains("SELECT `id`, `name`"));
		assertTrue(sql.contains("FROM `order`;"));
	}

	@Test
	void buildCreateTable_includesPrimaryKey() {
		List<ColumnInfoBO> columns = List.of(column("id", true), column("name", false));

		String sql = builder.buildCreateTable("A", columns);

		assertTrue(sql.startsWith("CREATE TABLE `A` ("));
		assertTrue(sql.contains("`id`"));
		assertTrue(sql.contains("PRIMARY KEY (`id`)"));
		assertTrue(sql.endsWith(");"));
	}

	private ColumnInfoBO column(String name, boolean primary) {
		return ColumnInfoBO.builder().name(name).type("VARCHAR(255)").primary(primary).notnull(primary).build();
	}

}
