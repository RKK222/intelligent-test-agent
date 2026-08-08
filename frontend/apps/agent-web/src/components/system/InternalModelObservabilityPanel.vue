<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Activity, RefreshCw } from "lucide-vue-next";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  InternalModelCallHourlyStat,
  InternalModelCallOutcome,
  InternalModelCallOutcomeGroup,
  InternalModelCallSource
} from "@test-agent/shared-types";
import MetricHelpLabel from "./MetricHelpLabel.vue";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();

const filterProviderId = ref("");
const filterOutcomeGroup = ref<InternalModelCallOutcomeGroup | "">("");
// 默认只看真实用户调用，避免每 5 分钟一次的探活把业务 TTFT/成功率冲淡。
const filterSource = ref<InternalModelCallSource | "">("USER_CALL");
const page = ref(1);
const pageSize = 20;
const HOUR_MILLIS = 3_600_000;
const WINDOW_HOURS = 24;

type QueryWindow = { from: string; to: string };

/** 查询当前小时和之前 23 个小时桶，并以实际加载时刻收口，避免把未来时段算进 RPS 分母。 */
function createQueryWindow(nowMillis = Date.now()): QueryWindow {
  const currentHourMillis = Math.floor(nowMillis / HOUR_MILLIS) * HOUR_MILLIS;
  return {
    from: new Date(currentHourMillis - (WINDOW_HOURS - 1) * HOUR_MILLIS).toISOString(),
    to: new Date(nowMillis).toISOString()
  };
}

const queryWindow = ref<QueryWindow>(createQueryWindow());

const outcomeGroupText: Record<InternalModelCallOutcomeGroup, string> = {
  SUCCESS: "成功",
  REQUEST_OR_CONFIGURATION: "请求或配置问题",
  UPSTREAM_FAILURE: "上游服务异常",
  CALLER_INTERRUPTED: "调用方中断",
  OTHER: "其他异常"
};

const outcomesByGroup: Record<InternalModelCallOutcomeGroup, readonly InternalModelCallOutcome[]> = {
  SUCCESS: ["SUCCESS"],
  REQUEST_OR_CONFIGURATION: ["PROXY_AUTH_FAILED", "PROVIDER_UNAVAILABLE", "REQUEST_INVALID"],
  UPSTREAM_FAILURE: [
    "UPSTREAM_CONNECT_FAILED",
    "UPSTREAM_FIRST_RESPONSE_TIMEOUT",
    "UPSTREAM_FIRST_EVENT_TIMEOUT",
    "UPSTREAM_STREAM_IDLE_TIMEOUT",
    "UPSTREAM_HTTP_ERROR",
    "UPSTREAM_STREAM_INTERRUPTED",
    "UPSTREAM_STREAM_FAILED"
  ],
  CALLER_INTERRUPTED: ["CLIENT_DISCONNECTED"],
  OTHER: ["UNKNOWN_ERROR"]
};

/** 底层原因只用于明细排障，页面统计统一折叠为五个稳定结果大类。 */
function outcomeGroupOf(outcome: InternalModelCallOutcome): InternalModelCallOutcomeGroup {
  const group = (Object.keys(outcomesByGroup) as InternalModelCallOutcomeGroup[])
    .find((candidate) => outcomesByGroup[candidate].includes(outcome));
  return group ?? "OTHER";
}

