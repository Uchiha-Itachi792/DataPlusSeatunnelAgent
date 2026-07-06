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
 * 渐进式解析阶段（L1～L4），{@link #order} 显式定义流水线顺序，不依赖 {@link Enum#ordinal()}。
 */
@Getter
public enum ResolvePhase {

	SYSTEM(1, "L1 系统定位", "确定 sourceRef / targetRef（从哪个数据源读、写到哪个数据源）"),

	OBJECT(2, "L2 对象定位", "确定 source / sink 表或文件（DataPointer）"),

	INTENT(3, "L3 同步意图", "判定 syncKind（如 TABLE_COPY）或 Pipeline 骨架"),

	SQL(4, "L4 SQL", "生成 SELECT SQL（JDBC）或 LoadStepSpec（文件源）");

	private final int order;

	private final String displayName;

	private final String description;

	ResolvePhase(int order, String displayName, String description) {
		this.order = order;
		this.displayName = displayName;
		this.description = description;
	}

	/**
	 * 从 {@code startPhase} 续跑时，是否应执行本阶段。
	 * <p>
	 * {@code startPhase} 为 null 时视为从 L1 开始；仅执行 order 大于等于起始阶段的层。
	 *
	 * @param startPhase 起始阶段（首次 resolve 为 SYSTEM；澄清续跑为 pendingPhase）
	 * @return 是否应执行本阶段
	 */
	public boolean shouldRunFrom(ResolvePhase startPhase) {
		int startOrder = startPhase != null ? startPhase.order : SYSTEM.order;
		return startOrder <= this.order;
	}

}
