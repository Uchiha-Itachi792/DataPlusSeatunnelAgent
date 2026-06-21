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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.sync.TableSyncService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 数据同步任务节点：解析源/目标表，生成 MySQL 同步 SQL 或返回错误提示（不执行 SQL）。
 */
@Slf4j
@Component
@AllArgsConstructor
public class SyncTaskNode implements NodeAction {

	private final TableSyncService tableSyncService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		String userInput = StateUtil.getStringValue(state, INPUT_KEY);
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);
		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		log.info("Processing data sync task for agent: {}, input: {}", agentIdStr, userInput);

		Long agentId = Long.valueOf(agentIdStr);
		SyncTaskResult result = tableSyncService.generateSyncSql(agentId, userInput, multiTurn);

		Flux<ChatResponse> messageFlux = buildMessageFlux(result);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在解析同步任务...", null, ignored -> Map.of(), messageFlux);

		return Map.of(SYNC_TASK_NODE_OUTPUT, generator);
	}

	private Flux<ChatResponse> buildMessageFlux(SyncTaskResult result) {
		if (result.getType() == SyncTaskResult.Type.ERROR) {
			return Flux.just(ChatResponseUtil.createResponse(result.getMessage()));
		}

		List<ChatResponse> chunks = new ArrayList<>();
		String prefix = result.getType() == SyncTaskResult.Type.INSERT_SQL ? "已生成数据同步 SQL："
				: "目标表不存在，已生成建表 SQL：";
		chunks.add(ChatResponseUtil.createResponse(prefix));
		chunks.add(ChatResponseUtil.createPureResponse(TextType.SQL.getStartSign()));
		chunks.add(ChatResponseUtil.createResponse(result.getSql()));
		chunks.add(ChatResponseUtil.createPureResponse(TextType.SQL.getEndSign()));
		return Flux.fromIterable(chunks);
	}

}
