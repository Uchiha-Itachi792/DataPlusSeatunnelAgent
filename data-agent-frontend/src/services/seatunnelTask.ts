/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import axios from 'axios';
import type { ApiResponse } from './common';

export type SeatunnelTaskExecStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'IGNORED' | 'FAILED';

export interface SeatunnelTaskItem {
  id: number;
  agentId: number;
  sourceDatasourceId: number;
  sinkDatasourceId: number;
  sourceTable: string;
  targetTable: string;
  jobConfig: string;
  execStatus: SeatunnelTaskExecStatus;
  execStatusLabel: string;
  externalJobId?: string;
  errorMsg?: string;
  createTime?: string;
  execTime?: string;
}

const API_BASE_URL = '/api/seatunnel-task';

class SeatunnelTaskApiService {
  async list(status?: string): Promise<SeatunnelTaskItem[]> {
    const response = await axios.get<ApiResponse<SeatunnelTaskItem[]>>(`${API_BASE_URL}/list`, {
      params: status ? { status } : {},
    });
    if (!response.data.success) {
      throw new Error(response.data.message || '获取列表失败');
    }
    return response.data.data || [];
  }

  async execute(id: number): Promise<ApiResponse<string>> {
    const response = await axios.post<ApiResponse<string>>(`${API_BASE_URL}/${id}/execute`);
    return response.data;
  }

  async ignore(id: number): Promise<ApiResponse<string>> {
    const response = await axios.post<ApiResponse<string>>(`${API_BASE_URL}/${id}/ignore`);
    return response.data;
  }
}

export default new SeatunnelTaskApiService();