// 页面提示只讲业务含义、分母和空值规则，避免把采集与存储实现暴露给使用者。
const metricHelp = {
  totalRequests: "当前筛选范围内一共发起了多少次调用，成功和失败都会算在内。",
  providerCount: "当前筛选范围内实际产生过调用记录的供应商数量，没有调用记录的不计入。",
  successRate: "成功完成的调用次数占总调用次数的比例。失败或中途断开的调用不算成功。",
  failureRate: "发生错误或中途断开的调用次数占总调用次数的比例，和请求成功率相加为 100%。",
  failureCount: "发生错误或中途断开的调用次数，包括请求或配置问题、上游服务异常和调用方中断。",
  duration: "一次调用从平台开始处理，到结果发送完或确认失败的端到端等待时间。",
  avgDuration: "所有调用耗时相加后除以调用次数。成功和失败都会参与计算。",
  maxDuration: "当前筛选范围内耗时最长的那一次调用。成功和失败都会参与比较。",
  totalDuration: "把每一次调用的耗时相加。多次调用可能同时进行，所以它不等于实际经过的钟表时间。",
  rps: "当前小时和之前 23 个小时段内，总请求数除以从最早整点到本次加载或刷新时刻的秒数。它反映平均请求负载，不是瞬时峰值。",
  firstToken: "首 Token 延迟（TTFT）：从发起调用到模型开始返回实际回答的等待时间；非流式调用或没有实际回答时显示“—”。",
  avgFirstToken: "平均首 Token 延迟（TTFT）：只统计模型确实开始回答的流式调用，没有返回实际回答的调用不参与。",
  maxFirstToken: "最大首 Token 延迟（TTFT）：只比较模型确实开始回答的流式调用。",
  streamComplete: "从发起调用到流式回答正常结束所用的时间；它以完整结束信号为准，不等同于最后一个 Token 到达时间。",
  avgStreamComplete: "只统计模型正常结束回答的调用，用这些调用的完整回答时间计算平均值；中断或未结束的调用不参与。",
  maxStreamComplete: "只比较模型正常结束回答的调用，取完整回答时间最长的一次；中断或未结束的调用不参与。",
  requestCount: "这一小时内，符合本行供应商、模型、来源和结果大类的调用次数。",
  durationTotal: "这一行所有调用耗时相加，成功和失败都会计入。",
  providerFailure: "这个供应商发生错误或中途断开的调用次数。"
} as const;

const chartHelp = {
  hourlyTrend: "按小时查看请求数和请求成功率如何变化；选择结果大类后只展示该类请求数。",
  successComposition: "把当前筛选范围内的调用分成成功和错误两类，展示各自所占比例。",
  failureBreakdown: "只看异常调用，归并为请求或配置问题、上游服务异常、调用方中断和其他异常。",
  providerVolume: "按供应商汇总当前筛选范围内的调用次数，用来比较各供应商实际承载的调用量。"
} as const;

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
    outcomeGroup: filterOutcomeGroup.value || null,
    source: filterSource.value || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to,
    page: page.value
  }]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.listInternalModelCallRecords({
    providerId: filterProviderId.value || null,
    outcomeGroup: filterOutcomeGroup.value || null,
    source: filterSource.value || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to,
    page: page.value,
    size: pageSize
  })
});

