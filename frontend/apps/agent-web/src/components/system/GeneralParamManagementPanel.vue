<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Refresh, Clock, Monitor } from "@element-plus/icons-vue";
import { ElMessage, ElDialog, ElButton, ElInput, ElForm, ElFormItem, ElDrawer, ElEmpty, ElTag } from "element-plus";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  GeneralParameter,
  CommonParameterChangeLog,
  CommonParameterMemoryCluster,
  CommonParameterMemoryProcess,
  CommonParameterMemoryProcessStatus,
  RepositoryDeploymentOptions
} from "@test-agent/shared-types";

const PUBLIC_AGENT_GIT_PARAM = "OPENCODE_PUBLIC_AGENT_GIT_URL";
const EXTERNAL_DEPLOYMENT_MODE = "EXTERNAL";
const INTERNAL_DEPLOYMENT_MODE = "INTERNAL";
const DEFAULT_REPOSITORY_DEPLOYMENT_OPTIONS: RepositoryDeploymentOptions = {
  defaultDeploymentMode: EXTERNAL_DEPLOYMENT_MODE,
  internalSshPrefix: "",
  options: [
    { mode: EXTERNAL_DEPLOYMENT_MODE, label: "外部部署" },
    { mode: INTERNAL_DEPLOYMENT_MODE, label: "内部部署" }
  ]
};
const MEMORY_VALUES_QUERY_KEY = ["common-parameter-memory-values"] as const;

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();

const page = ref(1);
const size = ref(50);
const draftPlatform = ref("");
const activePlatform = ref("");

// 编辑通用参数相关状态
const editDialogOpen = ref(false);
const editingParam = ref<GeneralParameter | null>(null);
const editingValue = ref("");
const editPublicAgentGitDeploymentMode = ref(EXTERNAL_DEPLOYMENT_MODE);
const saving = ref(false);

// 修改历史抽屉状态
const changeLogsDrawerOpen = ref(false);
const changeLogsParam = ref<GeneralParameter | null>(null);

// JVM 内存参数只在运维抽屉打开时查询，避免通用参数页面常驻轮询集群。
const memoryValuesDrawerOpen = ref(false);
const refreshingMemoryProcessId = ref<string | null>(null);

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);

const params = computed(() => ({
  platform: activePlatform.value || undefined,
  page: page.value,
  size: size.value
}));

const query = useQuery({
  queryKey: computed(() => ["common-parameters", page.value, size.value, activePlatform.value]),
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.listGeneralParameters(params.value)
});

const repositoryDeploymentOptionsQuery = useQuery({
  queryKey: ["repository-deployment-options"],
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getRepositoryDeploymentOptions()
});

const rows = computed(() => query.data.value?.items ?? []);
const total = computed(() => query.data.value?.total ?? 0);
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)));
const isFetching = computed(() => query.isFetching.value);
const errorMessage = computed(() => formatError(query.error.value));
const repositoryDeploymentOptions = computed(() =>
  repositoryDeploymentOptionsQuery.data.value ?? DEFAULT_REPOSITORY_DEPLOYMENT_OPTIONS
);
const currentRepositoryDeploymentMode = computed(() =>
  repositoryDeploymentOptions.value.defaultDeploymentMode || EXTERNAL_DEPLOYMENT_MODE
);
const internalSshPrefix = computed(() => {
  if (repositoryDeploymentOptions.value.internalSshPrefix) return repositoryDeploymentOptions.value.internalSshPrefix;
  return props.currentUser?.unifiedAuthId ? `ssh://${props.currentUser.unifiedAuthId}@` : "ssh://";
});
const publicAgentGitModeLabel = computed(() => deploymentModeLabel(currentRepositoryDeploymentMode.value));
const publicAgentGitValueRule = computed(() => {
  return publicAgentGitRuleForMode(currentRepositoryDeploymentMode.value);
});
const publicAgentGitToolbarNote = computed(() => {
  if (repositoryDeploymentOptionsQuery.error.value) {
    return "公共 Git 部署模式读取失败，请刷新后再修改该参数。";
  }
  return publicAgentGitValueRule.value
    ? `公共 Git 当前为${publicAgentGitModeLabel.value}：${publicAgentGitValueRule.value}`
    : `公共 Git 当前为${publicAgentGitModeLabel.value}`;
});
const editPublicAgentGitDeploymentModeLabel = computed(() => deploymentModeLabel(editPublicAgentGitDeploymentMode.value));
const editPublicAgentGitModeHint = computed(() =>
  publicAgentGitRuleForMode(editPublicAgentGitDeploymentMode.value)
);

