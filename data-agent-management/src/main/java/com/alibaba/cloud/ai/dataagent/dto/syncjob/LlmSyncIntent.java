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
import com.alibaba.cloud.ai.dataagent.enums.WriteMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * L3 同步意图输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmSyncIntent {

	@JsonProperty("phase")
	@JsonPropertyDescription("固定为 INTENT")
	private String phase;

	@JsonProperty("syncKind")
	@JsonPropertyDescription("同步类型，必须显式给出")
	private SyncKind syncKind;

	@JsonProperty("writeMode")
	@JsonPropertyDescription("写入模式")
	@Builder.Default
	private WriteMode writeMode = WriteMode.APPEND;

	@JsonProperty("needClarify")
	@JsonPropertyDescription("是否需要向用户澄清")
	@Builder.Default
	private boolean needClarify = false;

	@JsonProperty("clarifyQuestions")
	@JsonPropertyDescription("需澄清的问题列表")
	@Builder.Default
	private List<String> clarifyQuestions = new ArrayList<>();

}
