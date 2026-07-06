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
package com.alibaba.cloud.ai.dataagent.service.sync.resolve;

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncSql;
import com.alibaba.cloud.ai.dataagent.enums.ResolvePhase;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import org.springframework.stereotype.Service;

/**
 * L4 SQL：M1 TABLE_COPY 跳过 LLM，sql 为空由 Compiler 补 SELECT *。
 */
@Service
public class SyncSqlService {

	public LlmSyncSql resolveForTableCopy() {
		return LlmSyncSql.builder().phase(ResolvePhase.SQL.name()).scope("single").sql(null).build();
	}

	public LlmSyncSql resolve(SyncKind syncKind) {
		if (syncKind == SyncKind.TABLE_COPY) {
			return resolveForTableCopy();
		}
		throw new IllegalArgumentException("M1 仅支持 TABLE_COPY，不支持：" + syncKind);
	}

}
