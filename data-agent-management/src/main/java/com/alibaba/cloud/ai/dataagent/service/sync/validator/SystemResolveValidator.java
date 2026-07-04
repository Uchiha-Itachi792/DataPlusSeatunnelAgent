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
package com.alibaba.cloud.ai.dataagent.service.sync.validator;

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSystemResolve;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogEntry;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * L1 系统定位校验：ref 必须在 Agent 授权 Catalog 中。
 */
@Component
@AllArgsConstructor
public class SystemResolveValidator {

	private final SyncCatalogService syncCatalogService;

	public void validate(Long agentId, LlmSystemResolve system) {
		if (system == null) {
			throw new IllegalArgumentException("L1 系统定位结果为空");
		}
		if (system.isNeedClarify()) {
			return;
		}
		Set<String> allowedRefs = syncCatalogService.listRefs(agentId)
			.stream()
			.map(SyncCatalogEntry::getRef)
			.collect(Collectors.toSet());
		validateRef(system.getSourceRef(), allowedRefs, "源系统");
		validateRef(system.getTargetRef(), allowedRefs, "目标系统");
	}

	private void validateRef(String ref, Set<String> allowedRefs, String label) {
		if (!StringUtils.hasText(ref)) {
			throw new IllegalArgumentException(label + " ref 不能为空");
		}
		if (!allowedRefs.contains(ref)) {
			throw new IllegalArgumentException(label + " ref 不在 Agent 授权 Catalog 中：" + ref);
		}
	}

}
