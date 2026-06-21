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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.dto.sync.SqlCheckDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
import com.alibaba.cloud.ai.dataagent.entity.SqlCheck;
import com.alibaba.cloud.ai.dataagent.enums.SqlCheckExecStatus;
import com.alibaba.cloud.ai.dataagent.mapper.SqlCheckMapper;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;

@ExtendWith(MockitoExtension.class)
class SqlCheckServiceTest {

	@Mock
	private SqlCheckMapper sqlCheckMapper;

	@Mock
	private DatabaseUtil databaseUtil;

	@Mock
	private DBConnectionPoolFactory dbConnectionPoolFactory;

	private SqlCheckService sqlCheckService;

	@BeforeEach
	void setUp() {
		sqlCheckService = new SqlCheckService(sqlCheckMapper, databaseUtil, dbConnectionPoolFactory);
	}

	@Test
	void save_validResult_insertsPendingRecord() {
		SyncTaskResult result = SyncTaskResult.insertSql("INSERT INTO t SELECT * FROM s", "s", "t", 1);
		when(sqlCheckMapper.insert(any(SqlCheck.class))).thenAnswer(invocation -> {
			SqlCheck record = invocation.getArgument(0);
			record.setId(10);
			return 1;
		});

		sqlCheckService.save(result, 1L);

		ArgumentCaptor<SqlCheck> captor = ArgumentCaptor.forClass(SqlCheck.class);
		verify(sqlCheckMapper).insert(captor.capture());
		SqlCheck saved = captor.getValue();
		assertEquals(1, saved.getAgentId());
		assertEquals(1, saved.getDatasourceId());
		assertEquals("s", saved.getSourceTable());
		assertEquals("t", saved.getTargetTable());
		assertEquals(SqlCheckExecStatus.PENDING.getValue(), saved.getExecStatus());
	}

	@Test
	void save_errorResult_throws() {
		SyncTaskResult result = SyncTaskResult.error("parse failed");
		assertThrows(IllegalArgumentException.class, () -> sqlCheckService.save(result, 1L));
	}

	@Test
	void list_mapsStatusLabel() {
		SqlCheck record = SqlCheck.builder()
			.id(1)
			.agentId(1)
			.datasourceId(1)
			.sourceTable("a")
			.targetTable("b")
			.syncSql("sql")
			.sqlType("INSERT_SQL")
			.execStatus(SqlCheckExecStatus.PENDING.getValue())
			.createTime(LocalDateTime.now())
			.build();
		when(sqlCheckMapper.selectAll(null)).thenReturn(List.of(record));

		List<SqlCheckDTO> list = sqlCheckService.list(null);

		assertEquals(1, list.size());
		assertEquals("未执行", list.get(0).getExecStatusLabel());
	}

	@Test
	void ignore_pendingRecord_updatesStatus() {
		SqlCheck record = pendingRecord();
		when(sqlCheckMapper.selectById(1)).thenReturn(record);

		sqlCheckService.ignore(1);

		ArgumentCaptor<SqlCheck> captor = ArgumentCaptor.forClass(SqlCheck.class);
		verify(sqlCheckMapper).updateStatus(captor.capture());
		assertEquals(SqlCheckExecStatus.IGNORED.getValue(), captor.getValue().getExecStatus());
	}

	@Test
	void execute_nonPendingRecord_throws() {
		SqlCheck record = pendingRecord();
		record.setExecStatus(SqlCheckExecStatus.SUCCESS.getValue());
		when(sqlCheckMapper.selectById(1)).thenReturn(record);

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> sqlCheckService.execute(1));
		assertNotNull(ex.getMessage());
		assertEquals(true, ex.getMessage().contains("已处理"));
	}

	@Test
	void execute_sqlFailure_updatesFailedStatus() throws Exception {
		SqlCheck record = pendingRecord();
		when(sqlCheckMapper.selectById(1)).thenReturn(record);

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setDialectType("MySQL");
		dbConfig.setSchema("testdb");
		when(databaseUtil.getAgentDbConfig(1L)).thenReturn(dbConfig);

		DBConnectionPool pool = mock(DBConnectionPool.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		Statement statement = mock(Statement.class);

		when(dbConnectionPoolFactory.getPoolByDbType("MySQL")).thenReturn(pool);
		when(pool.getConnection(dbConfig)).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getDatabaseProductName()).thenReturn("MySQL");
		when(connection.createStatement()).thenReturn(statement);
		when(statement.execute(anyString())).thenThrow(new java.sql.SQLException("syntax error"));

		assertThrows(IllegalStateException.class, () -> sqlCheckService.execute(1));

		ArgumentCaptor<SqlCheck> captor = ArgumentCaptor.forClass(SqlCheck.class);
		verify(sqlCheckMapper).updateStatus(captor.capture());
		assertEquals(SqlCheckExecStatus.FAILED.getValue(), captor.getValue().getExecStatus());
		assertEquals("syntax error", captor.getValue().getErrorMsg());
	}

	@Test
	void execute_success_updatesStatus() throws Exception {
		SqlCheck record = pendingRecord();
		when(sqlCheckMapper.selectById(1)).thenReturn(record);

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setDialectType("MySQL");
		dbConfig.setSchema("testdb");
		when(databaseUtil.getAgentDbConfig(1L)).thenReturn(dbConfig);

		DBConnectionPool pool = mock(DBConnectionPool.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		Statement statement = mock(Statement.class);

		when(dbConnectionPoolFactory.getPoolByDbType("MySQL")).thenReturn(pool);
		when(pool.getConnection(dbConfig)).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getDatabaseProductName()).thenReturn("MySQL");
		when(connection.createStatement()).thenReturn(statement);
		when(statement.execute(anyString())).thenReturn(false);
		when(statement.getUpdateCount()).thenReturn(3);

		sqlCheckService.execute(1);

		ArgumentCaptor<SqlCheck> captor = ArgumentCaptor.forClass(SqlCheck.class);
		verify(sqlCheckMapper).updateStatus(captor.capture());
		assertEquals(SqlCheckExecStatus.SUCCESS.getValue(), captor.getValue().getExecStatus());
	}

	private SqlCheck pendingRecord() {
		return SqlCheck.builder()
			.id(1)
			.agentId(1)
			.datasourceId(1)
			.sourceTable("order")
			.targetTable("A")
			.syncSql("INSERT INTO `A` SELECT * FROM `order`")
			.sqlType("INSERT_SQL")
			.execStatus(SqlCheckExecStatus.PENDING.getValue())
			.build();
	}

}
