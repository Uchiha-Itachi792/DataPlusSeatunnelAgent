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
package com.alibaba.cloud.ai.dataagent.service.sync.fastpath;

import com.alibaba.cloud.ai.dataagent.properties.SeatunnelProperties;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单全表拷贝 Fast Path：表名清晰且无复杂语义时跳过部分 LLM。
 */
@Component
@AllArgsConstructor
public class FastPathDetector {

	private static final Pattern SYNC_CN = Pattern
		.compile("同步\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*到\\s*([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

	private static final Pattern SYNC_BA = Pattern.compile(
			"把\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:表)?\\s*同步到\\s*([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

	private static final Pattern COPY_EN = Pattern
		.compile("copy\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+to\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

	/** 澄清续跑时用户可能只补「a 到 b」 */
	private static final Pattern TO_CN = Pattern
		.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*到\\s*([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

	private static final Pattern COMPLEX_SEMANTICS = Pattern.compile(
			"过滤|排除|聚合|JOIN|join|关联|GROUP|group|WHERE|where|字段|映射|转换|增量|CDC|cdc|Kafka|kafka|"
					+ "流式|删除目标|清空|不要|只要|WHERE",
			Pattern.CASE_INSENSITIVE);

	private final SeatunnelProperties seatunnelProperties;

	private final SyncCatalogService syncCatalogService;

	public boolean isEnabled() {
		return seatunnelProperties.getResolve().isFastPathEnabled();
	}

	public boolean hasComplexSemantics(String query) {
		return StringUtils.hasText(query) && COMPLEX_SEMANTICS.matcher(query).find();
	}

	/**
	 * 从用户话中解析源/目标表名（物理标识符）。
	 */
	public Optional<String[]> parseClearTableNames(String query) {
		if (!StringUtils.hasText(query)) {
			return Optional.empty();
		}
		Matcher ba = SYNC_BA.matcher(query);
		if (ba.find()) {
			return Optional.of(new String[] { ba.group(1), ba.group(2) });
		}
		Matcher cn = SYNC_CN.matcher(query);
		if (cn.find()) {
			return Optional.of(new String[] { cn.group(1), cn.group(2) });
		}
		Matcher en = COPY_EN.matcher(query);
		if (en.find()) {
			return Optional.of(new String[] { en.group(1), en.group(2) });
		}
		Matcher to = TO_CN.matcher(query);
		if (to.find()) {
			return Optional.of(new String[] { to.group(1), to.group(2) });
		}
		return Optional.empty();
	}

	/**
	 * 表名清晰、无复杂语义、且 object 在 Catalog 中存在时，可跳过 L2 LLM 与 L3/L4。
	 */
	public Optional<String[]> detectTableCopyFastPath(Long agentId, String query, String sourceRef, String targetRef) {
		if (!isEnabled() || hasComplexSemantics(query)) {
			return Optional.empty();
		}
		Optional<String[]> tables = parseClearTableNames(query);
		if (tables.isEmpty()) {
			return Optional.empty();
		}
		String sourceTable = tables.get()[0];
		String sinkTable = tables.get()[1];
		if (!syncCatalogService.objectExists(agentId, sourceRef, sourceTable)
				|| !syncCatalogService.objectExists(agentId, targetRef, sinkTable)) {
			return Optional.empty();
		}
		return Optional.of(new String[] { sourceTable, sinkTable });
	}

}
