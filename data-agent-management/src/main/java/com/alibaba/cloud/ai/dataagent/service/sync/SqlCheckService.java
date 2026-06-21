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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.dto.sync.SqlCheckDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
import com.alibaba.cloud.ai.dataagent.entity.SqlCheck;
import com.alibaba.cloud.ai.dataagent.enums.SqlCheckExecStatus;
import com.alibaba.cloud.ai.dataagent.mapper.SqlCheckMapper;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 同步 SQL 审批服务：持久化、列表查询、执行与忽略。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SqlCheckService {

	private final SqlCheckMapper sqlCheckMapper;

	private final DatabaseUtil databaseUtil;

	private final DBConnectionPoolFactory dbConnectionPoolFactory;

	/**
	 * 将生成的同步 SQL 写入审批表。
	 */
	public void save(SyncTaskResult result, Long agentId) {
		if (result.getType() == SyncTaskResult.Type.ERROR) {
			throw new IllegalArgumentException("错误类型的同步结果无需保存");
		}
		if (!StringUtils.hasText(result.getSql())) {
			throw new IllegalStateException("同步 SQL 为空，无法保存审批记录");
		}

		SqlCheck record = SqlCheck.builder()
			.agentId(agentId.intValue())
			.datasourceId(result.getDatasourceId())
			.sourceTable(result.getSourceTable())
			.targetTable(result.getTargetTable())
			.syncSql(result.getSql())
			.sqlType(result.getType().name())
			.execStatus(SqlCheckExecStatus.PENDING.getValue())
			.build();

		int rows = sqlCheckMapper.insert(record);
		if (rows <= 0) {
			throw new IllegalStateException("保存审批记录失败，请稍后重试");
		}
		log.info("Saved sql_check record id={} for agent={}", record.getId(), agentId);
	}

	public List<SqlCheckDTO> list(String status) {
		return sqlCheckMapper.selectAll(status).stream().map(this::toDto).toList();
	}

	/**
	 * 在 Agent 关联数据源上执行同步 SQL。
	 */
	public void execute(Integer id) {
		SqlCheck record = requirePendingRecord(id);
		try {
			DbConfigBO dbConfig = databaseUtil.getAgentDbConfig(record.getAgentId().longValue());
			DBConnectionPool pool = dbConnectionPoolFactory.getPoolByDbType(dbConfig.getDialectType());
			try (Connection connection = pool.getConnection(dbConfig)) {
				SqlExecutor.executeScript(connection, dbConfig.getSchema(), record.getSyncSql());
			}
			updateStatus(record.getId(), SqlCheckExecStatus.SUCCESS, null, LocalDateTime.now());
			log.info("Executed sql_check id={} successfully", id);
		}
		catch (Exception ex) {
			log.error("Failed to execute sql_check id={}: {}", id, ex.getMessage());
			updateStatus(record.getId(), SqlCheckExecStatus.FAILED, ex.getMessage(), LocalDateTime.now());
			throw new IllegalStateException("SQL 执行失败：" + ex.getMessage(), ex);
		}
	}

	/**
	 * 忽略待执行的同步 SQL。
	 */
	public void ignore(Integer id) {
		SqlCheck record = requirePendingRecord(id);
		updateStatus(record.getId(), SqlCheckExecStatus.IGNORED, null, null);
		log.info("Ignored sql_check id={}", id);
	}

	private SqlCheck requirePendingRecord(Integer id) {
		SqlCheck record = sqlCheckMapper.selectById(id);
		if (record == null) {
			throw new IllegalArgumentException("审批记录不存在，请刷新列表后重试");
		}
		if (!SqlCheckExecStatus.PENDING.getValue().equals(record.getExecStatus())) {
			SqlCheckExecStatus current = SqlCheckExecStatus.fromValue(record.getExecStatus());
			throw new IllegalStateException("该 SQL 已处理（当前状态：" + current.getLabel() + "），无法重复操作");
		}
		return record;
	}

	private void updateStatus(Integer id, SqlCheckExecStatus status, String errorMsg, LocalDateTime execTime) {
		SqlCheck update = SqlCheck.builder()
			.id(id)
			.execStatus(status.getValue())
			.errorMsg(errorMsg)
			.execTime(execTime)
			.build();
		sqlCheckMapper.updateStatus(update);
	}

	private SqlCheckDTO toDto(SqlCheck record) {
		SqlCheckExecStatus status = SqlCheckExecStatus.fromValue(record.getExecStatus());
		return SqlCheckDTO.builder()
			.id(record.getId())
			.agentId(record.getAgentId())
			.datasourceId(record.getDatasourceId())
			.sourceTable(record.getSourceTable())
			.targetTable(record.getTargetTable())
			.syncSql(record.getSyncSql())
			.sqlType(record.getSqlType())
			.execStatus(record.getExecStatus())
			.execStatusLabel(status.getLabel())
			.errorMsg(record.getErrorMsg())
			.createTime(record.getCreateTime())
			.execTime(record.getExecTime())
			.build();
	}

}
