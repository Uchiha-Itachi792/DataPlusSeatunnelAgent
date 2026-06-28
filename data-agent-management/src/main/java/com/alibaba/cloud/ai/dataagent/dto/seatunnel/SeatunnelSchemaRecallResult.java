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
package com.alibaba.cloud.ai.dataagent.dto.seatunnel;

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * SeaTunnel 链路 Schema 向量召回结果（与 SQL 同步轨 {@code SyncSchemaRecallResult} 隔离）。
 */
@Data
@Builder
public class SeatunnelSchemaRecallResult {

	@Builder.Default
	private List<Document> tableDocuments = new ArrayList<>();

	@Builder.Default
	private List<Document> columnDocuments = new ArrayList<>();

	private SchemaDTO schemaDTO;

	@Builder.Default
	private List<String> recalledTableNames = new ArrayList<>();

}
