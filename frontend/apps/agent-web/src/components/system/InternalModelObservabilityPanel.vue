<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Activity, BookOpen, ChevronDown, ChevronUp, Clock, ExternalLink, FileText, Filter, RefreshCw } from "lucide-vue-next";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  InternalModelCallHourlyStat,
  InternalModelCallOutcome,
  InternalModelCallOutcomeGroup,
  InternalModelCallSource,
  InternalModelLatencyDistribution,
  InternalModelThroughputDistribution
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
const pageSize = ref(20);
const pageSizeOptions = [20, 50, 100];
const MILLISECONDS_PER_SECOND = 1000;
const HOUR_MILLIS = 3_600_000;
const selectedWindowHours = ref<number>(24);
const showGlossary = ref(true);
const showDocDialog = ref(false);
const showBenchmarkDialog = ref(false);

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
const timePopoverVisible = ref(false);
const isCustomTime = ref(false);

function selectPreset(hours: number) {
  selectedWindowHours.value = hours;
  isCustomTime.value = false;
  customTimeRange.value = null;
  queryWindow.value = createQueryWindow(hours);
  timePopoverVisible.value = false;
  applyFilters();
}

function onCustomTimeChange(val: unknown) {
  if (val === null) {
    isCustomTime.value = false;
    selectedWindowHours.value = 24;
    queryWindow.value = createQueryWindow(24);
    applyFilters();
    return;
  }

  // 日期范围组件选完第一个端点时会发出中间态；必须等待两个合法端点都就绪后再查询和收起。
  if (!Array.isArray(val) || val.length !== 2
    || typeof val[0] !== "string" || !val[0].trim()
    || typeof val[1] !== "string" || !val[1].trim()) return;
  const fromMillis = new Date(val[0].replace(" ", "T")).getTime();
  const toMillis = new Date(val[1].replace(" ", "T")).getTime();
  if (!Number.isFinite(fromMillis) || !Number.isFinite(toMillis) || fromMillis >= toMillis) return;

  isCustomTime.value = true;
  selectedWindowHours.value = 0;
  queryWindow.value = {
    from: new Date(fromMillis).toISOString(),
    to: new Date(toMillis).toISOString()
  };
  timePopoverVisible.value = false;
  applyFilters();
}

function formatShortTime(dateStr: string): string {
  if (!dateStr) return "";
  const parts = dateStr.split(" ");
  if (parts.length === 2) {
    return `${parts[0].slice(5)} ${parts[1].slice(0, 5)}`;
  }
  return dateStr;
}

const timeDisplayLabel = computed(() => {
  if (isCustomTime.value && customTimeRange.value && customTimeRange.value.length === 2) {
    return `${formatShortTime(customTimeRange.value[0])} 至 ${formatShortTime(customTimeRange.value[1])}`;
  }
  const found = windowHourOptions.find((o) => o.value === selectedWindowHours.value);
  return found ? found.label : "最近 24 小时";
});

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

interface GlossaryItem {
  abbr: string;
  name: string;
  desc: string;
}

