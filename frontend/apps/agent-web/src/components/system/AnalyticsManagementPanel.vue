<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { useQuery } from "@tanstack/vue-query";
import { Download, RefreshCw, Search } from "lucide-vue-next";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  AnalyticsCapabilities,
  AnalyticsExceptionDetail,
  AnalyticsFunnel,
  AnalyticsHeatmapMetric,
  AnalyticsHourlyHeatmap,
  AnalyticsOrganizationUsageRow,
  AnalyticsOverview,
  AnalyticsQueryParams,
  AnalyticsSatisfaction,
  AnalyticsSessionUsageRow,
  AnalyticsTimeSeriesPoint,
  AnalyticsTokenOperations,
  AnalyticsUserUsageRow,
  PageResponse
} from "@test-agent/shared-types";

const api = inject<BackendApiClient>("api")!;

type TabKey = "overview" | "users" | "sessions" | "token" | "capabilities" | "organizations" | "satisfaction" | "exceptions";
type RangePreset = "7" | "30" | "90" | "custom";
type CapabilityType = "ALL" | "AGENT" | "SKILL" | "TOOL";

const activeTab = ref<TabKey>("overview");
const now = new Date();
const startTime = ref(toLocalInput(new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)));
const endTime = ref(toLocalInput(now));
const rangePreset = ref<RangePreset>("30");
const granularity = ref<"hour" | "day" | "week" | "month">("day");
const organization = ref("");
const rdDepartment = ref("");
const department = ref("");
const userKeyword = ref("");
const heatmapMetric = ref<AnalyticsHeatmapMetric>("USER_MESSAGES");
const capabilityType = ref<CapabilityType>("ALL");

// 列表分页：明细类接口支持服务端分页，每页 20 条；
// 组织分析、Token 用户排行只有 topN 上限（最大 100），改为一次取满后在本地分页。
const SERVER_PAGE_SIZE = 20;
const RANK_FETCH_LIMIT = 100;
const usersPage = ref(1);
const sessionsPage = ref(1);
const feedbackPage = ref(1);
const exceptionsPage = ref(1);
const organizationPage = ref(1);
const tokenUserPage = ref(1);

const params = computed<AnalyticsQueryParams>(() => ({
  startTime: fromLocalInput(startTime.value),
  endTime: fromLocalInput(endTime.value),
  granularity: granularity.value,
  organization: organization.value || undefined,
  rdDepartment: rdDepartment.value || undefined,
  department: department.value || undefined,
  user: userKeyword.value.trim() || undefined,
  topN: RANK_FETCH_LIMIT,
  page: 1,
  pageSize: SERVER_PAGE_SIZE,
  sort: "active"
}));
// 服务端分页的三个明细列表各自持有页码，避免切换 Tab 时互相串页
const usersParams = computed<AnalyticsQueryParams>(() => ({ ...params.value, page: usersPage.value }));
const sessionsParams = computed<AnalyticsQueryParams>(() => ({ ...params.value, page: sessionsPage.value }));
const feedbackParams = computed<AnalyticsQueryParams>(() => ({ ...params.value, page: feedbackPage.value }));
const exceptionsParams = computed<AnalyticsQueryParams>(() => ({ ...params.value, page: exceptionsPage.value }));
const heatmapRangeSupported = computed(() => {
  const start = new Date(params.value.startTime ?? "").getTime();
  const end = new Date(params.value.endTime ?? "").getTime();
  return Number.isFinite(start) && Number.isFinite(end) && start < end
    && end - start <= 90 * 24 * 60 * 60 * 1000;
});

