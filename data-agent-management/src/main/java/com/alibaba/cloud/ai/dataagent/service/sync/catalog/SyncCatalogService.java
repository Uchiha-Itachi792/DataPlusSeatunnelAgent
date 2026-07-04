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

import java.util.List;
import java.util.Optional;

/**
 * Agent 授权数据源 Catalog。
 */
public interface SyncCatalogService {

	List<SyncCatalogEntry> listRefs(Long agentId);

	Optional<SyncCatalogEntry> getEntry(String ref);

	boolean existsRef(String ref);

	Optional<Integer> resolveDatasourceId(String ref);

	boolean objectExists(Long agentId, String ref, String object);

}
