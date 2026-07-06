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
package com.alibaba.cloud.ai.dataagent.dto.syncjob;

import com.alibaba.cloud.ai.dataagent.enums.ResolvePhase;
import lombok.Builder;
import lombok.Data;

/**
 * 多轮澄清状态：当前 phase、各层快照、threadId。
 */
@Data
@Builder
public class SyncResolveState {

	private String threadId;

	private Long agentId;

	private ResolvePhase pendingPhase;

	private String originalQuery;

	private String canonicalQuery;

	private String multiTurn;

	private MockSyncLayerResult layers;

}
