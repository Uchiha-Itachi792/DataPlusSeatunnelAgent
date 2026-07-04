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
package com.alibaba.cloud.ai.dataagent.service.sync.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.DataPointer;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncTask;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;

@ExtendWith(MockitoExtension.class)
class TableCopyCompilerTest {

	@Mock
	private CredentialInjector credentialInjector;

	@Mock
	private com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService syncCatalogService;

	private TableCopyCompiler compiler;

	@BeforeEach
	void setUp() {
		SeatunnelProperties properties = new SeatunnelProperties();
		compiler = new TableCopyCompiler(credentialInjector, syncCatalogService, properties);
	}

	@Test
	void compile_containsJdbcSourceSinkAndTables() {
		DbConfigBO dbConfig = DbConfigBO.builder()
			.url("jdbc:mysql://127.0.0.1:3306/testdb")
			.username("root")
			.password("secret")
			.schema("testdb")
			.build();
		when(syncCatalogService.resolveDatasourceId("ds_1")).thenReturn(java.util.Optional.of(1));
		when(credentialInjector.resolveDbConfig("ds_1")).thenReturn(dbConfig);
		when(credentialInjector.escapeHocon(org.mockito.ArgumentMatchers.anyString()))
			.thenAnswer(inv -> inv.getArgument(0));

		LlmSyncTask task = LlmSyncTask.builder()
			.syncKind(SyncKind.TABLE_COPY)
			.source(DataPointer.builder().ref("ds_1").object("orders").build())
			.sink(DataPointer.builder().ref("ds_1").object("orders_backup").build())
			.build();

		CompiledJobConfig result = compiler.compile(task);
		String conf = result.getJobConfig();

		assertTrue(conf.contains("source"));
		assertTrue(conf.contains("sink"));
		assertTrue(conf.contains("jdbc:mysql://127.0.0.1:3306/testdb"));
		assertTrue(conf.contains("SELECT * FROM `orders`"));
		assertTrue(conf.contains("table = \"orders_backup\""));
		assertTrue(conf.contains("job.mode = \"BATCH\""));
	}

}
