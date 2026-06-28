<script setup lang="ts">
import { computed, inject, ref } from "vue";
import { useQuery } from "@tanstack/vue-query";
import { Download, RefreshCw } from "lucide-vue-next";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  AnalyticsOverview,
  AnalyticsQueryParams,
  AnalyticsTimeSeriesPoint,
  AnalyticsPeaks,
  AnalyticsSatisfaction,
  AnalyticsUserUsageRow,
  AnalyticsOrganizationUsageRow,
  AnalyticsExceptionDetail,
  PageResponse
} from "@test-agent/shared-types";

const api = inject<BackendApiClient>("api")!;

type TabKey = "overview" | "users" | "organizations" | "satisfaction" | "exceptions";

const activeTab = ref<TabKey>("overview");
const now = new Date();
const defaultStart = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
const startTime = ref(toLocalInput(defaultStart));
const endTime = ref(toLocalInput(now));
const granularity = ref<"hour" | "day" | "week" | "month">("day");
const organization = ref("");
const rdDepartment = ref("");
const department = ref("");
const userId = ref("");
const workspaceId = ref("");
const agentId = ref("");
const model = ref("");
const sort = ref("active");

const params = computed<AnalyticsQueryParams>(() => ({
  startTime: fromLocalInput(startTime.value),
  endTime: fromLocalInput(endTime.value),
  granularity: granularity.value,
  organization: organization.value.trim() || undefined,
  rdDepartment: rdDepartment.value.trim() || undefined,
  department: department.value.trim() || undefined,
  userId: userId.value.trim() || undefined,
  workspaceId: workspaceId.value.trim() || undefined,
  agentId: agentId.value.trim() || undefined,
  model: model.value.trim() || undefined,
  topN: 10,
  page: 1,
  pageSize: 20,
  sort: sort.value
}));

const overviewQuery = useQuery<AnalyticsOverview, Error>({
  queryKey: computed(() => ["analytics-overview", params.value]),
  retry: false,
  queryFn: () => api.getAnalyticsOverview(params.value)
});

const timeseriesQuery = useQuery<AnalyticsTimeSeriesPoint[], Error>({
  queryKey: computed(() => ["analytics-timeseries", params.value]),
  retry: false,
  queryFn: () => api.getAnalyticsTimeseries(params.value)
});

const peaksQuery = useQuery<AnalyticsPeaks, Error>({
  queryKey: computed(() => ["analytics-peaks", params.value]),
  retry: false,
  queryFn: () => api.getAnalyticsPeaks(params.value)
});

const usersQuery = useQuery<PageResponse<AnalyticsUserUsageRow>, Error>({
  queryKey: computed(() => ["analytics-users", params.value]),
  enabled: () => activeTab.value === "users",
  retry: false,
  queryFn: () => api.getAnalyticsUsers(params.value)
});

const organizationsQuery = useQuery<AnalyticsOrganizationUsageRow[], Error>({
  queryKey: computed(() => ["analytics-organizations", params.value]),
  enabled: () => activeTab.value === "organizations",
  retry: false,
  queryFn: () => api.getAnalyticsOrganizations({ ...params.value, groupBy: "department" })
});

const satisfactionQuery = useQuery<AnalyticsSatisfaction, Error>({
  queryKey: computed(() => ["analytics-satisfaction", params.value]),
  enabled: () => activeTab.value === "satisfaction",
  retry: false,
  queryFn: () => api.getAnalyticsSatisfaction(params.value)
});

const exceptionsQuery = useQuery<PageResponse<AnalyticsExceptionDetail>, Error>({
  queryKey: computed(() => ["analytics-exceptions", params.value]),
  enabled: () => activeTab.value === "exceptions",
  retry: false,
  queryFn: () => api.getAnalyticsExceptions(params.value)
});

