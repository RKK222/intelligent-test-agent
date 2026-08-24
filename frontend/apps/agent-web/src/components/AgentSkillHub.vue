<script setup lang="ts">
import { computed, inject, onMounted, onUnmounted, ref, watch, type CSSProperties } from "vue";
import { ElMessage } from "element-plus";
import { CodeEditor } from "@test-agent/editor";
import { MergeConflictEditor } from "@test-agent/diff-viewer";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  AgentSkillHubAsset,
  AgentSkillHubAssetDetail,
  AgentSkillHubAssetType,
  AgentSkillHubConflictFile,
  AgentSkillHubSkillCategory,
  AgentSkillHubSourceKind,
  AgentSkillHubSkillSubcategory,
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
  Maximize2,
  Minimize2,
  PackageOpen,
  PlugZap,
  RefreshCw,
  Search,
  Sparkles,
  UploadCloud,
  UserRound,
  UsersRound,
  Wrench,
  X
} from "lucide-vue-next";

type RuntimeHubItem = {
  id: string;
  name: string;
  description?: string;
  status?: string;
};

const props = defineProps<{
  selectedAppId?: string;
  workspaceId?: string;
  canManage: boolean;
  pageActive: boolean;
  canClassifySkills?: boolean;
  runtimeMcp?: RuntimeHubItem[];
  runtimeTools?: RuntimeHubItem[];
}>();

const emit = defineEmits<{
  updateCount: [count: number];
  changed: [paths: string[]];
  refreshRuntime: [];
}>();

const api = inject<BackendApiClient>("api")!;
if (!api) throw new Error("AgentSkillHub requires backend api");

type HubTab = "DISCOVER" | "AGENT" | "SKILL" | "MCP" | "TOOL" | "REFERENCED" | "UPDATES";
type SkillCategoryFilter = AgentSkillHubSkillCategory | "ALL";
type SkillSubcategoryFilter = AgentSkillHubSkillSubcategory | "ALL";
type SkillSourceFilter = AgentSkillHubSourceKind | "ALL";

const SKILL_CATEGORIES: Array<{ value: SkillCategoryFilter; label: string; hint: string }> = [
  { value: "ALL", label: "全部", hint: "全部 Skill" },
  { value: "WORKER", label: "日常工作", hint: "Worker" },
  { value: "TEST", label: "测试", hint: "Test" },
  { value: "CODE", label: "代码", hint: "Code" },
  { value: "OTHER", label: "其他", hint: "待分类" }
];
const TEST_SUBCATEGORIES: Array<{ value: AgentSkillHubSkillSubcategory; label: string }> = [
  { value: "TEST_DESIGN", label: "测试设计" },
  { value: "TEST_DATA_CONSTRUCTION", label: "测试数据构造" },
  { value: "TEST_EXECUTION", label: "测试执行" },
  { value: "TEST_ANALYSIS", label: "测试分析" }
];
const CODE_SUBCATEGORIES: Array<{ value: AgentSkillHubSkillSubcategory; label: string }> = [
  { value: "WHITE_BOX_ANALYSIS", label: "白盒分析" }
];

const tab = ref<HubTab>("DISCOVER");
const skillCategory = ref<SkillCategoryFilter>("ALL");
const skillSubcategory = ref<SkillSubcategoryFilter>("ALL");
const skillSource = ref<SkillSourceFilter>("ALL");
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
const selectedRuntimeItem = ref<RuntimeHubItem | null>(null);
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
const detailPanelWidth = ref(640);
const detailFullscreen = ref(false);
const detailResizing = ref(false);
const classificationCategory = ref<AgentSkillHubSkillCategory>("OTHER");
const classificationSubcategory = ref<AgentSkillHubSkillSubcategory | null>(null);

const visibleSkillSubcategories = computed(() => {
  if (skillCategory.value === "TEST") return TEST_SUBCATEGORIES;
  if (skillCategory.value === "CODE") return CODE_SUBCATEGORIES;
  return [];
});
const classificationSubcategories = computed(() => {
  if (classificationCategory.value === "TEST") return TEST_SUBCATEGORIES;
  if (classificationCategory.value === "CODE") return CODE_SUBCATEGORIES;
  return [];
});

const runtimeItems = computed(() => {
  const source = tab.value === "MCP" ? props.runtimeMcp ?? [] : tab.value === "TOOL" ? props.runtimeTools ?? [] : [];
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  if (!normalizedKeyword) return source;
  return source.filter((item) => [item.id, item.name, item.description, item.status]
    .some((value) => value?.toLowerCase().includes(normalizedKeyword)));
});
const isRuntimeTab = computed(() => tab.value === "MCP" || tab.value === "TOOL");
const detailPanelStyle = computed<CSSProperties>(() => detailFullscreen.value
  ? { width: "100vw", height: "100vh" }
  : { width: `${detailPanelWidth.value}px` });

const DETAIL_MIN_WIDTH = 420;
const DETAIL_VIEWPORT_MARGIN = 16;

/** Hub 详情固定在右侧，普通模式宽度始终限制在当前视口内。 */
function clampDetailPanelWidth() {
  const maxWidth = Math.max(320, window.innerWidth - DETAIL_VIEWPORT_MARGIN * 2);
  const minWidth = Math.min(DETAIL_MIN_WIDTH, maxWidth);
  detailPanelWidth.value = Math.min(maxWidth, Math.max(minWidth, detailPanelWidth.value));
}

type DetailResizeSnapshot = {
  pointerId: number;
  startX: number;
  width: number;
  bodyCursor: string;
  bodyUserSelect: string;
};

let detailResizeSnapshot: DetailResizeSnapshot | null = null;

/** 面板从右侧展开，因此向左拖动左边缘代表放大。 */
function startDetailResize(event: PointerEvent) {
  if (detailFullscreen.value) return;
  (event.currentTarget as HTMLElement | null)?.focus();
  detailResizeSnapshot = {
    pointerId: event.pointerId,
    startX: event.clientX,
    width: detailPanelWidth.value,
    bodyCursor: document.body.style.cursor,
    bodyUserSelect: document.body.style.userSelect
  };
  detailResizing.value = true;
  document.body.style.cursor = "ew-resize";
  document.body.style.userSelect = "none";
  window.addEventListener("pointermove", resizeDetailPanel);
  window.addEventListener("pointerup", stopDetailResize);
  window.addEventListener("pointercancel", stopDetailResize);
}

