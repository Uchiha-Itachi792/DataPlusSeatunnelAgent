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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmObjectResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncIntent;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncSql;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncTask;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSystemResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.MockSyncLayerResult;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.enums.WriteMode;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SyncPlanConsistencyValidator;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SystemResolveValidator;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

class SyncGoldenFixtureTest {

	private final ObjectMapper mapper = JsonUtil.getObjectMapper();

	@Test
	void loadL1AndL2Golden_assemblePlan() throws Exception {
		LlmSystemResolve system = read("sync/golden/l1-system.json", LlmSystemResolve.class);
		LlmObjectResolve object = read("sync/golden/l2-object.json", LlmObjectResolve.class);
		assertEquals("ds_1", system.getSourceRef());
		assertEquals("orders", object.getSource().getObject());

		MockSyncLayerResult layers = MockSyncLayerResult.builder()
			.system(system)
			.object(object)
			.intent(LlmSyncIntent.builder().syncKind(SyncKind.TABLE_COPY).writeMode(WriteMode.APPEND).build())
			.sql(LlmSyncSql.builder().scope("single").sql(null).build())
			.build();

		SyncPlanAssembler assembler = new SyncPlanAssembler(mock(SystemResolveValidator.class),
				mock(SyncPlanConsistencyValidator.class));
		SyncPlanAssembler.AssemblyResult result = assembler.assemble(1L, layers);
		assertFalse(result.needsClarify());
		LlmSyncTask plan = result.plan();
		assertNotNull(plan);
		assertEquals(SyncKind.TABLE_COPY, plan.getSyncKind());
		assertEquals("orders_backup", plan.getSink().getObject());
	}

	private <T> T read(String classpath, Class<T> type) throws Exception {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpath)) {
			assertNotNull(in, "missing " + classpath);
			String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return mapper.readValue(json, type);
		}
	}

}