const statsQuery = useQuery({
  queryKey: computed(() => ["internal-model-observability-stats", {
    providerId: filterProviderId.value || null,
    source: filterSource.value || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to
  }]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelCallStats({
    providerId: filterProviderId.value || null,
    source: filterSource.value || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to
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

/** 刷新时重取同一整点窗口内的看板和探活状态；跨小时后自动切换到新的 24 小时范围。 */
async function refreshAll() {
  queryWindow.value = createQueryWindow();
  await Promise.all([
    recordsQuery.refetch(),
    statsQuery.refetch(),
    probeStatusQuery.refetch()
  ]);
}

const probeStatuses = computed(() => probeStatusQuery.data.value ?? []);
const records = computed(() => recordsQuery.data.value?.items ?? []);
const recordsTotal = computed(() => recordsQuery.data.value?.total ?? 0);
// stats API 返回底层 outcome；页面按大类筛选，明细 API 使用相同大类，保证两块口径一致。
const stats = computed(() => (statsQuery.data.value ?? []).filter((row) =>
  !filterOutcomeGroup.value || outcomeGroupOf(row.outcome) === filterOutcomeGroup.value
));

const showRateMetrics = computed(() => !filterOutcomeGroup.value);

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

/** 异常结果按面向运营的四个大类汇总，底层具体原因只留在调用明细。 */
const failureBreakdown = computed<Array<{ group: InternalModelCallOutcomeGroup; label: string; count: number }>>(() => {
  const byGroup = new Map<InternalModelCallOutcomeGroup, number>();
  for (const row of stats.value) {
    if (row.outcome === "SUCCESS") continue;
    const group = outcomeGroupOf(row.outcome);
    byGroup.set(group, (byGroup.get(group) ?? 0) + row.requestCount);
  }
  return [...byGroup.entries()]
    .map(([group, count]) => ({
      group,
      label: outcomeGroupText[group],
      count
    }))
    .sort((a, b) => b.count - a.count);
});

/** 全局总览指标：只使用小时聚合可准确还原的计数、均值、最大值与 TTFT 均值。 */
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
  }
  const failureCount = totalRequests - successCount;
  const successRate = totalRequests === 0 ? 0 : Math.round((successCount / totalRequests) * 1000) / 10;
  // 错误率从已四舍五入的成功率补足，保证页面两项始终严格相加为 100%。
  const failureRate = totalRequests === 0 ? 0 : Math.round((100 - successRate) * 10) / 10;
  const avgDuration = totalRequests > 0
    ? Math.round(totalDurationMillis / totalRequests)
    : 0;
  const firstTokenAvg = firstTokenCount > 0
    ? Math.round(firstTokenMillisSum / firstTokenCount)
    : null;
  const streamCompleteAvg = streamCompleteCount > 0
    ? Math.round(streamCompleteMillisSum / streamCompleteCount)
    : null;
  const windowSeconds = Math.max(
    1,
    (new Date(queryWindow.value.to).getTime() - new Date(queryWindow.value.from).getTime()) / 1000
  );
  const rps = totalRequests / windowSeconds;
  const providerCount = new Set(stats.value.map((row) => row.providerId)).size;
  return {
    totalRequests, successCount, failureCount, successRate, failureRate,
    avgDuration, maxDuration,
    firstTokenAvg, firstTokenMax: firstTokenCount > 0 ? firstTokenMillisMax : null,
    firstTokenCount,
    streamCompleteAvg,
    streamCompleteMax: streamCompleteCount > 0 ? streamCompleteMillisMax : null,
    streamCompleteCount,
    totalDurationMillis,
    rps, providerCount
  };
});

/** 按查询窗口补齐无调用小时，避免趋势图跨空档直接连线造成持续有流量的错觉。 */
const hourlyTrend = computed(() => {
  const byHour = new Map<number, { total: number; success: number }>();
  for (const row of stats.value) {
    const hour = new Date(row.statHour).getTime();
    const cur = byHour.get(hour) ?? { total: 0, success: 0 };
    cur.total += row.requestCount;
    if (row.outcome === "SUCCESS") cur.success += row.requestCount;
    byHour.set(hour, cur);
  }
  if (byHour.size === 0) {
    return { hours: [], requests: [], successRate: [] };
  }
  const hours: number[] = [];
  for (
    let hour = new Date(queryWindow.value.from).getTime();
    hour < new Date(queryWindow.value.to).getTime();
    hour += HOUR_MILLIS
  ) {
    hours.push(hour);
  }
  return {
    hours: hours.map((hour) => new Date(hour).toISOString()),
    requests: hours.map((hour) => byHour.get(hour)?.total ?? 0),
    successRate: hours.map((h) => {
      const { total, success } = byHour.get(h) ?? { total: 0, success: 0 };
      return total === 0 ? 0 : Math.round((success / total) * 1000) / 10;
    })
  };
});

/** 成功率/失败率饼图数据。 */
const outcomePieData = computed(() => [
  { name: "成功", value: overallMetrics.value.successCount },
  { name: "错误", value: overallMetrics.value.failureCount }
].filter((item) => item.value > 0));

type GroupedHourlyStat = Omit<InternalModelCallHourlyStat, "outcome"> & {
  outcomeGroup: InternalModelCallOutcomeGroup;
};

/** 小时表按结果大类再次合并，避免同一个上游异常拆成多行。 */
const groupedHourlyStats = computed<GroupedHourlyStat[]>(() => {
  const grouped = new Map<string, GroupedHourlyStat>();
  for (const row of stats.value) {
    const outcomeGroup = outcomeGroupOf(row.outcome);
    const key = [row.statHour, row.providerId, row.model, row.endpoint, row.source, outcomeGroup].join("\u0000");
    const current = grouped.get(key);
    if (!current) {
      grouped.set(key, { ...row, outcomeGroup });
      continue;
    }
    current.requestCount += row.requestCount;
    current.durationMillisSum += row.durationMillisSum;
    current.durationMillisMax = Math.max(current.durationMillisMax, row.durationMillisMax);
    current.firstTokenMillisSum = (current.firstTokenMillisSum ?? 0) + (row.firstTokenMillisSum ?? 0);
    current.firstTokenMillisMax = Math.max(current.firstTokenMillisMax ?? 0, row.firstTokenMillisMax ?? 0);
    current.firstTokenCount = (current.firstTokenCount ?? 0) + (row.firstTokenCount ?? 0);
    current.streamCompleteMillisSum = (current.streamCompleteMillisSum ?? 0) + (row.streamCompleteMillisSum ?? 0);
    current.streamCompleteMillisMax = Math.max(
      current.streamCompleteMillisMax ?? 0,
      row.streamCompleteMillisMax ?? 0
    );
    current.streamCompleteCount = (current.streamCompleteCount ?? 0) + (row.streamCompleteCount ?? 0);
  }
  return [...grouped.values()].sort((left, right) =>
    left.statHour.localeCompare(right.statHour)
      || left.providerId.localeCompare(right.providerId)
      || left.outcomeGroup.localeCompare(right.outcomeGroup)
  );
});

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
        ...(showRateMetrics.value ? [{
          name: "成功率%",
          type: "line",
          yAxisIndex: 1,
          showSymbol: false,
          data: hourlyTrend.value.successRate,
          itemStyle: { color: "#16a34a" },
          lineStyle: { type: "dashed" }
        }] : [])
      ]
    }, true);
  }
  if (showRateMetrics.value && pieChartEl.value && pieChartEl.value.clientWidth > 0) {
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

// 首批数据会同时创建 v-if 中的图表容器，必须等 DOM 完成后再初始化 ECharts。
watch(() => stats.value, () => {
  void nextTick(renderCharts);
}, { deep: true, flush: "post" });

const outcomeText: Record<InternalModelCallOutcome, string> = {
  SUCCESS: "成功",
  PROXY_AUTH_FAILED: "代理鉴权失败",
  PROVIDER_UNAVAILABLE: "供应商配置不可用",
  REQUEST_INVALID: "请求校验失败",
  UPSTREAM_CONNECT_FAILED: "上游连接失败",
  UPSTREAM_FIRST_RESPONSE_TIMEOUT: "上游首响应超时",
  UPSTREAM_FIRST_EVENT_TIMEOUT: "上游首输出超时",
  UPSTREAM_STREAM_IDLE_TIMEOUT: "上游输出空闲超时",
  UPSTREAM_HTTP_ERROR: "上游 HTTP 错误",
  UPSTREAM_STREAM_INTERRUPTED: "流中断",
  UPSTREAM_STREAM_FAILED: "流读取失败",
  CLIENT_DISCONNECTED: "调用方提前断开",
  UNKNOWN_ERROR: "未知错误"
};

const outcomeDetailLabel = (outcome: InternalModelCallOutcome) => outcomeText[outcome] ?? "未知错误";

/** 耗时按量级自适应显示，短耗时保留毫秒，避免首 Token 延迟被四舍五入成 0。 */
function formatDuration(millis: number | null | undefined): string {
  if (millis === null || millis === undefined || !Number.isFinite(millis) || millis < 0) return "-";
  if (millis < 1000) return `${Math.round(millis)}ms`;
  const seconds = millis / 1000;
  return seconds < 10 ? `${seconds.toFixed(2)}s` : `${seconds.toFixed(1)}s`;
}

/** 低流量不压成 0.00，高流量保持两位小数。 */
function formatRps(value: number): string {
  if (!Number.isFinite(value) || value <= 0) return "0";
  if (value < 0.01) return value.toFixed(4);
  if (value < 1) return value.toFixed(3);
  return value.toFixed(2);
}

const outcomeTagType = (group: InternalModelCallOutcomeGroup): "success" | "danger" | "warning" | "info" => {
  if (group === "SUCCESS") return "success";
  if (group === "UPSTREAM_FAILURE") return "danger";
  if (group === "CALLER_INTERRUPTED" || group === "OTHER") return "warning";
  return "info";
};

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
  for (const row of statsQuery.data.value ?? []) {
    if (!seen.has(row.providerId)) {
      seen.add(row.providerId);
      options.push({ label: row.providerId, value: row.providerId });
    }
  }
  for (const row of records.value) {
    if (!seen.has(row.providerId)) {
      seen.add(row.providerId);
      options.push({ label: row.providerId, value: row.providerId });
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
        <span class="ta-imob-sub">默认查看当前小时和之前 23 个小时段的用户调用，统计截至本次加载或刷新时刻；也可以切换查看自动探活。所有耗时都从调用发起时开始计算；模型没有开始回答或没有正常结束时，相应指标显示“—”。</span>
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
            v-model="filterOutcomeGroup"
            placeholder="结果分类"
            clearable
            class="ta-imob-filter"
            @change="applyFilters"
          >
            <el-option
              v-for="(label, key) in outcomeGroupText"
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
            :disabled="recordsQuery.isFetching.value || statsQuery.isFetching.value"
            @click="refreshAll()"
          >
            <RefreshCw
              :size="12"
              :class="{ 'ta-imob-spin': recordsQuery.isFetching.value || statsQuery.isFetching.value }"
            />
            刷新数据
          </button>
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
            <el-table-column label="端到端请求延迟" min-width="170">
              <template #header>
                <MetricHelpLabel label="端到端请求延迟" :description="metricHelp.duration" />
              </template>
              <template #default="{ row }">{{ formatDuration(row.durationMillis) }}</template>
            </el-table-column>
            <el-table-column label="首 Token 延迟（TTFT）" min-width="190">
              <template #header>
                <MetricHelpLabel label="首 Token 延迟（TTFT）" :description="metricHelp.firstToken" />
              </template>
              <template #default="{ row }">{{ formatDuration(row.firstTokenMillis) }}</template>
            </el-table-column>
            <el-table-column label="流式响应完成时间" min-width="180">
              <template #header>
                <MetricHelpLabel label="流式响应完成时间" :description="metricHelp.streamComplete" />
              </template>
              <template #default="{ row }">{{ formatDuration(row.streamCompleteMillis) }}</template>
            </el-table-column>
            <el-table-column prop="model" label="模型" min-width="150">
              <template #default="{ row }">{{ row.model ?? "-" }}</template>
            </el-table-column>
            <el-table-column label="来源 / 用户 ID" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.source === 'PROBE'" type="info" size="small">探活</el-tag>
                <span v-else class="ta-imob-user-id">{{ row.ucid || "未知用户" }}</span>
              </template>
            </el-table-column>
            <el-table-column label="结果分类" min-width="180">
              <template #default="{ row }">
                <div class="ta-imob-outcome-cell">
                  <el-tag :type="outcomeTagType(outcomeGroupOf(row.outcome))" size="small">
                    {{ outcomeGroupText[outcomeGroupOf(row.outcome)] }}
                  </el-tag>
                  <span class="ta-imob-outcome-detail">{{ outcomeDetailLabel(row.outcome) }}</span>
                </div>
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
                  <MetricHelpLabel class="ta-imob-overview-label" label="请求数" :description="metricHelp.totalRequests" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.providerCount }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="有调用供应商数" :description="metricHelp.providerCount" />
                </div>
                <div v-if="showRateMetrics" class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value" :class="{ 'is-ok': overallMetrics.successRate >= 90 }">
                    {{ overallMetrics.successRate }}%
                  </span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="请求成功率" :description="metricHelp.successRate" />
                </div>
                <div v-if="showRateMetrics" class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value" :class="{ 'is-bad': overallMetrics.failureRate > 10 }">
                    {{ overallMetrics.failureRate }}%
                  </span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="请求错误率" :description="metricHelp.failureRate" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value" :class="{ 'is-bad': overallMetrics.failureCount > 0 }">
                    {{ overallMetrics.failureCount }}
                  </span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="错误请求数" :description="metricHelp.failureCount" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.avgDuration) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="平均端到端请求延迟" :description="metricHelp.avgDuration" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.maxDuration) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="最大端到端请求延迟" :description="metricHelp.maxDuration" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.totalDurationMillis) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="累计请求时长" :description="metricHelp.totalDuration" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatRps(overallMetrics.rps) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="平均请求速率（RPS）" :description="metricHelp.rps" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.firstTokenAvg) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="平均首 Token 延迟（TTFT）" :description="metricHelp.avgFirstToken" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.firstTokenMax) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="最大首 Token 延迟（TTFT）" :description="metricHelp.maxFirstToken" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.streamCompleteAvg) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="平均流式响应完成时间" :description="metricHelp.avgStreamComplete" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.streamCompleteMax) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="最大流式响应完成时间" :description="metricHelp.maxStreamComplete" />
                </div>
              </div>
            </div>

            <!-- 图表：趋势 / 成功率 / 失败分类 / 供应商对比。小时聚合无法还原分位数与分布，避免展示伪 P90/P95。 -->
            <div v-if="hourlyTrend.hours.length" class="ta-imob-charts">
              <div class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel
                    :label="showRateMetrics ? '请求量与请求成功率趋势' : '请求量趋势'"
                    :description="chartHelp.hourlyTrend"
                  />
                </h4>
                <div ref="trendChartEl" class="ta-imob-chart" />
              </div>
              <div v-if="showRateMetrics" class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel label="请求结果分布" :description="chartHelp.successComposition" />
                </h4>
                <div ref="pieChartEl" class="ta-imob-chart" />
              </div>
              <div v-if="failureBarData.length" class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel label="异常结果分布" :description="chartHelp.failureBreakdown" />
                </h4>
                <div ref="failureChartEl" class="ta-imob-chart" />
              </div>
              <div v-if="providerBarData.length" class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel label="供应商请求量" :description="chartHelp.providerVolume" />
                </h4>
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
                      <MetricHelpLabel class="ta-imob-metric-label" label="请求数" :description="metricHelp.totalRequests" />
                    </div>
                    <div v-if="showRateMetrics" class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value" :class="{ 'is-ok': metric.successRate >= 90 }">
                        {{ metric.successRate }}%
                      </span>
                      <MetricHelpLabel class="ta-imob-metric-label" label="请求成功率" :description="metricHelp.successRate" />
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgDurationMillis) }}</span>
                      <MetricHelpLabel class="ta-imob-metric-label" label="平均端到端延迟" :description="metricHelp.avgDuration" />
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.maxDurationMillis) }}</span>
                      <MetricHelpLabel class="ta-imob-metric-label" label="最大端到端延迟" :description="metricHelp.maxDuration" />
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgFirstTokenMillis) }}</span>
                      <MetricHelpLabel class="ta-imob-metric-label" label="平均 TTFT" :description="metricHelp.avgFirstToken" />
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgStreamCompleteMillis) }}</span>
                      <MetricHelpLabel class="ta-imob-metric-label" label="平均流式完成时间" :description="metricHelp.avgStreamComplete" />
                    </div>
                    <div class="ta-imob-metric-cell">
                      <span class="ta-imob-metric-value" :class="{ 'is-bad': metric.failureCount > 0 }">
                        {{ metric.failureCount }}
                      </span>
                      <MetricHelpLabel class="ta-imob-metric-label" label="错误请求数" :description="metricHelp.providerFailure" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else-if="!statsQuery.isLoading.value" class="ta-imob-placeholder">暂无聚合数据</div>

            <!-- 按小时明细 -->
            <el-table v-if="groupedHourlyStats.length" :data="groupedHourlyStats" stripe class="ta-imob-hourly-table">
              <el-table-column prop="statHour" label="小时" min-width="160">
                <template #default="{ row }">{{ formatTime(row.statHour) }}</template>
              </el-table-column>
              <el-table-column prop="providerId" label="供应商" min-width="140" />
              <el-table-column prop="model" label="模型" min-width="140" />
              <el-table-column label="结果分类" min-width="150">
                <template #default="{ row }">
                  <el-tag :type="outcomeTagType(row.outcomeGroup)" size="small">
                    {{ outcomeGroupText[row.outcomeGroup as InternalModelCallOutcomeGroup] }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="requestCount" label="请求数" width="110">
                <template #header>
                  <MetricHelpLabel label="请求数" :description="metricHelp.requestCount" />
                </template>
              </el-table-column>
              <el-table-column label="累计请求时长" min-width="135">
                <template #header>
                  <MetricHelpLabel label="累计请求时长" :description="metricHelp.durationTotal" />
                </template>
                <template #default="{ row }">{{ formatDuration(row.durationMillisSum) }}</template>
              </el-table-column>
              <el-table-column label="最大端到端延迟" min-width="150">
                <template #header>
                  <MetricHelpLabel label="最大端到端延迟" :description="metricHelp.maxDuration" />
                </template>
                <template #default="{ row }">{{ formatDuration(row.durationMillisMax) }}</template>
              </el-table-column>
              <el-table-column label="平均 TTFT" min-width="125">
                <template #header>
                  <MetricHelpLabel label="平均 TTFT" :description="metricHelp.avgFirstToken" />
                </template>
                <template #default="{ row }">
                  {{ formatDuration((row.firstTokenCount ?? 0) > 0 ? Math.round((row.firstTokenMillisSum ?? 0) / (row.firstTokenCount ?? 1)) : null) }}
                </template>
              </el-table-column>
              <el-table-column label="最大 TTFT" min-width="125">
                <template #header>
                  <MetricHelpLabel label="最大 TTFT" :description="metricHelp.maxFirstToken" />
                </template>
                <template #default="{ row }">{{ formatDuration((row.firstTokenCount ?? 0) > 0 ? (row.firstTokenMillisMax ?? 0) : null) }}</template>
              </el-table-column>
              <el-table-column label="平均流式完成时间" min-width="155">
                <template #header>
                  <MetricHelpLabel label="平均流式完成时间" :description="metricHelp.avgStreamComplete" />
                </template>
                <template #default="{ row }">
                  {{ formatDuration((row.streamCompleteCount ?? 0) > 0 ? Math.round((row.streamCompleteMillisSum ?? 0) / (row.streamCompleteCount ?? 1)) : null) }}
                </template>
              </el-table-column>
              <el-table-column label="最大流式完成时间" min-width="155">
                <template #header>
                  <MetricHelpLabel label="最大流式完成时间" :description="metricHelp.maxStreamComplete" />
                </template>
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
  /* 整张 BI 看板由最外层统一滚动，内部区域不能在固定高度里收缩后让内容互相覆盖。 */
  flex: 0 0 auto;
  min-height: auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.ta-imob-section {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: auto;
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
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
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
.ta-imob-user-id {
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
.ta-imob-outcome-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}
.ta-imob-outcome-detail {
  color: #64748b;
  font-size: 11px;
  line-height: 1.25;
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
