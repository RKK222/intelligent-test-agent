<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, onScopeDispose, provide, ref, shallowRef, watch } from "vue";
import { useRouter } from "vue-router";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/vue-query";
import { AgentChat, buildComposerPromptParts, createInitialAgentChatRuntimeState, reduceAgentChatRuntime, type ComposerAttachment } from "@test-agent/agent-chat";
import { BackendApiError, createBackendApiClient } from "@test-agent/backend-api";
import { DiffViewer, parseUnifiedPatch } from "@test-agent/diff-viewer";
import { CodeEditor, languageFromPath, type EditorSelectionContext } from "@test-agent/editor";
import { subscribeRunEvents } from "@test-agent/event-stream-client";
import { Code2, MessageSquare, Monitor } from "lucide-vue-next";
import { Setting as ElSetting } from "@element-plus/icons-vue";
import type {
  AgentMessage,
  AiMessageFeedback,
  AiMessageFeedbackPayload,
  ApplicationWorkspaceTemplate,
  ApplicationWorkspaceVersion,
  FileContent,
  FileSearchResult,
  FileTreeEntry,
  ManagedApplication,
  MessagePart,
  PageResponse,
  PromptPart,
  Run,
  RunDiffFile,
  RunEvent,
  RuntimeResourceInfo,
  RuntimeToolInfo,
  Session,
  UserOpencodeProcess,
  Workspace,
  WorkspaceBackendServer,
  WorkspaceDirectoryList
} from "@test-agent/shared-types";
import { TerminalPanel } from "@test-agent/terminal";
import { TestRunnerPanel } from "@test-agent/test-runner";
import { type Feedback } from "@test-agent/ui-kit";
import {
  useWorkbenchStore,
  mockVcsDiffFiles,
  mockPublicAgentDiffs,
  mockWorkspaceAgentDiffs,
  type EditorTab
} from "@test-agent/workbench-shell";
import { useAuthStore } from "../stores/authStore";
import FigmaShell from "./FigmaShell.vue";
import FigmaFileExplorer from "./FigmaFileExplorer.vue";
import FigmaEditorArea from "./FigmaEditorArea.vue";
import FigmaChatPanel from "./FigmaChatPanel.vue";
import SettingsDialog from "./settings/SettingsDialog.vue";
import WorkspaceBootstrap from "./WorkspaceBootstrap.vue";
import WorkspaceDirectoryPickerDialog from "./WorkspaceDirectoryPickerDialog.vue";
import ServerWorkspacePickerDialog from "./ServerWorkspacePickerDialog.vue";
import SystemManagementWrapper from "./SystemManagementWrapper.vue";
import WorkbenchFooter from "./WorkbenchFooter.vue";
import { notifyFeedback } from "./notify";
import { canStartFollowUp, createFollowUpDraft, dequeueFollowUp, enqueueFollowUp, isRunBusyStatus, type FollowUpDraft } from "./follow-up-queue";
import {
  buildPromptParts,
  diffFilesFromPayload,
  diffFilesFromSessionMessages,
  dispatchRuntimeResult,
  errorFeedback,
  filterWorkspaceRootEntries,
  historyItems,
  inferDiffFromToolPart,
  initialMessages,
  mergeDiffFiles,
  messagesFromSessionMessages,
  modelIdOnly,
  modelValue,
  notifyOnAttention,
  parseCommand,
  promptFromParts,
  runtimeResources,
  runtimeStatus,
  sessionTitleFromFirstMessage,
  syntheticEvent,
  text
} from "./workbench-utils";

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl });
provide("api", api);
const queryClient = useQueryClient();
const workbench = useWorkbenchStore();
const authStore = useAuthStore();
const router = useRouter();
const OPENCODE_PROCESS_STATUS_FAST_REFETCH_INTERVAL_MS = 5000;
const OPENCODE_PROCESS_STATUS_READY_REFETCH_INTERVAL_MS = 30000;

const isSuperAdmin = computed(() => authStore.currentUser?.roles?.includes("SUPER_ADMIN") === true);

// 设置弹窗依赖当前用户角色；工作台直达时需要主动补齐 /api/auth/me。
void authStore.fetchCurrentUser(api);

// 工作台状态
const selectedWorkspaceId = ref<string | undefined>(undefined);
const entriesByDirectory = ref<Record<string, FileTreeEntry[]>>({});
const expandedDirectories = ref<Set<string>>(new Set());
// 多个目录可能同时在加载（用户连续点开多个折叠项，或 expandPathToFile 一次性
// 展开多层）。使用 Set<string> 而非单值 ref，避免后到的加载把前者的 loading 状态覆盖，
// 导致"点击没反应"——toggleDirectory 的二次点击守卫会因 loading 状态丢失而误判。
const loadingPath = ref<Set<string>>(new Set());
// 文件搜索状态：searchKeyword 由输入框双向驱动；searchResults/searchLoading 由防抖后的 RPC 回填。
let searchTimer: ReturnType<typeof setTimeout> | null = null;
let searchSeq = 0;
const searchKeyword = ref("");
const searchResults = ref<FileSearchResult[]>([]);
const searchLoading = ref(false);
const session = shallowRef<Session | null>(null);
const run = shallowRef<Run | null>(null);
const lastPrompt = ref("");
const selectedAgent = ref("");
const selectedProvider = ref("");
const selectedModel = ref("");
const promptMode = ref("build");
const logs = ref<string[]>([]);
const diffFiles = ref<RunDiffFile[]>([]);
const diffSource = ref<"run" | "session" | "vcs" | "agent">("run");
const diffViewMode = ref<"split" | "unified">("split");
const centerMode = ref<"editor" | "diff" | "system">("editor");
const feedback = ref<Feedback | null>(null);
const diffViewerRef = ref<InstanceType<typeof DiffViewer> | null>(null);
const isDiffDirty = ref(false);
const sessionSearch = ref("");
const followUpQueue = ref<FollowUpDraft[]>([]);
const diffContextParts = ref<PromptPart[]>([]);
const editorSelection = ref<EditorSelectionContext | undefined>(undefined);
const bottomMode = ref<"run" | "terminal">("run");
const bottomDrawerOpen = ref(false);
const leftPanelOpen = ref(true);
const rightPanelOpen = ref(true);
const savedLeftPanelOpen = ref(true);
const savedRightPanelOpen = ref(true);

watch(centerMode, (newMode, oldMode) => {
  if (newMode === "system") {
    if (oldMode !== "system") {
      savedLeftPanelOpen.value = leftPanelOpen.value;
      savedRightPanelOpen.value = rightPanelOpen.value;
    }
    leftPanelOpen.value = false;
    rightPanelOpen.value = false;
  } else if (oldMode === "system") {
    leftPanelOpen.value = savedLeftPanelOpen.value;
    rightPanelOpen.value = savedRightPanelOpen.value;
  }
});

const selectedAppId = ref<string | undefined>(undefined);
// 当前选中版本对应的默认个人工作区 ID，供 GitChangesPanel 调用 publishPersonalWorkspace。
const currentPersonalWorkspaceId = ref<string | undefined>(undefined);
const readonlySessionReason = ref("");
const chatTitle = computed(() => session.value?.title ?? "生成测试案例");
// 任务消耗展示：duration 取 chatStartedAt 实时计算；tokens 从助手消息的 step-finish part
// 累计（opencode 每轮 step 结束会上报 tokens.total）；thought for 累计 reasoning part 的
// durationMs。Run 结束后保留最后值继续展示。Run 切换时清零。
const chatStartedAt = ref<number | null>(null);
const accumulatedTokens = ref(0);
const accumulatedReasoningMs = ref(0);
const totalDurationMs = ref(0);
let lastDuration: string | undefined;
let lastTokens = 0;
let lastThoughtForMs = 0;
const nowTick = ref(Date.now());
const settingsOpen = ref(false);
const directoryPickerOpen = ref(false);
const directoryPickerLoading = ref(false);
const directoryPickerData = shallowRef<WorkspaceDirectoryList | null>(null);
const serverWorkspacePickerOpen = ref(false);
const serverWorkspacePickerLoading = ref(false);
const serverWorkspaceServers = shallowRef<WorkspaceBackendServer[]>([]);
const serverWorkspaceDirectory = shallowRef<WorkspaceDirectoryList | null>(null);
const selectedServerWorkspaceServerId = ref<string | undefined>(undefined);
// 实时追踪：开启后 agent 每次写文件（write/edit/apply_patch 工具完成）就把该文件以只读预览
// 打开在中间编辑器并读取磁盘最新内容刷新——agent 直接写盘，磁盘即最新。
const liveTrack = ref(false);
// 已跟随过的 tool partId，避免同一工具调用重复读盘刷新。
const liveFollowedParts = ref<Set<string>>(new Set());
// Markdown 预览开关：状态由 FigmaEditorArea tab 表头按钮双向绑定到 CodeEditor 的 showPreview。
// 切换非 Markdown 文件时由 watch 主动复位，避免下次切回 md 时残留之前的开启状态。
const markdownPreview = ref(false);
// 只有用户主动触发的健康刷新需要短暂阻止提交；后台轮询刷新不应周期性打断输入体验。
const manualOpencodeProcessRefreshing = ref(false);

// Ctrl/Cmd+S 全局快捷键：在编辑器打开文件时拦截浏览器默认的「保存网页」行为，
// 转而触发右下角保存按钮同款逻辑（saveMutation.mutate）。
// 条件与保存按钮禁用态完全一致：必须有 activeTab、非 livePreview、文件存在未保存改动、
// 非只读、未在保存中。即使条件不满足也要 preventDefault，避免在 IDE 类应用里出现
// 「按 Ctrl+S 弹网页另存为」的尴尬体验。
function tryHandleSaveShortcut(event: KeyboardEvent) {
  // 同时覆盖 Windows/Linux 的 Ctrl 和 macOS 的 Cmd
  const isSaveCombo = (event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey && (event.key === "s" || event.key === "S");
  if (!isSaveCombo) return;
  const tab = activeTab.value;
  // 没有活动 tab（用户还在聊天/文件树）也吞掉事件，避免浏览器另存为
  if (!tab) {
    event.preventDefault();
    return;
  }
  const dirty = tab.content !== tab.savedContent;
  const canSave = !tab.livePreview && !tab.readonly && !saveMutation.isPending.value && dirty;
  // 始终 preventDefault：即使不可保存也吞掉浏览器默认行为
  event.preventDefault();
  if (canSave) {
    saveMutation.mutate(tab);
  }
}

function onWindowKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null;
  if (target) {
    // Monaco 编辑器自带 addCommand 注册了 Ctrl/Cmd+S 快捷键，
    // 这里识别 Monaco 容器后跳过 window 层处理，避免与编辑器命令重复触发。
    if (target.closest && target.closest(".monaco-editor")) {
      return;
    }
    const tag = target.tagName;
    const isEditable = target.isContentEditable;
    // 普通输入控件（composer、设置页输入、搜索框等）不拦截，
    // 保留浏览器/控件自身的快捷键和文本编辑体验。
    if (isEditable || tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") {
      return;
    }
  }
  tryHandleSaveShortcut(event);
}

onMounted(() => {
  window.addEventListener("keydown", onWindowKeydown);
});
onBeforeUnmount(() => {
  window.removeEventListener("keydown", onWindowKeydown);
});

// Chat runtime：单一 reducer 维护，dispatch 闭包更新
const chatState = ref(createInitialAgentChatRuntimeState(initialMessages));
const messageFeedbacks = ref<Record<string, AiMessageFeedback | null>>({});
const feedbackSubmitting = ref<Record<string, boolean>>({});
function dispatchChat(action: Parameters<typeof reduceAgentChatRuntime>[1]) {
  chatState.value = reduceAgentChatRuntime(chatState.value, action);
}

const tabs = computed(() => workbench.tabs);
const activePath = computed(() => workbench.activePath);
const selectedDiffPath = computed(() => workbench.selectedDiffPath);
const activeTab = computed(() => tabs.value.find((tab: EditorTab) => tab.path === activePath.value));
const breadcrumbDisplay = computed(() => {
  if (!activePath.value) return "";
  return activePath.value.split(/[\\/]+/).filter(Boolean).join(" › ");
});

// ===== 查询 =====
const workspacesQuery = useQuery({
  queryKey: ["workspaces"],
  queryFn: () => api.listWorkspaces(1, 50)
});
const workspaces = computed(() => workspacesQuery.data.value?.items ?? []);
// selectedWorkspace 只接受应用 recent workspace 或用户显式选择产生的 selectedWorkspaceId。
// 禁止 fallback 到 workspaces[0]，否则会出现右上角应用与左侧文件树不同步。
const selectedWorkspace = computed(() => {
  if (!selectedAppId.value) return undefined;
  return workspaces.value.find((item) => item.workspaceId === selectedWorkspaceId.value);
});
const selectedWorkspaceIdRef = computed(() => selectedWorkspace.value?.workspaceId);
const sessionSearchTrim = computed(() => sessionSearch.value.trim());

const managedApplicationsQuery = useQuery({
  queryKey: ["managed-workspace", "applications"],
  queryFn: () => api.listManagedApplications(),
  retry: false
});
const managedApplications = computed<ManagedApplication[]>(() => managedApplicationsQuery.data.value ?? []);
const canListAllApplications = computed(() => {
  const roles = authStore.currentUser?.roles ?? [];
  return roles.includes("APP_ADMIN") || roles.includes("SUPER_ADMIN");
});
const allApplicationsEnabled = computed(
  () => canListAllApplications.value && managedApplicationsQuery.isSuccess.value && managedApplications.value.length === 0
);
const allApplicationsQuery = useQuery({
  queryKey: ["managed-workspace", "applications", "admin-fallback"],
  enabled: allApplicationsEnabled,
  queryFn: () => api.listApplications(true),
  retry: false
});
// 右上角应用目录优先展示当前用户加入的应用；管理员未加入任何应用时，回退到配置管理启用应用，
// 避免应用菜单一直显示"未选择应用"，同时后续 recent workspace 权限仍由 workspace-management 兜底。
const applicationCatalog = computed<ManagedApplication[]>(() =>
  managedApplications.value.length ? managedApplications.value : (allApplicationsQuery.data.value ?? [])
);
// 全局最近工作区：跨应用维度维护「上一次进入的应用 + 工作区」组合。
// 重新登录或换电脑登录时，前端用它直接还原上次的应用上下文（替代之前总是回退 apps[0] 的逻辑），
// 工作区是否在当前用户权限内则继续走 per-app recent / 模板首版本兜底。
const globalRecentQuery = useQuery({
  queryKey: ["managed-workspace", "recent-workspace"],
  queryFn: () => api.getRecentManagedWorkspace(),
  retry: false
});
const globalRecentAppId = computed(() => globalRecentQuery.data.value?.appId ?? null);
const globalRecentLoaded = computed(() => globalRecentQuery.isSuccess.value || globalRecentQuery.isError.value);
const shellApps = computed(() =>
  applicationCatalog.value.map((app) => ({ id: app.appId, name: app.appName, description: app.enabled ? "已启用" : "已停用" }))
);
const selectedManagedApplication = computed(() => applicationCatalog.value.find((app) => app.appId === selectedAppId.value));