const updateMutation = useMutation({
  mutationFn: ({ parameterId, value }: { parameterId: string; value: string }) =>
    api.updateGeneralParameter(parameterId, { value }),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ["common-parameters"] });
    queryClient.invalidateQueries({ queryKey: ["common-parameter-change-logs"] });
    queryClient.invalidateQueries({ queryKey: ["opencode-runtime-management"] });
  },
  onError: (error) => {
    ElMessage.error(formatError(error) || "保存失败");
  }
});

// 修改历史查询
const changeLogsQuery = useQuery({
  queryKey: computed(() => ["common-parameter-change-logs", changeLogsParam.value?.parameterId]),
  enabled: () => hasSuperAdmin.value && changeLogsDrawerOpen.value && !!changeLogsParam.value?.parameterId,
  retry: false,
  queryFn: () => api.listCommonParameterChangeLogs(changeLogsParam.value!.parameterId)
});

watch(changeLogsDrawerOpen, (open) => {
  if (open && changeLogsParam.value) {
    void changeLogsQuery.refetch();
  }
});

const changeLogs = computed<CommonParameterChangeLog[]>(() => changeLogsQuery.data.value ?? []);
const changeLogsFetching = computed(() => changeLogsQuery.isFetching.value);
const changeLogsError = computed(() => formatError(changeLogsQuery.error.value));

const memoryValuesQuery = useQuery({
  queryKey: MEMORY_VALUES_QUERY_KEY,
  enabled: () => hasSuperAdmin.value && memoryValuesDrawerOpen.value,
  retry: false,
  queryFn: () => api.listCommonParameterMemoryValues()
});

const memoryCluster = computed<CommonParameterMemoryCluster | null>(() => memoryValuesQuery.data.value ?? null);
const memoryProcesses = computed<CommonParameterMemoryProcess[]>(() => memoryCluster.value?.processes ?? []);
const memoryValuesFetching = computed(() => memoryValuesQuery.isFetching.value);
const memoryValuesError = computed(() => formatError(memoryValuesQuery.error.value));
const memoryPartialNotice = computed(() => {
  const result = memoryCluster.value;
  if (!result || (result.partiallySuccessfulProcesses === 0 && result.failedProcesses === 0)) return "";
  return `${result.partiallySuccessfulProcesses} 个 Java 部分成功，${result.failedProcesses} 个 Java 失败或不可用；其余成功结果已保留。`;
});

const refreshAllMemoryMutation = useMutation({
  mutationFn: () => api.refreshCommonParameterMemoryValues(),
  onSuccess: (result) => {
    queryClient.setQueryData(MEMORY_VALUES_QUERY_KEY, result);
  },
  onError: (error) => {
    ElMessage.error(formatError(error) || "刷新全部 Java 失败");
  }
});

const refreshOneMemoryMutation = useMutation({
  mutationFn: (backendProcessId: string) => api.refreshCommonParameterMemoryValuesForProcess(backendProcessId),
  onSuccess: (process) => {
    queryClient.setQueryData<CommonParameterMemoryCluster>(
      MEMORY_VALUES_QUERY_KEY,
      replaceMemoryProcess(memoryCluster.value, process)
    );
  },
  onError: (error) => {
    ElMessage.error(formatError(error) || "刷新此 Java 失败");
  },
  onSettled: () => {
    refreshingMemoryProcessId.value = null;
  }
});

const refreshingAllMemoryValues = computed(() => refreshAllMemoryMutation.isPending.value);

function refreshChangeLogs() {
  void changeLogsQuery.refetch();
}

function openMemoryValuesDrawer() {
  memoryValuesDrawerOpen.value = true;
}

function closeMemoryValuesDrawer() {
  memoryValuesDrawerOpen.value = false;
}

function refreshMemoryValuesQuery() {
  void memoryValuesQuery.refetch();
}

async function refreshAllMemoryValues() {
  try {
    const result = await refreshAllMemoryMutation.mutateAsync();
    if (result.partiallySuccessfulProcesses > 0 || result.failedProcesses > 0) {
      ElMessage.warning("刷新已完成，部分 Java 未完全成功，请查看逐进程结果");
    } else {
      ElMessage.success("全部在线 Java 已按数据库值刷新");
    }
  } catch {
    // mutation 统一展示安全错误。
  }
}

async function refreshOneMemoryValue(process: CommonParameterMemoryProcess) {
  refreshingMemoryProcessId.value = process.backendProcessId;
  try {
    const result = await refreshOneMemoryMutation.mutateAsync(process.backendProcessId);
    if (result.status === "SUCCESS") {
      ElMessage.success(`Java ${process.backendProcessId} 已按数据库值刷新`);
    } else {
      ElMessage.warning(`Java ${process.backendProcessId} 刷新完成，请查看失败项`);
    }
  } catch {
    // mutation 统一展示安全错误。
  }
}

