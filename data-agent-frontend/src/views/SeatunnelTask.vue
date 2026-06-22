<!--
 * Copyright 2025 the original author or authors.
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
-->
<template>
  <BaseLayout>
    <div class="seatunnel-task-page">
      <main class="main-content">
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">SeaTunnel 任务</h1>
            <p class="content-subtitle">查看并审批 SeaTunnel 同步任务配置，支持提交执行或忽略</p>
          </div>
        </div>

        <div class="action-section">
          <el-card>
            <div class="action-content">
              <div class="action-buttons">
                <el-button :icon="Refresh" @click="loadList" size="large">刷新</el-button>
              </div>
              <div class="filter-options">
                <el-select
                  v-model="statusFilter"
                  placeholder="筛选执行状态"
                  size="large"
                  clearable
                  style="width: 220px"
                  @change="loadList"
                >
                  <el-option label="全部" value="" />
                  <el-option label="未执行" value="PENDING" />
                  <el-option label="执行中" value="RUNNING" />
                  <el-option label="成功执行" value="SUCCESS" />
                  <el-option label="忽略" value="IGNORED" />
                  <el-option label="失败" value="FAILED" />
                </el-select>
              </div>
            </div>
          </el-card>
        </div>

        <div class="table-section">
          <el-card>
            <el-table v-loading="loading" :data="items" style="width: 100%" stripe>
              <el-table-column prop="sourceTable" label="源表" width="140" />
              <el-table-column prop="targetTable" label="目标表" width="140" />
              <el-table-column prop="jobConfig" label="SeaTunnel conf" min-width="280" show-overflow-tooltip>
                <template #default="scope">
                  <el-link type="primary" @click="showConfigPreview(scope.row)">查看配置</el-link>
                </template>
              </el-table-column>
              <el-table-column prop="execStatusLabel" label="执行状态" width="120">
                <template #default="scope">
                  <el-tag :type="getStatusTagType(scope.row.execStatus)" size="small">
                    {{ scope.row.execStatusLabel }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
              <el-table-column prop="createTime" label="创建时间" width="180" />
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="scope">
                  <template v-if="scope.row.execStatus === 'PENDING'">
                    <el-button
                      type="primary"
                      size="small"
                      :loading="executingId === scope.row.id"
                      @click="handleExecute(scope.row)"
                    >
                      执行
                    </el-button>
                    <el-button
                      size="small"
                      :loading="ignoringId === scope.row.id"
                      @click="handleIgnore(scope.row)"
                    >
                      忽略
                    </el-button>
                  </template>
                  <el-tooltip v-else content="该任务已处理，无法再次操作" placement="top">
                    <span class="text-muted">已处理</span>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!loading && items.length === 0" description="暂无待审批的 SeaTunnel 任务" />
          </el-card>
        </div>
      </main>

      <el-dialog v-model="configPreviewVisible" title="SeaTunnel conf 预览" width="720px">
        <pre class="config-preview"><code v-html="highlightedConfig"></code></pre>
      </el-dialog>
    </div>
  </BaseLayout>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { Refresh } from '@element-plus/icons-vue';
  import hljs from 'highlight.js/lib/core';
  import iniLang from 'highlight.js/lib/languages/ini';
  import BaseLayout from '@/layouts/BaseLayout.vue';
  import seatunnelTaskService, {
    type SeatunnelTaskItem,
    type SeatunnelTaskExecStatus,
  } from '@/services/seatunnelTask';

  hljs.registerLanguage('ini', iniLang);

  const loading = ref(false);
  const items = ref<SeatunnelTaskItem[]>([]);
  const statusFilter = ref('');
  const executingId = ref<number | null>(null);
  const ignoringId = ref<number | null>(null);
  const configPreviewVisible = ref(false);
  const previewConfig = ref('');

  const highlightedConfig = computed(() => {
    if (!previewConfig.value) {
      return '';
    }
    return hljs.highlight(previewConfig.value, { language: 'ini' }).value;
  });

  const getStatusTagType = (status: SeatunnelTaskExecStatus) => {
    switch (status) {
      case 'PENDING':
        return 'warning';
      case 'RUNNING':
        return 'primary';
      case 'SUCCESS':
        return 'success';
      case 'IGNORED':
        return 'info';
      case 'FAILED':
        return 'danger';
      default:
        return 'info';
    }
  };

  const loadList = async () => {
    loading.value = true;
    try {
      items.value = await seatunnelTaskService.list(statusFilter.value || undefined);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '加载列表失败，请稍后重试';
      ElMessage.error(message);
    } finally {
      loading.value = false;
    }
  };

  const showConfigPreview = (row: SeatunnelTaskItem) => {
    previewConfig.value = row.jobConfig;
    configPreviewVisible.value = true;
  };

  const handleExecute = async (row: SeatunnelTaskItem) => {
    try {
      await ElMessageBox.confirm(
        '确认提交该 SeaTunnel 作业？需已配置 Gateway 服务。',
        '执行确认',
        { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }

    executingId.value = row.id;
    try {
      const result = await seatunnelTaskService.execute(row.id);
      if (result.success) {
        ElMessage.success(result.message || 'SeaTunnel 作业提交成功');
        await loadList();
      } else {
        ElMessage.error(result.message || 'SeaTunnel 作业提交失败');
        await loadList();
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '执行请求失败，请检查网络或后端服务';
      ElMessage.error(message);
    } finally {
      executingId.value = null;
    }
  };

  const handleIgnore = async (row: SeatunnelTaskItem) => {
    try {
      await ElMessageBox.confirm('确认忽略该任务？忽略后将无法再次执行。', '忽略确认', {
        type: 'warning',
        confirmButtonText: '确认忽略',
        cancelButtonText: '取消',
      });
    } catch {
      return;
    }

    ignoringId.value = row.id;
    try {
      const result = await seatunnelTaskService.ignore(row.id);
      if (result.success) {
        ElMessage.success(result.message || '已忽略该任务');
        await loadList();
      } else {
        ElMessage.error(result.message || '忽略失败');
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '忽略请求失败，请检查网络或后端服务';
      ElMessage.error(message);
    } finally {
      ignoringId.value = null;
    }
  };

  onMounted(() => {
    loadList();
  });
</script>

<style scoped>
  .seatunnel-task-page {
    padding: 2rem;
    max-width: 1400px;
    margin: 0 auto;
  }

  .content-header {
    margin-bottom: 1.5rem;
  }

  .content-title {
    font-size: 1.75rem;
    font-weight: 600;
    color: #1e293b;
    margin: 0 0 0.5rem;
  }

  .content-subtitle {
    color: #64748b;
    margin: 0;
  }

  .action-section {
    margin-bottom: 1.5rem;
  }

  .action-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 1rem;
  }

  .table-section {
    margin-bottom: 2rem;
  }

  .text-muted {
    color: #94a3b8;
    font-size: 0.875rem;
  }

  .config-preview {
    background: #1e293b;
    color: #e2e8f0;
    padding: 1rem;
    border-radius: 8px;
    overflow-x: auto;
    max-height: 480px;
    margin: 0;
    font-size: 0.875rem;
    line-height: 1.5;
  }
</style>
