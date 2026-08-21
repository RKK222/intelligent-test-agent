<script setup lang="ts">
import { computed, inject, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { Copy, Eye, KeyRound, Pencil, Plus, RefreshCw, RotateCw, Search, Trash2 } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  ExternalApiCredential,
  ExternalApiCredentialCreatePayload,
  ExternalApiCredentialUpdatePayload,
  ExternalApiScope
} from "@test-agent/shared-types";

const props = defineProps<{ currentUser: CurrentUser | null; pageActive: boolean }>();
const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();
const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);

const keywordDraft = ref("");
const keyword = ref("");
const enabledFilter = ref<"" | "true" | "false">("");
const page = ref(1);
const size = 20;
const editorOpen = ref(false);
const editingCredentialId = ref<string | null>(null);
const secretOpen = ref(false);
const plainApiKey = ref("");
const secretToolCode = ref("");
const secretTitle = ref("");
let componentDisposed = false;
let sensitiveResponseEpoch = 0;
const form = reactive<{
  toolCode: string;
  toolName: string;
  scopes: ExternalApiScope[];
  enabled: boolean;
}>({ toolCode: "", toolName: "", scopes: [], enabled: true });

const scopeQuery = useQuery({
  queryKey: ["external-api-scopes"],
  enabled: () => hasSuperAdmin.value && props.pageActive,
  retry: false,
  queryFn: () => api.listExternalApiScopes()
});

const credentialQuery = useQuery({
  queryKey: computed(() => ["external-api-credentials", keyword.value, enabledFilter.value, page.value, size]),
  enabled: () => hasSuperAdmin.value && props.pageActive,
  retry: false,
  queryFn: () => api.listExternalApiCredentials({
    keyword: keyword.value || undefined,
    enabled: enabledFilter.value === "" ? undefined : enabledFilter.value === "true",
    page: page.value,
    size
  })
});

const rows = computed(() => credentialQuery.data.value?.items ?? []);
const total = computed(() => credentialQuery.data.value?.total ?? 0);
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)));
const scopeOptions = computed(() => scopeQuery.data.value ?? []);
const loadError = computed(() => formatError(credentialQuery.error.value || scopeQuery.error.value));

const createMutation = useMutation({
  mutationFn: async (payload: ExternalApiCredentialCreatePayload) => {
    const requestEpoch = sensitiveResponseEpoch;
    const result = await api.createExternalApiCredential(payload);
    // 异步响应晚于失活或卸载时不得读取、写回一次性明文 Key。
    if (!componentDisposed && props.pageActive && requestEpoch === sensitiveResponseEpoch) {
      showSecret("新建凭据", result.credential.toolCode, result.apiKey);
    }
  },
  onSuccess: () => {
    closeEditor();
    void invalidateList();
  },
  onError: (error) => ElMessage.error(formatError(error) || "新增 API Key 失败"),
  onSettled: () => queueMicrotask(() => createMutation.reset())
});

const updateMutation = useMutation({
  mutationFn: (command: { credentialId: string; payload: ExternalApiCredentialUpdatePayload }) =>
    api.updateExternalApiCredential(command.credentialId, command.payload),
  onSuccess: async () => {
    closeEditor();
    await invalidateList();
    ElMessage.success("API Key 配置已更新");
  },
  onError: (error) => ElMessage.error(formatError(error) || "更新 API Key 失败")
});

const revealMutation = useMutation({
  mutationFn: async (credentialId: string) => {
    const requestEpoch = sensitiveResponseEpoch;
    const result = await api.revealExternalApiCredential(credentialId);
    if (!componentDisposed && props.pageActive && requestEpoch === sensitiveResponseEpoch) {
      showSecret("查看当前凭据", result.toolCode, result.apiKey);
    }
  },
  onError: (error) => ElMessage.error(formatError(error) || "查看 API Key 失败"),
  onSettled: () => queueMicrotask(() => revealMutation.reset())
});

const rotateMutation = useMutation({
  mutationFn: async (credentialId: string) => {
    const requestEpoch = sensitiveResponseEpoch;
    const result = await api.rotateExternalApiCredential(credentialId);
    if (!componentDisposed && props.pageActive && requestEpoch === sensitiveResponseEpoch) {
      showSecret("轮换后的新凭据", result.toolCode, result.apiKey);
    }
  },
  onSuccess: () => {
    void invalidateList();
  },
  onError: (error) => ElMessage.error(formatError(error) || "轮换 API Key 失败"),
  onSettled: () => queueMicrotask(() => rotateMutation.reset())
});

