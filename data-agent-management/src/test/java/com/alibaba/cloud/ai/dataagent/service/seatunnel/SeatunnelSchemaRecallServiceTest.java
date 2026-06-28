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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;

@ExtendWith(MockitoExtension.class)
class SeatunnelSchemaRecallServiceTest {

	@Mock
	private SchemaService schemaService;

	@Mock
	private AgentVectorStoreService agentVectorStoreService;

	private SeatunnelSchemaRecallService seatunnelSchemaRecallService;

	@BeforeEach
	void setUp() {
		SeatunnelProperties properties = new SeatunnelProperties();
		properties.getSchemaRecall().setTableTopkLimit(8);
		properties.getSchemaRecall().setTableSimilarityThreshold(0.35);
		seatunnelSchemaRecallService = new SeatunnelSchemaRecallService(schemaService, agentVectorStoreService,
				properties);
	}

	@Test
	void recall_emptyTableDocuments_returnsEmptyResult() {
		when(agentVectorStoreService.searchByFilterAndQuery(any(Filter.Expression.class), anyString(), anyInt(),
				anyDouble())).thenReturn(List.of());

		SeatunnelSchemaRecallResult result = seatunnelSchemaRecallService.recall(1, 1L, "订单明细");

		assertTrue(result.getTableDocuments().isEmpty());
		assertTrue(result.getRecalledTableNames().isEmpty());
	}

	@Test
	void recall_withTables_usesSeatunnelTopKAndBuildsSchema() {
		Document tableDoc = new Document("订单明细表", Map.of("name", "order_items", "datasourceId", "1"));
		when(agentVectorStoreService.searchByFilterAndQuery(any(Filter.Expression.class),
				eq("把订单明细同步到 order_items_back"), eq(8), eq(0.35))).thenReturn(List.of(tableDoc));
		when(schemaService.getColumnDocumentsByTableName(eq(1), anyList())).thenReturn(List.of());

		SeatunnelSchemaRecallResult result = seatunnelSchemaRecallService.recall(1, 1L,
				"把订单明细同步到 order_items_back");

		assertEquals(1, result.getTableDocuments().size());
		assertEquals(List.of("order_items"), result.getRecalledTableNames());
		ArgumentCaptor<SchemaDTO> schemaCaptor = ArgumentCaptor.forClass(SchemaDTO.class);
		verify(schemaService).buildSchemaFromDocuments(eq("1"), anyList(), anyList(), schemaCaptor.capture());
		assertNotNull(schemaCaptor.getValue());
	}

}
