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

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmObjectResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncIntent;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveRequest;
import com.alibaba.cloud.ai.dataagent.enums.ResolvePhase;
import com.alibaba.cloud.ai.dataagent.enums.SyncKind;
import com.alibaba.cloud.ai.dataagent.enums.WriteMode;
import com.alibaba.cloud.ai.dataagent.service.sync.fastpath.FastPathDetector;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * L3 同步意图：M1 仅 TABLE_COPY；复杂语义 needClarify（不调 LLM）。
 */
@Service
@AllArgsConstructor
public class SyncIntentService {

	private final FastPathDetector fastPathDetector;

	public LlmSyncIntent resolve(SyncResolveRequest request, LlmObjectResolve object, boolean forceTableCopy) {
		String query = resolveQuery(request);
		if (!forceTableCopy && fastPathDetector.hasComplexSemantics(query)) {
			return LlmSyncIntent.builder()
				.phase(ResolvePhase.INTENT.name())
				.needClarify(true)
				.clarifyQuestions(List.of("当前仅支持全表拷贝（TABLE_COPY），暂不支持过滤、JOIN 或字段映射。是否改为全表同步？"))
				.build();
		}
		return LlmSyncIntent.builder()
			.phase(ResolvePhase.INTENT.name())
			.syncKind(SyncKind.TABLE_COPY)
			.writeMode(WriteMode.APPEND)
			.build();
	}

	private static String resolveQuery(SyncResolveRequest request) {
		return StringUtils.hasText(request.getCanonicalQuery()) ? request.getCanonicalQuery() : request.getUserInput();
	}

}
