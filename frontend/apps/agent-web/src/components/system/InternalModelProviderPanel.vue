<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { CirclePlus, KeyRound, Pencil, RefreshCw, Trash2 } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  InternalModelProviderConfig,
  InternalModelTokenDefinition
} from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

type ProviderRow = InternalModelProviderConfig & {
  originalProviderId: string | null;
  originalTokenId: number | null;
};

type TokenSaveCommand = {
  tokenId: number | null;
  name: string;
  token?: string;
};

const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();
const rows = ref<ProviderRow[]>([]);
const tokenEditorOpen = ref(false);
const editingTokenId = ref<number | null>(null);
const tokenNameDraft = ref("");
const tokenValueDraft = ref("");

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);

const query = useQuery({
  queryKey: ["internal-model-providers"],
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.getInternalModelProviders()
});

const tokenQuery = useQuery({
  queryKey: ["internal-model-tokens"],
  enabled: () => hasSuperAdmin.value,
  retry: false,
  queryFn: () => api.listInternalModelTokens()
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
    rows.value = (providers ?? []).map((provider) => ({
      ...provider,
      tokenId: normalizeTokenId(provider.tokenId),
      originalProviderId: provider.providerId,
      originalTokenId: normalizeTokenId(provider.tokenId)
    }));
  },
  { immediate: true }
);

const tokens = computed(() => tokenQuery.data.value ?? []);
const tokenConfigured = computed(() => query.data.value?.tokenConfigured === true);
const refreshStatus = computed(() => refreshStatusQuery.data.value);
const errorMessage = computed(() => formatError(
  query.error.value || tokenQuery.error.value || refreshStatusQuery.error.value
));

const saveMutation = useMutation({
  mutationFn: () => api.updateInternalModelProviders({
    providers: rows.value.map((row) => {
      const tokenId = normalizeTokenId(row.tokenId);
      return {
        providerId: row.providerId.trim(),
        name: row.name.trim(),
        baseUrl: row.baseUrl.trim(),
        enabled: row.enabled,
        sortOrder: Number(row.sortOrder || 0),
        ...(tokenId == null ? {} : { tokenId }),
        // 只有明确清空既有关系时才发送 clearToken，避免旧客户端语义被误触发。
        ...(tokenId == null && row.originalTokenId != null ? { clearToken: true } : {})
      };
    })
  }),
  onSuccess: async () => {
    await invalidateConfigurationQueries();
    ElMessage.success("内部模型供应商配置已保存");
  },
  onError: (error) => ElMessage.error(formatError(error) || "保存供应商失败")
});

const tokenSaveMutation = useMutation({
  mutationFn: async (command: TokenSaveCommand) => {
    try {
      return command.tokenId == null
        ? await api.createInternalModelToken({ name: command.name, token: command.token ?? "" })
        : await api.updateInternalModelToken(command.tokenId, { name: command.name, token: command.token });
    } finally {
      // API Promise 一结束即同时擦除输入草稿与 mutation 变量，避免后续刷新期间仍保留密钥。
      command.token = undefined;
      tokenValueDraft.value = "";
    }
  },
  onSuccess: async (_result, command) => {
    closeTokenEditor();
    await invalidateConfigurationQueries();
    ElMessage.success(command.tokenId == null ? "Token 已新增" : "Token 已更新");
  },
  onError: (error) => ElMessage.error(formatError(error) || "保存 Token 失败")
});

const tokenDeleteMutation = useMutation({
  mutationFn: (tokenId: number) => api.deleteInternalModelToken(tokenId),
  onSuccess: async () => {
    await invalidateConfigurationQueries();
    ElMessage.success("Token 已删除");
  },
  onError: (error) => ElMessage.error(formatError(error) || "删除 Token 失败")
});

