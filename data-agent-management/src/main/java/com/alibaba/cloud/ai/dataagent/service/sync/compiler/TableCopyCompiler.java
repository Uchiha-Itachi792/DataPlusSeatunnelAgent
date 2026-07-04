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
package com.alibaba.cloud.ai.dataagent.service.sync.compiler;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.DataPointer;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncTask;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * TABLE_COPY 单步 conf 编译：Java 模板拼 HOCON，sql 为空时补 SELECT *。
 */
@Component
@AllArgsConstructor
public class TableCopyCompiler implements SingleStepCompiler {

	private final CredentialInjector credentialInjector;

	private final SyncCatalogService syncCatalogService;

	private final SeatunnelProperties seatunnelProperties;

	@Override
	public CompiledJobConfig compile(LlmSyncTask task) {
		if (task.getSyncKind() != SyncKind.TABLE_COPY) {
			throw new IllegalArgumentException("TableCopyCompiler 仅支持 TABLE_COPY");
		}
		DataPointer source = task.getSource();
		DataPointer sink = task.getSink();
		String sourceTable = source.getObject();
		String targetTable = sink.getObject();
		String query = StringUtils.hasText(task.getSql()) ? task.getSql() : "SELECT * FROM `%s`".formatted(sourceTable);

		DbConfigBO sourceDb = credentialInjector.resolveDbConfig(source.getRef());
		DbConfigBO sinkDb = source.getRef().equals(sink.getRef()) ? sourceDb
				: credentialInjector.resolveDbConfig(sink.getRef());

		Integer sourceDatasourceId = syncCatalogService.resolveDatasourceId(source.getRef()).orElse(null);
		Integer sinkDatasourceId = syncCatalogService.resolveDatasourceId(sink.getRef()).orElse(null);

		String conf = buildConf(sourceDb, sinkDb, query, targetTable);
		return CompiledJobConfig.builder()
			.jobConfig(conf)
			.sourceTable(sourceTable)
			.targetTable(targetTable)
			.sourceDatasourceId(sourceDatasourceId)
			.sinkDatasourceId(sinkDatasourceId)
			.build();
	}

	private String buildConf(DbConfigBO sourceDb, DbConfigBO sinkDb, String query, String targetTable) {
		String jdbcUrlSource = credentialInjector.escapeHocon(sourceDb.getUrl());
		String userSource = credentialInjector.escapeHocon(sourceDb.getUsername());
		String passwordSource = credentialInjector.escapeHocon(sourceDb.getPassword());
		String jdbcUrlSink = credentialInjector.escapeHocon(sinkDb.getUrl());
		String userSink = credentialInjector.escapeHocon(sinkDb.getUsername());
		String passwordSink = credentialInjector.escapeHocon(sinkDb.getPassword());
		String queryEscaped = credentialInjector.escapeHocon(query);
		String target = credentialInjector.escapeHocon(targetTable);
		String database = credentialInjector.escapeHocon(sinkDb.getSchema());
		int parallelism = seatunnelProperties.getDefaultParallelism();
		String jobMode = seatunnelProperties.getDefaultJobMode();

		return """
				env {
				  parallelism = %d
				  job.mode = "%s"
				}

				source {
				  Jdbc {
				    url = "%s"
				    driver = "com.mysql.cj.jdbc.Driver"
				    user = "%s"
				    password = "%s"
				    query = "%s"
				  }
				}

				sink {
				  Jdbc {
				    url = "%s"
				    driver = "com.mysql.cj.jdbc.Driver"
				    user = "%s"
				    password = "%s"
				    database = "%s"
				    table = "%s"
				  }
				}
				""".formatted(parallelism, jobMode, jdbcUrlSource, userSource, passwordSource, queryEscaped,
				jdbcUrlSink, userSink, passwordSink, database, target);
	}

}
