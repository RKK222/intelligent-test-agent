<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Activity, BookOpen, ChevronDown, ChevronUp, ExternalLink, FileText, RefreshCw } from "lucide-vue-next";
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
const filterUcid = ref("");
const customTimeRange = ref<[string, string] | null>(null);
const page = ref(1);
const pageSize = 20;
const HOUR_MILLIS = 3_600_000;
const selectedWindowHours = ref<number>(24);
const showGlossary = ref(true);
const showDocDialog = ref(false);

const windowHourOptions = [
  { label: "最近 1 小时", value: 1 },
  { label: "最近 6 小时", value: 6 },
  { label: "最近 12 小时", value: 12 },
  { label: "最近 24 小时", value: 24 },
  { label: "最近 3 天", value: 72 },
  { label: "最近 7 天", value: 168 }
];

type QueryWindow = { from: string; to: string };

/** 查询当前小时和之前指定小时数（基于选定时间段），并以实际加载时刻收口。 */
function createQueryWindow(hours = selectedWindowHours.value, nowMillis = Date.now()): QueryWindow {
  const currentHourMillis = Math.floor(nowMillis / HOUR_MILLIS) * HOUR_MILLIS;
  return {
    from: new Date(currentHourMillis - (hours - 1) * HOUR_MILLIS).toISOString(),
    to: new Date(nowMillis).toISOString()
  };
}

const queryWindow = ref<QueryWindow>(createQueryWindow());

function onWindowHoursChange() {
  if (selectedWindowHours.value) {
    customTimeRange.value = null;
    queryWindow.value = createQueryWindow(selectedWindowHours.value);
    applyFilters();
  }
}

function onCustomTimeRangeChange(val: [string, string] | null) {
  if (val && val.length === 2) {
    selectedWindowHours.value = 0;
    queryWindow.value = {
      from: new Date(val[0]).toISOString(),
      to: new Date(val[1]).toISOString()
    };
  } else {
    selectedWindowHours.value = 24;
    queryWindow.value = createQueryWindow(24);
  }
  applyFilters();
}

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

// AIPerf (NVIDIA) 指标规范与业界标准英文缩写说明
const glossaryItems = [
  { abbr: "TTFT", name: "Time to First Token", desc: "首 Token 延迟：发起请求到接收到模型首个 Token 的时间（NVIDIA GenAI Perf 核心延迟指标）。" },
  {
    abbr: "ITL / TPOT",
    name: "Inter-Token Latency / Time Per Output Token",
    desc: "Token 输出间隔耗时：生成过程中连续两个 Output Token 之间的平均生成间隔。",
    isPending: true,
    pendingText: "（暂未计算）"
  },
  { abbr: "SCT", name: "Stream Completion Time", desc: "流式完成时间：发起请求到流式响应正常结束的总耗时。" },
  { abbr: "E2E", name: "End-to-End Latency", desc: "端到端延迟：发起请求到接收到完整响应或异常终止的总端到端时长。" },
  { abbr: "RPS", name: "Requests Per Second", desc: "每秒请求数：在统计时间窗口内的平均每秒请求处理量（Throughput 吞吐量指标）。" },
  { abbr: "REQ", name: "Requests", desc: "请求总数：包含成功与失败在内的总调用次数。" },
  { abbr: "SR", name: "Success Rate", desc: "请求成功率：成功完成的请求占总请求数的百分比。" },
  { abbr: "FR", name: "Failure Rate", desc: "请求错误率：失败或中途中断的请求占总请求数的百分比。" }
] as const;

// 页面提示只讲业务含义、分母和空值规则，计算逻辑保持不变。
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
  await nextTick();
  renderCharts();
}

const probeStatuses = computed(() => probeStatusQuery.data.value ?? []);
/** 从已获取的明细数据中动态汇总所有出现过的用户 ID 供下拉选单快捷选择。 */
const ucidOptions = computed(() => {
  const set = new Set<string>();
  for (const item of recordsQuery.data.value?.items ?? []) {
    if (item.ucid && item.ucid.trim()) {
      set.add(item.ucid.trim());
    }
  }
  return [...set].sort();
});
const records = computed(() => {
  const list = recordsQuery.data.value?.items ?? [];
  if (!filterUcid.value) return list;
  const keyword = filterUcid.value.trim().toLowerCase();
  return list.filter((row) => row.ucid && row.ucid.toLowerCase().includes(keyword));
});
const recordsTotal = computed(() => records.value.length);
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