const refreshMutation = useMutation({
  mutationFn: () => api.refreshInternalModelProviders(),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ["internal-model-provider-refresh-status"] });
    ElMessage.success("已触发内部模型供应商刷新");
  },
  onError: (error) => ElMessage.error(formatError(error) || "刷新失败")
});

const isFetching = computed(() => query.isFetching.value || tokenQuery.isFetching.value);
const isSaving = computed(() => saveMutation.isPending.value);
const isSavingToken = computed(() => tokenSaveMutation.isPending.value);
const isRefreshingMemory = computed(() => refreshMutation.isPending.value);

function addRow() {
  rows.value.push({
    providerId: "",
    name: "",
    baseUrl: "",
    enabled: true,
    sortOrder: rows.value.length + 1,
    tokenId: null,
    tokenName: null,
    tokenConfigured: false,
    originalProviderId: null,
    originalTokenId: null
  });
}

function removeRow(index: number) {
  rows.value.splice(index, 1);
}

function refresh() {
  void query.refetch();
  void tokenQuery.refetch();
  void refreshStatusQuery.refetch();
}

function save() {
  const invalid = rows.value.find((row) => !row.providerId.trim() || !row.name.trim() || !row.baseUrl.trim());
  if (invalid) {
    ElMessage.warning("providerId、名称和 baseUrl 不能为空");
    return;
  }
  if (rows.value.some((row) => row.enabled && normalizeTokenId(row.tokenId) == null)) {
    ElMessage.warning("启用的供应商必须选择 Token");
    return;
  }
  void saveMutation.mutate();
}

function openCreateToken() {
  editingTokenId.value = null;
  tokenNameDraft.value = "";
  tokenValueDraft.value = "";
  tokenEditorOpen.value = true;
}

function openEditToken(token: InternalModelTokenDefinition) {
  editingTokenId.value = token.tokenId;
  tokenNameDraft.value = token.name;
  tokenValueDraft.value = "";
  tokenEditorOpen.value = true;
}

function closeTokenEditor() {
  tokenEditorOpen.value = false;
  editingTokenId.value = null;
  tokenNameDraft.value = "";
  tokenValueDraft.value = "";
}

function saveToken() {
  const name = tokenNameDraft.value.trim();
  const token = tokenValueDraft.value;
  if (!name) {
    ElMessage.warning("Token 名称不能为空");
    return;
  }
  if (editingTokenId.value == null && !token.trim()) {
    ElMessage.warning("新增 Token 时必须粘贴外部 Token");
    return;
  }
  tokenSaveMutation.mutate({
    tokenId: editingTokenId.value,
    name,
    token: token || undefined
  });
}

async function deleteToken(token: InternalModelTokenDefinition) {
  try {
    await ElMessageBox.confirm(
      `确认删除 Token「${token.name}」？仍被供应商引用时后端会拒绝删除。`,
      "删除 Token",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
    tokenDeleteMutation.mutate(token.tokenId);
  } catch {
    // 用户取消确认时不产生请求，也不提示错误。
  }
}

function triggerRefresh() {
  void refreshMutation.mutate();
}

async function invalidateConfigurationQueries() {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["internal-model-providers"] }),
    queryClient.invalidateQueries({ queryKey: ["internal-model-tokens"] }),
    queryClient.invalidateQueries({ queryKey: ["internal-model-provider-refresh-status"] })
  ]);
}

