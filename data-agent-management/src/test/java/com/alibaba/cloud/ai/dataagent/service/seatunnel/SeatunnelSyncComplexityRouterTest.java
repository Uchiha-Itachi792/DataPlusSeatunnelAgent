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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeatunnelSyncComplexityRouterTest {

	private SeatunnelSyncComplexityRouter router;

	@BeforeEach
	void setUp() {
		router = new SeatunnelSyncComplexityRouter();
	}

	@Test
	void resolve_simpleFullTableSync_returnsTemplate() {
		SeatunnelSyncMode mode = router.resolve("把 order 表同步到 order_backup", List.of(), true);
		assertEquals(SeatunnelSyncMode.TEMPLATE, mode);
	}

	@Test
	void resolve_withRelatedTables_returnsLlm() {
		SeatunnelSyncMode mode = router.resolve("将 a 同步到 b", List.of("c"), true);
		assertEquals(SeatunnelSyncMode.LLM, mode);
	}

	@Test
	void resolve_targetTableNotExists_returnsLlm() {
		SeatunnelSyncMode mode = router.resolve("把 order 同步到 order_new", List.of(), false);
		assertEquals(SeatunnelSyncMode.LLM, mode);
	}

	@Test
	void resolve_filterKeyword_returnsLlm() {
		SeatunnelSyncMode mode = router.resolve("把 order 同步到 order_backup，排除 status=0 的数据", List.of(), true);
		assertEquals(SeatunnelSyncMode.LLM, mode);
	}

	@Test
	void resolve_joinKeyword_returnsLlm() {
		SeatunnelSyncMode mode = router.resolve("将 order 和 products JOIN 后同步到 order_backup", List.of(), true);
		assertEquals(SeatunnelSyncMode.LLM, mode);
	}

	@Test
	void resolve_cdcKeyword_returnsLlm() {
		SeatunnelSyncMode mode = router.resolve("用 CDC 同步 order 到 order_backup", List.of(), true);
		assertEquals(SeatunnelSyncMode.LLM, mode);
	}

	@Test
	void resolve_kafkaConnector_returnsLlm() {
		SeatunnelSyncMode mode = router.resolve("从 Kafka 同步到 order_backup", List.of(), true);
		assertEquals(SeatunnelSyncMode.LLM, mode);
	}

}
