<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Activity, Play, Radar, RefreshCw } from "lucide-vue-next";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
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

const filterProviderId = ref("");
const filterOutcome = ref<InternalModelCallOutcome | "">("");
// 默认只看真实用户调用，避免每 5 分钟一次的探活把业务首 token/成功率冲淡。
const filterSource = ref<InternalModelCallSource | "">("USER_CALL");
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
    providerId: filterProviderId.value || null,
    source: filterSource.value || null
  }]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelCallStats({
    providerId: filterProviderId.value || null,
    source: filterSource.value || null
  })
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

function probeAll() {
  probeMutation.mutate({});
}

const probeStatuses = computed(() => probeStatusQuery.data.value ?? []);
const records = computed(() => recordsQuery.data.value?.items ?? []);
const recordsTotal = computed(() => recordsQuery.data.value?.total ?? 0);
// stats API 返回全部 outcome；同页展示时在前端复用明细 outcome 筛选，保证两块口径一致。
const stats = computed(() => (statsQuery.data.value ?? []).filter((row) =>
  !filterOutcome.value || row.outcome === filterOutcome.value
));

type ProviderMetric = {
  providerId: string;
  totalRequests: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  durationMillisSum: number;
  avgDurationMillis: number;
  maxDurationMillis: number;
  firstTokenMillisSum: number;
  firstTokenMillisMax: number;
  firstTokenCount: number;
  avgFirstTokenMillis: number | null;
  streamCompleteMillisSum: number;
  streamCompleteMillisMax: number;
  streamCompleteCount: number;
  avgStreamCompleteMillis: number | null;
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
        durationMillisSum: 0,
        avgDurationMillis: 0,
        maxDurationMillis: 0,
        firstTokenMillisSum: 0,
        firstTokenMillisMax: 0,
        firstTokenCount: 0,
        avgFirstTokenMillis: null,
        streamCompleteMillisSum: 0,
        streamCompleteMillisMax: 0,
        streamCompleteCount: 0,
        avgStreamCompleteMillis: null
      };
      byProvider.set(providerId, metric);
    }
    metric.totalRequests += row.requestCount;
    if (row.outcome === "SUCCESS") {
      metric.successCount += row.requestCount;
    } else {
      metric.failureCount += row.requestCount;
    }
    metric.durationMillisSum += row.durationMillisSum;
    metric.maxDurationMillis = Math.max(metric.maxDurationMillis, row.durationMillisMax);
    metric.firstTokenMillisSum += row.firstTokenMillisSum ?? 0;
    metric.firstTokenMillisMax = Math.max(metric.firstTokenMillisMax, row.firstTokenMillisMax ?? 0);
    metric.firstTokenCount += row.firstTokenCount ?? 0;
    metric.streamCompleteMillisSum += row.streamCompleteMillisSum ?? 0;
    metric.streamCompleteMillisMax = Math.max(
      metric.streamCompleteMillisMax,
      row.streamCompleteMillisMax ?? 0
    );
    metric.streamCompleteCount += row.streamCompleteCount ?? 0;
  }
  for (const metric of byProvider.values()) {
    metric.successRate = metric.totalRequests === 0
      ? 0
      : Math.round((metric.successCount / metric.totalRequests) * 1000) / 10;
    metric.avgDurationMillis = metric.totalRequests === 0
      ? 0
      : Math.round(metric.durationMillisSum / metric.totalRequests);
    metric.avgFirstTokenMillis = metric.firstTokenCount === 0
      ? null
      : Math.round(metric.firstTokenMillisSum / metric.firstTokenCount);
    metric.avgStreamCompleteMillis = metric.streamCompleteCount === 0
      ? null
      : Math.round(metric.streamCompleteMillisSum / metric.streamCompleteCount);
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

/** 全局总览指标：只使用小时聚合可准确还原的计数、均值、最大值与首 token 均值。 */
const overallMetrics = computed(() => {
  let totalRequests = 0;
  let successCount = 0;
  let totalDurationMillis = 0;
  let maxDuration = 0;
  let firstTokenMillisSum = 0;
  let firstTokenMillisMax = 0;
  let firstTokenCount = 0;
  let streamCompleteMillisSum = 0;
  let streamCompleteMillisMax = 0;
  let streamCompleteCount = 0;
  let minHour: string | null = null;
  let maxHour: string | null = null;
  for (const row of stats.value) {
    totalRequests += row.requestCount;
    if (row.outcome === "SUCCESS") successCount += row.requestCount;
    totalDurationMillis += row.durationMillisSum;
    maxDuration = Math.max(maxDuration, row.durationMillisMax);
    firstTokenMillisSum += row.firstTokenMillisSum ?? 0;
    firstTokenMillisMax = Math.max(firstTokenMillisMax, row.firstTokenMillisMax ?? 0);
    firstTokenCount += row.firstTokenCount ?? 0;
    streamCompleteMillisSum += row.streamCompleteMillisSum ?? 0;
    streamCompleteMillisMax = Math.max(streamCompleteMillisMax, row.streamCompleteMillisMax ?? 0);
    streamCompleteCount += row.streamCompleteCount ?? 0;
    if (minHour === null || row.statHour < minHour) minHour = row.statHour;
    if (maxHour === null || row.statHour > maxHour) maxHour = row.statHour;
  }
  const failureCount = totalRequests - successCount;
  const successRate = totalRequests === 0 ? 0 : Math.round((successCount / totalRequests) * 1000) / 10;
  const failureRate = totalRequests === 0 ? 0 : Math.round((failureCount / totalRequests) * 1000) / 10;
  const avgDuration = totalRequests > 0
    ? Math.round(totalDurationMillis / totalRequests)
    : 0;
  const firstTokenAvg = firstTokenCount > 0
    ? Math.round(firstTokenMillisSum / firstTokenCount)
    : null;
  const streamCompleteAvg = streamCompleteCount > 0
    ? Math.round(streamCompleteMillisSum / streamCompleteCount)
    : null;
  // QPS：按小时跨度估算（至少 1 小时，避免单小时行除 0）。
  const hoursSpan = minHour && maxHour
    ? Math.max(1, (new Date(maxHour).getTime() - new Date(minHour).getTime()) / 3_600_000)
    : 1;
  const qps = totalRequests === 0 ? 0 : Math.round((totalRequests / (hoursSpan * 3600)) * 100) / 100;
  const providerCount = new Set(stats.value.map((row) => row.providerId)).size;
  return {
    totalRequests, successCount, failureCount, successRate, failureRate,
    avgDuration, maxDuration,
    firstTokenAvg, firstTokenMax: firstTokenCount > 0 ? firstTokenMillisMax : null,
    firstTokenCount,
    streamCompleteAvg,
    streamCompleteMax: streamCompleteCount > 0 ? streamCompleteMillisMax : null,
    streamCompleteCount,
    totalDurationSeconds: Math.round(totalDurationMillis / 1000),
    qps, providerCount
  };
});

/** 按小时聚合的请求量与成功率序列，供 echarts 趋势折线使用。 */
const hourlyTrend = computed(() => {
  const byHour = new Map<string, { total: number; success: number }>();
  for (const row of stats.value) {
    const hour = row.statHour;
    const cur = byHour.get(hour) ?? { total: 0, success: 0 };
    cur.total += row.requestCount;
    if (row.outcome === "SUCCESS") cur.success += row.requestCount;
    byHour.set(hour, cur);
  }
  const hours = [...byHour.keys()].sort();
  return {
    hours,
    requests: hours.map((h) => byHour.get(h)!.total),
    successRate: hours.map((h) => {
      const { total, success } = byHour.get(h)!;
      return total === 0 ? 0 : Math.round((success / total) * 1000) / 10;
    })
  };
});

/** 成功率/失败率饼图数据。 */
const outcomePieData = computed(() => [
  { name: "成功", value: overallMetrics.value.successCount },
  { name: "失败", value: overallMetrics.value.failureCount }
].filter((item) => item.value > 0));

/** 按供应商请求量对比（横向条形图数据）。 */
const providerBarData = computed(() =>
  providerMetrics.value
    .map((m) => ({ name: m.providerId, value: m.totalRequests }))
    .sort((a, b) => b.value - a.value)
);

/** 失败分类条形图数据（echarts，替代纯 CSS 条）。 */
const failureBarData = computed(() =>
  failureBreakdown.value.map((item) => ({ name: item.label, value: item.count }))
);

const trendChartEl = ref<HTMLDivElement | null>(null);
const pieChartEl = ref<HTMLDivElement | null>(null);
const failureChartEl = ref<HTMLDivElement | null>(null);
const providerChartEl = ref<HTMLDivElement | null>(null);
let trendChart: echarts.ECharts | null = null;
let pieChart: echarts.ECharts | null = null;
let failureChart: echarts.ECharts | null = null;
let providerChart: echarts.ECharts | null = null;

function ensureChart(el: HTMLDivElement, holder: { current: echarts.ECharts | null }) {
  // 若实例已存在但容器宽度为 0（曾在隐藏 tab 中初始化过），dispose 重建。
  if (holder.current && el.clientWidth === 0) {
    holder.current.dispose();
    holder.current = null;
  }
  if (!holder.current) {
    holder.current = echarts.init(el);
  }
  return holder.current;
}

function renderCharts() {
  if (trendChartEl.value && trendChartEl.value.clientWidth > 0) {
    trendChart = ensureChart(trendChartEl.value, { current: trendChart });
    trendChart.setOption({
      animation: false,
      tooltip: { trigger: "axis" },
      legend: { top: 0, right: 8, textStyle: { fontSize: 11 } },
      grid: { top: 32, left: 48, right: 16, bottom: 28 },
      xAxis: { type: "category", boundaryGap: false, data: hourlyTrend.value.hours },
      yAxis: [
        { type: "value", name: "请求", scale: true },
        { type: "value", name: "成功率%", max: 100, min: 0, splitLine: { show: false } }
      ],
      series: [
        {
          name: "请求数",
          type: "line",
          showSymbol: false,
          data: hourlyTrend.value.requests,
          itemStyle: { color: "#2563eb" }
        },
        {
          name: "成功率%",
          type: "line",
          yAxisIndex: 1,
          showSymbol: false,
          data: hourlyTrend.value.successRate,
          itemStyle: { color: "#16a34a" },
          lineStyle: { type: "dashed" }
        }
      ]
    }, true);
  }
  if (pieChartEl.value && pieChartEl.value.clientWidth > 0) {
    pieChart = ensureChart(pieChartEl.value, { current: pieChart });
    pieChart.setOption({
      animation: false,
      tooltip: { trigger: "item" },
      legend: { bottom: 0, textStyle: { fontSize: 11 } },
      series: [{
        type: "pie",
        radius: ["42%", "68%"],
        center: ["50%", "44%"],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: "#fff", borderWidth: 2 },
        label: { formatter: "{b}: {d}%" },
        data: outcomePieData.value
      }]
    }, true);
  }
  if (failureChartEl.value && failureChartEl.value.clientWidth > 0 && failureBarData.value.length) {
    failureChart = ensureChart(failureChartEl.value, { current: failureChart });
    failureChart.setOption({
      animation: false,
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      grid: { top: 16, left: 96, right: 24, bottom: 24 },
      xAxis: { type: "value", minInterval: 1 },
      yAxis: { type: "category", data: failureBarData.value.map((d) => d.name), inverse: true },
      series: [{
        type: "bar",
        data: failureBarData.value.map((d) => d.value),
        itemStyle: { color: "#ef4444", borderRadius: [0, 4, 4, 0] },
        barMaxWidth: 18
      }]
    }, true);
  }
  if (providerChartEl.value && providerChartEl.value.clientWidth > 0 && providerBarData.value.length) {
    providerChart = ensureChart(providerChartEl.value, { current: providerChart });
    providerChart.setOption({
      animation: false,
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      grid: { top: 16, left: 96, right: 24, bottom: 24 },
      xAxis: { type: "value", minInterval: 1 },
      yAxis: { type: "category", data: providerBarData.value.map((d) => d.name), inverse: true },
      series: [{
        type: "bar",
        data: providerBarData.value.map((d) => d.value),
        itemStyle: { color: "#2563eb", borderRadius: [0, 4, 4, 0] },
        barMaxWidth: 18
      }]
    }, true);
  }
}

