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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;

@ExtendWith(MockitoExtension.class)
class CredentialInjectorTest {

	@Mock
	private SyncCatalogService syncCatalogService;

	@Mock
	private DatasourceService datasourceService;

	private CredentialInjector credentialInjector;

	@BeforeEach
	void setUp() {
		credentialInjector = new CredentialInjector(syncCatalogService, datasourceService);
	}

	@Test
	void resolveDbConfig_returnsConfigForRef() {
		when(syncCatalogService.resolveDatasourceId("ds_1")).thenReturn(Optional.of(1));
		Datasource datasource = Datasource.builder().id(1).type("mysql").build();
		DbConfigBO dbConfig = DbConfigBO.builder()
			.url("jdbc:mysql://localhost/test")
			.username("u")
			.password("p")
			.schema("test")
			.build();
		when(datasourceService.getDatasourceById(1)).thenReturn(datasource);
		when(datasourceService.getDbConfig(datasource)).thenReturn(dbConfig);

		DbConfigBO result = credentialInjector.resolveDbConfig("ds_1");

		assertEquals("jdbc:mysql://localhost/test", result.getUrl());
	}

	@Test
	void escapeHocon_escapesQuotes() {
		assertTrue(credentialInjector.escapeHocon("a\"b").contains("\\\""));
	}

}