// AIPerf (NVIDIA) 指标规范与业界标准英文缩写说明
const glossaryItems: GlossaryItem[] = [
  { abbr: "TTFT", name: "Time to First Token", desc: "首 Token 延迟：发起请求到接收到模型首个 Token 的时间（NVIDIA GenAI Perf 核心延迟指标）。" },
  {
    abbr: "ITL / TPOT",
    name: "Inter-Token Latency / Time Per Output Token",
    desc: "Token 输出间隔：模型开始回答后，后续每个 Token 平均要等多久，页面按毫秒展示；至少输出 2 个 Token 且供应商返回准确用量时才统计。"
  },
  {
    abbr: "Output TPS",
    name: "Output Tokens Per Second",
    desc: "生成吞吐量：模型开始回答后每秒输出多少 Token；至少输出 2 个 Token、首末 Token 间隔大于 0 且供应商返回准确用量时才统计。"
  },
  { abbr: "SCT", name: "Stream Completion Time", desc: "流式完成时间：发起请求到流式响应正常结束的总耗时。" },
  { abbr: "E2E", name: "End-to-End Latency", desc: "端到端延迟：发起请求到接收到完整响应或异常终止的总端到端时长。" },
  { abbr: "RPS", name: "Requests Per Second", desc: "每秒请求数：在统计时间窗口内的平均每秒请求处理量（Throughput 吞吐量指标）。" },
  { abbr: "REQ", name: "Requests", desc: "请求总数：包含成功与失败在内的总调用次数。" },
  { abbr: "SR", name: "Success Rate", desc: "请求成功率：成功完成的请求占总请求数的百分比。" },
  { abbr: "FR", name: "Failure Rate", desc: "请求错误率：失败或中途中断的请求占总请求数的百分比。" }
];

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
  interTokenLatency: "ITL / TPOT：模型开始回答后，后续每个输出 Token 平均间隔多久，页面按毫秒展示。至少输出 2 个 Token 且供应商返回准确用量时才显示；不会把数据块数量当作 Token 数。",
  outputTps: "Output TPS：模型开始回答后平均每秒生成多少个输出 Token。只统计供应商返回准确用量且首末 Token 时间完整的调用。",
  avgInterTokenLatency: "平均 ITL / TPOT：把当前范围内每次可靠的 Token 输出间隔相加，再除以可靠样本数；按毫秒展示。没有准确输出 Token 数的调用不参与。",
  maxInterTokenLatency: "最大 ITL / TPOT：当前范围内最慢的一次可靠 Token 输出间隔；按毫秒展示。没有准确输出 Token 数的调用不参与。",
  avgOutputTps: "平均 Output TPS：先逐次计算每条完整流的输出速度，再对合格样本取平均；不会用 1000 ÷ 平均 ITL 反推。",
  medianOutputTps: "P50 Output TPS：一半合格调用的输出速度不低于该值，比平均值更不容易被少量极快或极慢请求影响。",
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
  ttftDistribution: "按模型厂商比较开始回答前的等待时间。每个厂商一个箱体：箱体表示该厂商中间一半的调用，箱内横线表示中位数，两端表示最短和最长等待时间。",
  itlDistribution: "按模型厂商比较开始回答后的输出节奏。每个厂商一个箱体：箱体表示该厂商中间一半的调用，箱内横线表示中位数，两端表示最快和最慢的平均 Token 间隔。",
  tpsDistribution: "按模型厂商比较生成吞吐量。数值越高越快；每个厂商一个箱体，展示最小值、P25、中位数、P75 和最大值。",
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
    ucid: filterUcid.value.trim() || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to,
    page: page.value,
    size: pageSize.value
  }]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.listInternalModelCallRecords({
    providerId: filterProviderId.value || null,
    outcomeGroup: filterOutcomeGroup.value || null,
    source: filterSource.value || null,
    ucid: filterUcid.value.trim() || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to,
    page: page.value,
    size: pageSize.value
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

/** 箱线图按当前统计结果中的 Provider 分组；选中单一 Provider 时仍保留同一查询路径。 */
const distributionProviderIds = computed(() => {
  if (filterProviderId.value) return [filterProviderId.value];
  const providerIds = new Set<string>();
  for (const row of statsQuery.data.value ?? []) {
    if (!filterOutcomeGroup.value || outcomeGroupOf(row.outcome) === filterOutcomeGroup.value) {
      providerIds.add(row.providerId);
    }
  }
  return [...providerIds].sort();
});

const ttftDistributionQueries = useQueries({
  queries: computed(() => distributionProviderIds.value.map((providerId) => ({
    queryKey: ["internal-model-observability-ttft-distribution", {
      providerId,
      outcomeGroup: filterOutcomeGroup.value || null,
      source: filterSource.value || null,
      from: queryWindow.value.from,
      to: queryWindow.value.to
    }],
    enabled: () => hasSuperAdmin.value,
    retry: false,
    queryFn: () => api.getInternalModelTtftDistribution({
      providerId,
      outcomeGroup: filterOutcomeGroup.value || null,
      source: filterSource.value || null,
      from: queryWindow.value.from,
      to: queryWindow.value.to
    })
  })))
});

const itlDistributionQueries = useQueries({
  queries: computed(() => distributionProviderIds.value.map((providerId) => ({
    queryKey: ["internal-model-observability-itl-distribution", {
      providerId,
      outcomeGroup: filterOutcomeGroup.value || null,
      source: filterSource.value || null,
      from: queryWindow.value.from,
      to: queryWindow.value.to
    }],
    enabled: () => hasSuperAdmin.value,
    retry: false,
    queryFn: () => api.getInternalModelItlDistribution({
      providerId,
      outcomeGroup: filterOutcomeGroup.value || null,
      source: filterSource.value || null,
      from: queryWindow.value.from,
      to: queryWindow.value.to
    })
  })))
});

const tpsDistributionQueries = useQueries({
  queries: computed(() => distributionProviderIds.value.map((providerId) => ({
    queryKey: ["internal-model-observability-tps-distribution", {
      providerId,
      outcomeGroup: filterOutcomeGroup.value || null,
      source: filterSource.value || null,
      from: queryWindow.value.from,
      to: queryWindow.value.to
    }],
    enabled: () => hasSuperAdmin.value,
    retry: false,
    queryFn: () => api.getInternalModelTpsDistribution({
      providerId,
      outcomeGroup: filterOutcomeGroup.value || null,
      source: filterSource.value || null,
      from: queryWindow.value.from,
      to: queryWindow.value.to
    })
  })))
});

/** Overview 使用全范围分布接口的准确均值与最大值，不能从当前页明细推算。 */
const itlOverviewQuery = useQuery({
  queryKey: computed(() => ["internal-model-observability-itl-overview", {
    providerId: filterProviderId.value || null,
    outcomeGroup: filterOutcomeGroup.value || null,
    source: filterSource.value || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to
  }]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelItlDistribution({
    providerId: filterProviderId.value || null,
    outcomeGroup: filterOutcomeGroup.value || null,
    source: filterSource.value || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to
  })
});

const tpsOverviewQuery = useQuery({
  queryKey: computed(() => ["internal-model-observability-tps-overview", {
    providerId: filterProviderId.value || null,
    outcomeGroup: filterOutcomeGroup.value || null,
    source: filterSource.value || null,
    from: queryWindow.value.from,
    to: queryWindow.value.to
  }]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelTpsDistribution({
    providerId: filterProviderId.value || null,
    outcomeGroup: filterOutcomeGroup.value || null,
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

/** 预设窗口刷新时推进到最新时刻；自定义窗口保持用户选择的固定起止时间。 */
async function refreshAll() {
  if (!isCustomTime.value) {
    queryWindow.value = createQueryWindow();
  }
  await Promise.all([
    recordsQuery.refetch(),
    statsQuery.refetch(),
    itlOverviewQuery.refetch(),
    tpsOverviewQuery.refetch(),
    probeStatusQuery.refetch()
  ]);
  await nextTick();
  await Promise.all([
    ...ttftDistributionQueries.value.map((query) => query.refetch()),
    ...itlDistributionQueries.value.map((query) => query.refetch()),
    ...tpsDistributionQueries.value.map((query) => query.refetch())
  ]);
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
const records = computed(() => recordsQuery.data.value?.items ?? []);
// 分页总数必须使用服务端对完整结果集的计数，当前页最多只有 pageSize 条，不能据此判断总页数。
const recordsTotal = computed(() => recordsQuery.data.value?.total ?? 0);
// stats API 返回底层 outcome；页面按大类筛选，明细 API 使用相同大类，保证两块口径一致。
const stats = computed(() => (statsQuery.data.value ?? []).filter((row) =>
  !filterOutcomeGroup.value || outcomeGroupOf(row.outcome) === filterOutcomeGroup.value
));

/** 只有后端返回完整、单调的五数概括时才绘图，并在进入图表前换算成目标单位。 */
function toBoxValues(
  distribution: InternalModelLatencyDistribution,
  divisor: number
): [number, number, number, number, number] | null {
  const values = [
    distribution.minimumMillis,
    distribution.firstQuartileMillis,
    distribution.medianMillis,
    distribution.thirdQuartileMillis,
    distribution.maximumMillis
  ];
  if (distribution.sampleCount <= 0 || !values.every((value) => typeof value === "number" && Number.isFinite(value))) {
    return null;
  }
  const [minimum, firstQuartile, median, thirdQuartile, maximum] = values as [number, number, number, number, number];
  if (minimum < 0 || minimum > firstQuartile || firstQuartile > median || median > thirdQuartile || thirdQuartile > maximum) {
    return null;
  }
  return [minimum, firstQuartile, median, thirdQuartile, maximum]
    .map((millis) => millis / divisor) as [number, number, number, number, number];
}

type ProviderDistributionBox = {
  providerId: string;
  sampleCount: number;
  values: [number, number, number, number, number];
};

const ttftBoxData = computed<ProviderDistributionBox[]>(() => distributionProviderIds.value.flatMap((providerId, index) => {
  const distribution = ttftDistributionQueries.value[index]?.data;
  const values = distribution ? toBoxValues(distribution, MILLISECONDS_PER_SECOND) : null;
  return distribution && values ? [{ providerId, sampleCount: distribution.sampleCount, values }] : [];
}));

const itlBoxData = computed<ProviderDistributionBox[]>(() => distributionProviderIds.value.flatMap((providerId, index) => {
  const distribution = itlDistributionQueries.value[index]?.data;
  const values = distribution ? toBoxValues(distribution, 1) : null;
  return distribution && values ? [{ providerId, sampleCount: distribution.sampleCount, values }] : [];
}));

/** TPS 字段已是 tokens/s，只校验五数概括完整、非负且单调，不再做单位换算。 */
function toThroughputBoxValues(
  distribution: InternalModelThroughputDistribution
): [number, number, number, number, number] | null {
  const values = [
    distribution.minimumTokensPerSecond,
    distribution.firstQuartileTokensPerSecond,
    distribution.medianTokensPerSecond,
    distribution.thirdQuartileTokensPerSecond,
    distribution.maximumTokensPerSecond
  ];
  if (distribution.sampleCount <= 0 || !values.every((value) => typeof value === "number" && Number.isFinite(value))) {
    return null;
  }
  const [minimum, firstQuartile, median, thirdQuartile, maximum] = values as [number, number, number, number, number];
  if (minimum < 0 || minimum > firstQuartile || firstQuartile > median || median > thirdQuartile || thirdQuartile > maximum) {
    return null;
  }
  return [minimum, firstQuartile, median, thirdQuartile, maximum];
}

const tpsBoxData = computed<ProviderDistributionBox[]>(() => distributionProviderIds.value.flatMap((providerId, index) => {
  const distribution = tpsDistributionQueries.value[index]?.data;
  const values = distribution ? toThroughputBoxValues(distribution) : null;
  return distribution && values ? [{ providerId, sampleCount: distribution.sampleCount, values }] : [];
}));

type PublicPerformanceBenchmark = {
  modelName: string;
  matches: (value: string) => boolean;
  testedOutputTps: number;
  peerMedianOutputTps: number;
  testedTtftMillis: number;
  peerMedianTtftMillis: number;
  sourceUrl: string;
};

const PUBLIC_BENCHMARK_CHECKED_AT = "2026-08-17";
const PUBLIC_BENCHMARK_METHODOLOGY_URL = "https://artificialanalysis.ai/methodology/";

// 公开数据会随测试方法和版本变化，固定标注核验日期，且不把公开同类中位数包装成行业标准。
const publicPerformanceBenchmarks: PublicPerformanceBenchmark[] = [
  {
    modelName: "Qwen3.6 27B Reasoning",
    matches: (value) => value.includes("qwen36") && value.includes("27b"),
    testedOutputTps: 59.7,
    peerMedianOutputTps: 107.1,
    testedTtftMillis: 3670,
    peerMedianTtftMillis: 2010,
    sourceUrl: "https://artificialanalysis.ai/models/qwen3-6-27b"
  },
  {
    modelName: "DeepSeek V4 Flash Reasoning Max",
    matches: (value) => value.includes("deepseekv4flash"),
    testedOutputTps: 118.2,
    peerMedianOutputTps: 67.4,
    testedTtftMillis: 1250,
    peerMedianTtftMillis: 1780,
    sourceUrl: "https://artificialanalysis.ai/models/deepseek-v4-flash/"
  }
];

const benchmarkDialogSource = ref<PublicPerformanceBenchmark | null>(null);
const benchmarkDialogTitle = computed(() => benchmarkDialogSource.value
  ? `${benchmarkDialogSource.value.modelName} · 公开数据源离线快照`
  : "公开性能参考 · 离线方法说明");

/** 企业内网无法访问外部站点时，直接在本地弹窗展示已固化的方法说明。 */
function openBenchmarkMethodology() {
  benchmarkDialogSource.value = null;
  showBenchmarkDialog.value = true;
}

/** 数据源按钮打开指定模型的本地快照，外网原文只保留为可选追溯入口。 */
function openBenchmarkSource(benchmark: PublicPerformanceBenchmark) {
  benchmarkDialogSource.value = benchmark;
  showBenchmarkDialog.value = true;
}

function normalizeModelName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "");
}

const benchmarkComparisons = computed(() => publicPerformanceBenchmarks.map((benchmark) => {
  const matchedStat = (statsQuery.data.value ?? []).find((row) =>
    benchmark.matches(normalizeModelName(row.model ?? ""))
      || benchmark.matches(normalizeModelName(row.providerId))
  );
  const providerId = matchedStat?.providerId ?? null;
  const tpsBox = providerId ? tpsBoxData.value.find((box) => box.providerId === providerId) : null;
  const ttftBox = providerId ? ttftBoxData.value.find((box) => box.providerId === providerId) : null;
  return {
    ...benchmark,
    providerId,
    deployedModel: matchedStat?.model ?? null,
    internalMedianOutputTps: tpsBox?.values[2] ?? null,
    internalMedianTtftSeconds: ttftBox?.values[2] ?? null
  };
}));

const ttftDistributionLoading = computed(() =>
  statsQuery.isLoading.value || ttftDistributionQueries.value.some((query) => query.isPending)
);
const itlDistributionLoading = computed(() =>
  statsQuery.isLoading.value || itlDistributionQueries.value.some((query) => query.isPending)
);
const tpsDistributionLoading = computed(() =>
  statsQuery.isLoading.value || tpsDistributionQueries.value.some((query) => query.isPending)
);
const ttftDistributionError = computed(() => ttftDistributionQueries.value.some((query) => query.isError));
const itlDistributionError = computed(() => itlDistributionQueries.value.some((query) => query.isError));
const tpsDistributionError = computed(() => tpsDistributionQueries.value.some((query) => query.isError));

const observabilityFetching = computed(() =>
  recordsQuery.isFetching.value
    || statsQuery.isFetching.value
    || itlOverviewQuery.isFetching.value
    || tpsOverviewQuery.isFetching.value
    || ttftDistributionQueries.value.some((query) => query.isFetching)
    || itlDistributionQueries.value.some((query) => query.isFetching)
    || tpsDistributionQueries.value.some((query) => query.isFetching)
);

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

/** 全局总览指标：小时聚合负责请求类指标，明细分布查询负责可靠的 ITL 与 TPS。 */
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
  const itlDistribution = itlOverviewQuery.data.value;
  const tpsDistribution = tpsOverviewQuery.data.value;
  return {
    totalRequests, successCount, failureCount, successRate, failureRate,
    avgDuration, maxDuration,
    firstTokenAvg, firstTokenMax: firstTokenCount > 0 ? firstTokenMillisMax : null,
    firstTokenCount,
    streamCompleteAvg,
    streamCompleteMax: streamCompleteCount > 0 ? streamCompleteMillisMax : null,
    streamCompleteCount,
    interTokenLatencyAvg: itlDistribution?.averageMillis ?? null,
    interTokenLatencyMax: itlDistribution?.maximumMillis ?? null,
    interTokenLatencyCount: itlDistribution?.sampleCount ?? 0,
    outputTpsAvg: tpsDistribution?.averageTokensPerSecond ?? null,
    outputTpsMedian: tpsDistribution?.medianTokensPerSecond ?? null,
    outputTpsCount: tpsDistribution?.sampleCount ?? 0,
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
const ttftChartEl = ref<HTMLDivElement | null>(null);
const itlChartEl = ref<HTMLDivElement | null>(null);
const tpsChartEl = ref<HTMLDivElement | null>(null);
const pieChartEl = ref<HTMLDivElement | null>(null);
const failureChartEl = ref<HTMLDivElement | null>(null);
const providerChartEl = ref<HTMLDivElement | null>(null);
let trendChart: echarts.ECharts | null = null;
let ttftChart: echarts.ECharts | null = null;
let itlChart: echarts.ECharts | null = null;
let tpsChart: echarts.ECharts | null = null;
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

/** 一个供应商绘制一个箱体，避免不同厂商的性能分布被合并后掩盖差异。 */
function renderDistributionBoxes(
  el: HTMLDivElement | null,
  chart: echarts.ECharts | null,
  boxes: ProviderDistributionBox[],
  label: string,
  unit: "s" | "ms" | "tokens/s",
  color: string,
  background: string
): echarts.ECharts | null {
  if (!boxes.length || !el || el.clientWidth <= 0) {
    chart?.dispose();
    return null;
  }
  const instance = ensureChart(el, { current: chart });
  instance?.setOption({
    animation: false,
    tooltip: {
      trigger: "item",
      formatter: (params: { dataIndex?: number }) => {
        const box = boxes[params.dataIndex ?? -1];
        if (!box) return "";
        const [minimum, firstQuartile, median, thirdQuartile, maximum] = box.values;
        return [
          `<strong>${escapeTooltipHtml(box.providerId)} · ${label}（${box.sampleCount} 次）</strong>`,
          `最小值：${formatDistributionValue(minimum, unit)}`,
          `P25：${formatDistributionValue(firstQuartile, unit)}`,
          `中位数：${formatDistributionValue(median, unit)}`,
          `P75：${formatDistributionValue(thirdQuartile, unit)}`,
          `最大值：${formatDistributionValue(maximum, unit)}`
        ].join("<br/>");
      }
    },
    grid: { top: 18, left: 82, right: 30, bottom: boxes.length > 3 ? 72 : 48 },
    xAxis: {
      type: "category",
      data: boxes.map((box) => box.providerId),
      axisTick: { show: false },
      axisLabel: { interval: 0, rotate: boxes.length > 3 ? 20 : 0 }
    },
    yAxis: {
      type: "value",
      name: `${label} (${unit})`,
      min: 0,
      scale: true,
      axisLabel: { formatter: (value: number) => formatDistributionValue(value, unit) }
    },
    series: [{
      name: label,
      type: "boxplot",
      layout: "vertical",
      boxWidth: [40, 90],
      data: boxes.map((box) => box.values),
      itemStyle: { color: background, borderColor: color, borderWidth: 2 }
    }]
  }, true);
  return instance;
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
  ttftChart = renderDistributionBoxes(
    ttftChartEl.value, ttftChart, ttftBoxData.value, "TTFT", "s", "#2563eb", "#dbeafe"
  );
  itlChart = renderDistributionBoxes(
    itlChartEl.value, itlChart, itlBoxData.value, "ITL / TPOT", "ms", "#7c3aed", "#ede9fe"
  );
  tpsChart = renderDistributionBoxes(
    tpsChartEl.value, tpsChart, tpsBoxData.value, "Output TPS", "tokens/s", "#ea580c", "#ffedd5"
  );
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
  ttftChart?.resize();
  itlChart?.resize();
  tpsChart?.resize();
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
  [trendChart, ttftChart, itlChart, tpsChart, pieChart, failureChart, providerChart]
    .forEach((chart) => chart?.dispose());
  trendChart = null;
  ttftChart = null;
  itlChart = null;
  tpsChart = null;
  pieChart = null;
  failureChart = null;
  providerChart = null;
});

// 数据变动后 post-flush 触发重新渲染，确保在新 DOM 或过滤数据更新后重绘图表
watch([
  () => stats.value,
  () => ttftBoxData.value,
  () => itlBoxData.value,
  () => tpsBoxData.value,
  filterProviderId,
  filterOutcomeGroup,
  filterSource
], () => {
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

/** E2E、TTFT、SCT 使用秒展示；接口保留毫秒字段，集中在此处完成兼容换算。 */
function formatDuration(millis: number | null | undefined): string {
  if (millis === null || millis === undefined || !Number.isFinite(millis) || millis < 0) return "-";
  return formatSeconds(millis / MILLISECONDS_PER_SECOND);
}

/** 秒值最多保留毫秒级精度；极短但非零的值不会显示成 0。 */
function formatSeconds(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds) || seconds < 0) return "-";
  const precision = seconds > 0 && seconds < 0.001 ? 6 : 3;
  const rounded = Number(seconds.toFixed(precision));
  if (seconds > 0 && rounded === 0) return "<0.000001s";
  return `${rounded}s`;
}

/** ITL / TPOT 使用毫秒展示，保留小数以免丢失单次 Token 间隔精度。 */
function formatMilliseconds(millis: number | null | undefined): string {
  if (millis === null || millis === undefined || !Number.isFinite(millis) || millis < 0) return "-";
  const precision = millis > 0 && millis < 0.001 ? 6 : 3;
  const rounded = Number(millis.toFixed(precision));
  if (millis > 0 && rounded === 0) return "<0.000001ms";
  return `${rounded}ms`;
}

function formatDistributionValue(
  value: number | null | undefined,
  unit: "s" | "ms" | "tokens/s"
): string {
  if (unit === "tokens/s") return formatTps(value);
  return unit === "s" ? formatSeconds(value) : formatMilliseconds(value);
}

/** ECharts tooltip 使用 HTML 渲染，供应商标识只作为文本展示。 */
function escapeTooltipHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

/** 只按业界口径计算可靠样本：首末输出间隔除以后续 Token 数。 */
function interTokenLatencyMillis(row: {
  firstTokenMillis?: number | null;
  lastTokenMillis?: number | null;
  outputTokenCount?: number | null;
}): number | null {
  const first = row.firstTokenMillis;
  const last = row.lastTokenMillis;
  const count = row.outputTokenCount;
  if (typeof first !== "number"
    || typeof last !== "number"
    || typeof count !== "number"
    || !Number.isFinite(first)
    || !Number.isFinite(last)
    || !Number.isInteger(count)
    || count < 2
    || last < first) {
    return null;
  }
  return (last - first) / (count - 1);
}

/** 单条明细与服务端分布使用同一公式；零时间跨度不展示无穷大 TPS。 */
function outputTokensPerSecond(row: {
  firstTokenMillis?: number | null;
  lastTokenMillis?: number | null;
  outputTokenCount?: number | null;
}): number | null {
  const latencyMillis = interTokenLatencyMillis(row);
  return latencyMillis !== null && latencyMillis > 0 ? MILLISECONDS_PER_SECOND / latencyMillis : null;
}

function formatTps(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value) || value < 0) return "-";
  return `${Number(value.toFixed(value < 10 ? 2 : 1))} tokens/s`;
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

function onPageSizeChange(next: number) {
  pageSize.value = next;
  page.value = 1;
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
        <span class="ta-imob-sub">默认查看{{ isCustomTime ? "当前自定义" : `当前 ${selectedWindowHours} 小时` }}时间段的用户调用，统计截至本次加载或刷新时刻。按用户只过滤调用明细及其分页总量；聚合指标和图表使用当前供应商、结果、来源与时间范围的全量记录，不受明细分页影响。E2E、TTFT、SCT 使用秒（s），ITL / TPOT 使用毫秒（ms），Output TPS 使用 tokens/s，英文缩写见页首对照指南。</span>

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
            >
              <div class="ta-imob-glossary-item-top">
                <code class="ta-imob-glossary-abbr">{{ item.abbr }}</code>
              </div>
              <span class="ta-imob-glossary-name">{{ item.name }}</span>
              <span class="ta-imob-glossary-desc">{{ item.desc }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="ta-imob-combined">
        <div class="ta-imob-sticky-bar">
          <div class="ta-imob-filter-label">
            <Filter :size="13" />
            <span>筛选</span>
          </div>

          <!-- 统一融合时间选择器 (Unified Time Picker) -->
          <el-popover
            v-model:visible="timePopoverVisible"
            placement="bottom-start"
            :width="300"
            trigger="click"
            popper-class="ta-imob-time-popover"
          >
            <template #reference>
              <button
                type="button"
                class="ta-imob-time-picker-btn"
                :class="{ 'is-custom': isCustomTime }"
                aria-label="选择统计时间范围"
                :aria-expanded="timePopoverVisible"
              >
                <Clock :size="12" />
                <span class="ta-imob-time-btn-text">{{ timeDisplayLabel }}</span>
                <ChevronDown :size="11" />
              </button>
            </template>

            <div class="ta-imob-time-popover-panel" @click.stop>
              <div class="ta-imob-time-section-head">快捷时间窗口</div>
              <div class="ta-imob-time-presets">
                <button
                  v-for="opt in windowHourOptions"
                  :key="opt.value"
                  type="button"
                  class="ta-imob-preset-chip"
                  :class="{ 'is-active': !isCustomTime && selectedWindowHours === opt.value }"
                  @click="selectPreset(opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>

              <div class="ta-imob-time-divider" />

              <div class="ta-imob-time-section-head">自定义起止时间段</div>
              <el-date-picker
                v-model="customTimeRange"
                type="datetimerange"
                :teleported="false"
                size="small"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
                @change="onCustomTimeChange"
              />
            </div>
          </el-popover>

          <el-select
            v-model="filterUcid"
            placeholder="按用户"
            size="small"
            clearable
            filterable
            allow-create
            default-first-option
            class="ta-imob-filter-select-user"
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
            size="small"
            clearable
            class="ta-imob-filter-select-provider"
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
            size="small"
            clearable
            class="ta-imob-filter-select-outcome"
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
            size="small"
            clearable
            class="ta-imob-filter-select-source"
            @change="applyFilters"
          >
            <el-option label="用户调用" value="USER_CALL" />
            <el-option label="探活" value="PROBE" />
          </el-select>

          <div class="ta-imob-filter-vdivider" />

          <div class="ta-imob-filter-actions">
            <button
              type="button"
              class="ta-imob-btn-small"
              :disabled="observabilityFetching"
              @click="refreshAll()"
            >
              <RefreshCw
                :size="11"
                :class="{ 'ta-imob-spin': observabilityFetching }"
              />
              <span>刷新</span>
            </button>
            <button
              type="button"
              class="ta-imob-btn-small"
              :disabled="probeMutation.isPending.value"
              @click="probeAll()"
            >
              <Activity class="ta-imob-probe-icon" :size="11" />
              <span>探活</span>
            </button>
          </div>
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
            <el-table-column label="ITL / TPOT" min-width="190">
              <template #header>
                <MetricHelpLabel label="ITL / TPOT" :description="metricHelp.interTokenLatency" />
              </template>
              <template #default="{ row }">{{ formatMilliseconds(interTokenLatencyMillis(row)) }}</template>
            </el-table-column>
            <el-table-column label="Output TPS" min-width="190">
              <template #header>
                <MetricHelpLabel label="Output TPS" :description="metricHelp.outputTps" />
              </template>
              <template #default="{ row }">{{ formatTps(outputTokensPerSecond(row)) }}</template>
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
              :page-sizes="pageSizeOptions"
              :total="recordsTotal"
              layout="sizes, prev, pager, next, total"
              @current-change="onPageChange"
              @size-change="onPageSizeChange"
            />
          </div>
        </section>

        <section class="ta-imob-section ta-imob-metrics-section">
          <h4 class="ta-imob-section-title">聚合指标</h4>
          <div v-loading="statsQuery.isLoading.value || itlOverviewQuery.isLoading.value || tpsOverviewQuery.isLoading.value" class="ta-imob-stats">
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
                  <span class="ta-imob-overview-value">{{ formatMilliseconds(overallMetrics.interTokenLatencyAvg) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Avg ITL / TPOT" :description="metricHelp.avgInterTokenLatency" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatMilliseconds(overallMetrics.interTokenLatencyMax) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Max ITL / TPOT" :description="metricHelp.maxInterTokenLatency" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatTps(overallMetrics.outputTpsAvg) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="Avg Output TPS" :description="metricHelp.avgOutputTps" />
                </div>
                <div class="ta-imob-overview-cell">
                  <span class="ta-imob-overview-value">{{ formatTps(overallMetrics.outputTpsMedian) }}</span>
                  <MetricHelpLabel class="ta-imob-overview-label" label="P50 Output TPS" :description="metricHelp.medianOutputTps" />
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

            <div class="ta-imob-benchmark">
              <div class="ta-imob-benchmark-heading">
                <div>
                  <h4 class="ta-imob-overview-title">公开性能参考（方向性对标）</h4>
                  <p>公开同类中位数不是行业标准。公开测试会做 Token 口径归一化，本页内部数据使用供应商原生 Token，并受硬件、量化和并发影响，仅用于定位差距。</p>
                </div>
                <button type="button" class="ta-imob-benchmark-link" @click="openBenchmarkMethodology">
                  <BookOpen :size="12" /> 方法说明（离线）
                </button>
              </div>
              <div class="ta-imob-benchmark-grid">
                <article v-for="benchmark in benchmarkComparisons" :key="benchmark.modelName" class="ta-imob-benchmark-card">
                  <div class="ta-imob-benchmark-model">{{ benchmark.modelName }}</div>
                  <div class="ta-imob-benchmark-deployment">
                    当前部署：{{ benchmark.providerId ? `${benchmark.providerId} / ${benchmark.deployedModel}` : "当前范围未识别到对应模型" }}
                  </div>
                  <div class="ta-imob-benchmark-values">
                    <span>内部 P50 TPS <strong>{{ formatTps(benchmark.internalMedianOutputTps) }}</strong></span>
                    <span>公开实测 TPS <strong>{{ formatTps(benchmark.testedOutputTps) }}</strong></span>
                    <span>同类中位 TPS <strong>{{ formatTps(benchmark.peerMedianOutputTps) }}</strong></span>
                    <span>内部 P50 TTFT <strong>{{ formatSeconds(benchmark.internalMedianTtftSeconds) }}</strong></span>
                    <span>公开实测 TTFT <strong>{{ formatDuration(benchmark.testedTtftMillis) }}</strong></span>
                    <span>同类中位 TTFT <strong>{{ formatDuration(benchmark.peerMedianTtftMillis) }}</strong></span>
                  </div>
                  <button type="button" class="ta-imob-benchmark-link" @click="openBenchmarkSource(benchmark)">
                    <FileText :size="11" /> 数据源（离线快照，核验于 {{ PUBLIC_BENCHMARK_CHECKED_AT }}）
                  </button>
                </article>
              </div>
            </div>

            <!-- 图表：趋势 / 调用结果与供应商对比 / TTFT 分布 / 失败分类 -->
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

              <!-- 两张业务图独占整行并增高；性能箱线图继续纵向排列，保证标题和坐标轴可读。 -->
              <div class="ta-imob-chart-comparison">
                <div class="ta-imob-chart-stack ta-imob-business-chart-stack">
                  <div v-if="showRateMetrics" class="ta-imob-chart-card">
                    <h4 class="ta-imob-overview-title">
                      <MetricHelpLabel label="调用结果分布" :description="chartHelp.successComposition" />
                    </h4>
                    <div ref="pieChartEl" class="ta-imob-chart" />
                  </div>
                  <div v-if="providerBarData.length" class="ta-imob-chart-card">
                    <h4 class="ta-imob-overview-title">
                      <MetricHelpLabel label="供应商请求量对比" :description="chartHelp.providerVolume" />
                    </h4>
                    <div ref="providerChartEl" class="ta-imob-chart" />
                  </div>
                </div>

                <div class="ta-imob-latency-box-stack">
                  <div v-loading="ttftDistributionLoading" class="ta-imob-chart-card ta-imob-box-card">
                    <div class="ta-imob-box-title-row">
                      <h4 class="ta-imob-overview-title">
                        <MetricHelpLabel label="TTFT 厂商对比（箱线图）" :description="chartHelp.ttftDistribution" />
                      </h4>
                      <div v-if="ttftBoxData.length" class="ta-imob-box-summary">
                        <div v-for="box in ttftBoxData" :key="box.providerId" class="ta-imob-box-summary-item">
                          <strong>{{ box.providerId }}</strong>
                          <span>中间 50%：{{ formatSeconds(box.values[1]) }}–{{ formatSeconds(box.values[3]) }}</span>
                          <span>中位数：{{ formatSeconds(box.values[2]) }}</span>
                          <span>样本：{{ box.sampleCount }} 次</span>
                        </div>
                      </div>
                    </div>
                    <div v-if="ttftDistributionError && !ttftBoxData.length" class="ta-imob-chart-empty">
                      TTFT 分布加载失败，请刷新重试
                    </div>
                    <div v-else-if="ttftBoxData.length" ref="ttftChartEl" class="ta-imob-chart ta-imob-chart-box" />
                    <div v-else-if="!ttftDistributionLoading" class="ta-imob-chart-empty">
                      当前筛选范围没有可用于统计的 TTFT
                    </div>
                  </div>

                  <div v-loading="itlDistributionLoading" class="ta-imob-chart-card ta-imob-box-card">
                    <div class="ta-imob-box-title-row">
                      <h4 class="ta-imob-overview-title">
                        <MetricHelpLabel label="ITL / TPOT 厂商对比（箱线图）" :description="chartHelp.itlDistribution" />
                      </h4>
                      <div v-if="itlBoxData.length" class="ta-imob-box-summary">
                        <div v-for="box in itlBoxData" :key="box.providerId" class="ta-imob-box-summary-item">
                          <strong>{{ box.providerId }}</strong>
                          <span>中间 50%：{{ formatMilliseconds(box.values[1]) }}–{{ formatMilliseconds(box.values[3]) }}</span>
                          <span>中位数：{{ formatMilliseconds(box.values[2]) }}</span>
                          <span>样本：{{ box.sampleCount }} 次</span>
                        </div>
                      </div>
                    </div>
                    <div v-if="itlDistributionError && !itlBoxData.length" class="ta-imob-chart-empty">
                      ITL / TPOT 分布加载失败，请刷新重试
                    </div>
                    <div v-else-if="itlBoxData.length" ref="itlChartEl" class="ta-imob-chart ta-imob-chart-box" />
                    <div v-else-if="!itlDistributionLoading" class="ta-imob-chart-empty">
                      当前范围没有可靠的 ITL / TPOT 样本
                    </div>
                  </div>

                  <div v-loading="tpsDistributionLoading" class="ta-imob-chart-card ta-imob-box-card">
                    <div class="ta-imob-box-title-row">
                      <h4 class="ta-imob-overview-title">
                        <MetricHelpLabel label="Output TPS 厂商对比（箱线图）" :description="chartHelp.tpsDistribution" />
                      </h4>
                      <div v-if="tpsBoxData.length" class="ta-imob-box-summary">
                        <div v-for="box in tpsBoxData" :key="box.providerId" class="ta-imob-box-summary-item">
                          <strong>{{ box.providerId }}</strong>
                          <span>中间 50%：{{ formatTps(box.values[1]) }}–{{ formatTps(box.values[3]) }}</span>
                          <span>中位数：{{ formatTps(box.values[2]) }}</span>
                          <span>样本：{{ box.sampleCount }} 次</span>
                        </div>
                      </div>
                    </div>
                    <div v-if="tpsDistributionError && !tpsBoxData.length" class="ta-imob-chart-empty">
                      Output TPS 分布加载失败，请刷新重试
                    </div>
                    <div v-else-if="tpsBoxData.length" ref="tpsChartEl" class="ta-imob-chart ta-imob-chart-box" />
                    <div v-else-if="!tpsDistributionLoading" class="ta-imob-chart-empty">
                      当前范围没有可靠的 Output TPS 样本
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="failureBarData.length" class="ta-imob-chart-card ta-imob-chart-card-full">
                <h4 class="ta-imob-overview-title">
                  <MetricHelpLabel label="失败原因分类" :description="chartHelp.failureBreakdown" />
                </h4>
                <div ref="failureChartEl" class="ta-imob-chart" />
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

      <!-- 公开对标数据固化为离线弹窗，企业内网无需访问 Artificial Analysis 即可查看。 -->
      <el-dialog
        v-model="showBenchmarkDialog"
        :title="benchmarkDialogTitle"
        width="760px"
        append-to-body
      >
        <div class="ta-imob-doc-content ta-imob-benchmark-dialog">
          <p class="ta-imob-doc-lead">
            以下说明与数值已固化在当前页面，无需访问外网。公开数据只作方向性对标，不代表统一行业标准。
          </p>

          <template v-if="benchmarkDialogSource">
            <table class="ta-imob-doc-table">
              <tbody>
                <tr><th>模型</th><td>{{ benchmarkDialogSource.modelName }}</td></tr>
                <tr><th>公开实测 Output TPS</th><td>{{ formatTps(benchmarkDialogSource.testedOutputTps) }}</td></tr>
                <tr><th>公开同类中位 Output TPS</th><td>{{ formatTps(benchmarkDialogSource.peerMedianOutputTps) }}</td></tr>
                <tr><th>公开实测 TTFT</th><td>{{ formatDuration(benchmarkDialogSource.testedTtftMillis) }}</td></tr>
                <tr><th>公开同类中位 TTFT</th><td>{{ formatDuration(benchmarkDialogSource.peerMedianTtftMillis) }}</td></tr>
                <tr><th>核验日期</th><td>{{ PUBLIC_BENCHMARK_CHECKED_AT }}</td></tr>
              </tbody>
            </table>
            <p>
              数据来源：Artificial Analysis 对应模型公开页面。外网可用时可追溯
              <a :href="benchmarkDialogSource.sourceUrl" target="_blank" rel="noopener noreferrer" class="ta-imob-external-link">
                原始模型页面 <ExternalLink :size="11" />
              </a>；企业内网验收以本弹窗快照为准。
            </p>
          </template>

          <template v-else>
            <h4>对标方法</h4>
            <ul class="ta-imob-benchmark-method-list">
              <li>公开实测值与同类中位数取自 Artificial Analysis 在核验日期展示的模型页面。</li>
              <li>Output TPS 表示首个输出 Token 之后的持续生成速度；TTFT 表示等待首个输出 Token 的时间。</li>
              <li>公开平台会统一测试口径；本页内部值使用企业供应商返回的原生 Token 数，不能当作同硬件、同量化、同并发条件下的严格排名。</li>
              <li>判断优化效果时，应优先比较同一内部模型、同一环境、同一负载下的历史趋势。</li>
            </ul>
            <h4>当前固化快照</h4>
            <table class="ta-imob-doc-table">
              <thead>
                <tr><th>模型</th><th>实测 TPS</th><th>同类中位 TPS</th><th>实测 TTFT</th><th>同类中位 TTFT</th></tr>
              </thead>
              <tbody>
                <tr v-for="benchmark in publicPerformanceBenchmarks" :key="benchmark.modelName">
                  <td>{{ benchmark.modelName }}</td>
                  <td>{{ formatTps(benchmark.testedOutputTps) }}</td>
                  <td>{{ formatTps(benchmark.peerMedianOutputTps) }}</td>
                  <td>{{ formatDuration(benchmark.testedTtftMillis) }}</td>
                  <td>{{ formatDuration(benchmark.peerMedianTtftMillis) }}</td>
                </tr>
              </tbody>
            </table>
            <p>
              外网可用时可追溯
              <a :href="PUBLIC_BENCHMARK_METHODOLOGY_URL" target="_blank" rel="noopener noreferrer" class="ta-imob-external-link">
                Artificial Analysis 方法原文 <ExternalLink :size="11" />
              </a>。
            </p>
          </template>
        </div>
      </el-dialog>

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
              href="https://docs.nvidia.com/aiperf/reference/ai-perf-metrics-reference"
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
              <tr>
                <td><code>ITL / TPOT</code></td>
                <td>Inter-Token Latency / Time Per Output Token</td>
                <td>Token 输出间隔 / 单 Token 耗时</td>
                <td>已统计，按毫秒展示（首末输出间隔 ÷ 后续 Token 数；至少 2 个输出 Token 且供应商返回准确用量）</td>
              </tr>
              <tr>
                <td><code>Output TPS</code></td>
                <td>Output Tokens Per Second</td>
                <td>每秒输出 Token 数</td>
                <td>已统计，按 tokens/s 展示（后续 Token 数 × 1000 ÷ 首末 Token 间隔；逐次计算后再聚合）</td>
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
            <span>官方参考链接：<a href="https://docs.nvidia.com/aiperf/reference/ai-perf-metrics-reference" target="_blank" rel="noopener noreferrer">NVIDIA AIPerf Metrics Reference ↗</a></span>
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
  gap: 16px;
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
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03);
}
.ta-imob-overview-title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}
.ta-imob-overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}
.ta-imob-overview-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 8px;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 6px;
  transition: all 0.15s ease;
}
.ta-imob-overview-cell:hover {
  background: #ffffff;
  border-color: #cbd5e1;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.ta-imob-overview-value {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
  white-space: nowrap;
}
.ta-imob-overview-value.is-ok {
  color: #16a34a;
}
.ta-imob-overview-value.is-bad {
  color: #dc2626;
}
.ta-imob-overview-label {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}
.ta-imob-benchmark {
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fffaf5;
  padding: 12px 16px;
}
.ta-imob-benchmark-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.ta-imob-benchmark-heading p {
  margin: -4px 0 10px;
  color: #78716c;
  font-size: 12px;
  line-height: 1.6;
}
.ta-imob-benchmark-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #c2410c;
  font-size: 12px;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
}
.ta-imob-benchmark-link:hover,
.ta-imob-benchmark-link:focus-visible {
  color: #9a3412;
  text-decoration: underline;
  outline: none;
}
.ta-imob-benchmark-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 10px;
}
.ta-imob-benchmark-card {
  border: 1px solid #ffedd5;
  border-radius: 7px;
  background: #fff;
  padding: 12px;
}
.ta-imob-benchmark-model {
  color: #9a3412;
  font-size: 13px;
  font-weight: 700;
}
.ta-imob-benchmark-deployment {
  margin: 3px 0 10px;
  color: #78716c;
  font-size: 11px;
}
.ta-imob-benchmark-values {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}
.ta-imob-benchmark-values span {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #78716c;
  font-size: 11px;
}
.ta-imob-benchmark-values strong {
  color: #292524;
  font-size: 13px;
}
.ta-imob-benchmark-dialog h4 {
  margin: 16px 0 8px;
  color: #292524;
  font-size: 14px;
}
.ta-imob-benchmark-method-list {
  margin: 0;
  padding-left: 20px;
  color: #57534e;
}
.ta-imob-benchmark-method-list li + li {
  margin-top: 6px;
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
.ta-imob-chart-comparison {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: stretch;
  gap: 16px;
}
@media (min-width: 1024px) {
  .ta-imob-chart-comparison {
    grid-template-columns: 380px minmax(0, 1fr);
  }
}
@media (min-width: 1400px) {
  .ta-imob-chart-comparison {
    grid-template-columns: 420px minmax(0, 1fr);
  }
}
.ta-imob-chart-stack {
  display: grid;
  grid-auto-rows: auto;
  gap: 12px;
  min-width: 0;
}
.ta-imob-latency-box-stack {
  display: grid;
  grid-auto-rows: minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
}
.ta-imob-chart-stack .ta-imob-chart-card,
.ta-imob-box-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.ta-imob-chart {
  width: 100%;
  height: 240px;
}
.ta-imob-chart-stack .ta-imob-chart {
  flex: none;
  height: 280px;
  min-height: 280px;
}
.ta-imob-chart-trend {
  height: 260px;
}
.ta-imob-chart-box {
  flex: 1;
  height: auto;
  min-height: 200px;
}
.ta-imob-box-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.ta-imob-box-summary {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 6px;
  color: #475569;
  font-size: 12px;
}
.ta-imob-box-summary-item {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px 12px;
}
.ta-imob-box-summary-item strong {
  color: #1e293b;
}
.ta-imob-chart-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 260px;
  color: #64748b;
  font-size: 13px;
}
@media (max-width: 960px) {
  .ta-imob-chart-stack .ta-imob-chart {
    height: 280px;
    min-height: 280px;
  }
  .ta-imob-chart-box {
    min-height: 240px;
  }
  .ta-imob-benchmark-heading {
    flex-direction: column;
  }
  .ta-imob-benchmark-values {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
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

/* 优化“按供应商”卡片布局：采用 4 列 / 3 列流式单元格结构 */
.ta-imob-metric-body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
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
.ta-imob-sticky-bar {
  position: sticky !important;
  top: 0 !important;
  z-index: 100 !important;
  display: flex !important;
  flex-wrap: nowrap !important;
  align-items: center !important;
  gap: 8px !important;
  padding: 8px 12px !important;
  margin: 0 !important;
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05) !important;
  overflow-x: auto !important;
  scrollbar-width: thin !important;
  flex-shrink: 0 !important;
}
.ta-imob-sticky-bar > * {
  flex-shrink: 0 !important;
}
.ta-imob-filter-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  padding-right: 2px;
}
.ta-imob-time-picker-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #ffffff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}
.ta-imob-time-picker-btn:hover {
  border-color: #c0c4cc;
  color: #303133;
}
.ta-imob-time-picker-btn.is-custom {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
}
.ta-imob-time-btn-text {
  font-weight: 500;
}
:deep(.ta-imob-filter-select-user) {
  width: 155px !important;
}
:deep(.ta-imob-filter-select-provider) {
  width: 145px !important;
}
:deep(.ta-imob-filter-select-outcome) {
  width: 145px !important;
}
:deep(.ta-imob-filter-select-source) {
  width: 125px !important;
}
.ta-imob-filter-vdivider {
  width: 1px;
  height: 14px;
  background: #cbd5e1;
  margin: 0 4px;
  flex-shrink: 0;
}
.ta-imob-filter-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.ta-imob-btn-small {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  background: #ffffff;
  color: #2563eb;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}
.ta-imob-btn-small:hover:not(:disabled) {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #1d4ed8;
}
.ta-imob-btn-small:disabled {
  opacity: 0.5;
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
.ta-imob-time-popover-panel {
  padding: 4px 2px;
}
.ta-imob-time-popover-panel :deep(.el-date-editor) {
  position: relative;
}
.ta-imob-time-popover-panel :deep(.el-picker__popper) {
  z-index: 1;
}
.ta-imob-time-section-head {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 6px;
}
.ta-imob-time-presets {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-bottom: 10px;
}
.ta-imob-preset-chip {
  padding: 4px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
  color: #475569;
  font-size: 11px;
  cursor: pointer;
  text-align: center;
  transition: all 0.15s ease;
}
.ta-imob-preset-chip:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  color: #1e293b;
}
.ta-imob-preset-chip.is-active {
  background: #0284c7;
  border-color: #0284c7;
  color: #ffffff;
  font-weight: 600;
}
.ta-imob-time-divider {
  height: 1px;
  background: #e2e8f0;
  margin: 10px 0;
}
</style>
