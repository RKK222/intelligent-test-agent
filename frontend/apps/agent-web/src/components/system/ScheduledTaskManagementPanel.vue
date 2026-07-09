<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Refresh, Search } from "@element-plus/icons-vue";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  SchedulerDiagnostics,
  ScheduledTaskManagementRun,
  ScheduledTaskManagementTask,
  ScheduledTaskRunListParams,
  SchedulerRunStatus,
  SchedulerTriggerType
} from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();

const taskPage = ref(1);
const taskSize = ref(20);
const runPage = ref(1);
const runSize = ref(20);
const selectedTaskKey = ref("");
const cronDrafts = ref<Record<string, string>>({});
const activeRunFilters = ref({ status: "", triggerType: "" });
const draftRunFilters = ref({ status: "", triggerType: "" });

const runStatusOptions: Array<{ label: string; value: SchedulerRunStatus }> = [
  { label: "待执行", value: "PENDING" },
  { label: "运行中", value: "RUNNING" },
  { label: "停止中", value: "STOPPING" },
  { label: "成功", value: "SUCCEEDED" },
  { label: "失败", value: "FAILED" },
  { label: "已跳过", value: "SKIPPED" },
  { label: "人工停止", value: "MANUALLY_STOPPED" }
];

const triggerTypeOptions: Array<{ label: string; value: SchedulerTriggerType }> = [
  { label: "定时触发", value: "CRON" },
  { label: "手工触发", value: "MANUAL" },
  { label: "用户计划", value: "USER_PLAN" }
];

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);

