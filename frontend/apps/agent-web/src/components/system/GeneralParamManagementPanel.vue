<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Refresh, Clock } from "@element-plus/icons-vue";
import { ElMessage, ElDialog, ElButton, ElInput, ElForm, ElFormItem, ElDrawer, ElEmpty, ElTag } from "element-plus";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  GeneralParameter,
  CommonParameterChangeLog,
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

function refreshChangeLogs() {
  void changeLogsQuery.refetch();
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
</style>
