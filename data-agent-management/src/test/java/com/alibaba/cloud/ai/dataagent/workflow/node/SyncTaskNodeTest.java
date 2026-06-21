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
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
import com.alibaba.cloud.ai.dataagent.service.sync.TableSyncService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

@ExtendWith(MockitoExtension.class)
class SyncTaskNodeTest {

	@Mock
	private TableSyncService tableSyncService;

	private SyncTaskNode syncTaskNode;

	@BeforeEach
	void setUp() {
		syncTaskNode = new SyncTaskNode(tableSyncService);
	}

	@Test
	void apply_returnsGeneratorUnderOutputKey() throws Exception {
		when(tableSyncService.generateSyncSql(anyLong(), anyString(), anyString()))
			.thenReturn(SyncTaskResult.insertSql("INSERT INTO `A` SELECT * FROM `order`;"));

		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(SYNC_TASK_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		state.updateState(Map.of(INPUT_KEY, "sync order to A", AGENT_ID, "1", MULTI_TURN_CONTEXT, "(无)"));

		Map<String, Object> result = syncTaskNode.apply(state);

		assertTrue(result.containsKey(SYNC_TASK_NODE_OUTPUT));
		assertNotNull(result.get(SYNC_TASK_NODE_OUTPUT));
	}

}