function normalizeTokenId(value: unknown): number | null {
  if (value == null || value === "") {
    return null;
  }
  const tokenId = Number(value);
  return Number.isInteger(tokenId) && tokenId > 0 ? tokenId : null;
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
      <div class="internal-provider-commandbar">
        <div>
          <h2>内部模型路由</h2>
          <p>记录外部 Token，并按 Provider ID 绑定到对应上游。</p>
        </div>
        <div class="internal-provider-commandbar-actions">
          <el-tag size="small" :type="tokenConfigured ? 'success' : 'warning'">
            {{ tokenConfigured ? "启用供应商均可用" : "存在未配置 Token 的供应商" }}
          </el-tag>
          <el-button size="small" :icon="RefreshCw" :loading="isFetching" @click="refresh">刷新页面</el-button>
          <el-button size="small" :loading="isRefreshingMemory" @click="triggerRefresh">刷新 Java 内存</el-button>
        </div>
      </div>

      <div v-if="errorMessage" class="internal-provider-alert">{{ errorMessage }}</div>

      <section class="internal-provider-section token-definition-section">
        <header class="internal-provider-section-header">
          <div>
            <div class="internal-provider-eyebrow"><KeyRound :size="14" /> 凭据定义</div>
            <h3>Token</h3>
            <p>Token 值来自外部系统。平台只记录，不生成，也不会再次回显。</p>
          </div>
          <el-button
            size="small"
            type="primary"
            :icon="CirclePlus"
            aria-label="新增 Token"
            @click="openCreateToken"
          >新增 Token</el-button>
        </header>

        <div v-if="tokens.length" class="token-definition-grid">
          <article v-for="token in tokens" :key="token.tokenId" class="token-definition-card">
            <div class="token-definition-mark"><KeyRound :size="16" /></div>
            <div class="token-definition-main">
              <strong>{{ token.name }}</strong>
              <div class="token-definition-meta">
                <code>#{{ token.tokenId }}</code>
                <span>{{ token.referencedProviderCount }} 个供应商引用</span>
                <span>更新于 {{ formatDate(token.updatedAt) }}</span>
              </div>
            </div>
            <div class="token-definition-actions">
              <el-button
                size="small"
                text
                :icon="Pencil"
                :aria-label="`编辑 ${token.name}`"
                @click="openEditToken(token)"
              />
              <el-button
                size="small"
                text
                type="danger"
                :icon="Trash2"
                :aria-label="`删除 ${token.name}`"
                @click="deleteToken(token)"
              />
            </div>
          </article>
        </div>
        <div v-else class="internal-provider-empty-state">
          尚未记录 Token。先新增一个外部 Token，再为供应商建立关联。
        </div>

        <div v-if="tokenEditorOpen" class="token-editor">
          <div class="token-editor-heading">
            <strong>{{ editingTokenId == null ? "新增 Token" : "编辑 Token" }}</strong>
            <span>{{ editingTokenId == null ? "密钥只在本次请求期间保留" : "留空密钥即可只修改名称" }}</span>
          </div>
          <el-input
            v-model="tokenNameDraft"
            size="small"
            aria-label="Token 名称"
            placeholder="Token 名称"
          />
          <el-input
            v-model="tokenValueDraft"
            size="small"
            type="password"
            show-password
            :aria-label="editingTokenId == null ? '粘贴外部 Token' : '留空则不修改'"
            :placeholder="editingTokenId == null ? '粘贴外部 Token' : '留空则不修改'"
          />
          <div class="token-editor-actions">
            <el-button size="small" @click="closeTokenEditor">取消</el-button>
            <el-button size="small" type="primary" :loading="isSavingToken" @click="saveToken">保存 Token</el-button>
          </div>
        </div>
      </section>

      <section class="internal-provider-section provider-routing-section">
        <header class="internal-provider-section-header">
          <div>
            <div class="internal-provider-eyebrow">Provider → Token</div>
            <h3>供应商关联</h3>
            <p>启用的供应商必须选择 Token；停用后可解除关联。</p>
          </div>
          <div class="internal-provider-section-actions">
            <el-button size="small" :icon="CirclePlus" @click="addRow">新增供应商</el-button>
            <el-button size="small" type="primary" :loading="isSaving" @click="save">保存供应商</el-button>
          </div>
        </header>

        <div class="internal-provider-table-shell">
          <table class="internal-provider-table">
            <thead>
              <tr>
                <th>Provider ID</th>
                <th>名称</th>
                <th>Base URL</th>
                <th>Token</th>
                <th>启用</th>
                <th>排序</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in rows" :key="`${row.originalProviderId ?? 'new'}-${index}`">
                <td>
                  <el-input
                    v-model="row.providerId"
                    size="small"
                    :aria-label="`Provider ID ${index + 1}`"
                    placeholder="enterprise-qwen"
                  />
                </td>
                <td><el-input v-model="row.name" size="small" placeholder="ENTERPRISE Qwen" /></td>
                <td><el-input v-model="row.baseUrl" size="small" placeholder="http://provider.example/v1" /></td>
                <td class="provider-token-cell">
                  <el-select
                    :model-value="row.tokenId ?? ''"
                    size="small"
                    clearable
                    placeholder="选择 Token"
                    :aria-label="`${row.providerId || `第 ${index + 1} 行`} 的 Token`"
                    @update:model-value="row.tokenId = normalizeTokenId($event)"
                  >
                    <el-option
                      v-for="token in tokens"
                      :key="token.tokenId"
                      :label="`${token.name}（${token.referencedProviderCount} 个引用）`"
                      :value="token.tokenId"
                    />
                  </el-select>
                </td>
                <td>
                  <el-switch
                    v-model="row.enabled"
                    size="small"
                    :aria-label="`启用 ${row.providerId || `第 ${index + 1} 行`}`"
                  />
                </td>
                <td>
                  <el-input-number
                    v-model="row.sortOrder"
                    size="small"
                    :min="0"
                    :max="9999"
                    controls-position="right"
                  />
                </td>
                <td>
                  <el-tag size="small" :type="row.tokenId ? 'success' : (row.enabled ? 'danger' : 'info')" effect="plain">
                    {{ row.tokenId ? "已关联" : (row.enabled ? "缺少 Token" : "未关联") }}
                  </el-tag>
                </td>
                <td>
                  <el-button
                    size="small"
                    text
                    type="danger"
                    :icon="Trash2"
                    :aria-label="`删除供应商 ${row.providerId || index + 1}`"
                    @click="removeRow(index)"
                  />
                </td>
              </tr>
              <tr v-if="rows.length === 0">
                <td colspan="8" class="internal-provider-empty">暂无内部供应商配置</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="internal-provider-snapshot">
        <div class="internal-provider-snapshot-title">当前 Java 内存快照</div>
        <div class="internal-provider-snapshot-meta">
          <span>加载时间：{{ formatDate(refreshStatus?.loadedAt) }}</span>
          <span>启用供应商 Token：{{ refreshStatus?.tokenConfigured ? "全部可用" : "存在缺失" }}</span>
        </div>
        <div class="internal-provider-chips">
          <el-tag
            v-for="provider in refreshStatus?.providers ?? []"
            :key="provider.providerId"
            size="small"
            :type="provider.tokenConfigured ? 'success' : 'danger'"
            effect="plain"
          >
            {{ provider.providerId }} → {{ provider.tokenName || "Token 未配置" }}
          </el-tag>
          <span v-if="(refreshStatus?.providers ?? []).length === 0" class="internal-provider-empty-inline">
            内存中暂无启用供应商
          </span>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.internal-provider-panel {
  --panel-ink: #202938;
  --panel-muted: #667085;
  --panel-line: #dfe5ec;
  --panel-canvas: #f5f7fa;
  --panel-surface: #ffffff;
  --panel-accent: #245b93;
  --panel-accent-soft: #edf5fc;
  height: 100%;
  min-height: 0;
  padding: 16px;
  box-sizing: border-box;
  overflow: auto;
  background: var(--panel-canvas);
  color: var(--panel-ink);
}

.internal-provider-placeholder,
.internal-provider-alert {
  padding: 12px;
  border: 1px solid var(--panel-line);
  border-radius: 7px;
  background: var(--panel-surface);
  color: var(--panel-muted);
  font-size: 13px;
}

.internal-provider-alert {
  margin-bottom: 12px;
  border-color: #fecaca;
  color: #b42318;
  background: #fff7f7;
}

.internal-provider-commandbar,
.internal-provider-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.internal-provider-commandbar {
  margin-bottom: 12px;
}

.internal-provider-commandbar h2,
.internal-provider-section-header h3 {
  margin: 0;
  letter-spacing: -0.015em;
}

.internal-provider-commandbar h2 {
  font-size: 18px;
}

.internal-provider-commandbar p,
.internal-provider-section-header p {
  margin: 4px 0 0;
  color: var(--panel-muted);
  font-size: 12px;
}

.internal-provider-commandbar-actions,
.internal-provider-section-actions,
.token-editor-actions,
.token-definition-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.internal-provider-section {
  margin-bottom: 12px;
  border: 1px solid var(--panel-line);
  border-radius: 8px;
  background: var(--panel-surface);
  overflow: hidden;
}

.internal-provider-section-header {
  padding: 12px 14px;
  border-bottom: 1px solid var(--panel-line);
}

.internal-provider-section-header h3 {
  margin-top: 2px;
  font-size: 15px;
}

.internal-provider-eyebrow {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--panel-accent);
  font: 600 10px/1.2 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.token-definition-section {
  border-left: 3px solid var(--panel-accent);
}

.token-definition-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 8px;
  padding: 12px;
}

