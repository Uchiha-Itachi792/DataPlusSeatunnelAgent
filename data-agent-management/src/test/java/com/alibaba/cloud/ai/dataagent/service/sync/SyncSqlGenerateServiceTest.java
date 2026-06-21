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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.dto.prompt.SyncSqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class SyncSqlGenerateServiceTest {

	@Mock
	private LlmService llmService;

	@Mock
	private Nl2SqlService nl2SqlService;

	private SyncSqlGenerateService syncSqlGenerateService;

	private SchemaDTO emptySchema;

	@BeforeEach
	void setUp() {
		syncSqlGenerateService = new SyncSqlGenerateService(llmService, nl2SqlService, new SyncSqlValidator());
		emptySchema = new SchemaDTO();
		emptySchema.setTable(new java.util.ArrayList<>());
	}

	@Test
	void generate_trimsAndValidatesSql() {
		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createResponse("sql")));
		when(llmService.blockToString(any())).thenReturn("```sql\nINSERT INTO `A` SELECT `id` FROM `order`\n```");
		when(nl2SqlService.sqlTrim(anyString())).thenReturn("INSERT INTO `A` SELECT `id` FROM `order`");

		SyncSqlGenerationDTO dto = SyncSqlGenerationDTO.builder()
			.userInput("sync")
			.multiTurn("(无)")
			.schemaDTO(emptySchema)
			.sourceTable("order")
			.targetTable("A")
			.relatedTables("无")
			.targetTableExists(true)
			.dialect("MySQL")
			.build();

		String sql = syncSqlGenerateService.generate(dto);

		assertNotNull(sql);
		assertTrue(sql.contains("INSERT INTO"));
	}

	@Test
	void generate_rejectsForbiddenSql() {
		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createResponse("sql")));
		when(llmService.blockToString(any())).thenReturn("DROP TABLE `A`");
		when(nl2SqlService.sqlTrim(anyString())).thenReturn("DROP TABLE `A`");

		SyncSqlGenerationDTO dto = SyncSqlGenerationDTO.builder()
			.userInput("sync")
			.schemaDTO(emptySchema)
			.sourceTable("order")
			.targetTable("A")
			.relatedTables("无")
			.targetTableExists(true)
			.dialect("MySQL")
			.build();

		assertThrows(IllegalArgumentException.class, () -> syncSqlGenerateService.generate(dto));
	}

}
