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
package com.alibaba.cloud.ai.dataagent.dto.seatunnel;

import lombok.Builder;
import lombok.Getter;

/**
 * SeaTunnel conf 生成结果。
 */
@Getter
@Builder
public class SeatunnelTaskResult {

	public enum Type {

		OK, ERROR

	}

	public enum GenerationMode {

		TEMPLATE, LLM

	}

	private final Type type;

	private final String message;

	private final String jobConfig;

	private final String sourceTable;

	private final String targetTable;

	private final Integer sourceDatasourceId;

	private final Integer sinkDatasourceId;

	private final GenerationMode generationMode;

	public static SeatunnelTaskResult error(String message) {
		return SeatunnelTaskResult.builder().type(Type.ERROR).message(message).build();
	}

	public static SeatunnelTaskResult ok(String jobConfig, String sourceTable, String targetTable,
			Integer sourceDatasourceId, Integer sinkDatasourceId, GenerationMode generationMode) {
		return SeatunnelTaskResult.builder()
			.type(Type.OK)
			.jobConfig(jobConfig)
			.sourceTable(sourceTable)
			.targetTable(targetTable)
			.sourceDatasourceId(sourceDatasourceId)
			.sinkDatasourceId(sinkDatasourceId)
			.generationMode(generationMode)
			.build();
	}

	public static SeatunnelTaskResult ok(String jobConfig, String sourceTable, String targetTable,
			Integer sourceDatasourceId, Integer sinkDatasourceId) {
		return ok(jobConfig, sourceTable, targetTable, sourceDatasourceId, sinkDatasourceId, null);
	}

	public boolean isSuccess() {
		return type == Type.OK && jobConfig != null && !jobConfig.isBlank();
	}

}