const overview = computed(() => overviewQuery.data.value);
const timeseries = computed(() => timeseriesQuery.data.value ?? []);
const peaks = computed(() => peaksQuery.data.value);
const maxTrendRun = computed(() => Math.max(1, ...timeseries.value.map(item => item.runCount)));
const maxHeatmap = computed(() => Math.max(1, ...(peaks.value?.heatmap ?? []).map(item => item.activeUsers + item.runCount + item.userMessageCount)));
const freshnessText = computed(() => {
  const freshness = overview.value?.freshness;
  if (!freshness?.generatedAt) return "暂无统计时间";
  const status = freshness.status === "FRESH" ? "最新" : freshness.status === "FAILED" ? "失败" : "可能延迟";
  return `${status} · ${new Date(freshness.generatedAt).toLocaleString("zh-CN")}`;
});

const summaryCards = computed(() => {
  const item = overview.value;
  return [
    { label: "活跃用户", value: item?.activeUsers ?? 0, extra: formatRate(item?.activeRate) },
    { label: "有效使用用户", value: item?.validUsers ?? 0, extra: `深度 ${item?.deepUsers ?? 0}` },
    { label: "新建会话", value: item?.sessionCount ?? 0, extra: `活跃会话 ${item?.activeSessionCount ?? 0}` },
    { label: "用户消息", value: item?.userMessageCount ?? 0, extra: `AI 回复 ${item?.assistantMessageCount ?? 0}` },
    { label: "Run 启动", value: item?.runCount ?? 0, extra: formatRate(item?.successRate) },
    { label: "满意率", value: formatRate(item?.satisfactionRate), extra: `反馈 ${((item?.positiveFeedbackCount ?? 0) + (item?.negativeFeedbackCount ?? 0))}` },
    { label: "Diff 采纳率", value: formatRate(item?.diffAcceptanceRate), extra: `生成 ${item?.diffProposedCount ?? 0}` },
    { label: "p95 耗时", value: formatDuration(item?.p95DurationMs), extra: `平均 ${formatDuration(item?.averageDurationMs)}` },
    { label: "token 使用量", value: formatNumber(item?.totalTokens ?? 0), extra: `人均 ${formatNumber(item?.tokensPerUser)}` }
  ];
});

function refresh() {
  void overviewQuery.refetch();
  void timeseriesQuery.refetch();
  void peaksQuery.refetch();
  void usersQuery.refetch();
  void organizationsQuery.refetch();
  void satisfactionQuery.refetch();
  void exceptionsQuery.refetch();
}

