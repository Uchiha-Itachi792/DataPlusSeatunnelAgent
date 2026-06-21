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
package com.alibaba.cloud.ai.dataagent.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.dataagent.enums.DatabaseDialectEnum;

class SqlExecutorTest {

	@Test
	void executeUpdate_mysqlDdl_returnsZero() throws SQLException {
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		Statement statement = mock(Statement.class);

		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getDatabaseProductName()).thenReturn(DatabaseDialectEnum.MYSQL.code);
		when(connection.createStatement()).thenReturn(statement);
		when(statement.execute("CREATE TABLE t (id INT);")).thenReturn(false);
		when(statement.getUpdateCount()).thenReturn(0);

		int affected = SqlExecutor.executeUpdate(connection, "demo", "CREATE TABLE t (id INT);");

		assertEquals(0, affected);
		verify(statement).execute("use `demo`;");
		verify(statement).execute("CREATE TABLE t (id INT);");
	}

	@Test
	void executeUpdate_mysqlInsert_returnsAffectedRows() throws SQLException {
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		Statement statement = mock(Statement.class);

		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getDatabaseProductName()).thenReturn(DatabaseDialectEnum.MYSQL.code);
		when(connection.createStatement()).thenReturn(statement);
		when(statement.execute(anyString())).thenReturn(false);
		when(statement.getUpdateCount()).thenReturn(5);

		int affected = SqlExecutor.executeUpdate(connection, "demo", "INSERT INTO t SELECT * FROM s");

		assertEquals(5, affected);
	}

	@Test
	void executeScript_runsMultipleStatements() throws SQLException {
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		Statement statement = mock(Statement.class);

		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getDatabaseProductName()).thenReturn(DatabaseDialectEnum.MYSQL.code);
		when(connection.createStatement()).thenReturn(statement);
		when(statement.execute(anyString())).thenReturn(false);
		when(statement.getUpdateCount()).thenReturn(1);

		SqlExecutor.executeScript(connection, "demo",
				"DELETE FROM `A` WHERE status = 0; INSERT INTO `A` SELECT `id` FROM `order`");

		verify(statement).execute("DELETE FROM `A` WHERE status = 0");
		verify(statement).execute("INSERT INTO `A` SELECT `id` FROM `order`");
	}

	@Test
	void splitStatements_splitsOnSemicolonOutsideQuotes() {
		var statements = SqlExecutor.splitStatements("DELETE FROM t; INSERT INTO t SELECT 1");
		assertEquals(2, statements.size());
	}

}
