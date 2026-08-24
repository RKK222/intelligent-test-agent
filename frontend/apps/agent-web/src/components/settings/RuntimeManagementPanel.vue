<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Refresh, Search } from "@element-plus/icons-vue";
import { ElMessageBox } from "element-plus";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  OpencodeRuntimeBackendMetricHistory,
  OpencodeRuntimeBackendProcess,
  OpencodeRuntimeContainer,
  OpencodeRuntimeContainerMetricHistory,
  OpencodeRuntimeLinuxServer,
  OpencodeRuntimeManagementOverview,
  OpencodeRuntimeManagementOverviewParams,
  OpencodeRuntimeManagedProcess,
  OpencodeRuntimeManager,
  OpencodeRuntimeProcess,
  PageResponse
} from "@test-agent/shared-types";
import RuntimeMetricChart from "./RuntimeMetricChart.vue";
import RuntimeTopologyGraph from "./RuntimeTopologyGraph.vue";

const props = defineProps<{
  currentUser: CurrentUser | null;
  pageActive: boolean;
}>();

const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();

type FilterDraft = {
  status: string;
  linuxServerId: string;
  containerId: string;
};

type RuntimeContainerManagerRow = {
  key: string;
  containerId?: string | null;
  linuxServerId?: string | null;
  container?: OpencodeRuntimeContainer;
  manager?: OpencodeRuntimeManager;
  managedProcesses: OpencodeRuntimeManagedProcess[];
};

type RuntimeServerBackendRow = {
  key: string;
  linuxServerId?: string | null;
  server?: OpencodeRuntimeLinuxServer;
  backend?: OpencodeRuntimeBackendProcess;
};

type ManagedProcessActionKind = "restart" | "stop";

type ManagedProcessActionRequest = {
  action: ManagedProcessActionKind;
  containerId: string;
  port: number;
};

type BatchManagedProcessTarget = {
  key: string;
  containerId: string;
  port: number;
  label: string;
  pid?: number | null;
  startedAt?: string | null;
};

type BatchManagedProcessFailure = {
  key: string;
  label: string;
  message: string;
};

type BatchManagedProcessResult = {
  action: ManagedProcessActionKind;
  total: number;
  succeeded: number;
  failures: BatchManagedProcessFailure[];
  targets: BatchManagedProcessTarget[];
};

type BatchManagedProcessTargetStatus = {
  label: string;
  state: "pending" | "running" | "succeeded" | "failed";
  detail?: string;
};

type BatchManagedProcessProgress = {
  version: 1;
  initiatedByUserId: string;
  action: ManagedProcessActionKind;
  targets: BatchManagedProcessTarget[];
  nextIndex: number;
  succeeded: number;
  failures: BatchManagedProcessFailure[];
  activeTargetKey?: string | null;
  retryAfter?: number | null;
  startedAt: number;
};

const BATCH_PROGRESS_STORAGE_PREFIX = "testagent.runtime-management.batch.v1";
const BATCH_RETRY_GRACE_MS = 30_000;

const processStatusOptions = ["RUNNING"];
const metricsSourceHelp =
  "“cgroup”: Linux 容器/cgroup 整体指标，包含 manager 和下属 TestAgent server 等进程\n" +
  "“process”: 降级为当前 Go manager 进程指标\n" +
  "“不可采集”: 当前环境无法安全采集\n" +
  "“-”: 旧数据或未上报";
const draftFilters = ref<FilterDraft>({ status: "", linuxServerId: "", containerId: "" });
const activeFilters = ref<FilterDraft>({ status: "", linuxServerId: "", containerId: "" });
const userKeywordDraft = ref("");
const activeUserKeyword = ref("");
const page = ref(1);
const size = ref(20);
const userProcessPage = ref(1);
const userProcessSize = ref(20);
const expandedRuntimeRowKeys = ref<Set<string>>(new Set());
const selectedMetricsTarget = ref<{ type: "container" | "backend"; id: string; title: string } | null>(null);
const selectedWindowMinutes = ref(60);
const actionErrorMessage = ref("");
const activeManagedProcessAction = ref<ManagedProcessActionRequest | null>(null);
const selectedManagedProcessKeys = ref<Set<string>>(new Set());
const batchManagedProcessAction = ref<ManagedProcessActionKind | null>(null);
const batchManagedProcessResult = ref<BatchManagedProcessResult | null>(null);
const batchManagedProcessProgress = ref<BatchManagedProcessProgress | null>(null);
const batchRunnerActive = ref(false);

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const overviewParams = computed<OpencodeRuntimeManagementOverviewParams>(() => ({
  status: activeFilters.value.status || undefined,
  linuxServerId: activeFilters.value.linuxServerId.trim() || undefined,
  containerId: activeFilters.value.containerId.trim() || undefined,
  page: page.value,
  size: size.value
}));
const overviewQueryKey = computed(() => ["opencode-runtime-management", overviewParams.value] as const);
const userProcessParams = computed(() => ({
  keyword: activeUserKeyword.value.trim(),
  page: userProcessPage.value,
  size: userProcessSize.value
}));
const userProcessQueryKey = computed(() => ["opencode-runtime-management-user-processes", userProcessParams.value] as const);

const overviewQuery = useQuery<OpencodeRuntimeManagementOverview, Error>({
  queryKey: overviewQueryKey,
  enabled: () => hasSuperAdmin.value && props.pageActive,
  retry: false,
  refetchInterval: () => props.pageActive ? 5000 : false,
  queryFn: () => api.getOpencodeRuntimeManagementOverview(overviewParams.value)
});

const userProcessQuery = useQuery<PageResponse<OpencodeRuntimeProcess>, Error>({
  queryKey: userProcessQueryKey,
  enabled: () => hasSuperAdmin.value && props.pageActive && activeUserKeyword.value.trim().length > 0,
  retry: false,
  queryFn: () => api.getOpencodeRuntimeManagementUserProcesses(userProcessParams.value)
});

const metricsQuery = useQuery<OpencodeRuntimeContainerMetricHistory | OpencodeRuntimeBackendMetricHistory, Error>({
  queryKey: computed(() => ["opencode-runtime-metrics", selectedMetricsTarget.value?.type, selectedMetricsTarget.value?.id, selectedWindowMinutes.value]),
  enabled: () => hasSuperAdmin.value && props.pageActive && selectedMetricsTarget.value !== null,
  retry: false,
  refetchInterval: () => props.pageActive ? 5000 : false,
  queryFn: () => {
    const target = selectedMetricsTarget.value;
    if (!target) {
      throw new Error("未选择监控对象");
    }
    const params = { windowMinutes: selectedWindowMinutes.value, maxPoints: 720 };
    if (target.type === "container") {
      return api.getOpencodeRuntimeContainerMetrics(target.id, params);
    }
    return api.getOpencodeRuntimeBackendServerMetrics(target.id, params);
  }
});

const managedProcessActionMutation = useMutation({
  mutationFn: (request: ManagedProcessActionRequest) => {
    if (request.action === "restart") {
      return api.restartOpencodeRuntimeManagedProcess(request.containerId, request.port);
    }
    return api.stopOpencodeRuntimeManagedProcess(request.containerId, request.port);
  },
  onMutate: request => {
    actionErrorMessage.value = "";
    batchManagedProcessResult.value = null;
    activeManagedProcessAction.value = request;
  },
  onSuccess: (_result, request) => {
    if (request.action === "stop") {
      removeStoppedManagedProcess(request);
      refetchUserProcessesIfActive();
      return;
    }
    refetchRuntimeManagementAfterAction();
  },
  onError: error => {
    actionErrorMessage.value = formatProcessActionError(error);
    refetchRuntimeManagementAfterAction();
  },
  onSettled: () => {
    activeManagedProcessAction.value = null;
  }
});

