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

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 判定 SeaTunnel conf 应走模板还是 LLM 生成。
 */
@Component
public class SeatunnelSyncComplexityRouter {

	private static final Pattern COMPLEX_SEMANTICS_PATTERN = Pattern.compile(
			"过滤|排除|聚合|JOIN|join|关联|GROUP|group|WHERE|where|字段|映射|转换|增量|CDC|cdc|Kafka|kafka|"
					+ "流式|删除目标|清空|Elasticsearch|elasticsearch|Hive|hive|Transform|transform",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern FORBIDDEN_CONNECTOR_PATTERN = Pattern.compile(
			"\\b(MySQL-CDC|Kafka|Elasticsearch|Hive|MongoDB|Redis|ClickHouse|Doris|StarRocks)\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * 解析同步复杂度。任一复杂条件命中则走 LLM。
	 * @param userInput 用户原始输入
	 * @param relatedTables 扩展后的关联表列表
	 * @param targetTableExists 目标表是否已存在
	 */
	public SeatunnelSyncMode resolve(String userInput, List<String> relatedTables, boolean targetTableExists) {
		if (relatedTables != null && !relatedTables.isEmpty()) {
			return SeatunnelSyncMode.LLM;
		}
		if (!targetTableExists) {
			return SeatunnelSyncMode.LLM;
		}
		if (StringUtils.hasText(userInput) && COMPLEX_SEMANTICS_PATTERN.matcher(userInput).find()) {
			return SeatunnelSyncMode.LLM;
		}
		if (StringUtils.hasText(userInput) && FORBIDDEN_CONNECTOR_PATTERN.matcher(userInput).find()) {
			return SeatunnelSyncMode.LLM;
		}
		return SeatunnelSyncMode.TEMPLATE;
	}

}
