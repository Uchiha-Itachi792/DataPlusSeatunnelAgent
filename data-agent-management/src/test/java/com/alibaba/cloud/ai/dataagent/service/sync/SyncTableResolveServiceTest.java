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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncTableResolveDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class SyncTableResolveServiceTest {

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	private SyncTableResolveService syncTableResolveService;

	@BeforeEach
	void setUp() {
		syncTableResolveService = new SyncTableResolveService(llmService, jsonParseUtil);
	}

	@Test
	void resolve_emptySchema_returnsNull() {
		assertNull(syncTableResolveService.resolve("sync", "(无)", new SchemaDTO()));
	}

	@Test
	void resolve_validLlmOutput_returnsDto() {
		SchemaDTO schemaDTO = new SchemaDTO();
		TableDTO table = new TableDTO();
		table.setName("order_items");
		schemaDTO.setTable(List.of(table));

		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createResponse("json")));
		when(llmService.blockToString(any())).thenReturn("json");
		SyncTableResolveDTO dto = new SyncTableResolveDTO();
		dto.setSourceTable("order_items");
		dto.setTargetTable("order_items_back");
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(dto);

		SyncTableResolveDTO result = syncTableResolveService.resolve("把订单明细同步到 order_items_back", "(无)", schemaDTO);

		assertNotNull(result);
		assertEquals("order_items", result.getSourceTable());
		assertEquals("order_items_back", result.getTargetTable());
	}

	@Test
	void resolve_incompleteLlmOutput_returnsNull() {
		SchemaDTO schemaDTO = new SchemaDTO();
		TableDTO table = new TableDTO();
		table.setName("order_items");
		schemaDTO.setTable(List.of(table));

		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createResponse("json")));
		when(llmService.blockToString(any())).thenReturn("json");
		SyncTableResolveDTO dto = new SyncTableResolveDTO();
		dto.setSourceTable("order_items");
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(dto);

		assertNull(syncTableResolveService.resolve("sync", "(无)", schemaDTO));
	}

}