const overviewQuery = useQuery<AnalyticsOverview, Error>({
  queryKey: computed(() => ["analytics-overview", params.value]),
  retry: false,
  queryFn: () => api.getAnalyticsOverview(params.value)
});
const optionsQuery = useQuery({
  queryKey: computed(() => ["analytics-filter-options", params.value.organization, params.value.rdDepartment]),
  retry: false,
  queryFn: () => api.getAnalyticsFilterOptions(params.value)
});
const funnelQuery = useQuery<AnalyticsFunnel, Error>({
  queryKey: computed(() => ["analytics-funnel", params.value]),
  retry: false,
  queryFn: () => api.getAnalyticsFunnel(params.value)
});
const heatmapQuery = useQuery<AnalyticsHourlyHeatmap, Error>({
  queryKey: computed(() => ["analytics-hourly-heatmap", params.value, heatmapMetric.value]),
  enabled: () => activeTab.value === "overview" && heatmapRangeSupported.value,
  retry: false,
  queryFn: () => api.getAnalyticsHourlyHeatmap(params.value, heatmapMetric.value)
});
const timeseriesQuery = useQuery<AnalyticsTimeSeriesPoint[], Error>({
  queryKey: computed(() => ["analytics-timeseries", params.value]),
  retry: false,
  queryFn: () => api.getAnalyticsTimeseries(params.value)
});
const usersQuery = useQuery<PageResponse<AnalyticsUserUsageRow>, Error>({
  queryKey: computed(() => ["analytics-users", usersParams.value]),
  enabled: () => activeTab.value === "users",
  retry: false,
  queryFn: () => api.getAnalyticsUsers(usersParams.value)
});
const tokenQuery = useQuery<AnalyticsTokenOperations, Error>({
  queryKey: computed(() => ["analytics-token-operations", params.value]),
  enabled: () => activeTab.value === "token",
  retry: false,
  queryFn: () => api.getAnalyticsTokenOperations(params.value)
});
const capabilitiesQuery = useQuery<AnalyticsCapabilities, Error>({
  queryKey: computed(() => ["analytics-capabilities", params.value]),
  enabled: () => activeTab.value === "capabilities",
  retry: false,
  queryFn: () => api.getAnalyticsCapabilities(params.value)
});
const organizationsQuery = useQuery<AnalyticsOrganizationUsageRow[], Error>({
  queryKey: computed(() => ["analytics-organizations", params.value]),
  enabled: () => activeTab.value === "organizations",
  retry: false,
  queryFn: () => api.getAnalyticsOrganizations({ ...params.value, groupBy: "department" })
});
const satisfactionQuery = useQuery<AnalyticsSatisfaction, Error>({
  queryKey: computed(() => ["analytics-satisfaction", feedbackParams.value]),
  enabled: () => activeTab.value === "satisfaction",
  retry: false,
  queryFn: () => api.getAnalyticsSatisfaction(feedbackParams.value)
});
const exceptionsQuery = useQuery<PageResponse<AnalyticsExceptionDetail>, Error>({
  queryKey: computed(() => ["analytics-exceptions", exceptionsParams.value]),
  enabled: () => activeTab.value === "exceptions",
  retry: false,
  queryFn: () => api.getAnalyticsExceptions(exceptionsParams.value)
});
const sessionUsageQuery = useQuery<PageResponse<AnalyticsSessionUsageRow>, Error>({
  queryKey: computed(() => ["analytics-session-usage", sessionsParams.value]),
  enabled: () => activeTab.value === "sessions",
  retry: false,
  queryFn: () => api.getAnalyticsSessionUsage(sessionsParams.value)
});

const overview = computed(() => overviewQuery.data.value);
const funnel = computed(() => funnelQuery.data.value);
const timeseries = computed(() => timeseriesQuery.data.value ?? []);
const heatmap = computed(() => heatmapQuery.data.value);
const maxTrendRun = computed(() => Math.max(1, ...timeseries.value.map(item => item.runCount)));
const maxHeatmap = computed(() => Math.max(1, ...(heatmap.value?.points ?? []).map(item => item.value)));
const trendGridStyle = computed(() => ({ "--ta-trend-columns": String(Math.max(timeseries.value.length, 1)) }));
const capabilityRows = computed(() => (capabilitiesQuery.data.value?.rows ?? [])
  .filter(row => capabilityType.value === "ALL" || row.type === capabilityType.value));
// 组织分析与 Token 用户排行接口只返回 topN 条数组、没有 total，取满后在这里本地分页
const organizationRows = computed(() => organizationsQuery.data.value ?? []);
const organizationPageRows = computed(() => pageSlice(organizationRows.value, organizationPage.value));
const tokenUserRows = computed(() => tokenQuery.data.value?.users ?? []);
const tokenUserPageRows = computed(() => pageSlice(tokenUserRows.value, tokenUserPage.value));

function pageSlice<T>(rows: T[], page: number): T[] {
  const from = (page - 1) * SERVER_PAGE_SIZE;
  return rows.slice(from, from + SERVER_PAGE_SIZE);
}
const capabilityCoverageText = computed(() => {
  const value = capabilitiesQuery.data.value;
  if (!value) return "正在读取插件覆盖口径";
  const source = value.source === "OPENCODE_PLUGIN" ? "OpenCode 插件事实" : "旧 RunEvent 推导";
  const start = value.coverageStartAt ? new Date(value.coverageStartAt).toLocaleString("zh-CN") : "暂无覆盖起点";
  const completeThrough = value.completeThrough
    ? new Date(value.completeThrough).toLocaleString("zh-CN")
    : "尚无完整水位";
  return `${source} · 覆盖 ${start} · 完整至 ${completeThrough} · Rollout ${formatRate(value.rolloutCompleteness)}`;
});
const freshnessText = computed(() => {
  const freshness = overview.value?.freshness;
  if (!freshness?.generatedAt) return "暂无统计时间";
  const status = freshness.status === "FRESH" ? "最新" : freshness.status === "FAILED" ? "失败" : "可能延迟";
  return `${status} · ${new Date(freshness.generatedAt).toLocaleString("zh-CN")}`;
});
const summaryCards = computed(() => {
  const item = overview.value;
  return [
    { label: "用户消息", value: item?.userMessageCount ?? 0, extra: `AI 回复 ${item?.assistantMessageCount ?? 0}` },
    { label: "Run 启动", value: item?.runCount ?? 0, extra: `成功率 ${formatRate(item?.successRate)}` },
    { label: "满意率", value: formatRate(item?.satisfactionRate), extra: `反馈 ${(item?.positiveFeedbackCount ?? 0) + (item?.negativeFeedbackCount ?? 0)}` },
    { label: "Diff 采纳率", value: formatRate(item?.diffAcceptanceRate), extra: `生成 ${item?.diffProposedCount ?? 0}` },
    { label: "p95 耗时", value: formatDuration(item?.p95DurationMs), extra: `平均 ${formatDuration(item?.averageDurationMs)}` },
    { label: "主 Token 使用量", value: formatNumber(item?.totalTokens ?? 0), extra: `Run 均 ${formatNumber(item?.tokensPerRun)}` }
  ];
});

