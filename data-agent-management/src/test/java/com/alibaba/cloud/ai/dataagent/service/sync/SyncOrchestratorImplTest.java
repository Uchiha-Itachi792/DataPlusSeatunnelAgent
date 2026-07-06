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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.alibaba.cloud.ai.dataagent.enums.ResolvePhase;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.SeatunnelSchemaRecallService;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogEntry;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.CredentialInjector;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.SingleStepCompilerRegistry;
import com.alibaba.cloud.ai.dataagent.service.sync.compiler.TableCopyCompiler;
import com.alibaba.cloud.ai.dataagent.service.sync.fastpath.FastPathDetector;
import com.alibaba.cloud.ai.dataagent.service.sync.resolve.ObjectResolveService;
import com.alibaba.cloud.ai.dataagent.service.sync.resolve.SyncIntentService;
import com.alibaba.cloud.ai.dataagent.service.sync.resolve.SyncSqlService;
import com.alibaba.cloud.ai.dataagent.service.sync.resolve.SystemResolveService;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.ObjectResolveValidator;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SyncPlanConsistencyValidator;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SystemResolveValidator;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;

@ExtendWith(MockitoExtension.class)
class SyncOrchestratorImplTest {

	@Mock
	private SyncCatalogService syncCatalogService;

	@Mock
	private CredentialInjector credentialInjector;

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private SeatunnelSchemaRecallService seatunnelSchemaRecallService;

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	private SyncResolveStateStore stateStore;

	private SyncOrchestratorImpl orchestrator;

	@BeforeEach
	void setUp() {
		SeatunnelProperties properties = new SeatunnelProperties();
		properties.getResolve().setFastPathEnabled(true);
		stateStore = new SyncResolveStateStore();
		FastPathDetector fastPathDetector = new FastPathDetector(properties, syncCatalogService);
		SystemResolveService systemResolveService = new SystemResolveService(syncCatalogService, llmService,
				jsonParseUtil);
		ObjectResolveService objectResolveService = new ObjectResolveService(syncCatalogService,
				seatunnelSchemaRecallService, agentDatasourceService, fastPathDetector, llmService, jsonParseUtil);
		SyncIntentService syncIntentService = new SyncIntentService(fastPathDetector);
		SyncSqlService syncSqlService = new SyncSqlService();
		SystemResolveValidator systemResolveValidator = new SystemResolveValidator(syncCatalogService);
		SyncPlanConsistencyValidator consistencyValidator = new SyncPlanConsistencyValidator(syncCatalogService);
		ObjectResolveValidator objectResolveValidator = new ObjectResolveValidator(consistencyValidator);
		SyncPlanAssembler assembler = new SyncPlanAssembler(systemResolveValidator, consistencyValidator);
		TableCopyCompiler tableCopyCompiler = new TableCopyCompiler(credentialInjector, syncCatalogService, properties);
		SingleStepCompilerRegistry registry = new SingleStepCompilerRegistry(tableCopyCompiler);
		orchestrator = new SyncOrchestratorImpl(new SyncModeRouter(), systemResolveService, objectResolveService,
				syncIntentService, syncSqlService, fastPathDetector, systemResolveValidator, objectResolveValidator,
				assembler, registry, properties, stateStore);
	}

	@Test
	void resolve_tableCopyFastPathSuccess() {
		stubSingleRefCatalog();
		when(syncCatalogService.objectExists(eq(1L), eq("ds_1"), any())).thenReturn(true);
		stubCredentials();

		SyncResolveRequest request = SyncResolveRequest.builder()
			.agentId(1L)
			.userInput("同步 orders 到 orders_backup")
			.canonicalQuery("同步 orders 到 orders_backup")
			.threadId("t-1")
			.build();

		SyncResolveResult result = orchestrator.resolve(request);

		assertEquals(SyncResolveResult.Type.SUCCESS, result.getType());
		assertTrue(result.getJobConfig().contains("BATCH"));
		assertTrue(result.getJobConfig().contains("SELECT * FROM `orders`"));
		assertEquals("orders_backup", result.getPlan().getSink().getObject());
		assertFalse(stateStore.has("t-1"));
	}

	@Test
	void resolve_complexSemantics_clarify() {
		stubSingleRefCatalog();
		when(syncCatalogService.objectExists(eq(1L), eq("ds_1"), any())).thenReturn(true);

		SyncResolveRequest request = SyncResolveRequest.builder()
			.agentId(1L)
			.userInput("同步 orders 到 orders_backup，不要已删除的")
			.canonicalQuery("同步 orders 到 orders_backup，不要已删除的")
			.threadId("t-clarify")
			.build();

		SyncResolveResult result = orchestrator.resolve(request);

		assertEquals(SyncResolveResult.Type.CLARIFY, result.getType());
		assertTrue(stateStore.has("t-clarify"));
		assertEquals(ResolvePhase.INTENT, stateStore.get("t-clarify").get().getPendingPhase());
	}

	@Test
	void resume_afterClarify_success() {
		stubSingleRefCatalog();
		stubCredentials();

		SyncResolveRequest first = SyncResolveRequest.builder()
			.agentId(1L)
			.userInput("同步一下")
			.canonicalQuery("同步一下")
			.threadId("t-resume")
			.build();
		SyncResolveResult clarify = orchestrator.resolve(first);
		assertEquals(SyncResolveResult.Type.CLARIFY, clarify.getType());
		assertTrue(stateStore.has("t-resume"));
		assertEquals(ResolvePhase.OBJECT, stateStore.get("t-resume").get().getPendingPhase());

		when(syncCatalogService.objectExists(eq(1L), eq("ds_1"), eq("orders"))).thenReturn(true);
		when(syncCatalogService.objectExists(eq(1L), eq("ds_1"), eq("orders_backup"))).thenReturn(true);

		SyncResolveRequest second = SyncResolveRequest.builder()
			.agentId(1L)
			.userInput("orders 到 orders_backup")
			.canonicalQuery("orders 到 orders_backup")
			.threadId("t-resume")
			.build();
		SyncResolveResult result = orchestrator.resume(second, "orders 到 orders_backup");
		assertEquals(SyncResolveResult.Type.SUCCESS, result.getType());
		assertTrue(result.getJobConfig().contains("SELECT * FROM `orders`"));
		assertFalse(stateStore.has("t-resume"));
	}

	private void stubSingleRefCatalog() {
		when(syncCatalogService.listRefs(1L)).thenReturn(List.of(SyncCatalogEntry.builder()
			.ref("ds_1")
			.datasourceId(1)
			.connectorType("JDBC")
			.description("test")
			.build()));
		when(syncCatalogService.existsRef("ds_1")).thenReturn(true);
	}

	private void stubCredentials() {
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
	}

}
