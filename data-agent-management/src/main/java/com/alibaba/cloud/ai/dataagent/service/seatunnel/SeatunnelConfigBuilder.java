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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于数据源连接信息构建 SeaTunnel HOCON 作业配置。
 */
@Component
@AllArgsConstructor
public class SeatunnelConfigBuilder {

	private final DatasourceService datasourceService;

	/**
	 * 构建 MySQL → MySQL 单表同步 conf（source/sink 使用同一数据源，MVP 阶段）。
	 */
	public String build(Datasource datasource, String sourceTable, String targetTable) {
		DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);
		String jdbcUrl = escapeHocon(dbConfig.getUrl());
		String username = escapeHocon(dbConfig.getUsername());
		String password = escapeHocon(dbConfig.getPassword());
		String source = escapeHocon(sourceTable);
		String target = escapeHocon(targetTable);

		return """
				env {
				  parallelism = 1
				  job.mode = "BATCH"
				}

				source {
				  Jdbc {
				    url = "%s"
				    driver = "com.mysql.cj.jdbc.Driver"
				    user = "%s"
				    password = "%s"
				    query = "SELECT * FROM `%s`"
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
				""".formatted(jdbcUrl, username, password, source, jdbcUrl, username, password,
				escapeHocon(dbConfig.getSchema()), target);
	}

	private String escapeHocon(String value) {
		return SeatunnelConfPostProcessor.escapeHocon(value);
	}

}
