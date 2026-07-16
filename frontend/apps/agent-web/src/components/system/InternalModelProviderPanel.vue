<script setup lang="ts">
import { computed, inject, reactive, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { CirclePlus, RefreshCw, Trash2 } from "lucide-vue-next";
import { ElMessage } from "element-plus";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, InternalModelProviderConfig } from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();
const tokenDraft = ref("");
const rows = ref<InternalModelProviderConfig[]>([]);

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);

const query = useQuery({
  queryKey: ["internal-model-providers"],
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelProviders()
});

const refreshStatusQuery = useQuery({
  queryKey: ["internal-model-provider-refresh-status"],
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelProviderRefreshStatus()
});

watch(
  () => query.data.value?.providers,
  (providers) => {
    rows.value = (providers ?? []).map((provider) => ({ ...provider }));
  },
  { immediate: true }
);

const tokenConfigured = computed(() => query.data.value?.tokenConfigured === true);
const refreshStatus = computed(() => refreshStatusQuery.data.value);
const errorMessage = computed(() => formatError(query.error.value || refreshStatusQuery.error.value));

const saveMutation = useMutation({
  mutationFn: () =>
    api.updateInternalModelProviders({
      providers: rows.value.map((row) => ({
        providerId: row.providerId.trim(),
        name: row.name.trim(),
        baseUrl: row.baseUrl.trim(),
        enabled: row.enabled,
        sortOrder: Number(row.sortOrder || 0)
      })),
      authToken: tokenDraft.value.trim() || undefined
    }),
  onSuccess: async () => {
    tokenDraft.value = "";
    await queryClient.invalidateQueries({ queryKey: ["internal-model-providers"] });
    await queryClient.invalidateQueries({ queryKey: ["internal-model-provider-refresh-status"] });
    ElMessage.success("内部模型供应商配置已保存");
  },
  onError: (error) => ElMessage.error(formatError(error) || "保存失败")
});

const refreshMutation = useMutation({
  mutationFn: () => api.refreshInternalModelProviders(),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ["internal-model-provider-refresh-status"] });
    ElMessage.success("已触发内部模型供应商刷新");
  },
  onError: (error) => ElMessage.error(formatError(error) || "刷新失败")
});

const isFetching = computed(() => query.isFetching.value);
const isSaving = computed(() => saveMutation.isPending.value);
const isRefreshingMemory = computed(() => refreshMutation.isPending.value);

function addRow() {
  rows.value.push(
    reactive({
      providerId: "",
      name: "",
      baseUrl: "",
      enabled: true,
      sortOrder: rows.value.length + 1
    }) as InternalModelProviderConfig
  );
}

function removeRow(index: number) {
  rows.value.splice(index, 1);
}

function refresh() {
  void query.refetch();
  void refreshStatusQuery.refetch();
}

function save() {
  const invalid = rows.value.find((row) => !row.providerId.trim() || !row.name.trim() || !row.baseUrl.trim());
  if (invalid) {
    ElMessage.warning("providerId、名称和 baseUrl 不能为空");
    return;
  }
  void saveMutation.mutate();
}

function triggerRefresh() {
  void refreshMutation.mutate();
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
  return error instanceof Error ? error.message : "内部模型供应商数据加载失败";
}
</script>

<template>
  <section class="internal-provider-panel">
    <div v-if="!hasSuperAdmin" class="internal-provider-placeholder">当前账号无内部模型供应商管理权限</div>
    <template v-else>
      <div class="internal-provider-toolbar">
        <el-button size="small" :icon="RefreshCw" :loading="isFetching" @click="refresh">刷新</el-button>
        <el-button size="small" type="primary" :loading="isSaving" @click="save">保存</el-button>
        <el-button size="small" :icon="CirclePlus" @click="addRow">新增供应商</el-button>
        <el-button size="small" :loading="isRefreshingMemory" @click="triggerRefresh">刷新 Java 内存</el-button>
        <el-tag size="small" :type="tokenConfigured ? 'success' : 'warning'">
          {{ tokenConfigured ? "Token 已配置" : "Token 未配置" }}
        </el-tag>
      </div>

      <div class="internal-provider-token-row">
        <el-input
          v-model="tokenDraft"
          size="small"
          type="password"
          show-password
          placeholder="写入新的 ENTERPRISE_OPENAI_AUTH_TOKEN；留空则不修改"
        />
      </div>

      <div v-if="errorMessage" class="internal-provider-alert">{{ errorMessage }}</div>

      <div class="internal-provider-table-shell">
        <table class="internal-provider-table">
          <thead>
            <tr>
              <th>Provider ID</th>
              <th>名称</th>
              <th>Base URL</th>
              <th>启用</th>
              <th>排序</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in rows" :key="`${row.providerId}-${index}`">
              <td><el-input v-model="row.providerId" size="small" placeholder="enterprise-qwen" /></td>
              <td><el-input v-model="row.name" size="small" placeholder="ENTERPRISE Qwen" /></td>
              <td><el-input v-model="row.baseUrl" size="small" placeholder="http://provider.example/v1" /></td>
              <td><el-switch v-model="row.enabled" size="small" /></td>
              <td><el-input-number v-model="row.sortOrder" size="small" :min="0" :max="9999" controls-position="right" /></td>
              <td>
                <el-button size="small" text type="danger" :icon="Trash2" @click="removeRow(index)" />
              </td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="6" class="internal-provider-empty">暂无内部供应商配置</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="internal-provider-snapshot">
        <div class="internal-provider-snapshot-title">当前 Java 内存快照</div>
        <div class="internal-provider-snapshot-meta">
          <span>加载时间：{{ formatDate(refreshStatus?.loadedAt) }}</span>
          <span>Token：{{ refreshStatus?.tokenConfigured ? "已配置" : "未配置" }}</span>
        </div>
        <div class="internal-provider-chips">
          <el-tag
            v-for="provider in refreshStatus?.providers ?? []"
            :key="provider.providerId"
            size="small"
            effect="plain"
          >
            {{ provider.providerId }} · {{ provider.name }}
          </el-tag>
          <span v-if="(refreshStatus?.providers ?? []).length === 0" class="internal-provider-empty-inline">内存中暂无启用供应商</span>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.internal-provider-panel {
  height: 100%;
  min-height: 0;
  padding: 14px;
  box-sizing: border-box;
  background: #f7f8fa;
  color: #1f2937;
}
.internal-provider-placeholder,
.internal-provider-alert {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
}
.internal-provider-alert {
  margin-top: 10px;
  border-color: #fecaca;
  color: #b91c1c;
  background: #fff7f7;
}
.internal-provider-toolbar,
.internal-provider-token-row,
.internal-provider-snapshot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.internal-provider-token-row {
  max-width: 560px;
}
.internal-provider-table-shell {
  overflow: auto;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}
.internal-provider-table {
  width: 100%;
  min-width: 860px;
  border-collapse: collapse;
  font-size: 12px;
}
.internal-provider-table th,
.internal-provider-table td {
  padding: 8px;
  border-bottom: 1px solid #edf0f3;
  text-align: left;
  vertical-align: middle;
}
.internal-provider-table th {
  background: #f9fafb;
  color: #4b5563;
  font-weight: 600;
}
.internal-provider-empty,
.internal-provider-empty-inline {
  color: #9ca3af;
  font-size: 12px;
}
.internal-provider-snapshot {
  margin-top: 12px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  align-items: flex-start;
  flex-direction: column;
}
.internal-provider-snapshot-title {
  font-size: 13px;
  font-weight: 600;
}
.internal-provider-snapshot-meta,
.internal-provider-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
}
</style>
