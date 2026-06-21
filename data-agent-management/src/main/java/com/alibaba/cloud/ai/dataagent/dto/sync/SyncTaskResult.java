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
package com.alibaba.cloud.ai.dataagent.dto.sync;

import lombok.Builder;
import lombok.Getter;

/**
 * 表同步 SQL 生成结果。
 */
@Getter
@Builder
public class SyncTaskResult {

	public enum Type {

		INSERT_SQL, CREATE_SQL, ERROR

	}

	private final Type type;

	private final String message;

	private final String sql;

	public static SyncTaskResult error(String message) {
		return SyncTaskResult.builder().type(Type.ERROR).message(message).build();
	}

	public static SyncTaskResult insertSql(String sql) {
		return SyncTaskResult.builder().type(Type.INSERT_SQL).sql(sql).build();
	}

	public static SyncTaskResult createSql(String sql) {
		return SyncTaskResult.builder().type(Type.CREATE_SQL).sql(sql).build();
	}

}
