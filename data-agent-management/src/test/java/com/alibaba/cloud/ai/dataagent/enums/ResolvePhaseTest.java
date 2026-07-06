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
package com.alibaba.cloud.ai.dataagent.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResolvePhaseTest {

	@Test
	void shouldRunFrom_startAtSystem_runsAllPhases() {
		assertTrue(ResolvePhase.SYSTEM.shouldRunFrom(ResolvePhase.SYSTEM));
		assertTrue(ResolvePhase.OBJECT.shouldRunFrom(ResolvePhase.SYSTEM));
		assertTrue(ResolvePhase.INTENT.shouldRunFrom(ResolvePhase.SYSTEM));
		assertTrue(ResolvePhase.SQL.shouldRunFrom(ResolvePhase.SYSTEM));
	}

	@Test
	void shouldRunFrom_resumeAtObject_skipsSystem() {
		assertFalse(ResolvePhase.SYSTEM.shouldRunFrom(ResolvePhase.OBJECT));
		assertTrue(ResolvePhase.OBJECT.shouldRunFrom(ResolvePhase.OBJECT));
		assertTrue(ResolvePhase.INTENT.shouldRunFrom(ResolvePhase.OBJECT));
		assertTrue(ResolvePhase.SQL.shouldRunFrom(ResolvePhase.OBJECT));
	}

	@Test
	void shouldRunFrom_nullStart_treatedAsSystem() {
		assertTrue(ResolvePhase.SYSTEM.shouldRunFrom(null));
		assertTrue(ResolvePhase.SQL.shouldRunFrom(null));
	}

	@Test
	void displayNameAndDescription() {
		assertEquals("L1 系统定位", ResolvePhase.SYSTEM.getDisplayName());
		assertEquals("确定 sourceRef / targetRef（从哪个数据源读、写到哪个数据源）",
				ResolvePhase.SYSTEM.getDescription());
		assertEquals("L4 SQL", ResolvePhase.SQL.getDisplayName());
	}

}