watch(organization, () => {
  rdDepartment.value = "";
  department.value = "";
});
watch(rdDepartment, () => {
  department.value = "";
});

// 筛选条件变化后各列表总数会变，页码必须统一回到第 1 页，避免停留在越界页
function resetListPages() {
  usersPage.value = 1;
  sessionsPage.value = 1;
  feedbackPage.value = 1;
  exceptionsPage.value = 1;
  organizationPage.value = 1;
  tokenUserPage.value = 1;
}
watch(params, resetListPages);

function changeUsersPage(next: number) {
  usersPage.value = next;
}
function changeSessionPage(next: number) {
  sessionsPage.value = next;
}
function changeFeedbackPage(next: number) {
  feedbackPage.value = next;
}
function changeExceptionsPage(next: number) {
  exceptionsPage.value = next;
}
function changeOrganizationPage(next: number) {
  organizationPage.value = next;
}
function changeTokenUserPage(next: number) {
  tokenUserPage.value = next;
}

function refresh() {
  void optionsQuery.refetch();
  void overviewQuery.refetch();
  void funnelQuery.refetch();
  if (heatmapRangeSupported.value) void heatmapQuery.refetch();
  void timeseriesQuery.refetch();
  void usersQuery.refetch();
  void sessionUsageQuery.refetch();
  void tokenQuery.refetch();
  void capabilitiesQuery.refetch();
  void organizationsQuery.refetch();
  void satisfactionQuery.refetch();
  void exceptionsQuery.refetch();
}

function applyRangePreset() {
  if (rangePreset.value === "custom") return;
  const rangeEnd = new Date();
  const days = Number(rangePreset.value);
  startTime.value = toLocalInput(new Date(rangeEnd.getTime() - days * 24 * 60 * 60 * 1000));
  endTime.value = toLocalInput(rangeEnd);
  granularity.value = days <= 2 ? "hour" : days <= 90 ? "day" : "week";
}

function markCustomRange() {
  rangePreset.value = "custom";
}

async function exportAll() {
  const blob = await api.exportAnalyticsXlsx(params.value);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  const date = new Date();
  const stamp = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
  link.download = `运营分析-${stamp}.xlsx`;
  link.click();
  URL.revokeObjectURL(url);
}

function heatmapColor(value: number) {
  if (value === 0) return "#f1f3f6";
  const alpha = 0.18 + Math.min(0.82, value / maxHeatmap.value * 0.82);
  return `rgba(190, 31, 48, ${alpha})`;
}

