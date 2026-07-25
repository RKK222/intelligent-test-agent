<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { CodeEditor } from "@test-agent/editor";
import { MergeConflictEditor } from "@test-agent/diff-viewer";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  AgentSkillHubAsset,
  AgentSkillHubAssetDetail,
  AgentSkillHubAssetType,
  AgentSkillHubConflictFile,
  AgentSkillHubUpdate,
  AgentSkillHubUpdateOperation,
  WorkspaceGitConflict
} from "@test-agent/shared-types";
import {
  ArrowDownToLine,
  Bot,
  Boxes,
  Building2,
  CheckCircle2,
  Clock3,
  Compass,
  GitCommitHorizontal,
  Library,
  Loader2,
  PackageOpen,
  RefreshCw,
  Search,
  Sparkles,
  UploadCloud,
  UsersRound,
  X
} from "lucide-vue-next";

const props = defineProps<{
  selectedAppId?: string;
  workspaceId?: string;
  canManage: boolean;
}>();

const emit = defineEmits<{
  updateCount: [count: number];
  changed: [paths: string[]];
}>();

const api = inject<BackendApiClient>("api")!;
if (!api) throw new Error("AgentSkillHub requires backend api");

type HubTab = "DISCOVER" | "AGENT" | "SKILL" | "REFERENCED" | "UPDATES";
const tab = ref<HubTab>("DISCOVER");
const keyword = ref("");
const loading = ref(false);
const actionLoading = ref(false);
const assets = ref<AgentSkillHubAsset[]>([]);
const updates = ref<AgentSkillHubUpdate[]>([]);
const updateCount = ref(0);
const agentTotal = ref(0);
const skillTotal = ref(0);
const referencedTotal = ref(0);
const selectedAsset = ref<AgentSkillHubAsset | null>(null);
const detail = ref<AgentSkillHubAssetDetail | null>(null);
const selectedFile = ref<string | null>(null);
const fileContent = ref("");
const fileLoading = ref(false);
const error = ref("");
const publishDialog = ref(false);
const referenceDialog = ref(false);
const removeDialog = ref(false);
const aliasTechnicalId = ref("");
const dependencyCandidates = ref<AgentSkillHubAsset[]>([]);
const selectedDependencies = ref<Set<string>>(new Set());
const updateOperation = ref<AgentSkillHubUpdateOperation | null>(null);
const activeConflictPath = ref<string | null>(null);

const activeConflict = computed(() => {
  const file = updateOperation.value?.files.find((item) => item.path === activeConflictPath.value);
  return file?.kind === "TEXT" ? toWorkspaceConflict(file) : null;
});
const activeBinaryConflict = computed(() =>
  updateOperation.value?.files.find((item) => item.path === activeConflictPath.value && item.kind === "BINARY") ?? null
);

const canPublishSelected = computed(() =>
  props.canManage && !selectedAsset.value?.builtin
    && selectedAsset.value?.sourceAppId === props.selectedAppId && !selectedAsset.value?.deleted
    && (!selectedAsset.value?.published || selectedAsset.value?.updateAvailable)
);
const canReferenceSelected = computed(() =>
  props.canManage && Boolean(props.workspaceId) && !selectedAsset.value?.builtin
    && selectedAsset.value?.published && !selectedAsset.value?.deleted && !selectedAsset.value?.referenceStatus
);
const canRemoveSelected = computed(() =>
  props.canManage && Boolean(props.workspaceId) && !selectedAsset.value?.builtin
    && Boolean(selectedAsset.value?.referenceStatus)
);

function selectedAssetType(): AgentSkillHubAssetType | undefined {
  if (tab.value === "AGENT" || tab.value === "SKILL") return tab.value;
  return undefined;
}

async function loadAssets() {
  loading.value = true;
  error.value = "";
  try {
    if (tab.value === "REFERENCED" && !props.workspaceId) {
      assets.value = [];
      selectedAsset.value = null;
      clearDetail();
      return;
    }
    const page = await api.listAgentSkillHubAssets({
      type: selectedAssetType(),
      keyword: keyword.value || undefined,
      referencedOnly: tab.value === "REFERENCED",
      targetWorkspaceId: props.workspaceId,
      page: 1,
      size: 100
    });
    // 未选个人工作区时没有可判定的目标引用上下文，避免把其它应用的引用误显示为当前引用。
    assets.value = props.workspaceId
      ? page.items
      : page.items.map((item) => ({ ...item, referenced: false, referenceStatus: null }));
    if (selectedAsset.value && assets.value.some((item) => item.assetId === selectedAsset.value?.assetId)) {
      await selectAsset(selectedAsset.value);
    } else {
      closeDetail();
    }
  } catch (cause) {
    error.value = message(cause);
  } finally {
    loading.value = false;
  }
}

/** Hero 概览使用轻量分页查询，只读取总数，不拉取额外制品正文。 */
async function refreshOverview() {
  try {
    const [agents, skills, referenced] = await Promise.all([
      api.listAgentSkillHubAssets({ type: "AGENT", page: 1, size: 1 }),
      api.listAgentSkillHubAssets({ type: "SKILL", page: 1, size: 1 }),
      props.workspaceId
        ? api.listAgentSkillHubAssets({ referencedOnly: true, targetWorkspaceId: props.workspaceId, page: 1, size: 1 })
        : Promise.resolve({ items: [], total: 0, page: 1, size: 1 })
    ]);
    agentTotal.value = agents.total;
    skillTotal.value = skills.total;
    referencedTotal.value = referenced.total;
  } catch {
    // 概览统计失败不阻断 Hub 目录和详情使用。
  }
}

async function loadUpdates() {
  loading.value = true;
  error.value = "";
  try {
    const page = await api.listAgentSkillHubUpdates(1, 100, props.workspaceId);
    updates.value = page.items;
    updateCount.value = page.total;
    emit("updateCount", page.total);
  } catch (cause) {
    error.value = message(cause);
  } finally {
    loading.value = false;
  }
}

async function refreshCount() {
  try {
    updateCount.value = (await api.getAgentSkillHubUpdateCount(props.workspaceId)).count;
    emit("updateCount", updateCount.value);
  } catch {
    // 活动栏角标属于辅助信息，短暂失败不覆盖主界面错误。
  }
}

