<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { CirclePlus, KeyRound, Pencil, RefreshCw, TestTube2, Trash2 } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import { hasSuperAdminCapability } from "../../auth/roleCapabilities";
import type {
  CurrentUser,
  InternalModelCapability,
  InternalModelProviderConfig,
  InternalModelProviderModel,
  InternalModelTokenDefinition
} from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
  pageActive: boolean;
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

type ModelRow = {
  modelId: string;
  upstreamModelId: string;
  displayName: string;
  contextLimit: number | null;
  embeddingDimension: number | null;
  enabled: boolean;
  capabilities: InternalModelCapability[];
  probedCapabilities: InternalModelCapability[];
  lastProbedAt: string | null;
};

const CAPABILITY_OPTIONS: Array<{ value: InternalModelCapability; label: string }> = [
  { value: "CHAT", label: "对话 CHAT" },
  { value: "TOOLS", label: "工具 TOOLS" },
  { value: "VISION", label: "视觉 VISION" },
  { value: "REASONING", label: "推理 REASONING" },
  { value: "EMBEDDING", label: "向量 EMBEDDING" },
  { value: "RERANK", label: "重排 RERANK" },
  { value: "IMAGE", label: "图像 IMAGE" },
  { value: "SPEECH", label: "语音合成 SPEECH" },
  { value: "TRANSCRIPTION", label: "语音识别 TRANSCRIPTION" }
];

const api = inject<BackendApiClient>("api")!;
const queryClient = useQueryClient();
const rows = ref<ProviderRow[]>([]);
const tokenEditorOpen = ref(false);
const editingTokenId = ref<number | null>(null);
const tokenNameDraft = ref("");
const tokenValueDraft = ref("");
const selectedCatalogProviderId = ref("");
const modelRows = ref<ModelRow[]>([]);
const modelCatalogLoading = ref(false);
const modelCatalogSaving = ref(false);
const modelCatalogError = ref("");
let modelCatalogRequest = 0;
let pendingTokenCommand: TokenSaveCommand | null = null;

const hasSuperAdmin = computed(() => hasSuperAdminCapability(props.currentUser?.roles));

const query = useQuery({
  queryKey: ["internal-model-providers"],
  enabled: () => hasSuperAdmin.value && props.pageActive,
  retry: false,
  queryFn: () => api.getInternalModelProviders()
});

const tokenQuery = useQuery({
  queryKey: ["internal-model-tokens"],
  enabled: () => hasSuperAdmin.value && props.pageActive,
  retry: false,
  queryFn: () => api.listInternalModelTokens()
});

const refreshStatusQuery = useQuery({
  queryKey: ["internal-model-provider-refresh-status"],
  enabled: () => hasSuperAdmin.value && props.pageActive,
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
    const providerIds = (providers ?? []).map((provider) => provider.providerId);
    if (!providerIds.includes(selectedCatalogProviderId.value)) {
      selectedCatalogProviderId.value = providerIds[0] ?? "";
    }
  },
  { immediate: true }
);

watch(selectedCatalogProviderId, (providerId) => {
  void loadModelCatalog(providerId);
}, { immediate: true });

