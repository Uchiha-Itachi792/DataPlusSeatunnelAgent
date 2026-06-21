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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyncSqlValidatorTest {

	private SyncSqlValidator validator;

	@BeforeEach
	void setUp() {
		validator = new SyncSqlValidator();
	}

	@Test
	void validate_insertSql_passes() {
		validator.validate("INSERT INTO `A` (`id`) SELECT `id` FROM `order`");
	}

	@Test
	void validate_deleteThenInsert_passes() {
		validator.validate("DELETE FROM `A` WHERE status = 0; INSERT INTO `A` SELECT * FROM `order`");
	}

	@Test
	void validate_createAndInsert_passes() {
		validator.validate(
				"CREATE TABLE `A` (`id` INT PRIMARY KEY); INSERT INTO `A` (`id`) SELECT `id` FROM `order`");
	}

	@Test
	void validate_dropStatement_rejected() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> validator.validate("DROP TABLE `A`"));
		assertTrue(ex.getMessage().contains("不允许"));
	}

	@Test
	void validate_truncateStatement_rejected() {
		assertThrows(IllegalArgumentException.class, () -> validator.validate("TRUNCATE TABLE `A`"));
	}

	@Test
	void validate_emptySql_rejected() {
		assertThrows(IllegalArgumentException.class, () -> validator.validate("   "));
	}

	@Test
	void splitStatements_respectsQuotes() {
		var statements = validator.splitStatements("DELETE FROM `t` WHERE x = ';'; INSERT INTO `t` SELECT 1");
		assertEquals(2, statements.size());
		assertTrue(statements.get(0).contains("';'"));
	}

}