function resizeDetailPanel(event: PointerEvent) {
  if (!detailResizeSnapshot || event.pointerId !== detailResizeSnapshot.pointerId) return;
  detailPanelWidth.value = detailResizeSnapshot.width + detailResizeSnapshot.startX - event.clientX;
  clampDetailPanelWidth();
}

function stopDetailResize(event?: PointerEvent) {
  if (event && detailResizeSnapshot && event.pointerId !== detailResizeSnapshot.pointerId) return;
  if (detailResizeSnapshot) {
    document.body.style.cursor = detailResizeSnapshot.bodyCursor;
    document.body.style.userSelect = detailResizeSnapshot.bodyUserSelect;
  }
  detailResizeSnapshot = null;
  detailResizing.value = false;
  window.removeEventListener("pointermove", resizeDetailPanel);
  window.removeEventListener("pointerup", stopDetailResize);
  window.removeEventListener("pointercancel", stopDetailResize);
}

/** 键盘在左边缘调整宽度，Shift 把步长从 16px 提升到 48px。 */
function resizeDetailByKeyboard(event: KeyboardEvent) {
  const step = event.shiftKey ? 48 : 16;
  if (event.key === "ArrowLeft") detailPanelWidth.value += step;
  else if (event.key === "ArrowRight") detailPanelWidth.value -= step;
  else return;
  event.preventDefault();
  clampDetailPanelWidth();
}

function toggleDetailFullscreen() {
  stopDetailResize();
  detailFullscreen.value = !detailFullscreen.value;
  if (!detailFullscreen.value) clampDetailPanelWidth();
}

const activeConflict = computed(() => {
  const file = updateOperation.value?.files.find((item) => item.path === activeConflictPath.value);
  return file?.kind === "TEXT" ? toWorkspaceConflict(file) : null;
});
const activeBinaryConflict = computed(() =>
  updateOperation.value?.files.find((item) => item.path === activeConflictPath.value && item.kind === "BINARY") ?? null
);

const canPublishSelected = computed(() =>
  props.canManage && !selectedAsset.value?.builtin
    && selectedAsset.value?.sourceKind !== "SKILLHUB"
    && selectedAsset.value?.sourceAppId === props.selectedAppId && !selectedAsset.value?.deleted
    && (!selectedAsset.value?.published || selectedAsset.value?.updateAvailable)
);
const canReferenceSelected = computed(() =>
  props.canManage && Boolean(props.workspaceId) && !selectedAsset.value?.builtin
    && selectedAsset.value?.sourceAvailable !== false && !selectedAsset.value?.deleted
    && (selectedAsset.value?.published || selectedAsset.value?.sourceKind === "SKILLHUB")
    && (!selectedAsset.value?.referenceStatus || selectedAsset.value.referenceStatus === "PENDING_REMOVE")
);
const canRemoveSelected = computed(() =>
  props.canManage && Boolean(props.workspaceId) && !selectedAsset.value?.builtin
    && Boolean(selectedAsset.value?.referenceStatus)
    && selectedAsset.value?.referenceStatus !== "PENDING_REMOVE"
);

function selectedAssetType(value: HubTab = tab.value): AgentSkillHubAssetType | undefined {
  if (value === "AGENT" || value === "SKILL") return value;
  return undefined;
}

let assetRequestVersion = 0;

async function loadAssets() {
  const requestVersion = ++assetRequestVersion;
  const requestedTab = tab.value;
  const requestedWorkspaceId = props.workspaceId;
  const requestedKeyword = keyword.value;
  loading.value = true;
  error.value = "";
  try {
    if (requestedTab === "MCP" || requestedTab === "TOOL") {
      assets.value = [];
      closeDetail();
      emit("refreshRuntime");
      return;
    }
    if (requestedTab === "REFERENCED" && !requestedWorkspaceId) {
      assets.value = [];
      selectedAsset.value = null;
      clearDetail();
      return;
    }
    const page = await api.listAgentSkillHubAssets({
      type: selectedAssetType(requestedTab),
      source: requestedTab === "SKILL" ? skillSource.value : requestedTab === "AGENT" ? "PLATFORM" : "ALL",
      category: requestedTab === "SKILL" && skillCategory.value !== "ALL" ? skillCategory.value : undefined,
      subcategory: requestedTab === "SKILL" && skillSubcategory.value !== "ALL" ? skillSubcategory.value : undefined,
      keyword: requestedKeyword || undefined,
      referencedOnly: requestedTab === "REFERENCED",
      targetWorkspaceId: requestedWorkspaceId,
      page: 1,
      size: 100
    });
    // 切换分类、工作区或搜索条件后，迟到的旧请求不得覆盖当前列表。
    if (requestVersion !== assetRequestVersion) return;
    // 未选个人工作区时没有可判定的目标引用上下文，避免把其它应用的引用误显示为当前引用。
    assets.value = requestedWorkspaceId
      ? page.items
      : page.items.map((item) => ({ ...item, referenced: false, referenceStatus: null }));
    if (selectedAsset.value && assets.value.some((item) => item.assetId === selectedAsset.value?.assetId)) {
      await selectAsset(selectedAsset.value);
    } else {
      closeDetail();
    }
  } catch (cause) {
    if (requestVersion === assetRequestVersion) error.value = message(cause);
  } finally {
    if (requestVersion === assetRequestVersion) loading.value = false;
  }
}