function replaceMemoryProcess(
  current: CommonParameterMemoryCluster | null,
  process: CommonParameterMemoryProcess
): CommonParameterMemoryCluster {
  const existing = current?.processes ?? [];
  const processes = existing.some((item) => item.backendProcessId === process.backendProcessId)
    ? existing.map((item) => item.backendProcessId === process.backendProcessId ? process : item)
    : [...existing, process];
  processes.sort((left, right) =>
    left.linuxServerId.localeCompare(right.linuxServerId) || left.backendProcessId.localeCompare(right.backendProcessId)
  );
  return {
    capturedAt: process.capturedAt,
    totalProcesses: processes.length,
    successfulProcesses: processes.filter((item) => item.status === "SUCCESS").length,
    partiallySuccessfulProcesses: processes.filter((item) => item.status === "PARTIAL").length,
    failedProcesses: processes.filter((item) => item.status === "FAILED" || item.status === "UNAVAILABLE").length,
    processes
  };
}

function processStatusLabel(status: CommonParameterMemoryProcessStatus) {
  return ({
    SUCCESS: "成功",
    PARTIAL: "部分成功",
    FAILED: "失败",
    UNAVAILABLE: "不可用"
  } as const)[status];
}

function processStatusTag(status: CommonParameterMemoryProcessStatus) {
  if (status === "SUCCESS") return "success";
  if (status === "PARTIAL") return "warning";
  if (status === "FAILED") return "danger";
  return "info";
}

function refresh() {
  void query.refetch();
}

function applyFilter() {
  activePlatform.value = draftPlatform.value;
  page.value = 1;
}

function clearFilter() {
  draftPlatform.value = "";
  activePlatform.value = "";
  page.value = 1;
}

function previousPage() {
  page.value = Math.max(1, page.value - 1);
}

function nextPage() {
  page.value = Math.min(totalPages.value, page.value + 1);
}

function deploymentModeLabel(mode: string) {
  return repositoryDeploymentOptions.value.options.find((item) => item.mode === mode)?.label ?? mode;
}

function publicAgentGitRuleForMode(mode: string) {
  if (mode === INTERNAL_DEPLOYMENT_MODE) {
    return `参数值只填写 host[:port]/path；后端会按当前用户拼接 ${internalSshPrefix.value}。`;
  }
  return "";
}

function isPublicAgentGitParam(param?: GeneralParameter | null) {
  return param?.englishName === PUBLIC_AGENT_GIT_PARAM;
}

function publicAgentGitHint(param?: GeneralParameter | null) {
  return isPublicAgentGitParam(param) ? publicAgentGitValueRule.value : "";
}

function isInternalPublicAgentGitValue(value: string) {
  const normalized = value.trim();
  if (!normalized || normalized.toUpperCase() === "UNCONFIGURED") return false;
  return !normalized.includes("://") && !normalized.includes("@") && normalized.includes("/") && !/\s/.test(normalized);
}

function inferPublicAgentGitDeploymentMode(value: string) {
  if (isInternalPublicAgentGitValue(value)) return INTERNAL_DEPLOYMENT_MODE;
  const currentMode = currentRepositoryDeploymentMode.value;
  if (!value.trim() || value.trim().toUpperCase() === "UNCONFIGURED") return currentMode;
  return EXTERNAL_DEPLOYMENT_MODE;
}

function normalizePublicAgentGitInputForMode(value: string, mode: string) {
  const normalized = value.trim();
  if (mode !== INTERNAL_DEPLOYMENT_MODE) return normalized;
  if (normalized.startsWith(internalSshPrefix.value)) {
    return normalized.slice(internalSshPrefix.value.length);
  }
  if (normalized.toLowerCase().startsWith("ssh://")) {
    const rest = normalized.slice("ssh://".length);
    const at = rest.indexOf("@");
    return at > 0 ? rest.slice(at + 1) : normalized;
  }
  return normalized;
}

function editValueForSubmit() {
  if (!isPublicAgentGitParam(editingParam.value)) return editingValue.value.trim();
  return normalizePublicAgentGitInputForMode(editingValue.value, editPublicAgentGitDeploymentMode.value);
}

// Dialog 编辑逻辑
const isDialogValueDirty = computed(() => {
  if (!editingParam.value) return false;
  const val = editValueForSubmit();
  return !!val && val !== editingParam.value.parameterValue;
});

function openEditDialog(param: GeneralParameter) {
  editingParam.value = param;
  if (isPublicAgentGitParam(param)) {
    editPublicAgentGitDeploymentMode.value = inferPublicAgentGitDeploymentMode(param.parameterValue);
    editingValue.value = normalizePublicAgentGitInputForMode(param.parameterValue, editPublicAgentGitDeploymentMode.value);
  } else {
    editPublicAgentGitDeploymentMode.value = EXTERNAL_DEPLOYMENT_MODE;
    editingValue.value = param.parameterValue;
  }
  editDialogOpen.value = true;
}

