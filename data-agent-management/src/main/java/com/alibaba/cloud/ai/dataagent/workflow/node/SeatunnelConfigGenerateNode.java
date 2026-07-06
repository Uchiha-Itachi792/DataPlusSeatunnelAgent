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
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveRequest;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveResult;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.SeatunnelTaskService;
import com.alibaba.cloud.ai.dataagent.service.sync.SyncOrchestrator;
import com.alibaba.cloud.ai.dataagent.service.sync.SyncResolveStateStore;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * SeaTunnel conf 生成节点：调用 SyncOrchestrator 解析计划、编译 conf 并写入审批表。
 */
@Slf4j
@Component
@AllArgsConstructor
public class SeatunnelConfigGenerateNode implements NodeAction {

	private final SyncOrchestrator syncOrchestrator;

	private final SeatunnelTaskService seatunnelTaskService;

	private final SyncResolveStateStore syncResolveStateStore;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		String userInput = StateUtil.getStringValue(state, INPUT_KEY);
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);
		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");
		String canonicalQuery = resolveCanonicalQuery(state, userInput);
		String threadId = StateUtil.getStringValue(state, TRACE_THREAD_ID, null);

		log.info("Processing SeaTunnel sync task for agent: {}, input: {}", agentIdStr, userInput);

		Long agentId = Long.valueOf(agentIdStr);
		SyncResolveRequest request = SyncResolveRequest.builder()
			.agentId(agentId)
			.userInput(userInput)
			.canonicalQuery(canonicalQuery)
			.multiTurn(multiTurn)
			.threadId(threadId)
			.build();

		SyncResolveResult result;
		if (StringUtils.hasText(threadId) && syncResolveStateStore.has(threadId)) {
			log.info("Resuming SeaTunnel sync clarify for threadId={}", threadId);
			result = syncOrchestrator.resume(request, userInput);
		}
		else {
			result = syncOrchestrator.resolve(request);
		}

		boolean savedToApproval = false;
		String saveError = null;
		if (result.getType() == SyncResolveResult.Type.SUCCESS) {
			try {
				seatunnelTaskService.save(result, agentId);
				savedToApproval = true;
			}
			catch (Exception ex) {
				log.warn("Failed to save SeaTunnel conf to seatunnel_task for agent {}: {}", agentId, ex.getMessage());
				saveError = ex.getMessage();
			}
		}

		Flux<ChatResponse> messageFlux = buildMessageFlux(result, savedToApproval, saveError);
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在生成 SeaTunnel 配置...", null, ignored -> Map.of(), messageFlux);
		return Map.of(SEATUNNEL_CONFIG_GENERATE_NODE_OUTPUT, generator);
	}

	private Flux<ChatResponse> buildMessageFlux(SyncResolveResult result, boolean savedToApproval, String saveError) {
		if (result.getType() == SyncResolveResult.Type.ERROR) {
			return Flux.just(ChatResponseUtil.createResponse(result.getMessage()));
		}
		if (result.getType() == SyncResolveResult.Type.CLARIFY) {
			String questions = CollectionUtils.isEmpty(result.getClarifyQuestions()) ? "请补充同步任务信息。"
					: result.getClarifyQuestions().stream().collect(Collectors.joining("\n"));
			return Flux.just(ChatResponseUtil.createResponse("需要进一步确认：\n" + questions));
		}

		List<ChatResponse> chunks = new ArrayList<>();
		chunks.add(ChatResponseUtil.createResponse("已生成 SeaTunnel 作业配置（TABLE_COPY 全表同步）："));
		chunks.add(ChatResponseUtil.createPureResponse(TextType.CONFIG.getStartSign()));
		chunks.add(ChatResponseUtil.createResponse(result.getJobConfig()));
		chunks.add(ChatResponseUtil.createPureResponse(TextType.CONFIG.getEndSign()));

		if (savedToApproval) {
			chunks.add(ChatResponseUtil.createResponse("\n请前往 SeaTunnel 任务中心审核。"));
		}
		else if (saveError != null) {
			chunks.add(ChatResponseUtil.createResponse("\n配置已生成，但保存审批记录失败：" + saveError));
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
			log.debug("No query enhance output in state, using raw user input for SeaTunnel sync");
		}
		return userInput;
	}

}