/** Hero 概览使用轻量分页查询，只读取总数，不拉取额外制品正文。 */
async function refreshOverview() {
  try {
    const [agents, skills, referenced] = await Promise.all([
      api.listAgentSkillHubAssets({ type: "AGENT", page: 1, size: 1 }),
      api.listAgentSkillHubAssets({ type: "SKILL", source: "ALL", page: 1, size: 1 }),
      props.workspaceId
        ? api.listAgentSkillHubAssets({ source: "ALL", referencedOnly: true, targetWorkspaceId: props.workspaceId, page: 1, size: 1 })
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

function refreshCurrentTab() {
  if (tab.value === "UPDATES") {
    void loadUpdates();
    return;
  }
  if (isRuntimeTab.value) {
    emit("refreshRuntime");
    return;
  }
  void loadAssets();
}

async function selectAsset(asset: AgentSkillHubAsset) {
  selectedRuntimeItem.value = null;
  selectedAsset.value = asset;
  clampDetailPanelWidth();
  detail.value = await api.getAgentSkillHubAsset(asset.assetId, undefined, props.workspaceId);
  classificationCategory.value = detail.value.asset.category ?? "OTHER";
  classificationSubcategory.value = detail.value.asset.subcategory ?? null;
  selectedFile.value = detail.value.files[0]?.path ?? null;
  if (selectedFile.value) await readFile(selectedFile.value);
  else fileContent.value = "";
}

/** 一级分类切换会清空二级筛选，避免把上一分类的具体事项带入新请求。 */
function selectSkillCategory(category: SkillCategoryFilter) {
  skillCategory.value = category;
  skillSubcategory.value = "ALL";
  void loadAssets();
}

function selectSkillSubcategory(subcategory: SkillSubcategoryFilter) {
  skillSubcategory.value = subcategory;
  void loadAssets();
}

function selectSkillSource(source: SkillSourceFilter) {
  skillSource.value = source;
  void loadAssets();
}

function skillCategoryLabel(asset: AgentSkillHubAsset) {
  return SKILL_CATEGORIES.find((item) => item.value === (asset.category ?? "OTHER"))?.label ?? "其他";
}

function skillSubcategoryLabel(asset: AgentSkillHubAsset) {
  const value = asset.subcategory;
  if (!value) return null;
  return [...TEST_SUBCATEGORIES, ...CODE_SUBCATEGORIES].find((item) => item.value === value)?.label ?? value;
}

/** 超级管理员分类后直接更新当前快照，再按现有筛选重新拉取目录。 */
async function saveSkillClassification() {
  if (!props.canClassifySkills || selectedAsset.value?.type !== "SKILL") return;
  actionLoading.value = true;
  try {
    const result = await api.updateAgentSkillHubClassification(
      selectedAsset.value.assetId,
      classificationCategory.value,
      classificationSubcategory.value
    );
    const updated = {
      ...selectedAsset.value,
      category: result.category,
      subcategory: result.subcategory ?? null
    };
    selectedAsset.value = updated;
    if (detail.value) detail.value = { ...detail.value, asset: updated };
    assets.value = assets.value.map((item) => item.assetId === updated.assetId ? updated : item);
    ElMessage.success("Skill 事项分类已更新");
    await loadAssets();
  } catch (cause) {
    ElMessage.error(message(cause));
  } finally {
    actionLoading.value = false;
  }
}

function selectRuntimeItem(item: RuntimeHubItem) {
  selectedAsset.value = null;
  clearDetail();
  selectedRuntimeItem.value = item;
  clampDetailPanelWidth();
}

async function readFile(path: string) {
  if (!detail.value?.selectedRevisionId) return;
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

async function materializeSelected() {
  if (!selectedAsset.value || selectedAsset.value.sourceKind !== "SKILLHUB") return;
  actionLoading.value = true;
  try {
    detail.value = await api.materializeAgentSkillHubAsset(selectedAsset.value.assetId, props.workspaceId);
    selectedAsset.value = detail.value.asset;
    assets.value = assets.value.map((item) => item.assetId === detail.value?.asset.assetId ? detail.value.asset : item);
    selectedFile.value = detail.value.files[0]?.path ?? null;
    if (selectedFile.value) await readFile(selectedFile.value);
  } catch (cause) {
    ElMessage.error(message(cause));
  } finally {
    actionLoading.value = false;
  }
}

function clearDetail() {
  detail.value = null;
  selectedFile.value = null;
  fileContent.value = "";
}

function closeDetail() {
  stopDetailResize();
  selectedAsset.value = null;
  selectedRuntimeItem.value = null;
  detailFullscreen.value = false;
  clearDetail();
}

async function openPublish() {
  if (!props.canManage || !selectedAsset.value) return;
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
  if (!props.canManage || !selectedAsset.value) return;
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
  if (!props.canManage || !selectedAsset.value) return;
  aliasTechnicalId.value = selectedAsset.value.technicalId;
  referenceDialog.value = true;
}

async function createReference() {
  if (!props.canManage || !selectedAsset.value || !props.workspaceId) return;
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
  if (!props.canManage || !selectedAsset.value || !props.workspaceId) return;
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
  if (!props.canManage) return;
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
  if (!props.canManage || !props.workspaceId || !updateOperation.value || !activeConflictPath.value) return;
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
  if (!props.canManage || !props.workspaceId || !updateOperation.value) return;
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

/** SkillHub 文档 contributor 是创建人用户 ID，清理现场数据中可能携带的换行。 */
function skillCreator(asset: AgentSkillHubAsset) {
  return asset.externalContributor?.trim() || "未提供";
}

/** 列表状态必须同时给出文本与图标，不能只依赖颜色表达。 */
function assetStatus(asset: AgentSkillHubAsset) {
  if (asset.sourceAvailable === false) return { key: "deleted", label: "来源已下架", title: "来源目录已删除，当前应用仍可移除已有引用" };
  if (asset.deleted) return { key: "deleted", label: "远端已删除", title: "该资产已从来源远端删除" };
  if (asset.builtin) return { key: "builtin", label: "平台内置", title: "平台内置、无需发布或引用" };
  if (asset.sourceKind === "SKILLHUB" && !asset.contentAvailable) {
    return { key: "pushed", label: "外部 · 未下载", title: "仅同步目录元数据，预览或引用时才下载正文" };
  }
  if (asset.sourceKind === "SKILLHUB") return { key: "published", label: "SkillHub", title: "外部 SkillHub 当前可用" };
  if (tab.value === "REFERENCED") {
    if (asset.referenceStatus === "PENDING_PUSH") {
      return { key: "waiting", label: "引用待推送", title: "已写入当前 worktree，尚未 push 到远端" };
    }
    if (asset.referenceStatus === "UPDATE_CONFLICT") {
      return { key: "conflict", label: "更新有冲突", title: "引用更新存在待解决冲突" };
    }
    if (asset.referenceStatus === "ACTIVE" && asset.updateAvailable) {
      return { key: "update", label: "已引用 · 新版待发布", title: "当前引用已生效，来源另有尚未发布的新 push" };
    }
    if (asset.referenceStatus === "ACTIVE" && asset.referenced) {
      return { key: "referenced", label: "引用生效", title: "该引用已由当前应用成功 push 到远端" };
    }
  }
  if (asset.updateAvailable) return { key: "update", label: "新版待发布", title: "最新 push 内容尚未发布" };
  if (asset.published) return { key: "published", label: "已发布", title: "其他应用可以引用" };
  return { key: "pushed", label: "仅已推送", title: "已形成不可变快照，尚未发布" };
}

function consumerStatus(status: string) {
  if (status === "PENDING_PUSH") return { key: "waiting", label: "引用待推送" };
  if (status === "PENDING_REMOVE") return { key: "removing", label: "取消待推送" };
  if (status === "UPDATE_CONFLICT") return { key: "conflict", label: "更新有冲突" };
  return { key: "referenced", label: "引用生效" };
}

watch(classificationCategory, (category) => {
  if (category === "TEST") {
    if (!TEST_SUBCATEGORIES.some((item) => item.value === classificationSubcategory.value)) {
      classificationSubcategory.value = "TEST_DESIGN";
    }
    return;
  }
  if (category === "CODE") {
    classificationSubcategory.value = "WHITE_BOX_ANALYSIS";
    return;
  }
  classificationSubcategory.value = null;
});

watch(tab, async (value) => {
  if (value === "UPDATES") {
    assetRequestVersion++;
    await loadUpdates();
  }
  else await loadAssets();
});

watch([() => props.runtimeMcp, () => props.runtimeTools], () => {
  if (!selectedRuntimeItem.value || !isRuntimeTab.value) return;
  selectedRuntimeItem.value = runtimeItems.value.find((item) => item.id === selectedRuntimeItem.value?.id) ?? null;
}, { deep: true });

watch(() => props.workspaceId, async () => {
  if (tab.value === "UPDATES") await loadUpdates();
  else await Promise.all([loadAssets(), refreshCount(), refreshOverview()]);
});

// 权限可能在弹窗打开期间被撤销；立即收敛 mutation 状态，避免保留旧确认入口。
watch(() => props.canManage, (canManage) => {
  if (canManage) return;
  publishDialog.value = false;
  referenceDialog.value = false;
  removeDialog.value = false;
  updateOperation.value = null;
  activeConflictPath.value = null;
});

let searchTimer: ReturnType<typeof setTimeout> | null = null;
let searchRefreshPending = false;
watch(keyword, () => {
  if (searchTimer) {
    clearTimeout(searchTimer);
    searchTimer = null;
  }
  searchRefreshPending = true;
  if (!props.pageActive) return;
  searchTimer = setTimeout(() => {
    searchTimer = null;
    searchRefreshPending = false;
    if (tab.value !== "UPDATES" && !isRuntimeTab.value) void loadAssets();
  }, 250);
});

watch(() => props.pageActive, (active) => {
  if (!active) {
    if (searchTimer) {
      clearTimeout(searchTimer);
      searchTimer = null;
      searchRefreshPending = true;
    }
    return;
  }
  if (!searchRefreshPending || tab.value === "UPDATES" || isRuntimeTab.value) return;
  searchRefreshPending = false;
  void loadAssets();
});

onMounted(async () => {
  if (props.pageActive) await Promise.all([loadAssets(), refreshCount(), refreshOverview()]);
});

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer);
  stopDetailResize();
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
              <h1>Agent · Skill · MCP · Tool Hub</h1>
              <span class="hub-kicker-tag">共享能力中心</span>
            </div>
            <p>发现和复用 Agent / Skill，并盘点当前运行态的 MCP / Tool。</p>
          </div>
        </div>
        <div class="hub-header-actions">
          <div class="hub-overview" aria-label="Hub 概览">
            <div class="hub-overview-item"><Bot :size="13" /><span><b>{{ agentTotal }}</b> Agent</span></div>
            <div class="hub-overview-item"><Sparkles :size="13" /><span><b>{{ skillTotal }}</b> Skill</span></div>
            <div class="hub-overview-item"><PlugZap :size="13" /><span><b>{{ runtimeMcp?.length ?? 0 }}</b> MCP</span></div>
            <div class="hub-overview-item"><Wrench :size="13" /><span><b>{{ runtimeTools?.length ?? 0 }}</b> Tool</span></div>
          </div>
          <button class="hub-refresh-btn" type="button" @click="refreshCurrentTab">
            <RefreshCw :size="13" :class="loading && 'hub-spin'" />刷新目录
          </button>
        </div>
      </div>
    </header>

    <nav class="hub-tabs" aria-label="Hub 分类">
      <button :class="tab === 'DISCOVER' && 'is-active'" @click="tab = 'DISCOVER'"><Compass :size="14" />发现</button>
      <button :class="tab === 'AGENT' && 'is-active'" @click="tab = 'AGENT'"><Bot :size="14" />Agent</button>
      <button :class="tab === 'SKILL' && 'is-active'" @click="tab = 'SKILL'"><Sparkles :size="14" />Skill</button>
      <button :class="tab === 'MCP' && 'is-active'" @click="tab = 'MCP'"><PlugZap :size="14" />MCP</button>
      <button :class="tab === 'TOOL' && 'is-active'" @click="tab = 'TOOL'"><Wrench :size="14" />Tool</button>
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
          <code>{{ update.activeRevisionId?.slice(-8) || '未推送' }} → {{ update.latestVersion || update.latestRevisionId.slice(-8) }}</code>
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
            <span>{{ tab === 'REFERENCED' ? 'CURRENT APPLICATION' : isRuntimeTab ? 'RUNTIME CATALOG' : 'CAPABILITY CATALOG' }}</span>
            <h2>{{ tab === 'REFERENCED' ? '当前应用的能力' : tab === 'AGENT' ? 'Agent 目录' : tab === 'SKILL' ? 'Skill 目录' : tab === 'MCP' ? 'MCP 目录' : tab === 'TOOL' ? 'Tool 目录' : '探索全部能力' }}</h2>
            <p>{{ tab === 'REFERENCED' ? '展示当前应用已生效、待推送和更新冲突的引用。' : isRuntimeTab ? '与顶栏运行态资源盘点使用同一份已加载目录；仅展示当前工作区真实可用内容。' : '展示远端能力、原创应用和归属工作区；每个版本均为成功 push 的不可变快照。' }}</p>
          </div>
          <label class="hub-search"><Search :size="14" /><input v-model="keyword" placeholder="搜索名称、应用或技术 ID" /></label>
        </div>
        <div v-if="tab === 'SKILL'" class="hub-taxonomy" aria-label="Skill 事项分类">
          <div class="hub-taxonomy-row">
            <span>能力来源</span>
            <button type="button" :class="skillSource === 'ALL' && 'is-active'" @click="selectSkillSource('ALL')">全部</button>
            <button type="button" :class="skillSource === 'SKILLHUB' && 'is-active'" @click="selectSkillSource('SKILLHUB')">接口文档</button>
            <button type="button" :class="skillSource === 'PLATFORM' && 'is-active'" @click="selectSkillSource('PLATFORM')">平台更新</button>
          </div>
          <div class="hub-taxonomy-row">
            <span>事项分类</span>
            <button
              v-for="category in SKILL_CATEGORIES"
              :key="category.value"
              type="button"
              :class="skillCategory === category.value && 'is-active'"
              :title="category.hint"
              @click="selectSkillCategory(category.value)"
            >{{ category.label }}</button>
          </div>
          <div v-if="visibleSkillSubcategories.length" class="hub-taxonomy-row is-secondary">
            <span>具体事项</span>
            <button
              type="button"
              :class="skillSubcategory === 'ALL' && 'is-active'"
              @click="selectSkillSubcategory('ALL')"
            >全部</button>
            <button
              v-for="subcategory in visibleSkillSubcategories"
              :key="subcategory.value"
              type="button"
              :class="skillSubcategory === subcategory.value && 'is-active'"
              @click="selectSkillSubcategory(subcategory.value)"
            >{{ subcategory.label }}</button>
          </div>
        </div>
        <div v-if="loading" class="hub-loading"><Loader2 class="hub-spin" :size="18" />{{ isRuntimeTab ? '正在同步运行态目录' : '正在读取远端快照' }}</div>
        <div v-else-if="isRuntimeTab && runtimeItems.length" class="hub-card-grid">
          <button
            v-for="item in runtimeItems"
            :key="item.id"
            :class="['hub-asset-card', selectedRuntimeItem?.id === item.id && 'is-active']"
            @click="selectRuntimeItem(item)"
          >
            <span class="hub-card-top">
              <span class="hub-asset-avatar" :data-type="tab"><PlugZap v-if="tab === 'MCP'" :size="18" /><Wrench v-else :size="18" /></span>
              <span class="hub-card-type">{{ tab }}</span>
              <span class="hub-asset-status builtin"><CheckCircle2 :size="11" />当前已加载</span>
            </span>
            <strong>{{ item.name }}</strong>
            <code>{{ item.id }}</code>
            <p>{{ item.description || (tab === 'MCP' ? '该 MCP 当前未提供说明。' : '该 Tool 当前未提供说明。') }}</p>
            <span class="hub-card-meta">
              <span class="hub-card-origin"><Building2 :size="12" />当前工作区运行态</span>
              <span v-if="item.status"><CheckCircle2 :size="12" />{{ item.status }}</span>
            </span>
          </button>
        </div>
        <div v-else-if="assets.length" class="hub-card-grid">
          <button
            v-for="asset in assets"
            :key="asset.assetId"
            :class="['hub-asset-card', selectedAsset?.assetId === asset.assetId && 'is-active']"
            @click="selectAsset(asset)"
          >
            <span class="hub-card-top">
              <span class="hub-asset-avatar" :data-type="asset.type"><Bot v-if="asset.type === 'AGENT'" :size="18" /><Sparkles v-else :size="18" /></span>
              <span class="hub-card-type">{{ asset.type }} · {{ asset.sourceKind === 'SKILLHUB' ? '接口文档' : '平台更新' }}</span>
              <span :class="['hub-asset-status', assetStatus(asset).key]" :title="assetStatus(asset).title">
                <CheckCircle2 v-if="['builtin', 'published', 'referenced'].includes(assetStatus(asset).key)" :size="11" />
                <RefreshCw v-else-if="assetStatus(asset).key === 'update'" :size="11" />
                <UploadCloud v-else-if="assetStatus(asset).key === 'waiting'" :size="11" />
                <Clock3 v-else :size="11" />{{ assetStatus(asset).label }}
              </span>
            </span>
            <strong>{{ asset.displayName || asset.technicalId }}</strong>
            <code>{{ asset.technicalId }}</code>
            <span v-if="asset.type === 'SKILL'" class="hub-card-taxonomy">
              {{ skillCategoryLabel(asset) }}<template v-if="skillSubcategoryLabel(asset)"> · {{ skillSubcategoryLabel(asset) }}</template>
            </span>
            <span v-if="asset.type === 'SKILL' && asset.sourceKind === 'SKILLHUB'" class="hub-card-contributor">
              <UserRound :size="11" />创建人：{{ skillCreator(asset) }}
            </span>
            <p>{{ asset.description || '该能力暂未提供说明。' }}</p>
            <span class="hub-card-meta">
              <span class="hub-card-origin"><Building2 :size="12" />来源：<b>{{ asset.builtin ? '平台内置' : asset.sourceAppName }}</b></span>
              <span v-if="tab === 'REFERENCED'" :aria-label="`${asset.referenceCount} 个应用引用`"><UsersRound :size="12" />{{ asset.referenceCount }} 个引用</span>
              <span v-else><Library :size="12" />归属工作区：{{ asset.builtin ? '平台' : asset.sourceWorkspaceName }}</span>
            </span>
          </button>
        </div>
        <div v-else class="hub-empty compact">
          <Library v-if="tab === 'REFERENCED'" :size="28" /><PackageOpen v-else :size="28" />
          <strong>{{ isRuntimeTab ? `当前没有可用的 ${tab}` : tab === 'REFERENCED' ? (workspaceId ? '当前应用还没有引用能力' : '请先选择个人工作区') : '没有匹配的远端快照' }}</strong>
          <span>{{ isRuntimeTab ? '请确认当前工作区运行态已经就绪，或刷新目录后重试。' : tab === 'REFERENCED' ? '从发现、Agent 或 Skill 目录中选择已发布能力即可引用。' : '可调整搜索条件后重试。' }}</span>
        </div>
      </main>

      <Teleport to="body" :disabled="!detailFullscreen">
        <Transition name="hub-slide">
        <aside
          v-if="selectedAsset || selectedRuntimeItem"
          :class="['hub-drawer', detailFullscreen && 'is-fullscreen']"
          :data-layout-mode="detailFullscreen ? 'fullscreen' : 'window'"
          data-testid="hub-detail-drawer"
        >
          <div class="hub-drawer-overlay" @click="closeDetail" />
          <div :class="['hub-detail-panel', detailResizing && 'is-resizing']" :style="detailPanelStyle">
            <button
              v-if="!detailFullscreen"
              type="button"
              class="hub-detail-resize-handle"
              :class="{ 'is-resizing': detailResizing }"
              aria-label="调整 Hub 详情宽度"
              title="向左拖动展开；聚焦后可使用左右方向键"
              @pointerdown.prevent="startDetailResize"
              @keydown="resizeDetailByKeyboard"
            />
            <div class="hub-detail-head-bar">
              <div class="hub-drawer-brand">
                <span class="hub-asset-avatar" :data-type="selectedAsset?.type || tab">
                  <Bot v-if="selectedAsset?.type === 'AGENT'" :size="17" />
                  <Sparkles v-else-if="selectedAsset?.type === 'SKILL'" :size="17" />
                  <PlugZap v-else-if="tab === 'MCP'" :size="17" />
                  <Wrench v-else :size="17" />
                </span>
                <div>
                  <span class="hub-card-type">{{ selectedAsset?.type || tab }}</span>
                  <h2>{{ selectedAsset ? (selectedAsset.displayName || selectedAsset.technicalId) : selectedRuntimeItem?.name }}</h2>
                </div>
              </div>
              <div class="hub-detail-head-actions">
                <button
                  class="hub-close-btn"
                  type="button"
                  :title="detailFullscreen ? '退出全屏' : '进入全屏'"
                  :aria-label="detailFullscreen ? '退出全屏' : '进入全屏'"
                  @click="toggleDetailFullscreen"
                >
                  <Minimize2 v-if="detailFullscreen" :size="17" />
                  <Maximize2 v-else :size="17" />
                </button>
                <button class="hub-close-btn" type="button" title="关闭详情" aria-label="关闭 Hub 详情" @click="closeDetail">
                  <X :size="18" />
                </button>
              </div>
            </div>

            <div v-if="selectedAsset" class="hub-origin-banner">
              <Building2 :size="15" />
              <div class="hub-origin-info">
                <span class="hub-origin-label">能力来源</span>
                <strong>{{ selectedAsset.builtin ? '平台内置' : selectedAsset.sourceAppName }}</strong>
                <span v-if="!selectedAsset.builtin" class="hub-origin-workspace">（工作区：{{ selectedAsset.sourceWorkspaceName }}）</span>
              </div>
            </div>

            <div v-if="selectedAsset && detail" class="hub-detail-content">
              <div class="hub-detail-top">
                <div>
                  <code>{{ selectedAsset.technicalId }}</code>
                  <p>{{ selectedAsset.description || '该资产未提供说明。可先阅读完整内容，再决定是否发布或引用。' }}</p>
                </div>
                <div class="hub-actions">
                  <button
                    v-if="selectedAsset.sourceKind === 'SKILLHUB' && !selectedAsset.contentAvailable && selectedAsset.sourceAvailable !== false"
                    class="hub-secondary"
                    :disabled="actionLoading"
                    @click="materializeSelected"
                  ><PackageOpen :size="14" />预览内容</button>
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

              <section v-if="selectedAsset.sourceKind === 'SKILLHUB'" class="hub-classification">
                <div>
                  <strong>SkillHub 目录元数据</strong>
                  <span>版本 {{ selectedAsset.externalVersion || '-' }} · {{ selectedAsset.externalPhaseName || selectedAsset.externalPhase || '未标注阶段' }} · 创建人：{{ skillCreator(selectedAsset) }}</span>
                </div>
                <b class="hub-classification-value">{{ selectedAsset.externalTag || selectedAsset.externalSource || '外部能力' }}</b>
              </section>

              <section v-if="selectedAsset.type === 'SKILL'" class="hub-classification">
                <div>
                  <strong>事项分类</strong>
                  <span>新入库 Skill 默认进入“其他”，由超级管理员归入受控事项。</span>
                </div>
                <div v-if="canClassifySkills" class="hub-classification-form">
                  <label>
                    一级分类
                    <select v-model="classificationCategory" aria-label="Skill 一级分类">
                      <option value="WORKER">日常工作（Worker）</option>
                      <option value="TEST">测试（Test）</option>
                      <option value="CODE">代码（Code）</option>
                      <option value="OTHER">其他</option>
                    </select>
                  </label>
                  <label v-if="classificationSubcategories.length">
                    具体事项
                    <select v-model="classificationSubcategory" aria-label="Skill 具体事项">
                      <option
                        v-for="subcategory in classificationSubcategories"
                        :key="subcategory.value"
                        :value="subcategory.value"
                      >{{ subcategory.label }}</option>
                    </select>
                  </label>
                  <button class="hub-secondary" type="button" :disabled="actionLoading" @click="saveSkillClassification">
                    保存分类
                  </button>
                </div>
                <b v-else class="hub-classification-value">
                  {{ skillCategoryLabel(selectedAsset) }}<template v-if="skillSubcategoryLabel(selectedAsset)"> · {{ skillSubcategoryLabel(selectedAsset) }}</template>
                </b>
              </section>

              <div class="hub-revision-strip">
                <GitCommitHorizontal :size="17" />
                <div><strong>{{ selectedAsset.sourceKind === 'SKILLHUB' ? 'SkillHub 版本' : selectedAsset.builtin ? '平台公共提交' : '最新 push' }}</strong><code>{{ selectedAsset.externalVersion || selectedAsset.pushedRevisionId?.slice(-12) || '正文未下载' }}</code></div>
                <span class="hub-revision-line" />
                <div><strong>{{ selectedAsset.sourceKind === 'SKILLHUB' ? (selectedAsset.contentAvailable ? '正文已缓存' : '按需下载') : selectedAsset.builtin ? '平台内置' : selectedAsset.published ? '已发布' : '等待发布' }}</strong><code>{{ selectedAsset.sourceKind === 'SKILLHUB' ? (selectedAsset.contentAvailable ? selectedAsset.publishedRevisionId?.slice(-12) : '预览或引用时下载') : selectedAsset.builtin ? '无需引用' : selectedAsset.publishedRevisionId?.slice(-12) || '不可被引用' }}</code></div>
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

              <div v-if="detail.files.length" class="hub-files">
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
              <div v-else-if="selectedAsset.sourceKind === 'SKILLHUB'" class="hub-runtime-notice">
                <PackageOpen :size="16" />
                <div><strong>正文尚未下载</strong><p>目录卡片只保存元数据。点击“预览内容”或直接引用时，平台才会下载并校验 ZIP。</p></div>
              </div>
            </div>
            <div v-else-if="selectedRuntimeItem" class="hub-detail-content hub-runtime-detail">
              <div class="hub-detail-top">
                <div>
                  <code>{{ selectedRuntimeItem.id }}</code>
                  <p>{{ selectedRuntimeItem.description || '当前运行态未提供详细说明。' }}</p>
                </div>
                <span class="hub-asset-status builtin"><CheckCircle2 :size="11" />当前已加载</span>
              </div>
              <section class="hub-runtime-summary">
                <div><strong>能力类型</strong><span>{{ tab }}</span></div>
                <div><strong>运行状态</strong><span>{{ selectedRuntimeItem.status || '已加载' }}</span></div>
                <div><strong>数据来源</strong><span>当前工作区 OpenCode 运行态</span></div>
              </section>
              <div class="hub-runtime-notice">
                <PlugZap v-if="tab === 'MCP'" :size="16" /><Wrench v-else :size="16" />
                <div><strong>只读运行态目录</strong><p>MCP / Tool 与顶栏资源盘点共用同一数据，不进入 Agent / Skill 的发布、引用和更新流程。</p></div>
              </div>
            </div>
            <div v-else class="hub-loading overlay"><Loader2 class="hub-spin" :size="18" />正在加载资产详情...</div>
            <div class="hub-drawer-footer">
              <span class="hub-drawer-footer-note">{{ selectedAsset ? '不可变版本快照 · 只读模式' : '当前运行态目录 · 只读模式' }}</span>
              <button class="hub-secondary" type="button" @click="closeDetail">关闭详情</button>
            </div>
          </div>
        </aside>
        </Transition>
      </Teleport>
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
.hub-taxonomy{display:flex;flex-direction:column;gap:7px;margin:-4px 0 16px;border:1px solid #e2e8f0;border-radius:9px;background:#f8fafc;padding:9px 11px}
.hub-taxonomy-row{display:flex;align-items:center;flex-wrap:wrap;gap:6px}
.hub-taxonomy-row>span{width:56px;color:#64748b;font-size:9px;font-weight:700}
.hub-taxonomy-row button{border:1px solid #cbd5e1;border-radius:999px;background:#fff;padding:4px 10px;color:#475569;font-size:9px;cursor:pointer}
.hub-taxonomy-row button:hover{border-color:#93c5fd;color:#1d4ed8}
.hub-taxonomy-row button.is-active{border-color:#2563eb;background:#eff6ff;color:#1d4ed8;font-weight:700}
.hub-taxonomy-row.is-secondary{border-top:1px dashed #dbe3ed;padding-top:7px}
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
.hub-asset-avatar[data-type="MCP"]{background:#f3e8ff;color:#7e22ce}
.hub-asset-avatar[data-type="TOOL"]{background:#fff7ed;color:#c2410c}
.hub-card-type{margin-left:8px;color:#64748b;font-family:var(--font-mono);font-size:9px;letter-spacing:.09em;font-weight:600}
.hub-card-top .hub-asset-status{margin-left:auto}
.hub-asset-card>strong{overflow:hidden;margin-top:10px;text-overflow:ellipsis;font-size:14px;white-space:nowrap;color:#0f172a;font-weight:700}
.hub-asset-card>code{margin-top:2px;color:#64748b;font-size:9px;font-family:var(--font-mono)}
.hub-card-taxonomy{align-self:flex-start;margin-top:7px;border-radius:4px;background:#f1f5f9;padding:3px 6px;color:#475569;font-size:9px;font-weight:700}
.hub-card-contributor{display:inline-flex;align-items:center;gap:3px;margin-top:6px;color:#64748b;font-size:9px;font-weight:600}
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
.hub-drawer.is-fullscreen{position:fixed;z-index:10030}
.hub-drawer-overlay{position:absolute;inset:0;background:rgba(15,23,42,.32);backdrop-filter:blur(2px)}
.hub-detail-panel{position:relative;z-index:1;display:flex;width:min(640px,88vw);height:100%;flex-direction:column;background:#fff;box-shadow:-8px 0 30px rgba(0,0,0,.12);overflow:hidden}
.hub-detail-resize-handle{position:absolute;inset:0 auto 0 0;z-index:3;width:10px;padding:0;border:0;background:transparent;cursor:ew-resize;touch-action:none}
.hub-detail-resize-handle::after{content:"";position:absolute;top:50%;left:50%;width:3px;height:48px;border-radius:999px;background:#cbd5e1;opacity:0;transform:translate(-50%,-50%);transition:opacity .14s ease,background-color .14s ease}
.hub-detail-resize-handle:hover::after,.hub-detail-resize-handle:focus-visible::after,.hub-detail-resize-handle.is-resizing::after{background:var(--hub-blue);opacity:1}
.hub-detail-resize-handle:focus-visible{outline:2px solid var(--hub-blue);outline-offset:-2px}
.hub-detail-head-bar{display:flex;align-items:center;justify-content:space-between;padding:14px 20px;border-bottom:1px solid #e2e8f0;background:#f8fafc}
.hub-drawer-brand{display:flex;align-items:center;gap:10px}
.hub-drawer-brand h2{margin:0;font-size:16px;letter-spacing:-.02em;color:#0f172a}
.hub-detail-head-actions{display:flex;align-items:center;gap:6px}
.hub-close-btn{display:grid;height:32px;width:32px;place-items:center;border:1px solid #cbd5e1;border-radius:8px;background:#fff;color:#475569;transition:all .15s ease;cursor:pointer}
.hub-close-btn:hover{background:#f1f5f9;color:#0f172a;border-color:#94a3b8}

.hub-origin-banner{display:flex;align-items:center;gap:10px;margin:12px 20px 0;padding:10px 14px;border:1px solid #bae6fd;border-radius:8px;background:#f0f9ff;color:#0369a1}
.hub-origin-info{display:flex;align-items:baseline;gap:6px;font-size:11px}
.hub-origin-label{color:#0284c7;font-weight:600;font-size:10px}
.hub-origin-info strong{color:#0c4a6e;font-size:12px}
.hub-origin-workspace{color:#0284c7;font-size:10px}

.hub-detail-content{flex:1;min-height:0;overflow-y:auto;padding:16px 20px 32px}
.hub-detail-top{display:flex;align-items:flex-start;justify-content:space-between;gap:14px}
.hub-detail-top>div:first-child{min-width:0}
.hub-detail-top code{color:#64748b;font-size:10px;font-family:var(--font-mono)}
.hub-detail-top p{margin:6px 0 0;color:var(--hub-muted);font-size:11px;line-height:1.6}
.hub-classification{display:flex;align-items:flex-end;justify-content:space-between;gap:12px;margin-top:12px;border:1px solid #bfdbfe;border-radius:8px;background:#eff6ff;padding:10px 12px}
.hub-classification>div:first-child{display:flex;min-width:150px;flex-direction:column;gap:2px}
.hub-classification>div:first-child strong{color:#1e3a8a;font-size:10px}
.hub-classification>div:first-child span{color:#64748b;font-size:9px;line-height:1.4}
.hub-classification-form{display:flex;align-items:flex-end;justify-content:flex-end;flex-wrap:wrap;gap:7px}
.hub-classification-form label{display:flex;flex-direction:column;gap:3px;color:#475569;font-size:8px;font-weight:700}
.hub-classification-form select{height:29px;min-width:132px;border:1px solid #93c5fd;border-radius:6px;background:#fff;padding:0 8px;color:#1e293b;font-size:10px;outline:0}
.hub-classification-form select:focus{border-color:#2563eb;box-shadow:0 0 0 2px rgba(37,99,235,.12)}
.hub-classification-value{flex:none;border-radius:5px;background:#dbeafe;padding:5px 8px;color:#1d4ed8;font-size:10px}
.hub-runtime-detail{display:flex;flex-direction:column;gap:16px}
.hub-runtime-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.hub-runtime-summary>div{display:flex;min-width:0;flex-direction:column;gap:5px;border:1px solid #e2e8f0;border-radius:8px;background:#f8fafc;padding:12px}
.hub-runtime-summary strong{color:#64748b;font-size:9px;text-transform:uppercase;letter-spacing:.08em}
.hub-runtime-summary span{overflow:hidden;color:#0f172a;font-size:11px;font-weight:600;text-overflow:ellipsis;white-space:nowrap}
.hub-runtime-notice{display:flex;align-items:flex-start;gap:10px;border:1px solid #bfdbfe;border-radius:9px;background:#eff6ff;padding:13px;color:#1d4ed8}
.hub-runtime-notice>svg{flex:none;margin-top:1px}
.hub-runtime-notice div{min-width:0}.hub-runtime-notice strong{font-size:11px}.hub-runtime-notice p{margin:3px 0 0;color:#475569;font-size:10px;line-height:1.55}
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

.hub-files{display:grid;height:320px;min-height:240px;grid-template-columns:150px minmax(0,1fr);margin-top:12px;margin-bottom:8px;overflow:hidden;border:1px solid #cbd5e1;border-radius:8px;background:#fff}
.hub-file-tabs{overflow:auto;border-right:1px solid #e2e8f0;background:#f8fafc;padding:5px}
.hub-file-tabs button{display:flex;width:100%;flex-direction:column;border:0;border-radius:5px;background:transparent;padding:6px 8px;text-align:left;font-family:var(--font-mono);font-size:9px;color:#334155;cursor:pointer}
.hub-file-tabs button.is-active{background:#eff6ff;color:#1d4ed8;font-weight:600}
.hub-file-tabs small{margin-top:2px;color:#94a3b8;font-size:8px}
.hub-code{position:relative;min-width:0;min-height:0}.hub-code :deep(.ta-code-editor){height:100%}

.hub-drawer-footer{display:flex;flex:none;height:44px;align-items:center;justify-content:space-between;padding:0 20px;border-top:1px solid #e2e8f0;background:#f8fafc;box-shadow:0 -2px 10px rgba(0,0,0,.02)}
.hub-drawer-footer-note{color:#94a3b8;font-size:10px;font-weight:500}

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

@media (max-width:760px){.hub-header-main{flex-direction:column;align-items:stretch}.hub-header-actions{flex-direction:column;align-items:stretch}.hub-overview{flex-wrap:wrap}.hub-tabs{overflow:auto;padding:0 12px}.hub-tabs button{flex:none}.hub-catalog-head{align-items:stretch;flex-direction:column}.hub-search{width:100%}.hub-classification{align-items:stretch;flex-direction:column}.hub-classification-form{justify-content:flex-start}.hub-detail-panel{width:100%!important}.hub-runtime-summary{grid-template-columns:1fr}.hub-detail-resize-handle{display:none}}
</style>
