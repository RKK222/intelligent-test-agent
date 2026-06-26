<script setup lang="ts">
import { computed, inject, ref } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Edit, Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage, ElDialog, ElButton, ElInput, ElForm, ElFormItem } from "element-plus";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, GeneralParameter } from "@test-agent/shared-types";

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
const saving = ref(false);

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

const rows = computed(() => query.data.value?.items ?? []);
const total = computed(() => query.data.value?.total ?? 0);
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)));
const isFetching = computed(() => query.isFetching.value);
const errorMessage = computed(() => formatError(query.error.value));

const updateMutation = useMutation({
  mutationFn: ({ parameterId, value }: { parameterId: string; value: string }) =>
    api.updateGeneralParameter(parameterId, { value }),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ["common-parameters"] });
  },
  onError: (error) => {
    ElMessage.error(formatError(error) || "保存失败");
  }
});

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

// Dialog 编辑逻辑
const isDialogValueDirty = computed(() => {
  if (!editingParam.value) return false;
  const val = editingValue.value.trim();
  return !!val && val !== editingParam.value.parameterValue;
});

function openEditDialog(param: GeneralParameter) {
  editingParam.value = param;
  editingValue.value = param.parameterValue;
  editDialogOpen.value = true;
}

function closeEditDialog() {
  editDialogOpen.value = false;
  editingParam.value = null;
  editingValue.value = "";
}

async function submitEdit() {
  if (!editingParam.value) return;
  const value = editingValue.value.trim();
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
          <el-select v-model="draftPlatform" placeholder="平台" size="small" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="linux" value="linux" />
            <el-option label="windows" value="windows" />
            <el-option label="all" value="all" />
          </el-select>
          <el-button size="small" :icon="Search" @click="applyFilter">筛选</el-button>
          <el-button size="small" text @click="clearFilter">重置</el-button>
        </div>
        <span class="ta-common-param-toolbar-note">通用参数为系统级配置，仅可修改参数值，不可新增或删除。</span>
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
              <td>{{ param.chineseName }}</td>
              <td><span class="ta-common-param-tag">{{ param.platform }}</span></td>
              <td>
                <div class="ta-common-param-val-cell" @click="openEditDialog(param)" title="点击修改参数值">
                  <code class="ta-common-param-val-code">{{ param.parameterValue }}</code>
                  <span class="ta-common-param-edit-hint">点击修改</span>
                </div>
              </td>
              <td>{{ formatDate(param.updatedAt) }}</td>
              <td class="ta-common-param-actions">
                <el-button
                  size="small"
                  type="primary"
                  plain
                  @click="openEditDialog(param)"
                >编辑</el-button>
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
        width="560px"
        destroy-on-close
        :close-on-click-modal="false"
        class="ta-common-param-dialog"
      >
        <div v-if="editingParam" class="ta-common-param-form">
          <div class="ta-dialog-form-item">
            <span class="ta-dialog-form-label">参数英文名</span>
            <div class="ta-dialog-form-value ta-common-param-mono">{{ editingParam.englishName }}</div>
          </div>
          <div class="ta-dialog-form-item">
            <span class="ta-dialog-form-label">参数中文名</span>
            <div class="ta-dialog-form-value">{{ editingParam.chineseName }}</div>
          </div>
          <div class="ta-dialog-form-item">
            <span class="ta-dialog-form-label">适用平台</span>
            <div class="ta-dialog-form-value">
              <span class="ta-common-param-tag">{{ editingParam.platform }}</span>
            </div>
          </div>
          <div class="ta-dialog-form-item">
            <span class="ta-dialog-form-label">参数值</span>
            <el-input
              v-model="editingValue"
              type="textarea"
              :rows="4"
              placeholder="参数值不能为空"
              :disabled="saving"
            />
          </div>
        </div>
        <template #footer>
          <div class="ta-dialog-footer">
            <el-button @click="closeEditDialog" :disabled="saving">取消</el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!isDialogValueDirty"
              @click="submitEdit"
            >
              保存
            </el-button>
          </div>
        </template>
      </el-dialog>
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
.ta-common-param-alert {
  padding: 8px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
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
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  max-width: 320px;
  transition: all 0.2s ease;
}
.ta-common-param-val-cell:hover {
  background: #eff6ff;
  border-color: #bfdbfe;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}
.ta-common-param-val-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ta-common-param-val-cell:hover .ta-common-param-val-code {
  color: #1d4ed8;
}
.ta-common-param-edit-hint {
  font-size: 11px;
  color: #9ca3af;
  flex-shrink: 0;
}
.ta-common-param-val-cell:hover .ta-common-param-edit-hint {
  color: #3b82f6;
}

/* Dialog Form Styling */
.ta-common-param-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0;
}
.ta-dialog-form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ta-dialog-form-label {
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
}
.ta-dialog-form-value {
  font-size: 13px;
  color: #1f2937;
  padding: 6px 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  min-height: 32px;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}
.ta-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
