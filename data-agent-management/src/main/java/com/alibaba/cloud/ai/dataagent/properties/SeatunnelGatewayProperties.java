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
package com.alibaba.cloud.ai.dataagent.properties;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 独立 SeaTunnel Gateway 服务连接配置。
 */
@Data
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".seatunnel-gateway")
public class SeatunnelGatewayProperties {

	/**
	 * Gateway 服务根地址，为空表示未配置（执行时将返回明确错误）。
	 */
	private String baseUrl = "";

	/**
	 * 提交作业超时（毫秒）。
	 */
	private long submitTimeoutMs = 30_000;

	/**
	 * Gateway API Key（可选）。
	 */
	private String apiKey = "";

	public boolean isConfigured() {
		return StringUtils.hasText(baseUrl);
	}

}
