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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.SeatunnelTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SeatunnelTaskMapper {

	@Insert("""
			INSERT INTO seatunnel_task (agent_id, source_datasource_id, sink_datasource_id, source_table, target_table,
			                            job_config, resolve_trace, sync_plan, sync_mode, exec_status, create_time, update_time)
			VALUES (#{agentId}, #{sourceDatasourceId}, #{sinkDatasourceId}, #{sourceTable}, #{targetTable},
			        #{jobConfig}, #{resolveTrace}, #{syncPlan}, #{syncMode}, #{execStatus}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SeatunnelTask task);

	@Select("""
			<script>
			SELECT id, agent_id, source_datasource_id, sink_datasource_id, source_table, target_table, job_config,
			       resolve_trace, sync_plan, sync_mode, exec_status, external_job_id, error_msg, create_time, update_time, exec_time
			FROM seatunnel_task
			<where>
			  <if test='status != null and status != ""'>
			    AND exec_status = #{status}
			  </if>
			</where>
			ORDER BY create_time DESC
			</script>
			""")
	List<SeatunnelTask> selectAll(@Param("status") String status);

	@Select("""
			SELECT id, agent_id, source_datasource_id, sink_datasource_id, source_table, target_table, job_config,
			       resolve_trace, sync_plan, sync_mode, exec_status, external_job_id, error_msg, create_time, update_time, exec_time
			FROM seatunnel_task WHERE id = #{id}
			""")
	SeatunnelTask selectById(@Param("id") Integer id);

	@Update("""
			UPDATE seatunnel_task
			SET exec_status = #{execStatus},
			    external_job_id = #{externalJobId},
			    error_msg = #{errorMsg},
			    exec_time = #{execTime},
			    update_time = NOW()
			WHERE id = #{id}
			""")
	int updateStatus(SeatunnelTask task);

}
