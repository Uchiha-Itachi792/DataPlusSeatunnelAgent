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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;

@ExtendWith(MockitoExtension.class)
class SyncRelatedTableExpanderTest {

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private Accessor accessor;

	private SyncRelatedTableExpander expander;

	@BeforeEach
	void setUp() {
		expander = new SyncRelatedTableExpander(datasourceService, accessorFactory);
	}

	@Test
	void expand_productFilter_includesProductsTable() throws Exception {
		mockForeignKeys(List.of(ForeignKeyInfoBO.builder()
			.table("order_items")
			.column("product_id")
			.referencedTable("products")
			.referencedColumn("id")
			.build()));

		String userInput = "把order_items表的数据同步到order_items_back表中。要求：如果order_items表中对应的商品为T恤衫，那么这条数据不要同步";
		List<String> expanded = expander.expand(1, "order_items", userInput, List.of());

		assertTrue(expanded.contains("products"));
	}

	@Test
	void expand_noFilterIntent_keepsParsedRelatedOnly() {
		List<String> expanded = expander.expand(1, "order_items", "把 order_items 全量同步到 order_items_back", List.of());

		assertEquals(0, expanded.size());
	}

	private void mockForeignKeys(List<ForeignKeyInfoBO> foreignKeys) throws Exception {
		Datasource datasource = new Datasource();
		datasource.setId(1);
		datasource.setType("mysql");
		when(datasourceService.getDatasourceById(1)).thenReturn(datasource);
		when(datasourceService.getDbConfig(datasource)).thenReturn(new DbConfigBO());
		when(datasourceService.getLogicalRelations(1)).thenReturn(List.of());
		when(accessorFactory.getAccessorByDbConfig(any())).thenReturn(accessor);
		when(accessor.showForeignKeys(any(), any())).thenReturn(foreignKeys);
	}

}
