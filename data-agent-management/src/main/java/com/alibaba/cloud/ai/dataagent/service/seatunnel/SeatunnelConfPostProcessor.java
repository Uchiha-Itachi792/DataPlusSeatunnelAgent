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
import org.springframework.stereotype.Component;

/**
 * 将 LLM 生成的 HOCON 占位符替换为真实数据源连接信息。
 */
@Component
public class SeatunnelConfPostProcessor {

	public static final String PLACEHOLDER_JDBC_URL = "__JDBC_URL__";

	public static final String PLACEHOLDER_JDBC_USER = "__JDBC_USER__";

	public static final String PLACEHOLDER_JDBC_PASSWORD = "__JDBC_PASSWORD__";

	public static final String PLACEHOLDER_JDBC_DATABASE = "__JDBC_DATABASE__";

	public String injectCredentials(String rawConf, DbConfigBO dbConfig) {
		return rawConf.replace(PLACEHOLDER_JDBC_URL, escapeHocon(dbConfig.getUrl()))
			.replace(PLACEHOLDER_JDBC_USER, escapeHocon(dbConfig.getUsername()))
			.replace(PLACEHOLDER_JDBC_PASSWORD, escapeHocon(dbConfig.getPassword()))
			.replace(PLACEHOLDER_JDBC_DATABASE, escapeHocon(dbConfig.getSchema()));
	}

	public static String escapeHocon(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