async function selectAsset(asset: AgentSkillHubAsset) {
  selectedAsset.value = asset;
  detail.value = await api.getAgentSkillHubAsset(asset.assetId, undefined, props.workspaceId);
  selectedFile.value = detail.value.files[0]?.path ?? null;
  if (selectedFile.value) await readFile(selectedFile.value);
  else fileContent.value = "";
}

async function readFile(path: string) {
  if (!detail.value) return;
  selectedFile.value = path;
  fileLoading.value = true;
  try {
    fileContent.value = (await api.readAgentSkillHubFile(detail.value.selectedRevisionId, path)).content;
  } catch (cause) {
    error.value = message(cause);
  } finally {
    fileLoading.value = false;
  }
}

function clearDetail() {
  detail.value = null;
  selectedFile.value = null;
  fileContent.value = "";
}

function closeDetail() {
  selectedAsset.value = null;
  clearDetail();
}

async function openPublish() {
  if (!selectedAsset.value) return;
  actionLoading.value = true;
  try {
    const [agents, skills] = await Promise.all([
      api.listAgentSkillHubAssets({ type: "AGENT", targetWorkspaceId: props.workspaceId, page: 1, size: 100 }),
      api.listAgentSkillHubAssets({ type: "SKILL", targetWorkspaceId: props.workspaceId, page: 1, size: 100 })
    ]);
    dependencyCandidates.value = [...agents.items, ...skills.items]
      .filter((item) => item.published && item.assetId !== selectedAsset.value?.assetId);
    selectedDependencies.value = new Set(detail.value?.dependencies.map((item) => item.assetId) ?? []);
    publishDialog.value = true;
  } finally {
    actionLoading.value = false;
  }
}

function toggleDependency(assetId: string) {
  const next = new Set(selectedDependencies.value);
  if (next.has(assetId)) next.delete(assetId); else next.add(assetId);
  selectedDependencies.value = next;
}

async function publishSelected() {
  if (!selectedAsset.value) return;
  actionLoading.value = true;
  try {
    await api.publishAgentSkillHubAsset(selectedAsset.value.assetId, [...selectedDependencies.value]);
    publishDialog.value = false;
    ElMessage.success("已发布到 Hub");
    await loadAssets();
  } catch (cause) {
    ElMessage.error(message(cause));
  } finally {
    actionLoading.value = false;
  }
}

function openReference() {
  if (!selectedAsset.value) return;
  aliasTechnicalId.value = selectedAsset.value.technicalId;
  referenceDialog.value = true;
}

async function createReference() {
  if (!selectedAsset.value || !props.workspaceId) return;
  actionLoading.value = true;
  try {
    const result = await api.createAgentSkillHubReference(
      props.workspaceId,
      selectedAsset.value.assetId,
      aliasTechnicalId.value
    );
    referenceDialog.value = false;
    emit("changed", [result.targetPath.endsWith("/") ? `${result.targetPath}SKILL.md` : result.targetPath]);
    ElMessage.success(result.message);
    await Promise.all([loadAssets(), refreshCount(), refreshOverview()]);
  } catch (cause) {
    ElMessage.error(message(cause));
  } finally {
    actionLoading.value = false;
  }
}

async function removeReference() {
  if (!selectedAsset.value || !props.workspaceId) return;
  actionLoading.value = true;
  try {
    const result = await api.removeAgentSkillHubReference(props.workspaceId, selectedAsset.value.assetId);
    removeDialog.value = false;
    emit("changed", [result.targetPath.endsWith("/") ? `${result.targetPath}SKILL.md` : result.targetPath]);
    ElMessage.success(result.message);
    await Promise.all([loadAssets(), refreshCount(), refreshOverview()]);
  } catch (cause) {
    ElMessage.error(message(cause));
  } finally {
    actionLoading.value = false;
  }
}

async function beginUpdate(update: AgentSkillHubUpdate) {
  if (!props.workspaceId) {
    ElMessage.warning("请先切换到该应用的个人工作区");
    return;
  }
  actionLoading.value = true;
  try {
    const operation = await api.startAgentSkillHubReferenceUpdate(props.workspaceId, update.referenceId);
    if (operation.status === "CONFLICT") {
      updateOperation.value = operation;
      activeConflictPath.value = operation.files.find((file) => file.conflicted)?.path ?? null;
      return;
    }
    const target = operation.files.find((file) => file.path.includes("/SKILL.md"))?.path
      ?? operation.files[0]?.path;
    if (target) emit("changed", [target]);
    ElMessage.success("已合并最新发布版，请在 Git Changes 中确认并推送");
    await loadUpdates();
  } catch (cause) {
    ElMessage.error(message(cause));
  } finally {
    actionLoading.value = false;
  }
}

async function resolveConflict(payload: { resolution: string; content?: string | null }) {
  if (!props.workspaceId || !updateOperation.value || !activeConflictPath.value) return;
  actionLoading.value = true;
  try {
    const operation = await api.resolveAgentSkillHubUpdateConflict(
      props.workspaceId,
      updateOperation.value.operationId,
      { path: activeConflictPath.value, ...payload }
    );
    updateOperation.value = operation;
    const next = operation.files.find((file) => file.conflicted);
    if (next) {
      activeConflictPath.value = next.path;
      return;
    }
    const result = await api.completeAgentSkillHubUpdate(props.workspaceId, operation.operationId);
    updateOperation.value = null;
    activeConflictPath.value = null;
    emit("changed", [result.targetPath.endsWith("/") ? `${result.targetPath}SKILL.md` : result.targetPath]);
    ElMessage.success(result.message);
    await loadUpdates();
  } catch (cause) {
    ElMessage.error(message(cause));
  } finally {
    actionLoading.value = false;
  }
}

async function abortConflict() {
  if (!props.workspaceId || !updateOperation.value) return;
  await api.abortAgentSkillHubUpdate(props.workspaceId, updateOperation.value.operationId);
  updateOperation.value = null;
  activeConflictPath.value = null;
}

function toWorkspaceConflict(file: AgentSkillHubConflictFile): WorkspaceGitConflict {
  return {
    path: file.path,
    rawStatus: "HUB",
    baseContent: file.baseContent,
    currentContent: file.currentContent,
    incomingContent: file.incomingContent,
    resultContent: file.resultContent
  };
}

function message(cause: unknown) {
  return cause instanceof Error ? cause.message : "Hub 操作失败";
}

