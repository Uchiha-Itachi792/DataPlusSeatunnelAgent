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

/**
 * SeaTunnel 同步链路独立配置（与 SQL 同步轨 {@link DataAgentProperties.VectorStoreProperties} 隔离）。
 */
@Data
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".seatunnel")
public class SeatunnelProperties {

	private SchemaRecall schemaRecall = new SchemaRecall();

	private RelatedTable relatedTable = new RelatedTable();

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

}
