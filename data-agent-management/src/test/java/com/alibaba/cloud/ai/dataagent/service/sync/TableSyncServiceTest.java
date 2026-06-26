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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncSqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncTableResolveDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableSyncServiceTest {

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private SyncSchemaRecallService syncSchemaRecallService;

	@Mock
	private SyncTableResolveService syncTableResolveService;

	@Mock
	private SyncSchemaBuilder syncSchemaBuilder;

	@Mock
	private SyncSqlGenerateService syncSqlGenerateService;

	@Mock
	private SyncRelatedTableExpander syncRelatedTableExpander;

	private TableSyncService tableSyncService;

	@BeforeEach
	void setUp() {
		tableSyncService = new TableSyncService(agentDatasourceService, datasourceService, syncSchemaRecallService,
				syncTableResolveService, syncSchemaBuilder, syncSqlGenerateService, syncRelatedTableExpander);
		when(syncRelatedTableExpander.expand(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(3));
	}

	@Test
	void generateSyncSql_delegatesToLlmGenerator() throws Exception {
		mockRecallAndResolve("order", "A", List.of());
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
		assertTrue(result.getSql().contains("DELETE FROM"));
	}

	@Test
	void generateSyncSql_orderItemsBusinessName_resolvesPhysicalTable() throws Exception {
		mockRecallAndResolve("order_items", "order_items_back", List.of());
		mockMysqlAgent();

		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("order_items"));
		List<ColumnInfoBO> columns = List.of(col("id"), col("order_id"));
		when(datasourceService.getTableColumnMetadata(1, "order_items")).thenReturn(columns);
		when(syncSchemaBuilder.build(any())).thenReturn(new SchemaDTO());
		when(syncSqlGenerateService.generate(any(SyncSqlGenerationDTO.class)))
			.thenReturn("INSERT INTO `order_items_back` SELECT * FROM `order_items`;");

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "把订单明细同步到 order_items_back", "(无)");

		assertEquals(SyncTaskResult.Type.SYNC_SQL, result.getType());
		assertEquals("order_items", result.getSourceTable());
		assertEquals("order_items_back", result.getTargetTable());
		assertTrue(result.getSql().contains("order_items"));
	}

	@Test
	void generateSyncSql_sourceNotExists_returnsError() throws Exception {
		mockRecallAndResolve("order", "A", List.of());
		mockMysqlAgent();
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("A"));

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync order to A", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
		assertTrue(result.getMessage().contains("源表 order 不存在"));
	}

	@Test
	void generateSyncSql_relatedTableMissing_returnsError() throws Exception {
		mockRecallAndResolve("a", "b", List.of("c"));
		mockMysqlAgent();
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("a", "b"));
		when(datasourceService.getTableColumnMetadata(1, "a")).thenReturn(List.of(col("id")));

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync a and c to b", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
		assertTrue(result.getMessage().contains("关联表 c 不存在"));
	}

	@Test
	void generateSyncSql_schemaRecallEmpty_returnsError() {
		mockMysqlAgent();
		when(syncSchemaRecallService.recall(any(), anyLong(), anyString()))
			.thenReturn(SyncSchemaRecallResult.builder().build());

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync something", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
		assertTrue(result.getMessage().contains("未检索到相关数据表"));
	}

	@Test
	void generateSyncSql_tableResolveFailed_returnsError() {
		mockMysqlAgent();
		Document tableDoc = new Document("t", Map.of("name", "order_items"));
		when(syncSchemaRecallService.recall(any(), anyLong(), anyString())).thenReturn(SyncSchemaRecallResult.builder()
			.tableDocuments(List.of(tableDoc))
			.schemaDTO(new SchemaDTO())
			.build());
		when(syncTableResolveService.resolve(anyString(), anyString(), any())).thenReturn(null);

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
		assertTrue(result.getMessage().contains("无法从 Schema 中确定源表或目标表"));
	}

	@Test
	void generateSyncSql_validationFailure_returnsError() throws Exception {
		mockRecallAndResolve("order", "A", List.of());
		mockMysqlAgent();
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("order", "A"));
		when(datasourceService.getTableColumnMetadata(1, "order")).thenReturn(List.of(col("id")));
		when(datasourceService.getTableColumnMetadata(1, "A")).thenReturn(List.of(col("id")));
		when(syncSchemaBuilder.build(any())).thenReturn(new SchemaDTO());
		when(syncSqlGenerateService.generate(any(SyncSqlGenerationDTO.class)))
			.thenThrow(new IllegalArgumentException("生成的 SQL 包含不允许的操作"));

		SyncTaskResult result = tableSyncService.generateSyncSql(1L, "sync order to A", "(无)");

		assertEquals(SyncTaskResult.Type.ERROR, result.getType());
		assertTrue(result.getMessage().contains("不允许的操作"));
	}

	private void mockRecallAndResolve(String source, String target, List<String> related) {
		Document tableDoc = new Document("t", Map.of("name", source));
		SchemaDTO schemaDTO = new SchemaDTO();
		when(syncSchemaRecallService.recall(any(), anyLong(), anyString())).thenReturn(SyncSchemaRecallResult.builder()
			.tableDocuments(List.of(tableDoc))
			.schemaDTO(schemaDTO)
			.recalledTableNames(List.of(source))
			.build());
		SyncTableResolveDTO dto = new SyncTableResolveDTO();
		dto.setSourceTable(source);
		dto.setTargetTable(target);
		dto.setRelatedTables(related);
		when(syncTableResolveService.resolve(anyString(), anyString(), any())).thenReturn(dto);
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
