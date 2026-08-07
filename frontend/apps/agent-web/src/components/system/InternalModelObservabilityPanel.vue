<script setup lang="ts">
import { computed, inject, ref } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Activity, Play, Radar, RefreshCw } from "lucide-vue-next";
import { ElMessage } from "element-plus";
import { type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  InternalModelCallOutcome,
  InternalModelCallSource,
  InternalModelProbeStatus
} from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();

const activeTab = ref<"records" | "stats">("records");
const filterProviderId = ref("");
const filterOutcome = ref<InternalModelCallOutcome | "">("");
const filterSource = ref<InternalModelCallSource | "">("");
const page = ref(1);
const pageSize = 20;

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);

const probeStatusQuery = useQuery({
  queryKey: ["internal-model-observability-probe-status"],
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelProbeStatus()
});

const recordsQuery = useQuery({
  queryKey: computed(() => ["internal-model-observability-records", {
    providerId: filterProviderId.value || null,
    outcome: filterOutcome.value || null,
    source: filterSource.value || null,
    page: page.value
  }]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.listInternalModelCallRecords({
    providerId: filterProviderId.value || null,
    outcome: filterOutcome.value || null,
    source: filterSource.value || null,
    page: page.value,
    size: pageSize
  })
});

const statsQuery = useQuery({
  queryKey: computed(() => ["internal-model-observability-stats", {
    providerId: filterProviderId.value || null
  }]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelCallStats({ providerId: filterProviderId.value || null })
});

const probeMutation = useMutation({
  mutationFn: (variables: { providerId?: string }) =>
    api.triggerInternalModelProbe(variables.providerId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ["internal-model-observability-probe-status"] });
    ElMessage.success("探活完成");
  },
  onError: (error) => ElMessage.error(error instanceof Error ? error.message : "探活失败")
});

function probeOne(providerId: string) {
  probeMutation.mutate({ providerId });
}

function probeAll() {
  probeMutation.mutate({});
}

const probeStatuses = computed(() => probeStatusQuery.data.value ?? []);
const records = computed(() => recordsQuery.data.value?.items ?? []);
const recordsTotal = computed(() => recordsQuery.data.value?.total ?? 0);
const stats = computed(() => statsQuery.data.value ?? []);

type ProviderMetric = {
  providerId: string;
  totalRequests: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  avgDurationMillis: number;
  maxDurationMillis: number;
};

/** 按 provider 汇总小时聚合为指标卡片；用于「聚合统计」tab 的指标视图。 */
const providerMetrics = computed<ProviderMetric[]>(() => {
  const byProvider = new Map<string, ProviderMetric>();
  for (const row of stats.value) {
    const providerId = row.providerId;
    let metric = byProvider.get(providerId);
    if (!metric) {
      metric = {
        providerId,
        totalRequests: 0,
        successCount: 0,
        failureCount: 0,
        successRate: 0,
        avgDurationMillis: 0,
        maxDurationMillis: 0
      };
      byProvider.set(providerId, metric);
    }
    metric.totalRequests += row.requestCount;
    if (row.outcome === "SUCCESS") {
      metric.successCount += row.requestCount;
    } else {
      metric.failureCount += row.requestCount;
    }
    metric.maxDurationMillis = Math.max(metric.maxDurationMillis, row.durationMillisMax);
    // durationSum 是该 provider 该 outcome 的请求数 × 平均耗时的近似；按成功请求累加平均更准。
    if (row.outcome === "SUCCESS" && row.requestCount > 0) {
      metric.avgDurationMillis += row.durationMillisSum / row.requestCount;
    }
  }
  for (const metric of byProvider.values()) {
    metric.successRate = metric.totalRequests === 0
      ? 0
      : Math.round((metric.successCount / metric.totalRequests) * 1000) / 10;
    // 平均耗时仅基于成功请求的平均值，多 outcome 行取最大平均值近似。
  }
  return [...byProvider.values()];
});

/** 失败分类分布（跨 provider），用于指标视图的失败构成。 */
const failureBreakdown = computed<Array<{ outcome: string; label: string; count: number }>>(() => {
  const byOutcome = new Map<string, number>();
  for (const row of stats.value) {
    if (row.outcome === "SUCCESS") continue;
    byOutcome.set(row.outcome, (byOutcome.get(row.outcome) ?? 0) + row.requestCount);
  }
  return [...byOutcome.entries()]
    .map(([outcome, count]) => ({
      outcome,
      label: outcomeText[outcome as InternalModelCallOutcome] ?? outcome,
      count
    }))
    .sort((a, b) => b.count - a.count);
});

