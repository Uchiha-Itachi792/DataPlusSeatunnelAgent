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

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_CAPABILITY_UNDER_DEVELOPMENT_MSG;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SYNC_TASK_STUB_NODE_OUTPUT;

/**
 * 数据同步任务占位节点：在意图识别为《数据同步任务》时输出固定提示并结束。
 * <p>
 * 后续 SeaTunnel 接入时可替换为配置生成与作业提交节点。
 */
@Slf4j
@Component
public class SyncTaskStubNode implements NodeAction {

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		log.info("Data sync task detected, returning under-development placeholder message");

		Flux<ChatResponse> messageFlux = Flux
			.just(ChatResponseUtil.createResponse(SYNC_CAPABILITY_UNDER_DEVELOPMENT_MSG));

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(
				this.getClass(), state, null, null, ignored -> Map.of(), messageFlux);

		return Map.of(SYNC_TASK_STUB_NODE_OUTPUT, generator);
	}

}
