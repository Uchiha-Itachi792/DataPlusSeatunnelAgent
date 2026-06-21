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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncIntentParseDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncSqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableSyncServiceTest {

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private SyncSchemaBuilder syncSchemaBuilder;

	@Mock
	private SyncSqlGenerateService syncSqlGenerateService;

	@Mock
	private SyncRelatedTableExpander syncRelatedTableExpander;

	private TableSyncService tableSyncService;

	@BeforeEach
	void setUp() {
		tableSyncService = new TableSyncService(llmService, jsonParseUtil, agentDatasourceService, datasourceService,
				syncSchemaBuilder, syncSqlGenerateService, syncRelatedTableExpander);
		when(syncRelatedTableExpander.expand(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(3));
	}

	@Test
	void generateSyncSql_delegatesToLlmGenerator() throws Exception {
		mockParse("order", "A", List.of());
		mockMysqlAgent();

		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("order", "A"));
		List<ColumnInfoBO> columns = List.of(col("id"), col("name"));
		when(datasourceService.getTableColumnMetadata(1, "order")).thenReturn(columns);
		when(datasourceService.getTableColumnMetadata(1, "A")).thenReturn(columns);
		when(syncSchemaBuilder.build(any())).thenReturn(new SchemaDTO());
		when(syncSqlGenerateService.generate(any(SyncSqlGenerationDTO.class)))
			.thenReturn("DELETE FROM `A` WHERE status = 0; INSERT INTO `A` SELECT * FROM `order`;");

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync order to A", "(无)");

		assertEquals(SyncTaskResult.Type.SYNC_SQL, result.getType());
		assertNotNull(result.getSql());
		assertEquals(true, result.getSql().contains("DELETE FROM"));
	}

	@Test
	void generateSyncSql_sourceNotExists_returnsError() throws Exception {
		mockParse("order", "A", List.of());
		mockMysqlAgent();
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("A"));

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync order to A", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
		assertEquals(true, result.getMessage().contains("源表 order 不存在"));
	}

	@Test
	void generateSyncSql_relatedTableMissing_returnsError() throws Exception {
		mockParse("a", "b", List.of("c"));
		mockMysqlAgent();
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("a", "b"));
		when(datasourceService.getTableColumnMetadata(1, "a")).thenReturn(List.of(col("id")));

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync a and c to b", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
		assertEquals(true, result.getMessage().contains("关联表 c 不存在"));
	}

	@Test
	void generateSyncSql_validationFailure_returnsError() throws Exception {
		mockParse("order", "A", List.of());
		mockMysqlAgent();
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("order", "A"));
		when(datasourceService.getTableColumnMetadata(1, "order")).thenReturn(List.of(col("id")));
		when(datasourceService.getTableColumnMetadata(1, "A")).thenReturn(List.of(col("id")));
		when(syncSchemaBuilder.build(any())).thenReturn(new SchemaDTO());
		when(syncSqlGenerateService.generate(any(SyncSqlGenerationDTO.class)))
			.thenThrow(new IllegalArgumentException("生成的 SQL 包含不允许的操作"));

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync order to A", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
		assertEquals(true, result.getMessage().contains("不允许的操作"));
	}

	@Test
	void generateSyncSql_parseFailed_returnsError() {
		when(llmService.callUser(anyString())).thenReturn(Flux.empty());
		when(llmService.blockToString(any())).thenReturn("");

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "hello", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
	}

	private void mockParse(String source, String target, List<String> related) {
		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createResponse("parsed")));
		when(llmService.blockToString(any())).thenReturn("parsed-json");
		SyncIntentParseDTO dto = new SyncIntentParseDTO();
		dto.setSourceTable(source);
		dto.setTargetTable(target);
		dto.setRelatedTables(related);
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(dto);
	}

	private void mockMysqlAgent() {
		AgentDatasource agentDatasource = new AgentDatasource();
		agentDatasource.setDatasourceId(1);
		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(agentDatasource);
		Datasource datasource = new Datasource();
		datasource.setId(1);
		datasource.setType("mysql");
		when(datasourceService.getDatasourceById(1)).thenReturn(datasource);
	}

	private ColumnInfoBO col(String name) {
		return ColumnInfoBO.builder().name(name).type("VARCHAR(255)").build();
	}

}
