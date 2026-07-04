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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.dto.syncjob.DataPointer;
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

@ExtendWith(MockitoExtension.class)
class SyncPlanAssemblerTest {

	@Mock
	private SystemResolveValidator systemResolveValidator;

	@Mock
	private SyncPlanConsistencyValidator syncPlanConsistencyValidator;

	private SyncPlanAssembler assembler;

	@BeforeEach
	void setUp() {
		assembler = new SyncPlanAssembler(systemResolveValidator, syncPlanConsistencyValidator);
	}

	@Test
	void assemble_tableCopyPlan() {
		MockSyncLayerResult layers = MockSyncLayerResult.builder()
			.system(LlmSystemResolve.builder().sourceRef("ds_1").targetRef("ds_1").build())
			.object(LlmObjectResolve.builder()
				.source(DataPointer.builder().ref("ds_1").object("order_items").build())
				.sink(DataPointer.builder().ref("ds_1").object("order_items_back").build())
				.build())
			.intent(LlmSyncIntent.builder().syncKind(SyncKind.TABLE_COPY).writeMode(WriteMode.APPEND).build())
			.sql(LlmSyncSql.builder().sql(null).build())
			.build();

		SyncPlanAssembler.AssemblyResult result = assembler.assemble(1L, layers);

		assertFalse(result.needsClarify());
		LlmSyncTask plan = result.plan();
		assertNotNull(plan);
		assertEquals("1.0", plan.getSchemaVersion());
		assertEquals(SyncKind.TABLE_COPY, plan.getSyncKind());
		assertEquals("order_items", plan.getSource().getObject());
		assertNotNull(result.trace());
	}

}