/** 无论是否有调用数据，均生成 24 小时区间完整的轴刻度，避免刷新或类型切换时数据变为空导致图表容器卸载。 */
const hourlyTrend = computed(() => {
  const byHour = new Map<number, { total: number; success: number }>();
  for (const row of stats.value) {
    const hour = new Date(row.statHour).getTime();
    const cur = byHour.get(hour) ?? { total: 0, success: 0 };
    cur.total += row.requestCount;
    if (row.outcome === "SUCCESS") cur.success += row.requestCount;
    byHour.set(hour, cur);
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
  { name: "成功 (Success)", value: overallMetrics.value.successCount },
  { name: "错误 (Failure)", value: overallMetrics.value.failureCount }
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

/** 失败分类条形图数据。 */
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
  // 当组件重绘、DOM 节点更新（getDom 不匹配）或实例销毁时，dispose 旧实例并在新 DOM 节点初始化
  if (holder.current) {
    if (holder.current.isDisposed() || holder.current.getDom() !== el || el.clientWidth === 0) {
      holder.current.dispose();
      holder.current = null;
    }
  }
  if (!holder.current && el.clientWidth > 0) {
    holder.current = echarts.init(el);
  }
  return holder.current;
}

function renderCharts() {
  if (trendChartEl.value && trendChartEl.value.clientWidth > 0) {
    trendChart = ensureChart(trendChartEl.value, { current: trendChart });
    trendChart?.setOption({
      animation: false,
      tooltip: { trigger: "axis" },
      legend: { top: 0, left: "center", itemGap: 16, textStyle: { fontSize: 11 } },
      grid: { top: 34, left: 48, right: 44, bottom: 28 },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: hourlyTrend.value.hours.map(h => new Date(h).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }))
      },
      yAxis: [
        { type: "value", name: "REQ", scale: true },
        { type: "value", name: "SR %", max: 100, min: 0, splitLine: { show: false } }
      ],
      series: [
        {
          name: "REQ",
          type: "line",
          showSymbol: false,
          data: hourlyTrend.value.requests,
          itemStyle: { color: "#2563eb" }
        },
        ...(showRateMetrics.value ? [{
          name: "SR %",
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
    pieChart?.setOption({
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
    failureChart?.setOption({
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
    providerChart?.setOption({
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
  void nextTick(renderCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  [trendChart, pieChart, failureChart, providerChart].forEach((chart) => chart?.dispose());
  trendChart = null;
  pieChart = null;
  failureChart = null;
  providerChart = null;
});

// 数据变动后 post-flush 触发重新渲染，确保在新 DOM 或过滤数据更新后重绘图表
watch([() => stats.value, filterProviderId, filterOutcomeGroup, filterSource], () => {
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
        <div class="ta-imob-title-row">
          <h3 class="ta-imob-title">内部模型调用可观测</h3>
          <button
            v-if="!showGlossary"
            type="button"
            class="ta-imob-expand-glossary-btn"
            @click="showGlossary = true"
          >
            <BookOpen :size="13" />
            <span>展开指标英文缩写指南 (Glossary)</span>
            <ChevronDown :size="13" />
          </button>
        </div>
        <span class="ta-imob-sub">默认查看当前 {{ selectedWindowHours }} 小时时间段的用户调用，统计截至本次加载或刷新时刻。所有的英文缩写见页首对照指南。</span>

        <!-- 页首 AIPerf / 业界指标英文缩写对照指南 (Glossary) -->
        <div v-if="showGlossary" class="ta-imob-glossary-card">
          <div class="ta-imob-glossary-header">
            <div class="ta-imob-glossary-header-title">
              <BookOpen :size="14" class="ta-imob-glossary-icon" />
              <strong>AIPerf & 业界指标英文缩写指南</strong>
            </div>
            <div class="ta-imob-glossary-actions">
              <button
                type="button"
                class="ta-imob-spec-link"
                @click="showDocDialog = true"
              >
                <FileText :size="12" />
                <span>NVIDIA AIPerf 规范</span>
                <ExternalLink :size="11" />
              </button>
              <button
                type="button"
                class="ta-imob-collapse-btn"
                @click="showGlossary = false"
              >
                <ChevronUp :size="13" />
                <span>收起指南</span>
              </button>
            </div>
          </div>
          <div class="ta-imob-glossary-grid">
            <div
              v-for="item in glossaryItems"
              :key="item.abbr"
              class="ta-imob-glossary-item"
              :class="{ 'is-pending': item.isPending }"
            >
              <div class="ta-imob-glossary-item-top">
                <code class="ta-imob-glossary-abbr">{{ item.abbr }}</code>
                <span v-if="item.pendingText" class="ta-imob-glossary-pending-badge">{{ item.pendingText }}</span>
              </div>
              <span class="ta-imob-glossary-name">{{ item.name }}</span>
              <span class="ta-imob-glossary-desc">{{ item.desc }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="ta-imob-combined">
        <div class="ta-imob-filter-bar">
          <span class="ta-imob-filter-title">筛选条件</span>
          <el-select
            v-model="selectedWindowHours"
            placeholder="时间范围"
            class="ta-imob-filter"
            @change="onWindowHoursChange"
          >
            <el-option
              v-for="option in windowHourOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-date-picker
            v-model="customTimeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="ta-imob-filter-date"
            @change="onCustomTimeRangeChange"
          />
          <el-select
            v-model="filterUcid"
            placeholder="按人 (用户)"
            clearable
            filterable
            allow-create
            default-first-option
            class="ta-imob-filter"
            @change="applyFilters"
          >
            <el-option
              v-for="user in ucidOptions"
              :key="user"
              :label="user"
              :value="user"
            />
          </el-select>
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
            <el-table-column label="E2E Latency" min-width="170">
              <template #header>
                <MetricHelpLabel label="E2E Latency" :description="metricHelp.duration" />
              </template>
              <template #default="{ row }">{{ formatDuration(row.durationMillis) }}</template>
            </el-table-column>
            <el-table-column label="TTFT" min-width="170">
              <template #header>
                <MetricHelpLabel label="TTFT" :description="metricHelp.firstToken" />
              </template>
              <template #default="{ row }">{{ formatDuration(row.firstTokenMillis) }}</template>
            </el-table-column>
            <el-table-column label="SCT" min-width="170">
              <template #header>
                <MetricHelpLabel label="SCT" :description="metricHelp.streamComplete" />
              </template>
              <template #default="{ row }">{{ formatDuration(row.streamCompleteMillis) }}</template>
            </el-table-column>
            <el-table-column prop="model" label="Model" min-width="150">
              <template #default="{ row }">{{ row.model ?? "-" }}</template>
            </el-table-column>
            <el-table-column label="Source / User" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.source === 'PROBE'" type="info" size="small">探活</el-tag>
                <span v-else class="ta-imob-user-id">{{ row.ucid || "未知用户" }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Outcome Group" min-width="180">
              <template #default="{ row }">
                <div class="ta-imob-outcome-cell">
                  <el-tag :type="outcomeTagType(outcomeGroupOf(row.outcome))" size="small">
                    {{ outcomeGroupText[outcomeGroupOf(row.outcome)] }}
                  </el-tag>
                  <span class="ta-imob-outcome-detail">{{ outcomeDetailLabel(row.outcome) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="startedAt" label="Time" min-width="180">
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
            <!-- 全局总览指标 -->
            <div v-if="overallMetrics.totalRequests !== undefined" class="ta-imob-overview">
              <h4 class="ta-imob-overview-title">Overview</h4>
              <div class="ta-imob-overview-grid">
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.totalRequests }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="REQ" :description="metricHelp.totalRequests" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ overallMetrics.providerCount }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Providers" :description="metricHelp.providerCount" />
                </div>
                <div v-if="showRateMetrics" class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value" :class="{ 'is-ok': overallMetrics.successRate >= 90 }">
                    {{ overallMetrics.successRate }}%
                  </span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="SR" :description="metricHelp.successRate" />
                </div>
                <div v-if="showRateMetrics" class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value" :class="{ 'is-bad': overallMetrics.failureRate > 10 }">
                    {{ overallMetrics.failureRate }}%
                  </span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="FR" :description="metricHelp.failureRate" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value" :class="{ 'is-bad': overallMetrics.failureCount > 0 }">
                    {{ overallMetrics.failureCount }}
                  </span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Failures" :description="metricHelp.failureCount" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.avgDuration) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Avg E2E" :description="metricHelp.avgDuration" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.maxDuration) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Max E2E" :description="metricHelp.maxDuration" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.totalDurationMillis) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Total Duration" :description="metricHelp.totalDuration" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatRps(overallMetrics.rps) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="RPS" :description="metricHelp.rps" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.firstTokenAvg) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Avg TTFT" :description="metricHelp.avgFirstToken" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.firstTokenMax) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Max TTFT" :description="metricHelp.maxFirstToken" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.streamCompleteAvg) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Avg SCT" :description="metricHelp.avgStreamComplete" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatDuration(overallMetrics.streamCompleteMax) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Max SCT" :description="metricHelp.maxStreamComplete" />
                </div>
              </div>
            </div>

            <!-- 图表：趋势 / 成功率 / 失败分类 / 供应商对比 -->
            <div class="ta-imob-charts">
              <!-- 折线图独立占满全行 -->
              <div class="ta-imob-chart-card ta-imob-chart-card-full">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel
                    :label="showRateMetrics ? '请求数与成功率趋势' : '请求数趋势'"
                    :description="chartHelp.hourlyTrend"
                  />
                </h4>
                <div ref="trendChartEl" class="ta-imob-chart ta-imob-chart-trend" />
              </div>
              <div v-if="showRateMetrics" class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel label="调用结果分布" :description="chartHelp.successComposition" />
                </h4>
                <div ref="pieChartEl" class="ta-imob-chart" />
              </div>
              <div v-if="failureBarData.length" class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel label="失败原因分类" :description="chartHelp.failureBreakdown" />
                </h4>
                <div ref="failureChartEl" class="ta-imob-chart" />
              </div>
              <div v-if="providerBarData.length" class="ta-imob-chart-card">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel label="供应商请求量对比" :description="chartHelp.providerVolume" />
                </h4>
                <div ref="providerChartEl" class="ta-imob-chart" />
              </div>
            </div>

            <!-- 按供应商汇总 -->
            <div v-if="providerMetrics.length">
              <h4 class="ta-imob-overview-title">按供应商</h4>
              <div class="ta-imob-metric-grid">
                <div v-for="metric in providerMetrics" :key="metric.providerId" class="ta-imob-metric-card">
                  <div class="ta-imob-metric-provider">{{ metric.providerId }}</div>
                  <div class="ta-imob-metric-body">
                    <div class="ta-imob-metric-cell">
                      <MetricHelpLabel class="ta-imob-metric-label" label="REQ" :description="metricHelp.totalRequests" />
                      <span class="ta-imob-metric-value">{{ metric.totalRequests }}</span>
                    </div>
                    <div v-if="showRateMetrics" class="ta-imob-metric-cell">
                      <MetricHelpLabel class="ta-imob-metric-label" label="SR" :description="metricHelp.successRate" />
                      <span class="ta-imob-metric-value" :class="{ 'is-ok': metric.successRate >= 90 }">
                        {{ metric.successRate }}%
                      </span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <MetricHelpLabel class="ta-imob-metric-label" label="Avg E2E" :description="metricHelp.avgDuration" />
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgDurationMillis) }}</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <MetricHelpLabel class="ta-imob-metric-label" label="Max E2E" :description="metricHelp.maxDuration" />
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.maxDurationMillis) }}</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <MetricHelpLabel class="ta-imob-metric-label" label="Avg TTFT" :description="metricHelp.avgFirstToken" />
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgFirstTokenMillis) }}</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <MetricHelpLabel class="ta-imob-metric-label" label="Avg SCT" :description="metricHelp.avgStreamComplete" />
                      <span class="ta-imob-metric-value">{{ formatDuration(metric.avgStreamCompleteMillis) }}</span>
                    </div>
                    <div class="ta-imob-metric-cell">
                      <MetricHelpLabel class="ta-imob-metric-label" label="Failures" :description="metricHelp.providerFailure" />
                      <span class="ta-imob-metric-value" :class="{ 'is-bad': metric.failureCount > 0 }">
                        {{ metric.failureCount }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else-if="!statsQuery.isLoading.value" class="ta-imob-placeholder">暂无聚合数据</div>
          </div>
        </section>
      </div>

      <!-- 性能指标规范定义 (离线指南弹窗) -->
      <el-dialog
        v-model="showDocDialog"
        title="AIPerf & 业界模型性能指标规范定义 (离线指南)"
        width="760px"
        append-to-body
      >
        <div class="ta-imob-doc-content">
          <p class="ta-imob-doc-lead">
            本文档参考
            <a
              href="https://docs.nvidia.com/aiperf/dev/reference/ai-perf-metrics-reference"
              target="_blank"
              rel="noopener noreferrer"
              class="ta-imob-external-link"
            >
              NVIDIA GenAI Perf / AI Perf Metrics Reference
            </a>
            官方规范标准定义。对应本地源码 Markdown 文件位于 <code>docs/standards/metrics-glossary.md</code>。
          </p>

          <table class="ta-imob-doc-table">
            <thead>
              <tr>
                <th>缩写</th>
                <th>全称 (Full Name)</th>
                <th>中文名称</th>
                <th>状态 / 计算说明</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>TTFT</code></td>
                <td>Time to First Token</td>
                <td>首 Token 延迟</td>
                <td>已统计（计算模型生成首包 Token 的启动延迟）</td>
              </tr>
              <tr class="is-pending-row">
                <td><code>ITL / TPOT</code></td>
                <td>Inter-Token Latency / Time Per Output Token</td>
                <td>Token 输出间隔 / 单 Token 耗时</td>
                <td><span class="ta-imob-orange-badge">[暂未计算]</span>（待代理协议提取 Token 粒度时间戳后计算）</td>
              </tr>
              <tr>
                <td><code>SCT</code></td>
                <td>Stream Completion Time</td>
                <td>流式完成时间</td>
                <td>已统计（计算流式响应完整结束传输的耗时）</td>
              </tr>
              <tr>
                <td><code>E2E</code></td>
                <td>End-to-End Latency</td>
                <td>端到端总延迟</td>
                <td>已统计（客户端 HTTP 请求开始到整体结束的总耗时）</td>
              </tr>
              <tr>
                <td><code>RPS</code></td>
                <td>Requests Per Second</td>
                <td>每秒请求数 (吞吐量)</td>
                <td>已统计（统计窗口内平均每秒请求处理量）</td>
              </tr>
              <tr>
                <td><code>REQ</code></td>
                <td>Requests</td>
                <td>请求总数</td>
                <td>已统计（全量请求计数，含成功与异常）</td>
              </tr>
              <tr>
                <td><code>SR</code></td>
                <td>Success Rate</td>
                <td>请求成功率</td>
                <td>已统计（成功请求占总请求数的百分比）</td>
              </tr>
              <tr>
                <td><code>FR</code></td>
                <td>Failure Rate</td>
                <td>请求错误率</td>
                <td>已统计（异常或中断请求占总请求数的百分比）</td>
              </tr>
            </tbody>
          </table>

          <div class="ta-imob-doc-footer">
            <span>官方参考链接：<a href="https://docs.nvidia.com/aiperf/dev/reference/ai-perf-metrics-reference" target="_blank" rel="noopener noreferrer">NVIDIA GenAI Perf / AI Perf Metrics Reference ↗</a></span>
          </div>
        </div>
      </el-dialog>
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
  gap: 6px;
}
.ta-imob-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.ta-imob-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}
.ta-imob-expand-glossary-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f0f9ff;
  color: #0284c7;
  border: 1px solid #bae6fd;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}
.ta-imob-expand-glossary-btn:hover {
  background: #e0f2fe;
  color: #0369a1;
  border-color: #7dd3fc;
}
.ta-imob-sub {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}
.ta-imob-glossary-card {
  margin-top: 6px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f0f9ff;
  color: #1e293b;
}
.ta-imob-glossary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e0f2fe;
}
.ta-imob-glossary-header-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #0284c7;
}
.ta-imob-glossary-header-title strong {
  font-size: 13px;
  font-weight: 600;
  color: #0369a1;
}
.ta-imob-glossary-icon {
  color: #0284c7;
  flex-shrink: 0;
}
.ta-imob-glossary-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ta-imob-spec-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 6px;
  background: #e0f2fe;
  color: #0369a1;
  border: 1px solid #bae6fd;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}
.ta-imob-spec-link:hover {
  background: #bae6fd;
  color: #0284c7;
  border-color: #7dd3fc;
}
.ta-imob-collapse-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 6px;
  background: #ffffff;
  color: #475569;
  border: 1px solid #cbd5e1;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}
.ta-imob-collapse-btn:hover {
  background: #f8fafc;
  color: #1e293b;
  border-color: #94a3b8;
}
.ta-imob-glossary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px 14px;
}
.ta-imob-glossary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  border-radius: 6px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
}
.ta-imob-glossary-item.is-pending {
  border-color: #fcd34d;
  background-color: #fffbeb;
}
.ta-imob-glossary-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ta-imob-glossary-pending-badge {
  font-size: 10px;
  font-weight: 600;
  color: #d97706;
  background-color: #fef3c7;
  border: 1px solid #fcd34d;
  border-radius: 4px;
  padding: 1px 5px;
}
.ta-imob-doc-content {
  font-size: 13px;
  color: #1f2328;
  line-height: 1.6;
}
.ta-imob-doc-lead {
  margin-top: 0;
  margin-bottom: 12px;
  color: #57606a;
}
.ta-imob-external-link {
  color: #0969da;
  text-decoration: underline;
  font-weight: 500;
}
.ta-imob-doc-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
}
.ta-imob-doc-table th,
.ta-imob-doc-table td {
  border: 1px solid #d0d7de;
  padding: 8px 12px;
  text-align: left;
  font-size: 12px;
}
.ta-imob-doc-table th {
  background-color: #f6f8fa;
  font-weight: 600;
}
.ta-imob-doc-table tr.is-pending-row {
  background-color: #fffbeb;
}
.ta-imob-orange-badge {
  color: #d97706;
  font-weight: 700;
}
.ta-imob-doc-footer {
  margin-top: 12px;
  font-size: 11px;
  color: #57606a;
  border-top: 1px dashed #d0d7de;
  padding-top: 8px;
}
.ta-imob-glossary-abbr {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 700;
  font-size: 12px;
  color: #0969da;
}
.ta-imob-glossary-name {
  font-size: 11px;
  font-weight: 600;
  color: #24292f;
}
.ta-imob-glossary-desc {
  font-size: 11px;
  color: #57606a;
  line-height: 1.35;
}
.ta-imob-combined {
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
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
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
.ta-imob-chart-card-full {
  grid-column: 1 / -1;
}
.ta-imob-chart {
  width: 100%;
  height: 240px;
}
.ta-imob-chart-trend {
  height: 260px;
}
.ta-imob-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
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

/* 优化“按供应商”卡片布局：采用上下垂直结构 (Label在上，Value在下)，防止狭窄列内字体折叠堆叠 */
.ta-imob-metric-body {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 8px;
}
.ta-imob-metric-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  padding: 6px 8px;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 6px;
}
.ta-imob-metric-label {
  font-size: 11px;
  color: #6b7280;
  font-weight: 500;
  white-space: nowrap;
}
.ta-imob-metric-value {
  font-size: 15px;
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
.ta-imob-filter-date {
  max-width: 340px;
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