/** 列表状态必须同时给出文本与图标，不能只依赖颜色表达。 */
function assetStatus(asset: AgentSkillHubAsset) {
  if (asset.deleted) return { key: "deleted", label: "远端已删除", title: "该资产已从来源远端删除" };
  if (asset.builtin) return { key: "builtin", label: "平台内置", title: "平台内置、无需发布或引用" };
  if (asset.referenceStatus === "PENDING_REMOVE") {
    return { key: "removing", label: "取消待推送", title: "已从当前 worktree 移除，push 后正式解除引用" };
  }
  if (asset.referenceStatus === "PENDING_PUSH") {
    return { key: "waiting", label: "引用待推送", title: "已写入当前 worktree，尚未 push 到远端" };
  }
  if (asset.referenceStatus === "UPDATE_CONFLICT") {
    return { key: "conflict", label: "更新有冲突", title: "引用更新存在待解决冲突" };
  }
  if (asset.referenceStatus === "ACTIVE" && asset.updateAvailable) {
    return { key: "update", label: "已引用 · 新版待发布", title: "当前引用已生效，来源另有尚未发布的新 push" };
  }
  if (asset.updateAvailable) return { key: "update", label: "新版待发布", title: "最新 push 内容尚未发布" };
  if (asset.referenceStatus === "ACTIVE" && asset.referenced) {
    return { key: "referenced", label: "已发布 · 已引用", title: "该引用已由当前应用成功 push 到远端" };
  }
  if (asset.published) return { key: "published", label: "已发布", title: "其他应用可以引用" };
  return { key: "pushed", label: "仅已推送", title: "已形成不可变快照，尚未发布" };
}

function consumerStatus(status: string) {
  if (status === "PENDING_PUSH") return { key: "waiting", label: "引用待推送" };
  if (status === "PENDING_REMOVE") return { key: "removing", label: "取消待推送" };
  if (status === "UPDATE_CONFLICT") return { key: "conflict", label: "更新有冲突" };
  return { key: "referenced", label: "引用生效" };
}

watch(tab, async (value) => {
  if (value === "UPDATES") await loadUpdates();
  else await loadAssets();
});

watch(() => props.workspaceId, async () => {
  if (tab.value === "UPDATES") await loadUpdates();
  else await Promise.all([loadAssets(), refreshCount(), refreshOverview()]);
});

let searchTimer: ReturnType<typeof setTimeout> | null = null;
watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    if (tab.value !== "UPDATES") void loadAssets();
  }, 250);
});

onMounted(async () => {
  await Promise.all([loadAssets(), refreshCount(), refreshOverview()]);
});
</script>