const deleteMutation = useMutation({
  mutationFn: (credentialId: string) => api.deleteExternalApiCredential(credentialId),
  onSuccess: async () => {
    await invalidateList();
    ElMessage.success("API Key 已删除");
  },
  onError: (error) => ElMessage.error(formatError(error) || "删除 API Key 失败")
});

function searchCredentials() {
  keyword.value = keywordDraft.value.trim();
  page.value = 1;
}

function openCreate() {
  editingCredentialId.value = null;
  form.toolCode = "";
  form.toolName = "";
  form.scopes = [];
  form.enabled = true;
  editorOpen.value = true;
}

function openEdit(credential: ExternalApiCredential) {
  editingCredentialId.value = credential.credentialId;
  form.toolCode = credential.toolCode;
  form.toolName = credential.toolName;
  form.scopes = [...credential.scopes];
  form.enabled = credential.enabled;
  editorOpen.value = true;
}

function closeEditor() {
  editorOpen.value = false;
  editingCredentialId.value = null;
  form.toolCode = "";
  form.toolName = "";
  form.scopes = [];
  form.enabled = true;
}

function toggleScope(scope: ExternalApiScope, checked: boolean) {
  form.scopes = checked
    ? Array.from(new Set([...form.scopes, scope]))
    : form.scopes.filter((item) => item !== scope);
}

function saveCredential() {
  const toolCode = form.toolCode.trim();
  const toolName = form.toolName.trim();
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(toolCode)) {
    ElMessage.warning("工具编码格式不正确");
    return;
  }
  if (!toolName || toolName.length > 128) {
    ElMessage.warning("工具名称必须为 1–128 个字符");
    return;
  }
  if (form.scopes.length === 0) {
    ElMessage.warning("至少选择一个 scope");
    return;
  }
  if (editingCredentialId.value) {
    updateMutation.mutate({
      credentialId: editingCredentialId.value,
      payload: { toolName, scopes: [...form.scopes], enabled: form.enabled }
    });
    return;
  }
  createMutation.mutate({ toolCode, toolName, scopes: [...form.scopes], enabled: form.enabled });
}

function updateEnabled(credential: ExternalApiCredential, enabled: boolean) {
  updateMutation.mutate({
    credentialId: credential.credentialId,
    payload: { toolName: credential.toolName, scopes: [...credential.scopes], enabled }
  });
}

function reveal(credential: ExternalApiCredential) {
  revealMutation.mutate(credential.credentialId);
}

async function rotate(credential: ExternalApiCredential) {
  try {
    await ElMessageBox.confirm(
      `轮换后旧 Key 将立即失效，确认轮换「${credential.toolName}」？`,
      "轮换 API Key",
      { type: "warning", confirmButtonText: "立即轮换", cancelButtonText: "取消" }
    );
    rotateMutation.mutate(credential.credentialId);
  } catch {
    // 取消确认不发起请求。
  }
}

async function remove(credential: ExternalApiCredential) {
  try {
    await ElMessageBox.confirm(
      `确认永久删除「${credential.toolName}」及其 scope？`,
      "删除 API Key",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
    deleteMutation.mutate(credential.credentialId);
  } catch {
    // 取消确认不发起请求。
  }
}

function showSecret(title: string, toolCode: string, apiKey: string) {
  clearSecret();
  secretTitle.value = title;
  secretToolCode.value = toolCode;
  plainApiKey.value = apiKey;
  secretOpen.value = true;
}

function clearSecret() {
  secretOpen.value = false;
  plainApiKey.value = "";
  secretToolCode.value = "";
  secretTitle.value = "";
}

async function copySecret() {
  if (!plainApiKey.value) return;
  try {
    await navigator.clipboard.writeText(plainApiKey.value);
    ElMessage.success("API Key 已复制");
  } catch {
    ElMessage.error("复制失败，请手工复制");
  }
}

async function invalidateList() {
  await queryClient.invalidateQueries({ queryKey: ["external-api-credentials"] });
}

function refresh() {
  void Promise.all([credentialQuery.refetch(), scopeQuery.refetch()]);
}

function changePage(next: number) {
  if (next >= 1 && next <= totalPages.value) page.value = next;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false
  }).format(new Date(value));
}