const overview = computed(() => overviewQuery.data.value);
const summary = computed(() => overview.value?.summary);
const processPage = computed(() => userProcessQuery.data.value);
const processRows = computed(() => processPage.value?.items ?? []);
const isLoading = computed(() => overviewQuery.isLoading.value);
const isFetching = computed(() => overviewQuery.isFetching.value);
const isUserProcessFetching = computed(() => userProcessQuery.isFetching.value);
const hasUserProcessQuery = computed(() => activeUserKeyword.value.trim().length > 0);
const metricHistory = computed(() => metricsQuery.data.value);
const metricSamples = computed(() => metricHistory.value?.samples ?? []);
const metricErrorMessage = computed(() => {
  const error = metricsQuery.error.value;
  if (!error) {
    return "";
  }
  if (error instanceof BackendApiError) {
    return `${error.message}（${error.code}）`;
  }
  return error.message || "监控历史加载失败";
});
const errorMessage = computed(() => {
  const error = overviewQuery.error.value;
  if (!error) {
    return "";
  }
  if (error instanceof BackendApiError) {
    return `${error.message}（${error.code}）`;
  }
  return error.message || "运行管理数据加载失败";
});
const userProcessErrorMessage = computed(() => {
  const error = userProcessQuery.error.value;
  if (!error) {
    return "";
  }
  if (error instanceof BackendApiError) {
    return `${error.message}（${error.code}）`;
  }
  return error.message || "用户 TestAgent 进程加载失败";
});
const batchManagedProcessResultMessage = computed(() => {
  const result = batchManagedProcessResult.value;
  if (!result) {
    return "";
  }
  const actionLabel = result.action === "restart" ? "重启" : "关闭";
  const summary = `批量${actionLabel}完成：成功 ${result.succeeded}，失败 ${result.failures.length}。`;
  if (result.failures.length === 0) {
    return summary;
  }
  const details = result.failures
    .map(failure => `${failure.label}：${failure.message}`)
    .join("；");
  return `${summary}失败项：${details}`;
});
const batchManagedProcessProgressMessage = computed(() => {
  const progress = batchManagedProcessProgress.value;
  if (!progress) {
    return "";
  }
  const actionLabel = progress.action === "restart" ? "重启" : "关闭";
  const current = progress.targets[progress.nextIndex];
  const currentMessage = current ? `；当前：${current.label}` : "";
  return `批量${actionLabel}进行中：已完成 ${progress.nextIndex} / ${progress.targets.length}，成功 ${progress.succeeded}，失败 ${progress.failures.length}${currentMessage}`;
});
const batchManagedProcessTargetStatuses = computed(() => {
  const statuses = new Map<string, BatchManagedProcessTargetStatus>();
  const progress = batchManagedProcessProgress.value;
  if (progress) {
    const actionLabel = progress.action === "restart" ? "重启" : "关闭";
    const failures = new Map(progress.failures.map(failure => [failure.key, failure]));
    progress.targets.forEach((target, index) => {
      const failure = failures.get(target.key);
      if (failure) {
        statuses.set(target.key, { label: `${actionLabel}失败`, state: "failed", detail: failure.message });
      } else if (progress.activeTargetKey === target.key) {
        statuses.set(target.key, { label: `正在${actionLabel}`, state: "running" });
      } else if (index < progress.nextIndex) {
        statuses.set(target.key, { label: `${actionLabel}成功`, state: "succeeded" });
      } else {
        statuses.set(target.key, { label: `等待${actionLabel}`, state: "pending" });
      }
    });
    return statuses;
  }

  const result = batchManagedProcessResult.value;
  if (!result) {
    return statuses;
  }
  const actionLabel = result.action === "restart" ? "重启" : "关闭";
  const failures = new Map(result.failures.map(failure => [failure.key, failure]));
  result.targets.forEach(target => {
    const failure = failures.get(target.key);
    statuses.set(target.key, failure
      ? { label: `${actionLabel}失败`, state: "failed", detail: failure.message }
      : { label: `${actionLabel}成功`, state: "succeeded" });
  });
  return statuses;
});
const totalPages = computed(() => Math.max(1, Math.ceil((processPage.value?.total ?? 0) / userProcessSize.value)));
const summaryCards = computed(() => {
  const item = summary.value;
  return [
    { label: "服务器", value: item?.linuxServers ?? 0, extra: `${item?.readyLinuxServers ?? 0} READY` },
    { label: "后端进程", value: item?.backendProcesses ?? 0, extra: `${item?.readyBackendProcesses ?? 0} READY` },
    { label: "容器", value: item?.containers ?? 0, extra: `${item?.readyContainers ?? 0} READY` },
    { label: "管理进程", value: item?.managers ?? 0, extra: `${item?.connectedManagers ?? 0} CONNECTED` },
    { label: "TestAgent 进程", value: item?.opencodeProcesses ?? 0, extra: `${item?.runningOpencodeProcesses ?? 0} RUNNING` },
    { label: "用户绑定", value: item?.userBindings ?? 0, extra: `${item?.managerBackendConnections ?? 0} 连接` }
  ];
});
const serverBackendRows = computed<RuntimeServerBackendRow[]>(() => {
  const rows = new Map<string, RuntimeServerBackendRow>();
  for (const server of overview.value?.linuxServers ?? []) {
    rows.set(server.linuxServerId, {
      key: `server:${server.linuxServerId}`,
      linuxServerId: server.linuxServerId,
      server
    });
  }
  for (const backend of overview.value?.backendProcesses ?? []) {
    const existing = rows.get(backend.linuxServerId);
    if (existing && !existing.backend) {
      existing.backend = backend;
      existing.linuxServerId = existing.linuxServerId || backend.linuxServerId;
      continue;
    }
    rows.set(backend.linuxServerId, {
      key: `backend:${backend.linuxServerId}`,
      linuxServerId: backend.linuxServerId,
      backend
    });
  }
  return Array.from(rows.values()).sort((left, right) =>
    (left.linuxServerId ?? left.backend?.backendProcessId ?? "").localeCompare(
      right.linuxServerId ?? right.backend?.backendProcessId ?? ""
    )
  );
});
const backendAddressByLinuxServerId = computed(() => {
  const addresses = new Map<string, string>();
  for (const backend of overview.value?.backendProcesses ?? []) {
    const address = extractHostFromUrl(backend.listenUrl);
    if (address && !addresses.has(backend.linuxServerId)) {
      addresses.set(backend.linuxServerId, address);
    }
  }
  return addresses;
});
const containerManagerRows = computed<RuntimeContainerManagerRow[]>(() => {
  const rows = new Map<string, RuntimeContainerManagerRow>();
  for (const container of overview.value?.containers ?? []) {
    rows.set(container.containerId, {
      key: `container:${container.containerId}`,
      containerId: container.containerId,
      linuxServerId: container.linuxServerId,
      container,
      managedProcesses: []
    });
  }
  for (const manager of overview.value?.managers ?? []) {
    const existing = rows.get(manager.containerId);
    if (existing) {
      existing.key = `container:${manager.containerId}`;
      existing.manager = manager;
      existing.managedProcesses = manager.managedProcesses ?? [];
      existing.linuxServerId = existing.linuxServerId || manager.linuxServerId;
      continue;
    }
    rows.set(manager.containerId, {
      key: `manager:${manager.managerId}`,
      containerId: manager.containerId,
      linuxServerId: manager.linuxServerId,
      manager,
      managedProcesses: manager.managedProcesses ?? []
    });
  }
  return Array.from(rows.values()).sort((left, right) => {
    const leftServer = left.linuxServerId ?? "";
    const rightServer = right.linuxServerId ?? "";
    if (leftServer !== rightServer) {
      return leftServer.localeCompare(rightServer);
    }
    return (left.containerId ?? left.manager?.managerId ?? "").localeCompare(right.containerId ?? right.manager?.managerId ?? "");
  });
});
const ownedManagedProcessTargets = computed<BatchManagedProcessTarget[]>(() => {
  const targets = new Map<string, BatchManagedProcessTarget>();
  for (const row of containerManagerRows.value) {
    const containerId = runtimeRowContainerId(row);
    if (!containerId) {
      continue;
    }
    for (const process of ownedProcesses(row)) {
      if (!Number.isFinite(process.port)) {
        continue;
      }
      const key = `${containerId}:${process.port}`;
      targets.set(key, {
        key,
        containerId,
        port: process.port,
        label: `${processOwner(process)}（${containerId}:${process.port}）`,
        pid: process.pid,
        startedAt: process.startedAt
      });
    }
  }
  return Array.from(targets.values());
});
const selectedManagedProcessTargets = computed(() =>
  ownedManagedProcessTargets.value.filter(target => selectedManagedProcessKeys.value.has(target.key))
);
const allOwnedManagedProcessesSelected = computed(() =>
  ownedManagedProcessTargets.value.length > 0
    && selectedManagedProcessTargets.value.length === ownedManagedProcessTargets.value.length
);
const someOwnedManagedProcessesSelected = computed(() =>
  selectedManagedProcessTargets.value.length > 0 && !allOwnedManagedProcessesSelected.value
);

watch(ownedManagedProcessTargets, targets => {
  // 5 秒 overview 刷新时只剔除已经消失的目标，保留仍有效的人工选择。
  const availableKeys = new Set(targets.map(target => target.key));
  const retained = new Set(Array.from(selectedManagedProcessKeys.value).filter(key => availableKeys.has(key)));
  if (retained.size !== selectedManagedProcessKeys.value.size) {
    selectedManagedProcessKeys.value = retained;
  }
});

watch(overview, value => {
  if (value && batchManagedProcessProgress.value) {
    expandBatchManagedProcessTargets(batchManagedProcessProgress.value.targets);
    void resumePersistedBatchManagedProcessAction();
  }
});

onMounted(() => {
  restoreBatchManagedProcessProgress();
});

function applyFilters() {
  activeFilters.value = { ...draftFilters.value };
  page.value = 1;
}

function clearFilters() {
  draftFilters.value = { status: "", linuxServerId: "", containerId: "" };
  activeFilters.value = { status: "", linuxServerId: "", containerId: "" };
  page.value = 1;
}

function refresh() {
  void overviewQuery.refetch();
  if (activeUserKeyword.value.trim()) {
    void userProcessQuery.refetch();
  }
  if (selectedMetricsTarget.value) {
    void metricsQuery.refetch();
  }
}

function queryUserProcesses() {
  const nextKeyword = userKeywordDraft.value.trim();
  const repeatsCurrentQuery = nextKeyword.length > 0
    && nextKeyword === activeUserKeyword.value.trim()
    && userProcessPage.value === 1;
  activeUserKeyword.value = nextKeyword;
  userProcessPage.value = 1;
  if (repeatsCurrentQuery) {
    // Query Key 未变化时 Vue Query 不会自动请求；人工再次点击必须绕过旧空结果。
    void userProcessQuery.refetch();
  }
}

function prevPage() {
  userProcessPage.value = Math.max(1, userProcessPage.value - 1);
}

function nextPage() {
  userProcessPage.value = Math.min(totalPages.value, userProcessPage.value + 1);
}

function statusClass(status?: string | null) {
  const normalized = (status ?? "").toUpperCase();
  if (["READY", "RUNNING", "CONNECTED"].includes(normalized)) {
    return "is-ok";
  }
  if (["STARTING", "CONNECTING"].includes(normalized)) {
    return "is-pending";
  }
  if (["FAILED", "UNHEALTHY", "OFFLINE", "DISCONNECTED"].includes(normalized)) {
    return "is-bad";
  }
  if (["STOPPED", "UNKNOWN"].includes(normalized)) {
    return "is-muted";
  }
  return "is-neutral";
}

function formatDate(value?: string | null) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  }).format(new Date(value));
}

function formatNullable(value?: string | number | null) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  return value;
}

