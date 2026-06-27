<script setup lang="ts">
import { computed, inject, ref } from "vue";
import { useQuery } from "@tanstack/vue-query";
import { Refresh, Search } from "@element-plus/icons-vue";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  OpencodeRuntimeBackendMetricHistory,
  OpencodeRuntimeContainerMetricHistory,
  OpencodeRuntimeManagementOverview,
  OpencodeRuntimeManagementOverviewParams
} from "@test-agent/shared-types";
import RuntimeMetricChart from "./RuntimeMetricChart.vue";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;

type FilterDraft = {
  status: string;
  linuxServerId: string;
  containerId: string;
  username: string;
};

const processStatusOptions = ["RUNNING"];
const draftFilters = ref<FilterDraft>({ status: "", linuxServerId: "", containerId: "", username: "" });
const activeFilters = ref<FilterDraft>({ status: "", linuxServerId: "", containerId: "", username: "" });
const page = ref(1);
const size = ref(20);
const selectedMetricsTarget = ref<{ type: "container" | "backend"; id: string; title: string } | null>(null);
const selectedWindowMinutes = ref(60);

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const overviewParams = computed<OpencodeRuntimeManagementOverviewParams>(() => ({
  status: activeFilters.value.status || undefined,
  linuxServerId: activeFilters.value.linuxServerId.trim() || undefined,
  containerId: activeFilters.value.containerId.trim() || undefined,
  username: activeFilters.value.username.trim() || undefined,
  page: page.value,
  size: size.value
}));

