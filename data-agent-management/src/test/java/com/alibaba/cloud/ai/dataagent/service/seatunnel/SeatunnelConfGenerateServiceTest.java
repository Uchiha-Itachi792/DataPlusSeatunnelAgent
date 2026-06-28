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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.alibaba.cloud.ai.dataagent.dto.prompt.SeatunnelConfGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeatunnelConfGenerateServiceTest {

	@Mock
	private LlmService llmService;

	private SeatunnelConfGenerateService generateService;

	private SchemaDTO emptySchema;

	@BeforeEach
	void setUp() {
		generateService = new SeatunnelConfGenerateService(llmService, new SeatunnelConfValidator());
		emptySchema = new SchemaDTO();
		emptySchema.setTable(new java.util.ArrayList<>());
	}

	@Test
	void generate_trimsAndValidatesConf() {
		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createResponse("conf")));
		when(llmService.blockToString(any())).thenReturn("""
				```hocon
				env { job.mode = "BATCH" }
				source { Jdbc { url = "__JDBC_URL__" password = "__JDBC_PASSWORD__" query = "SELECT * FROM orders" } }
				sink { Jdbc { url = "__JDBC_URL__" } }
				```
				""");

		SeatunnelConfGenerationDTO dto = SeatunnelConfGenerationDTO.builder()
			.userInput("sync with filter")
			.multiTurn("(无)")
			.schemaDTO(emptySchema)
			.sourceTable("order")
			.targetTable("A")
			.relatedTables("products")
			.targetTableExists(true)
			.build();

		String conf = generateService.generate(dto);

		assertNotNull(conf);
		assertTrue(conf.contains("source"));
		assertTrue(conf.contains("sink"));
	}

	@Test
	void generate_rejectsForbiddenConnector() {
		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createResponse("conf")));
		when(llmService.blockToString(any())).thenReturn("""
				env { job.mode = "BATCH" }
				source { Kafka { topic = "orders" } }
				sink { Jdbc { url = "__JDBC_URL__" } }
				""");

		SeatunnelConfGenerationDTO dto = SeatunnelConfGenerationDTO.builder()
			.userInput("sync")
			.schemaDTO(emptySchema)
			.sourceTable("order")
			.targetTable("A")
			.relatedTables("无")
			.targetTableExists(true)
			.build();

		assertThrows(IllegalArgumentException.class, () -> generateService.generate(dto));
	}

}
