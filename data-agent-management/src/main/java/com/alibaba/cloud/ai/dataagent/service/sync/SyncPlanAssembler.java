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
package com.alibaba.cloud.ai.dataagent.service.sync;

import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmObjectResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncIntent;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncSql;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncTask;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSystemResolve;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.MockSyncLayerResult;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.ResolveTrace;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SyncPlanConsistencyValidator;
import com.alibaba.cloud.ai.dataagent.service.sync.validator.SystemResolveValidator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 各层校验通过后合并 LlmSyncTask 与 ResolveTrace。
 */
@Component
@AllArgsConstructor
public class SyncPlanAssembler {

	private final SystemResolveValidator systemResolveValidator;

	private final SyncPlanConsistencyValidator syncPlanConsistencyValidator;

	public AssemblyResult assemble(Long agentId, MockSyncLayerResult layers) {
		List<String> clarify = collectClarifyQuestions(layers);
		if (!clarify.isEmpty()) {
			return AssemblyResult.clarify(clarify);
		}

		systemResolveValidator.validate(agentId, layers.getSystem());
		syncPlanConsistencyValidator.validateObjectResolve(agentId, layers.getObject());

		LlmSyncTask plan = LlmSyncTask.builder()
			.schemaVersion("1.0")
			.syncKind(layers.getIntent().getSyncKind())
			.source(layers.getObject().getSource())
			.sink(layers.getObject().getSink())
			.others(layers.getObject().getOthers() != null ? layers.getObject().getOthers() : new ArrayList<>())
			.sql(layers.getSql() != null ? layers.getSql().getSql() : null)
			.writeMode(layers.getIntent().getWriteMode())
			.build();

		syncPlanConsistencyValidator.validatePlan(agentId, plan);

		ResolveTrace trace = ResolveTrace.builder()
			.system(layers.getSystem())
			.object(layers.getObject())
			.intent(layers.getIntent())
			.sql(layers.getSql())
			.build();

		return AssemblyResult.success(plan, trace);
	}

	private List<String> collectClarifyQuestions(MockSyncLayerResult layers) {
		List<String> questions = new ArrayList<>();
		addClarify(layers.getSystem(), questions);
		addClarify(layers.getObject(), questions);
		addClarify(layers.getIntent(), questions);
		addClarify(layers.getSql(), questions);
		return questions;
	}

	private void addClarify(LlmSystemResolve layer, List<String> questions) {
		if (layer != null && layer.isNeedClarify() && !CollectionUtils.isEmpty(layer.getClarifyQuestions())) {
			questions.addAll(layer.getClarifyQuestions());
		}
	}

	private void addClarify(LlmObjectResolve layer, List<String> questions) {
		if (layer != null && layer.isNeedClarify() && !CollectionUtils.isEmpty(layer.getClarifyQuestions())) {
			questions.addAll(layer.getClarifyQuestions());
		}
	}

	private void addClarify(LlmSyncIntent layer, List<String> questions) {
		if (layer != null && layer.isNeedClarify() && !CollectionUtils.isEmpty(layer.getClarifyQuestions())) {
			questions.addAll(layer.getClarifyQuestions());
		}
	}

	private void addClarify(LlmSyncSql layer, List<String> questions) {
		if (layer != null && layer.isNeedClarify() && !CollectionUtils.isEmpty(layer.getClarifyQuestions())) {
			questions.addAll(layer.getClarifyQuestions());
		}
	}

	public record AssemblyResult(LlmSyncTask plan, ResolveTrace trace, List<String> clarifyQuestions) {

		static AssemblyResult success(LlmSyncTask plan, ResolveTrace trace) {
			return new AssemblyResult(plan, trace, List.of());
		}

		static AssemblyResult clarify(List<String> questions) {
			return new AssemblyResult(null, null, questions);
		}

		boolean needsClarify() {
			return !CollectionUtils.isEmpty(clarifyQuestions);
		}

	}

}
