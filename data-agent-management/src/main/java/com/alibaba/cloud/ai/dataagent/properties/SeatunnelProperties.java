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
package com.alibaba.cloud.ai.dataagent.properties;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * SeaTunnel 同步链路独立配置（与 SQL 同步轨 {@link DataAgentProperties.VectorStoreProperties} 隔离）。
 */
@Data
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".seatunnel")
public class SeatunnelProperties {

	private SchemaRecall schemaRecall = new SchemaRecall();

	private RelatedTable relatedTable = new RelatedTable();

	/**
	 * 默认作业模式。
	 */
	private String defaultJobMode = "BATCH";

	/**
	 * 默认并行度。
	 */
	private int defaultParallelism = 1;

	/**
	 * 启用的单步 syncKind 白名单。
	 */
	private List<String> enabledSyncKinds = new ArrayList<>(List.of("TABLE_COPY"));

	private Resolve resolve = new Resolve();

	private Spark spark = new Spark();

	public boolean isSyncKindEnabled(String syncKind) {
		return enabledSyncKinds != null && enabledSyncKinds.stream().anyMatch(k -> k.equalsIgnoreCase(syncKind));
	}

	@lombok.Getter
	@lombok.Setter
	public static class SchemaRecall {

		/**
		 * SeaTunnel Schema 向量召回表 TopK 上限。
		 */
		private int tableTopkLimit = 10;

		/**
		 * SeaTunnel Schema 向量召回相似度阈值。
		 */
		private double tableSimilarityThreshold = 0.2;

	}

	@lombok.Getter
	@lombok.Setter
	public static class RelatedTable {

		/**
		 * 是否根据外键/逻辑外键自动扩展关联表（过滤语义场景）。
		 */
		private boolean fkAutoExpandEnabled = true;

	}

	@lombok.Getter
	@lombok.Setter
	public static class Resolve {

		/**
		 * L1 Catalog 进 Prompt 的 ref TopK 上限。
		 */
		private int catalogTopk = 10;

		/**
		 * L2 每个 ref 对象索引 TopK 上限。
		 */
		private int objectIndexTopk = 8;

		/**
		 * 是否启用 Fast Path（M1 生效）。
		 */
		private boolean fastPathEnabled = true;

	}

	@lombok.Getter
	@lombok.Setter
	public static class Spark {

		/**
		 * 是否启用 Spark 步（M5 生效）。
		 */
		private boolean enabled = false;

		/**
		 * Spark 执行器类型：cli 等。
		 */
		private String executorType = "cli";

	}

}
