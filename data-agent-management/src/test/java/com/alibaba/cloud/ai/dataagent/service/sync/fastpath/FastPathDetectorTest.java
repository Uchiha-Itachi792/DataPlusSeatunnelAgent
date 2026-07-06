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
package com.alibaba.cloud.ai.dataagent.service.sync.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;

@ExtendWith(MockitoExtension.class)
class FastPathDetectorTest {

	@Mock
	private SyncCatalogService syncCatalogService;

	private FastPathDetector detector;

	@BeforeEach
	void setUp() {
		SeatunnelProperties properties = new SeatunnelProperties();
		properties.getResolve().setFastPathEnabled(true);
		detector = new FastPathDetector(properties, syncCatalogService);
	}

	@Test
	void parseClearTableNames_syncCn() {
		Optional<String[]> tables = detector.parseClearTableNames("同步 orders 到 orders_backup");
		assertTrue(tables.isPresent());
		assertEquals("orders", tables.get()[0]);
		assertEquals("orders_backup", tables.get()[1]);
	}

	@Test
	void parseClearTableNames_ba() {
		Optional<String[]> tables = detector.parseClearTableNames("把 order_items 同步到 order_items_back");
		assertTrue(tables.isPresent());
		assertEquals("order_items", tables.get()[0]);
		assertEquals("order_items_back", tables.get()[1]);
	}

	@Test
	void hasComplexSemantics_filter() {
		assertTrue(detector.hasComplexSemantics("同步 orders 到 backup，不要已删除的"));
		assertFalse(detector.hasComplexSemantics("同步 orders 到 orders_backup"));
	}

	@Test
	void detectTableCopyFastPath_success() {
		when(syncCatalogService.objectExists(eq(1L), eq("ds_1"), eq("orders"))).thenReturn(true);
		when(syncCatalogService.objectExists(eq(1L), eq("ds_1"), eq("orders_backup"))).thenReturn(true);
		Optional<String[]> tables = detector.detectTableCopyFastPath(1L, "同步 orders 到 orders_backup", "ds_1", "ds_1");
		assertTrue(tables.isPresent());
	}

	@Test
	void detectTableCopyFastPath_complexSkipped() {
		Optional<String[]> tables = detector.detectTableCopyFastPath(1L, "同步 orders 到 backup，过滤 status=0", "ds_1",
				"ds_1");
		assertTrue(tables.isEmpty());
	}

}
