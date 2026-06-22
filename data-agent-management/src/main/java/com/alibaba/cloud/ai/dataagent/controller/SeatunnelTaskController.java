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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.seatunnel.SeatunnelTaskDTO;
import com.alibaba.cloud.ai.dataagent.service.seatunnel.SeatunnelTaskService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SeaTunnel 任务审批 REST API。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/seatunnel-task")
public class SeatunnelTaskController {

	private final SeatunnelTaskService seatunnelTaskService;

	@GetMapping("/list")
	public ApiResponse<List<SeatunnelTaskDTO>> list(@RequestParam(required = false) String status) {
		try {
			return ApiResponse.success("获取 SeaTunnel 任务列表成功", seatunnelTaskService.list(status));
		}
		catch (Exception e) {
			return ApiResponse.error("获取 SeaTunnel 任务列表失败：" + e.getMessage());
		}
	}

	@PostMapping("/{id}/execute")
	public ApiResponse<String> execute(@PathVariable Integer id) {
		try {
			seatunnelTaskService.execute(id);
			return ApiResponse.success("SeaTunnel 作业提交成功");
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return ApiResponse.error(e.getMessage());
		}
		catch (Exception e) {
			return ApiResponse.error("执行失败：" + e.getMessage());
		}
	}

	@PostMapping("/{id}/ignore")
	public ApiResponse<String> ignore(@PathVariable Integer id) {
		try {
			seatunnelTaskService.ignore(id);
			return ApiResponse.success("已忽略该任务");
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return ApiResponse.error(e.getMessage());
		}
		catch (Exception e) {
			return ApiResponse.error("忽略失败：" + e.getMessage());
		}
	}

}
