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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SeaTunnel HOCON 配置结构与安全校验。
 */
@Component
public class SeatunnelConfValidator {

	private static final Pattern FORBIDDEN_CONNECTOR_PATTERN = Pattern.compile(
			"\\b(Kafka|MySQL-CDC|Elasticsearch|MongoDB|Redis|ClickHouse|Doris|StarRocks|Hive)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern STREAMING_MODE_PATTERN = Pattern.compile("job\\.mode\\s*=\\s*\"STREAMING\"",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern PLAINTEXT_PASSWORD_PATTERN = Pattern.compile("password\\s*=\\s*\"(?!__JDBC_PASSWORD__)",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern SOURCE_JDBC_QUERY_PATTERN = Pattern.compile(
			"source\\s*\\{[\\s\\S]*?Jdbc\\s*\\{[\\s\\S]*?query\\s*=\\s*\"([^\"]*?)\"",
			Pattern.CASE_INSENSITIVE);

	public void validate(String conf) {
		validate(conf, null);
	}

	/**
	 * 校验 LLM 生成的 conf；{@code userInput} 非空时额外校验过滤语义是否体现在 query WHERE 中。
	 */
	public void validate(String conf, String userInput) {
		if (!StringUtils.hasText(conf)) {
			throw new IllegalArgumentException("生成的 SeaTunnel conf 为空，请重新描述同步需求");
		}

		String normalized = conf.trim();
		if (!containsBlock(normalized, "env")) {
			throw new IllegalArgumentException("生成的 SeaTunnel conf 缺少 env 块，请重新描述同步需求");
		}
		if (!containsBlock(normalized, "source")) {
			throw new IllegalArgumentException("生成的 SeaTunnel conf 缺少 source 块，请重新描述同步需求");
		}
		if (!containsBlock(normalized, "sink")) {
			throw new IllegalArgumentException("生成的 SeaTunnel conf 缺少 sink 块，请重新描述同步需求");
		}
		if (FORBIDDEN_CONNECTOR_PATTERN.matcher(normalized).find()) {
			throw new IllegalArgumentException("生成的 conf 包含当前不支持的 Connector（Kafka/CDC 等），请修改同步需求后重试");
		}
		if (STREAMING_MODE_PATTERN.matcher(normalized).find()) {
			throw new IllegalArgumentException("生成的 conf 使用了 STREAMING 模式，当前仅支持 BATCH，请修改同步需求后重试");
		}
		if (PLAINTEXT_PASSWORD_PATTERN.matcher(normalized).find()) {
			throw new IllegalArgumentException("生成的 conf 包含明文密码，请使用 __JDBC_PASSWORD__ 占位符");
		}
		validateSourceQuery(normalized, userInput);
	}

	private void validateSourceQuery(String conf, String userInput) {
		Matcher matcher = SOURCE_JDBC_QUERY_PATTERN.matcher(conf);
		if (!matcher.find()) {
			throw new IllegalArgumentException("生成的 SeaTunnel conf 的 source.Jdbc 缺少非空 query，请重新描述同步需求");
		}
		String query = matcher.group(1).trim();
		if (!StringUtils.hasText(query)) {
			throw new IllegalArgumentException("生成的 SeaTunnel conf 的 source.Jdbc.query 为空，请重新描述同步需求");
		}
		if (StringUtils.hasText(userInput) && hasFilterIntent(userInput)
				&& !query.toUpperCase(Locale.ROOT).contains("WHERE")) {
			throw new IllegalArgumentException("用户要求过滤/排除数据，但 source.Jdbc.query 缺少 WHERE 条件，请重新描述同步需求");
		}
	}

	private boolean hasFilterIntent(String userInput) {
		String lower = userInput.toLowerCase(Locale.ROOT);
		return lower.contains("不要") || lower.contains("排除") || lower.contains("过滤") || lower.contains("不同步")
				|| lower.contains("跳过") || lower.contains("仅") || lower.contains("只") || lower.contains("where")
				|| lower.contains("status=");
	}

	private boolean containsBlock(String conf, String blockName) {
		return Pattern.compile("\\b" + blockName + "\\s*\\{", Pattern.CASE_INSENSITIVE).matcher(conf).find();
	}

}