const tokens = computed(() => tokenQuery.data.value ?? []);
const tokenConfigured = computed(() => query.data.value?.tokenConfigured === true);
const refreshStatus = computed(() => refreshStatusQuery.data.value);
const persistedProviders = computed(() => query.data.value?.providers ?? []);
const selectedCatalogProvider = computed(() => persistedProviders.value.find(
  (provider) => provider.providerId === selectedCatalogProviderId.value
));
const canProbeSelectedProvider = computed(() => selectedCatalogProvider.value?.enabled === true
  && selectedCatalogProvider.value.tokenConfigured !== false);
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
    pendingTokenCommand = command;
    try {
      return command.tokenId == null
        ? await api.createInternalModelToken({ name: command.name, token: command.token ?? "" })
        : await api.updateInternalModelToken(command.tokenId, { name: command.name, token: command.token });
    } finally {
      // API Promise 一结束即同时擦除输入草稿与 mutation 变量，避免后续刷新期间仍保留密钥。
      command.token = undefined;
      tokenValueDraft.value = "";
      if (pendingTokenCommand === command) pendingTokenCommand = null;
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
  void loadModelCatalog(selectedCatalogProviderId.value);
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

function addModelRow() {
  modelRows.value.push({
    modelId: "",
    upstreamModelId: "",
    displayName: "",
    contextLimit: null,
    embeddingDimension: null,
    enabled: true,
    capabilities: ["CHAT"],
    probedCapabilities: [],
    lastProbedAt: null
  });
}

function removeModelRow(index: number) {
  modelRows.value.splice(index, 1);
}

/**
 * 模型目录只绑定已经保存的 Provider；请求代次用于避免快速切换 Provider 时旧响应覆盖新选择。
 */
async function loadModelCatalog(providerId = selectedCatalogProviderId.value) {
  const requestId = ++modelCatalogRequest;
  modelCatalogError.value = "";
  if (!providerId) {
    modelRows.value = [];
    modelCatalogLoading.value = false;
    return;
  }
  modelCatalogLoading.value = true;
  try {
    const models = await api.getInternalModelProviderModels(providerId);
    if (requestId !== modelCatalogRequest) return;
    modelRows.value = models.map(toModelRow);
  } catch (error) {
    if (requestId !== modelCatalogRequest) return;
    modelRows.value = [];
    modelCatalogError.value = formatError(error) || "模型目录加载失败";
  } finally {
    if (requestId === modelCatalogRequest) modelCatalogLoading.value = false;
  }
}

/**
 * 保存目录后后端会清空该 Provider 的全部旧探测状态。
 * 指定能力时除探测本次目标能力外，还会重新探测保存前已经成功的能力，避免探测 EMBEDDING 时意外清掉 CHAT 等可用状态。
 */
async function saveModelCatalog(probeCapability: InternalModelCapability | null) {
  const providerId = selectedCatalogProviderId.value;
  if (!providerId) {
    ElMessage.warning("请先保存并选择供应商");
    return;
  }
  const validationMessage = validateModelRows(modelRows.value);
  if (validationMessage) {
    ElMessage.warning(validationMessage);
    return;
  }
  if (probeCapability && !canProbeSelectedProvider.value) {
    ElMessage.warning(`执行 ${probeCapability} 探测前，请先启用供应商并配置 Token`);
    return;
  }

  const previouslyProbedCapabilities = new Map(modelRows.value.map((row) => [
    row.modelId.trim(),
    [...row.probedCapabilities]
  ]));
  modelCatalogSaving.value = true;
  modelCatalogError.value = "";
  try {
    const saved = await api.updateInternalModelProviderModels(providerId, {
      models: modelRows.value.map((row) => ({
        modelId: row.modelId.trim(),
        upstreamModelId: row.upstreamModelId.trim(),
        displayName: row.displayName.trim(),
        contextLimit: normalizePositiveNumber(row.contextLimit),
        embeddingDimension: row.capabilities.includes("EMBEDDING")
          ? normalizePositiveNumber(row.embeddingDimension)
          : null,
        enabled: row.enabled,
        capabilities: [...row.capabilities]
      }))
    });
    modelRows.value = saved.map(toModelRow);

    if (!probeCapability) {
      ElMessage.success("模型目录已保存；目录变更后请重新执行能力探测");
      return;
    }
    const targetModels = saved.filter((model) => model.enabled
      && model.declaredCapabilities.includes(probeCapability));
    if (targetModels.length === 0) {
      ElMessage.warning(`模型目录已保存，但没有启用且声明 ${probeCapability} 能力的模型`);
      return;
    }

    const probeTargets = saved.flatMap((model) => {
      if (!model.enabled) return [];
      const capabilities = new Set<InternalModelCapability>();
      for (const capability of previouslyProbedCapabilities.get(model.modelId) ?? []) {
        if (model.declaredCapabilities.includes(capability)) capabilities.add(capability);
      }
      if (model.declaredCapabilities.includes(probeCapability)) capabilities.add(probeCapability);
      return [...capabilities].map((capability) => ({ modelId: model.modelId, capability }));
    });
    const results = await Promise.allSettled(probeTargets.map(async (target) => ({
      ...target,
      result: await api.probeInternalModelProviderModel(providerId, target.modelId, target.capability)
    })));
    await loadModelCatalog(providerId);
    const targetResults = results.filter((result) => result.status === "rejected"
      || result.value.capability === probeCapability);
    const successfulCount = targetResults.filter((result) =>
      result.status === "fulfilled" && result.value.result.succeeded
    ).length;
    if (successfulCount === targetModels.length) {
      const usageHint = probeCapability === "CHAT"
        ? "可在记忆配置中选择"
        : "固定 CPU BGE 和企业 Embedding 可被记忆配置校验";
      ElMessage.success(`${probeCapability} 模型已保存并探测成功，${usageHint}`);
    } else {
      ElMessage.warning(`模型目录已保存，${probeCapability} 探测成功 ${successfulCount}/${targetModels.length}；请检查上游模型 ID、Base URL 和 Token`);
    }
  } catch (error) {
    modelCatalogError.value = formatError(error) || "模型目录保存或探测失败";
    ElMessage.error(modelCatalogError.value);
  } finally {
    modelCatalogSaving.value = false;
  }
}

function toModelRow(model: InternalModelProviderModel): ModelRow {
  return {
    modelId: model.modelId,
    upstreamModelId: model.upstreamModelId,
    displayName: model.displayName,
    contextLimit: normalizePositiveNumber(model.contextLimit),
    embeddingDimension: normalizePositiveNumber(model.embeddingDimension),
    enabled: model.enabled,
    capabilities: [...model.declaredCapabilities],
    probedCapabilities: [...model.probedCapabilities],
    lastProbedAt: model.lastProbedAt ?? null
  };
}

function validateModelRows(models: ModelRow[]) {
  const modelIds = new Set<string>();
  for (const [index, model] of models.entries()) {
    const rowNumber = index + 1;
    const modelId = model.modelId.trim();
    if (!modelId || !model.upstreamModelId.trim() || !model.displayName.trim()) {
      return `第 ${rowNumber} 个模型的公开 ID、上游 ID 和显示名不能为空`;
    }
    if (modelIds.has(modelId)) {
      return `公开模型 ID「${modelId}」不能重复`;
    }
    modelIds.add(modelId);
    if (model.capabilities.length === 0) {
      return `模型「${modelId}」至少声明一项能力`;
    }
    if (model.contextLimit != null && normalizePositiveNumber(model.contextLimit) == null) {
      return `模型「${modelId}」的上下文上限必须为正整数`;
    }
    if (model.capabilities.includes("EMBEDDING")
      && normalizePositiveNumber(model.embeddingDimension) == null) {
      return `Embedding 模型「${modelId}」必须填写正整数向量维度`;
    }
  }
  return "";
}

function normalizePositiveNumber(value: unknown): number | null {
  if (value == null || value === "") return null;
  const numberValue = Number(value);
  return Number.isInteger(numberValue) && numberValue > 0 ? numberValue : null;
}

function formatProbedCapabilities(row: ModelRow) {
  return row.probedCapabilities.length > 0
    ? `已通过 ${row.probedCapabilities.join(" / ")}`
    : "尚未探测";
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

watch(() => props.pageActive, (active) => {
  if (active) return;
  // 页面隐藏时只擦除敏感 Token；名称和供应商编辑草稿按普通页面状态继续保留。
  tokenValueDraft.value = "";
  if (pendingTokenCommand) pendingTokenCommand.token = undefined;
});
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

      <section class="internal-provider-section model-catalog-section">
        <header class="internal-provider-section-header model-catalog-header">
          <div>
            <div class="internal-provider-eyebrow"><TestTube2 :size="14" /> Provider → Models</div>
            <h3>模型目录与能力探测</h3>
            <p>配置公开模型与上游模型 ID；目录每次保存后都必须重新探测，记忆配置要求 CHAT 与固定 CPU BGE 分别通过对应能力探测。</p>
          </div>
          <div class="internal-provider-section-actions model-catalog-actions">
            <el-select
              v-model="selectedCatalogProviderId"
              size="small"
              aria-label="选择模型目录供应商"
              placeholder="选择已保存的供应商"
            >
              <el-option
                v-for="provider in persistedProviders"
                :key="provider.providerId"
                :label="`${provider.name} · ${provider.providerId}${provider.enabled ? '' : '（已停用）'}`"
                :value="provider.providerId"
              />
            </el-select>
            <el-button
              size="small"
              :icon="CirclePlus"
              :disabled="!selectedCatalogProviderId"
              @click="addModelRow"
            >新增模型</el-button>
            <el-button
              size="small"
              :loading="modelCatalogSaving"
              :disabled="!selectedCatalogProviderId || modelCatalogLoading"
              @click="saveModelCatalog(null)"
            >保存模型目录</el-button>
            <el-button
              size="small"
              type="primary"
              :icon="TestTube2"
              :loading="modelCatalogSaving"
              :disabled="!selectedCatalogProviderId || modelCatalogLoading || !canProbeSelectedProvider"
              @click="saveModelCatalog('CHAT')"
            >保存并探测 CHAT</el-button>
            <el-button
              size="small"
              type="primary"
              plain
              :icon="TestTube2"
              :loading="modelCatalogSaving"
              :disabled="!selectedCatalogProviderId || modelCatalogLoading || !canProbeSelectedProvider"
              @click="saveModelCatalog('EMBEDDING')"
            >保存并探测 EMBEDDING</el-button>
          </div>
        </header>

        <div v-if="modelCatalogError" class="model-catalog-alert">{{ modelCatalogError }}</div>
        <div v-if="!persistedProviders.length" class="internal-provider-empty-state">
          请先在“供应商关联”中保存至少一个供应商，再配置模型目录。
        </div>
        <div v-else-if="modelCatalogLoading" class="internal-provider-empty-state">正在加载模型目录…</div>
        <div v-else class="internal-provider-table-shell">
          <table class="internal-provider-table model-catalog-table">
            <thead>
              <tr>
                <th>公开 Model ID</th>
                <th>上游 Model ID</th>
                <th>显示名</th>
                <th>声明能力</th>
                <th>上下文上限</th>
                <th>向量维度</th>
                <th>启用</th>
                <th>探测状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in modelRows" :key="`${selectedCatalogProviderId}-${index}`">
                <td>
                  <el-input
                    v-model="row.modelId"
                    size="small"
                    :aria-label="`公开 Model ID ${index + 1}`"
                    placeholder="enterprise-chat"
                  />
                </td>
                <td>
                  <el-input
                    v-model="row.upstreamModelId"
                    size="small"
                    :aria-label="`上游 Model ID ${index + 1}`"
                    placeholder="供应商真实模型 ID"
                  />
                </td>
                <td>
                  <el-input
                    v-model="row.displayName"
                    size="small"
                    :aria-label="`模型显示名 ${index + 1}`"
                    placeholder="企业对话模型"
                  />
                </td>
                <td class="model-capability-cell">
                  <el-select
                    v-model="row.capabilities"
                    size="small"
                    multiple
                    collapse-tags
                    collapse-tags-tooltip
                    :aria-label="`模型能力 ${index + 1}`"
                    placeholder="选择能力"
                  >
                    <el-option
                      v-for="capability in CAPABILITY_OPTIONS"
                      :key="capability.value"
                      :label="capability.label"
                      :value="capability.value"
                    />
                  </el-select>
                </td>
                <td>
                  <el-input-number
                    v-model="row.contextLimit"
                    size="small"
                    :min="1"
                    :max="100000000"
                    :aria-label="`上下文上限 ${index + 1}`"
                    placeholder="可空"
                    controls-position="right"
                  />
                </td>
                <td>
                  <el-input-number
                    v-model="row.embeddingDimension"
                    size="small"
                    :min="1"
                    :max="1000000"
                    :disabled="!row.capabilities.includes('EMBEDDING')"
                    :aria-label="`向量维度 ${index + 1}`"
                    placeholder="仅 Embedding"
                    controls-position="right"
                  />
                </td>
                <td>
                  <el-switch v-model="row.enabled" size="small" :aria-label="`启用模型 ${row.modelId || index + 1}`" />
                </td>
                <td class="model-probe-status">
                  <el-tag
                    size="small"
                    :type="row.probedCapabilities.length > 0 ? 'success' : 'info'"
                    effect="plain"
                  >{{ formatProbedCapabilities(row) }}</el-tag>
                  <small v-if="row.lastProbedAt">{{ formatDate(row.lastProbedAt) }}</small>
                </td>
                <td>
                  <el-button
                    size="small"
                    text
                    type="danger"
                    :icon="Trash2"
                    :aria-label="`删除模型 ${row.modelId || index + 1}`"
                    @click="removeModelRow(index)"
                  />
                </td>
              </tr>
              <tr v-if="modelRows.length === 0">
                <td colspan="9" class="internal-provider-empty">
                  当前供应商没有模型。点击“新增模型”，填写上游真实 Model ID，然后执行对应能力的保存与探测。
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="selectedCatalogProviderId && !canProbeSelectedProvider" class="model-catalog-hint">
          当前供应商未启用或未配置 Token：可以保存目录，但必须先完成供应商关联，才能执行真实能力探测。
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

.model-catalog-section {
  border-left: 3px solid var(--panel-accent);
}

.model-catalog-header {
  align-items: flex-end;
}

.model-catalog-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.model-catalog-actions :deep(.el-select) {
  width: min(320px, 40vw);
}

.model-catalog-alert,
.model-catalog-hint {
  padding: 9px 12px;
  border-bottom: 1px solid var(--panel-line);
  background: #fff7f7;
  color: #b42318;
  font-size: 12px;
}

.model-catalog-hint {
  border-top: 1px solid #fde68a;
  border-bottom: 0;
  background: #fffbeb;
  color: #92400e;
}

.model-catalog-table {
  min-width: 1540px;
}

.model-catalog-table td:nth-child(1),
.model-catalog-table td:nth-child(2),
.model-catalog-table td:nth-child(3) {
  min-width: 190px;
}

.model-capability-cell {
  min-width: 230px;
}

.model-capability-cell :deep(.el-select),
.model-catalog-table :deep(.el-input-number) {
  width: 100%;
}

.model-probe-status {
  min-width: 170px;
}

.model-probe-status small {
  display: block;
  margin-top: 4px;
  color: var(--panel-muted);
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

  .model-catalog-actions {
    justify-content: flex-start;
  }

  .model-catalog-actions :deep(.el-select) {
    width: min(100%, 360px);
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
