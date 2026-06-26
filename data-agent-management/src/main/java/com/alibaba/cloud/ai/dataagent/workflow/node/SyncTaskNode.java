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

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.sync.SyncTaskResult;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.sync.SqlCheckService;
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
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 数据同步任务节点：解析源/目标表，生成 MySQL 同步 SQL 并写入审批表（不直接执行 SQL）。
 */
@Slf4j
@Component
@AllArgsConstructor
public class SyncTaskNode implements NodeAction {

	private final TableSyncService tableSyncService;

	private final SqlCheckService sqlCheckService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		String userInput = StateUtil.getStringValue(state, INPUT_KEY);
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);
		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");
		String canonicalQuery = resolveCanonicalQuery(state, userInput);
		String evidence = StateUtil.getStringValue(state, EVIDENCE, "无");

		log.info("Processing data sync task for agent: {}, input: {}", agentIdStr, userInput);

		Long agentId = Long.valueOf(agentIdStr);
		SyncTaskResult result = tableSyncService.generateSyncSql(agentId, userInput, multiTurn, canonicalQuery,
				evidence);

		boolean savedToApproval = false;
		String saveError = null;
		if (result.getType() != SyncTaskResult.Type.ERROR) {
			try {
				sqlCheckService.save(result, agentId);
				savedToApproval = true;
			}
			catch (Exception ex) {
				log.warn("Failed to save sync SQL to sql_check for agent {}: {}", agentId, ex.getMessage());
				saveError = ex.getMessage();
			}
		}

		Flux<ChatResponse> messageFlux = buildMessageFlux(result, savedToApproval, saveError);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在解析同步任务...", null, ignored -> Map.of(), messageFlux);

		return Map.of(SYNC_TASK_NODE_OUTPUT, generator);
	}

	private Flux<ChatResponse> buildMessageFlux(SyncTaskResult result, boolean savedToApproval, String saveError) {
		if (result.getType() == SyncTaskResult.Type.ERROR) {
			return Flux.just(ChatResponseUtil.createResponse(result.getMessage()));
		}

		List<ChatResponse> chunks = new ArrayList<>();
		chunks.add(ChatResponseUtil.createResponse("已生成数据同步 SQL 脚本："));
		chunks.add(ChatResponseUtil.createPureResponse(TextType.SQL.getStartSign()));
		chunks.add(ChatResponseUtil.createResponse(result.getSql()));
		chunks.add(ChatResponseUtil.createPureResponse(TextType.SQL.getEndSign()));

		if (savedToApproval) {
			chunks.add(ChatResponseUtil.createResponse("\n请前往 SQL 审批页面查看。"));
		}
		else if (saveError != null) {
			chunks.add(ChatResponseUtil.createResponse("\nSQL 已生成，但保存审批记录失败：" + saveError));
		}

		return Flux.fromIterable(chunks);
	}

	private String resolveCanonicalQuery(OverAllState state, String userInput) {
		try {
			QueryEnhanceOutputDTO queryEnhance = StateUtil.getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT,
					QueryEnhanceOutputDTO.class);
			if (queryEnhance != null && StringUtils.hasText(queryEnhance.getCanonicalQuery())) {
				return queryEnhance.getCanonicalQuery().trim();
			}
		}
		catch (Exception ex) {
			log.debug("No query enhance output in state, using raw user input for schema recall");
		}
		return userInput;
	}

}
