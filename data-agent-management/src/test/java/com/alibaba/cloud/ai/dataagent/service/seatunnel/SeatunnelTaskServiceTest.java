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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskResult;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.DataPointer;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncTask;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.ResolveTrace;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveResult;
import com.alibaba.cloud.ai.dataagent.entity.SeatunnelTask;
import com.alibaba.cloud.ai.dataagent.enums.SeatunnelTaskExecStatus;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.mapper.SeatunnelTaskMapper;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.gateway.SeatunnelGatewayClient;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;

@ExtendWith(MockitoExtension.class)
class SeatunnelTaskServiceTest {

	@Mock
	private SeatunnelTaskMapper seatunnelTaskMapper;

	@Mock
	private SeatunnelGatewayClient seatunnelGatewayClient;

	@Mock
	private SyncCatalogService syncCatalogService;

	private SeatunnelTaskService seatunnelTaskService;

	@BeforeEach
	void setUp() {
		seatunnelTaskService = new SeatunnelTaskService(seatunnelTaskMapper, seatunnelGatewayClient,
				syncCatalogService);
	}

	@Test
	void save_syncResolveResult_insertsPendingRecord() {
		LlmSyncTask plan = LlmSyncTask.builder()
			.syncKind(SyncKind.TABLE_COPY)
			.source(DataPointer.builder().ref("ds_1").object("s").build())
			.sink(DataPointer.builder().ref("ds_1").object("t").build())
			.build();
		SyncResolveResult result = SyncResolveResult.success(plan, "env {}", ResolveTrace.builder().build());
		when(syncCatalogService.resolveDatasourceId("ds_1")).thenReturn(java.util.Optional.of(1));
		when(seatunnelTaskMapper.insert(any(SeatunnelTask.class))).thenAnswer(invocation -> {
			SeatunnelTask record = invocation.getArgument(0);
			record.setId(10);
			return 1;
		});

		seatunnelTaskService.save(result, 1L);

		ArgumentCaptor<SeatunnelTask> captor = ArgumentCaptor.forClass(SeatunnelTask.class);
		verify(seatunnelTaskMapper).insert(captor.capture());
		SeatunnelTask saved = captor.getValue();
		assertEquals("SINGLE", saved.getSyncMode());
		assertNotNull(saved.getSyncPlan());
		assertNotNull(saved.getResolveTrace());
	}

	@Test
	void save_validResult_insertsPendingRecord() {
		SeatunnelTaskResult result = SeatunnelTaskResult.ok("env {}", "s", "t", 1, 1);
		when(seatunnelTaskMapper.insert(any(SeatunnelTask.class))).thenAnswer(invocation -> {
			SeatunnelTask record = invocation.getArgument(0);
			record.setId(10);
			return 1;
		});

		seatunnelTaskService.save(result, 1L);

		ArgumentCaptor<SeatunnelTask> captor = ArgumentCaptor.forClass(SeatunnelTask.class);
		verify(seatunnelTaskMapper).insert(captor.capture());
		SeatunnelTask saved = captor.getValue();
		assertEquals(1, saved.getAgentId());
		assertEquals(1, saved.getSourceDatasourceId());
		assertEquals(1, saved.getSinkDatasourceId());
		assertEquals("s", saved.getSourceTable());
		assertEquals("t", saved.getTargetTable());
		assertEquals(SeatunnelTaskExecStatus.PENDING.getValue(), saved.getExecStatus());
	}

	@Test
	void save_errorResult_throws() {
		SeatunnelTaskResult result = SeatunnelTaskResult.error("parse failed");
		assertThrows(IllegalArgumentException.class, () -> seatunnelTaskService.save(result, 1L));
	}

	@Test
	void list_mapsStatusLabel() {
		SeatunnelTask record = SeatunnelTask.builder()
			.id(1)
			.agentId(1)
			.sourceDatasourceId(1)
			.sinkDatasourceId(1)
			.sourceTable("a")
			.targetTable("b")
			.jobConfig("env {}")
			.execStatus(SeatunnelTaskExecStatus.PENDING.getValue())
			.createTime(LocalDateTime.now())
			.build();
		when(seatunnelTaskMapper.selectAll(null)).thenReturn(List.of(record));

		List<SeatunnelTaskDTO> list = seatunnelTaskService.list(null);

		assertEquals(1, list.size());
		assertEquals("未执行", list.get(0).getExecStatusLabel());
	}

	@Test
	void ignore_pendingRecord_updatesStatus() {
		SeatunnelTask record = pendingRecord();
		when(seatunnelTaskMapper.selectById(1)).thenReturn(record);

		seatunnelTaskService.ignore(1);

		ArgumentCaptor<SeatunnelTask> captor = ArgumentCaptor.forClass(SeatunnelTask.class);
		verify(seatunnelTaskMapper).updateStatus(captor.capture());
		assertEquals(SeatunnelTaskExecStatus.IGNORED.getValue(), captor.getValue().getExecStatus());
	}

	@Test
	void execute_gatewayNotConfigured_updatesFailedStatus() {
		SeatunnelTask record = pendingRecord();
		when(seatunnelTaskMapper.selectById(1)).thenReturn(record);
		when(seatunnelGatewayClient.submit(any())).thenThrow(new IllegalStateException("Gateway 未配置"));

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seatunnelTaskService.execute(1));
		assertNotNull(ex.getMessage());
		assertTrue(ex.getMessage().contains("Gateway"));

		ArgumentCaptor<SeatunnelTask> captor = ArgumentCaptor.forClass(SeatunnelTask.class);
		verify(seatunnelTaskMapper, org.mockito.Mockito.times(2)).updateStatus(captor.capture());
		assertEquals(SeatunnelTaskExecStatus.FAILED.getValue(), captor.getAllValues().get(1).getExecStatus());
	}

	private SeatunnelTask pendingRecord() {
		return SeatunnelTask.builder()
			.id(1)
			.agentId(1)
			.sourceDatasourceId(1)
			.sinkDatasourceId(1)
			.sourceTable("order")
			.targetTable("A")
			.jobConfig("env { parallelism = 1 }")
			.execStatus(SeatunnelTaskExecStatus.PENDING.getValue())
			.build();
	}

}
