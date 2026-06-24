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

/**
 * SeaTunnel conf 生成模式：简单模板或 LLM 复杂生成。
 */
public enum SeatunnelSyncMode {

	/**
	 * 固定模板：同库全表 SELECT * 同步。
	 */
	TEMPLATE,

	/**
	 * LLM 生成：含 JOIN、过滤、字段映射等复杂语义。
	 */
	LLM

}
