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

import com.alibaba.cloud.ai.dataagent.dto.syncjob.DataPointer;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmObjectResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncTask;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 计划一致性校验：DataPointer 非空、syncKind 与 others 数量一致。
 */
@Component
@AllArgsConstructor
public class SyncPlanConsistencyValidator {

	private final SyncCatalogService syncCatalogService;

	public void validateObjectResolve(Long agentId, LlmObjectResolve object) {
		if (object == null) {
			throw new IllegalArgumentException("L2 对象定位结果为空");
		}
		if (object.isNeedClarify()) {
			return;
		}
		validatePointer(agentId, object.getSource(), "源表");
		validatePointer(agentId, object.getSink(), "目标表");
		if (!CollectionUtils.isEmpty(object.getOthers())) {
			for (DataPointer other : object.getOthers()) {
				validatePointer(agentId, other, "关联表");
			}
		}
	}

	public void validatePlan(Long agentId, LlmSyncTask plan) {
		if (plan == null) {
			throw new IllegalArgumentException("同步计划为空");
		}
		if (plan.getSyncKind() == null) {
			throw new IllegalArgumentException("syncKind 不能为空");
		}
		validatePointer(agentId, plan.getSource(), "源表");
		validatePointer(agentId, plan.getSink(), "目标表");
		if (plan.getSyncKind() == SyncKind.TABLE_COPY || plan.getSyncKind() == SyncKind.COLUMN_MAP) {
			if (!CollectionUtils.isEmpty(plan.getOthers())) {
				throw new IllegalArgumentException(plan.getSyncKind() + " 不应包含 others 表");
			}
		}
	}

	private void validatePointer(Long agentId, DataPointer pointer, String label) {
		if (pointer == null || !StringUtils.hasText(pointer.getRef()) || !StringUtils.hasText(pointer.getObject())) {
			throw new IllegalArgumentException(label + " DataPointer 的 ref 与 object 均不能为空");
		}
		if (!syncCatalogService.existsRef(pointer.getRef())) {
			throw new IllegalArgumentException(label + " ref 不存在：" + pointer.getRef());
		}
		if (!syncCatalogService.objectExists(agentId, pointer.getRef(), pointer.getObject())) {
			throw new IllegalArgumentException(label + " object 不在对象索引中：" + pointer.getObject());
		}
	}

}
