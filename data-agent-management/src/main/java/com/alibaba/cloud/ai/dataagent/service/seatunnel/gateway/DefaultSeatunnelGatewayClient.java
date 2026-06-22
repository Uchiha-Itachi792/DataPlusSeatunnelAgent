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
package com.alibaba.cloud.ai.dataagent.service.seatunnel.gateway;

import com.alibaba.cloud.ai.dataagent.properties.SeatunnelGatewayProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 默认 SeaTunnel Gateway 客户端（阶段 3 前为占位实现）。
 */
@Slf4j
@Service
@AllArgsConstructor
public class DefaultSeatunnelGatewayClient implements SeatunnelGatewayClient {

	private static final String GATEWAY_NOT_CONFIGURED_MSG = "SeaTunnel Gateway 未配置，请在 application.yml 中设置 spring.ai.alibaba.data-agent.seatunnel-gateway.base-url";

	private final SeatunnelGatewayProperties properties;

	private final RestTemplate restTemplate;

	@Override
	public String submit(String jobConfig) {
		if (!properties.isConfigured()) {
			throw new IllegalStateException(GATEWAY_NOT_CONFIGURED_MSG);
		}
		String url = properties.getBaseUrl().replaceAll("/$", "") + "/api/jobs";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (StringUtils.hasText(properties.getApiKey())) {
			headers.set("X-Api-Key", properties.getApiKey());
		}
		HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("config", jobConfig), headers);
		try {
			ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
			if (response.getBody() != null && response.getBody().get("jobId") != null) {
				return String.valueOf(response.getBody().get("jobId"));
			}
			throw new IllegalStateException("Gateway 响应缺少 jobId");
		}
		catch (RestClientException ex) {
			log.error("Failed to submit job to SeaTunnel Gateway: {}", ex.getMessage());
			throw new IllegalStateException("调用 SeaTunnel Gateway 失败：" + ex.getMessage(), ex);
		}
	}

}
