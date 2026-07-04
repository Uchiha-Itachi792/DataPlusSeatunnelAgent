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
package com.alibaba.cloud.ai.dataagent.service.sync.compiler;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.SeatunnelConfPostProcessor;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按 ref 注入 JDBC 凭证到 conf 模板。
 */
@Component
@AllArgsConstructor
public class CredentialInjector {

	private final SyncCatalogService syncCatalogService;

	private final DatasourceService datasourceService;

	public DbConfigBO resolveDbConfig(String ref) {
		Integer datasourceId = syncCatalogService.resolveDatasourceId(ref)
			.orElseThrow(() -> new IllegalArgumentException("无法解析 ref 对应的数据源：" + ref));
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new IllegalStateException("数据源不存在，ref=" + ref);
		}
		return datasourceService.getDbConfig(datasource);
	}

	public String escapeHocon(String value) {
		return SeatunnelConfPostProcessor.escapeHocon(value);
	}

}
