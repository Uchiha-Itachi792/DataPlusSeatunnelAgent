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
package com.alibaba.cloud.ai.dataagent.service.sync.catalog;

import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * M0 Catalog：基于 Agent 授权数据源生成 ref 清单（ds_{datasourceId}）。
 */
@Service
@AllArgsConstructor
public class MockSyncCatalogService implements SyncCatalogService {

	private static final Pattern OBJECT_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	@Override
	public List<SyncCatalogEntry> listRefs(Long agentId) {
		List<AgentDatasource> agentDatasource = agentDatasourceService.getAgentDatasource(agentId);
		List<SyncCatalogEntry> syncCatalogEntries = agentDatasource
				.stream()
				.map(this::toEntry)
				.toList();
		return syncCatalogEntries;
	}

	@Override
	public Optional<SyncCatalogEntry> getEntry(String ref) {
		return resolveDatasourceId(ref).map(id -> {
			Datasource datasource = datasourceService.getDatasourceById(id);
			if (datasource == null) {
				return null;
			}
			SyncCatalogEntry entry = SyncCatalogEntry.builder()
					.ref(ref)
					.connectorType(StringUtils.hasText(datasource.getType()) ? datasource.getType().toUpperCase() : "JDBC")
					.description(StringUtils.hasText(datasource.getName()) ? datasource.getName() : "datasource-" + id)
					.datasourceId(id)
					.build();
			return entry;
		});
	}

	@Override
	public boolean existsRef(String ref) {
		return getEntry(ref).isPresent();
	}

	@Override
	public Optional<Integer> resolveDatasourceId(String ref) {
		if (!StringUtils.hasText(ref) || !ref.startsWith("ds_")) {
			return Optional.empty();
		}
		try {
			return Optional.of(Integer.parseInt(ref.substring(3)));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

	@Override
	public boolean objectExists(Long agentId, String ref, String object) {
		if (!StringUtils.hasText(object) || !OBJECT_NAME.matcher(object).matches()) {
			return false;
		}
		Optional<Integer> datasourceId = resolveDatasourceId(ref);
		if (datasourceId.isEmpty()) {
			return false;
		}
		List<AgentDatasource> agentDatasource = agentDatasourceService.getAgentDatasource(agentId);
		return agentDatasource
			.stream()
			.filter(ad -> datasourceId.get().equals(ad.getDatasourceId()))
			.findFirst()
			.map(ad -> {
				List<String> tables = ad.getSelectTables();
				if (tables == null || tables.isEmpty()) {
					return true;
				}
				return tables.contains(object);
			})
			.orElse(false);
	}

	private SyncCatalogEntry toEntry(AgentDatasource agentDatasource) {
		Integer datasourceId = agentDatasource.getDatasourceId();
		Datasource datasource = agentDatasource.getDatasource();
		String description = datasource != null && StringUtils.hasText(datasource.getName()) ? datasource.getName()
				: "datasource-" + datasourceId;
		String connectorType = datasource != null && StringUtils.hasText(datasource.getType()) ? datasource.getType()
				: "JDBC";
		SyncCatalogEntry entry = SyncCatalogEntry.builder()
				.ref(SyncCatalogEntry.toRef(datasourceId))
				.connectorType(connectorType.toUpperCase())
				.description(description)
				.datasourceId(datasourceId)
				.build();
		return entry;
	}

}
