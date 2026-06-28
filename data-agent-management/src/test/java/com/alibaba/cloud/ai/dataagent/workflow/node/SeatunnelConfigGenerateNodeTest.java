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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskResult;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.SeatunnelSyncService;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.SeatunnelTaskService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

@ExtendWith(MockitoExtension.class)
class SeatunnelConfigGenerateNodeTest {

	@Mock
	private SeatunnelSyncService seatunnelSyncService;

	@Mock
	private SeatunnelTaskService seatunnelTaskService;

	private SeatunnelConfigGenerateNode node;

	@BeforeEach
	void setUp() {
		node = new SeatunnelConfigGenerateNode(seatunnelSyncService, seatunnelTaskService);
	}

	@Test
	void apply_returnsGeneratorUnderOutputKey() throws Exception {
		SeatunnelTaskResult result = SeatunnelTaskResult.ok("env {}", "order", "A", 1, 1);
		when(seatunnelSyncService.generateConf(anyLong(), anyString(), anyString(), anyString(), anyString()))
			.thenReturn(result);

		OverAllState state = buildState();

		Map<String, Object> output = node.apply(state);

		assertTrue(output.containsKey(SEATUNNEL_CONFIG_GENERATE_NODE_OUTPUT));
		assertNotNull(output.get(SEATUNNEL_CONFIG_GENERATE_NODE_OUTPUT));
		verify(seatunnelTaskService).save(eq(result), eq(1L));
		verify(seatunnelSyncService).generateConf(eq(1L), anyString(), anyString(), eq("canonical seatunnel sync"),
				eq("evidence text"));
	}

	@Test
	void apply_saveFailure_stillReturnsGenerator() throws Exception {
		SeatunnelTaskResult result = SeatunnelTaskResult.ok("env {}", "order", "A", 1, 1);
		when(seatunnelSyncService.generateConf(anyLong(), anyString(), anyString(), anyString(), anyString()))
			.thenReturn(result);
		doThrow(new IllegalStateException("db error")).when(seatunnelTaskService).save(eq(result), eq(1L));

		OverAllState state = buildState();

		Map<String, Object> output = node.apply(state);

		assertTrue(output.containsKey(SEATUNNEL_CONFIG_GENERATE_NODE_OUTPUT));
		assertNotNull(output.get(SEATUNNEL_CONFIG_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_withoutQueryEnhance_fallsBackToUserInput() throws Exception {
		SeatunnelTaskResult result = SeatunnelTaskResult.ok("env {}", "order", "A", 1, 1);
		when(seatunnelSyncService.generateConf(anyLong(), anyString(), anyString(), anyString(), anyString()))
			.thenReturn(result);

		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(SEATUNNEL_CONFIG_GENERATE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.updateState(Map.of(INPUT_KEY, "用 SeaTunnel 同步 order 到 A", AGENT_ID, "1", MULTI_TURN_CONTEXT, "(无)",
				EVIDENCE, "无"));

		node.apply(state);

		verify(seatunnelSyncService).generateConf(eq(1L), eq("用 SeaTunnel 同步 order 到 A"), eq("(无)"),
				eq("用 SeaTunnel 同步 order 到 A"), eq("无"));
	}

	private OverAllState buildState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(SEATUNNEL_CONFIG_GENERATE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		QueryEnhanceOutputDTO queryEnhance = new QueryEnhanceOutputDTO();
		queryEnhance.setCanonicalQuery("canonical seatunnel sync");
		queryEnhance.setExpandedQueries(java.util.List.of("expanded"));
		state.updateState(Map.of(INPUT_KEY, "用 SeaTunnel 同步 order 到 A", AGENT_ID, "1", MULTI_TURN_CONTEXT, "(无)",
				QUERY_ENHANCE_NODE_OUTPUT, queryEnhance, EVIDENCE, "evidence text"));
		return state;
	}

}