.token-definition-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--panel-line);
  border-radius: 7px;
  background: #fbfcfe;
  transition: border-color 120ms ease, background-color 120ms ease;
}

.token-definition-card:hover {
  border-color: #a8c3df;
  background: var(--panel-accent-soft);
}

.token-definition-mark {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 6px;
  background: var(--panel-accent-soft);
  color: var(--panel-accent);
}

.token-definition-main {
  min-width: 0;
}

.token-definition-main strong {
  display: block;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-definition-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 10px;
  margin-top: 4px;
  color: var(--panel-muted);
  font-size: 11px;
}

.token-definition-meta code,
.internal-provider-table :deep(input:first-child) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.token-editor {
  display: grid;
  grid-template-columns: minmax(160px, 0.65fr) minmax(260px, 1fr) minmax(280px, 1.6fr) auto;
  align-items: end;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid var(--panel-line);
  background: #f8fafc;
}

.token-editor-heading {
  align-self: center;
}

.token-editor-heading strong,
.token-editor-heading span {
  display: block;
}

.token-editor-heading strong {
  font-size: 12px;
}

.token-editor-heading span {
  margin-top: 2px;
  color: var(--panel-muted);
  font-size: 10px;
}

.internal-provider-empty-state {
  padding: 18px 14px;
  color: var(--panel-muted);
  font-size: 12px;
}

.internal-provider-table-shell {
  overflow: auto;
}

.internal-provider-table {
  width: 100%;
  min-width: 1120px;
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
  background: #f8fafc;
  color: #475467;
  font-weight: 600;
}

.internal-provider-table tbody tr:last-child td {
  border-bottom: 0;
}

.provider-token-cell {
  min-width: 220px;
}

.provider-token-cell :deep(.el-select) {
  width: 100%;
}

.internal-provider-empty,
.internal-provider-empty-inline {
  color: #98a2b3;
  font-size: 12px;
}

.internal-provider-snapshot {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 7px;
  padding: 11px 12px;
  border: 1px solid var(--panel-line);
  border-radius: 8px;
  background: var(--panel-surface);
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
  color: var(--panel-muted);
}

@media (max-width: 900px) {
  .internal-provider-commandbar,
  .internal-provider-section-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .internal-provider-commandbar-actions {
    flex-wrap: wrap;
  }

  .token-editor {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .token-editor-actions {
    justify-content: flex-end;
  }
}

@media (prefers-reduced-motion: reduce) {
  .token-definition-card {
    transition: none;
  }
}
</style>