function formatError(error: unknown) {
  if (!error) return "";
  if (error instanceof BackendApiError) return `${error.message}（${error.code}）`;
  return error instanceof Error ? error.message : "API Key 数据加载失败";
}

watch(() => props.pageActive, (active) => {
  if (active) return;
  // 代次先失效，保证同一事件循环中返回的迟到请求也无法重新展示明文。
  sensitiveResponseEpoch += 1;
  clearSecret();
});

onBeforeUnmount(() => {
  componentDisposed = true;
  sensitiveResponseEpoch += 1;
  clearSecret();
  closeEditor();
  createMutation.reset();
  revealMutation.reset();
  rotateMutation.reset();
});
</script>

<template>
  <section class="api-key-panel">
    <div v-if="!hasSuperAdmin" class="api-key-placeholder">当前账号无 API Key 管理权限</div>
    <template v-else>
      <header class="api-key-commandbar">
        <div>
          <div class="api-key-eyebrow"><KeyRound :size="14" /> 外部系统凭据</div>
          <h2>API Key 管理</h2>
          <p>按工具分配最小 scope；查看与轮换产生的明文只在当前弹窗中短暂保留。</p>
        </div>
        <div class="api-key-command-actions">
          <button type="button" class="secondary-button" @click="refresh"><RefreshCw :size="15" />刷新</button>
          <button type="button" class="primary-button" aria-label="新增 API Key" @click="openCreate">
            <Plus :size="15" />新增 API Key
          </button>
        </div>
      </header>

      <div class="api-key-filterbar">
        <label class="search-field">
          <Search :size="15" />
          <input v-model="keywordDraft" aria-label="搜索工具" placeholder="搜索工具编码或名称" @keyup.enter="searchCredentials" />
        </label>
        <select v-model="enabledFilter" aria-label="启用状态" @change="searchCredentials">
          <option value="">全部状态</option>
          <option value="true">已启用</option>
          <option value="false">已停用</option>
        </select>
        <button type="button" class="secondary-button" @click="searchCredentials">查询</button>
        <span class="result-count">共 {{ total }} 个工具凭据</span>
      </div>

      <div v-if="loadError" class="api-key-alert">{{ loadError }}</div>
      <div class="api-key-table-wrap">
        <table class="api-key-table">
          <thead>
            <tr><th>工具</th><th>Scope</th><th>API Key</th><th>状态</th><th>更新时间</th><th class="actions-column">操作</th></tr>
          </thead>
          <tbody>
            <tr v-if="credentialQuery.isFetching && rows.length === 0"><td colspan="6" class="empty-row">正在加载凭据…</td></tr>
            <tr v-else-if="rows.length === 0"><td colspan="6" class="empty-row">暂无 API Key，点击右上角新增。</td></tr>
            <tr v-for="credential in rows" :key="credential.credentialId">
              <td>
                <strong>{{ credential.toolName }}</strong>
                <code>{{ credential.toolCode }}</code>
              </td>
              <td><span v-for="scope in credential.scopes" :key="scope" class="scope-chip">{{ scope }}</span></td>
              <td><code class="key-hint">{{ credential.keyHint }}</code></td>
              <td>
                <label class="status-switch">
                  <input
                    type="checkbox"
                    :aria-label="`启用 ${credential.toolCode}`"
                    :checked="credential.enabled"
                    @change="updateEnabled(credential, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ credential.enabled ? "已启用" : "已停用" }}</span>
                </label>
              </td>
              <td>{{ formatDate(credential.updatedAt) }}</td>
              <td class="row-actions">
                <button type="button" :aria-label="`查看 ${credential.toolCode}`" title="查看" @click="reveal(credential)"><Eye :size="15" /></button>
                <button type="button" :aria-label="`编辑 ${credential.toolCode}`" title="编辑" @click="openEdit(credential)"><Pencil :size="15" /></button>
                <button type="button" :aria-label="`轮换 ${credential.toolCode}`" title="轮换" @click="rotate(credential)"><RotateCw :size="15" /></button>
                <button type="button" class="danger" :aria-label="`删除 ${credential.toolCode}`" title="删除" @click="remove(credential)"><Trash2 :size="15" /></button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="api-key-pagination">
        <button type="button" class="secondary-button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button type="button" class="secondary-button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </footer>

      <div v-if="editorOpen" class="api-key-modal-backdrop" @click.self="closeEditor">
        <section class="api-key-modal" role="dialog" aria-modal="true" aria-label="API Key 编辑器">
          <header><div><span>{{ editingCredentialId ? "编辑工具" : "登记新工具" }}</span><h3>{{ editingCredentialId ? form.toolCode : "生成 API Key" }}</h3></div></header>
          <div class="api-key-form">
            <label>工具编码<input v-model="form.toolCode" aria-label="工具编码" maxlength="64" :disabled="editingCredentialId !== null" placeholder="例如 deploy.bot" /></label>
            <label>工具名称<input v-model="form.toolName" aria-label="工具名称" maxlength="128" placeholder="便于管理员识别" /></label>
            <fieldset>
              <legend>Scope</legend>
              <label v-for="scope in scopeOptions" :key="scope.code" class="scope-option">
                <input
                  type="checkbox"
                  :aria-label="scope.name"
                  :checked="form.scopes.includes(scope.code)"
                  @change="toggleScope(scope.code, ($event.target as HTMLInputElement).checked)"
                />
                <span><strong>{{ scope.name }}</strong><code>{{ scope.code }}</code></span>
              </label>
            </fieldset>
            <label class="enabled-option">
              <input v-model="form.enabled" type="checkbox" aria-label="启用凭据" />
              {{ editingCredentialId ? "启用此凭据" : "创建后立即启用" }}
            </label>
          </div>
          <footer>
            <button type="button" class="secondary-button" @click="closeEditor">取消</button>
            <button type="button" class="primary-button" aria-label="保存 API Key" @click="saveCredential">保存 API Key</button>
          </footer>
        </section>
      </div>

      <div v-if="secretOpen" class="api-key-modal-backdrop">
        <section class="api-key-modal secret-modal" role="dialog" aria-modal="true" aria-label="API Key 明文">
          <header><div><span>{{ secretTitle }}</span><h3>{{ secretToolCode }}</h3></div></header>
          <div class="secret-warning">此值关闭后会立即从页面内存清除。请复制到调用工具的受控密钥配置中。</div>
          <label class="secret-field">API Key
            <input :value="plainApiKey" aria-label="API Key 明文" readonly spellcheck="false" />
          </label>
          <footer>
            <button type="button" class="secondary-button" aria-label="复制 API Key" @click="copySecret"><Copy :size="15" />复制</button>
            <button type="button" class="primary-button" aria-label="关闭明文 API Key" @click="clearSecret">我已保存，关闭</button>
          </footer>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.api-key-panel { min-height: 0; height: 100%; display: flex; flex-direction: column; color: #1f2937; background: #f7f8fa; }
.api-key-placeholder { margin: 16px; padding: 16px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; color: #6b7280; font-size: 13px; }
.api-key-commandbar { display: flex; justify-content: space-between; gap: 24px; padding: 22px 24px 18px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.api-key-commandbar h2 { margin: 4px 0; font-size: 21px; letter-spacing: -.02em; }
.api-key-commandbar p { margin: 0; color: #6b7280; font-size: 13px; }
.api-key-eyebrow { display: flex; align-items: center; gap: 6px; color: #2563eb; font-size: 11px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.api-key-command-actions, .row-actions, .api-key-pagination, .api-key-modal footer { display: flex; align-items: center; gap: 8px; }
button { font: inherit; }
.primary-button, .secondary-button { min-height: 32px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 0 12px; border-radius: 6px; cursor: pointer; }
.primary-button { border: 1px solid #1d4ed8; background: #2563eb; color: #fff; }
.secondary-button { border: 1px solid #d1d5db; background: #fff; color: #374151; }
button:disabled { opacity: .45; cursor: not-allowed; }
.api-key-filterbar { display: flex; align-items: center; gap: 10px; padding: 14px 24px; border-bottom: 1px solid #e5e7eb; }
.search-field { width: min(360px, 45%); display: flex; align-items: center; gap: 8px; padding: 0 10px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; }
.search-field input { flex: 1; height: 32px; border: 0; outline: 0; }
.api-key-filterbar select { height: 34px; padding: 0 28px 0 10px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; }
.result-count { margin-left: auto; color: #6b7280; font-size: 12px; }
.api-key-alert, .secret-warning { margin: 12px 24px 0; padding: 10px 12px; border: 1px solid #fecaca; border-radius: 6px; background: #fef2f2; color: #991b1b; font-size: 12px; }
.api-key-table-wrap { flex: 1; min-height: 0; margin: 16px 24px 0; overflow: auto; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.api-key-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.api-key-table th { position: sticky; top: 0; z-index: 1; padding: 10px 12px; text-align: left; background: #f9fafb; color: #4b5563; font-weight: 600; border-bottom: 1px solid #e5e7eb; }
.api-key-table td { padding: 13px 12px; border-bottom: 1px solid #f0f1f3; vertical-align: middle; }
.api-key-table td strong, .api-key-table td > code { display: block; }
.api-key-table td > code { margin-top: 4px; color: #6b7280; }
.scope-chip { display: inline-flex; margin: 2px 4px 2px 0; padding: 3px 6px; border-radius: 4px; background: #eef2ff; color: #3730a3; font-family: ui-monospace, monospace; font-size: 10px; }
.key-hint { color: #334155 !important; letter-spacing: .02em; }
.status-switch { display: inline-flex; align-items: center; gap: 6px; color: #4b5563; cursor: pointer; }
.row-actions { justify-content: flex-end; }
.row-actions button { width: 29px; height: 29px; display: grid; place-items: center; border: 1px solid #e5e7eb; border-radius: 5px; background: #fff; color: #475569; cursor: pointer; }
.row-actions button:hover { border-color: #93c5fd; color: #1d4ed8; }
.row-actions button.danger:hover { border-color: #fecaca; color: #b91c1c; }
.actions-column { text-align: right !important; }
.empty-row { height: 120px; text-align: center; color: #9ca3af; }
.api-key-pagination { justify-content: flex-end; padding: 14px 24px 18px; color: #6b7280; font-size: 12px; }
.api-key-modal-backdrop { position: fixed; inset: 0; z-index: 1500; display: grid; place-items: center; padding: 24px; background: rgba(15, 23, 42, .42); }
.api-key-modal { width: min(520px, 100%); border: 1px solid #dbe1e8; border-radius: 10px; background: #fff; box-shadow: 0 24px 70px rgba(15, 23, 42, .22); overflow: hidden; }
.api-key-modal header { padding: 19px 22px 15px; border-bottom: 1px solid #e5e7eb; }
.api-key-modal header span { color: #2563eb; font-size: 11px; font-weight: 700; letter-spacing: .06em; }
.api-key-modal h3 { margin: 3px 0 0; font-size: 18px; }
.api-key-form { display: grid; gap: 15px; padding: 20px 22px; }
.api-key-form > label, .secret-field { display: grid; gap: 6px; color: #374151; font-size: 12px; font-weight: 600; }
.api-key-form input[type="text"], .api-key-form label > input:not([type]), .secret-field input { height: 36px; padding: 0 10px; border: 1px solid #d1d5db; border-radius: 6px; font: inherit; }
.api-key-form fieldset { display: grid; gap: 8px; margin: 0; padding: 12px; border: 1px solid #e5e7eb; border-radius: 7px; }
.api-key-form legend { padding: 0 5px; color: #6b7280; font-size: 11px; font-weight: 700; }
.scope-option { display: flex; align-items: flex-start; gap: 9px; }
.scope-option span { display: grid; gap: 2px; }
.scope-option code { color: #6b7280; font-size: 10px; }
.enabled-option { display: flex !important; grid-template-columns: auto 1fr; align-items: center; gap: 8px !important; }
.api-key-modal footer { justify-content: flex-end; padding: 14px 22px; border-top: 1px solid #e5e7eb; background: #f9fafb; }
.secret-modal .secret-warning { margin: 18px 22px 12px; border-color: #fde68a; background: #fffbeb; color: #92400e; }
.secret-field { margin: 0 22px 20px; }
.secret-field input { font-family: ui-monospace, SFMono-Regular, monospace; color: #0f172a; background: #f8fafc; }
@media (max-width: 760px) {
  .api-key-commandbar { align-items: flex-start; flex-direction: column; }
  .api-key-filterbar { flex-wrap: wrap; }
  .search-field { width: 100%; }
  .result-count { width: 100%; margin-left: 0; }
}
</style>
