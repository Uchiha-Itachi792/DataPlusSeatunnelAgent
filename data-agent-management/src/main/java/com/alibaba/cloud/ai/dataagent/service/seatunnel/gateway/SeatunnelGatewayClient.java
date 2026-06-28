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

/**
 * 独立 SeaTunnel Gateway 客户端。
 * <p>
 * 当前仅 {@link #submit(String)}；{@code getJobStatus} 与状态轮询见路线图阶段 3，Gateway 服务未部署前非 MVP 范围。
 */
public interface SeatunnelGatewayClient {

	/**
	 * 提交 SeaTunnel 作业配置。
	 * @param jobConfig HOCON 配置全文
	 * @return Gateway 返回的作业 ID
	 */
	String submit(String jobConfig);

}
