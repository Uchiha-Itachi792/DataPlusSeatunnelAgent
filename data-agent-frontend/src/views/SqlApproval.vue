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
    <div class="sql-approval-page">
      <main class="main-content">
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">SQL 审批</h1>
            <p class="content-subtitle">查看并审批数据同步任务生成的 SQL，支持执行或忽略</p>
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
              <el-table-column prop="syncSql" label="同步 SQL" min-width="280" show-overflow-tooltip>
                <template #default="scope">
                  <el-link type="primary" @click="showSqlPreview(scope.row)">查看 SQL</el-link>
                </template>
              </el-table-column>
              <el-table-column prop="execStatusLabel" label="执行状态" width="120">
                <template #default="scope">
                  <el-tag :type="getStatusTagType(scope.row.execStatus)" size="small">
                    {{ scope.row.execStatusLabel }}
                  </el-tag>
                </template>
              </el-table-column>
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
                  <el-tooltip v-else content="该 SQL 已处理，无法再次操作" placement="top">
                    <span class="text-muted">已处理</span>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!loading && items.length === 0" description="暂无待审批的同步 SQL" />
          </el-card>
        </div>
      </main>

      <el-dialog v-model="sqlPreviewVisible" title="同步 SQL 预览" width="720px">
        <pre class="sql-preview"><code v-html="highlightedSql"></code></pre>
      </el-dialog>
    </div>
  </BaseLayout>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { Refresh } from '@element-plus/icons-vue';
  import hljs from 'highlight.js/lib/core';
  import sqlLang from 'highlight.js/lib/languages/sql';
  import BaseLayout from '@/layouts/BaseLayout.vue';
  import sqlCheckService, { type SqlCheckItem, type SqlCheckExecStatus } from '@/services/sqlCheck';

  hljs.registerLanguage('sql', sqlLang);

  const loading = ref(false);
  const items = ref<SqlCheckItem[]>([]);
  const statusFilter = ref('');
  const executingId = ref<number | null>(null);
  const ignoringId = ref<number | null>(null);
  const sqlPreviewVisible = ref(false);
  const previewSql = ref('');

  const highlightedSql = computed(() => {
    if (!previewSql.value) {
      return '';
    }
    return hljs.highlight(previewSql.value, { language: 'sql' }).value;
  });

  const getStatusTagType = (status: SqlCheckExecStatus) => {
    switch (status) {
      case 'PENDING':
        return 'warning';
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
      items.value = await sqlCheckService.list(statusFilter.value || undefined);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '加载列表失败，请稍后重试';
      ElMessage.error(message);
    } finally {
      loading.value = false;
    }
  };

  const showSqlPreview = (row: SqlCheckItem) => {
    previewSql.value = row.syncSql;
    sqlPreviewVisible.value = true;
  };

  const handleExecute = async (row: SqlCheckItem) => {
    try {
      await ElMessageBox.confirm(
        '确认在数据源上执行该同步 SQL？此操作不可撤销。',
        '执行确认',
        { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }

    executingId.value = row.id;
    try {
      const result = await sqlCheckService.execute(row.id);
      if (result.success) {
        ElMessage.success(result.message || 'SQL 执行成功');
        await loadList();
      } else {
        ElMessage.error(result.message || 'SQL 执行失败');
        await loadList();
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '执行请求失败，请检查网络或后端服务';
      ElMessage.error(message);
    } finally {
      executingId.value = null;
    }
  };

  const handleIgnore = async (row: SqlCheckItem) => {
    try {
      await ElMessageBox.confirm('确认忽略该 SQL？忽略后将无法再次执行。', '忽略确认', {
        type: 'warning',
        confirmButtonText: '确认忽略',
        cancelButtonText: '取消',
      });
    } catch {
      return;
    }

    ignoringId.value = row.id;
    try {
      const result = await sqlCheckService.ignore(row.id);
      if (result.success) {
        ElMessage.success(result.message || '已忽略该 SQL');
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
  .sql-approval-page {
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

  .sql-preview {
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