function resizeCharts() {
  trendChart?.resize();
  pieChart?.resize();
  failureChart?.resize();
  providerChart?.resize();
}

onMounted(() => {
  window.addEventListener("resize", resizeCharts);
  renderCharts();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  [trendChart, pieChart, failureChart, providerChart].forEach((chart) => chart?.dispose());
  trendChart = null;
  pieChart = null;
  failureChart = null;
  providerChart = null;
});

// 聚合指标与明细常驻同一页面，数据变化后直接重绘图表。
watch(() => stats.value, renderCharts, { deep: true });

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

/** 耗时毫秒转秒：小于 1s 保留 1 位小数，否则取整，单位统一为 s；空值显示 -。 */
function formatDuration(millis: number | null | undefined): string {
  if (millis === null || millis === undefined || !Number.isFinite(millis) || millis < 0) return "-";
  const seconds = millis / 1000;
  return seconds < 1 ? `${seconds.toFixed(1)}s` : `${Math.round(seconds)}s`;
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
        <span class="ta-imob-sub">默认统计真实用户调用（可切换探活）；首 token 为首个真实输出 SSE data，流完成为收到 [DONE]，端到端耗时包含下游写出；仅记录结构化字段</span>
      </div>

      <div class="ta-imob-combined">
        <div class="ta-imob-filter-bar">
          <span class="ta-imob-filter-title">筛选条件</span>
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

        <section class="ta-imob-section ta-imob-records-section">
          <h4 class="ta-imob-section-title">调用明细</h4>

          <el-table v-loading="recordsQuery.isLoading.value" :data="records" stripe>
            <el-table-column prop="traceId" label="Trace ID" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.traceId || "-" }}</template>
            </el-table-column>
            <el-table-column label="端到端耗时" min-width="140">
              <template #default="{ row }">{{ formatDuration(row.durationMillis) }}</template>
            </el-table-column>
            <el-table-column label="首 token" min-width="140">
              <template #default="{ row }">{{ formatDuration(row.firstTokenMillis) }}</template>
            </el-table-column>
            <el-table-column label="流完成" min-width="140">
              <template #default="{ row }">{{ formatDuration(row.streamCompleteMillis) }}</template>
            </el-table-column>
            <el-table-column prop="model" label="模型" min-width="150">
              <template #default="{ row }">{{ row.model ?? "-" }}</template>
            </el-table-column>
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
            <el-table-column prop="startedAt" label="时间" min-width="180">
              <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
            </el-table-column>
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
        </section>

        <section class="ta-imob-section ta-imob-metrics-section">
          <h4 class="ta-imob-section-title">聚合指标</h4>
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
                  <span class="ta-imob-overview-value">{{ overallMetrics.providerCount }}</span>
                  <span class="ta-imob-overview-label">供应商</span>
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
                  <span class="ta-imob-overview-value" :class="{ 'is-bad': overallMetrics.failureCount > 0 }">
                    {{ overallMetrics.failureCount }}
                  </span>
                  <span class="ta-imob-overview-label">失败数</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.avgDuration) }}</span>
                  <span class="ta-imob-overview-label">平均耗时</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.maxDuration) }}</span>
                  <span class="ta-imob-overview-label">最大耗时</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.totalDurationSeconds }}s</span>
                  <span class="ta-imob-overview-label">总耗时</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.qps }}</span>
                  <span class="ta-imob-overview-label">QPS</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.firstTokenAvg) }}</span>
                  <span class="ta-imob-overview-label">平均首 token</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.firstTokenMax) }}</span>
                  <span class="ta-imob-overview-label">最大首 token</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.streamCompleteAvg) }}</span>
                  <span class="ta-imob-overview-label">平均流完成</span>
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.streamCompleteMax) }}</span>
                  <span class="ta-imob-overview-label">最大流完成</span>
                </div>
              </div>
            </div>

            <!-- 图表：趋势 / 成功率 / 失败分类 / 供应商对比。小时聚合无法还原分位数与分布，避免展示伪 P90/P95。 -->
            <div v-if="hourlyTrend.hours.length" class="ta-imob-charts">
              <div class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">小时趋势</h4>
                <div ref="trendChartEl" class="ta-imob-chart" />
              </div>
              <div class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">成功率构成</h4>
                <div ref="pieChartEl" class="ta-imob-chart" />
              </div>
              <div v-if="failureBarData.length" class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">失败分类</h4>
                <div ref="failureChartEl" class="ta-imob-chart" />
              </div>
              <div v-if="providerBarData.length" class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">按供应商请求量</h4>
                <div ref="providerChartEl" class="ta-imob-chart" />
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
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgDurationMillis) }}</span>
                      <span class="ta-imob-metric-label">平均耗时</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.maxDurationMillis) }}</span>
                      <span class="ta-imob-metric-label">最大耗时</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgFirstTokenMillis) }}</span>
                      <span class="ta-imob-metric-label">平均首 token</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgStreamCompleteMillis) }}</span>
                      <span class="ta-imob-metric-label">平均流完成</span>
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
              <el-table-column label="耗时合计" width="100">
                <template #default="{ row }">{{ formatDuration(row.durationMillisSum) }}</template>
              </el-table-column>
              <el-table-column label="最大耗时" width="100">
                <template #default="{ row }">{{ formatDuration(row.durationMillisMax) }}</template>
              </el-table-column>
              <el-table-column label="平均首 token" width="120">
                <template #default="{ row }">
                  {{ formatDuration((row.firstTokenCount ?? 0) > 0 ? Math.round((row.firstTokenMillisSum ?? 0) / (row.firstTokenCount ?? 1)) : null) }}
                </template>
              </el-table-column>
              <el-table-column label="最大首 token" width="120">
                <template #default="{ row }">{{ formatDuration((row.firstTokenCount ?? 0) > 0 ? (row.firstTokenMillisMax ?? 0) : null) }}</template>
              </el-table-column>
              <el-table-column label="平均流完成" width="120">
                <template #default="{ row }">
                  {{ formatDuration((row.streamCompleteCount ?? 0) > 0 ? Math.round((row.streamCompleteMillisSum ?? 0) / (row.streamCompleteCount ?? 1)) : null) }}
                </template>
              </el-table-column>
              <el-table-column label="最大流完成" width="120">
                <template #default="{ row }">{{ formatDuration((row.streamCompleteCount ?? 0) > 0 ? (row.streamCompleteMillisMax ?? 0) : null) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </section>
      </div>
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
.ta-imob-combined {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.ta-imob-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.ta-imob-metrics-section {
  order: 1;
}
.ta-imob-records-section {
  order: 2;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}
.ta-imob-section-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
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
.ta-imob-charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
}
.ta-imob-chart-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px 16px;
}
.ta-imob-chart {
  width: 100%;
  height: 240px;
}
.ta-imob-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 10px;
}
.ta-imob-metric-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px 14px;
}
.ta-imob-metric-provider {
  font-weight: 600;
  font-size: 13px;
  color: #111827;
  margin-bottom: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ta-imob-metric-body {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 16px;
}
.ta-imob-metric-cell {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  min-width: 0;
}
.ta-imob-metric-value {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  white-space: nowrap;
}
.ta-imob-metric-label {
  font-size: 12px;
  color: #6b7280;
  flex-shrink: 0;
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
.ta-imob-hourly-table {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.ta-imob-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  flex-wrap: wrap;
}
.ta-imob-filter-title {
  margin-right: 4px;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
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