<template>
  <section class="hub" data-testid="agent-skill-hub">
    <div v-if="activeConflict" class="hub-conflict">
      <MergeConflictEditor
        :conflict="activeConflict"
        :resolving="actionLoading"
        @resolve="resolveConflict"
        @abort="abortConflict"
        @close="activeConflictPath = null"
      />
    </div>
    <div v-else-if="activeBinaryConflict" class="hub-conflict hub-binary-conflict">
      <section>
        <h2>二进制更新冲突</h2>
        <code>{{ activeBinaryConflict.path }}</code>
        <p>二进制文件无法自动合并。请选择保留当前文件、采用 Hub 最新文件，或删除该文件。</p>
        <div>
          <button class="hub-secondary" :disabled="actionLoading" @click="resolveConflict({ resolution: 'CURRENT' })">保留当前</button>
          <button class="hub-primary" :disabled="actionLoading" @click="resolveConflict({ resolution: 'INCOMING' })">采用 Hub 最新版</button>
          <button class="hub-secondary" :disabled="actionLoading" @click="resolveConflict({ resolution: 'DELETE' })">删除文件</button>
          <button class="hub-secondary" :disabled="actionLoading" @click="abortConflict">取消本次更新</button>
        </div>
      </section>
    </div>

    <header class="hub-header">
      <div class="hub-header-main">
        <div class="hub-brand">
          <span class="hub-brand-icon"><Boxes :size="16" /></span>
          <div class="hub-brand-text">
            <div class="hub-title-row">
              <h1>Agent &amp; Skill Hub</h1>
              <span class="hub-kicker-tag">共享能力中心</span>
            </div>
            <p>发现、发布并追踪来自所有应用远端提交的可复用能力。</p>
          </div>
        </div>
        <div class="hub-header-actions">
          <div class="hub-overview" aria-label="Hub 概览">
            <div class="hub-overview-item"><Bot :size="13" /><span><b>{{ agentTotal }}</b> Agents</span></div>
            <div class="hub-overview-item"><Sparkles :size="13" /><span><b>{{ skillTotal }}</b> Skills</span></div>
            <div class="hub-overview-item"><Library :size="13" /><span><b>{{ referencedTotal }}</b> 当前应用引用</span></div>
            <div class="hub-overview-item" :class="{ 'is-alert': updateCount > 0 }"><Clock3 :size="13" /><span><b>{{ updateCount }}</b> 待处理</span></div>
          </div>
          <button class="hub-refresh-btn" type="button" @click="tab === 'UPDATES' ? loadUpdates() : loadAssets()">
            <RefreshCw :size="13" :class="loading && 'hub-spin'" />刷新目录
          </button>
        </div>
      </div>
    </header>

    <nav class="hub-tabs" aria-label="Hub 分类">
      <button :class="tab === 'DISCOVER' && 'is-active'" @click="tab = 'DISCOVER'"><Compass :size="14" />发现</button>
      <button :class="tab === 'AGENT' && 'is-active'" @click="tab = 'AGENT'"><Bot :size="14" />Agents</button>
      <button :class="tab === 'SKILL' && 'is-active'" @click="tab = 'SKILL'"><Sparkles :size="14" />Skills</button>
      <span class="hub-tabs-divider" />
      <button :class="tab === 'REFERENCED' && 'is-active'" @click="tab = 'REFERENCED'">
        <Library :size="14" />当前应用<span class="hub-nav-note">{{ referencedTotal }}</span>
      </button>
      <button :class="tab === 'UPDATES' && 'is-active'" @click="tab = 'UPDATES'">
        <Clock3 :size="14" />待更新<span v-if="updateCount" class="hub-tab-count" aria-label="待更新数量">{{ updateCount }}</span>
      </button>
    </nav>

    <div v-if="error" class="hub-error">{{ error }}</div>

    <div v-if="tab === 'UPDATES'" class="hub-updates">
      <div class="hub-section-heading">
        <div><span>UPDATE INBOX</span><h2>引用更新</h2><p>所有变更都先进入当前 worktree，由你确认并 push 后生效。</p></div>
      </div>
      <div v-if="loading" class="hub-loading"><Loader2 class="hub-spin" :size="18" />正在检查更新</div>
      <div v-else-if="!updates.length" class="hub-empty"><CheckCircle2 :size="28" /><strong>已是最新</strong><span>当前应用引用的 Agent / Skill 没有待确认更新。</span></div>
      <article v-for="update in updates" v-else :key="update.referenceId" class="hub-update-card">
        <div class="hub-asset-avatar" :data-type="update.targetPath.includes('/agents/') ? 'AGENT' : 'SKILL'">
          <Bot v-if="update.targetPath.includes('/agents/')" :size="17" /><Sparkles v-else :size="17" />
        </div>
        <div class="hub-update-main">
          <strong>{{ update.displayName || update.technicalId }}</strong>
          <span>原创应用：{{ update.sourceAppName }} · {{ update.sourceWorkspaceName }}</span>
          <code>{{ update.activeRevisionId?.slice(-8) || '未推送' }} → {{ update.latestRevisionId.slice(-8) }}</code>
        </div>
        <span :class="['hub-update-state', update.status === 'PENDING_PUSH' && 'waiting']">
          <RefreshCw v-if="update.status !== 'PENDING_PUSH'" :size="13" />
          <UploadCloud v-else :size="13" />
          {{ update.status === 'PENDING_PUSH' ? '引用待推送' : '待确认更新' }}
        </span>
        <button class="hub-primary" :disabled="!canManage || actionLoading" @click="beginUpdate(update)">查看并更新</button>
      </article>
    </div>

    <div v-else class="hub-browser">
      <main class="hub-catalog">
        <div class="hub-catalog-head">
          <div>
            <span>{{ tab === 'REFERENCED' ? 'CURRENT APPLICATION' : 'CAPABILITY CATALOG' }}</span>
            <h2>{{ tab === 'REFERENCED' ? '当前应用的能力' : tab === 'AGENT' ? 'Agent 目录' : tab === 'SKILL' ? 'Skill 目录' : '探索全部能力' }}</h2>
            <p>{{ tab === 'REFERENCED' ? '包含已生效、待推送和取消待推送的全部引用。' : '每个版本都来自成功 push 的不可变远端快照。' }}</p>
          </div>
          <label class="hub-search"><Search :size="14" /><input v-model="keyword" placeholder="搜索名称、应用或技术 ID" /></label>
        </div>
        <div v-if="loading" class="hub-loading"><Loader2 class="hub-spin" :size="18" />正在读取远端快照</div>
        <div v-else-if="assets.length" class="hub-card-grid">
          <button
            v-for="asset in assets"
            :key="asset.assetId"
            :class="['hub-asset-card', selectedAsset?.assetId === asset.assetId && 'is-active']"
            @click="selectAsset(asset)"
          >
            <span class="hub-card-top">
              <span class="hub-asset-avatar" :data-type="asset.type"><Bot v-if="asset.type === 'AGENT'" :size="18" /><Sparkles v-else :size="18" /></span>
              <span class="hub-card-type">{{ asset.type }}</span>
              <span :class="['hub-asset-status', assetStatus(asset).key]" :title="assetStatus(asset).title">
                <CheckCircle2 v-if="['builtin', 'published', 'referenced'].includes(assetStatus(asset).key)" :size="11" />
                <RefreshCw v-else-if="assetStatus(asset).key === 'update'" :size="11" />
                <UploadCloud v-else-if="assetStatus(asset).key === 'waiting'" :size="11" />
                <Clock3 v-else :size="11" />{{ assetStatus(asset).label }}
              </span>
            </span>
            <strong>{{ asset.displayName || asset.technicalId }}</strong>
            <code>{{ asset.technicalId }}</code>
            <p>{{ asset.description || '该能力暂未提供说明。' }}</p>
            <span class="hub-card-meta">
              <span class="hub-card-origin"><Building2 :size="12" />原创应用：<b>{{ asset.builtin ? '平台内置' : asset.sourceAppName }}</b></span>
              <span :aria-label="`${asset.referenceCount} 个应用引用`"><UsersRound :size="12" />{{ asset.referenceCount }} 个引用</span>
            </span>
          </button>
        </div>
        <div v-else class="hub-empty compact">
          <Library v-if="tab === 'REFERENCED'" :size="28" /><PackageOpen v-else :size="28" />
          <strong>{{ tab === 'REFERENCED' ? (workspaceId ? '当前应用还没有引用能力' : '请先选择个人工作区') : '没有匹配的远端快照' }}</strong>
          <span>{{ tab === 'REFERENCED' ? '从发现、Agent 或 Skill 目录中选择已发布能力即可引用。' : '可调整搜索条件后重试。' }}</span>
        </div>
      </main>

      <Transition name="hub-slide">
        <aside v-if="selectedAsset" class="hub-drawer">
          <div class="hub-drawer-overlay" @click="closeDetail" />
          <div class="hub-detail-panel">
            <div class="hub-detail-head-bar">
              <div class="hub-drawer-brand">
                <span class="hub-asset-avatar" :data-type="selectedAsset.type">
                  <Bot v-if="selectedAsset.type === 'AGENT'" :size="17" />
                  <Sparkles v-else :size="17" />
                </span>
                <div>
                  <span class="hub-card-type">{{ selectedAsset.type }}</span>
                  <h2>{{ selectedAsset.displayName || selectedAsset.technicalId }}</h2>
                </div>
              </div>
              <button class="hub-close-btn" type="button" title="关闭详情" @click="closeDetail">
                <X :size="18" />
              </button>
            </div>

            <div class="hub-origin-banner">
              <Building2 :size="15" />
              <div class="hub-origin-info">
                <span class="hub-origin-label">原创应用</span>
                <strong>{{ selectedAsset.builtin ? '平台内置' : selectedAsset.sourceAppName }}</strong>
                <span v-if="!selectedAsset.builtin" class="hub-origin-workspace">（工作区：{{ selectedAsset.sourceWorkspaceName }}）</span>
              </div>
            </div>

            <div v-if="detail" class="hub-detail-content">
              <div class="hub-detail-top">
                <div>
                  <code>{{ selectedAsset.technicalId }}</code>
                  <p>{{ selectedAsset.description || '该资产未提供说明。可先阅读完整内容，再决定是否发布或引用。' }}</p>
                </div>
                <div class="hub-actions">
                  <button v-if="canPublishSelected" class="hub-secondary" :disabled="actionLoading" @click="openPublish"><UploadCloud :size="14" />发布</button>
                  <button v-if="canReferenceSelected" class="hub-primary" :disabled="actionLoading" @click="openReference"><ArrowDownToLine :size="14" />引用到当前应用</button>
                  <button
                    v-if="canRemoveSelected"
                    class="hub-danger"
                    :disabled="actionLoading || selectedAsset.referenceStatus === 'PENDING_REMOVE'"
                    @click="removeDialog = true"
                  >{{ selectedAsset.referenceStatus === 'PENDING_REMOVE' ? '取消待推送' : '取消引用' }}</button>
                </div>
              </div>

              <div class="hub-revision-strip">
                <GitCommitHorizontal :size="17" />
                <div><strong>{{ selectedAsset.builtin ? '平台公共提交' : '最新 push' }}</strong><code>{{ selectedAsset.pushedRevisionId.slice(-12) }}</code></div>
                <span class="hub-revision-line" />
                <div><strong>{{ selectedAsset.builtin ? '平台内置' : selectedAsset.published ? '已发布' : '等待发布' }}</strong><code>{{ selectedAsset.builtin ? '无需引用' : selectedAsset.publishedRevisionId?.slice(-12) || '不可被引用' }}</code></div>
                <span v-if="selectedAsset.updateAvailable" class="hub-status update">有未发布更新</span>
              </div>

              <div v-if="detail.dependencies.length" class="hub-dependencies">
                <span>随引用安装</span>
                <b v-for="dependency in detail.dependencies" :key="dependency.assetId">{{ dependency.displayName || dependency.technicalId }}</b>
              </div>

              <section class="hub-consumers">
                <div class="hub-consumers-title">
                  <span><UsersRound :size="15" /></span>
                  <div><strong>引用此能力的应用</strong><small>{{ selectedAsset.referenceCount }} 个应用已生效</small></div>
                </div>
                <div v-if="detail.consumers.length" class="hub-consumer-list">
                  <article v-for="consumer in detail.consumers" :key="consumer.referenceId">
                    <span class="hub-consumer-icon"><Building2 :size="14" /></span>
                    <div><strong>{{ consumer.targetAppName }}</strong><small>{{ consumer.targetWorkspaceName }} · {{ consumer.aliasTechnicalId }}</small></div>
                    <em :class="consumerStatus(consumer.status).key">{{ consumerStatus(consumer.status).label }}</em>
                  </article>
                </div>
                <p v-else>尚未被任何应用引用。待推送引用仅对目标应用成员可见。</p>
              </section>

              <div class="hub-files">
                <div class="hub-file-tabs">
                  <button v-for="file in detail.files" :key="file.path" :class="selectedFile === file.path && 'is-active'" @click="readFile(file.path)">
                    {{ file.path }}<small>{{ Math.max(1, Math.ceil(file.size / 1024)) }} KB</small>
                  </button>
                </div>
                <div class="hub-code">
                  <div v-if="fileLoading" class="hub-loading overlay"><Loader2 class="hub-spin" :size="18" />加载不可变制品</div>
                  <CodeEditor :path="selectedFile || undefined" :content="fileContent" :readonly="true" :dirty="false" />
                </div>
              </div>
            </div>
            <div v-else class="hub-loading overlay"><Loader2 class="hub-spin" :size="18" />正在加载资产详情...</div>
          </div>
        </aside>
      </Transition>
    </div>

    <div v-if="publishDialog" class="hub-modal-backdrop" @click.self="publishDialog = false">
      <section class="hub-modal">
        <h2>发布 {{ selectedAsset?.displayName || selectedAsset?.technicalId }}</h2>
        <p>本次会把最新 push 修订公开为可引用版本。选择精确依赖后，引用者会一并安装。</p>
        <div class="hub-check-list">
          <label v-for="candidate in dependencyCandidates" :key="candidate.assetId">
            <input type="checkbox" :checked="selectedDependencies.has(candidate.assetId)" @change="toggleDependency(candidate.assetId)" />
            <span><strong>{{ candidate.displayName || candidate.technicalId }}</strong><small>{{ candidate.type }} · {{ candidate.sourceAppName }}</small></span>
          </label>
          <div v-if="!dependencyCandidates.length" class="hub-empty compact">暂无可选的已发布依赖</div>
        </div>
        <footer><button class="hub-secondary" @click="publishDialog = false">取消</button><button class="hub-primary" :disabled="actionLoading" @click="publishSelected">确认发布</button></footer>
      </section>
    </div>

    <div v-if="referenceDialog" class="hub-modal-backdrop" @click.self="referenceDialog = false">
      <section class="hub-modal small">
        <h2>引用到当前个人 worktree</h2>
        <p>引用会写入本地 Agent/Skill 配置并立即刷新本人运行态；你仍需在 Git Changes 中提交并推送。</p>
        <label class="hub-field"><span>英文技术 ID</span><input v-model="aliasTechnicalId" autocomplete="off" /></label>
        <footer><button class="hub-secondary" @click="referenceDialog = false">取消</button><button class="hub-primary" :disabled="actionLoading || !aliasTechnicalId" @click="createReference">写入引用</button></footer>
      </section>
    </div>

    <div v-if="removeDialog" class="hub-modal-backdrop" @click.self="removeDialog = false">
      <section class="hub-modal small">
        <h2>取消引用 {{ selectedAsset?.displayName || selectedAsset?.technicalId }}</h2>
        <p>系统会先从当前个人 worktree 移除文件。提交并成功 push 后，远端引用关系才会正式解除；push 前状态会显示“取消待推送”。</p>
        <footer><button class="hub-secondary" @click="removeDialog = false">暂不取消</button><button class="hub-danger" :disabled="actionLoading" @click="removeReference">移除并等待推送</button></footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.hub{--hub-ink:#15233b;--hub-muted:#69768c;--hub-blue:#3567ee;--hub-line:#e2e8f0;position:relative;display:flex;height:100%;min-height:0;flex-direction:column;background:#f6f8fc;color:var(--hub-ink);font-family:var(--font-sans)}
.hub-header{position:relative;flex:none;background:#fff;border-bottom:1px solid #e2e8f0;padding:12px 24px 12px 54px;box-shadow:0 1px 2px rgba(0,0,0,.02)}
.hub-header-main{display:flex;align-items:center;justify-content:space-between;gap:16px}
.hub-brand{display:flex;align-items:center;gap:10px}
.hub-brand-icon{display:grid;height:32px;width:32px;flex:none;place-items:center;border-radius:8px;background:#eff6ff;color:#2563eb}
.hub-brand-text{display:flex;flex-direction:column}
.hub-title-row{display:flex;align-items:center;gap:8px}
.hub-title-row h1{margin:0;font-size:16px;font-weight:700;letter-spacing:-.02em;color:#0f172a;line-height:1.2}
.hub-kicker-tag{display:inline-flex;align-items:center;border-radius:4px;background:#f1f5f9;padding:2px 6px;color:#64748b;font-size:10px;font-weight:600;white-space:nowrap}
.hub-brand p{margin:2px 0 0;color:#64748b;font-size:11px}
.hub-header-actions{display:flex;align-items:center;gap:14px}
.hub-overview{display:flex;align-items:center;gap:8px}
.hub-overview-item{display:flex;align-items:center;gap:6px;border:1px solid #e2e8f0;border-radius:7px;background:#f8fafc;padding:5px 10px;color:#475569;font-size:11px}
.hub-overview-item b{margin-right:2px;color:#0f172a;font-weight:700}
.hub-overview-item.is-alert{border-color:#fde68a;background:#fffbeb;color:#b45309}
.hub-overview-item.is-alert b{color:#92400e}
.hub-refresh-btn{display:flex;align-items:center;gap:6px;border:1px solid #cbd5e1;border-radius:7px;background:#fff;padding:6px 12px;color:#334155;font-size:11px;font-weight:600;transition:all .15s ease;cursor:pointer}
.hub-refresh-btn:hover{background:#f1f5f9;border-color:#94a3b8;color:#0f172a}

.hub-tabs{display:flex;height:40px;flex:none;align-items:center;gap:4px;border-bottom:1px solid var(--hub-line);background:#fff;padding:0 24px 0 54px}
.hub-tabs button{display:flex;height:30px;align-items:center;gap:6px;border:0;border-radius:6px;background:transparent;padding:0 12px;color:#64748b;font-size:12px;font-weight:500;transition:all .15s ease;cursor:pointer}
.hub-tabs button:hover{background:#f1f5f9;color:#0f172a}
.hub-tabs button.is-active{background:#eff6ff;color:#2563eb;font-weight:700}
.hub-tabs-divider{height:18px;width:1px;margin:0 6px;background:#e2e8f0}
.hub-nav-note{color:#94a3b8;font-size:10px;margin-left:2px}
.hub-tab-count{display:grid;min-width:16px;height:16px;place-items:center;border-radius:4px;background:#ef4444;padding:0 4px;color:#fff;font-size:9px;line-height:1;font-weight:700}

.hub-browser{position:relative;display:flex;min-height:0;flex:1;overflow:hidden}
.hub-catalog{flex:1;min-width:0;overflow:auto;padding:20px 24px 32px}
.hub-catalog-head,.hub-section-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:18px;margin-bottom:16px}
.hub-catalog-head>div>span,.hub-section-heading>div>span{color:#64748b;font-family:var(--font-mono);font-size:9px;letter-spacing:.12em;font-weight:600}
.hub-catalog-head h2,.hub-section-heading h2{margin:2px 0 1px;font-size:18px;letter-spacing:-.02em;color:#0f172a}
.hub-catalog-head p,.hub-section-heading p{margin:0;color:#64748b;font-size:11px}
.hub-search{display:flex;width:min(300px,45%);height:34px;align-items:center;gap:7px;border:1px solid #cbd5e1;border-radius:7px;background:#fff;padding:0 10px;color:#94a3b8;box-shadow:0 1px 2px rgba(0,0,0,.03)}
.hub-search:focus-within{border-color:#3b82f6;box-shadow:0 0 0 3px rgba(59,130,246,.12)}
.hub-search input{min-width:0;flex:1;border:0;outline:0;font-size:11px;color:#0f172a}

.hub-card-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:14px}
.hub-asset-card{display:flex;min-height:175px;min-width:0;flex-direction:column;border:1px solid #e2e8f0;border-radius:10px;background:#fff;padding:14px;text-align:left;box-shadow:0 1px 3px rgba(0,0,0,.04);transition:all .18s ease;cursor:pointer}
.hub-asset-card:hover{transform:translateY(-2px);border-color:#93c5fd;box-shadow:0 8px 20px rgba(37,99,235,.08)}
.hub-asset-card.is-active{border-color:#3b82f6;box-shadow:0 0 0 2px rgba(59,130,246,.2),0 8px 20px rgba(37,99,235,.08)}
.hub-card-top{display:flex;align-items:center}
.hub-asset-avatar{display:grid;height:34px;width:34px;place-items:center;border-radius:9px;background:#eff6ff;color:#2563eb}
.hub-asset-avatar[data-type="SKILL"]{background:#ccfbf1;color:#0d9488}
.hub-card-type{margin-left:8px;color:#64748b;font-family:var(--font-mono);font-size:9px;letter-spacing:.09em;font-weight:600}
.hub-card-top .hub-asset-status{margin-left:auto}
.hub-asset-card>strong{overflow:hidden;margin-top:10px;text-overflow:ellipsis;font-size:14px;white-space:nowrap;color:#0f172a;font-weight:700}
.hub-asset-card>code{margin-top:2px;color:#64748b;font-size:9px;font-family:var(--font-mono)}
.hub-asset-card>p{display:-webkit-box;min-height:32px;overflow:hidden;margin:8px 0;color:#475569;font-size:11px;line-height:1.5;-webkit-box-orient:vertical;-webkit-line-clamp:2}
.hub-card-meta{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:auto;border-top:1px solid #f1f5f9;padding-top:9px;color:#64748b;font-size:10px}
.hub-card-meta>span{display:flex;min-width:0;align-items:center;gap:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hub-card-origin{color:#334155}.hub-card-origin b{color:#0f172a;font-weight:600}
.hub-asset-status{display:inline-flex;align-items:center;gap:3px;border:1px solid #cbd5e1;border-radius:5px;background:#fff;padding:3px 6px;color:#475569;font-size:9px;font-style:normal;font-weight:700;white-space:nowrap}
.hub-asset-status.builtin{border-color:#bfdbfe;background:#eff6ff;color:#1d4ed8}
.hub-asset-status.published,.hub-asset-status.referenced{border-color:#a7f3d0;background:#ecfdf5;color:#047857}
.hub-asset-status.update,.hub-asset-status.pushed,.hub-asset-status.waiting{border-color:#fde68a;background:#fffbeb;color:#b45309}
.hub-asset-status.deleted,.hub-asset-status.removing,.hub-asset-status.conflict{border-color:#fecaca;background:#fef2f2;color:#b91c1c}

/* Drawer / Slide out detail panel */
.hub-drawer{position:absolute;inset:0;z-index:20;display:flex;justify-content:flex-end;overflow:hidden}
.hub-drawer-overlay{position:absolute;inset:0;background:rgba(15,23,42,.32);backdrop-filter:blur(2px)}
.hub-detail-panel{position:relative;z-index:1;display:flex;width:min(640px,88vw);height:100%;flex-direction:column;background:#fff;box-shadow:-8px 0 30px rgba(0,0,0,.12);overflow:hidden}
.hub-detail-head-bar{display:flex;align-items:center;justify-content:space-between;padding:14px 20px;border-bottom:1px solid #e2e8f0;background:#f8fafc}
.hub-drawer-brand{display:flex;align-items:center;gap:10px}
.hub-drawer-brand h2{margin:0;font-size:16px;letter-spacing:-.02em;color:#0f172a}
.hub-close-btn{display:grid;height:32px;width:32px;place-items:center;border:1px solid #cbd5e1;border-radius:8px;background:#fff;color:#475569;transition:all .15s ease;cursor:pointer}
.hub-close-btn:hover{background:#f1f5f9;color:#0f172a;border-color:#94a3b8}

.hub-origin-banner{display:flex;align-items:center;gap:10px;margin:12px 20px 0;padding:10px 14px;border:1px solid #bae6fd;border-radius:8px;background:#f0f9ff;color:#0369a1}
.hub-origin-info{display:flex;align-items:baseline;gap:6px;font-size:11px}
.hub-origin-label{color:#0284c7;font-weight:600;font-size:10px}
.hub-origin-info strong{color:#0c4a6e;font-size:12px}
.hub-origin-workspace{color:#0284c7;font-size:10px}

.hub-detail-content{flex:1;min-height:0;overflow:auto;padding:16px 20px 24px}
.hub-detail-top{display:flex;align-items:flex-start;justify-content:space-between;gap:14px}
.hub-detail-top>div:first-child{min-width:0}
.hub-detail-top code{color:#64748b;font-size:10px;font-family:var(--font-mono)}
.hub-detail-top p{margin:6px 0 0;color:var(--hub-muted);font-size:11px;line-height:1.6}
.hub-actions{display:flex;flex:none;flex-wrap:wrap;justify-content:flex-end;gap:6px}
.hub-primary,.hub-secondary,.hub-danger{display:inline-flex;align-items:center;justify-content:center;gap:5px;border-radius:6px;padding:6px 12px;font-size:11px;font-weight:700;cursor:pointer;transition:all .15s ease}
.hub-primary{border:1px solid var(--hub-blue);background:var(--hub-blue);color:#fff}.hub-primary:hover{background:#244fc4}
.hub-secondary{border:1px solid #cbd5e1;background:#fff;color:#334155}.hub-secondary:hover{background:#f1f5f9;border-color:#94a3b8}
.hub-danger{border:1px solid #fca5a5;background:#fef2f2;color:#b91c1c}.hub-danger:hover{background:#fee2e2}
.hub-primary:disabled,.hub-secondary:disabled,.hub-danger:disabled{cursor:not-allowed;opacity:.5}

.hub-revision-strip{display:flex;min-height:50px;align-items:center;gap:9px;margin-top:14px;border:1px solid #e2e8f0;border-radius:8px;background:#f8fafc;padding:0 12px;color:#475569}
.hub-revision-strip>div{display:flex;min-width:0;flex-direction:column}
.hub-revision-strip strong{font-size:9px;text-transform:uppercase;color:#64748b}
.hub-revision-strip code{overflow:hidden;text-overflow:ellipsis;font-size:9px;color:#0f172a}
.hub-revision-line{height:1px;width:25px;background:#cbd5e1}
.hub-status{border-radius:4px;padding:3px 6px;font-size:9px;font-weight:700}
.hub-status.update,.hub-status.waiting{margin-left:auto;background:#fffbeb;color:#b45309}
.hub-dependencies{display:flex;align-items:center;gap:5px;margin-top:9px;font-size:9px;color:#64748b}
.hub-dependencies b{border:1px solid #cbd5e1;border-radius:4px;background:#fff;padding:3px 6px;color:#334155;font-weight:600}

.hub-consumers{margin-top:12px;border:1px solid #e2e8f0;border-radius:8px;background:#f8fafc;padding:12px}
.hub-consumers-title{display:flex;align-items:center;gap:8px}
.hub-consumers-title>span,.hub-consumer-icon{display:grid;height:28px;width:28px;place-items:center;border-radius:7px;background:#eff6ff;color:#2563eb}
.hub-consumers-title>div{display:flex;flex-direction:column}
.hub-consumers-title strong{font-size:11px;color:#0f172a}
.hub-consumers-title small{color:#64748b;font-size:9px}
.hub-consumer-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;margin-top:9px}
.hub-consumer-list article{display:grid;min-width:0;grid-template-columns:28px minmax(0,1fr) auto;align-items:center;gap:6px;border:1px solid #e2e8f0;border-radius:7px;background:#fff;padding:7px}
.hub-consumer-list article>div{display:flex;min-width:0;flex-direction:column}
.hub-consumer-list article strong,.hub-consumer-list article small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hub-consumer-list article strong{font-size:10px;color:#0f172a}
.hub-consumer-list article small{color:#64748b;font-size:8px}
.hub-consumer-list em{border-radius:4px;background:#ecfdf5;padding:3px 5px;color:#047857;font-size:8px;font-style:normal;font-weight:700;white-space:nowrap}
.hub-consumer-list em.waiting{background:#fffbeb;color:#b45309}
.hub-consumer-list em.removing,.hub-consumer-list em.conflict{background:#fef2f2;color:#b91c1c}
.hub-consumers>p{margin-top:8px;color:#64748b;font-size:9px}

.hub-files{display:grid;height:340px;min-height:260px;grid-template-columns:150px minmax(0,1fr);margin-top:12px;overflow:hidden;border:1px solid #cbd5e1;border-radius:8px;background:#fff}
.hub-file-tabs{overflow:auto;border-right:1px solid #e2e8f0;background:#f8fafc;padding:5px}
.hub-file-tabs button{display:flex;width:100%;flex-direction:column;border:0;border-radius:5px;background:transparent;padding:6px 8px;text-align:left;font-family:var(--font-mono);font-size:9px;color:#334155;cursor:pointer}
.hub-file-tabs button.is-active{background:#eff6ff;color:#1d4ed8;font-weight:600}
.hub-file-tabs small{margin-top:2px;color:#94a3b8;font-size:8px}
.hub-code{position:relative;min-width:0;min-height:0}.hub-code :deep(.ta-code-editor){height:100%}

.hub-updates{min-height:0;overflow:auto;padding:20px 24px}.hub-updates .hub-section-heading{max-width:960px;margin-right:auto;margin-left:auto}
.hub-update-card{display:grid;grid-template-columns:34px minmax(0,1fr) auto auto;align-items:center;gap:12px;max-width:960px;margin:0 auto 9px;border:1px solid #e2e8f0;border-left:3px solid #3b82f6;border-radius:9px;background:#fff;padding:11px 14px;box-shadow:0 1px 3px rgba(0,0,0,.03)}
.hub-update-main{display:flex;min-width:0;flex-direction:column}
.hub-update-main strong{font-size:13px;color:#0f172a}
.hub-update-main span{color:#64748b;font-size:10px}
.hub-update-main code{margin-top:3px;color:#475569;font-size:9px;font-family:var(--font-mono)}
.hub-update-state{display:inline-flex;align-items:center;gap:5px;border:1px solid #bfdbfe;border-radius:5px;background:#eff6ff;padding:5px 8px;color:#1d4ed8;font-size:9px;font-weight:700;white-space:nowrap}
.hub-update-state.waiting{border-color:#fde68a;background:#fffbeb;color:#b45309}

.hub-loading{display:flex;align-items:center;justify-content:center;gap:7px;padding:28px;color:#64748b;font-size:11px}
.hub-loading.overlay{position:absolute;inset:0;z-index:2;background:rgba(255,255,255,.8)}
.hub-spin{animation:hub-spin 1s linear infinite}@keyframes hub-spin{to{transform:rotate(360deg)}}
.hub-empty{display:flex;min-height:220px;align-items:center;justify-content:center;flex-direction:column;gap:6px;color:#64748b;font-size:10px;text-align:center}
.hub-empty strong{color:#334155;font-size:13px}
.hub-empty.compact{min-height:160px}
.hub-error{position:absolute;top:140px;right:16px;z-index:4;max-width:420px;border:1px solid #fca5a5;border-radius:7px;background:#fef2f2;padding:8px 12px;color:#b91c1c;font-size:10px}

/* Slide animation */
.hub-slide-enter-active,.hub-slide-leave-active{transition:all .25s cubic-bezier(.16,1,.3,1)}
.hub-slide-enter-active .hub-drawer-overlay,.hub-slide-leave-active .hub-drawer-overlay{transition:opacity .25s ease}
.hub-slide-enter-active .hub-detail-panel,.hub-slide-leave-active .hub-detail-panel{transition:transform .25s cubic-bezier(.16,1,.3,1)}
.hub-slide-enter-from .hub-drawer-overlay,.hub-slide-leave-to .hub-drawer-overlay{opacity:0}
.hub-slide-enter-from .hub-detail-panel,.hub-slide-leave-to .hub-detail-panel{transform:translateX(100%)}

.hub-modal-backdrop{position:absolute;inset:0;z-index:30;display:grid;place-items:center;background:rgba(15,23,42,.48);backdrop-filter:blur(3px)}
.hub-modal{width:min(560px,calc(100% - 40px));max-height:76%;overflow:auto;border:1px solid #cbd5e1;border-radius:11px;background:#fff;padding:20px;box-shadow:0 20px 50px rgba(0,0,0,.2)}
.hub-modal.small{width:min(430px,calc(100% - 40px))}
.hub-modal h2{margin:0;font-size:16px;color:#0f172a}
.hub-modal p{margin:6px 0 14px;color:#64748b;font-size:11px;line-height:1.55}
.hub-modal footer{display:flex;justify-content:flex-end;gap:8px;margin-top:18px}
.hub-check-list{max-height:320px;overflow:auto;border:1px solid #e2e8f0;border-radius:8px}
.hub-check-list label{display:flex;align-items:center;gap:9px;border-bottom:1px solid #f1f5f9;padding:10px}
.hub-check-list label:last-child{border-bottom:0}
.hub-check-list span{display:flex;flex-direction:column}
.hub-check-list strong{font-size:12px;color:#0f172a}
.hub-check-list small{color:#64748b;font-size:10px}
.hub-field{display:flex;flex-direction:column;gap:6px}
.hub-field span{font-size:11px;font-weight:600;color:#334155}
.hub-field input{height:34px;border:1px solid #cbd5e1;border-radius:7px;padding:0 10px;font-family:var(--font-mono);font-size:12px;outline:0}
.hub-field input:focus{border-color:#3b82f6;box-shadow:0 0 0 3px rgba(59,130,246,.12)}
.hub-conflict{position:absolute;inset:0;z-index:40}
.hub-binary-conflict{display:grid;place-items:center;background:rgba(248,250,252,.96)}
.hub-binary-conflict>section{width:min(520px,calc(100% - 40px));border:1px solid #cbd5e1;border-radius:12px;background:#fff;padding:20px;box-shadow:0 20px 50px rgba(0,0,0,.15)}
.hub-binary-conflict h2{margin:0 0 8px;font-size:16px}
.hub-binary-conflict code{font-size:10px}
.hub-binary-conflict p{color:#64748b;font-size:11px;line-height:1.6}
.hub-binary-conflict section>div{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}

@media (max-width:760px){.hub-header-main{flex-direction:column;align-items:stretch}.hub-header-actions{flex-direction:column;align-items:stretch}.hub-overview{flex-wrap:wrap}.hub-tabs{overflow:auto;padding:0 12px}.hub-tabs button{flex:none}.hub-catalog-head{align-items:stretch;flex-direction:column}.hub-search{width:100%}.hub-detail-panel{width:100%}}
</style>