const taskQuery = useQuery({
  queryKey: computed(() => ["scheduler-management", "tasks", taskPage.value, taskSize.value]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.listScheduledTasks({ page: taskPage.value, size: taskSize.value })
});

const runParams = computed<ScheduledTaskRunListParams>(() => ({
  taskKey: selectedTaskKey.value || undefined,
  status: activeRunFilters.value.status || undefined,
  triggerType: activeRunFilters.value.triggerType || undefined,
  page: runPage.value,
  size: runSize.value
}));

const runQuery = useQuery({
  queryKey: computed(() => ["scheduler-management", "runs", runParams.value]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.listScheduledTaskRuns(runParams.value)
});

const diagnosticsQuery = useQuery({
  queryKey: computed(() => ["scheduler-management", "diagnostics", selectedTaskKey.value]),
  enabled: () => hasSuperAdmin.value && Boolean(selectedTaskKey.value),
  retry: false,
  queryFn: () => api.getSchedulerDiagnostics(selectedTaskKey.value)
});

const taskRows = computed(() => taskQuery.data.value?.items ?? []);
const runRows = computed(() => runQuery.data.value?.items ?? []);
const diagnostics = computed<SchedulerDiagnostics | null>(() => diagnosticsQuery.data.value ?? null);
const taskTotalPages = computed(() => Math.max(1, Math.ceil((taskQuery.data.value?.total ?? 0) / taskSize.value)));
const runTotalPages = computed(() => Math.max(1, Math.ceil((runQuery.data.value?.total ?? 0) / runSize.value)));
const isTaskFetching = computed(() => taskQuery.isFetching.value);
const isRunFetching = computed(() => runQuery.isFetching.value);
const isDiagnosticsFetching = computed(() => diagnosticsQuery.isFetching.value);
const errorMessage = computed(() => formatError(taskQuery.error.value) || formatError(runQuery.error.value) || formatError(diagnosticsQuery.error.value));

watch(
  taskRows,
  (rows) => {
    const drafts = { ...cronDrafts.value };
    rows.forEach((task) => {
      drafts[task.taskKey] ??= task.cronExpression;
    });
    cronDrafts.value = drafts;
    if (!selectedTaskKey.value && rows.length > 0) {
      selectedTaskKey.value = rows[0].taskKey;
    }
  },
  { immediate: true }
);

const updateTaskMutation = useMutation({
  mutationFn: ({ taskKey, payload }: { taskKey: string; payload: { enabled?: boolean; cronExpression?: string; lockTtlSeconds?: number } }) =>
    api.updateScheduledTask(taskKey, payload),
  onSuccess: () => refreshSchedulerData()
});

const triggerTaskMutation = useMutation({
  mutationFn: (taskKey: string) => api.triggerScheduledTask(taskKey),
  onSuccess: () => refreshSchedulerData()
});

const stopRunMutation = useMutation({
  mutationFn: (taskRunId: string) => api.stopScheduledTaskRun(taskRunId),
  onSuccess: () => refreshSchedulerData()
});

function refreshSchedulerData() {
  void queryClient.invalidateQueries({ queryKey: ["scheduler-management"] });
}

function refresh() {
  void taskQuery.refetch();
  void runQuery.refetch();
}

function selectTask(taskKey: string) {
  selectedTaskKey.value = taskKey;
  runPage.value = 1;
}

function saveCron(task: ScheduledTaskManagementTask) {
  const cronExpression = cronDrafts.value[task.taskKey]?.trim();
  if (!cronExpression || cronExpression === task.cronExpression) {
    return;
  }
  updateTaskMutation.mutate({ taskKey: task.taskKey, payload: { cronExpression } });
}

function toggleTask(task: ScheduledTaskManagementTask) {
  updateTaskMutation.mutate({ taskKey: task.taskKey, payload: { enabled: !task.enabled } });
}

function triggerTask(task: ScheduledTaskManagementTask) {
  if (isTaskActive(task)) {
    return;
  }
  triggerTaskMutation.mutate(task.taskKey);
}

function stopRun(run: ScheduledTaskManagementRun) {
  if (run.status !== "RUNNING") {
    return;
  }
  stopRunMutation.mutate(run.taskRunId);
}

function applyRunFilters() {
  activeRunFilters.value = { ...draftRunFilters.value };
  runPage.value = 1;
}

function clearRunFilters() {
  draftRunFilters.value = { status: "", triggerType: "" };
  activeRunFilters.value = { status: "", triggerType: "" };
  runPage.value = 1;
}

function previousTaskPage() {
  taskPage.value = Math.max(1, taskPage.value - 1);
}

function nextTaskPage() {
  taskPage.value = Math.min(taskTotalPages.value, taskPage.value + 1);
}

function previousRunPage() {
  runPage.value = Math.max(1, runPage.value - 1);
}

function nextRunPage() {
  runPage.value = Math.min(runTotalPages.value, runPage.value + 1);
}

function isTaskActive(task: ScheduledTaskManagementTask) {
  return task.currentRun ? isActiveStatus(task.currentRun.status) : false;
}

function isActiveStatus(status?: string | null) {
  return ["PENDING", "RUNNING", "STOPPING"].includes((status ?? "").toUpperCase());
}

function statusClass(status?: string | null) {
  const normalized = (status ?? "").toUpperCase();
  if (["SUCCEEDED", "REGISTERED"].includes(normalized)) {
    return "is-ok";
  }
  if (["PENDING", "RUNNING", "STOPPING"].includes(normalized)) {
    return "is-pending";
  }
  if (["FAILED", "MISSING_HANDLER"].includes(normalized)) {
    return "is-bad";
  }
  if (["SKIPPED", "MANUALLY_STOPPED"].includes(normalized)) {
    return "is-muted";
  }
  return "is-neutral";
}

function labelOrCode(label?: string | null, code?: string | null) {
  return label || code || "-";
}

function runOutcome(run: ScheduledTaskManagementRun) {
  return run.errorMessage || run.skipReason || run.stopReason || "-";
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

function yesNo(value?: boolean | null) {
  return value ? "是" : "否";
}

function readyText(value?: boolean | null) {
  return value ? "就绪" : "受阻";
}

function formatSeconds(value?: number | null) {
  if (value == null) {
    return "-";
  }
  return `${value} 秒`;
}

function formatMillisAsSeconds(value?: number | null) {
  if (value == null) {
    return "-";
  }
  return `${Math.max(0, Math.round(value / 1000))} 秒`;
}

function lockStatusText(value: SchedulerDiagnostics | null) {
  if (!value) {
    return "-";
  }
  if (!value.redisLock.checkable) {
    return value.redisLock.errorMessage ? `不可检查：${value.redisLock.errorMessage}` : "不可检查";
  }
  if (!value.redisLock.locked) {
    return "未占用";
  }
  return `锁占用，剩余 ${formatMillisAsSeconds(value.redisLock.ttlMillis)}`;
}

function formatError(error: unknown) {
  if (!error) {
    return "";
  }
  if (error instanceof BackendApiError) {
    return `${error.message}（${error.code}）`;
  }
  return error instanceof Error ? error.message : "定时任务管理数据加载失败";
}
</script>

<template>
  <section class="ta-scheduler-management" @dragstart.prevent>
    <div v-if="!hasSuperAdmin" class="ta-scheduler-placeholder">当前账号无定时任务管理权限</div>
    <template v-else>
      <div class="ta-scheduler-toolbar">
        <el-button size="small" :icon="Refresh" :loading="isTaskFetching || isRunFetching" @click="refresh">刷新</el-button>
        <span class="ta-scheduler-toolbar-note">手工启动会绕过任务启用开关，但同一 taskKey 有未结束运行时不可启动。</span>
      </div>

      <div v-if="errorMessage" class="ta-scheduler-alert" role="alert">{{ errorMessage }}</div>

      <section class="ta-scheduler-section">
        <header class="ta-scheduler-section-header">
          <h4>运行条件</h4>
          <span v-if="isDiagnosticsFetching">刷新中</span>
          <span v-else>当前进程实际生效值</span>
        </header>
        <div v-if="diagnostics" class="ta-scheduler-diagnostics">
          <div class="ta-scheduler-diagnostic-group">
            <span class="ta-scheduler-diagnostic-label">全局 scheduler</span>
            <strong :class="['ta-status', diagnostics.scheduler.enabled ? 'is-ok' : 'is-bad']">
              {{ diagnostics.scheduler.enabled ? "启用" : "关闭" }}
            </strong>
          </div>
          <div class="ta-scheduler-diagnostic-group">
            <span class="ta-scheduler-diagnostic-label">扫描线程</span>
            <strong :class="['ta-status', diagnostics.scheduler.runnerRunning ? 'is-ok' : 'is-bad']">
              {{ diagnostics.scheduler.runnerRunning ? "运行中" : "未运行" }}
            </strong>
          </div>
          <div class="ta-scheduler-diagnostic-group">
            <span class="ta-scheduler-diagnostic-label">实例</span>
            <strong>实例 {{ diagnostics.scheduler.instanceId }}</strong>
          </div>
          <div class="ta-scheduler-diagnostic-group">
            <span class="ta-scheduler-diagnostic-label">扫描参数</span>
            <strong>
              间隔 {{ formatSeconds(diagnostics.scheduler.scanIntervalSeconds) }} /
              due {{ diagnostics.scheduler.dueTaskLimit }} /
              manual {{ diagnostics.scheduler.manualRunLimit }}
            </strong>
          </div>
          <div class="ta-scheduler-diagnostic-group">
            <span class="ta-scheduler-diagnostic-label">最近扫描</span>
            <strong>{{ formatDate(diagnostics.scheduler.lastScanStartedAt) }} → {{ formatDate(diagnostics.scheduler.lastScanFinishedAt) }}</strong>
          </div>
          <div class="ta-scheduler-diagnostic-group">
            <span class="ta-scheduler-diagnostic-label">Redis 锁</span>
            <strong>{{ lockStatusText(diagnostics) }}</strong>
          </div>
          <div class="ta-scheduler-diagnostic-group">
            <span class="ta-scheduler-diagnostic-label">手工触发</span>
            <strong :class="['ta-status', diagnostics.diagnosis.manualTriggerReady ? 'is-ok' : 'is-pending']">
              {{ readyText(diagnostics.diagnosis.manualTriggerReady) }}
            </strong>
          </div>
          <div class="ta-scheduler-diagnostic-group">
            <span class="ta-scheduler-diagnostic-label">Cron 触发</span>
            <strong :class="['ta-status', diagnostics.diagnosis.cronReady ? 'is-ok' : 'is-pending']">
              {{ readyText(diagnostics.diagnosis.cronReady) }}
            </strong>
          </div>
        </div>
        <div v-if="diagnostics" class="ta-scheduler-blockers">
          <span class="ta-scheduler-diagnostic-label">任务阻塞诊断</span>
          <span v-if="diagnostics.diagnosis.blockers.length === 0" class="ta-scheduler-ok-text">当前没有阻塞项</span>
          <span
            v-for="blocker in diagnostics.diagnosis.blockers"
            :key="blocker.code"
            class="ta-scheduler-blocker"
          >
            {{ blocker.message }}
          </span>
        </div>
        <div v-else class="ta-scheduler-placeholder">请选择一个定时任务查看诊断信息</div>
      </section>

      <section class="ta-scheduler-section">
        <header class="ta-scheduler-section-header">
          <h4>定时任务</h4>
          <span>第 {{ taskPage }} / {{ taskTotalPages }} 页</span>
        </header>
        <div class="ta-scheduler-table-scroll">
          <table class="ta-scheduler-table">
            <thead>
              <tr>
                <th>任务</th>
                <th>启用</th>
                <th>Cron</th>
                <th>下次触发</th>
                <th>注册状态</th>
                <th>当前/最近执行</th>
                <th>锁 TTL</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="taskRows.length === 0">
                <td colspan="8" class="is-empty">暂无定时任务</td>
              </tr>
              <tr
                v-for="task in taskRows"
                :key="task.taskKey"
                :class="{ 'is-selected': selectedTaskKey === task.taskKey }"
                @click="selectTask(task.taskKey)"
              >
                <td>
                  <strong>{{ task.name }}</strong>
                  <span class="ta-scheduler-subtext">{{ task.taskKey }}</span>
                </td>
                <td>
                  <span :class="['ta-status', task.enabled ? 'is-ok' : 'is-muted']">{{ task.enabled ? "启用" : "停用" }}</span>
                </td>
                <td class="ta-scheduler-cron-cell" @click.stop>
                  <el-input v-model="cronDrafts[task.taskKey]" size="small" placeholder="Cron 表达式" />
                </td>
                <td>{{ formatDate(task.nextFireAt) }}</td>
                <td>
                  <span :class="['ta-status', statusClass(task.registrationStatus)]">
                    {{ labelOrCode(task.registrationStatusLabel, task.registrationStatus) }}
                  </span>
                </td>
                <td>
                  <span v-if="task.currentRun" :class="['ta-status', statusClass(task.currentRun.status)]">
                    {{ labelOrCode(task.currentRun.statusLabel, task.currentRun.status) }}
                  </span>
                  <span v-else-if="task.latestRun" :class="['ta-status', statusClass(task.latestRun.status)]">
                    {{ labelOrCode(task.latestRun.statusLabel, task.latestRun.status) }}
                  </span>
                  <span v-else>-</span>
                </td>
                <td>{{ task.lockTtlSeconds }} 秒</td>
                <td class="ta-scheduler-actions" @click.stop>
                  <el-button size="small" @click="toggleTask(task)">{{ task.enabled ? "停用" : "启用" }}</el-button>
                  <el-button size="small" @click="saveCron(task)">保存 Cron</el-button>
                  <el-button size="small" type="primary" :disabled="isTaskActive(task)" @click="triggerTask(task)">手工启动</el-button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer class="ta-scheduler-pagination">
          <el-button size="small" :disabled="taskPage <= 1" @click="previousTaskPage">上一页</el-button>
          <el-button size="small" :disabled="taskPage >= taskTotalPages" @click="nextTaskPage">下一页</el-button>
        </footer>
      </section>

      <section class="ta-scheduler-section">
        <header class="ta-scheduler-section-header">
          <h4>执行记录</h4>
          <span>第 {{ runPage }} / {{ runTotalPages }} 页</span>
        </header>
        <div class="ta-scheduler-filters">
          <el-select v-model="draftRunFilters.status" size="small" clearable placeholder="执行状态" class="ta-scheduler-filter">
            <el-option v-for="status in runStatusOptions" :key="status.value" :label="status.label" :value="status.value" />
          </el-select>
          <el-select v-model="draftRunFilters.triggerType" size="small" clearable placeholder="触发方式" class="ta-scheduler-filter">
            <el-option v-for="trigger in triggerTypeOptions" :key="trigger.value" :label="trigger.label" :value="trigger.value" />
          </el-select>
          <el-button size="small" type="primary" :icon="Search" @click="applyRunFilters">查询</el-button>
          <el-button size="small" @click="clearRunFilters">清空</el-button>
        </div>
        <div class="ta-scheduler-table-scroll">
          <table class="ta-scheduler-table">
            <thead>
              <tr>
                <th>运行 ID</th>
                <th>任务</th>
                <th>状态</th>
                <th>触发</th>
                <th>计划/开始/结束</th>
                <th>执行实例</th>
                <th>停止操作</th>
                <th>结果</th>
                <th>traceId</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="runRows.length === 0">
                <td colspan="10" class="is-empty">暂无执行记录</td>
              </tr>
              <tr v-for="run in runRows" :key="run.taskRunId">
                <td class="is-compact">{{ run.taskRunId }}</td>
                <td>{{ run.taskKey }}</td>
                <td>
                  <span :class="['ta-status', statusClass(run.status)]">{{ labelOrCode(run.statusLabel, run.status) }}</span>
                </td>
                <td>{{ labelOrCode(run.triggerTypeLabel, run.triggerType) }}</td>
                <td class="is-compact">
                  <span>{{ formatDate(run.scheduledFireAt) }}</span>
                  <span>{{ formatDate(run.startedAt) }}</span>
                  <span>{{ formatDate(run.endedAt) }}</span>
                </td>
                <td class="is-compact">{{ run.ownerInstanceId || "-" }}</td>
                <td class="is-compact">
                  <span>{{ formatDate(run.stopRequestedAt) }}</span>
                  <span>{{ run.stopRequestedByUserId || "-" }}</span>
                </td>
                <td class="is-compact">{{ runOutcome(run) }}</td>
                <td class="is-compact">{{ run.traceId }}</td>
                <td>
                  <el-button v-if="run.status === 'RUNNING'" size="small" type="danger" @click="stopRun(run)">停止</el-button>
                  <span v-else>-</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer class="ta-scheduler-pagination">
          <el-button size="small" :disabled="runPage <= 1" @click="previousRunPage">上一页</el-button>
          <el-button size="small" :disabled="runPage >= runTotalPages" @click="nextRunPage">下一页</el-button>
        </footer>
      </section>
    </template>
  </section>
</template>

<style scoped>
.ta-scheduler-management {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
  padding: 16px;
  box-sizing: border-box;
  overflow: auto;
  background: #f7f8fa;
  color: #1f2937;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}
.ta-scheduler-placeholder,
.ta-scheduler-alert {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
}
.ta-scheduler-alert {
  border-color: #fecaca;
  color: #b91c1c;
  background: #fef2f2;
}
.ta-scheduler-toolbar,
.ta-scheduler-filters,
.ta-scheduler-pagination {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ta-scheduler-toolbar-note {
  font-size: 12px;
  color: #6b7280;
}
.ta-scheduler-section {
  min-height: 0;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}
.ta-scheduler-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
}
.ta-scheduler-section-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.ta-scheduler-section-header span {
  font-size: 12px;
  color: #6b7280;
}
.ta-scheduler-diagnostics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid #eef0f3;
}
.ta-scheduler-diagnostic-group {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
  color: #111827;
  font-size: 12px;
}
.ta-scheduler-diagnostic-label {
  color: #6b7280;
  font-size: 11px;
}
.ta-scheduler-diagnostic-group strong {
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  word-break: break-word;
}
.ta-scheduler-blockers {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 14px;
}
.ta-scheduler-blocker,
.ta-scheduler-ok-text {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
}
.ta-scheduler-blocker {
  color: #b91c1c;
  background: #fef2f2;
}
.ta-scheduler-ok-text {
  color: #047857;
  background: #ecfdf5;
}
.ta-scheduler-table-scroll {
  overflow: auto;
}
.ta-scheduler-table {
  width: 100%;
  min-width: 1120px;
  border-collapse: collapse;
  font-size: 12px;
}
.ta-scheduler-table th,
.ta-scheduler-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #eef0f3;
  text-align: left;
  vertical-align: middle;
}
.ta-scheduler-table th {
  color: #6b7280;
  font-weight: 500;
  background: #fafafa;
}
.ta-scheduler-table tr.is-selected td {
  background: #eff6ff;
}
.ta-scheduler-table strong,
.ta-scheduler-table span {
  display: block;
}
.ta-scheduler-subtext {
  margin-top: 3px;
  color: #6b7280;
  font-size: 11px;
}
.ta-scheduler-cron-cell {
  min-width: 180px;
}
.ta-scheduler-actions {
  min-width: 230px;
  white-space: nowrap;
}
.ta-scheduler-actions :deep(.el-button + .el-button) {
  margin-left: 6px;
}
.ta-scheduler-filters,
.ta-scheduler-pagination {
  padding: 10px 12px;
  border-bottom: 1px solid #eef0f3;
}
.ta-scheduler-pagination {
  border-top: 1px solid #eef0f3;
  border-bottom: 0;
  justify-content: flex-end;
}
.ta-scheduler-filter {
  width: 140px;
}
.ta-status {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  max-width: 100%;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 22px;
  white-space: nowrap;
}
.ta-status.is-ok {
  color: #047857;
  background: #ecfdf5;
}
.ta-status.is-pending {
  color: #1d4ed8;
  background: #eff6ff;
}
.ta-status.is-bad {
  color: #b91c1c;
  background: #fef2f2;
}
.ta-status.is-muted {
  color: #4b5563;
  background: #f3f4f6;
}
.ta-status.is-neutral {
  color: #374151;
  background: #f5f5f5;
}
.is-compact {
  max-width: 220px;
  color: #4b5563;
  word-break: break-all;
}
.is-empty {
  text-align: center;
  color: #9ca3af;
}
</style>
