<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
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
const valueDrafts = ref<Record<string, string>>({});
const savingId = ref<string>("");

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

watch(
  rows,
  (items) => {
    const drafts = { ...valueDrafts.value };
    items.forEach((param) => {
      drafts[param.parameterId] ??= param.parameterValue;
    });
    valueDrafts.value = drafts;
  },
  { immediate: true }
);

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

function isDirty(param: GeneralParameter) {
  const draft = valueDrafts.value[param.parameterId]?.trim();
  return !!draft && draft !== param.parameterValue;
}

function resetDraft(param: GeneralParameter) {
  valueDrafts.value = { ...valueDrafts.value, [param.parameterId]: param.parameterValue };
}

async function saveValue(param: GeneralParameter) {
  const value = valueDrafts.value[param.parameterId]?.trim();
  if (!value || value === param.parameterValue) {
    return;
  }
  savingId.value = param.parameterId;
  try {
    await updateMutation.mutateAsync({ parameterId: param.parameterId, value });
    ElMessage.success(`已更新参数：${param.englishName}`);
  } finally {
    savingId.value = "";
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
                <el-input
                  v-model="valueDrafts[param.parameterId]"
                  size="small"
                  placeholder="参数值不能为空"
                  :disabled="savingId === param.parameterId"
                />
              </td>
              <td>{{ formatDate(param.updatedAt) }}</td>
              <td class="ta-common-param-actions">
                <el-button
                  size="small"
                  type="primary"
                  :disabled="!isDirty(param)"
                  :loading="savingId === param.parameterId"
                  @click="saveValue(param)"
                >保存</el-button>
                <el-button
                  size="small"
                  text
                  :disabled="!isDirty(param) || savingId === param.parameterId"
                  @click="resetDraft(param)"
                >重置</el-button>
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
</style>
