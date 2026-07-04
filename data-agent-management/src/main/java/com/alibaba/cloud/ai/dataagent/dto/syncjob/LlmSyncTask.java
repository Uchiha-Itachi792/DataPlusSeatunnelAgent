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
 * 单步同步最终计划。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmSyncTask {

	@JsonProperty("schemaVersion")
	@JsonPropertyDescription("计划 schema 版本")
	@Builder.Default
	private String schemaVersion = "1.0";

	@JsonProperty("syncKind")
	@JsonPropertyDescription("同步类型")
	private SyncKind syncKind;

	@JsonProperty("source")
	@JsonPropertyDescription("源表指针")
	private DataPointer source;

	@JsonProperty("sink")
	@JsonPropertyDescription("目标表指针")
	private DataPointer sink;

	@JsonProperty("others")
	@JsonPropertyDescription("其它关联表")
	@Builder.Default
	private List<DataPointer> others = new ArrayList<>();

	@JsonProperty("sql")
	@JsonPropertyDescription("同步 SQL，TABLE_COPY 可为空")
	private String sql;

	@JsonProperty("writeMode")
	@JsonPropertyDescription("写入模式")
	@Builder.Default
	private WriteMode writeMode = WriteMode.APPEND;

}
