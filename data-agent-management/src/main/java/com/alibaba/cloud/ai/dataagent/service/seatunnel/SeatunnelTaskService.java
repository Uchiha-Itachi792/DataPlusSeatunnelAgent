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
package com.alibaba.cloud.ai.dataagent.service.seatunnel;

import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskDTO;
import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskResult;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.LlmSyncTask;
import com.alibaba.cloud.ai.dataagent.dto.syncjob.SyncResolveResult;
import com.alibaba.cloud.ai.dataagent.entity.SeatunnelTask;
import com.alibaba.cloud.ai.dataagent.enums.SeatunnelTaskExecStatus;
import com.alibaba.cloud.ai.dataagent.enums.SyncMode;
import com.alibaba.cloud.ai.dataagent.mapper.SeatunnelTaskMapper;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.gateway.SeatunnelGatewayClient;
import com.alibaba.cloud.ai.dataagent.service.sync.catalog.SyncCatalogService;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SeaTunnel 任务审批服务：持久化、列表查询、忽略；以及预留的执行入口。
 * <p>
 * 当前 MVP 重点为 conf 生成与审核落库。{@link #execute(Integer)} 依赖独立 SeaTunnel Gateway （见
 * {@code docs/SEATUNNEL_EXTENSION_ROADMAP.md} 阶段 3），Gateway 未部署时非验收范围。
 */
@Slf4j
@Service
@AllArgsConstructor
public class SeatunnelTaskService {

	private final SeatunnelTaskMapper seatunnelTaskMapper;

	private final SeatunnelGatewayClient seatunnelGatewayClient;

	private final SyncCatalogService syncCatalogService;

	/**
	 * 将新链路解析结果写入审批表。
	 */
	public void save(SyncResolveResult result, Long agentId) {
		if (result.getType() != SyncResolveResult.Type.SUCCESS) {
			throw new IllegalArgumentException("仅 SUCCESS 类型的同步结果可保存");
		}
		if (!StringUtils.hasText(result.getJobConfig())) {
			throw new IllegalStateException("SeaTunnel conf 为空，无法保存审批记录");
		}
		LlmSyncTask plan = result.getPlan();
		Integer sourceDatasourceId = syncCatalogService.resolveDatasourceId(plan.getSource().getRef())
			.orElseThrow(() -> new IllegalStateException("无法解析源 ref：" + plan.getSource().getRef()));
		Integer sinkDatasourceId = syncCatalogService.resolveDatasourceId(plan.getSink().getRef())
			.orElseThrow(() -> new IllegalStateException("无法解析目标 ref：" + plan.getSink().getRef()));

		SeatunnelTask record = SeatunnelTask.builder()
			.agentId(agentId.intValue())
			.sourceDatasourceId(sourceDatasourceId)
			.sinkDatasourceId(sinkDatasourceId)
			.sourceTable(plan.getSource().getObject())
			.targetTable(plan.getSink().getObject())
			.jobConfig(result.getJobConfig())
			.resolveTrace(toJson(result.getTrace()))
			.syncPlan(toJson(plan))
			.syncMode(result.getSyncMode() != null ? result.getSyncMode().name() : SyncMode.SINGLE.name())
			.execStatus(SeatunnelTaskExecStatus.PENDING.getValue())
			.build();

		int rows = seatunnelTaskMapper.insert(record);
		if (rows <= 0) {
			throw new IllegalStateException("保存 SeaTunnel 审批记录失败，请稍后重试");
		}
		log.info("Saved seatunnel_task record id={} for agent={} via SyncOrchestrator", record.getId(), agentId);
	}

	/**
	 * 将生成的 SeaTunnel conf 写入审批表（legacy 路径，过渡期保留）。
	 */
	public void save(SeatunnelTaskResult result, Long agentId) {
		if (result.getType() == SeatunnelTaskResult.Type.ERROR) {
			throw new IllegalArgumentException("错误类型的 SeaTunnel 结果无需保存");
		}
		if (!StringUtils.hasText(result.getJobConfig())) {
			throw new IllegalStateException("SeaTunnel conf 为空，无法保存审批记录");
		}

		SeatunnelTask record = SeatunnelTask.builder()
			.agentId(agentId.intValue())
			.sourceDatasourceId(result.getSourceDatasourceId())
			.sinkDatasourceId(result.getSinkDatasourceId())
			.sourceTable(result.getSourceTable())
			.targetTable(result.getTargetTable())
			.jobConfig(result.getJobConfig())
			.execStatus(SeatunnelTaskExecStatus.PENDING.getValue())
			.build();

		int rows = seatunnelTaskMapper.insert(record);
		if (rows <= 0) {
			throw new IllegalStateException("保存 SeaTunnel 审批记录失败，请稍后重试");
		}
		log.info("Saved seatunnel_task record id={} for agent={}", record.getId(), agentId);
	}

	public List<SeatunnelTaskDTO> list(String status) {
		return seatunnelTaskMapper.selectAll(status).stream().map(this::toDto).toList();
	}

	/**
	 * 提交 SeaTunnel 作业（读库 conf → 调 Gateway）。
	 * <p>
	 * 完整状态机（RUNNING → 轮询 → SUCCESS/FAILED）待独立 Gateway 与 StatusPoller 落地后实现； 当前 submit
	 * 成功即标记 SUCCESS，仅作 Gateway 对接占位。
	 */
	public void execute(Integer id) {
		SeatunnelTask record = requirePendingRecord(id);
		updateStatus(record.getId(), SeatunnelTaskExecStatus.RUNNING, null, null, null);
		try {
			String jobId = seatunnelGatewayClient.submit(record.getJobConfig());
			updateStatus(record.getId(), SeatunnelTaskExecStatus.SUCCESS, jobId, null, LocalDateTime.now());
			log.info("Submitted seatunnel_task id={} successfully, jobId={}", id, jobId);
		}
		catch (Exception ex) {
			log.error("Failed to execute seatunnel_task id={}: {}", id, ex.getMessage());
			updateStatus(record.getId(), SeatunnelTaskExecStatus.FAILED, null, ex.getMessage(), LocalDateTime.now());
			throw ex instanceof IllegalStateException illegalStateException ? illegalStateException
					: new IllegalStateException("SeaTunnel 作业提交失败：" + ex.getMessage(), ex);
		}
	}

	/**
	 * 忽略待执行的 SeaTunnel 任务。
	 */
	public void ignore(Integer id) {
		SeatunnelTask record = requirePendingRecord(id);
		updateStatus(record.getId(), SeatunnelTaskExecStatus.IGNORED, null, null, null);
		log.info("Ignored seatunnel_task id={}", id);
	}

	private SeatunnelTask requirePendingRecord(Integer id) {
		SeatunnelTask record = seatunnelTaskMapper.selectById(id);
		if (record == null) {
			throw new IllegalArgumentException("审批记录不存在，请刷新列表后重试");
		}
		if (!SeatunnelTaskExecStatus.PENDING.getValue().equals(record.getExecStatus())) {
			SeatunnelTaskExecStatus current = SeatunnelTaskExecStatus.fromValue(record.getExecStatus());
			throw new IllegalStateException("该任务已处理（当前状态：" + current.getLabel() + "），无法重复操作");
		}
		return record;
	}

	private void updateStatus(Integer id, SeatunnelTaskExecStatus status, String externalJobId, String errorMsg,
			LocalDateTime execTime) {
		SeatunnelTask update = SeatunnelTask.builder()
			.id(id)
			.execStatus(status.getValue())
			.externalJobId(externalJobId)
			.errorMsg(errorMsg)
			.execTime(execTime)
			.build();
		seatunnelTaskMapper.updateStatus(update);
	}

	private String toJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("序列化同步计划失败", ex);
		}
	}

	private SeatunnelTaskDTO toDto(SeatunnelTask record) {
		SeatunnelTaskExecStatus status = SeatunnelTaskExecStatus.fromValue(record.getExecStatus());
		return SeatunnelTaskDTO.builder()
			.id(record.getId())
			.agentId(record.getAgentId())
			.sourceDatasourceId(record.getSourceDatasourceId())
			.sinkDatasourceId(record.getSinkDatasourceId())
			.sourceTable(record.getSourceTable())
			.targetTable(record.getTargetTable())
			.jobConfig(record.getJobConfig())
			.syncPlan(record.getSyncPlan())
			.resolveTrace(record.getResolveTrace())
			.syncMode(record.getSyncMode())
			.execStatus(record.getExecStatus())
			.execStatusLabel(status.getLabel())
			.externalJobId(record.getExternalJobId())
			.errorMsg(record.getErrorMsg())
			.createTime(record.getCreateTime())
			.execTime(record.getExecTime())
			.build();
	}

}