function closeEditDialog() {
  editDialogOpen.value = false;
  editingParam.value = null;
  editingValue.value = "";
  editPublicAgentGitDeploymentMode.value = EXTERNAL_DEPLOYMENT_MODE;
}

function handleEditPublicAgentGitDeploymentModeChange(mode: string) {
  editPublicAgentGitDeploymentMode.value = mode;
  editingValue.value = normalizePublicAgentGitInputForMode(editingValue.value, mode);
}

async function submitEdit() {
  if (!editingParam.value) return;
  const value = editValueForSubmit();
  if (!value || value === editingParam.value.parameterValue) return;

  saving.value = true;
  try {
    await updateMutation.mutateAsync({ parameterId: editingParam.value.parameterId, value });
    ElMessage.success(`已更新参数：${editingParam.value.englishName}`);
    closeEditDialog();
  } catch (err) {
    // Error is handled by mutation onError
  } finally {
    saving.value = false;
  }
}

function openChangeLogsDrawer(param: GeneralParameter) {
  changeLogsParam.value = param;
  changeLogsDrawerOpen.value = true;
  // 抽屉打开时立即按当前参数重新拉取，避免复用上一条参数的历史缓存。
  void queryClient.invalidateQueries({ queryKey: ["common-parameter-change-logs"] });
}

function closeChangeLogsDrawer() {
  changeLogsDrawerOpen.value = false;
  changeLogsParam.value = null;
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

function formatFullDate(value?: string | null) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  }).format(new Date(value));
}

function formatError(error: unknown) {
  if (!error) {
    return "";
  }
  if (error instanceof BackendApiError) {
    return `${error.message}（${error.code}）`;
  }
  return error instanceof Error ? error.message : "通用参数数据加载失败";
}
</script>

