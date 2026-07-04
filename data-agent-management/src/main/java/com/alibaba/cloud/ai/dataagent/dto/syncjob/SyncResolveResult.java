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

import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.enums.SyncMode;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 同步解析统一返回。
 */
@Getter
@Builder
public class SyncResolveResult {

	public enum Type {

		SUCCESS, CLARIFY, ERROR

	}

	private final Type type;

	private final String message;

	private final LlmSyncTask plan;

	private final String jobConfig;

	private final ResolveTrace trace;

	private final SyncMode syncMode;

	private final SyncKind syncKind;

	private final List<String> clarifyQuestions;

	public static SyncResolveResult success(LlmSyncTask plan, String jobConfig, ResolveTrace trace) {
		return SyncResolveResult.builder()
			.type(Type.SUCCESS)
			.plan(plan)
			.jobConfig(jobConfig)
			.trace(trace)
			.syncMode(SyncMode.SINGLE)
			.syncKind(plan.getSyncKind())
			.build();
	}

	public static SyncResolveResult clarify(List<String> questions) {
		return SyncResolveResult.builder()
			.type(Type.CLARIFY)
			.message("需要补充信息")
			.clarifyQuestions(questions != null ? List.copyOf(questions) : Collections.emptyList())
			.build();
	}

	public static SyncResolveResult error(String message) {
		return SyncResolveResult.builder().type(Type.ERROR).message(message).build();
	}

}
