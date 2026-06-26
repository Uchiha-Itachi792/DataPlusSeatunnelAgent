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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
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

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;

@ExtendWith(MockitoExtension.class)
class SyncSchemaRecallServiceTest {

	@Mock
	private SchemaService schemaService;

	private SyncSchemaRecallService syncSchemaRecallService;

	@BeforeEach
	void setUp() {
		syncSchemaRecallService = new SyncSchemaRecallService(schemaService);
	}

	@Test
	void recall_emptyTableDocuments_returnsEmptyResult() {
		when(schemaService.searchTableDocumentsByQuery(1, "订单明细")).thenReturn(List.of());

		SyncSchemaRecallResult result = syncSchemaRecallService.recall(1, 1L, "订单明细");

		assertTrue(result.getTableDocuments().isEmpty());
		assertTrue(result.getRecalledTableNames().isEmpty());
	}

	@Test
	void recall_withTables_buildsSchema() {
		Document tableDoc = new Document("订单明细表", Map.of("name", "order_items", "datasourceId", "1"));
		when(schemaService.searchTableDocumentsByQuery(1, "把订单明细同步到 order_items_back"))
			.thenReturn(List.of(tableDoc));
		when(schemaService.getColumnDocumentsByTableName(eq(1), anyList())).thenReturn(List.of());

		SyncSchemaRecallResult result = syncSchemaRecallService.recall(1, 1L, "把订单明细同步到 order_items_back");

		assertEquals(1, result.getTableDocuments().size());
		assertEquals(List.of("order_items"), result.getRecalledTableNames());
		ArgumentCaptor<SchemaDTO> schemaCaptor = ArgumentCaptor.forClass(SchemaDTO.class);
		verify(schemaService).buildSchemaFromDocuments(eq("1"), anyList(), anyList(), schemaCaptor.capture());
		assertNotNull(schemaCaptor.getValue());
	}

}
