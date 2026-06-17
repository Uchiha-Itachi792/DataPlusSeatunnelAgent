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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_TASK_STUB_NODE_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

class SyncTaskStubNodeTest {

	private SyncTaskStubNode syncTaskStubNode;

	@BeforeEach
	void setUp() {
		syncTaskStubNode = new SyncTaskStubNode();
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(SYNC_TASK_STUB_NODE_OUTPUT, new ReplaceStrategy());
		return state;
	}

	@Test
	void apply_returnsGeneratorUnderOutputKey() throws Exception {
		OverAllState state = createTestState();

		Map<String, Object> result = syncTaskStubNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(SYNC_TASK_STUB_NODE_OUTPUT));
		assertNotNull(result.get(SYNC_TASK_STUB_NODE_OUTPUT));
	}

}