function extractHostFromUrl(value?: string | null) {
  if (!value) {
    return "";
  }
  try {
    return new URL(value).hostname || value;
  } catch {
    const withoutScheme = value.replace(/^[a-z][a-z\d+.-]*:\/\//i, "");
    const authority = withoutScheme.split(/[/?#]/, 1)[0] ?? "";
    if (authority.startsWith("[") && authority.includes("]")) {
      return authority.slice(1, authority.indexOf("]"));
    }
    return authority.split(":")[0] || value;
  }
}

function backendNetworkAddress(row: RuntimeServerBackendRow) {
  return String(formatNullable(extractHostFromUrl(row.backend?.listenUrl)));
}

function containerManagerNetworkAddress(row: RuntimeContainerManagerRow) {
  if (!row.linuxServerId) {
    return "-";
  }
  return String(formatNullable(backendAddressByLinuxServerId.value.get(row.linuxServerId)));
}

function compactRecord(value?: Record<string, unknown> | null) {
  if (!value || Object.keys(value).length === 0) {
    return "-";
  }
  return Object.entries(value)
    .slice(0, 4)
    .map(([key, item]) => `${key}: ${String(item)}`)
    .join(" / ");
}

function selectContainer(containerId: string) {
  if (selectedMetricsTarget.value?.type === "container" && selectedMetricsTarget.value.id === containerId) {
    selectedMetricsTarget.value = null;
  } else {
    selectedMetricsTarget.value = { type: "container", id: containerId, title: "容器监控趋势" };
  }
}

function selectBackendServer(linuxServerId: string) {
  if (selectedMetricsTarget.value?.type === "backend" && selectedMetricsTarget.value.id === linuxServerId) {
    selectedMetricsTarget.value = null;
  } else {
    selectedMetricsTarget.value = { type: "backend", id: linuxServerId, title: "后端监控趋势" };
  }
}

function formatPercent(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "-";
  }
  const rounded = Math.round(value * 10) / 10;
  return `${Number.isInteger(rounded) ? rounded.toFixed(0) : rounded.toFixed(1)}%`;
}

function formatBytes(value?: number | null) {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return "-";
  }
  const units = ["B", "KiB", "MiB", "GiB", "TiB"];
  let next = value;
  let unitIndex = 0;
  while (Math.abs(next) >= 1024 && unitIndex < units.length - 1) {
    next /= 1024;
    unitIndex += 1;
  }
  if (unitIndex === 0) {
    return `${Math.round(next)} ${units[unitIndex]}`;
  }
  return `${next.toFixed(next >= 10 ? 1 : 2)} ${units[unitIndex]}`;
}

function formatBytesRate(value?: number | null) {
  const formatted = formatBytes(value);
  return formatted === "-" ? "-" : `${formatted}/s`;
}

function formatLoad(value?: number | null) {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return "-";
  }
  return value.toFixed(value >= 10 ? 1 : 2);
}

function formatCountPair(value?: number | null, max?: number | null) {
  const current = value === null || value === undefined || !Number.isFinite(value) ? "-" : String(Math.round(value));
  const upper = max === null || max === undefined || !Number.isFinite(max) ? "-" : String(Math.round(max));
  return `${current}/${upper}`;
}

function backendMemoryTotal(backend?: OpencodeRuntimeBackendProcess) {
  return backend?.memoryTotalBytes ?? backend?.memoryMaxBytes;
}

function backendJvmHeapUsed(backend?: OpencodeRuntimeBackendProcess) {
  return backend?.jvmHeapUsedBytes ?? backend?.jvmMemoryUsedBytes;
}

function backendJvmHeapMax(backend?: OpencodeRuntimeBackendProcess) {
  return backend?.jvmHeapMaxBytes ?? backend?.jvmMemoryMaxBytes;
}

function formatMetricsSource(value?: string | null) {
  if (!value) {
    return "-";
  }
  if (value === "unavailable") {
    return "不可采集";
  }
  return value;
}

function activeRowClass(type: "container" | "backend", id: string) {
  return selectedMetricsTarget.value?.type === type && selectedMetricsTarget.value.id === id ? "is-selected" : "";
}

function portRange(row: RuntimeContainerManagerRow) {
  if (!row.container) {
    return "-";
  }
  return `${row.container.portStart}-${row.container.portEnd}`;
}

function capacityText(row: RuntimeContainerManagerRow) {
  if (!row.container) {
    return "-";
  }
  return `${row.container.currentProcesses}/${row.container.maxProcesses}`;
}

function heartbeatText(row: RuntimeContainerManagerRow) {
  return formatDate(row.manager?.lastHeartbeatAt ?? row.container?.lastHeartbeatAt);
}

function ownedProcesses(row: RuntimeContainerManagerRow) {
  return row.managedProcesses.filter(process => process.ownership === "BOUND");
}

function ghostProcesses(row: RuntimeContainerManagerRow) {
  return row.managedProcesses.filter(process => process.ownership !== "BOUND");
}

function hasManagedProcessCountMismatch(row: RuntimeContainerManagerRow) {
  return row.container !== undefined && row.container.currentProcesses !== row.managedProcesses.length;
}

function processOwner(process: OpencodeRuntimeManagedProcess) {
  return process.username || process.userId || "-";
}

function processBinding(process: OpencodeRuntimeManagedProcess) {
  if (process.bindingAgentId || process.bindingStatus) {
    return `${process.bindingAgentId ?? "-"} / ${process.bindingStatus ?? "-"}`;
  }
  return "-";
}

function processHealth(process: OpencodeRuntimeManagedProcess) {
  // manager 快照存在但平台没有进程记录时，后端尚未执行平台 HTTP 健康检查。
  if (!process.processId) {
    return "未执行 HTTP 健康检查";
  }
  return process.healthMessage || process.processStatus || "-";
}

function userProcessActualStatus(process: OpencodeRuntimeProcess) {
  if (process.managerStatus || process.healthStatus) {
    return `${process.managerStatus ?? "-"} / ${process.healthStatus ?? "-"}`;
  }
  return process.status;
}

function canRestartUserProcess(process: OpencodeRuntimeProcess) {
  return process.restartable === true && Boolean(process.containerId) && Number.isFinite(process.port);
}

function formatProcessActionError(error: unknown) {
  if (error instanceof BackendApiError) {
    return `${error.message}（${error.code}）`;
  }
  return error instanceof Error ? error.message : "进程操作失败";
}

function isMessageBoxCancellation(error: unknown) {
  return error === "cancel" || error === "close";
}

function managedProcessTargetKey(row: RuntimeContainerManagerRow, process: OpencodeRuntimeManagedProcess) {
  const containerId = runtimeRowContainerId(row);
  if (!containerId || !Number.isFinite(process.port)) {
    return "";
  }
  return `${containerId}:${process.port}`;
}

function batchManagedProcessTargetStatus(row: RuntimeContainerManagerRow, process: OpencodeRuntimeManagedProcess) {
  const key = managedProcessTargetKey(row, process);
  return key ? batchManagedProcessTargetStatuses.value.get(key) : undefined;
}

function batchManagedProcessTargetStatusTitle(row: RuntimeContainerManagerRow, process: OpencodeRuntimeManagedProcess) {
  const status = batchManagedProcessTargetStatus(row, process);
  if (!status) {
    return undefined;
  }
  return status.detail ? `${status.label}：${status.detail}` : status.label;
}

function expandBatchManagedProcessTargets(targets: BatchManagedProcessTarget[]) {
  const containerIds = new Set(targets.map(target => target.containerId));
  const expanded = new Set(expandedRuntimeRowKeys.value);
  for (const row of containerManagerRows.value) {
    const containerId = runtimeRowContainerId(row);
    if (containerId && containerIds.has(containerId)) {
      expanded.add(row.key);
    }
  }
  expandedRuntimeRowKeys.value = expanded;
}

function isOwnedManagedProcessSelected(row: RuntimeContainerManagerRow, process: OpencodeRuntimeManagedProcess) {
  const key = managedProcessTargetKey(row, process);
  return Boolean(key) && selectedManagedProcessKeys.value.has(key);
}

function toggleOwnedManagedProcessSelection(
  row: RuntimeContainerManagerRow,
  process: OpencodeRuntimeManagedProcess,
  event: Event
) {
  if (batchManagedProcessAction.value || managedProcessActionMutation.isPending.value) {
    return;
  }
  const key = managedProcessTargetKey(row, process);
  if (!key) {
    return;
  }
  const next = new Set(selectedManagedProcessKeys.value);
  if ((event.target as HTMLInputElement).checked) {
    next.add(key);
  } else {
    next.delete(key);
  }
  selectedManagedProcessKeys.value = next;
  batchManagedProcessResult.value = null;
}

function toggleAllOwnedManagedProcesses(event: Event) {
  if (batchManagedProcessAction.value || managedProcessActionMutation.isPending.value) {
    return;
  }
  selectedManagedProcessKeys.value = (event.target as HTMLInputElement).checked
    ? new Set(ownedManagedProcessTargets.value.map(target => target.key))
    : new Set();
  batchManagedProcessResult.value = null;
}

async function runBatchManagedProcessAction(action: ManagedProcessActionKind) {
  const targets = [...selectedManagedProcessTargets.value];
  if (targets.length === 0 || batchManagedProcessAction.value || managedProcessActionMutation.isPending.value) {
    return;
  }
  const actionLabel = action === "restart" ? "重启" : "关闭";
  const actionDetail = action === "restart"
    ? "每个进程都会经过现有停止、重新拉起和健康检查流程"
    : "关闭后，对应用户的服务端 OpenCode 状态和工作区会隐藏，可由用户头像菜单或超管重启恢复";
  // 确认框打开期间也锁住入口，避免双击产生两批相同控制命令。
  batchManagedProcessAction.value = action;
  try {
    await ElMessageBox.confirm(
      `确认逐个${actionLabel}选中的 ${targets.length} 个有主用户 OpenCode 进程吗？${actionDetail}；单个失败不会中断其余进程。`,
      `批量${actionLabel}用户 OpenCode`,
      { type: "warning", confirmButtonText: `批量${actionLabel}`, cancelButtonText: "取消" }
    );
  } catch (error) {
    batchManagedProcessAction.value = null;
    if (!isMessageBoxCancellation(error)) {
      actionErrorMessage.value = error instanceof Error ? error.message : `无法打开批量${actionLabel}确认框`;
    }
    return;
  }

  actionErrorMessage.value = "";
  batchManagedProcessResult.value = null;
  setBatchManagedProcessProgress({
    version: 1,
    initiatedByUserId: props.currentUser?.userId ?? "",
    action,
    targets,
    nextIndex: 0,
    succeeded: 0,
    failures: [],
    activeTargetKey: null,
    retryAfter: null,
    startedAt: Date.now()
  });
  expandBatchManagedProcessTargets(targets);
  await executeBatchManagedProcessProgress();
}