function capabilityLabel(type: string) {
  return type === "AGENT" ? "Agent" : type === "SKILL" ? "Skill" : type === "TOOL" ? "Tool" : type;
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
  return point.runCount === 0 ? "0px" : `${Math.max(6, Math.round((point.runCount / maxTrendRun.value) * 72))}px`;
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
        <button type="button" class="ta-icon-btn" title="刷新" aria-label="刷新" @click="refresh"><RefreshCw :size="16" /></button>
        <button type="button" class="ta-export-btn" @click="exportAll"><Download :size="15" /><span>导出 Excel</span></button>
      </div>
    </header>

    <div class="ta-analytics-filters">
      <label>快速范围
        <select v-model="rangePreset" aria-label="快速范围" @change="applyRangePreset">
          <option value="7">最近 7 天</option><option value="30">最近 30 天</option>
          <option value="90">最近 90 天</option><option value="custom">自定义</option>
        </select>
      </label>
      <label>开始<input v-model="startTime" type="datetime-local" @change="markCustomRange" /></label>
      <label>结束<input v-model="endTime" type="datetime-local" @change="markCustomRange" /></label>
      <label>机构
        <select v-model="organization"><option value="">全部机构</option><option v-for="item in optionsQuery.data.value?.organizations ?? []" :key="item.value" :value="item.value">{{ item.label }}</option></select>
      </label>
      <label>研发部
        <select v-model="rdDepartment"><option value="">全部研发部</option><option v-for="item in optionsQuery.data.value?.rdDepartments ?? []" :key="item.value" :value="item.value">{{ item.label }}</option></select>
      </label>
      <label>部门
        <select v-model="department"><option value="">全部部门</option><option v-for="item in optionsQuery.data.value?.departments ?? []" :key="item.value" :value="item.value">{{ item.label }}</option></select>
      </label>
      <label class="ta-search-label">用户<span class="ta-search"><Search :size="14" /><input v-model="userKeyword" aria-label="用户" placeholder="姓名或用户 ID" /></span></label>
    </div>

    <nav class="ta-analytics-tabs" aria-label="运营分析视图">
      <button :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">使用总览</button>
      <button :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">用户运营</button>
      <button :class="{ active: activeTab === 'sessions' }" @click="activeTab = 'sessions'">会话消息</button>
      <button :class="{ active: activeTab === 'token' }" @click="activeTab = 'token'">Token 运营</button>
      <button :class="{ active: activeTab === 'capabilities' }" @click="activeTab = 'capabilities'">能力使用</button>
      <button :class="{ active: activeTab === 'organizations' }" @click="activeTab = 'organizations'">组织分析</button>
      <button :class="{ active: activeTab === 'satisfaction' }" @click="activeTab = 'satisfaction'">满意度</button>
      <button :class="{ active: activeTab === 'exceptions' }" @click="activeTab = 'exceptions'">异常 Run</button>
    </nav>

    <main v-if="activeTab === 'overview'" class="ta-analytics-main">
      <div class="ta-card-grid">
        <article v-for="card in summaryCards" :key="card.label" class="ta-metric-card"><span>{{ card.label }}</span><strong>{{ card.value }}</strong><small>{{ card.extra }}</small></article>
      </div>

      <div class="ta-overview-grid">
        <section class="ta-panel ta-funnel-panel">
          <div class="ta-panel-heading"><h3>用户使用漏斗</h3><span>{{ formatRate(funnel?.deepRate) }} 活跃转深度</span></div>
          <div class="ta-funnel">
            <div class="ta-funnel-stage total"><span>总用户数</span><strong>{{ funnel?.totalUsers ?? 0 }}</strong></div>
            <div class="ta-funnel-stage active"><span>活跃用户数</span><strong>{{ funnel?.activeUsers ?? 0 }}</strong></div>
            <div class="ta-funnel-stage deep"><span>深度用户数</span><strong>{{ funnel?.deepUsers ?? 0 }}</strong></div>
          </div>
          <div class="ta-definitions"><p>{{ funnel?.activeDefinition }}</p><p>{{ funnel?.deepDefinition }}</p></div>
        </section>

        <section class="ta-panel">
          <div class="ta-panel-heading"><h3>Run 趋势</h3><span>{{ timeseries.length }} 个时间点</span></div>
          <div v-if="timeseries.length === 0" class="ta-empty">暂无数据</div>
          <div v-else class="ta-trend" :style="trendGridStyle">
            <div v-for="point in timeseries" :key="point.bucketStart" class="ta-trend-item">
              <div class="ta-trend-bar-wrapper">
                <span class="ta-trend-count">{{ point.runCount }}</span>
                <div class="ta-trend-bar" :style="{ height: trendHeight(point) }" />
              </div>
              <small>{{ new Date(point.bucketStart).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) }}</small>
            </div>
          </div>
        </section>
      </div>

      <section class="ta-panel ta-heatmap-panel">
        <div class="ta-panel-heading">
          <h3>小时热力</h3><span>{{ heatmap?.dates.length ?? 0 }} 天 × 24 小时</span>
          <div class="ta-segmented" aria-label="热力指标">
            <button v-for="item in [{ value: 'USER_MESSAGES', label: '用户消息' }, { value: 'PRIMARY_TOKENS', label: '主 Token' }, { value: 'CACHE_TOKENS', label: '缓存 Token' }]" :key="item.value" :class="{ active: heatmapMetric === item.value }" @click="heatmapMetric = item.value as AnalyticsHeatmapMetric">{{ item.label }}</button>
          </div>
        </div>
        <div v-if="!heatmapRangeSupported" class="ta-empty">小时热力图最多支持 90 天</div>
        <div v-else class="ta-heatmap-scroll">
          <div class="ta-heatmap-row ta-heatmap-hours"><span /> <small v-for="hour in 24" :key="hour">{{ String(hour - 1).padStart(2, '0') }}</small></div>
          <div v-for="date in heatmap?.dates ?? []" :key="date" class="ta-heatmap-row">
            <strong>{{ date.slice(5) }}</strong>
            <span v-for="point in (heatmap?.points ?? []).filter(item => item.date === date)" :key="`${date}-${point.hourOfDay}`" class="ta-heatmap-cell" :style="{ backgroundColor: heatmapColor(point.value) }" :title="`${date} ${point.hourOfDay}:00 · ${formatNumber(point.value)}`" />
          </div>
        </div>
      </section>
    </main>

    <section v-else-if="activeTab === 'token'" class="ta-stack">
      <div class="ta-card-grid">
        <article class="ta-metric-card"><span>总 Token 使用量</span><strong>{{ formatNumber(tokenQuery.data.value?.totalTokens) }}</strong><small>含主 Token 与缓存读写</small></article>
        <article class="ta-metric-card"><span>日人均 Token</span><strong>{{ formatNumber(tokenQuery.data.value?.dailyTokensPerUser) }}</strong><small>仅统计有 Token 使用的人天</small></article>
        <article class="ta-metric-card"><span>Token 使用率</span><strong>{{ formatRate(tokenQuery.data.value?.tokenUserRate) }}</strong><small>{{ tokenQuery.data.value?.tokenUsers ?? 0 }} / {{ tokenQuery.data.value?.activeUsers ?? 0 }} 人</small></article>
        <article class="ta-metric-card"><span>重复使用率</span><strong>{{ formatRate(tokenQuery.data.value?.repeatTokenUserRate) }}</strong><small>至少 2 个 Token 使用日</small></article>
        <article class="ta-metric-card"><span>缓存 Token</span><strong>{{ formatNumber((tokenQuery.data.value?.cacheReadTokens ?? 0) + (tokenQuery.data.value?.cacheWriteTokens ?? 0)) }}</strong><small>读 {{ formatNumber(tokenQuery.data.value?.cacheReadTokens) }} · 写 {{ formatNumber(tokenQuery.data.value?.cacheWriteTokens) }}</small></article>
      </div>
      <div class="ta-two-columns">
        <section class="ta-panel"><h3>每日 Token 使用</h3><table class="ta-table"><thead><tr><th>日期</th><th>使用用户</th><th>总 Token</th><th>日人均</th><th>主 Token</th><th>缓存读/写</th></tr></thead><tbody><tr v-for="row in tokenQuery.data.value?.daily ?? []" :key="row.date"><td>{{ row.date }}</td><td>{{ row.tokenUsers }}</td><td>{{ formatNumber(row.totalTokens) }}</td><td>{{ formatNumber(row.tokensPerUser) }}</td><td>{{ formatNumber(row.primaryTokens) }}</td><td>{{ formatNumber(row.cacheReadTokens) }} / {{ formatNumber(row.cacheWriteTokens) }}</td></tr></tbody></table></section>
        <section class="ta-panel"><h3>用户使用排行</h3><table class="ta-table"><thead><tr><th>用户</th><th>使用强度</th><th>Token 日</th><th>总 Token</th><th>Token 日均</th></tr></thead><tbody><tr v-for="row in tokenUserPageRows" :key="row.userId"><td>{{ row.username || row.userId }}</td><td><span class="ta-band">{{ row.intensityBand }}</span></td><td>{{ row.tokenDays }}</td><td>{{ formatNumber(row.totalTokens) }}</td><td>{{ formatNumber(row.tokensPerTokenDay) }}</td></tr></tbody></table>
          <div v-if="tokenUserRows.length > SERVER_PAGE_SIZE" class="ta-pagination"><el-pagination background layout="prev, pager, next, total" :current-page="tokenUserPage" :page-size="SERVER_PAGE_SIZE" :total="tokenUserRows.length" @current-change="changeTokenUserPage" /></div>
        </section>
      </div>
    </section>

    <section v-else-if="activeTab === 'capabilities'" class="ta-panel">
      <div class="ta-panel-heading"><div><h3>Agent / Skill / Tool 使用率</h3><span>分母：{{ capabilitiesQuery.data.value?.activeUsers ?? 0 }} 个活跃用户</span></div><div class="ta-segmented"><button v-for="type in ['ALL', 'AGENT', 'SKILL', 'TOOL'] as CapabilityType[]" :key="type" :class="{ active: capabilityType === type }" @click="capabilityType = type">{{ type === 'ALL' ? '全部' : capabilityLabel(type) }}</button></div></div>
      <p class="ta-capability-coverage">{{ capabilityCoverageText }}</p>
      <table class="ta-table"><thead><tr><th>类型</th><th>名称</th><th>使用率</th><th>使用用户</th><th>调用次数</th><th>成功</th><th>失败</th><th>取消</th><th>未完成</th></tr></thead><tbody><tr v-for="row in capabilityRows" :key="`${row.type}-${row.name}`"><td><span class="ta-type">{{ capabilityLabel(row.type) }}</span></td><td>{{ row.name }}</td><td class="ta-rate">{{ formatRate(row.usageRate) }}</td><td>{{ row.userCount }}</td><td>{{ row.invocationCount }}</td><td>{{ row.succeededCount }}</td><td>{{ row.failedCount }}</td><td>{{ row.cancelledCount }}</td><td>{{ row.incompleteCount }}</td></tr></tbody></table>
    </section>

    <section v-else-if="activeTab === 'users'" class="ta-panel"><h3>用户使用明细</h3><table class="ta-table"><thead><tr><th>用户</th><th>机构</th><th>研发部</th><th>部门</th><th>登录</th><th>会话</th><th>消息</th><th>Run</th><th>成功率</th><th>满意率</th><th>Token</th></tr></thead><tbody><tr v-for="row in usersQuery.data.value?.items ?? []" :key="row.userId"><td>{{ row.username || row.userId }}</td><td>{{ row.organization || '-' }}</td><td>{{ row.rdDepartment || '-' }}</td><td>{{ row.department || '-' }}</td><td>{{ row.loginCount }}</td><td>{{ row.activeSessionCount }}</td><td>{{ row.userMessageCount }}</td><td>{{ row.runCount }}</td><td>{{ formatRate(row.successRate) }}</td><td>{{ formatRate(row.satisfactionRate) }}</td><td>{{ formatNumber(row.totalTokens) }}</td></tr></tbody></table>
      <div v-if="(usersQuery.data.value?.total ?? 0) > SERVER_PAGE_SIZE" class="ta-pagination"><el-pagination background layout="prev, pager, next, total" :current-page="usersPage" :page-size="SERVER_PAGE_SIZE" :total="usersQuery.data.value?.total ?? 0" @current-change="changeUsersPage" /></div>
    </section>

    <section v-else-if="activeTab === 'sessions'" class="ta-panel"><h3>会话消息统计</h3><table class="ta-table"><thead><tr><th>用户名</th><th>会话名</th><th>用户消息数</th><th>首次发送时间</th><th>最后发送时间</th></tr></thead><tbody><tr v-for="row in sessionUsageQuery.data.value?.items ?? []" :key="`${row.userId ?? 'unknown'}-${row.sessionId}`"><td>{{ row.username || row.userId || '未知用户' }}</td><td><span class="ta-ellipsis" :title="row.sessionTitle || '-'">{{ row.sessionTitle || '-' }}</span></td><td>{{ row.userMessageCount }}</td><td>{{ row.firstMessageAt ? new Date(row.firstMessageAt).toLocaleString('zh-CN') : '-' }}</td><td>{{ row.lastMessageAt ? new Date(row.lastMessageAt).toLocaleString('zh-CN') : '-' }}</td></tr></tbody></table>
      <div v-if="(sessionUsageQuery.data.value?.total ?? 0) > SERVER_PAGE_SIZE" class="ta-pagination"><el-pagination background layout="prev, pager, next, total" :current-page="sessionsPage" :page-size="SERVER_PAGE_SIZE" :total="sessionUsageQuery.data.value?.total ?? 0" @current-change="changeSessionPage" /></div>
    </section>

    <section v-else-if="activeTab === 'organizations'" class="ta-panel"><h3>组织排行</h3><table class="ta-table"><thead><tr><th>维度</th><th>名称</th><th>登录用户</th><th>活跃用户</th><th>深度用户</th><th>Run</th><th>成功率</th><th>满意率</th><th>Token</th></tr></thead><tbody><tr v-for="row in organizationPageRows" :key="`${row.dimension}-${row.name}`"><td>{{ row.dimension }}</td><td>{{ row.name }}</td><td>{{ row.loginUsers }}</td><td>{{ row.activeUsers }}</td><td>{{ row.deepUsers }}</td><td>{{ row.runCount }}</td><td>{{ formatRate(row.successRate) }}</td><td>{{ formatRate(row.satisfactionRate) }}</td><td>{{ formatNumber(row.totalTokens) }}</td></tr></tbody></table>
      <div v-if="organizationRows.length > SERVER_PAGE_SIZE" class="ta-pagination"><el-pagination background layout="prev, pager, next, total" :current-page="organizationPage" :page-size="SERVER_PAGE_SIZE" :total="organizationRows.length" @current-change="changeOrganizationPage" /></div>
    </section>

    <section v-else-if="activeTab === 'satisfaction'" class="ta-panel"><h3>满意度与反馈明细</h3><div class="ta-reason-list"><span v-for="(count, reason) in satisfactionQuery.data.value?.negativeReasonCounts ?? {}" :key="reason">{{ reason }} · {{ count }}</span></div><table class="ta-table"><thead><tr><th>时间</th><th>用户</th><th>组织</th><th>会话</th><th>Run</th><th>反馈</th><th>原因</th><th>备注</th></tr></thead><tbody><tr v-for="row in satisfactionQuery.data.value?.feedbackDetails.items ?? []" :key="row.feedbackId"><td>{{ new Date(row.createdAt).toLocaleString('zh-CN') }}</td><td>{{ row.username || row.userId }}</td><td>{{ [row.organization, row.rdDepartment, row.department].filter(Boolean).join(' / ') }}</td><td>{{ row.sessionId }}</td><td>{{ row.runId || '-' }}</td><td>{{ row.rating === 'POSITIVE' ? '满意' : '不满意' }}</td><td>{{ row.reasonCode || '-' }}</td><td>{{ row.comment || '-' }}</td></tr></tbody></table>
      <div v-if="(satisfactionQuery.data.value?.feedbackDetails.total ?? 0) > SERVER_PAGE_SIZE" class="ta-pagination"><el-pagination background layout="prev, pager, next, total" :current-page="feedbackPage" :page-size="SERVER_PAGE_SIZE" :total="satisfactionQuery.data.value?.feedbackDetails.total ?? 0" @current-change="changeFeedbackPage" /></div>
    </section>

    <section v-else class="ta-panel"><h3>异常 Run 明细</h3><table class="ta-table"><thead><tr><th>时间</th><th>Run</th><th>用户</th><th>组织</th><th>状态</th></tr></thead><tbody><tr v-for="row in exceptionsQuery.data.value?.items ?? []" :key="row.runId"><td>{{ new Date(row.updatedAt).toLocaleString('zh-CN') }}</td><td>{{ row.runId }}</td><td>{{ row.username || row.userId }}</td><td>{{ [row.organization, row.rdDepartment, row.department].filter(Boolean).join(' / ') }}</td><td>{{ row.status }}</td></tr></tbody></table>
      <div v-if="(exceptionsQuery.data.value?.total ?? 0) > SERVER_PAGE_SIZE" class="ta-pagination"><el-pagination background layout="prev, pager, next, total" :current-page="exceptionsPage" :page-size="SERVER_PAGE_SIZE" :total="exceptionsQuery.data.value?.total ?? 0" @current-change="changeExceptionsPage" /></div>
    </section>
  </section>
