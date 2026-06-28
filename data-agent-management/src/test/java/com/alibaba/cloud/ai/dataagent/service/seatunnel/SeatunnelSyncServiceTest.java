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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.ai.document.Document;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SeatunnelConfGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SeatunnelTableResolveDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelSchemaRecallResult;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskResult;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.sync.SyncSchemaBuilder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeatunnelSyncServiceTest {

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private SeatunnelSchemaRecallService seatunnelSchemaRecallService;

	@Mock
	private SeatunnelTableResolveService seatunnelTableResolveService;

	@Mock
	private SeatunnelRelatedTableExpander seatunnelRelatedTableExpander;

	@Mock
	private SeatunnelConfigBuilder seatunnelConfigBuilder;

	@Mock
	private SyncSchemaBuilder syncSchemaBuilder;

	@Mock
	private SeatunnelConfGenerateService seatunnelConfGenerateService;

	@Mock
	private SeatunnelConfPostProcessor seatunnelConfPostProcessor;

	private SeatunnelSyncComplexityRouter complexityRouter;

	private SeatunnelSyncService seatunnelSyncService;

	@BeforeEach
	void setUp() {
		complexityRouter = new SeatunnelSyncComplexityRouter();
		seatunnelSyncService = new SeatunnelSyncService(agentDatasourceService, datasourceService,
				seatunnelSchemaRecallService, seatunnelTableResolveService, seatunnelRelatedTableExpander,
				seatunnelConfigBuilder, syncSchemaBuilder, complexityRouter, seatunnelConfGenerateService,
				seatunnelConfPostProcessor);
		when(seatunnelRelatedTableExpander.expand(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(3));
	}

	@Test
	void generateConf_simpleSync_usesTemplate() throws Exception {
		mockMysqlAgent();
		mockRecallAndResolve("order", "order_backup", List.of());
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("order", "order_backup"));
		List<ColumnInfoBO> columns = List.of(col("id"));
		when(datasourceService.getTableColumnMetadata(1, "order")).thenReturn(columns);
		when(datasourceService.getTableColumnMetadata(1, "order_backup")).thenReturn(columns);
		when(seatunnelConfigBuilder.build(any(), anyString(), anyString())).thenReturn("env { job.mode = \"BATCH\" }");

		SeatunnelTaskResult result = seatunnelSyncService.generateConf(1L, "把 order 同步到 order_backup", "(无)");

		assertEquals(SeatunnelTaskResult.Type.OK, result.getType());
		assertEquals(SeatunnelTaskResult.GenerationMode.TEMPLATE, result.getGenerationMode());
		verify(seatunnelConfigBuilder).build(any(), anyString(), anyString());
		verify(seatunnelConfGenerateService, never()).generate(any());
	}

	@Test
	void generateConf_complexSync_usesLlm() throws Exception {
		mockMysqlAgent();
		mockRecallAndResolve("order", "order_backup", List.of());
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("order", "order_backup"));
		List<ColumnInfoBO> columns = List.of(col("id"));
		when(datasourceService.getTableColumnMetadata(1, "order")).thenReturn(columns);
		when(datasourceService.getTableColumnMetadata(1, "order_backup")).thenReturn(columns);
		when(syncSchemaBuilder.build(any())).thenReturn(new SchemaDTO());
		when(seatunnelConfGenerateService.generate(any(SeatunnelConfGenerationDTO.class)))
			.thenReturn("env {} source {} sink {}");
		when(seatunnelConfPostProcessor.injectCredentials(anyString(), any())).thenReturn("env {} source {} sink {}");

		SeatunnelTaskResult result = seatunnelSyncService.generateConf(1L,
				"把 order 同步到 order_backup，排除 status=0 的数据", "(无)");

		assertEquals(SeatunnelTaskResult.Type.OK, result.getType());
		assertEquals(SeatunnelTaskResult.GenerationMode.LLM, result.getGenerationMode());
		verify(seatunnelConfGenerateService).generate(any(SeatunnelConfGenerationDTO.class));
		verify(seatunnelConfigBuilder, never()).build(any(), anyString(), anyString());
	}

	@Test
	void generateConf_emptyRecall_returnsError() {
		mockMysqlAgent();
		when(seatunnelSchemaRecallService.recall(eq(1), eq(1L), anyString()))
			.thenReturn(SeatunnelSchemaRecallResult.builder().build());

		SeatunnelTaskResult result = seatunnelSyncService.generateConf(1L, "sync order to backup", "(无)");

		assertEquals(SeatunnelTaskResult.Type.ERROR, result.getType());
		assertTrue(result.getMessage().contains("未检索到相关数据表"));
	}

	@Test
	void generateConf_sourceNotExists_returnsError() throws Exception {
		mockMysqlAgent();
		mockRecallAndResolve("order", "order_backup", List.of());
		when(datasourceService.getDatasourceTables(1)).thenReturn(List.of("order_backup"));

		SeatunnelTaskResult result = seatunnelSyncService.generateConf(1L, "sync order to order_backup", "(无)");

		assertEquals(SeatunnelTaskResult.Type.ERROR, result.getType());
		assertTrue(result.getMessage().contains("源表 order 不存在"));
	}

	private void mockRecallAndResolve(String source, String target, List<String> related) {
		SchemaDTO schemaDTO = new SchemaDTO();
		Document tableDoc = new Document("table", java.util.Map.of("name", source));
		when(seatunnelSchemaRecallService.recall(eq(1), eq(1L), anyString()))
			.thenReturn(SeatunnelSchemaRecallResult.builder()
				.schemaDTO(schemaDTO)
				.tableDocuments(List.of(tableDoc))
				.recalledTableNames(List.of(source))
				.build());
		SeatunnelTableResolveDTO resolved = new SeatunnelTableResolveDTO();
		resolved.setSourceTable(source);
		resolved.setTargetTable(target);
		resolved.setRelatedTables(related);
		when(seatunnelTableResolveService.resolve(anyString(), anyString(), eq(schemaDTO), any()))
			.thenReturn(resolved);
	}

	private void mockMysqlAgent() {
		AgentDatasource agentDatasource = new AgentDatasource();
		agentDatasource.setDatasourceId(1);
		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(agentDatasource);
		Datasource datasource = new Datasource();
		datasource.setId(1);
		datasource.setType("mysql");
		when(datasourceService.getDatasourceById(1)).thenReturn(datasource);
		when(datasourceService.getDbConfig(any())).thenReturn(DbConfigBO.builder()
			.url("jdbc:mysql://127.0.0.1:3306/testdb")
			.username("root")
			.password("secret")
			.schema("testdb")
			.build());
	}

	private ColumnInfoBO col(String name) {
		return ColumnInfoBO.builder().name(name).type("VARCHAR(255)").build();
	}

}