function batchProgressStorageKey() {
  const userId = props.currentUser?.userId?.trim();
  return userId ? `${BATCH_PROGRESS_STORAGE_PREFIX}:${userId}` : "";
}

function setBatchManagedProcessProgress(progress: BatchManagedProcessProgress) {
  batchManagedProcessProgress.value = progress;
  const key = batchProgressStorageKey();
  if (!key || typeof localStorage === "undefined") {
    return;
  }
  try {
    localStorage.setItem(key, JSON.stringify(progress));
  } catch {
    // 隐私模式或配额异常不阻断当前页面内仍可继续的批量操作。
  }
}

function clearPersistedBatchManagedProcessProgress() {
  const key = batchProgressStorageKey();
  if (key && typeof localStorage !== "undefined") {
    localStorage.removeItem(key);
  }
  batchManagedProcessProgress.value = null;
}

function restoreBatchManagedProcessProgress() {
  const key = batchProgressStorageKey();
  if (!key || typeof localStorage === "undefined") {
    return;
  }
  try {
    const parsed = JSON.parse(localStorage.getItem(key) ?? "null") as BatchManagedProcessProgress | null;
    if (!isValidBatchManagedProcessProgress(parsed)) {
      localStorage.removeItem(key);
      return;
    }
    batchManagedProcessAction.value = parsed.action;
    batchManagedProcessResult.value = null;
    batchManagedProcessProgress.value = parsed;
    expandBatchManagedProcessTargets(parsed.targets);
    if (overview.value) {
      void resumePersistedBatchManagedProcessAction();
    }
  } catch {
    localStorage.removeItem(key);
  }
}

function isValidBatchManagedProcessProgress(value: BatchManagedProcessProgress | null): value is BatchManagedProcessProgress {
  return value?.version === 1
    && value.initiatedByUserId === props.currentUser?.userId
    && (value.action === "restart" || value.action === "stop")
    && Array.isArray(value.targets)
    && value.targets.length > 0
    && Number.isInteger(value.nextIndex)
    && value.nextIndex >= 0
    && value.nextIndex <= value.targets.length
    && Array.isArray(value.failures);
}

async function resumePersistedBatchManagedProcessAction() {
  const progress = batchManagedProcessProgress.value;
  if (!progress || batchRunnerActive.value) {
    return;
  }
  if (progress.activeTargetKey) {
    const target = progress.targets[progress.nextIndex];
    const current = ownedManagedProcessTargets.value.find(item => item.key === target?.key);
    const commandAlreadyApplied = progress.action === "stop"
      ? !current
      : Boolean(current && (
          (target?.pid != null && current.pid !== target.pid)
          || (target?.startedAt && current.startedAt !== target.startedAt)
        ));
    if (commandAlreadyApplied) {
      setBatchManagedProcessProgress({
        ...progress,
        nextIndex: progress.nextIndex + 1,
        succeeded: progress.succeeded + 1,
        activeTargetKey: null,
        retryAfter: null
      });
    } else if ((progress.retryAfter ?? 0) > Date.now()) {
      window.setTimeout(
        () => void resumePersistedBatchManagedProcessAction(),
        Math.max(1, (progress.retryAfter ?? 0) - Date.now())
      );
      return;
    }
  }
  await executeBatchManagedProcessProgress();
}

async function executeBatchManagedProcessProgress() {
  if (batchRunnerActive.value) {
    return;
  }
  batchRunnerActive.value = true;
  try {
    // 控制命令串行发送；每项发出前持久化游标，刷新页面后可核对结果并继续剩余目标。
    while (batchManagedProcessProgress.value) {
      const progress = batchManagedProcessProgress.value;
      if (progress.nextIndex >= progress.targets.length) {
        finishBatchManagedProcessProgress(progress);
        return;
      }
      const target = progress.targets[progress.nextIndex];
      setBatchManagedProcessProgress({
        ...progress,
        activeTargetKey: target.key,
        retryAfter: Date.now() + BATCH_RETRY_GRACE_MS
      });
      let failure: BatchManagedProcessFailure | null = null;
      try {
        if (progress.action === "restart") {
          await api.restartOpencodeRuntimeManagedProcess(target.containerId, target.port);
        } else {
          await api.stopOpencodeRuntimeManagedProcess(target.containerId, target.port);
          removeStoppedManagedProcess({
            action: progress.action,
            containerId: target.containerId,
            port: target.port
          });
        }
      } catch (error) {
        failure = { key: target.key, label: target.label, message: formatProcessActionError(error) };
      }
      const latest = batchManagedProcessProgress.value;
      if (!latest) {
        return;
      }
      setBatchManagedProcessProgress({
        ...latest,
        nextIndex: latest.nextIndex + 1,
        succeeded: latest.succeeded + (failure ? 0 : 1),
        failures: failure ? [...latest.failures, failure] : latest.failures,
        activeTargetKey: null,
        retryAfter: null
      });
    }
  } finally {
    batchRunnerActive.value = false;
  }
}

function finishBatchManagedProcessProgress(progress: BatchManagedProcessProgress) {
  batchManagedProcessResult.value = {
    action: progress.action,
    total: progress.targets.length,
    succeeded: progress.succeeded,
    failures: progress.failures,
    targets: progress.targets
  };
  selectedManagedProcessKeys.value = new Set(progress.failures.map(failure => failure.key));
  batchManagedProcessAction.value = null;
  clearPersistedBatchManagedProcessProgress();
  refetchRuntimeManagementAfterAction();
}

function runUserProcessRestart(process: OpencodeRuntimeProcess) {
  if (batchManagedProcessAction.value) {
    return;
  }
  if (!canRestartUserProcess(process)) {
    actionErrorMessage.value = "该用户进程缺少容器或端口，无法重启";
    return;
  }
  managedProcessActionMutation.mutate({ action: "restart", containerId: process.containerId, port: process.port });
}

function isUserProcessRestartRunning(process: OpencodeRuntimeProcess) {
  const active = activeManagedProcessAction.value;
  return active?.action === "restart" && active.containerId === process.containerId && active.port === process.port;
}

function refetchUserProcessesIfActive() {
  if (activeUserKeyword.value.trim()) {
    void userProcessQuery.refetch();
  }
}

function refetchRuntimeManagementAfterAction() {
  void overviewQuery.refetch();
  refetchUserProcessesIfActive();
}

function removeStoppedManagedProcess(request: ManagedProcessActionRequest) {
  queryClient.setQueryData<OpencodeRuntimeManagementOverview>(overviewQueryKey.value, old => {
    if (!old) {
      return old;
    }
    let removed = false;
    const managers = old.managers.map(manager => {
      if (manager.containerId !== request.containerId || !manager.managedProcesses?.length) {
        return manager;
      }
      const managedProcesses = manager.managedProcesses.filter(process => process.port !== request.port);
      if (managedProcesses.length === manager.managedProcesses.length) {
        return manager;
      }
      removed = true;
      return { ...manager, managedProcesses };
    });
    if (!removed) {
      return old;
    }
    const containers = old.containers.map(container => {
      if (container.containerId !== request.containerId) {
        return container;
      }
      const currentProcesses = Math.max(0, container.currentProcesses - 1);
      const availableCapacity = Math.min(container.maxProcesses, container.availableCapacity + 1);
      return { ...container, currentProcesses, availableCapacity };
    });
    return { ...old, containers, managers };
  });
}

function runManagedProcessAction(
  row: RuntimeContainerManagerRow,
  process: OpencodeRuntimeManagedProcess,
  action: ManagedProcessActionKind
) {
  const containerId = runtimeRowContainerId(row);
  if (!containerId || !Number.isFinite(process.port)) {
    actionErrorMessage.value = "缺少容器或端口，无法发送进程操作";
    return;
  }
  managedProcessActionMutation.mutate({ action, containerId, port: process.port });
}

function isManagedProcessActionRunning(
  row: RuntimeContainerManagerRow,
  process: OpencodeRuntimeManagedProcess,
  action: ManagedProcessActionKind
) {
  const containerId = runtimeRowContainerId(row);
  const active = activeManagedProcessAction.value;
  return active?.action === action && active.containerId === containerId && active.port === process.port;
}

function isManagedProcessActionDisabled(row: RuntimeContainerManagerRow, process: OpencodeRuntimeManagedProcess) {
  const containerId = runtimeRowContainerId(row);
  return Boolean(batchManagedProcessAction.value)
    || managedProcessActionMutation.isPending.value
    || !containerId
    || !Number.isFinite(process.port);
}

function runtimeRowContainerId(row: RuntimeContainerManagerRow) {
  return row.containerId ?? row.container?.containerId ?? row.manager?.containerId;
}

function isRuntimeRowExpanded(row: RuntimeContainerManagerRow) {
  return expandedRuntimeRowKeys.value.has(row.key);
}

function toggleRuntimeRow(row: RuntimeContainerManagerRow) {
  const next = new Set(expandedRuntimeRowKeys.value);
  if (next.has(row.key)) {
    next.delete(row.key);
  } else {
    next.add(row.key);
  }
  expandedRuntimeRowKeys.value = next;
}

function startResize(e: MouseEvent) {
  const handle = e.target as HTMLElement;
  const th = handle.parentElement;
  if (!th) return;

  const startX = e.clientX;
  const startWidth = th.offsetWidth;

  handle.classList.add("is-resizing");

  const doDrag = (moveEvent: MouseEvent) => {
    const newWidth = Math.max(startWidth + (moveEvent.clientX - startX), 40);
    th.style.width = `${newWidth}px`;
    th.style.minWidth = `${newWidth}px`;
    th.style.maxWidth = `${newWidth}px`;
  };

  const stopDrag = () => {
    handle.classList.remove("is-resizing");
    document.removeEventListener("mousemove", doDrag);
    document.removeEventListener("mouseup", stopDrag);
  };

  document.addEventListener("mousemove", doDrag);
  document.addEventListener("mouseup", stopDrag);
}
</script>

