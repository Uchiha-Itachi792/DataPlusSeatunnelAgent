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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;

class SeatunnelConfPostProcessorTest {

	private SeatunnelConfPostProcessor postProcessor;

	@BeforeEach
	void setUp() {
		postProcessor = new SeatunnelConfPostProcessor();
	}

	@Test
	void injectCredentials_replacesAllPlaceholders() {
		String raw = """
				source {
				  Jdbc {
				    url = "__JDBC_URL__"
				    user = "__JDBC_USER__"
				    password = "__JDBC_PASSWORD__"
				  }
				}
				sink {
				  Jdbc {
				    database = "__JDBC_DATABASE__"
				  }
				}
				""";
		DbConfigBO dbConfig = DbConfigBO.builder()
			.url("jdbc:mysql://127.0.0.1:3306/testdb")
			.username("root")
			.password("pass\"word")
			.schema("testdb")
			.build();

		String result = postProcessor.injectCredentials(raw, dbConfig);

		assertTrue(result.contains("jdbc:mysql://127.0.0.1:3306/testdb"));
		assertTrue(result.contains("root"));
		assertTrue(result.contains("pass\\\"word"));
		assertTrue(result.contains("testdb"));
		assertTrue(!result.contains("__JDBC_URL__"));
		assertTrue(!result.contains("__JDBC_USER__"));
		assertTrue(!result.contains("__JDBC_PASSWORD__"));
		assertTrue(!result.contains("__JDBC_DATABASE__"));
	}

	@Test
	void escapeHocon_escapesBackslashAndQuote() {
		assertEquals("a\\\\b", SeatunnelConfPostProcessor.escapeHocon("a\\b"));
		assertEquals("say \\\"hi\\\"", SeatunnelConfPostProcessor.escapeHocon("say \"hi\""));
	}

}