// ===== 应用工作空间模板与版本（两级菜单数据源） =====
// 一级菜单：归属当前应用的工作空间模板（如 F-COSS 主服务）；二级菜单：模板下的应用版本（如 20260701）。
// 模板在切换应用时拉取一次；版本按需懒加载，用户在菜单里 hover 模板时才拉取，避免一次性把全部版本拉回前端。
const selectedAppIdRef = computed(() => selectedAppId.value);
const appTemplatesQuery = useQuery({
  queryKey: ["managed-workspace", "app-templates", selectedAppIdRef],
  enabled: () => Boolean(selectedAppIdRef.value),
  queryFn: () => api.listWorkspaceTemplates(selectedAppIdRef.value!),
  retry: false
});
const appTemplates = computed<ApplicationWorkspaceTemplate[]>(() => appTemplatesQuery.data.value ?? []);
const loadingAppTemplates = computed(() => appTemplatesQuery.isPending.value);
// 按模板 ID 缓存应用版本；用户首次展开某个模板时调用 ensureAppVersionsLoaded(templateId)。
const versionsByTemplateId = ref<Record<string, ApplicationWorkspaceVersion[]>>({});
const loadingVersionTemplateIds = ref<Set<string>>(new Set());
// 通过 useQueries 监听"已请求加载的模板"，避免在 set 里手写 fetch 后的状态同步。
// 数组元素跟着 loadedTemplateIds 派生；enabled = false 时该查询会被 vue-query 跳过。
const loadedTemplateIds = ref<Set<string>>(new Set());
const versionQueries = useQueries({
  queries: computed(() =>
    [...loadedTemplateIds.value].map((templateId) => {
      const template = appTemplates.value.find((item) => item.workspaceId === templateId);
      return {
        queryKey: ["managed-workspace", "app-versions", selectedAppIdRef, templateId],
        enabled: () => Boolean(selectedAppIdRef.value && template),
        queryFn: async (): Promise<ApplicationWorkspaceVersion[]> => {
          if (!template) return [];
          return api.listWorkspaceVersions(selectedAppIdRef.value!, template.workspaceId);
        }
      };
    })
  )
});
// 监听 useQueries 的结果回填到 versionsByTemplateId；并清掉 loading 标记。
// 注意：vue-query 的 useQueries 返回的数组元素是 QueryObserverResult（来自 query-core），
// 其属性是普通值（isPending/data 为值），不是 ComputedRef；watch 回调拿到的就是已解包的数组。
watch(
  versionQueries,
  (queries) => {
    const nextMap: Record<string, ApplicationWorkspaceVersion[]> = { ...versionsByTemplateId.value };
    const nextLoading = new Set(loadingVersionTemplateIds.value);
    const templateIds = [...loadedTemplateIds.value];
    templateIds.forEach((templateId, index) => {
      const result = queries[index];
      if (!result) return;
      if (result.isPending) {
        nextLoading.add(templateId);
        return;
      }
      nextLoading.delete(templateId);
      if (result.isSuccess && result.data) {
        nextMap[templateId] = result.data;
      }
    });
    versionsByTemplateId.value = nextMap;
    loadingVersionTemplateIds.value = nextLoading;
  },
  { deep: true }
);
const loadingAppVersions = computed(() => loadingVersionTemplateIds.value.size > 0);
// 把模板 + 关联的版本组装成 WorkbenchFooter 期望的两级结构。
const appTemplatesWithVersions = computed(() =>
  appTemplates.value.map((template) => ({
    ...template,
    versions: versionsByTemplateId.value[template.workspaceId]
  }))
);
// 当前选中的版本 ID：默认从 selectedWorkspaceId 与 recent 偏好反查；切到版本后由 handleSelectVersion 更新。
const currentVersionFromWorkspace = ref<string | undefined>(undefined);
const selectedVersionId = computed(() => currentVersionFromWorkspace.value);
// 触发懒加载：被调用时把 templateId 加入 loadedTemplateIds，useQueries 派生数组自动同步并发起请求。
// 重复调用幂等：Set 内部去重；已加载完成的模板（versions 不为 undefined）不会重复请求。
function ensureAppVersionsLoaded(templateId: string) {
  if (versionsByTemplateId.value[templateId] !== undefined) return;
  if (loadedTemplateIds.value.has(templateId)) return;
  const next = new Set(loadedTemplateIds.value);
  next.add(templateId);
  loadedTemplateIds.value = next;
}
// 切换应用时清空版本缓存，避免上一个应用的版本残留到新应用的菜单里。
watch(selectedAppId, () => {
  versionsByTemplateId.value = {};
  loadedTemplateIds.value = new Set();
  loadingVersionTemplateIds.value = new Set();
  currentVersionFromWorkspace.value = undefined;
});

const sessionsQuery = useQuery({
  queryKey: ["sessions", selectedWorkspaceIdRef, sessionSearchTrim],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) || Boolean(sessionSearchTrim.value),
  queryFn: () => {
    const query = sessionSearchTrim.value;
    return query ? api.listAllSessions(1, 30, query) : api.listSessions(selectedWorkspaceIdRef.value!, 1, 30);
  }
});

const opencodeProcessEnabled = computed(() => authStore.isAuthenticated());
const opencodeProcessQueryKey = computed(() => ["runtime", "opencode-process", "me", authStore.token ?? ""] as const);
const opencodeProcessQuery = useQuery({
  queryKey: opencodeProcessQueryKey,
  enabled: opencodeProcessEnabled,
  queryFn: () => api.getMyOpencodeProcess(),
  retry: false,
  // 未 READY 时保持快速探测；READY 后降频，避免每个工作台标签页持续压测 manager health。
  refetchInterval: (query) => {
    const status = (query.state.data as UserOpencodeProcess | undefined)?.status;
    return status === "READY"
      ? OPENCODE_PROCESS_STATUS_READY_REFETCH_INTERVAL_MS
      : OPENCODE_PROCESS_STATUS_FAST_REFETCH_INTERVAL_MS;
  },
  refetchIntervalInBackground: false
});
const opencodeProcessStatus = computed<UserOpencodeProcess | null>(() => opencodeProcessQuery.data.value ?? null);
const opencodeProcessReady = computed(() => opencodeProcessStatus.value?.status === "READY");