const overviewQuery = useQuery<OpencodeRuntimeManagementOverview, Error>({
  queryKey: computed(() => ["opencode-runtime-management", overviewParams.value]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getOpencodeRuntimeManagementOverview(overviewParams.value)
});

const metricsQuery = useQuery<OpencodeRuntimeContainerMetricHistory | OpencodeRuntimeBackendMetricHistory, Error>({
  queryKey: computed(() => ["opencode-runtime-metrics", selectedMetricsTarget.value?.type, selectedMetricsTarget.value?.id, selectedWindowMinutes.value]),
  enabled: () => hasSuperAdmin.value && selectedMetricsTarget.value !== null,
  retry: false,
  refetchInterval: 5000,
  queryFn: () => {
    const target = selectedMetricsTarget.value;
    if (!target) {
      throw new Error("未选择监控对象");
    }
    const params = { windowMinutes: selectedWindowMinutes.value, maxPoints: 720 };
    if (target.type === "container") {
      return api.getOpencodeRuntimeContainerMetrics(target.id, params);
    }
    return api.getOpencodeRuntimeBackendProcessMetrics(target.id, params);
  }
});

const overview = computed(() => overviewQuery.data.value);
const summary = computed(() => overview.value?.summary);
const processPage = computed(() => overview.value?.opencodeProcesses);
const processRows = computed(() => processPage.value?.items ?? []);
const isLoading = computed(() => overviewQuery.isLoading.value);
const isFetching = computed(() => overviewQuery.isFetching.value);
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
const totalPages = computed(() => Math.max(1, Math.ceil((processPage.value?.total ?? 0) / size.value)));
const summaryCards = computed(() => {
  const item = summary.value;
  return [
    { label: "Linux 服务器", value: item?.linuxServers ?? 0, extra: `${item?.readyLinuxServers ?? 0} READY` },
    { label: "后端进程", value: item?.backendProcesses ?? 0, extra: `${item?.readyBackendProcesses ?? 0} READY` },
    { label: "容器", value: item?.containers ?? 0, extra: `${item?.readyContainers ?? 0} READY` },
    { label: "管理进程", value: item?.managers ?? 0, extra: `${item?.connectedManagers ?? 0} CONNECTED` },
    { label: "opencode 进程", value: item?.opencodeProcesses ?? 0, extra: `${item?.runningOpencodeProcesses ?? 0} RUNNING` },
    { label: "用户绑定", value: item?.userBindings ?? 0, extra: `${item?.managerBackendConnections ?? 0} 连接` }
  ];
});

function applyFilters() {
  activeFilters.value = { ...draftFilters.value };
  page.value = 1;
}

function clearFilters() {
  draftFilters.value = { status: "", linuxServerId: "", containerId: "", username: "" };
  activeFilters.value = { status: "", linuxServerId: "", containerId: "", username: "" };
  page.value = 1;
}

function refresh() {
  void overviewQuery.refetch();
  if (selectedMetricsTarget.value) {
    void metricsQuery.refetch();
  }
}

function prevPage() {
  page.value = Math.max(1, page.value - 1);
}

function nextPage() {
  page.value = Math.min(totalPages.value, page.value + 1);
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

function selectBackendProcess(backendProcessId: string) {
  if (selectedMetricsTarget.value?.type === "backend" && selectedMetricsTarget.value.id === backendProcessId) {
    selectedMetricsTarget.value = null;
  } else {
    selectedMetricsTarget.value = { type: "backend", id: backendProcessId, title: "后端监控趋势" };
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
</script>

<template>
  <section class="ta-runtime-management" @dragstart.prevent>
    <div v-if="!hasSuperAdmin" class="ta-runtime-placeholder">当前账号无运行管理权限</div>
    <template v-else>
      <div class="ta-runtime-toolbar">
        <el-select v-model="draftFilters.status" size="small" clearable placeholder="进程状态" class="ta-runtime-filter">
          <el-option v-for="status in processStatusOptions" :key="status" :label="status" :value="status" />
        </el-select>
        <el-input v-model="draftFilters.linuxServerId" size="small" clearable placeholder="Linux IP" class="ta-runtime-filter" />
        <el-input v-model="draftFilters.containerId" size="small" clearable placeholder="容器 ID" class="ta-runtime-filter" />
        <el-input v-model="draftFilters.username" size="small" clearable placeholder="用户名" class="ta-runtime-filter" />
        <el-button size="small" type="primary" :icon="Search" :loading="isFetching" @click="applyFilters">查询</el-button>
        <el-button size="small" :icon="Refresh" :loading="isFetching" @click="refresh">刷新</el-button>
        <el-button size="small" @click="clearFilters">清空</el-button>
      </div>

      <div v-if="errorMessage" class="ta-runtime-alert" role="alert">{{ errorMessage }}</div>

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
            <div class="ta-runtime-block">
              <h5>Linux 服务器</h5>
              <div class="ta-runtime-block-scroll">
                <table class="ta-runtime-table">
                  <thead>
                    <tr><th>服务器</th><th>状态</th><th>最近心跳</th><th>容量</th><th>traceId</th></tr>
                  </thead>
                  <tbody>
                    <tr v-if="!overview?.linuxServers.length"><td colspan="5" class="is-empty">暂无服务器</td></tr>
                    <tr v-for="server in overview?.linuxServers ?? []" :key="server.linuxServerId">
                      <td>{{ server.linuxServerId }}</td>
                      <td><span :class="['ta-status', statusClass(server.status)]">{{ server.status }}</span></td>
                      <td>{{ formatDate(server.lastHeartbeatAt) }}</td>
                      <td class="is-compact">{{ compactRecord(server.capacitySummary) }}</td>
                      <td class="is-compact">{{ server.traceId }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div class="ta-runtime-block">
              <h5>后端 Java 进程</h5>
              <div class="ta-runtime-block-scroll">
                <table class="ta-runtime-table">
                  <thead>
                    <tr><th>进程</th><th>服务器</th><th>状态</th><th>CPU</th><th>内存</th><th>磁盘</th><th>JVM</th><th>心跳</th></tr>
                  </thead>
                  <tbody>
                    <tr v-if="!overview?.backendProcesses.length"><td colspan="8" class="is-empty">暂无后端进程</td></tr>
                    <tr
                      v-for="process in overview?.backendProcesses ?? []"
                      :key="process.backendProcessId"
                      :class="activeRowClass('backend', process.backendProcessId)"
                      tabindex="0"
                      @click="selectBackendProcess(process.backendProcessId)"
                      @keydown.enter="selectBackendProcess(process.backendProcessId)"
                    >
                      <td class="is-compact">{{ process.backendProcessId }}</td>
                      <td>{{ process.linuxServerId }}</td>
                      <td><span :class="['ta-status', statusClass(process.status)]">{{ process.status }}</span></td>
                      <td>{{ formatPercent(process.cpuUsagePercent) }}</td>
                      <td>{{ formatPercent(process.memoryUsagePercent) }} / {{ formatBytes(process.memoryUsedBytes) }}</td>
                      <td>{{ formatPercent(process.diskUsagePercent) }} / {{ formatBytes(process.diskUsedBytes) }}</td>
                      <td>{{ formatBytes(process.jvmMemoryUsedBytes) }} / {{ process.jvmThreadsLive ?? "-" }} 线程</td>
                      <td>{{ formatDate(process.lastHeartbeatAt) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 后端趋势图按指标归属区分服务器级资源与当前 JVM 进程。 -->
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
                  title="服务器 CPU / 内存 / 磁盘"
                  :samples="metricSamples"
                  :series="[
                    { name: 'CPU %', field: 'cpuUsagePercent' },
                    { name: '内存 %', field: 'memoryUsagePercent' },
                    { name: '磁盘 %', field: 'diskUsagePercent' }
                  ]"
                />
                <RuntimeMetricChart
                  title="JVM 内存（当前进程）"
                  :samples="metricSamples"
                  :series="[
                    { name: 'used', field: 'jvmMemoryUsedBytes' },
                    { name: 'committed', field: 'jvmMemoryCommittedBytes' },
                    { name: 'max', field: 'jvmMemoryMaxBytes' }
                  ]"
                />
                <RuntimeMetricChart
                  title="JVM GC / 线程（当前进程）"
                  :samples="metricSamples"
                  :series="[
                    { name: 'GC pause ms', field: 'jvmGcPauseMillis' },
                    { name: 'live threads', field: 'jvmThreadsLive' }
                  ]"
                />
              </div>
            </div>

            <div class="ta-runtime-block">
              <h5>容器</h5>
              <div class="ta-runtime-block-scroll">
                <table class="ta-runtime-table">
                  <thead>
                    <tr><th>容器</th><th>服务器</th><th>状态</th><th>端口池</th><th>容量</th><th>来源</th><th>CPU</th><th>内存率</th><th>已用内存</th><th>心跳</th></tr>
                  </thead>
                  <tbody>
                    <tr v-if="!overview?.containers.length"><td colspan="10" class="is-empty">暂无容器</td></tr>
                    <tr
                      v-for="container in overview?.containers ?? []"
                      :key="container.containerId"
                      :class="activeRowClass('container', container.containerId)"
                      tabindex="0"
                      @click="selectContainer(container.containerId)"
                      @keydown.enter="selectContainer(container.containerId)"
                    >
                      <td class="is-compact">{{ container.containerId }}</td>
                      <td>{{ container.linuxServerId }}</td>
                      <td><span :class="['ta-status', statusClass(container.status)]">{{ container.status }}</span></td>
                      <td>{{ container.portStart }}-{{ container.portEnd }}</td>
                      <td>{{ container.currentProcesses }}/{{ container.maxProcesses }}</td>
                      <td>{{ formatMetricsSource(container.metricsSource) }}</td>
                      <td>{{ formatPercent(container.cpuUsagePercent) }}</td>
                      <td>{{ formatPercent(container.memoryUsagePercent) }}</td>
                      <td>{{ formatBytes(container.memoryUsedBytes) }}</td>
                      <td>{{ formatDate(container.lastHeartbeatAt) }}</td>
                    </tr>
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

            <div class="ta-runtime-block">
              <h5>管理进程</h5>
              <div class="ta-runtime-block-scroll">
                <table class="ta-runtime-table">
                  <thead>
                    <tr><th>管理进程</th><th>容器</th><th>状态</th><th>协议</th><th>能力</th><th>心跳</th></tr>
                  </thead>
                  <tbody>
                    <tr v-if="!overview?.managers.length"><td colspan="6" class="is-empty">暂无管理进程</td></tr>
                    <tr v-for="manager in overview?.managers ?? []" :key="manager.managerId">
                      <td class="is-compact">{{ manager.managerId }}</td>
                      <td class="is-compact">{{ manager.containerId }}</td>
                      <td><span :class="['ta-status', statusClass(manager.connectionStatus)]">{{ manager.connectionStatus }}</span></td>
                      <td>{{ manager.protocolVersion }}</td>
                      <td class="is-compact">{{ compactRecord(manager.capabilities) }}</td>
                      <td>{{ formatDate(manager.lastHeartbeatAt) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div class="ta-runtime-block is-wide">
              <h5>Manager 与后端连接</h5>
              <div class="ta-runtime-block-scroll">
                <table class="ta-runtime-table">
                  <thead>
                    <tr><th>管理进程</th><th>后端进程</th><th>状态</th><th>连接时间</th><th>最近心跳</th><th>traceId</th></tr>
                  </thead>
                  <tbody>
                    <tr v-if="!overview?.managerBackendConnections.length"><td colspan="6" class="is-empty">暂无连接</td></tr>
                    <tr v-for="connection in overview?.managerBackendConnections ?? []" :key="`${connection.managerId}:${connection.backendProcessId}`">
                      <td class="is-compact">{{ connection.managerId }}</td>
                      <td class="is-compact">{{ connection.backendProcessId }}</td>
                      <td><span :class="['ta-status', statusClass(connection.status)]">{{ connection.status }}</span></td>
                      <td>{{ formatDate(connection.connectedAt) }}</td>
                      <td>{{ formatDate(connection.lastHeartbeatAt) }}</td>
                      <td class="is-compact">{{ connection.traceId }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </section>

        <section class="ta-runtime-section">
          <header class="ta-runtime-section-header">
            <h4>用户 opencode server 进程</h4>
            <span>{{ processPage?.total ?? 0 }} 条</span>
          </header>
          <div class="ta-runtime-table-scroll">
            <table class="ta-runtime-table">
              <thead>
                <tr>
                  <th>进程</th>
                  <th>用户</th>
                  <th>服务器</th>
                  <th>容器</th>
                  <th>端口</th>
                  <th>PID</th>
                  <th>状态</th>
                  <th>baseUrl</th>
                  <th>绑定</th>
                  <th>健康</th>
                  <th>traceId</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!processRows.length"><td colspan="11" class="is-empty">暂无 opencode 进程</td></tr>
                <tr v-for="process in processRows" :key="process.processId">
                  <td class="is-compact">{{ process.processId }}</td>
                  <td class="is-compact">{{ process.username || process.userId }}</td>
                  <td>{{ process.linuxServerId }}</td>
                  <td class="is-compact">{{ process.containerId }}</td>
                  <td>{{ process.port }}</td>
                  <td>{{ process.pid ?? "-" }}</td>
                  <td><span :class="['ta-status', statusClass(process.status)]">{{ process.status }}</span></td>
                  <td class="is-compact">{{ process.baseUrl }}</td>
                  <td>
                    <span v-if="process.bindingAgentId" :class="['ta-status', statusClass(process.bindingStatus)]">
                      {{ process.bindingAgentId }} / {{ process.bindingStatus }}
                    </span>
                    <span v-else>-</span>
                  </td>
                  <td class="is-compact">{{ process.healthMessage || formatDate(process.lastHealthCheckAt) }}</td>
                  <td class="is-compact">{{ process.traceId }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer class="ta-runtime-pagination">
            <el-button size="small" :disabled="page <= 1 || isFetching" @click="prevPage">上一页</el-button>
            <span>第 {{ page }} / {{ totalPages }} 页</span>
            <el-button size="small" :disabled="page >= totalPages || isFetching" @click="nextPage">下一页</el-button>
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
.ta-runtime-alert {
  flex-shrink: 0;
  padding: 9px 12px;
  border: 1px solid #f5c2c2;
  border-radius: 8px;
  background: #fff5f5;
  color: #b42318;
  font-size: 12px;
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
  border-collapse: collapse;
  font-size: 12px;
}
.ta-runtime-table th,
.ta-runtime-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #ebeef5;
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
}
.ta-runtime-table th {
  color: #606266;
  font-weight: 600;
  background: #f7f8fa;
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
.ta-runtime-table .is-empty {
  height: 44px;
  color: #909399;
  text-align: center;
}
.is-compact {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
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
</style>