<template>
  <section class="ta-runtime-management" @dragstart.prevent>
    <div v-if="!hasSuperAdmin" class="ta-runtime-placeholder">当前账号无运行管理权限</div>
    <template v-else>
      <div class="ta-runtime-toolbar">
        <el-select v-model="draftFilters.status" size="small" clearable filterable placeholder="进程状态" class="ta-runtime-filter">
          <el-option v-for="status in processStatusOptions" :key="status" :label="status" :value="status" />
        </el-select>
        <el-input v-model="draftFilters.linuxServerId" size="small" clearable placeholder="服务器ID" class="ta-runtime-filter" />
        <el-input v-model="draftFilters.containerId" size="small" clearable placeholder="容器 ID" class="ta-runtime-filter" />
        <el-button size="small" type="primary" :icon="Search" :loading="isFetching" @click="applyFilters">查询</el-button>
        <el-button size="small" :icon="Refresh" :loading="isFetching" @click="refresh">刷新</el-button>
        <el-button size="small" @click="clearFilters">清空</el-button>
      </div>

      <div v-if="errorMessage || userProcessErrorMessage || actionErrorMessage" class="ta-runtime-alert" role="alert">
        {{ errorMessage || userProcessErrorMessage || actionErrorMessage }}
      </div>
      <div
        v-if="batchManagedProcessResultMessage"
        :class="['ta-runtime-alert', batchManagedProcessResult?.failures.length ? 'is-warning' : 'is-success']"
        :role="batchManagedProcessResult?.failures.length ? 'alert' : 'status'"
      >
        {{ batchManagedProcessResultMessage }}
      </div>
      <div v-if="batchManagedProcessProgressMessage" class="ta-runtime-batch-progress" role="status">
        <span>{{ batchManagedProcessProgressMessage }}</span>
        <progress
          :value="batchManagedProcessProgress?.nextIndex ?? 0"
          :max="batchManagedProcessProgress?.targets.length ?? 1"
          aria-label="批量 OpenCode 进程操作进度"
        ></progress>
        <small>刷新页面后会自动核对当前项，并继续未执行的用户。</small>
      </div>

      <div v-if="isLoading" class="ta-runtime-placeholder">正在加载运行状态...</div>
      <template v-else>
        <div class="ta-runtime-summary" aria-label="运行管理概览">
          <div v-for="card in summaryCards" :key="card.label" class="ta-runtime-summary-item">
            <span class="ta-runtime-summary-label">{{ card.label }}</span>
            <strong class="ta-runtime-summary-value">{{ card.value }}</strong>
            <span class="ta-runtime-summary-extra">{{ card.extra }}</span>
          </div>
        </div>

        <section class="ta-runtime-section">
          <header class="ta-runtime-section-header">
            <h4>拓扑状态</h4>
            <span>生成时间 {{ formatDate(overview?.generatedAt) }}</span>
          </header>
          <div class="ta-runtime-grid">
            <div class="ta-runtime-block is-wide">
              <h5>服务器 / Java 进程</h5>
              <div class="ta-runtime-block-scroll">
                <table class="ta-runtime-table">
                  <thead>
                    <tr>
                      <th style="width: 120px; position: relative;">服务器<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 120px; position: relative;">IP地址<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 120px; position: relative;">Java 进程<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 140px; position: relative;">版本<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 90px; position: relative;">服务器状态<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 90px; position: relative;">Java 状态<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 130px; position: relative;">CPU<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 190px; position: relative;">内存<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 150px; position: relative;">磁盘<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 180px; position: relative;">JVM<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 140px; position: relative;">心跳<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 120px; position: relative;">容量<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 170px; position: relative;">操作<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="!serverBackendRows.length"><td colspan="13" class="is-empty">暂无服务器 / Java 进程</td></tr>
                    <tr
                      v-for="row in serverBackendRows"
                      :key="row.key"
                      :class="row.backend ? activeRowClass('backend', row.backend.linuxServerId) : ''"
                      :tabindex="row.backend ? 0 : undefined"
                      @click="row.backend ? selectBackendServer(row.backend.linuxServerId) : undefined"
                      @keydown.enter="row.backend ? selectBackendServer(row.backend.linuxServerId) : undefined"
                    >
                      <td :title="row.linuxServerId ?? undefined">{{ formatNullable(row.linuxServerId) }}</td>
                      <td :title="backendNetworkAddress(row)">{{ backendNetworkAddress(row) }}</td>
                      <td class="is-compact" :title="row.backend?.backendProcessId ?? undefined">{{ formatNullable(row.backend?.backendProcessId) }}</td>
                      <td class="is-compact">{{ formatNullable(row.backend?.buildVersion) }}</td>
                      <td>
                        <span v-if="row.server" :class="['ta-status', statusClass(row.server.status)]">{{ row.server.status }}</span>
                        <span v-else>-</span>
                      </td>
                      <td>
                        <span v-if="row.backend" :class="['ta-status', statusClass(row.backend.status)]">{{ row.backend.status }}</span>
                        <span v-else>-</span>
                      </td>
                      <td class="is-metric-stack">
                        <span>整机 {{ formatPercent(row.backend?.cpuUsagePercent) }} / {{ row.backend?.cpuCoreCount ?? "-" }}核</span>
                        <span>Java {{ formatPercent(row.backend?.jvmProcessCpuUsagePercent) }} / {{ formatLoad(row.backend?.jvmProcessCpuCoreUsage) }}核</span>
                      </td>
                      <td class="is-metric-stack">
                        <span>整机 {{ formatBytes(row.backend?.memoryUsedBytes) }} / {{ formatBytes(backendMemoryTotal(row.backend)) }}</span>
                        <span>可用 {{ formatBytes(row.backend?.memoryAvailableBytes) }} · RSS {{ formatBytes(row.backend?.jvmProcessResidentMemoryBytes) }}</span>
                      </td>
                      <td class="is-metric-stack">
                        <span>{{ formatPercent(row.backend?.diskUsagePercent) }} / {{ formatBytes(row.backend?.diskUsedBytes) }}</span>
                        <span>可用 {{ formatBytes(row.backend?.diskAvailableBytes) }}</span>
                      </td>
                      <td class="is-metric-stack">
                        <span>Heap {{ formatBytes(backendJvmHeapUsed(row.backend)) }} / {{ formatBytes(backendJvmHeapMax(row.backend)) }}</span>
                        <span>线程 {{ row.backend?.jvmThreadsLive ?? "-" }}/{{ row.backend?.jvmThreadsPeak ?? "-" }} · FD {{ formatCountPair(row.backend?.jvmOpenFileDescriptorCount, row.backend?.jvmMaxFileDescriptorCount) }}</span>
                      </td>
                      <td>{{ formatDate(row.backend?.lastHeartbeatAt ?? row.server?.lastHeartbeatAt) }}</td>
                      <td class="is-compact">{{ compactRecord(row.server?.capacitySummary) }}</td>
                      <td>
                        <div v-if="row.backend" class="ta-runtime-process-actions">
                          <button
                            type="button"
                            class="ta-runtime-trend-button"
                            :aria-label="`查看 ${row.backend.linuxServerId} 后端监控趋势`"
                            :title="`查看 ${row.backend.linuxServerId} 后端监控趋势`"
                            @click.stop="selectBackendServer(row.backend.linuxServerId)"
                            @keydown.enter.stop
                          >趋势</button>
                        </div>
                        <span v-else>-</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 后端趋势图按稳定服务器身份归并，JVM 样本跨 Java 进程重启连续。 -->
            <div v-if="selectedMetricsTarget && selectedMetricsTarget.type === 'backend'" class="ta-runtime-metrics-panel">
              <header class="ta-runtime-section-header">
                <h4>{{ selectedMetricsTarget.title }}</h4>
                <div class="ta-runtime-metrics-actions">
                  <el-radio-group v-model="selectedWindowMinutes" size="small">
                    <el-radio-button :value="1">1分钟</el-radio-button>
                    <el-radio-button :value="30">30分钟</el-radio-button>
                    <el-radio-button :value="60">1小时</el-radio-button>
                    <el-radio-button :value="360">6小时</el-radio-button>
                    <el-radio-button :value="720">12小时</el-radio-button>
                    <el-radio-button :value="1440">24小时</el-radio-button>
                    <el-radio-button :value="2880">48小时</el-radio-button>
                  </el-radio-group>
                  <span>{{ selectedMetricsTarget.id }} · {{ metricSamples.length }} 点</span>
                </div>
              </header>
              <div v-if="metricErrorMessage" class="ta-runtime-alert" role="alert">{{ metricErrorMessage }}</div>
              <div v-else-if="metricsQuery.isLoading.value" class="ta-runtime-placeholder">正在加载监控趋势...</div>
              <div v-else class="ta-runtime-chart-grid">
                <RuntimeMetricChart
                  title="服务器 CPU / Load"
                  :samples="metricSamples"
                  :series="[
                    { name: 'CPU %', field: 'cpuUsagePercent' },
                    { name: 'load 1m', field: 'loadAverage1m' },
                    { name: 'load 5m', field: 'loadAverage5m' },
                    { name: 'load 15m', field: 'loadAverage15m' }
                  ]"
                />
                <RuntimeMetricChart
                  title="服务器内存 / Swap / 磁盘"
                  :samples="metricSamples"
                  :series="[
                    { name: '内存 %', field: 'memoryUsagePercent' },
                    { name: 'swap %', field: 'swapUsagePercent' },
                    { name: '磁盘 %', field: 'diskUsagePercent' }
                  ]"
                />
                <RuntimeMetricChart
                  title="Java 进程 CPU"
                  :samples="metricSamples"
                  :series="[
                    { name: 'Java CPU %', field: 'jvmProcessCpuUsagePercent' },
                    { name: 'Java CPU cores', field: 'jvmProcessCpuCoreUsage' }
                  ]"
                />
                <RuntimeMetricChart
                  title="Java 进程内存 / RSS"
                  :samples="metricSamples"
                  :series="[
                    { name: 'RSS', field: 'jvmProcessResidentMemoryBytes' },
                    { name: 'peak RSS', field: 'jvmProcessPeakResidentMemoryBytes' },
                    { name: 'virtual', field: 'jvmProcessVirtualMemoryBytes' },
                    { name: 'swap', field: 'jvmProcessSwapBytes' }
                  ]"
                  yAxis-unit="G"
                />
                <RuntimeMetricChart
                  title="JVM Heap / Non-Heap / Direct"
                  :samples="metricSamples"
                  :series="[
                    { name: 'heap used', field: 'jvmHeapUsedBytes' },
                    { name: 'non-heap used', field: 'jvmNonHeapUsedBytes' },
                    { name: 'direct used', field: 'jvmDirectBufferUsedBytes' },
                    { name: 'mapped used', field: 'jvmMappedBufferUsedBytes' }
                  ]"
                  yAxis-unit="G"
                />
                <RuntimeMetricChart
                  title="GC / 线程 / FD"
                  :samples="metricSamples"
                  :series="[
                    { name: 'GC ms', field: 'jvmGcCollectionTimeDeltaMillis' },
                    { name: 'GC count', field: 'jvmGcCollectionCountDelta' },
                    { name: 'live threads', field: 'jvmThreadsLive' },
                    { name: 'open FD', field: 'jvmOpenFileDescriptorCount' }
                  ]"
                />
              </div>
            </div>

            <div class="ta-runtime-block is-wide">
              <div class="ta-runtime-block-header">
                <h5>容器 / 管理进程</h5>
                <div class="ta-runtime-batch-actions">
                  <label class="ta-runtime-select-all">
                    <input
                      type="checkbox"
                      aria-label="全选有主用户 OpenCode 进程"
                      :checked="allOwnedManagedProcessesSelected"
                      :indeterminate="someOwnedManagedProcessesSelected"
                      :disabled="ownedManagedProcessTargets.length === 0 || Boolean(batchManagedProcessAction) || managedProcessActionMutation.isPending.value"
                      @change="toggleAllOwnedManagedProcesses"
                    />
                    <span>全选有主进程</span>
                  </label>
                  <span>已选 {{ selectedManagedProcessTargets.length }} / {{ ownedManagedProcessTargets.length }}</span>
                  <el-button
                    size="small"
                    type="warning"
                    plain
                    :loading="batchManagedProcessAction === 'restart'"
                    :disabled="selectedManagedProcessTargets.length === 0 || Boolean(batchManagedProcessAction) || managedProcessActionMutation.isPending.value"
                    @click="runBatchManagedProcessAction('restart')"
                  >批量重启 OpenCode（{{ selectedManagedProcessTargets.length }}）</el-button>
                  <el-button
                    size="small"
                    type="danger"
                    plain
                    :loading="batchManagedProcessAction === 'stop'"
                    :disabled="selectedManagedProcessTargets.length === 0 || Boolean(batchManagedProcessAction) || managedProcessActionMutation.isPending.value"
                    @click="runBatchManagedProcessAction('stop')"
                  >批量关闭 OpenCode（{{ selectedManagedProcessTargets.length }}）</el-button>
                </div>
              </div>
              <div class="ta-runtime-block-scroll">
                <table class="ta-runtime-table">
                  <thead>
                    <tr>
                      <th class="is-expand" style="width: 32px;"></th>
                      <th style="width: 120px; position: relative;">容器<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 120px; position: relative;">管理进程<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 140px; position: relative;">版本<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 120px; position: relative;">服务器<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 120px; position: relative;">IP地址<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 90px; position: relative;">容器状态<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 90px; position: relative;">管理状态<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 100px; position: relative;">端口池<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 100px; position: relative;">容量<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 80px; position: relative;">来源<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 80px; position: relative;">CPU<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 80px; position: relative;">内存率<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 100px; position: relative;">已用内存<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 80px; position: relative;">协议<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 140px; position: relative;">心跳<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                      <th style="width: 120px; position: relative;">操作<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="!containerManagerRows.length"><td colspan="17" class="is-empty">暂无容器 / 管理进程</td></tr>
                    <template v-for="row in containerManagerRows" :key="row.key">
                      <tr
                        :class="[row.container ? activeRowClass('container', row.container.containerId) : '', { 'is-expanded': isRuntimeRowExpanded(row) }]"
                        tabindex="0"
                        :aria-expanded="isRuntimeRowExpanded(row)"
                        @click="toggleRuntimeRow(row)"
                        @keydown.enter.prevent="toggleRuntimeRow(row)"
                      >
                        <td class="is-expand">
                          <span :class="['ta-runtime-expand-icon', { 'is-expanded': isRuntimeRowExpanded(row) }]" aria-hidden="true"></span>
                        </td>
                         <td class="is-compact" :title="row.container ? `${row.container.containerName || row.container.containerId} (${row.container.containerId})` : undefined" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ formatNullable(row.container?.containerName || row.container?.containerId) }}</td>
                         <td class="is-compact" :title="row.manager?.managerId ?? undefined" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ formatNullable(row.manager?.managerId) }}</td>
                         <td class="is-compact">{{ formatNullable(row.manager?.buildVersion) }}</td>
                         <td :title="row.linuxServerId ?? undefined" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ formatNullable(row.linuxServerId) }}</td>
                         <td :title="containerManagerNetworkAddress(row)" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ containerManagerNetworkAddress(row) }}</td>
                        <td>
                          <span v-if="row.container" :class="['ta-status', statusClass(row.container.status)]">{{ row.container.status }}</span>
                          <span v-else>-</span>
                        </td>
                        <td>
                          <span v-if="row.manager" :class="['ta-status', statusClass(row.manager.connectionStatus)]">{{ row.manager.connectionStatus }}</span>
                          <span v-else>-</span>
                        </td>
                        <td>{{ portRange(row) }}</td>
                        <td>{{ capacityText(row) }}</td>
                        <td>
                          <span class="ta-runtime-help" :title="metricsSourceHelp">
                            {{ row.container ? formatMetricsSource(row.container.metricsSource) : "-" }}
                          </span>
                        </td>
                        <td>{{ formatPercent(row.container?.cpuUsagePercent) }}</td>
                        <td>{{ formatPercent(row.container?.memoryUsagePercent) }}</td>
                        <td>{{ formatBytes(row.container?.memoryUsedBytes) }}</td>
                        <td>{{ formatNullable(row.manager?.protocolVersion) }}</td>
                        <td>{{ heartbeatText(row) }}</td>
                        <td>
                          <button
                            v-if="row.container"
                            type="button"
                            class="ta-runtime-trend-button"
                            :aria-label="`查看 ${row.container.containerId} 容器监控趋势`"
                            :title="`查看 ${row.container.containerId} 容器监控趋势`"
                            @click.stop="selectContainer(row.container.containerId)"
                            @keydown.enter.stop
                          >
                            趋势
                          </button>
                          <span v-else>-</span>
                        </td>
                      </tr>
                      <tr v-if="isRuntimeRowExpanded(row)" class="ta-runtime-managed-detail">
                        <td colspan="17">
                          <div class="ta-runtime-managed-processes">
                            <div v-if="hasManagedProcessCountMismatch(row)" class="ta-runtime-managed-warning">
                              容量计数来自 manager state，明细来自 manager 上报数组；旧快照或旧 manager 可能缺失明细。
                            </div>

                            <section class="ta-runtime-managed-group">
                              <header class="ta-runtime-managed-group-header">
                                <strong>有主进程</strong>
                                <span>{{ ownedProcesses(row).length }} 条</span>
                              </header>
                              <div v-if="!ownedProcesses(row).length" class="ta-runtime-managed-empty">暂无有主进程</div>
                              <table v-else class="ta-runtime-managed-table">
                                <thead>
                                  <tr>
                                    <th class="is-selection">选择</th>
                                    <th style="width: 80px; position: relative;">端口<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 80px; position: relative;">PID<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 100px; position: relative;">用户<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 120px; position: relative;">进程<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 160px; position: relative;">baseUrl<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 140px; position: relative;">启动时间<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 120px; position: relative;">绑定<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 100px; position: relative;">健康<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 250px; position: relative;">启动命令<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 150px; position: relative;">traceId<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 120px; position: relative;">操作<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                  </tr>
                                </thead>
                                <tbody>
                                  <tr
                                    v-for="(process, index) in ownedProcesses(row)"
                                    :key="`owned:${row.key}:${process.processId ?? process.port}:${index}`"
                                    :class="{ 'is-selected': isOwnedManagedProcessSelected(row, process) }"
                                  >
                                    <td class="is-selection">
                                      <input
                                        type="checkbox"
                                        :aria-label="`选择 ${processOwner(process)}（${runtimeRowContainerId(row)}:${process.port}）`"
                                        :checked="isOwnedManagedProcessSelected(row, process)"
                                        :disabled="Boolean(batchManagedProcessAction) || managedProcessActionMutation.isPending.value"
                                        @click.stop
                                        @change="toggleOwnedManagedProcessSelection(row, process, $event)"
                                      />
                                    </td>
                                    <td>{{ process.port }}</td>
                                    <td>{{ formatNullable(process.pid) }}</td>
                                    <td class="is-compact" :title="processOwner(process) ?? undefined">
                                      <span>{{ processOwner(process) }}</span>
                                      <span
                                        v-if="batchManagedProcessTargetStatus(row, process)"
                                        :class="['ta-runtime-batch-target-status', `is-${batchManagedProcessTargetStatus(row, process)?.state}`]"
                                        :aria-label="`${processOwner(process)}：${batchManagedProcessTargetStatus(row, process)?.label}`"
                                        :title="batchManagedProcessTargetStatusTitle(row, process)"
                                      ></span>
                                    </td>
                                    <td class="is-compact" :title="process.processId ?? undefined">{{ formatNullable(process.processId) }}</td>
                                    <td class="is-compact" :title="process.baseUrl ?? undefined">{{ formatNullable(process.baseUrl) }}</td>
                                    <td>{{ formatDate(process.startedAt) }}</td>
                                    <td>{{ processBinding(process) }}</td>
                                    <td class="is-compact">{{ processHealth(process) }}</td>
                                    <td class="is-command" :title="process.startCommand ?? undefined"><code>{{ formatNullable(process.startCommand) }}</code></td>
                                    <td class="is-compact" :title="process.traceId ?? undefined">{{ formatNullable(process.traceId) }}</td>
                                    <td>
                                      <div class="ta-runtime-process-actions">
                                        <button
                                          type="button"
                                          class="ta-runtime-action-button"
                                          :class="{ 'is-running': isManagedProcessActionRunning(row, process, 'restart') }"
                                          :disabled="isManagedProcessActionDisabled(row, process)"
                                          title="重启该 TestAgent server"
                                          @click.stop="runManagedProcessAction(row, process, 'restart')"
                                        >
                                          重启
                                        </button>
                                        <button
                                          type="button"
                                          class="ta-runtime-action-button is-danger"
                                          :class="{ 'is-running': isManagedProcessActionRunning(row, process, 'stop') }"
                                          :disabled="isManagedProcessActionDisabled(row, process)"
                                          title="关闭并隐藏该用户的服务端 OpenCode"
                                          @click.stop="runManagedProcessAction(row, process, 'stop')"
                                        >
                                          停止
                                        </button>
                                      </div>
                                    </td>
                                  </tr>
                                </tbody>
                              </table>
                            </section>

                            <section class="ta-runtime-managed-group">
                              <header class="ta-runtime-managed-group-header">
                                <strong>无主进程</strong>
                                <span>{{ ghostProcesses(row).length }} 条</span>
                              </header>
                              <div v-if="!ghostProcesses(row).length" class="ta-runtime-managed-empty">暂无无主进程</div>
                              <table v-else class="ta-runtime-managed-table">
                                <thead>
                                  <tr>
                                    <th style="width: 80px; position: relative;">端口<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 80px; position: relative;">PID<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 100px; position: relative;">UCID<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 120px; position: relative;">Manager 状态<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 120px; position: relative;">平台进程<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 100px; position: relative;">平台状态<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 160px; position: relative;">baseUrl<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 140px; position: relative;">启动时间<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 100px; position: relative;">健康<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 250px; position: relative;">启动命令（无主）<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 150px; position: relative;">traceId<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                    <th style="width: 120px; position: relative;">操作<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                                  </tr>
                                </thead>
                                <tbody>
                                  <tr v-for="(process, index) in ghostProcesses(row)" :key="`ghost:${row.key}:${process.processId ?? process.port}:${index}`">
                                    <td>{{ process.port }}</td>
                                    <td>{{ formatNullable(process.pid) }}</td>
                                    <td class="is-compact" :title="process.unifiedAuthId ?? undefined">{{ formatNullable(process.unifiedAuthId) }}</td>
                                    <td><span :class="['ta-status', statusClass(process.managerStatus)]">{{ formatNullable(process.managerStatus) }}</span></td>
                                    <td class="is-compact" :title="process.processId ?? undefined">{{ process.processId || "平台未登记" }}</td>
                                    <td><span :class="['ta-status', statusClass(process.processStatus)]">{{ formatNullable(process.processStatus) }}</span></td>
                                    <td class="is-compact" :title="process.baseUrl ?? undefined">{{ formatNullable(process.baseUrl) }}</td>
                                    <td>{{ formatDate(process.startedAt) }}</td>
                                    <td class="is-compact">{{ processHealth(process) }}</td>
                                    <td class="is-command" :title="process.startCommand ?? undefined"><code>{{ formatNullable(process.startCommand) }}</code></td>
                                    <td class="is-compact" :title="process.traceId ?? undefined">{{ formatNullable(process.traceId) }}</td>
                                    <td>
                                      <div class="ta-runtime-process-actions">
                                        <button
                                          type="button"
                                          class="ta-runtime-action-button"
                                          :class="{ 'is-running': isManagedProcessActionRunning(row, process, 'restart') }"
                                          :disabled="isManagedProcessActionDisabled(row, process)"
                                          title="重启该无主 TestAgent server"
                                          @click.stop="runManagedProcessAction(row, process, 'restart')"
                                        >
                                          重启
                                        </button>
                                        <button
                                          type="button"
                                          class="ta-runtime-action-button is-danger"
                                          :class="{ 'is-running': isManagedProcessActionRunning(row, process, 'stop') }"
                                          :disabled="isManagedProcessActionDisabled(row, process)"
                                          title="停止该无主 TestAgent server"
                                          @click.stop="runManagedProcessAction(row, process, 'stop')"
                                        >
                                          停止
                                        </button>
                                      </div>
                                    </td>
                                  </tr>
                                </tbody>
                              </table>
                            </section>
                          </div>
                        </td>
                      </tr>
                    </template>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 容器趋势图展示 manager 心跳上报的容器级采样。 -->
            <div v-if="selectedMetricsTarget && selectedMetricsTarget.type === 'container'" class="ta-runtime-metrics-panel">
              <header class="ta-runtime-section-header">
                <h4>{{ selectedMetricsTarget.title }}</h4>
                <div class="ta-runtime-metrics-actions">
                  <el-radio-group v-model="selectedWindowMinutes" size="small">
                    <el-radio-button :value="1">1分钟</el-radio-button>
                    <el-radio-button :value="30">30分钟</el-radio-button>
                    <el-radio-button :value="60">1小时</el-radio-button>
                    <el-radio-button :value="360">6小时</el-radio-button>
                    <el-radio-button :value="720">12小时</el-radio-button>
                    <el-radio-button :value="1440">24小时</el-radio-button>
                    <el-radio-button :value="2880">48小时</el-radio-button>
                  </el-radio-group>
                  <span>{{ selectedMetricsTarget.id }} · {{ metricSamples.length }} 点</span>
                </div>
              </header>
              <div v-if="metricErrorMessage" class="ta-runtime-alert" role="alert">{{ metricErrorMessage }}</div>
              <div v-else-if="metricsQuery.isLoading.value" class="ta-runtime-placeholder">正在加载监控趋势...</div>
              <div v-else class="ta-runtime-chart-grid">
                <RuntimeMetricChart
                  title="CPU / 内存"
                  :samples="metricSamples"
                  :series="[
                    { name: 'CPU %', field: 'cpuUsagePercent' },
                    { name: '内存 %', field: 'memoryUsagePercent' }
                  ]"
                />
                <RuntimeMetricChart
                  title="进程容量"
                  :samples="metricSamples"
                  :series="[
                    { name: '当前进程', field: 'currentProcesses' },
                    { name: '最大进程', field: 'maxProcesses' }
                  ]"
                />
                <RuntimeMetricChart
                  title="磁盘 IO"
                  :samples="metricSamples"
                  :series="[
                    { name: '读取 B/s', field: 'diskReadBytesPerSecond' },
                    { name: '写入 B/s', field: 'diskWriteBytesPerSecond' }
                  ]"
                />
              </div>
            </div>

            <div class="ta-runtime-block is-wide">
              <h5>Manager 与后端连接拓扑</h5>
              <RuntimeTopologyGraph :overview="overview" :page-active="props.pageActive" />
            </div>
          </div>
        </section>

        <section class="ta-runtime-section">
          <header class="ta-runtime-section-header">
            <h4>用户 TestAgent server 进程</h4>
            <span>{{ processPage?.total ?? 0 }} 条</span>
          </header>
          <div class="ta-runtime-user-query">
            <el-input
              v-model="userKeywordDraft"
              size="small"
              clearable
              placeholder="用户名 / userId / 统一认证号"
              class="ta-runtime-user-filter"
              @keyup.enter="queryUserProcesses"
            />
            <el-button
              size="small"
              type="primary"
              :icon="Search"
              :loading="isUserProcessFetching"
              :disabled="Boolean(batchManagedProcessAction)"
              @click="queryUserProcesses"
            >查询用户进程</el-button>
          </div>
          <div class="ta-runtime-table-scroll">
            <table class="ta-runtime-table">
              <thead>
                <tr>
                  <th style="width: 120px; position: relative;">进程<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 100px; position: relative;">用户<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 120px; position: relative;">服务器<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 120px; position: relative;">容器<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 80px; position: relative;">端口<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 80px; position: relative;">PID<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 90px; position: relative;">状态<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 90px; position: relative;">实际状态<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 160px; position: relative;">baseUrl<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 120px; position: relative;">绑定<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 100px; position: relative;">健康<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 150px; position: relative;">traceId<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                  <th style="width: 120px; position: relative;">操作<div class="ta-resize-handle" @mousedown.stop.prevent="startResize"></div></th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!hasUserProcessQuery"><td colspan="13" class="is-empty">请输入用户关键字查询 TestAgent 进程</td></tr>
                <tr v-else-if="isUserProcessFetching && !processRows.length"><td colspan="13" class="is-empty">正在查询用户 TestAgent 进程...</td></tr>
                <tr v-else-if="!processRows.length"><td colspan="13" class="is-empty">暂无该用户 TestAgent 进程</td></tr>
                <tr v-for="process in processRows" :key="process.processId">
                  <td class="is-compact" :title="process.processId ?? undefined">{{ process.processId }}</td>
                  <td class="is-compact" :title="(process.username || process.userId) ?? undefined">{{ process.username || process.userId }}</td>
                  <td :title="process.linuxServerId ?? undefined">{{ process.linuxServerId }}</td>
                  <td class="is-compact" :title="process.containerId ?? undefined">{{ process.containerId }}</td>
                  <td>{{ process.port }}</td>
                  <td>{{ process.pid ?? "-" }}</td>
                  <td><span :class="['ta-status', statusClass(process.status)]">{{ process.status }}</span></td>
                  <td class="is-compact">{{ userProcessActualStatus(process) }}</td>
                  <td class="is-compact" :title="process.baseUrl ?? undefined">{{ process.baseUrl }}</td>
                  <td>
                    <span v-if="process.bindingAgentId" :class="['ta-status', statusClass(process.bindingStatus)]">
                      {{ process.bindingAgentId }} / {{ process.bindingStatus }}
                    </span>
                    <span v-else>-</span>
                  </td>
                  <td class="is-compact" :title="process.healthMessage || formatDate(process.lastHealthCheckAt) || undefined">{{ process.healthMessage || formatDate(process.lastHealthCheckAt) }}</td>
                  <td class="is-compact" :title="process.traceId ?? undefined">{{ process.traceId }}</td>
                  <td>
                    <button
                      v-if="canRestartUserProcess(process)"
                      type="button"
                      class="ta-runtime-action-button"
                      :class="{ 'is-running': isUserProcessRestartRunning(process) }"
                      :disabled="Boolean(batchManagedProcessAction) || managedProcessActionMutation.isPending.value"
                      title="重启该用户 TestAgent server"
                      @click="runUserProcessRestart(process)"
                    >
                      重启
                    </button>
                    <span v-else>-</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer class="ta-runtime-pagination">
            <el-button size="small" :disabled="userProcessPage <= 1 || isUserProcessFetching || Boolean(batchManagedProcessAction)" @click="prevPage">上一页</el-button>
            <span>第 {{ userProcessPage }} / {{ totalPages }} 页</span>
            <el-button size="small" :disabled="userProcessPage >= totalPages || isUserProcessFetching || Boolean(batchManagedProcessAction)" @click="nextPage">下一页</el-button>
          </footer>
        </section>
      </template>
    </template>

  </section>