const agentsQuery = useQuery({
  queryKey: ["runtime", "agents", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && opencodeProcessReady.value,
  queryFn: () => api.listAgents(selectedWorkspaceIdRef.value!)
});
const modelsQuery = useQuery({
  queryKey: ["runtime", "models"],
  enabled: opencodeProcessReady,
  queryFn: () => api.listModels()
});
const providersQuery = useQuery({
  queryKey: ["runtime", "providers"],
  enabled: opencodeProcessReady,
  queryFn: () => api.listProviders()
});
const commandsQuery = useQuery({
  queryKey: ["runtime", "commands", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && opencodeProcessReady.value,
  queryFn: () => api.listCommands(selectedWorkspaceIdRef.value!)
});
const lspStatusQuery = useQuery({
  queryKey: ["runtime", "lsp", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && opencodeProcessReady.value,
  queryFn: () => api.getLspStatus(selectedWorkspaceIdRef.value!),
  refetchInterval: 30000
});
const mcpStatusQuery = useQuery({
  queryKey: ["runtime", "mcp", "status", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && opencodeProcessReady.value,
  queryFn: () => api.getMcpStatus(selectedWorkspaceIdRef.value!),
  refetchInterval: 30000
});
const mcpResourcesQuery = useQuery({
  queryKey: ["runtime", "mcp", "resources", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && opencodeProcessReady.value,
  queryFn: () => api.getMcpResources(selectedWorkspaceIdRef.value!)
});
const mcpToolsQuery = useQuery({
  queryKey: ["runtime", "mcp", "tools", selectedWorkspaceIdRef, selectedProvider, selectedModel],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && opencodeProcessReady.value,
  queryFn: () => {
    const model = modelIdOnly(selectedModel.value);
    return api.getMcpTools(selectedWorkspaceIdRef.value!, selectedProvider.value || undefined, model || undefined);
  }
});
const vcsStatusQuery = useQuery({
  queryKey: ["runtime", "vcs", "status", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && opencodeProcessReady.value,
  queryFn: () => api.getVcsStatus(selectedWorkspaceIdRef.value!),
  refetchInterval: 30000
});

const agents = computed(() => agentsQuery.data.value ?? []);
const models = computed(() => modelsQuery.data.value ?? []);
const providers = computed(() => providersQuery.data.value ?? []);
const commands = computed(() => commandsQuery.data.value ?? []);
const mcpResourcesData = computed(() => mcpResourcesQuery.data.value);
const mcpToolsData = computed<RuntimeToolInfo[]>(() => mcpToolsQuery.data.value ?? []);
const vcsStatusData = computed(() => vcsStatusQuery.data.value);
const lspStatusData = computed(() => lspStatusQuery.data.value);
const mcpStatusData = computed(() => mcpStatusQuery.data.value);
// 只在首个状态响应回来前展示“正在检查”，避免 READY 数据后台刷新时把对话区重新置为阻塞态。
const opencodeProcessInitialLoading = computed(
  () => opencodeProcessEnabled.value && !opencodeProcessStatus.value && (opencodeProcessQuery.isPending.value || opencodeProcessQuery.isFetching.value)
);
const opencodeProcessRefreshing = computed(
  () => opencodeProcessEnabled.value && Boolean(opencodeProcessStatus.value) && manualOpencodeProcessRefreshing.value
);
const sessionsItems = computed(() => sessionsQuery.data.value?.items ?? []);
const selectedModelInfo = computed(() => {
  const selected = modelIdOnly(selectedModel.value);
  return models.value.find((model) => modelValue(model) === selectedModel.value || model.id === selected);
});
const selectedModelLabel = computed(() => selectedModelInfo.value?.name ?? selectedModel.value ?? "未选择模型");

function refreshOpencodeProcessStatus() {
  if (!opencodeProcessEnabled.value || opencodeProcessQuery.isFetching.value) return;
  manualOpencodeProcessRefreshing.value = true;
  void opencodeProcessQuery.refetch().finally(() => {
    manualOpencodeProcessRefreshing.value = false;
  });
}
const historyList = computed(() => historyItems(run.value, sessionsItems.value));
const resourcesList = computed(() => runtimeResources(mcpResourcesData.value, activeTab.value));
const runtimeStatusValue = computed(() =>
  runtimeStatus(session.value, run.value, selectedAgent.value, selectedModel.value, vcsStatusData.value, lspStatusData.value, mcpStatusData.value, mcpToolsData.value, mcpResourcesData.value)
);
// VCS 分支选择入口已下线：footer 不再展示「选择分支」/「记住当前分支」按钮，
// 因此 vcsCurrentBranch / vcsDefaultBranch / pendingBranchOverride / recentBranchPreference /
// handleChangeBranch / handleRememberCurrentBranch / loadBranchPreferenceOnEnter 等相关状态与函数全部移除。
// VCS 分支信息仍由 runtimeStatus 读取并参与运行态展示（见 lspStatusData 等 run.status 字段），不依赖此处的引用。

function selectRuntimeModel(model: typeof models.value[number]) {
  if (model.providerId) {
    selectedProvider.value = model.providerId;
  }
  selectedModel.value = modelValue(model);
}

// ===== 默认值与联动 effect =====
// 选择默认应用：优先使用「全局最近工作区」所属应用，让用户重新登录或换电脑登录时
// 自动回到上次所在的应用 + 工作区组合；该应用在当前账号的应用目录里找不到时降级到 apps[0]，
// 都没结果则不主动进入，由用户在右上角手动选择。
function trySelectDefaultApp() {
  if (selectedAppId.value) return;
  const apps = applicationCatalog.value;
  if (apps.length === 0 || !globalRecentLoaded.value) return;
  const preferredAppId =
    globalRecentAppId.value && apps.some((app) => app.appId === globalRecentAppId.value)
      ? globalRecentAppId.value
      : apps[0]?.appId;
  if (preferredAppId) {
    void handleSelectApp(preferredAppId);
  }
}
watch(applicationCatalog, () => {
  trySelectDefaultApp();
});
// applicationCatalog 先于 globalRecent 加载完成时，等 recent 回来再补一次选择。
watch(globalRecentLoaded, () => {
  trySelectDefaultApp();
});
watch(selectedWorkspace, (sw) => {
  if (!selectedWorkspaceId.value && sw?.workspaceId) {
    selectedWorkspaceId.value = sw.workspaceId;
  }
});
watch(activePath, () => {
  editorSelection.value = undefined;
  // 切换到非 Markdown 文件时强制关闭预览：CodeEditor 的预览区在非 md 文件下不会渲染，
  // 但保留 true 会让后续切回 md 时立刻弹出预览，违背"默认不预览"的心智。
  const path = activePath.value;
  if (!path || languageFromPath(path) !== "markdown") {
    markdownPreview.value = false;
  }
});
watch(selectedWorkspaceIdRef, (id) => {
  if (id) {
    void loadDirectory("", id);
  }
});
watch(agentsQuery.data, (data) => {
  if (!selectedAgent.value && data?.[0]?.agentId) {
    selectedAgent.value = data[0].agentId;
  }
});
watch(providersQuery.data, (data) => {
  if (!selectedProvider.value && data?.[0]?.providerId) {
    selectedProvider.value = data[0].providerId;
  }
});
watch(modelsQuery.data, (data) => {
  if (!selectedModel.value && data?.[0]) {
    selectedModel.value = modelValue(data.find((model) => model.defaultModel) ?? data[0]);
  }
});
watch(opencodeProcessReady, (ready, previous) => {
  if (!ready || previous) {
    return;
  }
  // 进程刚从不可用变为 READY 时，主动刷新运行态目录，避免模型/Provider 保留早期失败或空缓存。
  void queryClient.invalidateQueries({ queryKey: ["runtime", "agents"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime", "models"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime", "providers"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime", "commands"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime", "lsp"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime", "mcp"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime", "vcs"] });
});
watch([() => selectedProvider.value, () => selectedModel.value, modelsQuery.data], ([provider, model, data]) => {
  if (!provider || !model || String(model).startsWith(`${provider}/`)) {
    return;
  }
  const nextModel = (data as typeof modelsQuery.data.value | undefined)?.find((m) => m.providerId === provider);
  if (nextModel) {
    selectedModel.value = modelValue(nextModel);
  }
});

// ===== RunEvent SSE 订阅：Run 处于运行/取消中时建立，卸载/状态变化时关闭 =====
watch(run, (r, _old, onCleanup) => {
  if (!r || !["RUNNING", "CANCELLING"].includes(r.status)) {
    return;
  }
  const subscription = subscribeRunEvents({
    baseUrl: apiBaseUrl,
    runId: r.runId,
    onEvent: (event) => handleRunEvent(event),
    onStatus: (status) => {
      logs.value = [...logs.value.slice(-200), `[sse] ${status}`];
    },
    onError: () => {
      feedback.value = { kind: "error", title: "RunEvent SSE 连接异常", description: "前端会等待浏览器自动重连" };
    }
  });
  onCleanup(() => subscription.close());
});

// 实时追踪：tool part 每次更新都扫描新完成的写文件工具，读盘刷新预览（逐次实时）。
watch(
  () => chatState.value.messages,
  () => scanLiveToolParts(),
  { deep: true }
);
// 开启时重置已跟随记录，并立即扫描当前对话里已完成的历史写文件工具。
watch(liveTrack, (on) => {
  liveFollowedParts.value = new Set();
  if (on) {
    scanLiveToolParts();
  }
});
// 新 Run 开始时清空已跟随记录。
watch(run, (r) => {
  if (r && ["RUNNING", "CANCELLING"].includes(r.status)) {
    liveFollowedParts.value = new Set();
  }
});
// step 末兜底：diff.proposed 更新 changed files 时，确保最新被改文件已打开预览
// （覆盖 tool part 路径解析失败的边缘情况；路径不可用则静默跳过，不报错）。
watch(feedback, (current) => {
  if (current) notifyFeedback(current);
});
watch(diffFiles, (files) => {
  if (!liveTrack.value || files.length === 0) {
    return;
  }
  const rel = normalizeWorkspacePath(files.at(-1)!.path);
  if (rel && !rel.startsWith("/")) {
    void openLivePreview(rel);
  }
});

// ===== Mutations =====
const createWorkspaceMutation = useMutation({
  mutationFn: (payload: { name: string; rootPath: string }) => api.createWorkspace(payload),
  onSuccess: (workspace) => {
    selectedWorkspaceId.value = workspace.workspaceId;
    void queryClient.invalidateQueries({ queryKey: ["workspaces"] });
  },
  onError: (error) => {
    feedback.value = errorFeedback("创建 Workspace 失败", error);
  }
});

const saveMutation = useMutation({
  mutationFn: async (tab: NonNullable<typeof activeTab.value>) => {
    if (isAgentFilePath(tab.path)) {
      const agent = agentFileInfo(tab.path);
      if (agent.scope === "PUBLIC") {
        await api.writePublicAgentFile(agent.path, tab.content, agent.worktreeId, agent.linuxServerId);
      } else {
        if (!selectedWorkspace.value) {
          throw new Error("未选择 Workspace");
        }
        await api.writeWorkspaceAgentFile(selectedWorkspace.value.workspaceId, agent.path, tab.content, agent.worktreeId);
      }
      return tab;
    }
    if (isPublicFilePath(tab.path)) {
      // 公共目录写：仅 SUPER_ADMIN 角色可调（服务端二次校验），前端不暴露给普通用户。
      await api.writePublicFile(publicFilePath(tab.path), tab.content);
      return tab;
    }
    if (!selectedWorkspace.value) {
      throw new Error("未选择 Workspace");
    }
    await api.writeFile(selectedWorkspace.value.workspaceId, tab.path, tab.content);
    return tab;
  },
  onSuccess: (tab) => {
    workbench.markTabSaved(tab.path, tab.content);
    feedback.value = { kind: "success", title: "文件已保存", description: tab.path };
  },
  onError: (error) => {
    feedback.value = errorFeedback("保存文件失败", error);
  }
});

// 公共文件 tab.path 用 "public:<相对路径>" 表示；与工作区路径空间隔离，避免编辑器 activePath 撞名。
const PUBLIC_FILE_PREFIX = "public:";
const AGENT_PUBLIC_FILE_PREFIX = "agent-public:";
const AGENT_WORKSPACE_FILE_PREFIX = "agent-workspace:";
function isPublicFilePath(path: string): boolean {
  return path.startsWith(PUBLIC_FILE_PREFIX);
}
function publicFilePath(tabPath: string): string {
  return tabPath.startsWith(PUBLIC_FILE_PREFIX) ? tabPath.slice(PUBLIC_FILE_PREFIX.length) : tabPath;
}
function isAgentFilePath(path: string): boolean {
  return path.startsWith(AGENT_PUBLIC_FILE_PREFIX) || path.startsWith(AGENT_WORKSPACE_FILE_PREFIX);
}
function agentTabPath(scope: "PUBLIC" | "WORKSPACE", path: string, worktreeId?: string | null, linuxServerId?: string | null): string {
  const prefix = scope === "PUBLIC" ? AGENT_PUBLIC_FILE_PREFIX : AGENT_WORKSPACE_FILE_PREFIX;
  return `${prefix}${encodeURIComponent(worktreeId ?? "")}:${encodeURIComponent(linuxServerId ?? "")}:${encodeURIComponent(path)}`;
}
function agentFileInfo(tabPath: string): { scope: "PUBLIC" | "WORKSPACE"; path: string; worktreeId?: string; linuxServerId?: string } {
  const scope: "PUBLIC" | "WORKSPACE" = tabPath.startsWith(AGENT_PUBLIC_FILE_PREFIX) ? "PUBLIC" : "WORKSPACE";
  const prefix = scope === "PUBLIC" ? AGENT_PUBLIC_FILE_PREFIX : AGENT_WORKSPACE_FILE_PREFIX;
  const rest = tabPath.slice(prefix.length);
  const firstSeparator = rest.indexOf(":");
  const secondSeparator = firstSeparator >= 0 ? rest.indexOf(":", firstSeparator + 1) : -1;
  const rawWorktree = firstSeparator >= 0 ? rest.slice(0, firstSeparator) : "";
  const rawLinuxServer = secondSeparator >= 0 ? rest.slice(firstSeparator + 1, secondSeparator) : "";
  const rawPath = secondSeparator >= 0
    ? rest.slice(secondSeparator + 1)
    : firstSeparator >= 0
      ? rest.slice(firstSeparator + 1)
      : rest;
  return {
    scope,
    path: decodeURIComponent(rawPath),
    worktreeId: rawWorktree ? decodeURIComponent(rawWorktree) : undefined,
    linuxServerId: rawLinuxServer ? decodeURIComponent(rawLinuxServer) : undefined
  };
}

const startRunMutation = useMutation({
  mutationFn: async (input: { prompt: string; parts: PromptPart[]; title?: string }) => {
    if (!opencodeProcessReady.value) {
      throw new Error("请先初始化 opencode 进程");
    }
    if (!selectedWorkspace.value) {
      throw new Error("未选择 Workspace");
    }
    const activeSession =
      session.value ??
      (await api.createSession(
        selectedWorkspace.value.workspaceId,
        sessionTitleFromFirstMessage(input.title ?? input.prompt)
      ));
    session.value = activeSession;
    void queryClient.invalidateQueries({ queryKey: ["sessions"] });
    return api.startRun({
      sessionId: activeSession.sessionId,
      prompt: input.prompt,
      parts: input.parts,
      agent: selectedAgent.value || undefined,
      model: selectedModel.value || undefined,
      mode: promptMode.value
    });
  },
  onSuccess: (started) => {
    run.value = started;
    logs.value = [...logs.value, `[run] ${started.runId} ${started.status}`];
  },
  onError: (error) => {
    feedback.value = errorFeedback("启动 Run 失败", error);
  }
});

const initializeOpencodeProcessMutation = useMutation({
  mutationFn: () => api.initializeMyOpencodeProcess(),
  onSuccess: (status) => {
    queryClient.setQueryData(opencodeProcessQueryKey.value, status);
  },
  onError: (error) => {
    feedback.value = errorFeedback("初始化 opencode 进程失败", error);
  }
});

const commandMutation = useMutation({
  mutationFn: async (input: { command: string; arguments: string; prompt: string }) => {
    if (!session.value) {
      throw new Error("当前 Session 尚未绑定远端上下文，请先发送一次普通 prompt");
    }
    return api.runSessionCommand(session.value.sessionId, {
      command: input.command,
      arguments: input.arguments,
      agent: selectedAgent.value || undefined,
      model: selectedModel.value || undefined
    });
  },
  onSuccess: (result) => {
    logs.value = [...logs.value.slice(-200), "[command] completed"];
    dispatchRuntimeResult(result, dispatchChat);
  },
  onError: (error) => {
    feedback.value = errorFeedback("命令执行失败", error);
  }
});

const runtimeBusy = computed(() => isRunBusyStatus(run.value?.status) || startRunMutation.isPending.value || commandMutation.isPending.value);
const canStopRun = computed(() => Boolean(run.value && isRunBusyStatus(run.value.status) && !cancelRunMutation.isPending.value));
const stopDisabledReason = computed(() => {
  if (cancelRunMutation.isPending.value) return "正在终止";
  if (!run.value) return "当前没有可终止的运行";
  if (!isRunBusyStatus(run.value.status)) return "当前运行已结束";
  return "";
});

// 把累计毫秒数格式化为 "Xm Ys" 或 "Ys" 的展示文案。
function formatDurationMs(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  if (totalSec <= 0) return "0s";
  const minutes = Math.floor(totalSec / 60);
  const remain = totalSec % 60;
  return minutes > 0 ? `${minutes}m ${remain}s` : `${totalSec}s`;
}

// 把 "1s"/"500ms"/"1m 30s" 等字符串解析成毫秒；解析失败返回 0。
function parseDurationStringToMs(input: string): number {
  const trimmed = input.trim();
  if (!trimmed) return 0;
  if (trimmed.endsWith("ms")) {
    const n = Number(trimmed.slice(0, -2));
    return Number.isFinite(n) ? n : 0;
  }
  if (trimmed.endsWith("s")) {
    const n = Number(trimmed.slice(0, -1));
    return Number.isFinite(n) ? n * 1000 : 0;
  }
  if (trimmed.endsWith("m")) {
    const n = Number(trimmed.slice(0, -1));
    return Number.isFinite(n) ? n * 60_000 : 0;
  }
  const n = Number(trimmed);
  return Number.isFinite(n) ? n : 0;
}

// 任务消耗：duration 优先用 chatStartedAt 实时计算（每秒刷新），结束后回退 lastDuration。
// tokens/thoughtFor 优先用累计值，fallback 到 run 终态事件 payload 中的字段以保持向后兼容。
const taskUsage = computed<{ duration?: string; tokens?: number; thoughtFor?: string; totalDuration?: string }>(() => {
  // 引用 nowTick 以触发每秒重算
  void nowTick.value;
  const usage: { duration?: string; tokens?: number; thoughtFor?: string; totalDuration?: string } = {};
  if (chatStartedAt.value) {
    usage.duration = formatDurationMs(Date.now() - chatStartedAt.value);
  } else if (lastDuration) {
    usage.duration = lastDuration;
  }
  const tokens = accumulatedTokens.value > 0 ? accumulatedTokens.value : lastTokens;
  if (tokens > 0) {
    usage.tokens = tokens;
  }
  const reasoningMs = accumulatedReasoningMs.value > 0 ? accumulatedReasoningMs.value : lastThoughtForMs;
  if (reasoningMs > 0) {
    usage.thoughtFor = formatDurationMs(reasoningMs);
  }
  // 累计时间 = 已完成各轮耗时 + 当前轮实时耗时
  const finishedMs = totalDurationMs.value;
  const currentMs = chatStartedAt.value ? Date.now() - chatStartedAt.value : 0;
  const total = finishedMs + currentMs;
  if (total > 0) {
    usage.totalDuration = formatDurationMs(total);
  }
  return usage;
});

// 扫描 chatState.messages，累计 step-finish tokens 与 reasoning durationMs。
// 一次 Run 内多次 step-finish 会重复累加，与 opencode 上报的每轮消耗一致。
function recomputeUsageFromChat() {
  let tokens = 0;
  let reasoningMs = 0;
  for (const message of chatState.value.messages) {
    if (message.role !== "assistant") continue;
    for (const part of message.parts ?? []) {
      if (part.type === "step-finish") {
        tokens += part.tokens?.total ?? 0;
      } else if (part.type === "reasoning") {
        reasoningMs += part.durationMs ?? 0;
      }
    }
  }
  accumulatedTokens.value = tokens;
  accumulatedReasoningMs.value = reasoningMs;
}

watch(
  () => chatState.value.messages,
  () => recomputeUsageFromChat(),
  { deep: true }
);

// Run 运行时开启 1s tick 让 duration 持续滚动；空闲时停止。
let tickHandle: ReturnType<typeof setInterval> | null = null;
function startTick() {
  if (tickHandle) return;
  tickHandle = setInterval(() => {
    nowTick.value = Date.now();
  }, 1000);
}
function stopTick() {
  if (tickHandle) {
    clearInterval(tickHandle);
    tickHandle = null;
  }
}
onScopeDispose(() => stopTick());
watch(runtimeBusy, (busy) => {
  if (busy) startTick();
  else stopTick();
}, { immediate: true });

// follow-up 队列：Run 空闲且有排队 prompt 时自动出队执行
watch(
  [followUpQueue, run, session, () => startRunMutation.isPending.value, () => commandMutation.isPending.value, opencodeProcessReady],
  () => {
    if (
      followUpQueue.value.length === 0 ||
      !opencodeProcessReady.value ||
      !canStartFollowUp(run.value, startRunMutation.isPending.value || commandMutation.isPending.value)
    ) {
      return;
    }
    const { next, queue } = dequeueFollowUp(followUpQueue.value);
    if (!next) {
      return;
    }
    followUpQueue.value = queue;
    if (next.command && session.value) {
      commandMutation.mutate({ ...next.command, prompt: next.prompt });
    } else {
      startRunMutation.mutate({ prompt: next.prompt, parts: next.parts });
    }
  }
);

const updateSessionMutation = useMutation({
  mutationFn: async (input: { sessionId: string; title?: string; pinned?: boolean }) =>
    api.updateSession(input.sessionId, { title: input.title, pinned: input.pinned }),
  onSuccess: (updated) => {
    if (session.value?.sessionId === updated.sessionId) {
      session.value = updated;
    }
    void queryClient.invalidateQueries({ queryKey: ["sessions"] });
  },
  onError: (error) => {
    feedback.value = errorFeedback("更新 Session 失败", error);
  }
});

const deleteSessionMutation = useMutation({
  mutationFn: async (sessionId: string) => api.deleteSession(sessionId),
  onSuccess: (deleted) => {
    if (session.value?.sessionId === deleted.sessionId) {
      session.value = null;
      run.value = null;
      dispatchChat({ type: "reset" });
    }
    void queryClient.invalidateQueries({ queryKey: ["sessions"] });
  },
  onError: (error) => {
    feedback.value = errorFeedback("删除 Session 失败", error);
  }
});

const cancelRunMutation = useMutation({
  mutationFn: async () => {
    if (!run.value) {
      throw new Error("当前没有 Run");
    }
    return api.cancelRun(run.value.runId);
  },
  onSuccess: (cancelled) => {
    run.value = cancelled;
  },
  onError: (error) => {
    feedback.value = errorFeedback("取消 Run 失败", error);
  }
});

const acceptDiffMutation = useMutation({
  mutationFn: async () => {
    if (!run.value) {
      throw new Error("当前没有 Run");
    }
    return api.acceptRunDiff(run.value.runId);
  },
  onSuccess: (result) => {
    feedback.value = { kind: "success", title: "已接受 Run 级 Diff", description: `${result.fileCount} 个文件` };
  },
  onError: (error) => {
    feedback.value = errorFeedback("接受 Diff 失败", error);
  }
});

const rejectDiffMutation = useMutation({
  mutationFn: async () => {
    if (!run.value) {
      throw new Error("当前没有 Run");
    }
    return api.rejectRunDiff(run.value.runId);
  },
  onSuccess: (result) => {
    feedback.value = { kind: "success", title: "已拒绝 Run 级 Diff", description: `${result.fileCount} 个文件` };
  },
  onError: (error) => {
    feedback.value = errorFeedback("拒绝 Diff 失败", error);
  }
});

const replyPermissionMutation = useMutation({
  mutationFn: async (payload: { requestId: string; decision: "once" | "always" | "reject" }) => {
    if (!session.value) {
      throw new Error("当前没有 Session");
    }
    return api.replySessionPermission(session.value.sessionId, payload.requestId, { decision: payload.decision });
  },
  onSuccess: (_result, payload) => dispatchChat({ type: "permission.replied", requestId: payload.requestId }),
  onError: (error) => {
    feedback.value = errorFeedback("权限回复失败", error);
  }
});

const replyQuestionMutation = useMutation({
  mutationFn: async (payload: { requestId: string; answers: unknown[] }) => {
    if (!session.value) {
      throw new Error("当前没有 Session");
    }
    return api.replySessionQuestion(session.value.sessionId, payload.requestId, { answers: payload.answers });
  },
  onSuccess: (_result, payload) => dispatchChat({ type: "question.replied", requestId: payload.requestId }),
  onError: (error) => {
    feedback.value = errorFeedback("提问回复失败", error);
  }
});

const rejectQuestionMutation = useMutation({
  mutationFn: async (requestId: string) => {
    if (!session.value) {
      throw new Error("当前没有 Session");
    }
    return api.rejectSessionQuestion(session.value.sessionId, requestId);
  },
  onSuccess: (_result, requestId) => dispatchChat({ type: "question.replied", requestId }),
  onError: (error) => {
    feedback.value = errorFeedback("拒绝提问失败", error);
  }
});

const submitMessageFeedbackMutation = useMutation({
  mutationFn: async (payload: AiMessageFeedbackPayload & { messageId: string }) =>
    api.putMessageFeedback(payload.messageId, {
      rating: payload.rating,
      reasonCode: payload.reasonCode,
      comment: payload.comment
    }),
  onMutate: payload => {
    feedbackSubmitting.value = { ...feedbackSubmitting.value, [payload.messageId]: true };
  },
  onSuccess: (saved, payload) => {
    messageFeedbacks.value = { ...messageFeedbacks.value, [payload.messageId]: saved };
    feedback.value = { kind: "success", title: "反馈已提交", description: payload.rating === "POSITIVE" ? "满意" : "不满意" };
  },
  onError: (error, payload) => {
    feedback.value = errorFeedback("提交反馈失败", error);
    feedbackSubmitting.value = { ...feedbackSubmitting.value, [payload.messageId]: false };
  },
  onSettled: (_data, _error, payload) => {
    feedbackSubmitting.value = { ...feedbackSubmitting.value, [payload.messageId]: false };
  }
});

function createTerminalTicket() {
  if (!session.value) {
    throw new Error("当前 Session 尚未绑定远端上下文，请先发送一次普通 prompt");
  }
  return api.createTerminalTicket(session.value.sessionId, {
    workspaceId: selectedWorkspace.value?.workspaceId,
    cols: 120,
    rows: 32
  });
}

function resetWorkspaceState() {
  // Workspace 切换后必须清掉旧根目录绑定的文件树、编辑器、Diff 与运行态，避免误操作旧路径。
  entriesByDirectory.value = {};
  expandedDirectories.value = new Set();
  loadingPath.value = new Set();
  // 切换工作区时清空搜索状态，避免旧工作区的搜索结果残留。
  searchKeyword.value = "";
  searchResults.value = [];
  searchLoading.value = false;
  if (searchTimer) {
    clearTimeout(searchTimer);
    searchTimer = null;
  }
  searchSeq++;
  session.value = null;
  run.value = null;
  logs.value = [];
  diffFiles.value = [];
  diffSource.value = "run";
  centerMode.value = "editor";
  sessionSearch.value = "";
  followUpQueue.value = [];
  diffContextParts.value = [];
  editorSelection.value = undefined;
  readonlySessionReason.value = "";
  liveFollowedParts.value = new Set();
  selectedAgent.value = "";
  selectedProvider.value = "";
  selectedModel.value = "";
  markdownPreview.value = false;
  // 切工作区时同步清掉任务消耗计时与上一轮终态展示，避免旧 Run 的 token/duration 残留。
  chatStartedAt.value = null;
  accumulatedTokens.value = 0;
  accumulatedReasoningMs.value = 0;
  totalDurationMs.value = 0;
  lastDuration = undefined;
  lastTokens = 0;
  lastThoughtForMs = 0;
  nowTick.value = Date.now();
  dispatchChat({ type: "reset" });
  // 切工作区时清掉个人工作区 ID，避免旧版本的空 ID 残留导致提交/推送指向错误目标。
  currentPersonalWorkspaceId.value = undefined;
  workbench.resetWorkspaceView();
}

function cacheWorkspace(workspace: Workspace) {
  queryClient.setQueryData<PageResponse<Workspace>>(["workspaces"], (old) => {
    const previousItems = old?.items ?? [];
    const existed = previousItems.some((item) => item.workspaceId === workspace.workspaceId || sameWorkspaceLocation(item, workspace));
    const items = [
      workspace,
      ...previousItems.filter((item) => item.workspaceId !== workspace.workspaceId && !sameWorkspaceLocation(item, workspace))
    ];
    return {
      items,
      page: old?.page ?? 1,
      size: old?.size ?? Math.max(items.length, 50),
      total: old ? old.total + (existed ? 0 : 1) : items.length
    };
  });
}

function sameWorkspaceLocation(left: Workspace, right: Workspace) {
  return left.rootPath === right.rootPath && (left.linuxServerId ?? "") === (right.linuxServerId ?? "");
}

function workspaceNameFromPath(path: string) {
  return path.split(/[\\/]+/).filter(Boolean).at(-1) ?? "Workspace";
}

async function loadWorkspaceDirectories(path?: string) {
  directoryPickerLoading.value = true;
  try {
    directoryPickerData.value = await api.listWorkspaceDirectories(path);
  } catch (error) {
    feedback.value = errorFeedback("加载工作区目录失败", error);
  } finally {
    directoryPickerLoading.value = false;
  }
}

function openWorkspaceDirectoryPicker() {
  directoryPickerOpen.value = true;
  void loadWorkspaceDirectories();
}

async function openServerWorkspacePicker() {
  if (!isSuperAdmin.value) return;
  serverWorkspacePickerOpen.value = true;
  serverWorkspacePickerLoading.value = true;
  serverWorkspaceDirectory.value = null;
  try {
    const servers = await api.listWorkspaceBackendServers();
    serverWorkspaceServers.value = servers;
    const preferred = servers.find((server) => server.sameAsAgent) ?? servers[0];
    selectedServerWorkspaceServerId.value = preferred?.linuxServerId;
    if (preferred) {
      await loadServerWorkspaceDirectories(preferred.defaultDirectory ?? undefined, preferred);
    }
  } catch (error) {
    feedback.value = errorFeedback("加载后端服务器失败", error);
  } finally {
    serverWorkspacePickerLoading.value = false;
  }
}

async function selectServerWorkspaceServer(server: WorkspaceBackendServer) {
  selectedServerWorkspaceServerId.value = server.linuxServerId;
  serverWorkspaceDirectory.value = null;
  await loadServerWorkspaceDirectories(server.defaultDirectory ?? undefined, server);
}

async function loadServerWorkspaceDirectories(path?: string, server = selectedServerWorkspaceServer()) {
  if (!server) return;
  serverWorkspacePickerLoading.value = true;
  try {
    serverWorkspaceDirectory.value = await api.listServerWorkspaceDirectories(server, path);
  } catch (error) {
    feedback.value = errorFeedback("加载服务器目录失败", error);
  } finally {
    serverWorkspacePickerLoading.value = false;
  }
}

function selectedServerWorkspaceServer() {
  return serverWorkspaceServers.value.find((server) => server.linuxServerId === selectedServerWorkspaceServerId.value);
}

async function switchWorkspace(workspace: Workspace) {
  resetWorkspaceState();
  cacheWorkspace(workspace);
  selectedWorkspaceId.value = workspace.workspaceId;
  // 切到运行态 Workspace 后，反查当前 workspace 来自哪个应用版本，驱动两级菜单的高亮项。
  syncCurrentVersionFromWorkspace(workspace);
  void queryClient.invalidateQueries({ queryKey: ["workspaces"] });
  void queryClient.invalidateQueries({ queryKey: ["sessions"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime"] });
  await loadDirectory("", workspace.workspaceId);
}

// 根据当前选中的 workspace 匹配出对应的应用版本（用于两级菜单高亮）。
// 优先使用「最近工作区」接口直接回写的 versionId（重新登录或换电脑登录时不需要等模板 versions 异步加载），
// 同时按需触发对应模板 versions 的预加载，确保 WorkbenchFooter.selectedTemplate 能找到匹配、按钮显示当前工作区。
// 回退到精确匹配运行时 Workspace ID 与根路径，用于 versionId 缺失的旧数据。
function syncCurrentVersionFromWorkspace(workspace: Workspace) {
  if (workspace.versionId) {
    currentVersionFromWorkspace.value = workspace.versionId;
    if (workspace.applicationWorkspaceId) {
      ensureAppVersionsLoaded(workspace.applicationWorkspaceId);
    }
    return;
  }
  const entries = Object.values(versionsByTemplateId.value);
  for (const list of entries) {
    const hit = list.find((version) =>
      version.runtimeWorkspace?.workspaceId === workspace.workspaceId ||
      version.workspaceRootPath === workspace.rootPath
    );
    if (hit) {
      currentVersionFromWorkspace.value = hit.versionId;
      return;
    }
  }
  // 没有匹配到时不主动清空：可能是用户刚切换应用、版本尚未加载完，避免菜单高亮闪烁。
}

// 切换到某个应用版本：通过 ensureDefaultPersonalWorkspace 确保用户拥有默认个人工作区，
// 将返回的 runtimeWorkspace 作为当前工作区。同一用户同一版本复用 default 空间，避免重复创建。
async function handleSelectVersion(payload: { template: ApplicationWorkspaceTemplate; version: ApplicationWorkspaceVersion }) {
  try {
    const defaultPw = await api.ensureDefaultPersonalWorkspace(payload.version.versionId);
    const runtimeWorkspaceId = defaultPw.runtimeWorkspace?.workspaceId;
    if (!runtimeWorkspaceId) {
      feedback.value = { kind: "error", title: "该版本未关联运行态工作区", description: "请先在平台侧初始化版本。" };
      return;
    }
    if (runtimeWorkspaceId === selectedWorkspaceId.value) {
      feedback.value = { kind: "info", title: "已在该版本工作区", description: `${payload.version.version} (个人空间: default)` };
      return;
    }
    // 记录当前个人工作区 ID，供 GitChangesPanel 提交/推送使用
    currentPersonalWorkspaceId.value = defaultPw.personalWorkspaceId;
    const workspace = await api.getWorkspace(runtimeWorkspaceId);
    await applyManagedWorkspace(workspace, {
      successTitle: "已切换应用版本",
      successDescription: `${payload.template.workspaceName} · ${payload.version.version} (个人空间: default)`
    });
  } catch (error) {
    feedback.value = errorFeedback("切换应用版本失败", error);
  }
}

// 统一"记录最近使用 + 切到运行态 Workspace"流程：先调 markRecentManagedWorkspace 让 user→app→workspace
// 持久化到 user_application_workspace_preferences / user_global_workspace_preferences，再切工作台。
// 后端在校验通过后会返回最新的 WorkspaceRuntimeResponse（已回填 appId/versionId/applicationWorkspaceId），
// 前端用这个响应回写 versionId/applicationWorkspaceId 到本次切到的工作区，确保重新登录或换电脑登录时
// 左下角"切换工作空间"按钮能立刻显示当前所在的应用版本与模板，而不必等模板 versions 异步加载完成。
// 非托管工作区（不属于任何应用）的 markRecent 会抛 NOT_FOUND，忽略该错误即可，不阻塞切换。
async function applyManagedWorkspace(workspace: Workspace, feedbackDetail?: { successTitle: string; successDescription: string }) {
  const appId = selectedAppId.value;
  let resolvedWorkspace = workspace;
  try {
    const response = await api.markRecentManagedWorkspace(workspace.workspaceId);
    if (response) {
      resolvedWorkspace = mergeRecentRuntimeResponse(workspace, response);
    }
  } catch (error) {
    if (error instanceof BackendApiError && error.code === "FORBIDDEN") {
      throw error;
    }
    // NOT_FOUND：工作区不属于任何应用（通常是手动目录注册出来的个人空间），不写入偏好。
    // 其他错误：网络/服务异常，吞掉但仍尝试切工作区，避免偏好写失败导致整个流程中断。
  }
  await switchWorkspace(resolvedWorkspace);
  if (feedbackDetail) {
    feedback.value = { kind: "info", title: feedbackDetail.successTitle, description: feedbackDetail.successDescription };
  }
  // 注意：原「切到运行态工作区后回查 (userId, appId, workspaceId) 维度的最近 VCS 分支偏好」
  // 逻辑（loadBranchPreferenceOnEnter）已随 footer 的「选择分支」/「记住当前分支」入口下线一起移除；
  // 分支信息仍由 runtimeStatus 从 vcs.status 拉取并展示在右侧 Agent 面板。
}

// 把 markRecentManagedWorkspace 响应里能反映"工作区隶属于哪个应用 / 版本 / 模板"的字段
// 回填到工作区对象；只覆盖非空字段，避免后端把旧值/异常值覆盖回前端已有的有效值。
function mergeRecentRuntimeResponse(workspace: Workspace, response: Workspace): Workspace {
  if (!response) return workspace;
  let merged: Workspace = workspace;
  if (response.appId && !merged.appId) merged = { ...merged, appId: response.appId };
  if (response.versionId && !merged.versionId) merged = { ...merged, versionId: response.versionId };
  if (response.applicationWorkspaceId && !merged.applicationWorkspaceId) {
    merged = { ...merged, applicationWorkspaceId: response.applicationWorkspaceId };
  }
  return merged;
}

// 查询指定应用下的"默认进入工作空间"：优先 recent 偏好，否则回退到首模板的首版本。
// 回退时通过 ensureDefaultPersonalWorkspace 创建/复用默认个人工作区，避免直接使用应用版本副本。
// 返回 { workspace, isFallback }：isFallback=true 表示走了"首模板首版本"的兜底，false 表示命中 recent。
// 两种情况最终都会通过 applyManagedWorkspace 写入 recent，下次进入直接命中该条偏好。
async function pickDefaultWorkspaceForApp(appId: string): Promise<{ workspace: Workspace; isFallback: boolean } | null> {
  const recent = await api.getRecentManagedWorkspaceForApplication(appId);
  if (recent) return { workspace: recent, isFallback: false };
  const templates = await api.listWorkspaceTemplates(appId);
  const firstTemplate = templates[0];
  if (!firstTemplate) return null;
  const versions = await api.listWorkspaceVersions(appId, firstTemplate.workspaceId);
  const firstVersion = versions[0];
  if (!firstVersion) return null;
  // 确保默认个人工作区存在（复用或创建），避免直接使用应用版本副本
  const defaultPw = await api.ensureDefaultPersonalWorkspace(firstVersion.versionId);
  currentPersonalWorkspaceId.value = defaultPw.personalWorkspaceId;
  if (!defaultPw.runtimeWorkspace?.workspaceId) return null;
  return { workspace: defaultPw.runtimeWorkspace, isFallback: true };
}

// WorkbenchFooter / FigmaFileExplorer 上两级菜单展开模板时调用，触发版本懒加载。
function handleLoadVersions(templateId: string) {
  ensureAppVersionsLoaded(templateId);
}

// 「+新增版本」流程：把 yyyyMMdd 和后端所需的 branch（非标准库）传给 createWorkspaceVersion。
// 成功后失效该模板下的版本查询，让 useQueries 重新拉取；同时把新版本切到工作区。
const creatingVersion = ref(false);
async function handleCreateVersion(payload: { template: ApplicationWorkspaceTemplate; version: string; branch?: string }) {
  const appId = selectedAppId.value;
  if (!appId) {
    feedback.value = { kind: "error", title: "未选择应用", description: "请先选择要新增版本的应用。" };
    return;
  }
  creatingVersion.value = true;
  try {
    const response = await api.createWorkspaceVersion(appId, payload.template.workspaceId, {
      version: payload.version,
      branch: payload.branch
    });
    // 失效版本查询：清掉缓存的 versionsByTemplateId 条目，并加入 loadedTemplateIds
    // 让 useQueries 在下一个 tick 重新发起 listWorkspaceVersions。
    const nextCache = { ...versionsByTemplateId.value };
    delete nextCache[payload.template.workspaceId];
    versionsByTemplateId.value = nextCache;
    if (!loadedTemplateIds.value.has(payload.template.workspaceId)) {
      const nextLoaded = new Set(loadedTemplateIds.value);
      nextLoaded.add(payload.template.workspaceId);
      loadedTemplateIds.value = nextLoaded;
    } else {
      // 已加载过：主动触发一次 invalidate 让 vue-query 重新拉取
      queryClient.invalidateQueries({
        queryKey: ["managed-workspace", "app-versions", selectedAppIdRef, payload.template.workspaceId]
      });
    }
    // 确保默认个人工作区存在，并切换到该个人工作区的运行态 workspace。
    const defaultPw = await api.ensureDefaultPersonalWorkspace(response.versionId);
    currentPersonalWorkspaceId.value = defaultPw.personalWorkspaceId;
    if (defaultPw.runtimeWorkspace?.workspaceId) {
      await applyManagedWorkspace(defaultPw.runtimeWorkspace, {
        successTitle: "已切换应用版本",
        successDescription: `${payload.template.workspaceName} · ${response.version}`
      });
    } else {
      feedback.value = {
        kind: "info",
        title: "新增版本成功",
        description: `${payload.template.workspaceName} · ${response.version}`
      };
    }
  } catch (error) {
    feedback.value = errorFeedback("新增版本失败", error);
  } finally {
    creatingVersion.value = false;
  }
}

async function handleSelectApp(appId: string) {
  // 切换应用时先清空旧 workspace 状态，避免文件树继续展示上一个应用的 workspace 内容
  resetWorkspaceState();
  selectedWorkspaceId.value = undefined;
  selectedAppId.value = appId;
  try {
    // 解析应用下的"默认工作空间"：
    //   1) 优先使用 user_application_workspace_preferences 中的 recent；
    //   2) 没有 recent 时回退到首模板的首版本，并把这条记录写入 recent，下次进入即可命中。
    // 两种情况都通过 applyManagedWorkspace 完成"写偏好 + 切工作区"两步，保证状态与持久化一致。
    const pick = await pickDefaultWorkspaceForApp(appId);
    if (pick) {
      await applyManagedWorkspace(pick.workspace);
      return;
    }
    // 应用下没有任何工作空间模板/版本，保持空态由用户手动选择。
  } catch (error) {
    feedback.value = errorFeedback("切换应用失败", error);
  }
}

async function selectWorkspaceDirectory(path: string) {
  directoryPickerLoading.value = true;
  try {
    const workspace =
      workspaces.value.find((item) => item.rootPath === path) ??
      (await api.createWorkspace({ name: workspaceNameFromPath(path), rootPath: path }));
    await switchWorkspace(workspace);
    directoryPickerOpen.value = false;
    directoryPickerData.value = null;
  } catch (error) {
    feedback.value = errorFeedback("切换 Workspace 失败", error);
  } finally {
    directoryPickerLoading.value = false;
  }
}

async function selectServerWorkspaceDirectory(payload: { server: WorkspaceBackendServer; path: string }) {
  serverWorkspacePickerLoading.value = true;
  try {
    const existing = workspaces.value.find(
      (item) => item.rootPath === payload.path && item.linuxServerId === payload.server.linuxServerId
    );
    const workspace =
      existing ??
      (await api.createServerWorkspace(payload.server, {
        name: workspaceNameFromPath(payload.path),
        rootPath: payload.path
      }));
    await switchWorkspace(workspace);
    serverWorkspacePickerOpen.value = false;
    serverWorkspaceDirectory.value = null;
  } catch (error) {
    feedback.value = errorFeedback("切换服务器 Workspace 失败", error);
  } finally {
    serverWorkspacePickerLoading.value = false;
  }
}

async function loadDirectory(
  path: string,
  workspaceId = selectedWorkspace.value?.workspaceId,
  force = false
) {
  if (!workspaceId) {
    return;
  }
  // 已被其他并发请求加载完成（或正在加载）就直接返回，避免重复请求与状态竞争。
  // 显式传 force=true（典型场景：用户点击文件树刷新按钮）时，即便已经加载也要重新拉取；
  // 但仍跳过正在加载中的请求，避免短时间内多次点击产生并发重复请求。
  if (loadingPath.value.has(path) || (!force && entriesByDirectory.value[path] !== undefined)) {
    return;
  }
  const nextLoading = new Set(loadingPath.value);
  nextLoading.add(path);
  loadingPath.value = nextLoading;
  try {
    const entries = filterWorkspaceRootEntries(path, await api.listFiles(workspaceId, path));
    entriesByDirectory.value = { ...entriesByDirectory.value, [path]: entries };
  } catch (error) {
    feedback.value = errorFeedback("加载文件树失败", error);
    // 加载失败：从展开集合里把这条目录回滚掉，让目录行恢复成可点击触发重试。
    // 注意：不删除 entriesByDirectory[path]——该 key 在失败前并未写入。
    if (expandedDirectories.value.has(path)) {
      const nextExpanded = new Set(expandedDirectories.value);
      nextExpanded.delete(path);
      expandedDirectories.value = nextExpanded;
    }
  } finally {
    const cleared = new Set(loadingPath.value);
    cleared.delete(path);
    loadingPath.value = cleared;
  }
}

// 处理文件搜索输入：防抖 250ms 后发起 workspace.search RPC。
// 用 searchSeq 丢弃过期请求结果（用户快速输入时只采纳最后一次的结果）。
function handleFileSearch(keyword: string) {
  searchKeyword.value = keyword;
  const trimmed = keyword.trim();
  if (!trimmed) {
    searchResults.value = [];
    searchLoading.value = false;
    if (searchTimer) {
      clearTimeout(searchTimer);
      searchTimer = null;
    }
    return;
  }
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchLoading.value = true;
  const seq = ++searchSeq;
  searchTimer = setTimeout(async () => {
    searchTimer = null;
    try {
      const results = await api.searchFiles(selectedWorkspace.value!.workspaceId, trimmed);
      // 丢弃过期请求的结果
      if (seq === searchSeq) {
        searchResults.value = results;
      }
    } catch (error) {
      if (seq === searchSeq) {
        searchResults.value = [];
        feedback.value = errorFeedback("搜索文件失败", error);
      }
    } finally {
      if (seq === searchSeq) {
        searchLoading.value = false;
      }
    }
  }, 250);
}

async function openFile(path: string) {
  if (!selectedWorkspace.value) {
    return;
  }
  centerMode.value = "editor";
  try {
    const file = await api.readFile(selectedWorkspace.value.workspaceId, path);
    workbench.openTab({
      id: `file:${path}`,
      path,
      title: path.split(/[\\/]+/).filter(Boolean).at(-1) ?? path,
      content: file.content,
      savedContent: file.content,
      readonly: file.readonly
    });
  } catch (error) {
    feedback.value = errorFeedback("读取文件失败", error);
  }
}

// 公共目录打开文件：把"public:<相对路径>"作为 tab.path，确保与工作区路径空间隔离。
// readonly 由 canWrite 反向决定，普通用户永远是只读，超级管理员可写。
async function openPublicFile(payload: { path: string; content: FileContent; readonly: boolean }) {
  centerMode.value = "editor";
  const tabPath = `${PUBLIC_FILE_PREFIX}${payload.path}`;
  workbench.openTab({
    id: `public:file:${payload.path}`,
    path: tabPath,
    title: payload.path.split(/[\\/]+/).filter(Boolean).at(-1) ?? payload.path,
    content: payload.content.content,
    savedContent: payload.content.content,
    readonly: payload.readonly
  });
}

async function openAgentFile(payload: { scope: "PUBLIC" | "WORKSPACE"; path: string; content: FileContent; readonly: boolean; worktreeId?: string | null; linuxServerId?: string | null }) {
  centerMode.value = "editor";
  const tabPath = agentTabPath(payload.scope, payload.path, payload.worktreeId, payload.linuxServerId);
  workbench.openTab({
    id: `${payload.scope.toLowerCase()}:agent:file:${payload.worktreeId ?? "direct"}:${payload.linuxServerId ?? "local"}:${payload.path}`,
    path: tabPath,
    title: payload.path.split(/[\\/]+/).filter(Boolean).at(-1) ?? payload.path,
    content: payload.content.content,
    savedContent: payload.content.content,
    readonly: payload.readonly
  });
}

function toggleDirectory(path: string) {
  // 同一目录正在加载时再次点击，会让 path 先被加入、再被移除，表现为"点击没反应"。
  // 这里直接吞掉二次点击，让加载指示（旋转图标）有足够时间呈现给用户。
  if (loadingPath.value.has(path)) {
    return;
  }
  const next = new Set(expandedDirectories.value);
  if (next.has(path)) {
    next.delete(path);
  } else {
    next.add(path);
    if (entriesByDirectory.value[path] === undefined) {
      void loadDirectory(path);
    }
  }
  expandedDirectories.value = next;
}

function handleSend(prompt: string, attachments: ComposerAttachment[] = []) {
  // 解析技能标记 __SKILL__<name>__PROMPT__<prompt>
  let skillPrompt: string | undefined
  const skillMatch = prompt.match(/^__SKILL__(.+?)__PROMPT__(.+)$/s)
  if (skillMatch) {
    skillPrompt = skillMatch[2].trim()
    // 保持原始 prompt 用于消息展示，skillPrompt 用于发给 AI
  }
  if (readonlySessionReason.value) {
    feedback.value = { kind: "info", title: "当前会话只读", description: readonlySessionReason.value };
    return;
  }
  if (!opencodeProcessReady.value) {
    // 与聊天面板状态卡一致：按 serviceStatus 区分“未分配 / 未运行”提示
    const svc =
      opencodeProcessStatus.value?.serviceStatus ??
      (opencodeProcessStatus.value?.status === "READY"
        ? "RUNNING"
        : opencodeProcessStatus.value?.serviceAddress?.trim() ||
          (opencodeProcessStatus.value?.linuxServerId && opencodeProcessStatus.value?.port)
          ? "NOT_RUNNING"
          : "UNASSIGNED");
    const procTitle =
      svc === "NOT_RUNNING"
        ? "opencode 专属进程未运行"
        : svc === "UNASSIGNED"
        ? "尚未分配 opencode 专属进程"
        : "请先初始化 opencode 进程";
    feedback.value = {
      kind: "info",
      title: procTitle,
      description: opencodeProcessStatus.value?.message ?? "正在检查当前用户可用进程"
    };
    return;
  }
  if (!selectedWorkspace.value) {
    feedback.value = { kind: "info", title: "未选择工作区", description: "请先点击\"选择本机目录\"或切换到可用工作区，再发送任务。" };
    return;
  }
  const parts = buildPromptParts(prompt, activeTab.value, attachments, diffContextParts.value, editorSelection.value);
  const displayPrompt = prompt.trim() || promptFromParts(parts);
  lastPrompt.value = displayPrompt;
  diffContextParts.value = [];
  dispatchChat({ type: "user.submitted", prompt: displayPrompt, parts });
  if (!displayPrompt) {
    return;
  }
  // 启动计时 + 重置任务消耗累计（lastDuration/lastTokens/lastThoughtForMs 保留上一轮终态以供刷新对比）
  chatStartedAt.value = Date.now();
  accumulatedTokens.value = 0;
  accumulatedReasoningMs.value = 0;
  const command = parseCommand(prompt, promptMode.value);
  if (runtimeBusy.value) {
    followUpQueue.value = enqueueFollowUp(followUpQueue.value, createFollowUpDraft(displayPrompt, parts, undefined, command ?? undefined));
    feedback.value = { kind: "info", title: "Prompt 已排队", description: `等待当前 Run 完成后继续执行，队列 ${followUpQueue.value.length} 条` };
    return;
  }
  const aiPrompt = skillPrompt ?? displayPrompt
  if (command && session.value) {
    commandMutation.mutate({ ...command, prompt: aiPrompt });
    return;
  }
  startRunMutation.mutate({ prompt: aiPrompt, parts, title: displayPrompt });
}

function handleStopRun() {
  if (!run.value || !isRunBusyStatus(run.value.status)) {
    feedback.value = { kind: "info", title: "当前没有可终止的运行", description: "运行启动成功并返回 Run ID 后才能终止。" };
    return;
  }
  cancelRunMutation.mutate();
  if (chatStartedAt.value) {
    totalDurationMs.value += Date.now() - chatStartedAt.value;
    lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
    chatStartedAt.value = null;
    // 触发 taskUsage 重新计算（duration 从 live 切到 last）
    nowTick.value = Date.now();
  }
}

function handleRunEvent(event: RunEvent) {
  logs.value = [...logs.value.slice(-200), `[${event.seq}] ${event.type}`];
  dispatchChat({ type: "event", event });
  notifyOnAttention(event, selectedWorkspace.value, session.value);
  if (event.type === "assistant.message.delta") {
    return;
  } else if (event.type === "diff.proposed") {
    // opencode session.diff 与后端自生成的 diff.proposed 都映射为该事件类型。
    // - edit/apply_patch 工具的 diff.proposed payload.files 是本次刚编辑的文件对象数组；
    // - opencode session.diff payload.files 是 path 字符串数组。
    // 两种格式都按 path 累加去重，避免后到的单文件事件把前面已累加的多个文件覆盖。
    // 归一化路径后再合并：opencode 部分场景会把 file 写成 "a/src/App.vue" 或带盘符的
    // 绝对路径，与 inferDiffFromToolPart 推断出来的相对路径必须落到同一个 key。
    const files = diffFilesFromPayload(event.payload).map((f) => ({
      ...f,
      path: normalizeWorkspacePath(f.path) || f.path
    }));
    if (files.length) {
      diffSource.value = "run";
      diffFiles.value = mergeDiffFiles(diffFiles.value, files);
      workbench.setSelectedDiffPath(files[0]?.path);
    }
    // 注意：不再回退到 api.getRunDiff——该接口返回的是最近一个 diff.proposed 事件的 files，
    // 触发后会把当前已累加的多文件集合覆盖成单文件，导致"X 个文件已更改"提示从 3 变回 1。
  } else if (event.type === "session.diff") {
    // 历史事件类型。当前 OpencodeRunEventMapper 已将 session.diff 映射为 diff.proposed，
    // 这里保留以兼容后端直接转发该类型事件的场景。
    const files = diffFilesFromPayload(event.payload).map((f) => ({
      ...f,
      path: normalizeWorkspacePath(f.path) || f.path
    }));
    if (files.length) {
      diffSource.value = "session";
      diffFiles.value = mergeDiffFiles(diffFiles.value, files);
      workbench.setSelectedDiffPath(files[0]?.path);
    }
  } else if (event.type === "run.succeeded" || event.type === "run.failed" || event.type === "run.cancelled") {
    run.value = run.value
      ? { ...run.value, status: event.type === "run.succeeded" ? "SUCCEEDED" : event.type === "run.failed" ? "FAILED" : "CANCELLED" }
      : run.value;
    // Run 完成后刷新所有已展开的目录，确保新增/删除/修改的文件在左侧工作区立即可见。
    setTimeout(() => {
      loadDirectory("", undefined, true);
      for (const dir of expandedDirectories.value) {
        loadDirectory(dir, undefined, true);
      }
    }, 500);
    // 计算任务消耗统计：duration 由 chatStartedAt 锁定，tokens/thoughtFor 仍优先取累计值；
    // 如果后端 payload 直接带上 tokens 或 thoughtFor 字段，则覆盖一次（向后兼容未来后端实现）。
    if (chatStartedAt.value) {
      totalDurationMs.value += Date.now() - chatStartedAt.value;
      lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
      chatStartedAt.value = null;
    }
    const payload = event.payload as Record<string, unknown>;
    if (typeof payload.tokens === "number") {
      lastTokens = payload.tokens;
    }
    if (typeof payload.thoughtFor === "string") {
      // 兼容未来后端直接以 "1s"/"100ms" 等字符串上报
      lastThoughtForMs = parseDurationStringToMs(payload.thoughtFor);
    }
    // 触发 taskUsage 重新计算
    nowTick.value = Date.now();
  }
}

// agent 写文件用的 opencode 工具名；这些工具的 input 带文件路径，完成时磁盘已写入。
const LIVE_WRITE_TOOLS = new Set(["write", "edit", "apply_patch", "str_replace", "multi_edit", "create_file", "delete"]);

// 把绝对路径或带 git 前缀的路径归一化为 workspace 相对路径（统一使用 / 分隔符）。
// - 去掉 git diff 前缀 "a/" / "b/"
// - 把 Windows 反斜杠折叠成正斜杠，让 D:\workspace\vue\src\App.vue 与
//   D:/workspace/vue/src/App.vue 走同一条剥离分支
// - 去掉 workspace 根路径（兼容根路径带不带尾斜杠）
// - 折叠前导 ./ 与重复斜杠
function normalizeWorkspacePath(raw: string): string {
  const rootPath = selectedWorkspace.value?.rootPath ?? "";
  let p = raw.replace(/^([ab])\//, "").replace(/\\/g, "/");
  const normalizedRoot = rootPath.replace(/\\/g, "/").replace(/\/+$/, "");
  if (normalizedRoot) {
    if (p === normalizedRoot) {
      p = "";
    } else if (p.startsWith(`${normalizedRoot}/`)) {
      p = p.slice(normalizedRoot.length + 1);
    }
  }
  while (p.startsWith("./")) p = p.slice(2);
  p = p.replace(/\/+$/, "");
  p = p.replace(/\/+/g, "/");
  return p;
}

// 从 tool part 的 input 提取文件路径，并归一化为 workspace 相对路径。
// write/edit 通常用 input.filePath；apply_patch 还可能把文件列表放在 metadata.files。
function liveToolPath(part: Extract<MessagePart, { type: "tool" }>): string | undefined {
  const input = (part.input ?? {}) as Record<string, unknown>;
  const metadata = (part.metadata ?? {}) as Record<string, unknown>;

  const direct =
    text(input.filePath) ??
    text(input.path) ??
    text(input.file) ??
    text(metadata.filepath) ??
    text(metadata.filePath) ??
    text(metadata.path) ??
    text(metadata.file);
  if (direct) {
    return normalizeWorkspacePath(direct);
  }
  const metadataFiles = Array.isArray(metadata.files) ? metadata.files : [];
  for (const file of metadataFiles) {
    const item = recordValue(file);
    const filePath = item
      ? text(item.relativePath) ?? text(item.filePath) ?? text(item.path) ?? text(item.file) ?? text(item.movePath)
      : undefined;
    if (filePath) {
      return normalizeWorkspacePath(filePath);
    }
  }
  // apply_patch：patch 文本取第一个文件路径，兼容 input/patch/patchText 三种字段。
  const patchText = text(input.input) ?? text(input.patch) ?? text(input.patchText);
  if (patchText) {
    const gitMatch = /^diff --git a\/(.+?) b\//m.exec(patchText);
    if (gitMatch) {
      return normalizeWorkspacePath(gitMatch[1]);
    }
    const plusMatch = /^\+\+\+ (?:b\/)?(.+)$/m.exec(patchText);
    if (plusMatch) {
      return normalizeWorkspacePath(plusMatch[1]);
    }
  }
  return undefined;
}

function recordValue(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

// 读取磁盘最新内容并以只读实时预览 tab 打开在中间编辑器，并展开文件树到该文件。
// 同时刷新文件所在父目录的条目，让 agent 新建的文件能立刻出现在文件树中
// （opencode 写文件工具落盘后不会发 diff.proposed，前端必须主动 re-list）。
async function openLivePreview(relPath: string) {
  if (!selectedWorkspace.value) {
    return;
  }
  expandPathToFile(relPath);
  refreshParentDirectory(relPath);
  const existing = tabs.value.find((tab: EditorTab) => tab.path === relPath);
  if (existing && !existing.livePreview && existing.content !== existing.savedContent) {
    workbench.setActivePath(relPath);
    centerMode.value = "editor";
    feedback.value = { kind: "info", title: "实时追踪未覆盖未保存文件", description: relPath };
    return;
  }
  try {
    const file = await api.readFile(selectedWorkspace.value.workspaceId, relPath);
    workbench.openTab({
      id: `live:${relPath}`,
      path: relPath,
      title: relPath.split(/[\\/]+/).filter(Boolean).at(-1) ?? relPath,
      content: file.content,
      savedContent: file.content,
      readonly: true,
      livePreview: true
    });
    centerMode.value = "editor";
  } catch (error) {
    feedback.value = errorFeedback("实时追踪读取文件失败", error);
  }
}

// 把文件所在父目录重新拉取一次，让新建/删除的文件即时出现在文件树中。
// 不论父目录是否已加载过，都走 force=true 强制重新拉取：未加载时由 `loadDirectory` 直接发起
// 一次拉取，已加载时则覆盖旧条目；正在加载中的请求由 `loadDirectory` 内部的 `loadingPath`
// 守卫去重，避免对同一目录堆积并发请求。
// 注意：父目录的"加入 expandedDirectories"动作由 `expandPathToFile` 负责，
// `refreshParentDirectory` 单纯负责把磁盘最新状态拉回前端。
function refreshParentDirectory(relPath: string) {
  if (!relPath || relPath.startsWith("/")) {
    return;
  }
  const segments = relPath.split("/").filter(Boolean);
  if (segments.length <= 1) {
    void loadDirectory("", undefined, true);
    return;
  }
  const parentPath = segments.slice(0, -1).join("/");
  void loadDirectory(parentPath, undefined, true);
}

// 展开文件树到目标文件：把所有祖先目录加入 expandedDirectories 并按需懒加载。
function expandPathToFile(relPath: string) {
  if (!relPath || relPath.startsWith("/")) {
    return;
  }
  const segments = relPath.split("/").filter(Boolean);
  if (segments.length <= 1) {
    return;
  }
  const next = new Set(expandedDirectories.value);
  let acc = "";
  for (let i = 0; i < segments.length - 1; i += 1) {
    acc = acc ? `${acc}/${segments[i]}` : segments[i];
    next.add(acc);
    if (!entriesByDirectory.value[acc]) {
      void loadDirectory(acc);
    }
  }
  expandedDirectories.value = next;
}

// 从 tool card 消息的 payload 提取工具名，兼容多种字段名。
function toolNameFromPayload(payload: Record<string, unknown>): string | undefined {
  const name = payload.toolName ?? payload.tool ?? payload.name;
  return typeof name === "string" && name.length > 0 ? name : undefined;
}

// 从 tool card 消息的 payload 提取调用标识，用于去重。
function toolCardPartId(message: Extract<AgentMessage, { role: "card" }>): string {
  const payload = message.payload;
  const id = payload.callId ?? payload.callID ?? payload.partId ?? payload.partID ?? payload.rawEventId;
  return typeof id === "string" && id.length > 0 ? id : message.id;
}

// 从 tool card 消息的 payload 构造虚拟 ToolPart，供 liveToolPath 复用路径提取逻辑。
function toolCardToVirtualPart(message: Extract<AgentMessage, { role: "card" }>): Extract<MessagePart, { type: "tool" }> | null {
  const payload = message.payload;
  const toolName = toolNameFromPayload(payload);
  if (!toolName) {
    return null;
  }
  return {
    partId: toolCardPartId(message),
    type: "tool",
    toolName,
    status: typeof payload.status === "string" ? payload.status : "completed",
    input: typeof payload.input === "object" && payload.input !== null ? (payload.input as Record<string, unknown>) : undefined,
    metadata: typeof payload.metadata === "object" && payload.metadata !== null ? (payload.metadata as Record<string, unknown>) : undefined
  };
}

// 扫描对话中的 tool part：新完成的写文件工具 → 推断 diff 增量、刷新文件树、视情况打开预览。
// 同时处理 assistant message 的 parts 中的 tool part 和独立的 tool card 消息。
// diffFiles 与文件树刷新与 liveTrack 解耦，保证"文件变更卡片 +N"和"做测目录即时刷新"在
// 实时追踪未开启时也能正常工作。
function scanLiveToolParts() {
  for (const message of chatState.value.messages) {
    // 处理 assistant message 的 parts
    if (message.role === "assistant") {
      for (const part of message.parts ?? []) {
        if (part.type !== "tool" || part.status !== "completed") {
          continue;
        }
        if (!LIVE_WRITE_TOOLS.has(part.toolName)) {
          continue;
        }
        if (liveFollowedParts.value.has(part.partId)) {
          continue;
        }
        const path = liveToolPath(part);
        if (!path) {
          continue;
        }
        liveFollowedParts.value.add(part.partId);
        applyToolChangeToDiff(part, path);
        if (liveTrack.value) {
          void openLivePreview(path);
        } else {
          // 即使不开实时预览，也要展开文件树并刷新父目录，让用户看到新文件。
          if (part.toolName !== "delete") {
            expandPathToFile(path);
          }
          refreshParentDirectory(path);
        }
      }
      continue;
    }
    // 处理独立的 tool card 消息（由 tool.finished 事件生成）
    if (message.role === "card" && message.cardType === "tool") {
      const part = toolCardToVirtualPart(message);
      if (!part || part.status !== "completed") {
        continue;
      }
      if (!LIVE_WRITE_TOOLS.has(part.toolName)) {
        continue;
      }
      if (liveFollowedParts.value.has(part.partId)) {
        continue;
      }
      const path = liveToolPath(part);
      if (!path) {
        continue;
      }
      liveFollowedParts.value.add(part.partId);
      applyToolChangeToDiff(part, path);
      if (liveTrack.value) {
        void openLivePreview(path);
      } else {
        // 即使不开实时预览，也要展开文件树并刷新父目录，让用户看到新文件。
        // 与 assistant 分支保持一致：card 事件可能不经过 assistant message，
        // 必须在这里也展开祖先目录才能让新增文件立刻可见。
        expandPathToFile(path);
        refreshParentDirectory(path);
      }
    }
  }
}

// 把一次写文件工具的产出合并到 diffFiles，让"文件变更"卡片在写盘后即时显示 +N。
// 路径先归一化为 workspace 相对路径，与 diff.proposed 事件中的 path 形态保持一致。
function applyToolChangeToDiff(part: Extract<MessagePart, { type: "tool" }>, rawPath: string) {
  const inferred = inferDiffFromToolPart(part);
  if (!inferred) {
    return;
  }
  const relPath = normalizeWorkspacePath(inferred.path) || normalizeWorkspacePath(rawPath) || inferred.path;
  if (!relPath || relPath.startsWith("/")) {
    return;
  }
  diffFiles.value = mergeDiffFiles(diffFiles.value, [{ ...inferred, path: relPath }]);
  workbench.setSelectedDiffPath(relPath);
}

async function loadDiffSource(source: "run" | "session" | "vcs" | "agent") {
  diffSource.value = source;
  centerMode.value = "diff";
  try {
    let nextFiles: RunDiffFile[] = [];
    if (workbench.useMockTestData) {
      if (source === "vcs") {
        nextFiles = JSON.parse(JSON.stringify(mockVcsDiffFiles));
      } else if (source === "agent") {
        const mappedPub = mockPublicAgentDiffs.map((f) => ({
          path: f.path,
          status: f.status,
          additions: 0,
          deletions: 0,
          patch: f.patch
        }));
        const mappedWks = mockWorkspaceAgentDiffs.map((f) => ({
          path: f.path,
          status: f.status,
          additions: 0,
          deletions: 0,
          patch: f.patch
        }));
        nextFiles = [...mappedPub, ...mappedWks];
      }
      diffFiles.value = nextFiles;
      if (!workbench.selectedDiffPath || !nextFiles.some((f) => f.path === workbench.selectedDiffPath)) {
        workbench.setSelectedDiffPath(nextFiles[0]?.path);
      }
      return;
    }

    if (source === "run") {
      nextFiles = run.value ? (await api.getRunDiff(run.value.runId)).files : [];
    } else if (source === "session") {
      nextFiles = session.value ? (await api.getSessionDiff(session.value.sessionId)).files : [];
    } else if (source === "vcs") {
      nextFiles = selectedWorkspace.value ? (await api.getVcsDiffFiles(selectedWorkspace.value.workspaceId)).files : [];
    } else if (source === "agent") {
      const pubDiff = await api.getPublicAgentDiff(workbench.publicWorktree?.worktreeId).catch(() => ({ files: [] }));
      const mappedPub = pubDiff.files.map((f) => ({
        path: f.path,
        status: f.status,
        additions: 0,
        deletions: 0,
        patch: f.patch
      }));

      let mappedWks: RunDiffFile[] = [];
      if (selectedWorkspace.value) {
        const wksDiff = await api.getWorkspaceAgentDiff(selectedWorkspace.value.workspaceId, workbench.workspaceWorktree?.worktreeId).catch(() => ({ files: [] }));
        mappedWks = wksDiff.files.map((f) => ({
          path: f.path,
          status: f.status,
          additions: 0,
          deletions: 0,
          patch: f.patch
        }));
      }
      nextFiles = [...mappedPub, ...mappedWks];
    }
    diffFiles.value = nextFiles;
    if (!workbench.selectedDiffPath || !nextFiles.some((f) => f.path === workbench.selectedDiffPath)) {
      workbench.setSelectedDiffPath(nextFiles[0]?.path);
    }
  } catch (error) {
    feedback.value = errorFeedback("加载 Diff 失败", error);
  }
}

async function handleOpenDiff(payload: string | { path: string; source: "vcs" | "agent"; scope?: "PUBLIC" | "WORKSPACE" }) {
  if (typeof payload === "string") {
    await loadDiffSource("vcs");
    workbench.setSelectedDiffPath(payload);
  } else {
    if (payload.source === "vcs") {
      await loadDiffSource("vcs");
      workbench.setSelectedDiffPath(payload.path);
    } else {
      await loadDiffSource("agent");
      workbench.setSelectedDiffPath(payload.path);
    }
  }
}

function generateEntireFilePatch(path: string, original: string, modified: string): string {
  const originalLines = original.split("\n");
  const modifiedLines = modified.split("\n");
  let patch = `--- a/${path}\n+++ b/${path}\n@@ -1,${originalLines.length} +1,${modifiedLines.length} @@\n`;
  for (const line of originalLines) {
    patch += `-${line}\n`;
  }
  for (const line of modifiedLines) {
    patch += `+${line}\n`;
  }
  return patch;
}

const saveDiffFileMutation = useMutation({
  mutationFn: async ({ path, content }: { path: string; content: string }) => {
    if (workbench.useMockTestData) {
      const vcsFile = mockVcsDiffFiles.find((f) => f.path === path);
      if (vcsFile) {
        const parsedOld = parseUnifiedPatch(vcsFile.patch);
        vcsFile.patch = generateEntireFilePatch(path, parsedOld.original, content);
      }
      const pubAgentFile = mockPublicAgentDiffs.find((f) => f.path === path);
      if (pubAgentFile) {
        const parsedOld = parseUnifiedPatch(pubAgentFile.patch);
        pubAgentFile.patch = generateEntireFilePatch(path, parsedOld.original, content);
      }
      const wksAgentFile = mockWorkspaceAgentDiffs.find((f) => f.path === path);
      if (wksAgentFile) {
        const parsedOld = parseUnifiedPatch(wksAgentFile.patch);
        wksAgentFile.patch = generateEntireFilePatch(path, parsedOld.original, content);
      }
      return { path, content };
    }

    if (isAgentFilePath(path)) {
      const agent = agentFileInfo(path);
      if (agent.scope === "PUBLIC") {
        await api.writePublicAgentFile(agent.path, content, agent.worktreeId, agent.linuxServerId);
      } else {
        if (!selectedWorkspace.value) {
          throw new Error("未选择 Workspace");
        }
        await api.writeWorkspaceAgentFile(selectedWorkspace.value.workspaceId, agent.path, content, agent.worktreeId);
      }
      return { path, content };
    }
    if (isPublicFilePath(path)) {
      await api.writePublicFile(publicFilePath(path), content);
      return { path, content };
    }
    if (!selectedWorkspace.value) {
      throw new Error("未选择 Workspace");
    }
    await api.writeFile(selectedWorkspace.value.workspaceId, path, content);
    return { path, content };
  },
  onSuccess: async ({ path, content }) => {
    const tab = workbench.tabs.find((t: EditorTab) => t.path === path);
    if (tab) {
      workbench.markTabSaved(path, content);
    }
    feedback.value = { kind: "success", title: "文件已保存", description: path };
    await loadDiffSource(diffSource.value);
  },
  onError: (error) => {
    feedback.value = errorFeedback("保存文件失败", error);
  }
});

function handleSaveDiffFile(path: string, content: string) {
  saveDiffFileMutation.mutate({ path, content });
}

async function switchSession(sessionId: string) {
  const selected = sessionsQuery.data.value?.items.find((item) => item.sessionId === sessionId) ?? (await api.getSession(sessionId));
  let readonlyReason = "";
  if (selected.workspaceId !== selectedWorkspaceIdRef.value) {
    try {
      let workspace = await api.getWorkspace(selected.workspaceId);
      try {
        // markRecentManagedWorkspace 会同时回写 versionId/applicationWorkspaceId，
        // 这里把响应合并到工作区上，确保会话切换后左下角按钮也能定位到当前版本。
        const response = await api.markRecentManagedWorkspace(selected.workspaceId);
        if (response) {
          workspace = mergeRecentRuntimeResponse(workspace, response);
        }
      } catch (error) {
        if (error instanceof BackendApiError && error.code === "FORBIDDEN") {
          throw error;
        }
      }
      await switchWorkspace(workspace);
    } catch (error) {
      readonlyReason = "当前会话绑定的工作区不可用或无权限，已切换为只读。";
      feedback.value = errorFeedback("切换 Session 工作区失败", error);
    }
  }
  session.value = selected;
  readonlySessionReason.value = readonlyReason;
  try {
    const page = await api.listSessionMessages(sessionId, 1, 100);
    dispatchChat({ type: "reset", messages: messagesFromSessionMessages(page.items) });
    await loadFeedbacksForMessages(page.items);
    const restoredFiles = diffFilesFromSessionMessages(page.items).map((file) => ({
      ...file,
      path: normalizeWorkspacePath(file.path) || file.path
    }));
    diffFiles.value = restoredFiles;

    // 寻找最新的 runId 从而恢复 Run 状态与文件 Diff
    const lastMsgWithRunId = [...page.items].reverse().find((m) => m.runId);
    if (lastMsgWithRunId?.runId) {
      try {
        const [runDetail, diffDetail] = await Promise.all([
          api.getRun(lastMsgWithRunId.runId),
          api.getRunDiff(lastMsgWithRunId.runId).catch(() => ({ files: [] }))
        ]);
        run.value = runDetail;
        const runFiles = (diffDetail.files ?? []).map((file) => ({
          ...file,
          path: normalizeWorkspacePath(file.path) || file.path
        }));
        diffFiles.value = mergeDiffFiles(restoredFiles, runFiles);
      } catch (runErr) {
        console.error("加载关联 Run 失败", runErr);
      }
    } else {
      run.value = null;
    }

    feedback.value = { kind: "info", title: "已切换 Session", description: selected.title };
  } catch (error) {
    feedback.value = errorFeedback("加载 Session 消息失败", error);
  }
}

function handleNewConversation() {
  session.value = null;
  run.value = null;
  dispatchChat({ type: "reset" });
  messageFeedbacks.value = {};
  feedbackSubmitting.value = {};
  readonlySessionReason.value = "";
  diffFiles.value = [];
}

async function loadFeedbacksForMessages(messages: Array<{ messageId?: string; role?: string }>) {
  const assistantMessageIds = messages
    .filter(message => message.role === "ASSISTANT" && message.messageId?.startsWith("msg_"))
    .map(message => message.messageId!)
  const uniqueIds = [...new Set(assistantMessageIds)];
  const loaded: Record<string, AiMessageFeedback | null> = {};
  await Promise.all(uniqueIds.map(async messageId => {
    try {
      loaded[messageId] = await api.getMyMessageFeedback(messageId);
    } catch {
      loaded[messageId] = null;
    }
  }));
  messageFeedbacks.value = loaded;
}

function handleSubmitFeedback(payload: AiMessageFeedbackPayload & { messageId: string }) {
  submitMessageFeedbackMutation.mutate(payload);
}

function onCurrentFileFeedback(action: "accept-current" | "reject-current", path: string) {
  feedback.value = {
    kind: "info",
    title: action === "accept-current" ? "已选中当前文件接受意图" : "已选中当前文件拒绝意图",
    description: `${path} 当前版本只支持 Run 级提交`
  };
}

function onUseHunkContext(part: Extract<PromptPart, { type: "file" }>) {
  diffContextParts.value = [...diffContextParts.value, part];
  feedback.value = { kind: "info", title: "已引用当前 hunk", description: `${part.path ?? part.name} 将随下一条 Prompt 提交` };
}

async function handleLogout() {
  authStore.logout(api);
  await router.push({ name: "login" });
}
</script>

<template>
  <FigmaShell
    :workspace-name="selectedWorkspace?.name"
    :bottom-open="bottomDrawerOpen"
    :show-left-panel="leftPanelOpen"
    :show-right-panel="rightPanelOpen"
    :apps="shellApps"
    :selected-app-id="selectedAppId"
    :current-user-name="authStore.currentUser?.username"
    :current-user-role-labels="authStore.currentUser?.roleLabels"
    :opencode-process-status="opencodeProcessStatus"
    :opencode-process-loading="opencodeProcessInitialLoading"
    @toggle-left-panel="leftPanelOpen = !leftPanelOpen"
    @toggle-right-panel="rightPanelOpen = !rightPanelOpen"
    @select-app="handleSelectApp"
    @refresh-opencode-process="refreshOpencodeProcessStatus"
    @logout="handleLogout"
  >
    <template #activity>
      <nav class="figma-activity-nav" aria-label="工作台活动栏">
        <div class="figma-activity-top">
          <button
            type="button"
            :class="['figma-activity-btn', centerMode === 'editor' && 'figma-activity-btn--active']"
            aria-label="打开编辑器"
            title="打开编辑器"
            @click="centerMode = 'editor'"
          >
            <Code2 class="figma-activity-icon" :stroke-width="1.5" />
          </button>
          <button
            v-if="isSuperAdmin"
            type="button"
            :class="['figma-activity-btn', centerMode === 'system' && 'figma-activity-btn--active']"
            aria-label="系统管理"
            title="系统管理"
            @click="
              centerMode = 'system';
              bottomDrawerOpen = false;
            "
          >
            <Monitor class="figma-activity-icon" :stroke-width="1.5" />
          </button>
        </div>
        <div class="figma-activity-bottom">
          <button
            type="button"
            :class="['figma-activity-btn', settingsOpen && 'figma-activity-btn--active']"
            aria-label="系统设置"
            title="系统设置"
            @click="settingsOpen = true"
          >
            <ElSetting class="figma-activity-icon" />
          </button>
        </div>
      </nav>
    </template>

    <template #files>
      <div v-if="selectedManagedApplication || selectedWorkspace" class="managed-workspace-layout">
        <FigmaFileExplorer
          v-if="selectedWorkspace"
          class="managed-workspace-files"
          :workspace-name="selectedWorkspace.name"
          :workspace-root-path="selectedWorkspace.rootPath"
          :entries-by-directory="entriesByDirectory"
          :expanded-directories="expandedDirectories"
          :active-path="activePath"
          :changed-files="diffFiles"
          :loading-path="loadingPath"
          :app-name="selectedManagedApplication?.appName"
          :app-templates="appTemplatesWithVersions"
          :selected-version-id="selectedVersionId"
          :loading-app-templates="loadingAppTemplates"
          :loading-app-versions="loadingAppVersions"
          :creating-version="creatingVersion"
          :public-directory-writable="isSuperAdmin"
          :api-base-url="apiBaseUrl"
          :workspace-id="selectedWorkspace.workspaceId"
          :personal-workspace-id="currentPersonalWorkspaceId"
          :show-server-workspace-switch="isSuperAdmin"
          :search-results="searchResults"
          :search-loading="searchLoading"
          :search-keyword="searchKeyword"
          @toggle-directory="toggleDirectory"
          @open-file="openFile"
          @open-diff="handleOpenDiff"
          @refresh="loadDirectory('', undefined, true)"
          @select-version="handleSelectVersion"
          @load-versions="handleLoadVersions"
          @create-version="handleCreateVersion"
          @open-public-file="openPublicFile"
          @open-agent-file="openAgentFile"
          @open-server-workspace-picker="openServerWorkspacePicker"
          @search="handleFileSearch"
        />
        <div v-else class="managed-workspace-empty">
          <p>当前应用尚未切换到可用工作区。</p>
          <button type="button" class="managed-workspace-button" @click="openWorkspaceDirectoryPicker">选择本机目录</button>
        </div>
      </div>
      <WorkspaceBootstrap
        v-else
        :loading="createWorkspaceMutation.isPending.value"
        @create="(payload) => createWorkspaceMutation.mutate(payload)"
      />
    </template>

    <template #editor>
      <main class="managed-editor-main">
        <template v-if="centerMode === 'diff'">
          <div class="flex-1 min-h-0 min-w-0">
            <DiffViewer
              ref="diffViewerRef"
              :files="diffFiles"
              :selected-path="selectedDiffPath"
              :source="diffSource"
              :view-mode="diffViewMode"
              :accepting="acceptDiffMutation.isPending.value"
              :rejecting="rejectDiffMutation.isPending.value"
              :feedback="feedback"
              @select-file="(path: string) => workbench.setSelectedDiffPath(path)"
              @source-change="(source: 'run' | 'session' | 'vcs' | 'agent') => loadDiffSource(source)"
              @view-mode-change="(mode: 'split' | 'unified') => (diffViewMode = mode)"
              @refresh="loadDiffSource(diffSource)"
              @accept-run="acceptDiffMutation.mutate()"
              @reject-run="rejectDiffMutation.mutate()"
              @current-file-feedback="onCurrentFileFeedback"
              @use-hunk-context="onUseHunkContext"
              @save-file="handleSaveDiffFile"
              @dirty-change="(dirty: boolean) => (isDiffDirty = dirty)"
            />
          </div>
          <WorkbenchFooter
            :write-path="selectedDiffPath"
            :dirty="isDiffDirty"
            :saving="saveDiffFileMutation.isPending.value"
            show-save
            @save="() => diffViewerRef?.handleSave()"
          />
        </template>
        <template v-else-if="centerMode === 'system'">
          <div class="managed-runtime-container">
            <SystemManagementWrapper :current-user="authStore.currentUser" />
          </div>
          <WorkbenchFooter />
        </template>
        <FigmaEditorArea
          v-else
          :tabs="tabs"
          :active-path="activePath"
          :breadcrumb-path="breadcrumbDisplay"
          :write-path="activeTab?.path"
          :updated-at="activeTab ? Date.now() / 1000 : undefined"
          :dirty="!!activeTab && !activeTab.livePreview && activeTab.content !== activeTab.savedContent"
          :readonly="!!activeTab?.readonly"
          :saving="saveMutation.isPending.value"
          :markdown-preview="markdownPreview"
          @activate="(path: string) => workbench.setActivePath(path)"
          @close="(path: string) => workbench.closeTab(path)"
          @editor-action="() => {}"
          @save="() => activeTab && !activeTab.livePreview && saveMutation.mutate(activeTab)"
          @update:markdown-preview="(value: boolean) => (markdownPreview = value)"
        >
          <CodeEditor
            :path="activeTab?.path"
            :content="activeTab?.content"
            :dirty="activeTab && !activeTab.livePreview ? activeTab.content !== activeTab.savedContent : false"
            :readonly="activeTab?.readonly"
            :saving="saveMutation.isPending.value"
            :feedback="feedback"
            :show-preview="markdownPreview"
            @change="(content: string) => activeTab && workbench.updateTabContent(activeTab.path, content)"
            @save="() => activeTab && !activeTab.livePreview && saveMutation.mutate(activeTab)"
            @selection-change="(selection: EditorSelectionContext | undefined) => (editorSelection = selection)"
          />
        </FigmaEditorArea>
      </main>
    </template>

    <template #chat>
      <div class="managed-chat-panel">
        <FigmaChatPanel
          :messages="chatState.messages"
          :running="runtimeBusy"
          :title="chatTitle"
          :file-changes="diffFiles"
          :task-usage="taskUsage"
          :history="historyList"
          :readonly-reason="readonlySessionReason"
          :process-status="opencodeProcessStatus"
          process-required
          :process-loading="opencodeProcessInitialLoading"
          :process-refreshing="opencodeProcessRefreshing"
          :process-initializing="initializeOpencodeProcessMutation.isPending.value"
          :permissions="chatState.permissions"
          :questions="chatState.questions"
          :selected-model-label="selectedModelLabel"
          :model-picker-disabled="false"
          :stop-disabled="!canStopRun"
          :stop-disabled-reason="stopDisabledReason"
          :models="models"
          :selected-model="selectedModel"
          :message-feedbacks="messageFeedbacks"
          :feedback-submitting="feedbackSubmitting"
          placeholder="描述测试任务，例如：跑 checkout 模块并分析失败原因"
          @send="(text: string) => handleSend(text)"
          @stop="handleStopRun"
          @new-conversation="handleNewConversation"
          @initialize-process="() => initializeOpencodeProcessMutation.mutate()"
          @refresh-process="refreshOpencodeProcessStatus"
          @open-diff="(path: string) => { if (path) workbench.setSelectedDiffPath(path); centerMode = 'diff'; }"
          @reply-permission="(requestId: string, decision: 'once' | 'always' | 'reject') => replyPermissionMutation.mutate({ requestId, decision })"
          @reply-question="(requestId: string, answers: unknown[]) => replyQuestionMutation.mutate({ requestId, answers })"
          @reject-question="(requestId: string) => rejectQuestionMutation.mutate(requestId)"
          @select-session="(id: string) => switchSession(id)"
          @select-model="(model) => selectRuntimeModel(model)"
          @submit-feedback="handleSubmitFeedback"
          @close="rightPanelOpen = false"
        />
      </div>
    </template>

    <template #bottom>
      <div class="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
        <div class="flex h-9 shrink-0 items-center gap-1 border-b border-[var(--ta-border)] bg-[var(--ta-tabbar)] px-2">
          <button
            type="button"
            :class="['rounded px-2 py-1 text-[12px]', bottomMode === 'run' ? 'bg-[var(--ta-surface)] text-[var(--ta-text)] shadow-[inset_0_-2px_0_var(--ta-ink)]' : 'text-[var(--ta-muted)] hover:bg-[var(--ta-hover)] hover:text-[var(--ta-text)]']"
            @click="bottomMode = 'run'"
          >运行</button>
          <button
            type="button"
            :class="['rounded px-2 py-1 text-[12px]', bottomMode === 'terminal' ? 'bg-[var(--ta-surface)] text-[var(--ta-text)] shadow-[inset_0_-2px_0_var(--ta-ink)]' : 'text-[var(--ta-muted)] hover:bg-[var(--ta-hover)] hover:text-[var(--ta-text)]']"
            @click="bottomMode = 'terminal'"
          >终端</button>
          <button
            type="button"
            class="ml-auto rounded px-2 py-1 text-[12px] text-[var(--ta-muted)] hover:bg-[var(--ta-hover)] hover:text-[var(--ta-text)]"
            aria-label="关闭运行与终端"
            @click="bottomDrawerOpen = false"
          >关闭</button>
        </div>
        <div class="min-h-0 flex-1">
          <TestRunnerPanel
            v-if="bottomMode === 'run'"
            :run="run"
            :logs="logs"
            @cancel="cancelRunMutation.mutate()"
            @retry="() => lastPrompt && handleSend(lastPrompt)"
          />
          <TerminalPanel
            v-else
            :base-url="apiBaseUrl"
            :create-ticket="createTerminalTicket"
            :disabled="!session || !!readonlySessionReason"
            :disabled-reason="readonlySessionReason || '先发送一次 prompt 建立 Session 运行上下文后再连接终端'"
          />
        </div>
      </div>
    </template>
  </FigmaShell>

  <WorkspaceDirectoryPickerDialog
    :open="directoryPickerOpen"
    :directory="directoryPickerData"
    :loading="directoryPickerLoading"
    @close="directoryPickerOpen = false"
    @navigate="loadWorkspaceDirectories"
    @select="selectWorkspaceDirectory"
  />

  <ServerWorkspacePickerDialog
    :open="serverWorkspacePickerOpen"
    :servers="serverWorkspaceServers"
    :selected-server-id="selectedServerWorkspaceServerId"
    :directory="serverWorkspaceDirectory"
    :loading="serverWorkspacePickerLoading"
    :current-agent-linux-server-id="opencodeProcessStatus?.linuxServerId"
    @close="serverWorkspacePickerOpen = false"
    @select-server="selectServerWorkspaceServer"
    @navigate="(path: string) => loadServerWorkspaceDirectories(path)"
    @select="selectServerWorkspaceDirectory"
  />

  <SettingsDialog :open="settingsOpen" :current-user="authStore.currentUser" @close="settingsOpen = false" />
</template>

<style scoped>
.managed-chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.managed-editor-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.managed-editor-main > *:first-child {
  min-height: 0;
  flex: 1;
}

.managed-runtime-container {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  background: #f5f5f5;
}

.managed-chat-panel :deep(.figma-chat-root) {
  min-height: 0;
  flex: 1;
}

.managed-chat-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ta-border);
  background: var(--ta-panel);
}

.managed-model-button,
.managed-live-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 30px;
  border: 1px solid var(--ta-border);
  border-radius: 6px;
  background: var(--ta-panel-2);
  color: var(--ta-text);
  font-size: 12px;
}

.managed-model-button {
  min-width: 148px;
  padding: 0 10px;
}

.managed-model-button strong {
  max-width: 92px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.managed-live-button {
  width: 48px;
}

.managed-live-button.is-active {
  border-color: var(--ta-ink);
  color: var(--ta-ink);
}



.managed-workspace-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.managed-workspace-button {
  height: 34px;
  border: 1px solid var(--ta-border);
  border-radius: 12px;
  background: #fff;
  color: var(--ta-text);
  font-size: 13px;
  font-weight: 500;
  padding: 0 16px;
  white-space: nowrap;
  transition: background-color 0.12s, border-color 0.12s;
  cursor: pointer;
}
.managed-workspace-button:hover {
  background: #f5f5f5;
  border-color: #bbb;
}

.managed-workspace-files {
  min-height: 0;
  flex: 1 1 auto;
}

.managed-workspace-empty {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 24px 16px;
  color: var(--ta-muted);
  font-size: 14px;
  text-align: center;
  border-radius: 16px;
  background: #ffffff;
  border: 0.5px dashed var(--ta-border-strong);
  margin: 0;
}
</style>
