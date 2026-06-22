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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;

@ExtendWith(MockitoExtension.class)
class SeatunnelConfigBuilderTest {

	@Mock
	private DatasourceService datasourceService;

	private SeatunnelConfigBuilder builder;

	@BeforeEach
	void setUp() {
		builder = new SeatunnelConfigBuilder(datasourceService);
	}

	@Test
	void build_containsJdbcSourceSinkAndTables() {
		Datasource datasource = Datasource.builder()
			.id(1)
			.host("127.0.0.1")
			.port(3306)
			.databaseName("testdb")
			.username("root")
			.password("secret")
			.type("mysql")
			.build();
		DbConfigBO dbConfig = DbConfigBO.builder()
			.url("jdbc:mysql://127.0.0.1:3306/testdb")
			.username("root")
			.password("secret")
			.schema("testdb")
			.build();
		when(datasourceService.getDbConfig(datasource)).thenReturn(dbConfig);

		String conf = builder.build(datasource, "orders", "orders_backup");

		assertTrue(conf.contains("source"));
		assertTrue(conf.contains("sink"));
		assertTrue(conf.contains("jdbc:mysql://127.0.0.1:3306/testdb"));
		assertTrue(conf.contains("SELECT * FROM `orders`"));
		assertTrue(conf.contains("table = \"orders_backup\""));
	}

}