</template>

<style scoped>
.ta-runtime-management {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  color: #18181b;
  -webkit-user-drag: none;
  user-drag: none;
}
.ta-runtime-management * {
  -webkit-user-drag: none;
  user-drag: none;
}
.ta-runtime-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.ta-runtime-filter {
  width: 132px;
}
.ta-runtime-user-query {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.ta-runtime-user-filter {
  width: 240px;
}
.ta-runtime-alert {
  flex-shrink: 0;
  padding: 9px 12px;
  border: 1px solid #f5c2c2;
  border-radius: 8px;
  background: #fff5f5;
  color: #b42318;
  font-size: 12px;
}
.ta-runtime-alert.is-warning {
  border-color: #f3d19e;
  background: #fdf6ec;
  color: #946200;
}
.ta-runtime-alert.is-success {
  border-color: #b3e19d;
  background: #f0f9eb;
  color: #397826;
}
.ta-runtime-batch-progress {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(160px, 280px);
  align-items: center;
  gap: 6px 12px;
  flex-shrink: 0;
  padding: 9px 12px;
  border: 1px solid #a3c7f5;
  border-radius: 8px;
  background: #f2f8ff;
  color: #245b9e;
  font-size: 12px;
}
.ta-runtime-batch-progress progress {
  width: 100%;
}
.ta-runtime-batch-progress small {
  grid-column: 1 / -1;
  color: #5d7190;
}
.ta-runtime-batch-target-status {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-left: 6px;
  border-radius: 50%;
  background: #94a3b8;
  vertical-align: 1px;
}
.ta-runtime-batch-target-status.is-running {
  background: #3b82f6;
  box-shadow: 0 0 0 3px rgb(59 130 246 / 16%);
}
.ta-runtime-batch-target-status.is-succeeded {
  background: #22c55e;
}
.ta-runtime-batch-target-status.is-failed {
  background: #ef4444;
}
.ta-runtime-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  color: #606266;
  font-size: 13px;
}
.ta-runtime-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.ta-runtime-summary-item {
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
}
.ta-runtime-summary-label,
.ta-runtime-summary-extra {
  display: block;
  color: #606266;
  font-size: 12px;
  line-height: 18px;
}
.ta-runtime-summary-value {
  display: block;
  color: #18181b;
  font-size: 22px;
  line-height: 28px;
  font-weight: 650;
}
.ta-runtime-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ta-runtime-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.ta-runtime-section-header h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
  color: #18181b;
}
.ta-runtime-section-header span {
  color: #909399;
  font-size: 12px;
}
.ta-runtime-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}
.ta-runtime-block {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.ta-runtime-block.is-wide {
  grid-column: 1 / -1;
}
.ta-runtime-block h5 {
  margin: 0;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  font-size: 12px;
  font-weight: 650;
  color: #303133;
  background: #fafafa;
}
.ta-runtime-block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 16px;
  min-height: 44px;
  padding: 6px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}