</template>

<style scoped>
.ta-analytics { display:flex; flex-direction:column; height:100%; min-height:0; padding:16px; gap:12px; overflow:auto; background:#f5f7fa; color:#202630; }
.ta-analytics-header,.ta-analytics-header-actions,.ta-analytics-tabs,.ta-analytics-filters,.ta-card-grid,.ta-panel-heading,.ta-reason-list { display:flex; align-items:center; }
.ta-analytics-header { justify-content:space-between; gap:12px; }
.ta-analytics-header h2 { margin:0; font-size:19px; letter-spacing:0; }
.ta-analytics-header p { margin:4px 0 0; color:#687386; font-size:12px; }
.ta-analytics-header-actions { gap:8px; }
.ta-icon-btn,.ta-export-btn { height:32px; border:1px solid #d7dce3; border-radius:5px; background:#fff; color:#374151; cursor:pointer; }
.ta-icon-btn { display:grid; width:32px; place-items:center; }
.ta-export-btn { display:inline-flex; align-items:center; gap:6px; padding:0 10px; cursor:pointer; }
.ta-export-btn:hover { border-color:#b9c2cc; background:#f5f7fa; }
.ta-analytics-filters { flex-wrap:wrap; gap:9px 14px; padding:10px 12px; border-block:1px solid #dfe3e8; background:#fff; }
.ta-analytics-filters label { display:inline-flex; align-items:center; gap:6px; color:#505b6b; font-size:12px; }
.ta-analytics-filters input,.ta-analytics-filters select { height:30px; min-width:126px; box-sizing:border-box; border:1px solid #d5dae2; border-radius:4px; padding:0 8px; background:#fff; color:#28313d; font-size:12px; }
.ta-search { display:inline-flex; align-items:center; gap:4px; height:30px; padding-left:7px; border:1px solid #d5dae2; border-radius:4px; color:#8a94a3; }
.ta-search input { min-width:150px; height:28px; padding-left:0; border:0; outline:0; }
.ta-analytics-tabs { flex-wrap:wrap; gap:3px; border-bottom:1px solid #d8dde5; }
.ta-analytics-tabs button { height:34px; padding:0 12px; border:0; border-bottom:2px solid transparent; background:transparent; color:#596577; cursor:pointer; }
.ta-analytics-tabs button.active { border-bottom-color:#bd1f31; color:#a41729; font-weight:600; }
.ta-analytics-main,.ta-stack { display:grid; min-width:0; gap:12px; }
.ta-card-grid { min-width:0; flex-wrap:wrap; gap:8px; }
.ta-metric-card { flex:1 1 145px; min-width:140px; min-height:82px; box-sizing:border-box; padding:11px 12px; border:1px solid #e0e4e9; border-radius:6px; background:#fff; }
.ta-metric-card span,.ta-metric-card small { display:block; color:#687386; font-size:12px; }
.ta-metric-card strong { display:block; margin:7px 0 5px; color:#171d26; font-size:21px; letter-spacing:0; }
.ta-overview-grid,.ta-two-columns { display:grid; grid-template-columns:minmax(320px,.8fr) minmax(420px,1.2fr); gap:12px; }
.ta-panel { min-width:0; padding:12px; border:1px solid #e0e4e9; border-radius:6px; background:#fff; overflow:auto; }
.ta-panel h3 { margin:0 0 10px; font-size:14px; letter-spacing:0; }
.ta-panel-heading { justify-content:space-between; gap:12px; margin-bottom:10px; }
.ta-panel-heading h3 { margin:0; }
.ta-panel-heading span { color:#737e8e; font-size:12px; }
.ta-capability-coverage { margin:-2px 0 12px; padding:8px 10px; border:1px solid #e5e8ef; border-radius:8px; background:#f8f9fb; color:#626d7d; font-size:11px; }
.ta-funnel { display:flex; flex-direction:column; align-items:center; gap:6px; min-height:168px; justify-content:center; }
.ta-funnel-stage { display:flex; align-items:center; justify-content:space-between; height:45px; box-sizing:border-box; gap:8px; padding:0 22px; color:#fff; }
.ta-funnel-stage.total { width:92%; background:#44546a; clip-path:polygon(0 0, 100% 0, 93% 100%, 7% 100%); border-radius:4px 4px 0 0; }
.ta-funnel-stage.active { width:74%; background:#227c78; clip-path:polygon(0 0, 100% 0, 91% 100%, 9% 100%); }
.ta-funnel-stage.deep { width:56%; background:#bd1f31; clip-path:polygon(0 0, 100% 0, 88% 100%, 12% 100%); border-radius:0 0 4px 4px; }
.ta-funnel-stage span { min-width:0; font-size:12px; line-height:1.2; }.ta-funnel-stage strong { flex-shrink:0; font-size:18px; }
.ta-definitions { padding-top:8px; border-top:1px solid #edf0f3; color:#687386; font-size:11px; }.ta-definitions p { margin:4px 0; }
.ta-empty { padding:28px; color:#98a1ae; text-align:center; }
.ta-trend { display:grid; grid-template-columns:repeat(var(--ta-trend-columns),minmax(34px,1fr)); align-items:flex-end; gap:clamp(4px,.6vw,10px); min-height:180px; overflow-x:auto; }
.ta-trend-item { display:grid; grid-template-rows:112px 18px; justify-items:center; min-width:0; color:#737e8e; font-size:11px; }
.ta-trend-bar-wrapper { display:flex; flex-direction:column; align-items:center; justify-content:flex-end; width:100%; height:112px; }
.ta-trend-count { font-size:11px; color:#596577; margin-bottom:3px; line-height:1; font-weight:500; }
.ta-trend-bar { width:clamp(12px,45%,22px); border-radius:3px 3px 0 0; background:#227c78; }
.ta-heatmap-panel { overflow:hidden; }.ta-panel-heading .ta-segmented { margin-left:auto; }
.ta-segmented { display:inline-flex; border:1px solid #d4d9e0; border-radius:5px; overflow:hidden; }
.ta-segmented button { height:28px; padding:0 9px; border:0; border-right:1px solid #d4d9e0; background:#fff; color:#596577; font-size:11px; cursor:pointer; }.ta-segmented button:last-child { border-right:0; }.ta-segmented button.active { background:#2d3745; color:#fff; }
.ta-heatmap-scroll { width:100%; min-width:0; overflow-x:auto; }.ta-heatmap-row { display:grid; width:100%; min-width:0; grid-template-columns:56px repeat(24,minmax(0,1fr)); gap:3px; align-items:center; margin-bottom:3px; }
.ta-heatmap-row > strong { color:#596577; font-size:11px; font-weight:500; }.ta-heatmap-hours small { color:#8993a1; font-size:9px; text-align:center; }
.ta-heatmap-cell { width:100%; height:10px; border-radius:2px; }
.ta-reason-list { flex-wrap:wrap; gap:6px; margin-bottom:8px; }.ta-reason-list span,.ta-band,.ta-type { display:inline-block; padding:3px 6px; border-radius:4px; background:#eef1f4; color:#4c5868; font-size:11px; }
.ta-type { background:#e8f2f1; color:#176b67; }.ta-rate { color:#a41729; font-weight:600; }
.ta-table { width:100%; border-collapse:collapse; font-size:12px; }.ta-table th,.ta-table td { padding:8px; border-bottom:1px solid #edf0f3; text-align:left; white-space:nowrap; }.ta-table th { color:#697486; font-weight:600; background:#fafbfc; }
.ta-ellipsis { display:inline-block; max-width:320px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; vertical-align:bottom; }
.ta-pagination { display:flex; justify-content:flex-end; margin-top:8px; }
@media (max-width:900px) { .ta-overview-grid,.ta-two-columns { grid-template-columns:1fr; }.ta-analytics { padding:10px; }.ta-analytics-header { align-items:flex-start; }.ta-analytics-filters { align-items:flex-start; }.ta-search-label { width:100%; }.ta-search { flex:1; }.ta-search input { width:100%; }.ta-panel-heading { align-items:flex-start; flex-wrap:wrap; } }
</style>