<template>
  <section class="ta-common-param-management" @dragstart.prevent>
    <div v-if="!hasSuperAdmin" class="ta-common-param-placeholder">当前账号无通用参数管理权限</div>
    <template v-else>
      <div class="ta-common-param-toolbar">
        <el-button size="small" :icon="Refresh" :loading="isFetching" @click="refresh">刷新</el-button>
        <el-button size="small" :icon="Monitor" plain @click="openMemoryValuesDrawer">查看内存加载值</el-button>
        <div class="ta-common-param-filter">
          <el-select v-model="draftPlatform" placeholder="平台" size="small" clearable filterable style="width: 140px" @change="applyFilter">
            <el-option label="全部" value="" />
            <el-option label="linux" value="linux" />
            <el-option label="windows" value="windows" />
            <el-option label="macos" value="macos" />
            <el-option label="all" value="all" />
          </el-select>
          <el-button size="small" text @click="clearFilter">重置</el-button>
        </div>
        <span class="ta-common-param-toolbar-note">通用参数为系统级配置，仅可修改参数值，不可新增或删除；标记为只读的参数不可在前端修改。</span>
        <span class="ta-common-param-toolbar-note ta-common-param-toolbar-note-strong">{{ publicAgentGitToolbarNote }}</span>
      </div>

      <div v-if="errorMessage" class="ta-common-param-alert" role="alert">{{ errorMessage }}</div>

      <div class="ta-common-param-table-wrapper">
        <table class="ta-common-param-table">
          <thead>
            <tr>
              <th>参数英文名</th>
              <th>参数中文名</th>
              <th>平台</th>
              <th>参数值</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="rows.length === 0">
              <td colspan="6" class="ta-common-param-empty">暂无通用参数</td>
            </tr>
            <tr v-for="param in rows" :key="param.parameterId">
              <td class="ta-common-param-mono">{{ param.englishName }}</td>
              <td>
                <div class="ta-common-param-name-cell">
                  <span>{{ param.chineseName }}</span>
                  <span v-if="publicAgentGitHint(param)" class="ta-common-param-row-hint">{{ publicAgentGitHint(param) }}</span>
                </div>
              </td>
              <td><span class="ta-common-param-tag">{{ param.platform }}</span></td>
              <td>
                <div
                  class="ta-common-param-val-cell"
                  :class="{ 'is-readonly': !param.editable }"
                  @click="openEditDialog(param)"
                  :title="param.editable ? '点击修改参数值' : '只读参数，点击查看'"
                >
                  <code class="ta-common-param-val-code">{{ param.parameterValue }}</code>
                  <span v-if="!param.editable" class="ta-common-param-readonly-mark" aria-label="只读参数">🔒</span>
                </div>
              </td>
              <td>{{ formatDate(param.updatedAt) }}</td>
              <td class="ta-common-param-actions">
                <el-button
                  size="small"
                  plain
                  :icon="Clock"
                  @click="openChangeLogsDrawer(param)"
                >修改历史</el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="ta-common-param-pagination">
        <span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
        <div>
          <el-button size="small" :disabled="page <= 1" @click="previousPage">上一页</el-button>
          <el-button size="small" :disabled="page >= totalPages" @click="nextPage">下一页</el-button>
        </div>
      </footer>

      <!-- 编辑通用参数 Dialog -->
      <el-dialog
        v-model="editDialogOpen"
        title="修改通用参数"
        width="540px"
        destroy-on-close
        :close-on-click-modal="false"
        align-center
        class="ta-common-param-dialog"
      >
        <el-form v-if="editingParam" label-width="120px" class="ta-common-param-edit-form">
          <el-form-item label="参数英文名">
            <span class="ta-common-param-readonly-field ta-common-param-mono">{{ editingParam.englishName }}</span>
          </el-form-item>
          <el-form-item label="参数中文名">
            <span class="ta-common-param-readonly-field">{{ editingParam.chineseName }}</span>
          </el-form-item>
          <el-form-item label="适用平台">
            <span class="ta-common-param-readonly-field">
              <span class="ta-common-param-tag">{{ editingParam.platform }}</span>
            </span>
          </el-form-item>
          <el-form-item v-if="isPublicAgentGitParam(editingParam)" label="部署模式">
            <el-select
              :model-value="editPublicAgentGitDeploymentMode"
              aria-label="公共 Git 部署模式"
              style="width: 160px"
              filterable
              @update:model-value="handleEditPublicAgentGitDeploymentModeChange"
            >
              <el-option
                v-for="option in repositoryDeploymentOptions.options"
                :key="option.mode"
                :label="option.label"
                :value="option.mode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="参数值">
            <div class="ta-common-param-edit-value">
              <div class="ta-common-param-edit-value-row">
                <div
                  v-if="isPublicAgentGitParam(editingParam) && editPublicAgentGitDeploymentMode === INTERNAL_DEPLOYMENT_MODE"
                  class="ta-common-param-git-url-input-group"
                >
                  <span class="ta-common-param-git-url-prefix">{{ internalSshPrefix }}</span>
                  <el-input
                    v-model="editingValue"
                    placeholder="host[:port]/path"
                    :disabled="saving || !editingParam?.editable"
                  />
                </div>
                <el-input
                  v-else
                  v-model="editingValue"
                  placeholder="Git URL"
                  :disabled="saving || !editingParam?.editable"
                />
                <el-tag v-if="editingParam && !editingParam.editable" size="small" type="info">只读参数</el-tag>
              </div>
              <div v-if="editingParam && !editingParam.editable" class="ta-common-param-readonly-alert" role="alert">
                只读参数，修改后将影响系统正常运行
              </div>
              <div
                v-if="isPublicAgentGitParam(editingParam) && editPublicAgentGitModeHint"
                class="ta-common-param-mode-hint"
                role="note"
              >
                已选择{{ editPublicAgentGitDeploymentModeLabel }}。{{ editPublicAgentGitModeHint }}
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="ta-dialog-footer">
            <el-button @click="closeEditDialog" :disabled="saving">取消</el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!isDialogValueDirty || !editingParam?.editable"
              @click="submitEdit"
            >
              保存
            </el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 修改历史抽屉 -->
      <el-drawer
        v-model="changeLogsDrawerOpen"
        :title="changeLogsParam ? `修改历史 - ${changeLogsParam.englishName}` : '修改历史'"
        direction="rtl"
        size="50%"
        destroy-on-close
        @close="closeChangeLogsDrawer"
      >
        <div class="ta-change-logs">
          <div v-if="changeLogsParam" class="ta-change-log-param-card">
            <div class="ta-change-log-param-card-header">
              <span class="ta-common-param-mono">{{ changeLogsParam.englishName }}</span>
              <span class="ta-common-param-tag">{{ changeLogsParam.platform }}</span>
            </div>
            <div class="ta-change-log-param-grid">
              <span>参数中文名</span>
              <strong>{{ changeLogsParam.chineseName }}</strong>
              <span>当前参数值</span>
              <code class="ta-common-param-mono">{{ changeLogsParam.parameterValue }}</code>
              <span>更新时间</span>
              <strong>{{ formatFullDate(changeLogsParam.updatedAt) }}</strong>
            </div>
          </div>
          <div class="ta-change-logs-toolbar">
            <el-button size="small" :icon="Refresh" :loading="changeLogsFetching" @click="refreshChangeLogs">刷新</el-button>
            <span class="ta-change-logs-note">展示该参数最近 50 条修改记录</span>
          </div>
          <div v-if="changeLogsError" class="ta-common-param-alert" role="alert">{{ changeLogsError }}</div>
          <el-empty v-if="!changeLogsFetching && changeLogs.length === 0" description="暂无修改历史" />
          <div v-if="changeLogs.length > 0" class="ta-change-logs-table-wrapper">
            <table class="ta-change-logs-table">
              <thead>
                <tr>
                  <th>修改时间</th>
                  <th>修改用户</th>
                  <th>修改前值</th>
                  <th>修改后值</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="log in changeLogs" :key="log.logId">
                  <td class="ta-change-logs-time">{{ formatFullDate(log.createdAt) }}</td>
                  <td>{{ log.changedByUsername || '-' }}</td>
                  <td class="ta-common-param-mono ta-change-logs-val">{{ log.oldValue || '-' }}</td>
                  <td class="ta-common-param-mono ta-change-logs-val">{{ log.newValue }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </el-drawer>

      <!-- JVM 内存参数诊断抽屉：只管理显式注册项，多数通用参数仍按需直读数据库。 -->
      <el-drawer
        v-model="memoryValuesDrawerOpen"
        title="JVM 内存参数值"
        direction="rtl"
        size="72%"
        destroy-on-close
        @close="closeMemoryValuesDrawer"
      >
        <div class="ta-memory-values">
          <div class="ta-memory-values-toolbar">
            <div class="ta-memory-values-summary" aria-label="Java 进程刷新汇总">
              <span><strong>{{ memoryCluster?.totalProcesses ?? 0 }}</strong> 个在线 Java</span>
              <span class="is-success"><strong>{{ memoryCluster?.successfulProcesses ?? 0 }}</strong> 成功</span>
              <span class="is-warning"><strong>{{ memoryCluster?.partiallySuccessfulProcesses ?? 0 }}</strong> 部分成功</span>
              <span class="is-danger"><strong>{{ memoryCluster?.failedProcesses ?? 0 }}</strong> 失败</span>
            </div>
            <div class="ta-memory-values-actions">
              <el-button
                size="small"
                :icon="Refresh"
                :loading="memoryValuesFetching"
                :disabled="refreshingAllMemoryValues"
                @click="refreshMemoryValuesQuery"
              >重新查询</el-button>
              <el-button
                size="small"
                type="primary"
                :loading="refreshingAllMemoryValues"
                :disabled="memoryValuesFetching || refreshingMemoryProcessId !== null"
                @click="refreshAllMemoryValues"
              >刷新全部 Java</el-button>
            </div>
          </div>

          <p class="ta-memory-values-note">
            仅展示已显式加载到 JVM 内存的通用参数。手工刷新会从数据库重新读取，不修改数据库，也不会重复发布广播。
          </p>
          <div v-if="memoryPartialNotice" class="ta-memory-values-warning" role="status">{{ memoryPartialNotice }}</div>
          <div v-if="memoryValuesError" class="ta-common-param-alert" role="alert">{{ memoryValuesError }}</div>
          <div v-if="memoryValuesFetching && memoryProcesses.length === 0" class="ta-memory-values-loading">正在查询在线 Java 的内存值…</div>
          <el-empty
            v-if="!memoryValuesFetching && !memoryValuesError && memoryProcesses.length === 0"
            description="暂无已注册的 JVM 内存参数"
          />

          <div v-if="memoryProcesses.length > 0" class="ta-memory-process-list">
            <article
              v-for="process in memoryProcesses"
              :key="process.backendProcessId"
              class="ta-memory-process-card"
              :class="`is-${process.status.toLowerCase()}`"
            >
              <header class="ta-memory-process-header">
                <div class="ta-memory-process-identity">
                  <div class="ta-memory-process-title">
                    <code class="ta-common-param-mono">{{ process.backendProcessId }}</code>
                    <el-tag size="small" :type="processStatusTag(process.status)">
                      {{ processStatusLabel(process.status) }}
                    </el-tag>
                  </div>
                  <span>{{ process.linuxServerId }} · {{ process.listenUrl }}</span>
                </div>
                <el-button
                  size="small"
                  plain
                  :loading="refreshingMemoryProcessId === process.backendProcessId"
                  :disabled="refreshingAllMemoryValues || (refreshingMemoryProcessId !== null && refreshingMemoryProcessId !== process.backendProcessId)"
                  @click="refreshOneMemoryValue(process)"
                >刷新此 Java</el-button>
              </header>

              <div class="ta-memory-process-meta">
                <span>实例标识</span><code>{{ process.instanceId || '-' }}</code>
                <span>采集时间</span><strong>{{ formatFullDate(process.capturedAt) }}</strong>
              </div>
              <div v-if="process.errorMessage" class="ta-memory-process-error" role="status">
                {{ process.errorMessage }}<span v-if="process.errorCode">（{{ process.errorCode }}）</span>
              </div>

              <div v-if="process.parameters.length > 0" class="ta-memory-param-table-wrapper">
                <table class="ta-memory-param-table">
                  <thead>
                    <tr>
                      <th>参数键</th>
                      <th>数据库加载源值</th>
                      <th>内存生效值</th>
                      <th>加载时间</th>
                      <th>最近刷新</th>
                      <th>状态</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="parameter in process.parameters" :key="`${parameter.englishName}:${parameter.platform}`">
                      <td>
                        <code class="ta-common-param-mono">{{ parameter.englishName }}</code>
                        <span class="ta-memory-param-platform">{{ parameter.platform }}</span>
                      </td>
                      <td><code class="ta-memory-value-code">{{ parameter.sourceValue ?? '-' }}</code></td>
                      <td><code class="ta-memory-value-code is-effective">{{ parameter.memoryValue ?? '-' }}</code></td>
                      <td>{{ formatFullDate(parameter.loadedAt) }}</td>
                      <td>{{ formatFullDate(parameter.lastRefreshAttemptAt) }}</td>
                      <td>
                        <el-tag size="small" :type="parameter.refreshStatus === 'SUCCESS' ? 'success' : 'danger'">
                          {{ parameter.refreshStatus === 'SUCCESS' ? '成功' : '失败' }}
                        </el-tag>
                        <div v-if="parameter.errorMessage" class="ta-memory-param-error">{{ parameter.errorMessage }}</div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else-if="process.status !== 'UNAVAILABLE'" class="ta-memory-process-empty">本进程暂无已注册内存参数</div>
            </article>
          </div>
        </div>
      </el-drawer>
    </template>
  </section>
</template>

<style scoped>
.ta-common-param-management {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 16px;
  gap: 12px;
  background: #fff;
  color: #1f2937;
  box-sizing: border-box;
  overflow: auto;
}
.ta-common-param-placeholder {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  color: #6b7280;
  font-size: 13px;
}
.ta-common-param-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.ta-common-param-filter {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ta-common-param-toolbar-note {
  color: #6b7280;
  font-size: 12px;
}
.ta-common-param-toolbar-note-strong {
  color: #374151;
}
.ta-common-param-alert {
  padding: 8px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
}
.ta-common-param-readonly-alert {
  padding: 8px 12px;
  border: 1px solid #fcd9b6;
  border-radius: 6px;
  background: #fffbeb;
  color: #b45309;
  font-size: 12px;
}
.ta-common-param-mode-hint {
  padding: 8px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  line-height: 1.5;
}
.ta-change-log-param-card {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}
.ta-change-log-param-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
}
.ta-change-log-param-grid {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px 12px;
  font-size: 12px;
  color: #6b7280;
}
.ta-change-log-param-grid strong,
.ta-change-log-param-grid code {
  min-width: 0;
  color: #111827;
  font-weight: 500;
  word-break: break-all;
}
.ta-common-param-table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
.ta-common-param-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.ta-common-param-table thead th {
  position: sticky;
  top: 0;
  background: #f9fafb;
  text-align: left;
  padding: 8px 12px;
  font-weight: 600;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
  white-space: nowrap;
}
.ta-common-param-table tbody td {
  padding: 8px 12px;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
  word-break: break-all;
}
.ta-common-param-table tbody tr:hover {
  background: #f9fafb;
}
.ta-common-param-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #111827;
}
.ta-common-param-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ta-common-param-row-hint {
  color: #6b7280;
  font-size: 12px;
  line-height: 1.4;
  word-break: normal;
}
.ta-common-param-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
}
.ta-common-param-empty {
  text-align: center;
  color: #9ca3af;
  padding: 24px;
}
.ta-common-param-actions {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}
.ta-common-param-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
}
.ta-common-param-val-cell {
  display: flex;
  align-items: center;
  padding: 4px 10px;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  width: 320px;
  max-width: 100%;
  box-sizing: border-box;
  transition: all 0.2s ease;
}
.ta-common-param-val-cell:hover {
  background: #eff6ff;
  border-color: #bfdbfe;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}
.ta-common-param-val-cell.is-readonly {
  background: #f3f4f6;
  border-color: #e5e7eb;
  cursor: not-allowed;
}
.ta-common-param-val-cell.is-readonly:hover {
  background: #f3f4f6;
  border-color: #e5e7eb;
  box-shadow: none;
}
.ta-common-param-val-cell.is-readonly:hover .ta-common-param-val-code {
  color: #6b7280;
}
.ta-common-param-readonly-mark {
  margin-left: 6px;
  font-size: 12px;
  color: #9ca3af;
}
.ta-common-param-val-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
}
.ta-common-param-val-cell:hover .ta-common-param-val-code {
  color: #1d4ed8;
}