.ta-runtime-block-header h5 {
  padding: 0;
  border: 0;
  background: transparent;
}
.ta-runtime-batch-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.ta-runtime-batch-actions > span {
  color: #606266;
  font-size: 12px;
}
.ta-runtime-select-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #303133;
  font-size: 12px;
  cursor: pointer;
}
.ta-runtime-select-all input,
.ta-runtime-managed-table .is-selection input {
  width: 14px;
  height: 14px;
  margin: 0;
  accent-color: #b42318;
  cursor: pointer;
}
.ta-runtime-select-all input:disabled,
.ta-runtime-managed-table .is-selection input:disabled {
  cursor: not-allowed;
}
.ta-runtime-block-scroll {
  flex: 1;
  width: 100%;
  overflow-x: auto;
}
.ta-runtime-table-scroll {
  width: 100%;
  overflow-x: auto;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.ta-runtime-table {
  width: 100%;
  min-width: max-content;
  border-collapse: collapse;
  font-size: 12px;
  table-layout: fixed;
}
.ta-runtime-table th,
.ta-runtime-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #ebeef5;
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ta-runtime-table th {
  color: #606266;
  font-weight: 600;
  background: #f7f8fa;
}
.ta-runtime-table th.is-expand,
.ta-runtime-table td.is-expand {
  width: 32px;
  padding-right: 4px;
  text-align: center;
}
.ta-runtime-table tr:last-child td {
  border-bottom: 0;
}
.ta-runtime-table tbody tr[tabindex="0"] {
  cursor: pointer;
}
.ta-runtime-table tbody tr[tabindex="0"]:hover,
.ta-runtime-table tbody tr.is-selected {
  background: #f4f8ff;
}
.ta-runtime-table tbody tr.is-expanded {
  background: #f4f8ff;
}
.ta-runtime-table .is-empty {
  height: 44px;
  color: #909399;
  text-align: center;
}
.ta-runtime-table td.is-metric-stack {
  white-space: normal;
  line-height: 1.45;
}
.ta-runtime-table td.is-metric-stack span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ta-runtime-expand-icon {
  display: inline-block;
  width: 0;
  height: 0;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 6px solid #606266;
  transition: transform 0.15s ease;
}
.ta-runtime-expand-icon.is-expanded {
  transform: rotate(90deg);
}
.ta-runtime-managed-detail td {
  padding: 0;
  background: #fbfcff;
}
.ta-runtime-managed-processes {
  min-width: 720px;
  padding: 10px 12px 12px;
}
.ta-runtime-managed-warning {
  margin-bottom: 10px;
  padding: 8px 10px;
  border: 1px solid #f3d19e;
  border-radius: 6px;
  background: #fdf6ec;
  color: #946200;
  font-size: 12px;
}
.ta-runtime-managed-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ta-runtime-managed-group + .ta-runtime-managed-group {
  margin-top: 12px;
}
.ta-runtime-managed-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #303133;
  font-size: 12px;
}
.ta-runtime-managed-group-header span {
  color: #909399;
}
.ta-runtime-managed-empty {
  padding: 12px;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #909399;
}
.ta-runtime-managed-table {
  width: 100%;
  min-width: max-content;
  border-collapse: collapse;
  background: #fff;
  table-layout: fixed;
}
.ta-runtime-managed-table th,
.ta-runtime-managed-table td {
  padding: 7px 8px;
  border: 1px solid #ebeef5;
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ta-runtime-managed-table th {
  color: #606266;
  font-weight: 600;
  background: #f7f8fa;
}
.ta-runtime-managed-table th.is-selection,
.ta-runtime-managed-table td.is-selection {
  width: 54px;
  text-align: center;
}
.ta-runtime-managed-table tbody tr.is-selected {
  background: #fff7f7;
}
.ta-runtime-managed-table .is-command {
  max-width: 520px;
}
.ta-runtime-managed-table .is-command code {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  white-space: nowrap;
}
.ta-runtime-process-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.ta-runtime-action-button {
  height: 24px;
  padding: 0 8px;
  border: 1px solid #c7d2fe;
  border-radius: 6px;
  background: #fff;
  color: #1d4ed8;
  font: inherit;
  cursor: pointer;
}
.ta-runtime-action-button:hover,
.ta-runtime-action-button:focus-visible {
  border-color: #93c5fd;
  background: #eff6ff;
  outline: none;
}
.ta-runtime-action-button.is-danger {
  border-color: #fecaca;
  color: #b91c1c;
}
.ta-runtime-action-button.is-danger:hover,
.ta-runtime-action-button.is-danger:focus-visible {
  border-color: #fca5a5;
  background: #fef2f2;
}
.ta-runtime-action-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.ta-runtime-action-button.is-running {
  box-shadow: inset 0 0 0 1px currentColor;
}
.is-compact {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ta-runtime-help {
  cursor: help;
  text-decoration: underline dotted #a8abb2;
  text-underline-offset: 3px;
}
.ta-runtime-trend-button {
  height: 24px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #2563eb;
  font: inherit;
  cursor: pointer;
}
.ta-runtime-trend-button:hover,
.ta-runtime-trend-button:focus-visible {
  border-color: #93c5fd;
  background: #eff6ff;
  outline: none;
}
.ta-status {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 650;
  line-height: 20px;
  background: #eef1f5;
  color: #475569;
}
.ta-status.is-ok {
  background: #e8f6ef;
  color: #127a42;
}
.ta-status.is-pending {
  background: #fff5d7;
  color: #956800;
}
.ta-status.is-bad {
  background: #fdeaea;
  color: #b42318;
}
.ta-status.is-muted {
  background: #f1f2f4;
  color: #6b7280;
}
.ta-runtime-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  color: #606266;
  font-size: 12px;
}
.ta-runtime-metrics-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ta-runtime-chart-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.ta-runtime-metrics-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
@media (max-width: 900px) {
  .ta-runtime-summary,
  .ta-runtime-grid,
  .ta-runtime-chart-grid {
    grid-template-columns: 1fr;
  }
  .ta-runtime-filter {
    width: 100%;
  }
}

.ta-resize-handle {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  z-index: 10;
}
.ta-resize-handle:hover,
.ta-resize-handle.is-resizing {
  background: rgba(0, 0, 0, 0.08);
}
</style>
