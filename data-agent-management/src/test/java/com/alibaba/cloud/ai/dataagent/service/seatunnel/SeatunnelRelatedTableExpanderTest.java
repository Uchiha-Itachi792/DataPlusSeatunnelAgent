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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;

@ExtendWith(MockitoExtension.class)
class SeatunnelRelatedTableExpanderTest {

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private Accessor accessor;

	private SeatunnelRelatedTableExpander expander;

	@BeforeEach
	void setUp() {
		SeatunnelProperties properties = new SeatunnelProperties();
		properties.getRelatedTable().setFkAutoExpandEnabled(true);
		expander = new SeatunnelRelatedTableExpander(datasourceService, accessorFactory, properties);
	}

	@Test
	void expand_noFilterIntent_returnsParsedOnly() throws Exception {
		List<String> result = expander.expand(1, "order_items", "把 order_items 同步到 order_items_back",
				List.of("products"));

		assertEquals(List.of("products"), result);
	}

	@Test
	void expand_fkAutoExpandDisabled_skipsFkExpansion() throws Exception {
		SeatunnelProperties properties = new SeatunnelProperties();
		properties.getRelatedTable().setFkAutoExpandEnabled(false);
		expander = new SeatunnelRelatedTableExpander(datasourceService, accessorFactory, properties);

		List<String> result = expander.expand(1, "order_items", "排除 status=0 的数据", List.of());

		assertTrue(result.isEmpty());
	}

	@Test
	void expand_filterIntent_includesFkReferencedTable() throws Exception {
		Datasource datasource = new Datasource();
		datasource.setId(1);
		DbConfigBO dbConfig = DbConfigBO.builder().schema("test").dialectType("mysql").build();
		when(datasourceService.getDatasourceById(1)).thenReturn(datasource);
		when(datasourceService.getDbConfig(datasource)).thenReturn(dbConfig);
		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showForeignKeys(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(List.of(ForeignKeyInfoBO.builder()
				.table("order_items")
				.column("product_id")
				.referencedTable("products")
				.referencedColumn("id")
				.build()));
		when(datasourceService.getLogicalRelations(1)).thenReturn(List.of());

		List<String> result = expander.expand(1, "order_items", "排除商品 status=0 的数据", List.of());

		assertFalse(result.isEmpty());
		assertTrue(result.contains("products"));
	}

	@Test
	void expand_logicalRelationIncludedWhenMentioned() throws Exception {
		Datasource datasource = new Datasource();
		datasource.setId(1);
		DbConfigBO dbConfig = DbConfigBO.builder().schema("test").dialectType("mysql").build();
		when(datasourceService.getDatasourceById(1)).thenReturn(datasource);
		when(datasourceService.getDbConfig(datasource)).thenReturn(dbConfig);
		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showForeignKeys(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(List.of());
		when(datasourceService.getLogicalRelations(1)).thenReturn(List.of(LogicalRelation.builder()
			.sourceTableName("order_items")
			.sourceColumnName("product_id")
			.targetTableName("products")
			.isDeleted(0)
			.build()));

		List<String> result = expander.expand(1, "order_items", "排除 products 中 status=0 的数据", List.of());

		assertTrue(result.contains("products"));
	}

}
