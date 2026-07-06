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

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmObjectResolve;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * L2 对象定位校验：委托 {@link SyncPlanConsistencyValidator}。
 */
@Component
@AllArgsConstructor
public class ObjectResolveValidator {

	private final SyncPlanConsistencyValidator syncPlanConsistencyValidator;

	public void validate(Long agentId, LlmObjectResolve object) {
		syncPlanConsistencyValidator.validateObjectResolve(agentId, object);
	}

}