async function exportCsv(type: "overview" | "timeseries" | "users" | "organizations" | "feedback" | "exceptions") {
  const blob = await api.exportAnalyticsCsv(type, params.value);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `analytics-${type}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

function toLocalInput(date: Date) {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function fromLocalInput(value: string) {
  return value ? new Date(value).toISOString() : undefined;
}

function formatNumber(value: number | null | undefined) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 }).format(value);
}

function formatRate(value: number | null | undefined) {
  if (value === null || value === undefined) return "-";
  return `${(value * 100).toFixed(1)}%`;
}

function formatDuration(value: number | null | undefined) {
  if (value === null || value === undefined) return "-";
  if (value < 1000) return `${value}ms`;
  return `${(value / 1000).toFixed(1)}s`;
}

function trendHeight(point: AnalyticsTimeSeriesPoint) {
  return `${Math.max(6, Math.round((point.runCount / maxTrendRun.value) * 72))}px`;
}

function heatmapAlpha(activeUsers: number, runs: number, messages: number) {
  return Math.max(0.08, Math.min(0.95, (activeUsers + runs + messages) / maxHeatmap.value));
}
</script>

<template>
  <section class="ta-analytics">
    <header class="ta-analytics-header">
      <div>
        <h2>运营分析</h2>
        <p>{{ freshnessText }}</p>
      </div>
      <div class="ta-analytics-header-actions">
        <button type="button" class="ta-icon-btn" title="刷新" @click="refresh">
          <RefreshCw :size="16" />
        </button>
        <button type="button" class="ta-export-btn" @click="exportCsv(activeTab === 'satisfaction' ? 'feedback' : activeTab === 'overview' ? 'overview' : activeTab)">
          <Download :size="15" />
          <span>导出 CSV</span>
        </button>
      </div>
    </header>

    <div class="ta-analytics-filters">
      <label>开始<input v-model="startTime" type="datetime-local" /></label>
      <label>结束<input v-model="endTime" type="datetime-local" /></label>
      <label>粒度
        <select v-model="granularity">
          <option value="hour">小时</option>
          <option value="day">天</option>
          <option value="week">周</option>
          <option value="month">月</option>
        </select>
      </label>
      <label>机构<input v-model="organization" placeholder="organization" /></label>
      <label>研发部<input v-model="rdDepartment" placeholder="rdDepartment" /></label>
      <label>部门<input v-model="department" placeholder="department" /></label>
      <label>用户<input v-model="userId" placeholder="userId" /></label>
      <label>agent<input v-model="agentId" placeholder="agentId" /></label>
      <label>model<input v-model="model" placeholder="model" /></label>
      <label>workspace<input v-model="workspaceId" placeholder="workspaceId" /></label>
      <label>排序
        <select v-model="sort">
          <option value="active">活跃</option>
          <option value="runs">Run</option>
          <option value="successRate">成功率</option>
          <option value="satisfactionRate">满意率</option>
          <option value="diffAcceptanceRate">采纳率</option>
          <option value="cancelRate">取消率</option>
          <option value="negativeFeedback">负反馈</option>
          <option value="tokenUsage">token 使用量</option>
        </select>
      </label>
    </div>

    <div class="ta-analytics-tabs">
      <button :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">总览</button>
      <button :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">用户分析</button>
      <button :class="{ active: activeTab === 'organizations' }" @click="activeTab = 'organizations'">组织分析</button>
      <button :class="{ active: activeTab === 'satisfaction' }" @click="activeTab = 'satisfaction'">满意度分析</button>
      <button :class="{ active: activeTab === 'exceptions' }" @click="activeTab = 'exceptions'">异常 Run</button>
    </div>

    <div v-if="activeTab === 'overview'" class="ta-analytics-main">
      <div class="ta-card-grid">
        <article v-for="card in summaryCards" :key="card.label" class="ta-metric-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.extra }}</small>
        </article>
      </div>

      <section class="ta-panel">
        <h3>趋势</h3>
        <div v-if="timeseries.length === 0" class="ta-empty">暂无数据</div>
        <div v-else class="ta-trend">
          <div v-for="point in timeseries" :key="point.bucketStart" class="ta-trend-item">
            <div class="ta-trend-bar" :style="{ height: trendHeight(point) }" />
            <small>{{ new Date(point.bucketStart).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) }}</small>
            <span>{{ point.runCount }}</span>
          </div>
        </div>
      </section>

      <section class="ta-panel">
        <h3>小时热力</h3>
        <div v-if="!peaks?.heatmap?.length" class="ta-empty">暂无数据</div>
        <div v-else class="ta-heatmap">
          <div
            v-for="point in peaks.heatmap"
            :key="`${point.dayOfWeek}-${point.hourOfDay}`"
            class="ta-heatmap-cell"
            :style="{ backgroundColor: `rgba(37, 99, 235, ${heatmapAlpha(point.activeUsers, point.runCount, point.userMessageCount)})` }"
            :title="`周${point.dayOfWeek} ${point.hourOfDay}:00 活跃${point.activeUsers} Run${point.runCount} 消息${point.userMessageCount}`"
          />
        </div>
        <div class="ta-peak-list">
          <div v-for="peak in peaks?.peakPeriods ?? []" :key="peak.bucketStart">
            <strong>{{ new Date(peak.bucketStart).toLocaleString('zh-CN') }}</strong>
            <span>活跃 {{ peak.activeUsers }}</span>
            <span>Run {{ peak.runCount }}</span>
            <span>满意率 {{ formatRate(peak.satisfactionRate) }}</span>
            <span>取消率 {{ formatRate(peak.cancellationRate) }}</span>
          </div>
        </div>
      </section>
    </div>

    <div v-else-if="activeTab === 'users'" class="ta-panel">
      <h3>用户使用明细</h3>
      <table class="ta-table">
        <thead><tr><th>用户</th><th>机构</th><th>研发部</th><th>部门</th><th>登录</th><th>会话</th><th>消息</th><th>Run</th><th>成功率</th><th>满意率</th><th>采纳率</th><th>token 使用量</th></tr></thead>
        <tbody>
          <tr v-for="row in usersQuery.data.value?.items ?? []" :key="row.userId">
            <td>{{ row.username || row.userId }}</td>
            <td>{{ row.organization || '-' }}</td>
            <td>{{ row.rdDepartment || '-' }}</td>
            <td>{{ row.department || '-' }}</td>
            <td>{{ row.loginCount }}</td>
            <td>{{ row.activeSessionCount }}</td>
            <td>{{ row.userMessageCount }}</td>
            <td>{{ row.runCount }}</td>
            <td>{{ formatRate(row.successRate) }}</td>
            <td>{{ formatRate(row.satisfactionRate) }}</td>
            <td>{{ formatRate(row.diffAcceptanceRate) }}</td>
            <td>{{ formatNumber(row.totalTokens) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'organizations'" class="ta-panel">
      <h3>组织排行</h3>
      <table class="ta-table">
        <thead><tr><th>维度</th><th>名称</th><th>登录用户</th><th>活跃用户</th><th>深度用户</th><th>Run</th><th>成功率</th><th>满意率</th><th>采纳率</th><th>负反馈</th></tr></thead>
        <tbody>
          <tr v-for="row in organizationsQuery.data.value ?? []" :key="`${row.dimension}-${row.name}`">
            <td>{{ row.dimension }}</td>
            <td>{{ row.name }}</td>
            <td>{{ row.loginUsers }}</td>
            <td>{{ row.activeUsers }}</td>
            <td>{{ row.deepUsers }}</td>
            <td>{{ row.runCount }}</td>
            <td>{{ formatRate(row.successRate) }}</td>
            <td>{{ formatRate(row.satisfactionRate) }}</td>
            <td>{{ formatRate(row.diffAcceptanceRate) }}</td>
            <td>{{ row.negativeFeedbackCount }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'satisfaction'" class="ta-panel">
      <h3>满意度与反馈明细</h3>
      <div class="ta-reason-list">
        <span v-for="(count, reason) in satisfactionQuery.data.value?.negativeReasonCounts ?? {}" :key="reason">
          {{ reason }} · {{ count }}
        </span>
      </div>
      <table class="ta-table">
        <thead><tr><th>时间</th><th>用户</th><th>组织</th><th>会话</th><th>Run</th><th>消息</th><th>反馈</th><th>原因</th><th>备注</th></tr></thead>
        <tbody>
          <tr v-for="row in satisfactionQuery.data.value?.feedbackDetails.items ?? []" :key="row.feedbackId">
            <td>{{ new Date(row.createdAt).toLocaleString('zh-CN') }}</td>
            <td>{{ row.username || row.userId }}</td>
            <td>{{ [row.organization, row.rdDepartment, row.department].filter(Boolean).join(' / ') }}</td>
            <td>{{ row.sessionId }}</td>
            <td>{{ row.runId || '-' }}</td>
            <td>{{ row.messageId }}</td>
            <td>{{ row.rating === 'POSITIVE' ? '满意' : '不满意' }}</td>
            <td>{{ row.reasonCode || '-' }}</td>
            <td>{{ row.comment || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="ta-panel">
      <h3>异常 Run 明细</h3>
      <table class="ta-table">
        <thead><tr><th>时间</th><th>Run</th><th>用户</th><th>组织</th><th>workspace</th><th>agent</th><th>model</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="row in exceptionsQuery.data.value?.items ?? []" :key="row.runId">
            <td>{{ new Date(row.updatedAt).toLocaleString('zh-CN') }}</td>
            <td>{{ row.runId }}</td>
            <td>{{ row.username || row.userId }}</td>
            <td>{{ [row.organization, row.rdDepartment, row.department].filter(Boolean).join(' / ') }}</td>
            <td>{{ row.workspaceId }}</td>
            <td>{{ row.agentId || '-' }}</td>
            <td>{{ row.modelId || '-' }}</td>
            <td>{{ row.status }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.ta-analytics {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 16px;
  gap: 12px;
  overflow: auto;
  background: #f7f8fa;
  color: #1f2937;
}
.ta-analytics-header,
.ta-analytics-header-actions,
.ta-analytics-tabs,
.ta-analytics-filters,
.ta-card-grid,
.ta-trend,
.ta-peak-list,
.ta-reason-list {
  display: flex;
  align-items: center;
}
.ta-analytics-header {
  justify-content: space-between;
  gap: 12px;
}
.ta-analytics-header h2 {
  margin: 0;
  font-size: 18px;
}
.ta-analytics-header p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 12px;
}
.ta-analytics-header-actions {
  gap: 8px;
}
.ta-icon-btn,
.ta-export-btn {
  height: 32px;
  border: 1px solid #dfe3ea;
  border-radius: 6px;
  background: #fff;
  color: #374151;
  cursor: pointer;
}
.ta-icon-btn {
  width: 32px;
}
.ta-export-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
}
.ta-analytics-filters {
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}
.ta-analytics-filters label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #4b5563;
  font-size: 12px;
}
.ta-analytics-filters input,
.ta-analytics-filters select {
  height: 28px;
  min-width: 110px;
  border: 1px solid #dfe3ea;
  border-radius: 5px;
  padding: 0 8px;
  font-size: 12px;
}
.ta-analytics-tabs {
  gap: 6px;
}
.ta-analytics-tabs button {
  height: 30px;
  padding: 0 10px;
  border: 1px solid #dfe3ea;
  border-radius: 6px;
  background: #fff;
  color: #4b5563;
  cursor: pointer;
}
.ta-analytics-tabs button.active {
  border-color: #2563eb;
  background: #e8f0ff;
  color: #1d4ed8;
}
.ta-analytics-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}
.ta-card-grid {
  flex-wrap: wrap;
  gap: 10px;
}
.ta-metric-card {
  width: 172px;
  min-height: 86px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}
.ta-metric-card span,
.ta-metric-card small {
  display: block;
  color: #6b7280;
  font-size: 12px;
}
.ta-metric-card strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 22px;
  color: #111827;
}
.ta-panel {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  overflow: auto;
}
.ta-panel h3 {
  margin: 0 0 10px;
  font-size: 14px;
}
.ta-empty {
  padding: 28px;
  color: #9ca3af;
  text-align: center;
}
.ta-trend {
  align-items: flex-end;
  gap: 7px;
  min-height: 122px;
  overflow-x: auto;
}
.ta-trend-item {
  display: grid;
  grid-template-rows: 80px 18px 18px;
  justify-items: center;
  min-width: 34px;
  color: #6b7280;
  font-size: 11px;
}
.ta-trend-bar {
  align-self: end;
  width: 16px;
  border-radius: 4px 4px 0 0;
  background: #2563eb;
}
.ta-heatmap {
  display: grid;
  grid-template-columns: repeat(24, 14px);
  gap: 4px;
  margin-bottom: 10px;
}
.ta-heatmap-cell {
  width: 14px;
  height: 14px;
  border-radius: 3px;
}
.ta-peak-list,
.ta-reason-list {
  flex-wrap: wrap;
  gap: 8px;
}
.ta-peak-list div,
.ta-reason-list span {
  display: inline-flex;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 12px;
}
.ta-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.ta-table th,
.ta-table td {
  padding: 8px;
  border-bottom: 1px solid #edf0f4;
  text-align: left;
  white-space: nowrap;
}
.ta-table th {
  color: #6b7280;
  font-weight: 600;
}
</style>
