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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveRequest;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveResult;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogEntry;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.CredentialInjector;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.SingleStepCompilerRegistry;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.TableCopyCompiler;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SyncPlanConsistencyValidator;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SystemResolveValidator;

@ExtendWith(MockitoExtension.class)
class SyncOrchestratorImplTest {

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private SyncCatalogService syncCatalogService;

	@Mock
	private CredentialInjector credentialInjector;

	private SyncOrchestratorImpl orchestrator;

	@BeforeEach
	void setUp() {
		SeatunnelProperties properties = new SeatunnelProperties();
		MockSyncLayerResolver mockSyncLayerResolver = new MockSyncLayerResolver(agentDatasourceService);
		SyncPlanAssembler assembler = new SyncPlanAssembler(new SystemResolveValidator(syncCatalogService),
				new SyncPlanConsistencyValidator(syncCatalogService));
		TableCopyCompiler tableCopyCompiler = new TableCopyCompiler(credentialInjector, syncCatalogService, properties);
		SingleStepCompilerRegistry registry = new SingleStepCompilerRegistry(tableCopyCompiler);
		orchestrator = new SyncOrchestratorImpl(new SyncModeRouter(), mockSyncLayerResolver, assembler, registry,
				properties);
	}

	@Test
	void resolve_tableCopySuccess() {
		AgentDatasource active = new AgentDatasource(1L, 1);
		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(active);
		when(syncCatalogService.listRefs(1L)).thenReturn(List.of(SyncCatalogEntry.builder()
			.ref("ds_1")
			.datasourceId(1)
			.connectorType("JDBC")
			.description("test")
			.build()));
		when(syncCatalogService.existsRef("ds_1")).thenReturn(true);
		when(syncCatalogService.objectExists(eq(1L), eq("ds_1"), any())).thenReturn(true);
		when(syncCatalogService.resolveDatasourceId("ds_1")).thenReturn(Optional.of(1));
		DbConfigBO dbConfig = DbConfigBO.builder()
			.url("jdbc:mysql://127.0.0.1:3306/testdb")
			.username("root")
			.password("secret")
			.schema("testdb")
			.build();
		when(credentialInjector.resolveDbConfig("ds_1")).thenReturn(dbConfig);
		when(credentialInjector.escapeHocon(org.mockito.ArgumentMatchers.anyString()))
			.thenAnswer(inv -> inv.getArgument(0));

		SyncResolveRequest request = SyncResolveRequest.builder()
			.agentId(1L)
			.userInput("同步 orders 到 orders_backup")
			.canonicalQuery("同步 orders 到 orders_backup")
			.build();

		SyncResolveResult result = orchestrator.resolve(request);

		assertEquals(SyncResolveResult.Type.SUCCESS, result.getType());
		assertTrue(result.getJobConfig().contains("BATCH"));
		assertTrue(result.getJobConfig().contains("SELECT * FROM `orders`"));
		assertEquals("orders_backup", result.getPlan().getSink().getObject());
	}

}