/** 全局总览指标：跨所有 provider 的请求量、成功率、失败率、平均/最大耗时、P90 耗时。 */
const overallMetrics = computed(() => {
  let totalRequests = 0;
  let successCount = 0;
  const durations: number[] = [];
  for (const row of stats.value) {
    totalRequests += row.requestCount;
    if (row.outcome === "SUCCESS") successCount += row.requestCount;
    // 用每小时行展开近似时长样本：最大耗时按 requestCount 加权展开，用于 P90。
    for (let i = 0; i < row.requestCount; i++) {
      durations.push(row.durationMillisMax);
    }
  }
  const failureCount = totalRequests - successCount;
  const successRate = totalRequests === 0 ? 0 : Math.round((successCount / totalRequests) * 1000) / 10;
  const failureRate = totalRequests === 0 ? 0 : Math.round((failureCount / totalRequests) * 1000) / 10;
  const maxDuration = durations.length ? Math.max(...durations) : 0;
  const avgDuration = durations.length
    ? Math.round(durations.reduce((a, b) => a + b, 0) / durations.length)
    : 0;
  const p90 = durations.length
    ? durations.slice().sort((a, b) => a - b)[Math.floor(durations.length * 0.9)]
    : 0;
  return { totalRequests, successCount, failureCount, successRate, failureRate, avgDuration, maxDuration, p90 };
});

const outcomeText: Record<InternalModelCallOutcome, string> = {
  SUCCESS: "成功",
  PROXY_AUTH_FAILED: "代理鉴权失败",
  PROVIDER_UNAVAILABLE: "供应商不可用",
  REQUEST_INVALID: "请求校验失败",
  UPSTREAM_CONNECT_FAILED: "上游连接失败",
  UPSTREAM_FIRST_RESPONSE_TIMEOUT: "上游响应超时",
  UPSTREAM_FIRST_EVENT_TIMEOUT: "上游首事件超时",
  UPSTREAM_STREAM_IDLE_TIMEOUT: "流空闲超时",
  UPSTREAM_HTTP_ERROR: "上游 HTTP 错误",
  UPSTREAM_STREAM_INTERRUPTED: "流中断",
  UPSTREAM_STREAM_FAILED: "流读取失败",
  CLIENT_DISCONNECTED: "客户端断开",
  UNKNOWN_ERROR: "未知错误"
};

/** 失败分布条形图宽度：相对最大失败数归一化，最小 4% 保证可见。 */
function pctWidth(count: number): number {
  const max = failureBreakdown.value.length ? failureBreakdown.value[0].count : 0;
  if (max === 0) return 0;
  return Math.max(4, Math.round((count / max) * 100));
}

const outcomeTagType = (outcome: InternalModelCallOutcome): "success" | "danger" | "warning" | "info" => {
  if (outcome === "SUCCESS") return "success";
  if (outcome === "UPSTREAM_CONNECT_FAILED" || outcome === "UPSTREAM_FIRST_RESPONSE_TIMEOUT"
      || outcome === "UPSTREAM_FIRST_EVENT_TIMEOUT" || outcome === "UPSTREAM_STREAM_IDLE_TIMEOUT"
      || outcome === "UPSTREAM_HTTP_ERROR" || outcome === "UPSTREAM_STREAM_INTERRUPTED"
      || outcome === "UPSTREAM_STREAM_FAILED" || outcome === "PROVIDER_UNAVAILABLE") return "danger";
  if (outcome === "CLIENT_DISCONNECTED" || outcome === "UNKNOWN_ERROR") return "warning";
  return "info";
};

const healthy = (status: InternalModelProbeStatus) => status.lastOutcome === "SUCCESS";

const formatTime = (value?: string | null) => value ? new Date(value).toLocaleString() : "-";

const providerOptions = computed(() => {
  const seen = new Set<string>();
  const options: Array<{ label: string; value: string }> = [];
  for (const status of probeStatuses.value) {
    if (!seen.has(status.providerId)) {
      seen.add(status.providerId);
      options.push({ label: status.providerId, value: status.providerId });
    }
  }
  return options;
});

