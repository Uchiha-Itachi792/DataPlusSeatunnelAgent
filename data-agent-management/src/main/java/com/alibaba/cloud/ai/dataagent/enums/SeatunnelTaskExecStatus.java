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
package com.alibaba.cloud.ai.dataagent.enums;

import lombok.Getter;

/**
 * SeaTunnel 任务审批执行状态。
 */
@Getter
public enum SeatunnelTaskExecStatus {

	PENDING("PENDING", "未执行"),

	RUNNING("RUNNING", "执行中"),

	SUCCESS("SUCCESS", "成功执行"),

	IGNORED("IGNORED", "忽略"),

	FAILED("FAILED", "失败");

	private final String value;

	private final String label;

	SeatunnelTaskExecStatus(String value, String label) {
		this.value = value;
		this.label = label;
	}

	public static SeatunnelTaskExecStatus fromValue(String value) {
		for (SeatunnelTaskExecStatus status : SeatunnelTaskExecStatus.values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown seatunnel task exec status: " + value);
	}

}
