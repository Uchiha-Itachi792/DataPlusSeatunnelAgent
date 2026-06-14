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

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
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
 * 可行性评估节点，位于 {@code TableRelationNode} 之后。
 *
 * <p>
 * 在已召回 Schema、Evidence 和多轮上下文的前提下，调用 LLM 判断用户请求是否可被数据分析链路回答。
 * Prompt 模板见 {@code prompts/feasibility-assessment.txt}，输出三类需求类型之一：
 * <ul>
 * <li>《数据分析》— Schema/Evidence 能覆盖问题，{@link com.alibaba.cloud.ai.dataagent.workflow.dispatcher.FeasibilityAssessmentDispatcher}
 * 路由至 {@code PlannerNode}</li>
 * <li>《需要澄清》— 关键信息缺失或概念模糊，生成反问后图结束（{@code END}）</li>
 * <li>《自由闲聊》— Schema 为空且与业务无关，礼貌拒绝后图结束</li>
 * </ul>
 *
 * <p>
 * 与 {@code IntentRecognitionNode} 的分工：Intent 在无 Schema 阶段做粗分流；本节点在 Schema 就绪后做细粒度可行性判断。
 *
 * <p>
 * State 输入：canonical query、{@code TABLE_RELATION_OUTPUT}、{@code EVIDENCE}、{@code MULTI_TURN_CONTEXT}。
 * <br>
 * State 输出：{@code FEASIBILITY_ASSESSMENT_NODE_OUTPUT}（含【需求类型】【语种类型】【需求内容】）。
 */
@Slf4j
@Component
@AllArgsConstructor
public class FeasibilityAssessmentNode implements NodeAction {

	private final LlmService llmService;

	/**
	 * 执行可行性评估：组装 prompt → 流式调用 LLM → 将评估结果写入 state。
	 * @param state 图状态，含精筛后的 Schema 及上游上下文
	 * @return 含流式 generator 的 state 更新项，key 为 {@code FEASIBILITY_ASSESSMENT_NODE_OUTPUT}
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 获取canonical_query
		String canonicalQuery = StateUtil.getCanonicalQuery(state);

		// 获取召回的Schema
		SchemaDTO recalledSchema = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);

		// 获取证据信息
		String evidence = StateUtil.getStringValue(state, EVIDENCE);

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建可行性评估提示词
		String prompt = PromptHelper.buildFeasibilityAssessmentPrompt(canonicalQuery, recalledSchema, evidence,
				multiTurn);
		log.debug("Built feasibility assessment prompt as follows \n {} \n", prompt);

		// 调用LLM进行可行性评估
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		// LLM 流结束后将完整评估文本写入 FEASIBILITY_ASSESSMENT_NODE_OUTPUT，供 Dispatcher 解析【需求类型】
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在进行可行性评估...", "可行性评估完成！", llmOutput -> {
					// 获取评估结果
					String assessmentResult = llmOutput.trim();
					log.info("Feasibility assessment result: {}", assessmentResult);
					// 返回评估结果
					return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, assessmentResult);
				}, responseFlux);
		return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, generator);
	}

}