function applyFilters() {
  page.value = 1;
}

function onPageChange(next: number) {
  page.value = next;
}
</script>

<template>
  <section class="ta-imob">
    <template v-if="hasSuperAdmin">
      <div class="ta-imob-header">
        <h3 class="ta-imob-title">内部模型调用可观测</h3>
        <span class="ta-imob-sub">调用明细、聚合统计与供应商探活状态；仅记录结构化字段，不含请求正文</span>
      </div>

      <div v-if="probeStatuses.length" class="ta-imob-cards">
        <div
          v-for="status in probeStatuses"
          :key="status.providerId"
          :class="['ta-imob-card', { 'is-down': !healthy(status) }]"
        >
          <div class="ta-imob-card-head">
            <span class="ta-imob-dot" :class="{ 'is-down': !healthy(status) }" />
            <span class="ta-imob-card-provider">{{ status.providerId }}</span>
            <button
              type="button"
              class="ta-imob-probe-btn"
              :disabled="probeMutation.isPending.value"
              @click="probeOne(status.providerId)"
            >
              <Play class="ta-imob-probe-icon" :size="12" />
              探活
            </button>
          </div>
          <div class="ta-imob-card-body">
            <div class="ta-imob-card-row">
              <span class="ta-imob-label">最近结果</span>
              <el-tag :type="outcomeTagType(status.lastOutcome)" size="small">
                {{ outcomeText[status.lastOutcome] ?? status.lastOutcome }}
              </el-tag>
            </div>
            <div class="ta-imob-card-row">
              <span class="ta-imob-label">最近探活</span>
              <span>{{ formatTime(status.lastProbedAt) }}</span>
            </div>
            <div class="ta-imob-card-row">
              <span class="ta-imob-label">连续失败</span>
              <span :class="{ 'ta-imob-fail-count': status.consecutiveFailures > 0 }">
                {{ status.consecutiveFailures }}
              </span>
            </div>
            <div class="ta-imob-card-row">
              <span class="ta-imob-label">最近成功</span>
              <span>{{ formatTime(status.lastSuccessAt) }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="probeStatusQuery.isLoading.value" class="ta-imob-placeholder">
        <RefreshCw class="ta-imob-spin" :size="16" /> 加载探活状态…
      </div>
      <div v-else class="ta-imob-placeholder">
        <Radar :size="16" /> 尚无探活记录，可在下方手动触发或等待定时探活
      </div>

      <el-tabs v-model="activeTab" class="ta-imob-tabs">
        <el-tab-pane label="调用记录" name="records">
          <div class="ta-imob-filter-bar">
            <el-select
              v-model="filterProviderId"
              placeholder="供应商"
              clearable
              class="ta-imob-filter"
              @change="applyFilters"
            >
              <el-option
                v-for="option in providerOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-select
              v-model="filterOutcome"
              placeholder="结果分类"
              clearable
              class="ta-imob-filter"
              @change="applyFilters"
            >
              <el-option
                v-for="(label, key) in outcomeText"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>
            <el-select
              v-model="filterSource"
              placeholder="来源"
              clearable
              class="ta-imob-filter"
              @change="applyFilters"
            >
              <el-option label="用户调用" value="USER_CALL" />
              <el-option label="探活" value="PROBE" />
            </el-select>
            <button
              type="button"
              class="ta-imob-probe-all-btn"
              :disabled="probeMutation.isPending.value"
              @click="probeAll()"
            >
              <Activity class="ta-imob-probe-icon" :size="12" />
              全部探活
            </button>
          </div>

          <el-table v-loading="recordsQuery.isLoading.value" :data="records" stripe>
            <el-table-column prop="startedAt" label="时间" min-width="160">
              <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column prop="providerId" label="供应商" min-width="150" />
            <el-table-column prop="model" label="模型" min-width="150">
              <template #default="{ row }">{{ row.model ?? "-" }}</template>
            </el-table-column>
            <el-table-column prop="endpoint" label="端点" min-width="130" />
            <el-table-column label="来源" width="90">
              <template #default="{ row }">
                <el-tag :type="row.source === 'PROBE' ? 'info' : 'primary'" size="small">
                  {{ row.source === "PROBE" ? "探活" : "用户" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="130">
              <template #default="{ row }">
                <el-tag :type="outcomeTagType(row.outcome as InternalModelCallOutcome)" size="small">
                  {{ outcomeText[row.outcome as InternalModelCallOutcome] ?? row.outcome }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="httpStatus" label="状态码" width="80">
              <template #default="{ row }">{{ row.httpStatus ?? "-" }}</template>
            </el-table-column>
            <el-table-column label="耗时(ms)" width="110">
              <template #default="{ row }">{{ row.durationMillis }}</template>
            </el-table-column>
            <el-table-column prop="traceId" label="traceId" min-width="170" show-overflow-tooltip />
          </el-table>

          <div class="ta-imob-pager">
            <el-pagination
              :current-page="page"
              :page-size="pageSize"
              :total="recordsTotal"
              layout="prev, pager, next, total"
              @current-change="onPageChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="聚合统计" name="stats">
          <div v-loading="statsQuery.isLoading.value" class="ta-imob-stats">
            <!-- 全局总览指标（多类指标聚合） -->
            <div v-if="overallMetrics.totalRequests" class="ta-imob-overview">
              <h4 class="ta-imob-overview-title">总览</h4>
              <div class="ta-imob-overview-grid">
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.totalRequests }}</span>
                  <span class="ta-imob-overview-label">总请求</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value" :class="{ 'is-ok': overallMetrics.successRate >= 90 }">
                    {{ overallMetrics.successRate }}%
                  </span>
                  <span class="ta-imob-overview-label">成功率</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value" :class="{ 'is-bad': overallMetrics.failureRate > 10 }">
                    {{ overallMetrics.failureRate }}%
                  </span>
                  <span class="ta-imob-overview-label">失败率</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.avgDuration }}ms</span>
                  <span class="ta-imob-overview-label">平均耗时</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.p90 }}ms</span>
                  <span class="ta-imob-overview-label">P90 耗时</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.maxDuration }}ms</span>
                  <span class="ta-imob-overview-label">最大耗时</span>
                </div>
              </div>
            </div>

            <!-- 按供应商聚合 -->
            <div v-if="providerMetrics.length">
              <h4 class="ta-imob-overview-title">按供应商</h4>
              <div class="ta-imob-metric-grid">
                <div v-for="metric in providerMetrics" :key="metric.providerId" class="ta-imob-metric-card">
                  <div class="ta-imob-metric-provider">{{ metric.providerId }}</div>
                  <div class="ta-imob-metric-body">
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ metric.totalRequests }}</span>
                      <span class="ta-imob-metric-label">总请求</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value" :class="{ 'is-ok': metric.successRate >= 90 }">
                        {{ metric.successRate }}%
                      </span>
                      <span class="ta-imob-metric-label">成功率</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ metric.avgDurationMillis.toFixed(0) }}ms</span>
                      <span class="ta-imob-metric-label">平均耗时</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ metric.maxDurationMillis }}ms</span>
                      <span class="ta-imob-metric-label">最大耗时</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value" :class="{ 'is-bad': metric.failureCount > 0 }">
                        {{ metric.failureCount }}
                      </span>
                      <span class="ta-imob-metric-label">失败</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else-if="!statsQuery.isLoading.value" class="ta-imob-placeholder">暂无聚合数据</div>

            <!-- 失败分类分布 -->
            <div v-if="failureBreakdown.length" class="ta-imob-failure-block">
              <h4 class="ta-imob-failure-title">失败分类分布</h4>
              <div class="ta-imob-failure-bars">
                <div
                  v-for="item in failureBreakdown"
                  :key="item.outcome"
                  class="ta-imob-failure-row"
                >
                  <span class="ta-imob-failure-label">{{ item.label }}</span>
                  <div class="ta-imob-failure-track">
                    <div
                      class="ta-imob-failure-fill"
                      :style="{ width: pctWidth(item.count) + '%' }"
                    />
                  </div>
                  <span class="ta-imob-failure-count">{{ item.count }}</span>
                </div>
              </div>
            </div>

            <!-- 按小时明细 -->
            <el-table v-if="stats.length" :data="stats" stripe class="ta-imob-hourly-table">
              <el-table-column prop="statHour" label="小时" min-width="160">
                <template #default="{ row }">{{ formatTime(row.statHour) }}</template>
              </el-table-column>
              <el-table-column prop="providerId" label="供应商" min-width="140" />
              <el-table-column prop="model" label="模型" min-width="140" />
              <el-table-column label="结果" width="140">
                <template #default="{ row }">
                  <el-tag :type="outcomeTagType(row.outcome as InternalModelCallOutcome)" size="small">
                    {{ outcomeText[row.outcome as InternalModelCallOutcome] ?? row.outcome }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="requestCount" label="请求数" width="90" />
              <el-table-column prop="durationMillisSum" label="耗时合计(ms)" width="120" />
              <el-table-column prop="durationMillisMax" label="最大耗时(ms)" width="120" />
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
    <div v-else class="ta-imob-placeholder">当前账号无系统管理权限</div>
  </section>
</template>

<style scoped>
.ta-imob {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  overflow: auto;
}
.ta-imob-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ta-imob-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}
.ta-imob-sub {
  font-size: 12px;
  color: #6b7280;
}
.ta-imob-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.ta-imob-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
}
.ta-imob-card.is-down {
  border-color: #fca5a5;
  background: #fff5f5;
}
.ta-imob-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.ta-imob-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
}
.ta-imob-dot.is-down {
  background: #ef4444;
}
.ta-imob-card-provider {
  font-weight: 600;
  color: #111827;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ta-imob-probe-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  color: #2563eb;
  font-size: 12px;
  cursor: pointer;
}
.ta-imob-probe-btn:hover:not(:disabled) {
  background: #eff6ff;
}
.ta-imob-probe-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.ta-imob-card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
}
.ta-imob-card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #374151;
}
.ta-imob-label {
  color: #6b7280;
}
.ta-imob-fail-count {
  color: #dc2626;
  font-weight: 600;
}
.ta-imob-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
/* el-tabs 默认 content 高度不随 flex 收缩，显式约束让表格区域在容器内滚动而不是撑破外层。 */
.ta-imob-tabs :deep(.el-tabs__content),
.ta-imob-tabs :deep(.el-tab-pane) {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.ta-imob-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: auto;
}
.ta-imob-stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}
.ta-imob-overview {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px 16px;
}
.ta-imob-overview-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.ta-imob-overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
}
.ta-imob-overview-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.ta-imob-overview-value {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  white-space: nowrap;
}
.ta-imob-overview-value.is-ok {
  color: #16a34a;
}
.ta-imob-overview-value.is-bad {
  color: #dc2626;
}
.ta-imob-overview-label {
  font-size: 12px;
  color: #6b7280;
}
.ta-imob-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.ta-imob-metric-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
}
.ta-imob-metric-provider {
  font-weight: 600;
  color: #111827;
  margin-bottom: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ta-imob-metric-body {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}
.ta-imob-metric-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.ta-imob-metric-value {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  white-space: nowrap;
}
.ta-imob-metric-value.is-ok {
  color: #16a34a;
}
.ta-imob-metric-value.is-bad {
  color: #dc2626;
}
.ta-imob-metric-label {
  font-size: 11px;
  color: #6b7280;
}
.ta-imob-failure-block {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px 16px;
}
.ta-imob-failure-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.ta-imob-failure-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ta-imob-failure-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.ta-imob-failure-label {
  width: 120px;
  flex-shrink: 0;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ta-imob-failure-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: #f3f4f6;
  overflow: hidden;
}
.ta-imob-failure-fill {
  height: 100%;
  border-radius: 4px;
  background: #ef4444;
  transition: width 0.3s ease;
}
.ta-imob-failure-count {
  width: 40px;
  flex-shrink: 0;
  text-align: right;
  color: #6b7280;
}
.ta-imob-hourly-table {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.ta-imob-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.ta-imob-filter {
  width: 160px;
}
.ta-imob-probe-all-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  color: #2563eb;
  font-size: 12px;
  cursor: pointer;
}
.ta-imob-probe-all-btn:hover:not(:disabled) {
  background: #eff6ff;
}
.ta-imob-probe-all-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.ta-imob-probe-icon {
  flex-shrink: 0;
}
.ta-imob-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.ta-imob-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px;
  border: 1px dashed #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
  justify-content: center;
}
.ta-imob-spin {
  animation: ta-imob-spin 1s linear infinite;
}
@keyframes ta-imob-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
