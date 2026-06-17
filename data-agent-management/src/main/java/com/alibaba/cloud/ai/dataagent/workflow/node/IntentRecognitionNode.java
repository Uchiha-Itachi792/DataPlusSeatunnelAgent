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

import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 工作流首站：意图识别（闲聊 / 数据分析 / 数据同步）。
 * <p>
 * 数据流（仅列本仓库内可跳转的类）：
 * <ol>
 * <li>本类 {@code apply} 返回 {@code Map.of(INTENT_RECOGNITION_NODE_OUTPUT, generator)}，其中 generator 是 Flux</li>
 * <li>{@code GraphServiceImpl.subscribeToFlux} 订阅 {@code compiledGraph.stream()}，收到本节点的 StreamingOutput chunk 后写入 SSE sink</li>
 * <li>generator 流结束、上方 {@code result -> ...} 解析出 DTO 后，{@code INTENT_RECOGNITION_NODE_OUTPUT} 进入 state</li>
 * <li>{@code IntentRecognitionDispatcher} 读取该字段，决定走 {@code EvidenceRecallNode} 或 {@code END}</li>
 * </ol>
 * 本节点内无需 {@code subscribe}；generator 的订阅与 chunk 透传由依赖库 {@code spring-ai-alibaba-graph-core}
 *（{@code CompiledGraph} 运行时）在 {@code compiledGraph.stream()} 执行过程中完成，源码不在本仓库。
 */
@Slf4j
@Component
@AllArgsConstructor
public class IntentRecognitionNode implements NodeAction {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	/**
	 * 节点入口：组装 prompt 与 generator，立即返回 Map（不阻塞等 LLM 跑完）。
	 * LLM 实际在 {@code compiledGraph.stream()} 被订阅后才开始输出；详见类注释中的数据流说明。
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// --- 1. 从 OverAllState 读取本轮输入（由 GraphServiceImpl.stream(Map.of(...)) 注入）---
		String userInput = StateUtil.getStringValue(state, INPUT_KEY);
		log.info("User input for intent recognition: {}", userInput);

		// 多轮历史副本（源数据在 MultiTurnContextManager，经 MULTI_TURN_CONTEXT 注入 state）
		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// --- 2. 渲染 prompt 模板（intent-recognition.txt）---
		String prompt = PromptHelper.buildIntentRecognitionPrompt(multiTurn, userInput);
		log.debug("Built intent recognition prompt as follows \n {} \n", prompt);

		// --- 3. 流式调用 LLM；返回的是 token 级 Flux，尚未得到完整 JSON ---
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		// --- 4. 包装为图引擎可识别的流式 generator ---
		// concat 顺序：preFlux → responseFlux(LLM) → sufFlux
		// 每个 ChatResponse 会被转为 StreamingOutput chunk，最终进入 compiledGraph.stream()
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				// preFlux：LLM 之前推给前端的提示 + JSON 类型起始标记（$$$json，本身不展示）
				Flux.just(ChatResponseUtil.createResponse("正在进行意图识别..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				// sufFlux：JSON 结束标记（$$$）+ 完成提示
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n意图识别完成！")),
				// 流结束回调（见 FluxUtil：最后会 emit GraphResponse.done(本 Map)）
				// result = 整段 LLM 文本；解析为 DTO 后写入 INTENT_RECOGNITION_NODE_OUTPUT，供 Dispatcher 路由
				result -> {
					IntentRecognitionOutputDTO intentRecognitionOutput = jsonParseUtil.tryConvertToObject(result,
							IntentRecognitionOutputDTO.class);
					return Map.of(INTENT_RECOGNITION_NODE_OUTPUT, intentRecognitionOutput);
				});

		// --- 5. 返回 Map：value 是 generator（Flux），不是最终的 IntentRecognitionOutputDTO ---
		// DTO 要等 generator 流跑完、上方 result 回调执行后才进入 state
		// 前端看到的流式文字：generator → compiledGraph.stream → GraphServiceImpl.handleStreamNodeOutput → sink
		return Map.of(INTENT_RECOGNITION_NODE_OUTPUT, generator);
	}

}
