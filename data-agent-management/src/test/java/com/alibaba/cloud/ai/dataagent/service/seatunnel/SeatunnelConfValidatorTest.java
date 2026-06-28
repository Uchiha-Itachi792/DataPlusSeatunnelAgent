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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeatunnelConfValidatorTest {

	private SeatunnelConfValidator validator;

	@BeforeEach
	void setUp() {
		validator = new SeatunnelConfValidator();
	}

	@Test
	void validate_validConf_passes() {
		String conf = """
				env {
				  parallelism = 1
				  job.mode = "BATCH"
				}
				source {
				  Jdbc {
				    url = "__JDBC_URL__"
				    user = "__JDBC_USER__"
				    password = "__JDBC_PASSWORD__"
				    query = "SELECT id FROM orders"
				  }
				}
				sink {
				  Jdbc {
				    url = "__JDBC_URL__"
				    user = "__JDBC_USER__"
				    password = "__JDBC_PASSWORD__"
				    database = "__JDBC_DATABASE__"
				    table = "orders_backup"
				  }
				}
				""";
		assertDoesNotThrow(() -> validator.validate(conf));
	}

	@Test
	void validate_missingSink_throws() {
		String conf = """
				env { job.mode = "BATCH" }
				source { Jdbc { url = "__JDBC_URL__" } }
				""";
		assertThrows(IllegalArgumentException.class, () -> validator.validate(conf));
	}

	@Test
	void validate_kafkaConnector_throws() {
		String conf = """
				env { job.mode = "BATCH" }
				source { Kafka { topic = "orders" } }
				sink { Jdbc { url = "__JDBC_URL__" } }
				""";
		assertThrows(IllegalArgumentException.class, () -> validator.validate(conf));
	}

	@Test
	void validate_streamingMode_throws() {
		String conf = """
				env { job.mode = "STREAMING" }
				source { Jdbc { url = "__JDBC_URL__" } }
				sink { Jdbc { url = "__JDBC_URL__" } }
				""";
		assertThrows(IllegalArgumentException.class, () -> validator.validate(conf));
	}

	@Test
	void validate_plaintextPassword_throws() {
		String conf = """
				env { job.mode = "BATCH" }
				source { Jdbc { password = "secret123" } }
				sink { Jdbc { url = "__JDBC_URL__" } }
				""";
		assertThrows(IllegalArgumentException.class, () -> validator.validate(conf));
	}

	@Test
	void validate_emptySourceQuery_throws() {
		String conf = """
				env { job.mode = "BATCH" }
				source { Jdbc { url = "__JDBC_URL__" query = "" } }
				sink { Jdbc { url = "__JDBC_URL__" } }
				""";
		assertThrows(IllegalArgumentException.class, () -> validator.validate(conf));
	}

	@Test
	void validate_filterIntentWithoutWhere_throws() {
		String conf = """
				env { job.mode = "BATCH" }
				source { Jdbc { url = "__JDBC_URL__" query = "SELECT * FROM orders JOIN products ON orders.product_id = products.id" } }
				sink { Jdbc { url = "__JDBC_URL__" table = "orders_back" } }
				""";
		assertThrows(IllegalArgumentException.class,
				() -> validator.validate(conf, "排除 status=0 的数据"));
	}

	@Test
	void validate_filterIntentWithWhere_passes() {
		String conf = """
				env { job.mode = "BATCH" }
				source { Jdbc { url = "__JDBC_URL__" query = "SELECT * FROM orders WHERE status <> 0" } }
				sink { Jdbc { url = "__JDBC_URL__" table = "orders_back" } }
				""";
		assertDoesNotThrow(() -> validator.validate(conf, "排除 status=0 的数据"));
	}

}
