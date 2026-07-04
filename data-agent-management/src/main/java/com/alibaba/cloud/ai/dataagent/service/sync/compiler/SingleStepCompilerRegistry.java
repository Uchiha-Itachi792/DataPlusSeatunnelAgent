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
package com.alibaba.cloud.ai.dataagent.service.sync.compiler;

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncTask;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 按 SyncKind 注册单步 Compiler。
 */
@Component
public class SingleStepCompilerRegistry {

	private final Map<SyncKind, SingleStepCompiler> compilers = new EnumMap<>(SyncKind.class);

	public SingleStepCompilerRegistry(TableCopyCompiler tableCopyCompiler) {
		compilers.put(SyncKind.TABLE_COPY, tableCopyCompiler);
	}

	public CompiledJobConfig compile(LlmSyncTask task) {
		SingleStepCompiler compiler = compilers.get(task.getSyncKind());
		if (compiler == null) {
			throw new IllegalArgumentException("未注册的 syncKind：" + task.getSyncKind());
		}
		return compiler.compile(task);
	}

}
