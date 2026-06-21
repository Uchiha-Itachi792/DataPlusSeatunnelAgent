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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;

class SyncSchemaBuilderTest {

	private final SyncSchemaBuilder builder = new SyncSchemaBuilder();

	@Test
	void build_multipleTablesWithColumns() {
		Map<String, List<ColumnInfoBO>> tableColumns = Map.of("order",
				List.of(ColumnInfoBO.builder().name("id").type("INT").primary(true).build(),
						ColumnInfoBO.builder().name("name").type("VARCHAR(255)").build()),
				"A", List.of(ColumnInfoBO.builder().name("id").type("INT").build()));

		SchemaDTO schema = builder.build(tableColumns);

		assertEquals(2, schema.getTable().size());
		TableDTO orderTable = schema.getTable().stream().filter(t -> "order".equals(t.getName())).findFirst().orElseThrow();
		assertEquals(2, orderTable.getColumn().size());
		assertNotNull(orderTable.getPrimaryKeys());
		assertEquals("id", orderTable.getPrimaryKeys().get(0));
	}

}
