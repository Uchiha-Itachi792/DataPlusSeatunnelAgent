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
package com.alibaba.cloud.ai.dataagent.service.sync;

import com.alibaba.cloud.ai.dataagent.dto.syncjob.DataPointer;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmObjectResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncIntent;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncSql;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSystemResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.MockSyncLayerResult;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveRequest;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.enums.ResolvePhase;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.enums.WriteMode;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogEntry;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M0 专用：不调 LLM，规则生成 L1～L4 层结果。
 * @deprecated M1 起生产路径使用 {@link com.alibaba.cloud.ai.dataagent.service.sync.resolve} 分层服务。
 */
@Deprecated
@Component
@AllArgsConstructor
public class MockSyncLayerResolver {

	private static final Pattern SYNC_CN = Pattern
		.compile("同步\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*到\\s*([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

	private static final Pattern COPY_EN = Pattern
		.compile("copy\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+to\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

	private final AgentDatasourceService agentDatasourceService;

	public MockSyncLayerResult resolve(SyncResolveRequest request) {
		Long agentId = request.getAgentId();
		AgentDatasource active = agentDatasourceService.getCurrentAgentDatasource(agentId);
		String ref = SyncCatalogEntry.toRef(active.getDatasourceId());

		LlmSystemResolve system = LlmSystemResolve.builder()
			.phase(ResolvePhase.SYSTEM.name())
			.sourceRef(ref)
			.targetRef(ref)
			.build();

		String query = StringUtils.hasText(request.getCanonicalQuery()) ? request.getCanonicalQuery()
				: request.getUserInput();
		String[] tables = parseTables(query);
		LlmObjectResolve object = LlmObjectResolve.builder()
			.phase(ResolvePhase.OBJECT.name())
			.source(DataPointer.builder().ref(ref).object(tables[0]).build())
			.sink(DataPointer.builder().ref(ref).object(tables[1]).build())
			.others(new ArrayList<>())
			.build();

		LlmSyncIntent intent = LlmSyncIntent.builder()
			.phase(ResolvePhase.INTENT.name())
			.syncKind(SyncKind.TABLE_COPY)
			.writeMode(WriteMode.APPEND)
			.build();

		LlmSyncSql sql = LlmSyncSql.builder().phase(ResolvePhase.SQL.name()).scope("single").sql(null).build();

		return MockSyncLayerResult.builder().system(system).object(object).intent(intent).sql(sql).build();
	}

	private String[] parseTables(String query) {
		if (StringUtils.hasText(query)) {
			Matcher cn = SYNC_CN.matcher(query);
			if (cn.find()) {
				return new String[] { cn.group(1), cn.group(2) };
			}
			Matcher en = COPY_EN.matcher(query);
			if (en.find()) {
				return new String[] { en.group(1), en.group(2) };
			}
		}
		return new String[] { "order_items", "order_items_back" };
	}

}