.ta-common-param-edit-form {
  padding: 4px 0 12px;
}
.ta-common-param-readonly-field {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  max-width: 100%;
  color: #606266;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.ta-common-param-edit-value {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.ta-common-param-edit-value-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.ta-common-param-git-url-input-group {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}
.ta-common-param-git-url-prefix {
  flex-shrink: 0;
  max-width: 210px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 8px;
  height: 32px;
  line-height: 32px;
  font-size: 12px;
  color: #606266;
  border: 1px solid #dcdfe6;
  border-right: 0;
  border-radius: 4px 0 0 4px;
  background: #f5f7fa;
}
.ta-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 修改历史抽屉 */
.ta-change-logs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ta-change-logs-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ta-change-logs-note {
  color: #6b7280;
  font-size: 12px;
}
.ta-change-logs-table-wrapper {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: auto;
}
.ta-change-logs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.ta-change-logs-table thead th {
  text-align: left;
  padding: 8px 12px;
  font-weight: 600;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
  white-space: nowrap;
}
.ta-change-logs-table tbody td {
  padding: 8px 12px;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: top;
  word-break: break-all;
}
.ta-change-logs-table tbody tr:hover {
  background: #f9fafb;
}
.ta-change-logs-time {
  white-space: nowrap;
  color: #6b7280;
}
.ta-change-logs-val {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* JVM 内存值抽屉：以进程为诊断边界，保留同服务器多个 Java 的独立状态。 */
.ta-memory-values {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.ta-memory-values-toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.ta-memory-values-summary,
.ta-memory-values-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ta-memory-values-summary span {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #f9fafb;
  color: #4b5563;
  font-size: 12px;
}
.ta-memory-values-summary strong {
  color: #111827;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 14px;
}
.ta-memory-values-summary .is-success strong { color: #047857; }
.ta-memory-values-summary .is-warning strong { color: #b45309; }
.ta-memory-values-summary .is-danger strong { color: #b91c1c; }
.ta-memory-values-note {
  margin: 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.6;
}
.ta-memory-values-warning {
  padding: 8px 12px;
  border: 1px solid #fcd34d;
  border-radius: 6px;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
}
.ta-memory-values-loading,
.ta-memory-process-empty {
  padding: 24px;
  color: #6b7280;
  text-align: center;
  font-size: 13px;
}
.ta-memory-process-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ta-memory-process-card {
  overflow: hidden;
  border: 1px solid #dbe3ef;
  border-left: 4px solid #10b981;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.ta-memory-process-card.is-partial { border-left-color: #f59e0b; }
.ta-memory-process-card.is-failed { border-left-color: #ef4444; }
.ta-memory-process-card.is-unavailable { border-left-color: #94a3b8; }
.ta-memory-process-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid #edf1f6;
  background: #f8fafc;
}
.ta-memory-process-identity {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
  color: #64748b;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.ta-memory-process-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.ta-memory-process-meta {
  display: grid;
  grid-template-columns: 72px minmax(140px, 1fr) 72px minmax(140px, 1fr);
  gap: 8px 12px;
  padding: 10px 14px;
  color: #64748b;
  font-size: 12px;
}
.ta-memory-process-meta code,
.ta-memory-process-meta strong {
  min-width: 0;
  color: #1f2937;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.ta-memory-process-error {
  margin: 0 14px 10px;
  padding: 7px 10px;
  border-radius: 4px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
}
.ta-memory-param-table-wrapper {
  overflow: auto;
  border-top: 1px solid #edf1f6;
}
.ta-memory-param-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.ta-memory-param-table th {
  padding: 8px 10px;
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}
.ta-memory-param-table td {
  padding: 9px 10px;
  border-top: 1px solid #f1f5f9;
  color: #475569;
  vertical-align: top;
  white-space: nowrap;
}
.ta-memory-param-table td:first-child {
  min-width: 220px;
  white-space: normal;
}
.ta-memory-param-platform {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 8px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
}
.ta-memory-value-code {
  display: block;
  max-width: 260px;
  overflow: hidden;
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  text-overflow: ellipsis;
  white-space: pre;
}
.ta-memory-value-code.is-effective {
  color: #0f766e;
  font-weight: 600;
}
.ta-memory-param-error {
  max-width: 220px;
  margin-top: 5px;
  color: #b91c1c;
  white-space: normal;
}

@media (max-width: 900px) {
  .ta-memory-values-toolbar,
  .ta-memory-process-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .ta-memory-process-meta {
    grid-template-columns: 72px minmax(0, 1fr);
  }
}
</style>
