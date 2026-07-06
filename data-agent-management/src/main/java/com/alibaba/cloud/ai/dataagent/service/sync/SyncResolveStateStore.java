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

import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存多轮澄清状态存储（按 threadId）。
 */
@Component
public class SyncResolveStateStore {

	private final ConcurrentHashMap<String, SyncResolveState> states = new ConcurrentHashMap<>();

	public void save(SyncResolveState state) {
		if (state == null || !StringUtils.hasText(state.getThreadId())) {
			return;
		}
		states.put(state.getThreadId(), state);
	}

	public Optional<SyncResolveState> get(String threadId) {
		if (!StringUtils.hasText(threadId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(states.get(threadId));
	}

	public boolean has(String threadId) {
		return StringUtils.hasText(threadId) && states.containsKey(threadId);
	}

	public void remove(String threadId) {
		if (StringUtils.hasText(threadId)) {
			states.remove(threadId);
		}
	}

}
