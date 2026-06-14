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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.DB_DIALECT_TYPE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.EVIDENCE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.GENEGRATED_SEMANTIC_MODEL_PROMPT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_SCHEMA_MISSING_ADVICE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_EXCEPTION_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_RETRY_COUNT;
import static com.alibaba.cloud.ai.dataagent.prompt.PromptHelper.buildSemanticModelPrompt;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 表关系推断节点，位于 {@code SchemaRecallNode} 之后。
 *
 * <p>
 * 将向量召回的表/列 Document 组装为结构化 {@link SchemaDTO}，合并物理外键与数据源配置的逻辑外键，
 * 再调用 LLM（{@link Nl2SqlService#fineSelect}）按用户问题与 Evidence 精筛相关表，
 * 最终写入 {@code TABLE_RELATION_OUTPUT}，供 SqlGenerate、Planner、FeasibilityAssessment 等下游节点使用。
 *
 * <p>
 * 主要职责：
 * <ul>
 * <li>从召回 Document 构建初始 Schema，并按外键补拉缺失的关联表</li>
 * <li>合并数据源级逻辑外键，辅助 JOIN 推断</li>
 * <li>LLM 精筛表（支持 SQL 生成失败后的 schema 补建议重试）</li>
 * <li>按最终表名加载语义模型，生成 {@code GENEGRATED_SEMANTIC_MODEL_PROMPT}</li>
 * </ul>
 *
 * <p>
 * State 输入：{@code TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT}、{@code COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT}、
 * {@code EVIDENCE}、{@code AGENT_ID}、canonical query；可选 {@code SQL_GENERATE_SCHEMA_MISSING_ADVICE}。
 * <br>
 * State 输出：{@code TABLE_RELATION_OUTPUT}、{@code DB_DIALECT_TYPE}、
 * {@code GENEGRATED_SEMANTIC_MODEL_PROMPT}、{@code TABLE_RELATION_RETRY_COUNT}、
 * {@code TABLE_RELATION_EXCEPTION_OUTPUT}。
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class TableRelationNode implements NodeAction {

	private final SchemaService schemaService;

	private final Nl2SqlService nl2SqlService;

	private final SemanticModelService semanticModelService;

	private final DatabaseUtil databaseUtil;

	private final DatasourceService datasourceService;

	private final AgentDatasourceService agentDatasourceService;

	/**
	 * 执行表关系推断与 Schema 精筛。
	 *
	 * <p>
	 * 同步阶段完成初始 Schema 组装；异步流式阶段调用 LLM 选表并在完成后写入 resultMap。
	 * {@code DB_DIALECT_TYPE} 等字段需立即返回，以便后续节点在 generator 完成前即可读取方言信息。
	 * @param state 图状态，含 SchemaRecall 产出的表/列 Document 及上游上下文
	 * @return 含流式 generator 与立即可用的 state 更新项
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// --- 1. 读取上游输入 ---
		String canonicalQuery = StateUtil.getCanonicalQuery(state);

		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		List<Document> tableDocuments = StateUtil.getDocumentList(state, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT);
		List<Document> columnDocuments = StateUtil.getDocumentList(state, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT);
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);

		// --- 2. 同步构建初始 Schema（含逻辑外键） ---
		DbConfigBO agentDbConfig = databaseUtil.getAgentDbConfig(Long.valueOf(agentIdStr));

		List<String> logicalForeignKeys = getLogicalForeignKeys(Long.valueOf(agentIdStr), tableDocuments);
		log.info("Found {} logical foreign keys for agent: {}", logicalForeignKeys.size(), agentIdStr);

		SchemaDTO initialSchema = buildInitialSchema(agentIdStr, columnDocuments, tableDocuments, agentDbConfig,
				logicalForeignKeys);

		Map<String, Object> resultMap = new HashMap<>();
		// generator 完成时一次性写入 state；DB_DIALECT_TYPE 同时也在 return 中立即暴露
		resultMap.put(DB_DIALECT_TYPE, agentDbConfig.getDialectType());
		resultMap.put(TABLE_RELATION_RETRY_COUNT, 0);
		resultMap.put(TABLE_RELATION_EXCEPTION_OUTPUT, "");

		// --- 3. 异步 LLM 精筛表，完成后写入 TABLE_RELATION_OUTPUT 与语义模型 prompt ---
		Flux<ChatResponse> schemaFlux = processSchemaSelection(initialSchema, canonicalQuery, evidence, state,
				agentDbConfig, result -> {
					log.info("[{}] Schema processing result: {}", this.getClass().getSimpleName(), result);
					resultMap.put(TABLE_RELATION_OUTPUT, result);

					List<String> tableNames = result.getTable().stream().map(TableDTO::getName).toList();

					List<SemanticModel> semanticModels = semanticModelService
						.getByAgentIdAndTableNames(Long.valueOf(agentIdStr), tableNames);

					String semanticModelPrompt = buildSemanticModelPrompt(semanticModels);
					resultMap.put(GENEGRATED_SEMANTIC_MODEL_PROMPT, semanticModelPrompt);
				});

		// --- 4. 组装前端展示流（进度文案 + LLM 选表流） ---
		Flux<ChatResponse> preFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始构建初始Schema..."));
			emitter.next(ChatResponseUtil.createResponse("初始Schema构建完成."));
			emitter.complete();
		});
		Flux<ChatResponse> displayFlux = preFlux.concatWith(schemaFlux).concatWith(Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始处理Schema选择..."));
			emitter.next(ChatResponseUtil.createResponse("Schema选择处理完成."));
			emitter.complete();
		}));

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, v -> resultMap, displayFlux);

		return Map.of(TABLE_RELATION_OUTPUT, generator, DB_DIALECT_TYPE, agentDbConfig.getDialectType(),
				TABLE_RELATION_RETRY_COUNT, 0, TABLE_RELATION_EXCEPTION_OUTPUT, "");
	}

	/**
	 * 将召回的表/列 Document 转为初始 {@link SchemaDTO}，并合并逻辑外键。
	 * @param agentId Agent ID
	 * @param columnDocuments 召回的列 Document 列表
	 * @param tableDocuments 召回的表 Document 列表
	 * @param agentDbConfig Agent 绑定的数据库配置
	 * @param logicalForeignKeys 与召回表相关的逻辑外键（格式 {@code 源表.源列=目标表.目标列}）
	 * @return 候选 Schema，尚未经 LLM 精筛
	 */
	private SchemaDTO buildInitialSchema(String agentId, List<Document> columnDocuments, List<Document> tableDocuments,
			DbConfigBO agentDbConfig, List<String> logicalForeignKeys) {
		SchemaDTO schemaDTO = new SchemaDTO();

		schemaService.extractDatabaseName(schemaDTO, agentDbConfig);
		// buildSchemaFromDocuments 会按物理外键补拉缺失的关联表/列
		schemaService.buildSchemaFromDocuments(agentId, columnDocuments, tableDocuments, schemaDTO);

		// 合并数据源配置的逻辑外键（无物理 FK 时仍可用于 JOIN 推断）
		if (logicalForeignKeys != null && !logicalForeignKeys.isEmpty()) {
			List<String> existingForeignKeys = schemaDTO.getForeignKeys();
			if (existingForeignKeys == null || existingForeignKeys.isEmpty()) {
				// 如果没有现有外键，直接设置
				schemaDTO.setForeignKeys(logicalForeignKeys);
			}
			else {
				// 合并现有外键和逻辑外键
				List<String> allForeignKeys = new ArrayList<>(existingForeignKeys);
				allForeignKeys.addAll(logicalForeignKeys);
				schemaDTO.setForeignKeys(allForeignKeys);
			}
			log.info("Merged {} logical foreign keys into schema for agent: {}", logicalForeignKeys.size(), agentId);
		}

		return schemaDTO;
	}

	/**
	 * 调用 LLM 对候选 Schema 做精筛，可选地根据 SQL 生成阶段的缺表建议补选表。
	 * @param schemaDTO 初始候选 Schema，方法内会原地删除未被选中的表
	 * @param input 规范化后的用户问题
	 * @param evidence 业务知识 Evidence
	 * @param state 图状态，用于读取 {@code SQL_GENERATE_SCHEMA_MISSING_ADVICE}
	 * @param agentDbConfig 数据库配置
	 * @param dtoConsumer LLM 选表完成后的回调，接收精筛后的 SchemaDTO
	 * @return 含进度提示与 LLM 流式响应的 Flux
	 */
	private Flux<ChatResponse> processSchemaSelection(SchemaDTO schemaDTO, String input, String evidence,
			OverAllState state, DbConfigBO agentDbConfig, Consumer<SchemaDTO> dtoConsumer) {
		String schemaAdvice = StateUtil.getStringValue(state, SQL_GENERATE_SCHEMA_MISSING_ADVICE, null);

		Flux<ChatResponse> schemaFlux;
		if (schemaAdvice != null) {
			log.info("[{}] Processing with schema supplement advice: {}", this.getClass().getSimpleName(),
					schemaAdvice);
			schemaFlux = nl2SqlService.fineSelect(schemaDTO, input, evidence, schemaAdvice, agentDbConfig, dtoConsumer);
		}
		else {
			log.info("[{}] Executing regular schema selection", this.getClass().getSimpleName());
			schemaFlux = nl2SqlService.fineSelect(schemaDTO, input, evidence, null, agentDbConfig, dtoConsumer);
		}
		return Flux
			.just(ChatResponseUtil.createResponse("正在选择合适的数据表...\n"),
					ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()))
			.concatWith(schemaFlux)
			.concatWith(Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
					ChatResponseUtil.createResponse("\n\n选择数据表完成。")));
	}

	/**
	 * 查询 Agent 当前数据源的逻辑外键，并过滤出与召回表相关的外键。
	 * @param agentId Agent ID
	 * @param tableDocuments SchemaRecall 召回的表 Document，用于提取表名白名单
	 * @return 格式化外键列表，元素形如 {@code orders.id=order_items.order_id}；异常或无数据源时返回空列表
	 */
	private List<String> getLogicalForeignKeys(Long agentId, List<Document> tableDocuments) {
		try {
			// 获取当前 agent 激活的数据源
			AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			if (agentDatasource == null || agentDatasource.getDatasourceId() == null) {
				log.warn("No active datasource found for agent: {}", agentId);
				return Collections.emptyList();
			}

			Integer datasourceId = agentDatasource.getDatasourceId();

			// 从 tableDocuments 提取表名列表
			Set<String> recalledTableNames = tableDocuments.stream()
				.map(doc -> (String) doc.getMetadata().get("name"))
				.filter(name -> name != null && !name.isEmpty())
				.collect(Collectors.toSet());

			log.info("Recalled table names for agent {}: {}", agentId, recalledTableNames);

			// 查询该数据源的所有逻辑外键
			List<LogicalRelation> allLogicalRelations = datasourceService.getLogicalRelations(datasourceId);
			log.info("Found {} logical relations in datasource: {}", allLogicalRelations.size(), datasourceId);

			// 过滤只保留与召回表相关的外键（源表或目标表在召回列表中）
			List<String> formattedForeignKeys = allLogicalRelations.stream()
				.filter(lr -> recalledTableNames.contains(lr.getSourceTableName())
						|| recalledTableNames.contains(lr.getTargetTableName()))
				.map(lr -> String.format("%s.%s=%s.%s", lr.getSourceTableName(), lr.getSourceColumnName(),
						lr.getTargetTableName(), lr.getTargetColumnName()))
				.distinct()
				.collect(Collectors.toList());

			log.info("Filtered {} relevant logical relations for recalled tables", formattedForeignKeys.size());
			return formattedForeignKeys;
		}
		catch (Exception e) {
			log.error("Error fetching logical foreign keys for agent: {}", agentId, e);
			return Collections.emptyList();
		}
	}

}
