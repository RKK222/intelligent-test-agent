<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, onScopeDispose, provide, ref, shallowRef, watch } from "vue";
import { useRouter } from "vue-router";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/vue-query";
import { AgentChat, buildComposerPromptParts, createInitialAgentChatRuntimeState, reduceAgentChatRuntime, type ComposerAttachment } from "@test-agent/agent-chat";
import { BackendApiError, createBackendApiClient, type RawHttpExchange } from "@test-agent/backend-api";
import { DiffViewer, parseUnifiedPatch } from "@test-agent/diff-viewer";
import { CodeEditor, languageFromPath, type EditorSelectionContext } from "@test-agent/editor";
import { subscribeRunEvents, subscribeSessionRuntimeState, type RunEventRawMessage } from "@test-agent/event-stream-client";
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
  ModelInfo,
  ProviderInfo,
  Session,
  SessionMessage,
  SessionRuntimeState,
  SessionRuntimeStateSummary,
  OpencodeProcessStartOperation,
  UserOpencodeProcess,
  Workspace,
  WorkspaceBackendServer,
  WorkspaceDirectoryList,
  WorkspaceGitDiffFile
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
import {
  chatContextItemsToPromptParts,
  createContextId,
  serializeChatContexts,
  summarizeChatContextItems,
  useChatContextStore,
  validateChatSend,
  type ChatContextItem
} from "../stores/chatContextStore";
import FigmaShell, { type RuntimeInventoryItem, type RuntimeInventorySummary } from "./FigmaShell.vue";
import FigmaFileExplorer from "./FigmaFileExplorer.vue";
import FigmaEditorArea from "./FigmaEditorArea.vue";
import FigmaChatPanel from "./FigmaChatPanel.vue";
import { type PreviewMode } from "./WorkbenchFooter.vue";
import OpencodeProcessStartupDialog from "./OpencodeProcessStartupDialog.vue";
import SettingsDialog from "./settings/SettingsDialog.vue";
import ServerWorkspacePickerDialog from "./ServerWorkspacePickerDialog.vue";
import SystemManagementWrapper from "./SystemManagementWrapper.vue";
import WorkbenchFooter from "./WorkbenchFooter.vue";
import { notifyFeedback } from "./notify";
import { canStartFollowUp, createFollowUpDraft, dequeueFollowUp, enqueueFollowUp, isRunBusyStatus, isRuntimeBusy, type FollowUpDraft } from "./follow-up-queue";
import {
  buildPromptParts,
  chatStateFromSessionTreeSnapshot,
  dedupeSessionMessages,
  diffFilesFromPayload,
  diffFilesFromSessionMessages,
  errorFeedback,
  filterWorkspaceRootEntries,
  historyItems,
  inferDiffFromToolPart,
  initialMessages,
  mergeDiffFiles,
  messagesFromSessionMessages,
  modelIdOnly,
  modelValue,
  nextCenterModeAfterRunDiff,
  nextCenterModeAfterVcsRefresh,
  notifyOnAttention,
  OPENCODE_HEALTH_REFETCH_INTERVAL_MS,
  OPENCODE_RUNTIME_CAPABILITY_REFETCH_INTERVAL_MS,
  OPENCODE_VCS_STATUS_REFETCH_INTERVAL_MS,
  opencodeAvailabilityFromHealth,
  opencodeAvailabilityFromProcess,
  opencodeHealthRequestFromProcess,
  parseCommand,
  prepareAutoRetryRun,
  promptFromParts,
  resolveRetryDeadline,
  retryCountdownSeconds,
  retryExpirationDecision,
  runEventMatchesRun,
  runtimeResources,
  runtimeStatus,
  sessionTitleFromFirstMessage,
  shouldFailExhaustedRetry,
  syntheticEvent,
  text,
  workspaceLoadIsCurrent,
  type AutoRetryRunDraft,
  type OpencodeAvailabilityState,
  type RetryDeadlineMap
} from "./workbench-utils";

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl, rawExchangeObserver: observeRawHttpExchange });
provide("api", api);
const queryClient = useQueryClient();
const workbench = useWorkbenchStore();
const authStore = useAuthStore();
const chatContextStore = useChatContextStore();
const router = useRouter();
const OPENCODE_PROCESS_START_OPERATION_POLL_INTERVAL_MS = 500;
const AGENT_CATALOG_REQUEST_TIMEOUT_MS = 8000;
const RUN_EVENT_SSE_ERROR_TITLE = "RunEvent SSE 连接异常";
const OPENCODE_PROCESS_START_STEPS = [
  { step: "VALIDATING_REQUEST", name: "校验请求" },
  { step: "CHECKING_ASSIGNMENT", name: "确认分配" },
  { step: "SELECTING_CONTAINER", name: "选择容器" },
  { step: "PREPARING_STARTUP", name: "准备启动参数" },
  { step: "STARTING_PROCESS", name: "进程启动" },
  { step: "SAVING_CANDIDATE", name: "记录候选进程" },
  { step: "CHECKING_PROCESS", name: "检查进程" },
  { step: "HEALTH_CHECKING", name: "健康检查" },
  { step: "SAVING_BINDING", name: "写入绑定" },
  { step: "COMPLETED", name: "完成" }
] as const;
const SELECTED_PROVIDER_STORAGE_KEY = "ta_selected_provider";
const SELECTED_MODEL_STORAGE_KEY = "ta_selected_model";
const ACTIVE_RUN_PROBE_INTERVAL_MS = 1500;
const RAW_OUTPUT_MAX_ENTRIES_PER_SESSION = 10000;
const RAW_OUTPUT_BODY_LIMIT = 200_000;
const SESSION_HISTORY_PAGE_SIZE = 30;

type RawOutputKind = "request" | "response" | "sse";

type RawOutputEntry = {
  id: string;
  kind: RawOutputKind;
  title: string;
  method?: string;
  path?: string;
  status?: number;
  eventName?: string;
  traceId?: string;
  runId?: string;
  contentType?: string;
  body: string;
  truncated?: boolean;
  occurredAt: string;
};

const isSuperAdmin = computed(() => authStore.currentUser?.roles?.includes("SUPER_ADMIN") === true);

function readStoredRuntimePreference() {
  return {
    provider: localStorage.getItem(SELECTED_PROVIDER_STORAGE_KEY) || "",
    model: localStorage.getItem(SELECTED_MODEL_STORAGE_KEY) || ""
  };
}

function persistRuntimePreference(provider: string, model: string) {
  if (provider) {
    localStorage.setItem(SELECTED_PROVIDER_STORAGE_KEY, provider);
  } else {
    localStorage.removeItem(SELECTED_PROVIDER_STORAGE_KEY);
  }
  if (model) {
    localStorage.setItem(SELECTED_MODEL_STORAGE_KEY, model);
  } else {
    localStorage.removeItem(SELECTED_MODEL_STORAGE_KEY);
  }
}

// 设置弹窗依赖当前用户角色；工作台直达时需要主动补齐 /api/auth/me。
void authStore.fetchCurrentUser(api);

// 工作台状态
const selectedWorkspaceId = ref<string | undefined>(undefined);
const selectedWorkspaceSnapshot = shallowRef<Workspace | undefined>(undefined);
const entriesByDirectory = ref<Record<string, FileTreeEntry[]>>({});
const expandedDirectories = ref<Set<string>>(new Set());
// 文件树面板内错误状态，不覆盖全局顶部反馈
const fileTreeError = ref<string | null>(null);
let workspaceLoadGeneration = 0;
const fileTreeRetryTimers = new Set<ReturnType<typeof setTimeout>>();
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
const rawEntriesBySessionId = ref<Record<string, RawOutputEntry[]>>({});
const rawRunSessionMap = ref<Record<string, string>>({});
const reportedRunEventStreamErrors = new Set<string>();
const lastPrompt = ref("");
const lastRunDraft = ref<AutoRetryRunDraft | null>(null);
const selectedAgent = ref("");
const storedRuntimePreference = readStoredRuntimePreference();
const selectedProvider = ref(storedRuntimePreference.provider);
const selectedModel = ref(storedRuntimePreference.model);
const promptMode = ref("build");
const logs = ref<string[]>([]);
const diffFiles = ref<RunDiffFile[]>([]);
const vcsDiffFiles = ref<RunDiffFile[]>([]);
const diffSource = ref<"run" | "session" | "vcs" | "agent">("run");
const diffViewMode = ref<"split" | "unified">("split");
const centerMode = ref<"editor" | "diff" | "system">("editor");
const feedback = ref<Feedback | null>(null);
const diffViewerRef = ref<InstanceType<typeof DiffViewer> | null>(null);
const isDiffDirty = ref(false);
const sessionSearch = ref("");
const sessionHistoryPage = ref(1);
const sessionHistoryItems = ref<Session[]>([]);
const sessionRuntimeState = shallowRef<SessionRuntimeStateSummary | null>(null);
const followUpQueue = ref<FollowUpDraft[]>([]);
const retryDeadlines = ref<RetryDeadlineMap>({});
const retryActionInFlightKey = ref<string | null>(null);
const autoRetryStarting = ref(false);
const ignoredRunIds = ref<Set<string>>(new Set());
const diffContextParts = ref<PromptPart[]>([]);
const editorSelection = ref<EditorSelectionContext | undefined>(undefined);
const bottomMode = ref<"run" | "terminal">("run");
const bottomDrawerOpen = ref(false);
const leftPanelOpen = ref(true);
const rightPanelOpen = ref(true);
const savedLeftPanelOpen = ref(true);
const savedRightPanelOpen = ref(true);

function clearRunEventSseFeedback() {
  if (feedback.value?.title === RUN_EVENT_SSE_ERROR_TITLE) {
    feedback.value = null;
  }
}

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
const currentPersonalWorkspaceBranch = ref<string | undefined>(undefined);
let retryingWorkspaceAfterOpencodeReady = false;
let selectingAppId: string | undefined;
let appSelectionSeq = 0;
const readonlySessionReason = ref("");
const chatTitle = computed(() => session.value?.title ?? "");
const currentRawOutputEntries = computed(() => {
  const sessionId = session.value?.sessionId;
  return sessionId ? rawEntriesBySessionId.value[sessionId] ?? [] : [];
});
// 任务消耗展示：duration 取 chatStartedAt 实时计算；tokens 从助手消息的 step-finish part
// 累计（opencode 每轮 step 结束会上报 tokens.total）。Run 结束后保留最后值继续展示。Run 切换时清零。
const chatStartedAt = ref<number | null>(null);
const accumulatedTokens = ref(0);
const totalDurationMs = ref(0);
let lastDuration: string | undefined;
let lastTokens = 0;
const nowTick = ref(Date.now());
const settingsOpen = ref(false);
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
// Markdown 预览模式：off | full(整体预览) | split(分上下)。
// 切换非 Markdown 文件时由 watch 主动复位，避免下次切回 md 时残留之前的开启状态。
const markdownPreviewMode = ref<PreviewMode>("off");
const markdownPreview = computed(() => markdownPreviewMode.value !== "off");
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
  clearFileTreeRetryTimers();
  stopActiveRunProbe();
  stopProcessStartupPolling();
});

// Chat runtime：单一 reducer 维护，dispatch 闭包更新
const chatState = ref(createInitialAgentChatRuntimeState(initialMessages));
const messageFeedbacks = ref<Record<string, AiMessageFeedback | null>>({});
const feedbackSubmitting = ref<Record<string, boolean>>({});
const platformMessageIdsByRemoteId = ref<Record<string, string>>({});
function dispatchChat(action: Parameters<typeof reduceAgentChatRuntime>[1]) {
  chatState.value = reduceAgentChatRuntime(chatState.value, action);
}

function clearAutoRetryState() {
  lastRunDraft.value = null;
  retryDeadlines.value = {};
  retryActionInFlightKey.value = null;
  autoRetryStarting.value = false;
  ignoredRunIds.value = new Set();
}

function isPlatformSessionMessageId(id: string | undefined): id is string {
  return /^msg_[0-9a-f]{32}$/i.test(id ?? "");
}

function isRemoteRuntimeMessageId(id: string | undefined): id is string {
  return Boolean(id?.startsWith("msg_") && !isPlatformSessionMessageId(id));
}

function rememberPersistedMessageIdentities(messages: Pick<SessionMessage, "messageId" | "remoteMessageId">[]) {
  const next: Record<string, string> = {};
  for (const message of messages) {
    if (message.remoteMessageId && isPlatformSessionMessageId(message.messageId)) {
      next[message.remoteMessageId] = message.messageId;
    }
  }
  if (Object.keys(next).length > 0) {
    platformMessageIdsByRemoteId.value = { ...platformMessageIdsByRemoteId.value, ...next };
  }
}

function remoteMessageIdForAgentMessage(message: AgentMessage): string | undefined {
  if (message.role === "card") return undefined;
  if (isRemoteRuntimeMessageId(message.remoteMessageId)) return message.remoteMessageId;
  if (isRemoteRuntimeMessageId(message.messageId)) return message.messageId;
  if (isRemoteRuntimeMessageId(message.id)) return message.id;
  return undefined;
}

function platformMessageIdForAgentMessage(message: AgentMessage): string | undefined {
  if (message.role === "card") return undefined;
  if (isPlatformSessionMessageId(message.platformMessageId)) return message.platformMessageId;
  if (isPlatformSessionMessageId(message.messageId)) return message.messageId;
  if (isPlatformSessionMessageId(message.id)) return message.id;
  const remoteMessageId = remoteMessageIdForAgentMessage(message);
  return remoteMessageId ? platformMessageIdsByRemoteId.value[remoteMessageId] : undefined;
}

const chatMessagesForPanel = computed<AgentMessage[]>(() =>
  chatState.value.messages.map((message) => {
    if (message.role === "card") return message;
    const platformMessageId = platformMessageIdForAgentMessage(message);
    return platformMessageId ? { ...message, platformMessageId } : message;
  })
);

const tabs = computed(() => workbench.tabs);
const activePath = computed(() => workbench.activePath);
const selectedDiffPath = computed(() => workbench.selectedDiffPath);
const activeTab = computed(() => tabs.value.find((tab: EditorTab) => tab.path === activePath.value));
const codeEditorRef = ref<any>(null);
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
  const fromList = workspaces.value.find((item) => item.workspaceId === selectedWorkspaceId.value);
  if (fromList) return fromList;
  if (selectedWorkspaceSnapshot.value?.workspaceId === selectedWorkspaceId.value) {
    return selectedWorkspaceSnapshot.value;
  }
  return undefined;
});
const selectedWorkspaceIdRef = computed(() => selectedWorkspace.value?.workspaceId);
const sessionSearchTrim = computed(() => sessionSearch.value.trim());
const sessionRuntimeStateQueryKey = ["sessions", "runtime-state"] as const;

const managedApplicationsQuery = useQuery({
  queryKey: ["managed-workspace", "applications"],
  queryFn: () => api.listManagedApplications(),
  retry: false
});
const managedApplications = computed<ManagedApplication[]>(() => managedApplicationsQuery.data.value ?? []);
// 右上角切换菜单只展示当前用户已加入的托管应用；未加入应用仅进入"加入其他应用"弹窗。
const applicationCatalog = computed<ManagedApplication[]>(() => managedApplications.value);
// 全局最近工作区：跨应用维度维护「上一次进入的应用 + 工作区」组合。
// 重新登录或换电脑登录时，前端用它直接还原上次的应用上下文（替代之前总是回退 apps[0] 的逻辑），
// 工作区是否在当前用户权限内则继续走 per-app recent；无 versionId 的应用只选应用不加载工作区。
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

// ===== 开启应用列表查询与加入应用逻辑 =====
const allEnabledApplicationsQuery = useQuery({
  queryKey: ["managed-workspace", "all-enabled-applications"],
  queryFn: () => api.listApplications(true),
  retry: false
});

const joinableApps = computed(() => {
  const currentIds = new Set(shellApps.value.map((app) => app.id));
  const allApps = allEnabledApplicationsQuery.data.value ?? [];
  return allApps.filter((app) => !currentIds.has(app.appId));
});

async function handleJoinApp(appId: string, callback: (success: boolean) => void) {
  const currentUserId = authStore.currentUser?.userId;
  if (!currentUserId) {
    ElMessage.error("未获取到当前用户信息，请重新登录");
    callback(false);
    return;
  }
  try {
    await api.addApplicationMember(appId, currentUserId);
    ElMessage.success("成功加入应用");
    // 重新拉取已加入的应用和未加入的应用列表
    void queryClient.invalidateQueries({ queryKey: ["managed-workspace", "applications"] });
    void queryClient.invalidateQueries({ queryKey: ["managed-workspace", "all-enabled-applications"] });
    callback(true);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "加入应用失败");
    callback(false);
  }
}


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
  queryKey: ["sessions", "user-history", sessionSearchTrim, sessionHistoryPage],
  enabled: () => authStore.isAuthenticated(),
  queryFn: () => {
    const query = sessionSearchTrim.value;
    return api.listAllSessions(
      sessionHistoryPage.value,
      SESSION_HISTORY_PAGE_SIZE,
      query || undefined
    );
  }
});

const sessionRuntimeStateQuery = useQuery({
  queryKey: sessionRuntimeStateQueryKey,
  enabled: () => authStore.isAuthenticated(),
  queryFn: () => api.getSessionRuntimeState(),
  refetchOnWindowFocus: false
});

watch(sessionSearchTrim, () => {
  sessionHistoryPage.value = 1;
  sessionHistoryItems.value = [];
});

watch(
  () => sessionsQuery.data.value,
  (page) => {
    if (!page) return;
    if (page.page === 1) {
      sessionHistoryItems.value = page.items;
      return;
    }
    const seen = new Set(sessionHistoryItems.value.map((item) => item.sessionId));
    sessionHistoryItems.value = [
      ...sessionHistoryItems.value,
      ...page.items.filter((item) => !seen.has(item.sessionId))
    ];
  },
  { immediate: true }
);

watch(
  () => sessionRuntimeStateQuery.data.value,
  (summary) => {
    if (summary) {
      sessionRuntimeState.value = summary;
    }
  },
  { immediate: true }
);

watch(
  () => authStore.token,
  (token, _oldToken, onCleanup) => {
    if (!token) {
      sessionRuntimeState.value = null;
      return;
    }
    const subscription = subscribeSessionRuntimeState({
      baseUrl: apiBaseUrl,
      token,
      onEvent: (summary) => {
        sessionRuntimeState.value = summary;
        queryClient.setQueryData(sessionRuntimeStateQueryKey, summary);
      },
      onStatus: (status) => {
        logs.value = [...logs.value.slice(-200), `[runtime-state] ${status}`];
      }
    });
    onCleanup(() => subscription.close());
  },
  { immediate: true }
);

const opencodeProcessEnabled = computed(() => authStore.isAuthenticated());
const opencodeProcessQueryKey = computed(() => ["runtime", "opencode-process", "me", authStore.token ?? ""] as const);
const opencodeProcessQuery = useQuery({
  queryKey: opencodeProcessQueryKey,
  enabled: opencodeProcessEnabled,
  queryFn: () => api.getMyOpencodeProcess(),
  retry: false,
  refetchOnWindowFocus: false,
  refetchInterval: false
});
const opencodeProcessStatus = computed<UserOpencodeProcess | null>(() => opencodeProcessQuery.data.value ?? null);
const opencodeAvailability = ref<OpencodeAvailabilityState>({ ready: false, source: "process" });
const opencodeHealthRequest = computed(() => opencodeHealthRequestFromProcess(opencodeProcessStatus.value));
const opencodeHealthQuery = useQuery({
  queryKey: computed(
    () =>
      [
        "runtime",
        "opencode-process",
        "health",
        authStore.token ?? "",
        opencodeHealthRequest.value?.linuxServerId ?? "",
        opencodeHealthRequest.value?.containerId ?? "",
        opencodeHealthRequest.value?.port ?? 0
      ] as const
  ),
  enabled: () => opencodeProcessEnabled.value && Boolean(opencodeHealthRequest.value),
  queryFn: () => api.getMyOpencodeProcessHealth(opencodeHealthRequest.value!),
  retry: false,
  refetchInterval: OPENCODE_HEALTH_REFETCH_INTERVAL_MS,
  refetchIntervalInBackground: false
});
const opencodeHealthReady = computed(() => opencodeAvailability.value.ready);
const opencodeProcessReady = computed(() => opencodeHealthReady.value);
const workspaceFileRouteReadyById = ref<Record<string, boolean>>({});
const selectedWorkspaceFileRouteReady = computed(() => {
  const workspaceId = selectedWorkspaceIdRef.value;
  return Boolean(workspaceId && workspaceFileRouteReadyById.value[workspaceId]);
});
const processStartupDialogOpen = ref(false);
const processStartupActionLabel = ref("启动进程");
const processStartupOperation = ref<OpencodeProcessStartOperation | null>(null);
let processStartupPollTimer: ReturnType<typeof setInterval> | null = null;
let lastUnhealthyHealthIdentity = "";

function newProcessStartupOperationId(): string {
  const randomPart =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID().replace(/-/g, "").slice(0, 14)
      : Math.random().toString(36).slice(2, 16);
  return `opi_${Date.now().toString(36)}_${randomPart}`;
}

function initialProcessStartupOperation(operationId: string): OpencodeProcessStartOperation {
  const now = new Date().toISOString();
  return {
    operationId,
    status: "RUNNING",
    currentStep: "VALIDATING_REQUEST",
    steps: OPENCODE_PROCESS_START_STEPS.map((step, index) => ({
      ...step,
      status: index === 0 ? "RUNNING" : "PENDING"
    })),
    traceId: "",
    createdAt: now,
    updatedAt: now
  };
}

function stopProcessStartupPolling() {
  if (processStartupPollTimer) {
    clearInterval(processStartupPollTimer);
    processStartupPollTimer = null;
  }
}

async function refreshProcessStartupOperation(operationId: string) {
  try {
    const operation = await api.getOpencodeProcessStartOperation(operationId);
    processStartupOperation.value = operation;
    if (operation.status === "SUCCEEDED") {
      stopProcessStartupPolling();
      processStartupDialogOpen.value = false;
      void opencodeProcessQuery.refetch();
    } else if (operation.status === "FAILED") {
      stopProcessStartupPolling();
    }
  } catch {
    // 初始化 POST 刚发出时 operation 可能尚未落库，短轮询继续等待下一次快照。
  }
}

function startProcessStartupPolling(operationId: string) {
  stopProcessStartupPolling();
  void refreshProcessStartupOperation(operationId);
  processStartupPollTimer = setInterval(() => {
    void refreshProcessStartupOperation(operationId);
  }, OPENCODE_PROCESS_START_OPERATION_POLL_INTERVAL_MS);
}

function failLocalProcessStartupOperation(error: unknown) {
  const current = processStartupOperation.value;
  if (!current || current.status === "FAILED" || current.status === "SUCCEEDED") {
    return;
  }
  const apiError = error instanceof BackendApiError ? error : null;
  const errorCode = apiError?.code ?? "INTERNAL_ERROR";
  const errorMessage = apiError?.message ?? (error instanceof Error ? error.message : "初始化 TestAgent 进程失败");
  const traceId = apiError?.traceId ?? current.traceId;
  const currentStep = current.currentStep || "STARTING_PROCESS";
  processStartupOperation.value = {
    ...current,
    status: "FAILED",
    currentStep,
    errorCode,
    errorMessage,
    traceId,
    updatedAt: new Date().toISOString(),
    steps: current.steps.map((step) => {
      const stepCode = step.step ?? step.code;
      if (stepCode === currentStep) {
        return { ...step, status: "FAILED" };
      }
      if (step.status === "RUNNING") {
        return { ...step, status: "SUCCEEDED" };
      }
      return step;
    })
  };
}

function beginInitializeOpencodeProcess() {
  const operationId = newProcessStartupOperationId();
  processStartupActionLabel.value =
    opencodeProcessStatus.value?.serviceStatus === "NOT_RUNNING" ? "启动进程" : "分配专属进程";
  processStartupOperation.value = initialProcessStartupOperation(operationId);
  processStartupDialogOpen.value = true;
  startProcessStartupPolling(operationId);
  initializeOpencodeProcessMutation.mutate(operationId);
}

// 拆分就绪条件：不同能力依赖不同条件
// 1. 模型和 Provider：登录后立即加载，不依赖 workspace 和 opencode
const authReady = computed(() => authStore.isAuthenticated());
// 2. 文件路由：只需要 workspace 存在，不依赖 opencode 状态
const fileRouteReady = computed(() => Boolean(selectedWorkspaceIdRef.value));
// 3. Runtime 目录（Agent、Command）：需要 opencode 弱健康 READY + workspace
const opencodeCatalogReady = computed(() => opencodeProcessReady.value && Boolean(selectedWorkspaceIdRef.value));
// 4. LSP、MCP、VCS：需要 opencode 弱健康 READY + workspace
const runtimeReady = computed(() => opencodeProcessReady.value && selectedWorkspaceFileRouteReady.value);
// 5. Run 启动：需要 opencode 弱健康 READY + workspace 文件路由成功
const runReady = computed(() => opencodeProcessReady.value && selectedWorkspaceFileRouteReady.value);

// 模型和 Provider 登录后立即加载
const modelsQuery = useQuery({
  queryKey: ["runtime", "models"],
  enabled: authReady,
  queryFn: () => api.listModels(),
  retry: false
});
const providersQuery = useQuery({
  queryKey: ["runtime", "providers"],
  enabled: authReady,
  queryFn: () => api.listProviders(),
  retry: false
});

// Agent、Command 需要 opencode READY + workspace
const agentsQuery = useQuery({
  queryKey: computed(() => ["runtime", "agents", selectedWorkspaceIdRef.value ?? ""] as const),
  enabled: opencodeCatalogReady,
  queryFn: ({ signal, queryKey }) =>
    api.listAgents(String(queryKey[2]), { signal, timeoutMs: AGENT_CATALOG_REQUEST_TIMEOUT_MS }),
  retry: false
});
const commandsQuery = useQuery({
  queryKey: ["runtime", "commands", selectedWorkspaceIdRef],
  enabled: opencodeCatalogReady,
  queryFn: () => api.listCommands(selectedWorkspaceIdRef.value!),
  retry: false
});

// LSP、MCP、VCS 需要 opencode READY + workspace 文件路由成功
const lspStatusQuery = useQuery({
  queryKey: ["runtime", "lsp", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && runtimeReady.value,
  queryFn: () => api.getLspStatus(selectedWorkspaceIdRef.value!),
  retry: false,
  refetchInterval: OPENCODE_RUNTIME_CAPABILITY_REFETCH_INTERVAL_MS
});
const mcpStatusQuery = useQuery({
  queryKey: ["runtime", "mcp", "status", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && runtimeReady.value,
  queryFn: () => api.getMcpStatus(selectedWorkspaceIdRef.value!),
  retry: false,
  refetchInterval: OPENCODE_RUNTIME_CAPABILITY_REFETCH_INTERVAL_MS
});
const mcpResourcesQuery = useQuery({
  queryKey: ["runtime", "mcp", "resources", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && runtimeReady.value,
  queryFn: () => api.getMcpResources(selectedWorkspaceIdRef.value!),
  retry: false
});
const mcpToolsQuery = useQuery({
  queryKey: ["runtime", "mcp", "tools", selectedWorkspaceIdRef, selectedProvider, selectedModel],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && runtimeReady.value,
  queryFn: () => {
    const model = modelIdOnly(selectedModel.value);
    return api.getMcpTools(selectedWorkspaceIdRef.value!, selectedProvider.value || undefined, model || undefined);
  },
  retry: false
});
const vcsStatusQuery = useQuery({
  queryKey: ["runtime", "vcs", "status", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) && runtimeReady.value,
  queryFn: () => api.getVcsStatus(selectedWorkspaceIdRef.value!),
  retry: false,
  refetchInterval: OPENCODE_VCS_STATUS_REFETCH_INTERVAL_MS
});

const agents = computed(() => agentsQuery.data.value ?? []);
const agentsLoading = computed(() => opencodeCatalogReady.value && agentsQuery.isFetching.value && agents.value.length === 0);
const agentsRefreshing = computed(() => opencodeCatalogReady.value && agentsQuery.isFetching.value && agents.value.length > 0);
const agentsError = computed(() => agentCatalogErrorMessage(agentsQuery.error.value));
const models = computed(() => modelsQuery.data.value ?? []);
const providers = computed(() => providersQuery.data.value ?? []);
const allModels = computed<ModelInfo[]>(() => {
  const byValue = new Map<string, ModelInfo>();
  for (const model of models.value) {
    byValue.set(modelValue(model), model);
  }
  for (const provider of providers.value as ProviderInfo[]) {
    for (const model of provider.models ?? []) {
      const providerModel = { ...model, providerId: model.providerId ?? provider.providerId };
      byValue.set(modelValue(providerModel), providerModel);
    }
  }
  return Array.from(byValue.values());
});
const commands = computed(() => commandsQuery.data.value ?? []);
const mcpResourcesData = computed(() => mcpResourcesQuery.data.value);
const mcpToolsData = computed<RuntimeToolInfo[]>(() => mcpToolsQuery.data.value ?? []);
const vcsStatusData = computed(() => vcsStatusQuery.data.value);
const lspStatusData = computed(() => lspStatusQuery.data.value);
const mcpStatusData = computed(() => mcpStatusQuery.data.value);
function runtimeInventoryText(value: unknown): string | undefined {
  if (typeof value === "string" && value.trim()) return value.trim();
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return undefined;
}

function runtimeMcpEntrySource(value: unknown): unknown {
  const raw = recordValue(value);
  const data = recordValue(raw?.data);
  if (data?.servers || data?.mcp || data?.providers) {
    return data.servers ?? data.mcp ?? data.providers;
  }
  return raw?.servers ?? raw?.mcp ?? raw?.providers ?? value;
}

function runtimeMcpItems(value: unknown): RuntimeInventoryItem[] {
  const source = runtimeMcpEntrySource(value);
  if (Array.isArray(source)) {
    return source
      .map((entry, index) => {
        const item = recordValue(entry);
        const name = text(item?.name) ?? text(item?.id) ?? `MCP ${index + 1}`;
        return {
          id: text(item?.id) ?? name,
          name,
          status: runtimeInventoryText(item?.status),
          description: text(item?.description) ?? text(item?.message)
        };
      })
      .filter((item) => item.name);
  }
  const record = recordValue(source);
  if (!record) return [];
  return Object.entries(record)
    .filter(([key, entry]) => {
      if (["status", "tools", "resources", "message", "error"].includes(key)) return false;
      return typeof entry === "object" && entry !== null;
    })
    .map(([key, entry]) => {
      const item = recordValue(entry) ?? {};
      return {
        id: text(item.id) ?? key,
        name: text(item.name) ?? key,
        status: runtimeInventoryText(item.status),
        description: text(item.description) ?? text(item.message)
      };
    });
}

// 顶部资源盘点只消费已加载目录，不主动触发额外 runtime 请求。
const runtimeInventoryForShell = computed<RuntimeInventorySummary>(() => ({
  agents: agents.value
    .filter((agent) => !agent.hidden)
    .map((agent) => ({
      id: agent.agentId || agent.name,
      name: agent.name || agent.agentId,
      status: agent.mode,
      description: agent.description
    })),
  skills: commands.value
    .filter((command) => command.source === "skill")
    .map((command) => ({
      id: command.commandId,
      name: command.name,
      description: command.description
    })),
  mcp: runtimeMcpItems(mcpStatusData.value),
  plugins: commands.value
    .filter((command) => command.source === "plugin")
    .map((command) => ({
      id: command.commandId,
      name: command.name,
      description: command.description
    })),
  mcpTools: mcpToolsData.value.map((tool) => ({
    id: tool.toolId,
    name: tool.name,
    status: tool.source,
    description: tool.description
  })),
  mcpResources: (mcpResourcesData.value ?? []).map((resource) => ({
    id: resource.id,
    name: resource.name,
    status: resource.type,
    description: resource.uri
  }))
}));
// 只在首个状态响应回来前展示"正在检查"，避免 READY 数据后台刷新时把对话区重新置为阻塞态。
const opencodeProcessInitialLoading = computed(
  () => opencodeProcessEnabled.value && !opencodeProcessStatus.value && (opencodeProcessQuery.isPending.value || opencodeProcessQuery.isFetching.value)
);
const opencodeProcessRefreshing = computed(
  () => opencodeProcessEnabled.value && Boolean(opencodeProcessStatus.value) && manualOpencodeProcessRefreshing.value
);
const sessionsItems = computed(() => sessionHistoryItems.value);
const runtimeStatesBySessionId = computed<Record<string, SessionRuntimeState>>(() => {
  const entries = sessionRuntimeState.value?.sessions ?? [];
  return Object.fromEntries(entries.map((item) => [item.sessionId, item]));
});
const sessionHistoryTotal = computed(() => sessionsQuery.data.value?.total ?? sessionHistoryItems.value.length);
const sessionHistoryHasMore = computed(() => sessionHistoryTotal.value > sessionHistoryItems.value.length);
const sessionHistoryLoadingMore = computed(() => sessionsQuery.isFetching.value && sessionHistoryPage.value > 1);
const selectedModelInfo = computed(() => {
  const selected = modelIdOnly(selectedModel.value);
  return allModels.value.find((model) => modelValue(model) === selectedModel.value || model.id === selected);
});
const selectedModelLabel = computed(() => selectedModelInfo.value?.name ?? selectedModel.value ?? "未选择模型");

watch(opencodeProcessStatus, (status) => {
  if (!status) {
    opencodeAvailability.value = { ready: false, source: "process" };
    return;
  }
  opencodeAvailability.value = opencodeAvailabilityFromProcess(status);
}, { immediate: true });

watch(opencodeHealthQuery.data, (health) => {
  if (!health) return;
  opencodeAvailability.value = opencodeAvailabilityFromHealth(health);
  const identity = `${health.linuxServerId}:${health.containerId}:${health.port}:${health.status}:${health.message}`;
  if (health.healthy) {
    lastUnhealthyHealthIdentity = "";
    return;
  }
  if (identity === lastUnhealthyHealthIdentity) {
    return;
  }
  lastUnhealthyHealthIdentity = identity;
  if (opencodeProcessEnabled.value && !opencodeProcessQuery.isFetching.value) {
    void opencodeProcessQuery.refetch();
  }
});

function refreshOpencodeProcessStatus() {
  if (!opencodeProcessEnabled.value || opencodeProcessQuery.isFetching.value) return;
  manualOpencodeProcessRefreshing.value = true;
  void opencodeProcessQuery.refetch().finally(() => {
    manualOpencodeProcessRefreshing.value = false;
  });
}

function agentCatalogErrorMessage(error: unknown): string {
  if (!error) return "";
  if (error instanceof BackendApiError) {
    return error.message || "Agent 目录加载失败";
  }
  if (error instanceof Error) {
    return error.message || "Agent 目录加载失败";
  }
  return "Agent 目录加载失败";
}

function refreshAgentsCatalog() {
  if (!opencodeCatalogReady.value || agentsQuery.isFetching.value) return;
  void agentsQuery.refetch();
}
const historyList = computed(() => historyItems(run.value, sessionsItems.value, runtimeStatesBySessionId.value));
const historyLoadingSessionId = ref<string | null>(null);
let historySwitchSeq = 0;

function handleHistorySearchChange(query: string) {
  if (sessionSearch.value === query) return;
  sessionSearch.value = query;
}

function loadMoreHistory() {
  if (sessionsQuery.isFetching.value || !sessionHistoryHasMore.value) return;
  sessionHistoryPage.value += 1;
}

async function refreshHistoryOnOpen() {
  if (sessionsQuery.isFetching.value) return;
  if (sessionHistoryPage.value !== 1) {
    sessionHistoryPage.value = 1;
    sessionHistoryItems.value = [];
    await nextTick();
  }
  void sessionsQuery.refetch();
}
const resourcesList = computed(() => runtimeResources(mcpResourcesData.value, activeTab.value));
const runtimeStatusValue = computed(() =>
  runtimeStatus(session.value, run.value, selectedAgent.value, selectedModel.value, vcsStatusData.value, lspStatusData.value, mcpStatusData.value, mcpToolsData.value, mcpResourcesData.value)
);
// VCS 分支选择入口已下线：footer 不再展示「选择分支」/「记住当前分支」按钮，
// 因此 vcsCurrentBranch / vcsDefaultBranch / pendingBranchOverride / recentBranchPreference /
// handleChangeBranch / handleRememberCurrentBranch / loadBranchPreferenceOnEnter 等相关状态与函数全部移除。
// VCS 分支信息仍由 runtimeStatus 读取并参与运行态展示（见 lspStatusData 等 run.status 字段），不依赖此处的引用。

function selectRuntimeModel(model: ModelInfo) {
  if (model.providerId) {
    selectedProvider.value = model.providerId;
  }
  const val = modelValue(model);
  selectedModel.value = val;
  persistRuntimePreference(selectedProvider.value, val);
}

function chooseDefaultRuntimeModel(data: typeof modelsQuery.data.value | undefined) {
  return data?.find((model) => model.defaultModel) ?? data?.[0];
}

function modelMatchesProvider(model: typeof models.value[number], provider: string) {
  return !provider || model.providerId === provider || modelValue(model).startsWith(`${provider}/`);
}

function applyRuntimeModelPreference(data: ModelInfo[] | undefined) {
  if (!data?.length) {
    if (!modelsQuery.isPending.value && !providersQuery.isPending.value) {
      selectedModel.value = "";
      persistRuntimePreference(selectedProvider.value, "");
    }
    return;
  }
  const saved = readStoredRuntimePreference();
  const savedModel = saved.model
    ? data.find((model) => modelValue(model) === saved.model && modelMatchesProvider(model, saved.provider))
    : undefined;
  const currentModel = selectedModel.value
    ? data.find((model) => modelValue(model) === selectedModel.value && modelMatchesProvider(model, selectedProvider.value))
    : undefined;
  // 模型目录是运行时可用模型的事实源；历史 localStorage 中的目录外模型必须自动回退，避免继续命中不可用 provider。
  const nextModel = savedModel ?? currentModel ?? chooseDefaultRuntimeModel(data);
  if (!nextModel) {
    selectedModel.value = "";
    persistRuntimePreference(selectedProvider.value, "");
    return;
  }
  const nextProvider = nextModel.providerId || selectedProvider.value;
  const nextValue = modelValue(nextModel);
  selectedProvider.value = nextProvider;
  selectedModel.value = nextValue;
  persistRuntimePreference(nextProvider, nextValue);
}

function selectRuntimeAgent(agentId: string) {
  selectedAgent.value = agentId;
}

let activeRunProbeSeq = 0;
let activeRunProbeTimer: ReturnType<typeof setInterval> | null = null;

function stopActiveRunProbe() {
  if (activeRunProbeTimer) {
    clearInterval(activeRunProbeTimer);
    activeRunProbeTimer = null;
  }
}

function observeRawHttpExchange(exchange: RawHttpExchange) {
  if (!isConversationRawExchange(exchange)) {
    return;
  }
  const sessionId = extractRawExchangeSessionId(exchange);
  if (!sessionId) {
    return;
  }
  const requestBody = truncateRawOutputBody(exchange.requestBody ?? "");
  appendRawOutputEntry(sessionId, {
    id: nextRawOutputId("req"),
    kind: "request",
    title: `${exchange.method} ${exchange.path}`,
    method: exchange.method,
    path: exchange.path,
    traceId: exchange.traceId,
    body: requestBody.body,
    truncated: requestBody.truncated,
    occurredAt: exchange.startedAt
  });

  const responseBody = truncateRawOutputBody(exchange.responseText ?? exchange.errorMessage ?? "");
  appendRawOutputEntry(sessionId, {
    id: nextRawOutputId("res"),
    kind: "response",
    title: `${exchange.responseStatus ?? exchange.phase.toUpperCase()} ${exchange.method} ${exchange.path}`,
    method: exchange.method,
    path: exchange.path,
    status: exchange.responseStatus,
    traceId: exchange.responseHeaders?.["x-trace-id"] ?? exchange.traceId,
    contentType: exchange.responseHeaders?.["content-type"],
    body: responseBody.body,
    truncated: responseBody.truncated,
    occurredAt: exchange.endedAt
  });
}

function observeRawRunEventMessage(message: RunEventRawMessage, fallbackSessionId?: string) {
  const parsed = parseRawJsonObject(message.data);
  const traceId = rawText(parsed?.traceId);
  const sessionId = rawRunSessionMap.value[message.runId] ?? fallbackSessionId ?? session.value?.sessionId;
  if (!sessionId) {
    return;
  }
  const body = truncateRawOutputBody(message.data);
  appendRawOutputEntry(sessionId, {
    id: nextRawOutputId("sse"),
    kind: "sse",
    title: `${message.eventName}${message.lastEventId ? ` #${message.lastEventId}` : ""}`,
    eventName: message.eventName,
    runId: message.runId,
    traceId,
    body: body.body,
    truncated: body.truncated,
    occurredAt: message.receivedAt
  });
}

function observeRunEventStreamError(runId: string, fallbackSessionId?: string) {
  const sessionId = rawRunSessionMap.value[runId] ?? fallbackSessionId ?? session.value?.sessionId;
  if (!sessionId) {
    return;
  }
  appendRawOutputEntry(sessionId, {
    id: nextRawOutputId("sse_error"),
    kind: "sse",
    title: "SSE connection error",
    eventName: "error",
    runId,
    body: "EventSource reported a connection error. The browser will keep retrying automatically; wait for a run.failed/run.succeeded/run.cancelled event for the final run state.",
    occurredAt: new Date().toISOString()
  });
}

let rawOutputSeq = 0;

function nextRawOutputId(prefix: string) {
  rawOutputSeq += 1;
  return `${prefix}_${Date.now()}_${rawOutputSeq}`;
}

function appendRawOutputEntry(sessionId: string, entry: RawOutputEntry) {
  const current = rawEntriesBySessionId.value[sessionId] ?? [];
  rawEntriesBySessionId.value = {
    ...rawEntriesBySessionId.value,
    [sessionId]: [...current, entry].slice(-RAW_OUTPUT_MAX_ENTRIES_PER_SESSION)
  };
}

function clearCurrentRawOutput() {
  const sessionId = session.value?.sessionId;
  if (!sessionId) {
    return;
  }
  rawEntriesBySessionId.value = {
    ...rawEntriesBySessionId.value,
    [sessionId]: []
  };
}

function rememberRunSession(value: Run | null | undefined) {
  if (!value?.runId || !value.sessionId) {
    return;
  }
  rawRunSessionMap.value = {
    ...rawRunSessionMap.value,
    [value.runId]: value.sessionId
  };
}

function truncateRawOutputBody(body: string): { body: string; truncated?: boolean } {
  if (body.length <= RAW_OUTPUT_BODY_LIMIT) {
    return { body };
  }
  return {
    body: `${body.slice(0, RAW_OUTPUT_BODY_LIMIT)}\n...[已截断，原始长度 ${body.length} 字符]`,
    truncated: true
  };
}

function isConversationRawExchange(exchange: RawHttpExchange) {
  const path = rawPathWithoutQuery(exchange.path);
  if (exchange.method === "POST" && path === "/api/internal/platform/opencode-runtime/sessions") {
    return true;
  }
  if (/^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/messages$/.test(path)) {
    return true;
  }
  if (/^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/active-run$/.test(path)) {
    return true;
  }
  if (exchange.method === "POST" && /^\/api\/internal\/agent\/[^/]+\/runs$/.test(path)) {
    return true;
  }
  if (exchange.method === "POST" && /^\/api\/internal\/agent\/[^/]+\/runs\/[^/]+\/cancel$/.test(path)) {
    return true;
  }
  if (exchange.method === "POST" && /^\/api\/internal\/agent\/[^/]+\/permission\/[^/]+\/reply$/.test(path)) {
    return true;
  }
  return exchange.method === "POST" && /^\/api\/internal\/agent\/[^/]+\/question\/[^/]+\/(reply|reject)$/.test(path);
}

function extractRawExchangeSessionId(exchange: RawHttpExchange): string | undefined {
  const pathSession = exchange.path.match(/^\/api\/sessions\/([^/?]+)/)?.[1];
  if (pathSession) {
    return decodeRawPathPart(pathSession);
  }
  const querySession = rawSessionIdFromUrl(exchange.url);
  if (querySession) {
    return querySession;
  }
  const request = parseRawJsonObject(exchange.requestBody);
  const requestSession = rawText(request?.sessionId);
  if (requestSession) {
    return requestSession;
  }
  const response = parseRawJsonObject(exchange.responseText);
  const responseData = rawRecord(response?.data);
  const responseSession = rawText(responseData?.sessionId);
  if (responseSession) {
    return responseSession;
  }
  const responseRunId = rawText(responseData?.runId);
  if (responseRunId && rawRunSessionMap.value[responseRunId]) {
    return rawRunSessionMap.value[responseRunId];
  }
  const pathRunId = exchange.path.match(/^\/api\/internal\/agent\/[^/]+\/runs\/([^/?]+)/)?.[1];
  if (pathRunId) {
    const runId = decodeRawPathPart(pathRunId);
    if (rawRunSessionMap.value[runId]) {
      return rawRunSessionMap.value[runId];
    }
    if (run.value?.runId === runId) {
      return session.value?.sessionId;
    }
  }
  return session.value?.sessionId;
}

function rawPathWithoutQuery(path: string) {
  return path.split("?")[0] ?? path;
}

function rawSessionIdFromUrl(url: string) {
  try {
    return new URL(url).searchParams.get("sessionId") ?? undefined;
  } catch {
    return undefined;
  }
}

function decodeRawPathPart(value: string) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function parseRawJsonObject(value: string | undefined): Record<string, unknown> | null {
  if (!value) {
    return null;
  }
  try {
    return rawRecord(JSON.parse(value));
  } catch {
    return null;
  }
}

function rawRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function rawText(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}

async function recoverActiveRunForSession(sessionId: string, reason: string): Promise<Run | null> {
  if (autoRetryStarting.value) {
    return null;
  }
  const seq = ++activeRunProbeSeq;
  try {
    const activeRun = await api.getActiveRun(sessionId);
    if (seq !== activeRunProbeSeq || session.value?.sessionId !== sessionId) {
      return null;
    }
    if (activeRun && isRunBusyStatus(activeRun.status)) {
      if (run.value?.runId !== activeRun.runId || run.value.status !== activeRun.status) {
        // 刷新、历史切换或启动请求仍未返回时，以后端 active-run 为准接管 SSE。
        run.value = activeRun;
        rememberRunSession(activeRun);
        logs.value = [...logs.value.slice(-200), `[run] recovered ${activeRun.runId} ${activeRun.status} via ${reason}`];
      }
      return activeRun;
    }
  } catch (err) {
    console.warn("自动探测活动 Run 失败", err);
  }
  return null;
}

// ===== 默认值与联动 effect =====
// 选择默认应用：优先使用「全局最近工作区」所属应用；没有可用全局 recent 时降级到已加入应用的第一项。
// 这里仅负责选应用，是否加载工作区继续由 per-app recent + 已存在 default 私人工作区决定。
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
    markdownPreviewMode.value = "off";
  }
});
watch(selectedWorkspaceIdRef, (id, previous) => {
  if (previous && previous !== id) {
    void queryClient.cancelQueries({ queryKey: ["runtime", "agents", previous], exact: true });
    chatContextStore.clearContexts();
  }
  if (id) {
    workspaceFileRouteReadyById.value = { ...workspaceFileRouteReadyById.value, [id]: false };
    void loadDirectory("", id);
    void refreshWorkspaceGitDiff();
  }
}, { immediate: true });
watch(agentsQuery.data, (data) => {
  if (!data) return;
  // 主运行 Agent 与 opencode local.agent.list() 保持一致：排除 subagent 和 hidden。
  const defaultAgent = data.find((agent) => agent.mode !== "subagent" && !agent.hidden);
  if (!defaultAgent?.agentId) {
    selectedAgent.value = "";
    return;
  }
  const currentAgent = selectedAgent.value
    ? data.find((agent) => agent.agentId === selectedAgent.value || agent.name === selectedAgent.value)
    : undefined;
  if (!currentAgent || currentAgent.mode === "subagent" || currentAgent.hidden) {
    selectedAgent.value = defaultAgent.agentId;
  }
});
watch(providersQuery.data, (data) => {
  const savedProvider = readStoredRuntimePreference().provider;
  if (savedProvider && data?.some((p) => p.providerId === savedProvider)) {
    selectedProvider.value = savedProvider;
    return;
  }
  if (data?.some((p) => p.providerId === selectedProvider.value)) {
    return;
  }
  if (data?.[0]?.providerId) {
    selectedProvider.value = data[0].providerId;
    persistRuntimePreference(selectedProvider.value, selectedModel.value);
  }
});
watch(allModels, (data) => {
  applyRuntimeModelPreference(data);
}, { immediate: true });
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
  refreshAgentsCatalog();
  if (selectedWorkspaceId.value) {
    void loadDirectory("", selectedWorkspaceId.value, true);
  }
  if (selectedAppId.value && !selectedWorkspaceId.value && !retryingWorkspaceAfterOpencodeReady) {
    retryingWorkspaceAfterOpencodeReady = true;
    void handleSelectApp(selectedAppId.value).finally(() => {
      retryingWorkspaceAfterOpencodeReady = false;
    });
  }
});
watch([() => selectedProvider.value, () => selectedModel.value, allModels], ([provider, model, data]) => {
  if (!provider || !model || String(model).startsWith(`${provider}/`)) {
    return;
  }
  const nextModel = data.find((m) => m.providerId === provider);
  if (nextModel) {
    const val = modelValue(nextModel);
    selectedModel.value = val;
    persistRuntimePreference(provider, val);
  }
});

// ===== RunEvent SSE 订阅：Run 处于运行/取消中时建立，卸载/状态变化时关闭 =====
watch(run, (r, _old, onCleanup) => {
  if (!r || !isRunBusyStatus(r.status)) {
    return;
  }
  const subscription = subscribeRunEvents({
    baseUrl: apiBaseUrl,
    runId: r.runId,
    onRawMessage: (message) => observeRawRunEventMessage(message, r.sessionId),
    onEvent: (event) => {
      if (ignoredRunIds.value.has(event.runId)) {
        return;
      }
      if (!runEventMatchesRun(event, r.runId, run.value)) {
        return;
      }
      handleRunEvent(event);
    },
    onStatus: (status) => {
      logs.value = [...logs.value.slice(-200), `[sse] ${status}`];
    },
    onError: () => {
      if (run.value?.runId === r.runId && isRunBusyStatus(run.value.status)) {
        const message = "浏览器事件流连接异常，前端会等待自动重连；如后端确认失败，会继续收到 run.failed。";
        feedback.value = { kind: "error", title: RUN_EVENT_SSE_ERROR_TITLE, description: message };
        if (!reportedRunEventStreamErrors.has(r.runId)) {
          reportedRunEventStreamErrors.add(r.runId);
          observeRunEventStreamError(r.runId, r.sessionId);
          dispatchChat({ type: "run.stream.error", runId: r.runId, message });
        }
      }
    }
  });
  onCleanup(() => subscription.close());
});

// 自动恢复 Session 的活动 Run，确保页面刷新、挂载或重新连入时仍能通过 SSE 接管后台执行。
watch(
  () => session.value?.sessionId,
  async (sessionId) => {
    if (!sessionId) return;
    if (!isRunBusyStatus(run.value?.status)) {
      await recoverActiveRunForSession(sessionId, "session-watch");
    }
  },
  { immediate: true }
);

// agent 写文件用的 opencode 工具名；这些工具的 input 带文件路径，完成时磁盘已写入。
const LIVE_WRITE_TOOLS = new Set(["write", "edit", "apply_patch", "str_replace", "multi_edit", "create_file", "delete"]);

// 实时追踪只关注新完成的写文件工具签名，避免流式文本更新触发整棵消息树深度扫描。
const liveToolScanSignature = computed(() => {
  const signatures: string[] = [];
  for (const message of chatState.value.messages) {
    if (message.role === "assistant") {
      for (const part of message.parts ?? []) {
        if (part.type !== "tool" || part.status !== "completed" || !LIVE_WRITE_TOOLS.has(part.toolName)) {
          continue;
        }
        const path = liveToolPath(part);
        if (path) {
          signatures.push(`${part.partId}:${part.status}:${path}`);
        }
      }
      continue;
    }
    if (message.role === "card" && message.cardType === "tool") {
      const part = toolCardToVirtualPart(message);
      if (!part || part.status !== "completed" || !LIVE_WRITE_TOOLS.has(part.toolName)) {
        continue;
      }
      const path = liveToolPath(part);
      if (path) {
        signatures.push(`${part.partId}:${part.status}:${path}`);
      }
    }
  }
  return signatures.join("|");
});

watch(liveToolScanSignature, () => scanLiveToolParts());
// 开启时重置已跟随记录，并立即扫描当前对话里已完成的历史写文件工具。
watch(liveTrack, (on) => {
  liveFollowedParts.value = new Set();
  if (on) {
    scanLiveToolParts();
  }
});
// 新 Run 开始时清空已跟随记录。
watch(run, (r) => {
  rememberRunSession(r);
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
    if (!selectedWorkspace.value) {
      throw new Error("未选择 Workspace");
    }
    await api.writeFile(selectedWorkspace.value.workspaceId, tab.path, tab.content);
    return tab;
  },
  onSuccess: (tab) => {
    workbench.markTabSaved(tab.path, tab.content);
    feedback.value = { kind: "success", title: "文件已保存", description: tab.path };
    if (!isAgentFilePath(tab.path)) {
      void refreshWorkspaceGitDiff();
    }
  },
  onError: (error) => {
    feedback.value = errorFeedback("保存文件失败", error);
  }
});

const AGENT_PUBLIC_FILE_PREFIX = "agent-public:";
const AGENT_WORKSPACE_FILE_PREFIX = "agent-workspace:";
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
  mutationFn: async (input: {
    prompt: string;
    parts: PromptPart[];
    title?: string;
    command?: { command: string; arguments: string };
  }) => {
    if (!opencodeProcessReady.value) {
      throw new Error("请先初始化 TestAgent 进程");
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
      mode: promptMode.value,
      command: input.command?.command,
      arguments: input.command?.arguments
    });
  },
  onSuccess: (started) => {
    run.value = started;
    rememberRunSession(started);
    logs.value = [...logs.value, `[run] ${started.runId} ${started.status}`];
  },
  onError: (error) => {
    const startFailureFeedback = errorFeedback("启动 Run 失败", error);
    feedback.value = startFailureFeedback;
    dispatchChat({ type: "run.request.failed", message: startFailureFeedback.description });
    // Session 创建或 Run HTTP 提交失败时没有 RunEvent 终态，前端需要本地锁定本轮耗时。
    if (chatStartedAt.value) {
      totalDurationMs.value += Date.now() - chatStartedAt.value;
      lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
      chatStartedAt.value = null;
      nowTick.value = Date.now();
    }
    if (session.value?.sessionId && !isRunBusyStatus(run.value?.status)) {
      void recoverActiveRunForSession(session.value.sessionId, "start-run-error");
    }
  }
});

// startRun 的 HTTP 请求可能因为首次拉起 runtime 或长任务初始化而迟迟不返回；此时后端
// 可能已经创建了非终态 Run。pending 期间轮询 active-run，尽早拿到 runId 并订阅 SSE。
watch(
  [() => startRunMutation.isPending.value, () => session.value?.sessionId, () => run.value?.status],
  ([pending, sessionId, status]) => {
    stopActiveRunProbe();
    if (!pending || !sessionId || isRunBusyStatus(status)) {
      return;
    }
    void recoverActiveRunForSession(sessionId, "start-run-pending");
    activeRunProbeTimer = setInterval(() => {
      if (!startRunMutation.isPending.value || !session.value?.sessionId || isRunBusyStatus(run.value?.status)) {
        stopActiveRunProbe();
        return;
      }
      void recoverActiveRunForSession(session.value.sessionId, "start-run-pending");
    }, ACTIVE_RUN_PROBE_INTERVAL_MS);
  },
  { immediate: true }
);

const initializeOpencodeProcessMutation = useMutation({
  mutationFn: (operationId?: string) => api.initializeMyOpencodeProcess(operationId),
  onSuccess: (status, operationId) => {
    queryClient.setQueryData(opencodeProcessQueryKey.value, status);
    if (operationId) {
      void refreshProcessStartupOperation(operationId);
    }
    if (status.status === "READY") {
      stopProcessStartupPolling();
      processStartupDialogOpen.value = false;
    }
  },
  onError: (error, operationId) => {
    void (async () => {
      try {
        const refreshed = await opencodeProcessQuery.refetch();
        if (refreshed.data?.status === "READY") {
          queryClient.setQueryData(opencodeProcessQueryKey.value, refreshed.data);
          feedback.value = { kind: "info", title: "TestAgent 进程可用", description: refreshed.data.serviceAddress ?? refreshed.data.message };
          stopProcessStartupPolling();
          processStartupDialogOpen.value = false;
          return;
        }
      } catch {
        // 保留原始初始化错误，避免复查失败吞掉真正原因。
      }
      if (operationId) {
        await refreshProcessStartupOperation(operationId);
      }
      failLocalProcessStartupOperation(error);
      stopProcessStartupPolling();
      feedback.value = errorFeedback("初始化 TestAgent 进程失败", error);
    })();
  }
});

// Run 与 reducer 可能因网络时序短暂不一致；明确终态优先，避免完成后的残留 shimmer。
const runtimeBusy = computed(() =>
  isRuntimeBusy(run.value?.status, chatState.value.status, startRunMutation.isPending.value)
);
const timelineRuntimeStatusForPanel = computed(() => {
  const status = chatState.value.runtimeStatus;
  if (!status || status.type !== "retry") {
    return status;
  }
  void nowTick.value;
  return {
    ...status,
    retryAfterSeconds: retryCountdownSeconds(status, nowTick.value, retryDeadlines.value)
  };
});
const canStopRun = computed(() => Boolean(run.value && isRunBusyStatus(run.value.status) && !cancelRunMutation.isPending.value));
const stopDisabledReason = computed(() => {
  if (cancelRunMutation.isPending.value) return "正在终止";
  if (!run.value) return "当前没有可终止的运行";
  if (!isRunBusyStatus(run.value.status)) return "当前运行已结束";
  return "";
});

function lockCurrentRunDuration() {
  if (!chatStartedAt.value) {
    return;
  }
  totalDurationMs.value += Date.now() - chatStartedAt.value;
  lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
  chatStartedAt.value = null;
  nowTick.value = Date.now();
}

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
// tokens 优先用累计值，fallback 到 run 终态事件 payload 中的字段以保持向后兼容。
const taskUsage = computed<{ duration?: string; tokens?: number; totalDuration?: string }>(() => {
  // 引用 nowTick 以触发每秒重算
  void nowTick.value;
  const usage: { duration?: string; tokens?: number; totalDuration?: string } = {};
  if (chatStartedAt.value) {
    usage.duration = formatDurationMs(Date.now() - chatStartedAt.value);
  } else if (lastDuration) {
    usage.duration = lastDuration;
  }
  const tokens = accumulatedTokens.value > 0 ? accumulatedTokens.value : lastTokens;
  if (tokens > 0) {
    usage.tokens = tokens;
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

// 扫描 chatState.messages，累计 step-finish tokens。
// 一次 Run 内多次 step-finish 会重复累加，与 opencode 上报的每轮消耗一致。
function recomputeUsageFromChat() {
  let tokens = 0;
  for (const message of chatState.value.messages) {
    if (message.role !== "assistant") continue;
    for (const part of message.parts ?? []) {
      if (part.type === "step-finish") {
        tokens += part.tokens?.total ?? 0;
      }
    }
  }
  accumulatedTokens.value = tokens;
}

const usageScanSignature = computed(() =>
  chatState.value.messages
    .map((message) => {
      if (message.role !== "assistant") return "";
      return (message.parts ?? [])
        .map((part) => {
          if (part.type === "step-finish") {
            return `${part.partId}:step-finish:${part.tokens?.total ?? 0}`;
          }
          return "";
        })
        .filter(Boolean)
        .join(",");
    })
    .filter(Boolean)
    .join("|")
);

watch(usageScanSignature, () => recomputeUsageFromChat());

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

watch(
  () => chatState.value.runtimeStatus,
  (status) => {
    if (!status || status.type !== "retry") {
      retryActionInFlightKey.value = null;
      return;
    }
    const resolved = resolveRetryDeadline(retryDeadlines.value, status, Date.now());
    if (resolved.deadlines !== retryDeadlines.value) {
      retryDeadlines.value = resolved.deadlines;
    }
  },
  { immediate: true }
);

watch(
  [() => chatState.value.runtimeStatus, nowTick],
  () => {
    const status = chatState.value.runtimeStatus;
    if (!status || status.type !== "retry") {
      return;
    }
    const resolved = resolveRetryDeadline(retryDeadlines.value, status, nowTick.value);
    if (resolved.deadlines !== retryDeadlines.value) {
      retryDeadlines.value = resolved.deadlines;
    }
    const decision = retryExpirationDecision(status, nowTick.value, retryDeadlines.value);
    if (decision === "wait") {
      return;
    }
    const key = retryActionKey(status);
    if (retryActionInFlightKey.value === key) {
      return;
    }
    retryActionInFlightKey.value = key;
    if (shouldFailExhaustedRetry(status, nowTick.value, retryDeadlines.value)) {
      failRetryingRun(status.message ?? "重试 3 次后仍然失败");
      return;
    }
    handleAutoRetryRun();
  },
  { immediate: true }
);

function retryActionKey(status: { retryKey?: string; attempt?: number; message?: string }): string {
  return status.retryKey ?? `${status.attempt ?? 0}:${status.message ?? ""}`;
}

function failRetryingRun(message: string) {
  const occurredAt = new Date().toISOString();
  dispatchChat({
    type: "event",
    event: syntheticEvent("run.failed", { error: { name: "RetryExhausted", message }, status: "FAILED" }, occurredAt)
  });
  if (run.value && isRunBusyStatus(run.value.status)) {
    run.value = { ...run.value, status: "FAILED", updatedAt: occurredAt };
  }
  if (chatStartedAt.value) {
    totalDurationMs.value += Date.now() - chatStartedAt.value;
    lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
    chatStartedAt.value = null;
    nowTick.value = Date.now();
  }
}

function handleAutoRetryRun() {
  const prepared = prepareAutoRetryRun(run.value, lastRunDraft.value, new Date().toISOString());
  if (prepared.type === "missing-draft") {
    feedback.value = { kind: "error", title: "自动重试失败", description: "未找到上一条任务内容，请重新输入后发送" };
    failRetryingRun("未找到可自动重试的任务内容");
    return;
  }
  autoRetryStarting.value = true;
  if (prepared.cancelRunId) {
    ignoredRunIds.value = new Set([...ignoredRunIds.value, prepared.cancelRunId]);
    void api.cancelRun(prepared.cancelRunId).catch(() => undefined);
  }
  if (prepared.localRun) {
    run.value = prepared.localRun;
  }
  clearRunEventSseFeedback();
  lastRunDraft.value = prepared.input;
  dispatchChat({ type: "run.requested" });
  startRunMutation.mutate(prepared.input, {
    onSettled: () => {
      autoRetryStarting.value = false;
    }
  });
}

// follow-up 队列：Run 空闲且有排队 prompt 时自动出队执行
watch(
  [followUpQueue, run, session, () => startRunMutation.isPending.value, opencodeProcessReady],
  () => {
    if (
      followUpQueue.value.length === 0 ||
      !opencodeProcessReady.value ||
      !canStartFollowUp(run.value, startRunMutation.isPending.value)
    ) {
      return;
    }
    const { next, queue } = dequeueFollowUp(followUpQueue.value);
    if (!next) {
      return;
    }
    followUpQueue.value = queue;
    const draft: AutoRetryRunDraft = { prompt: next.prompt, parts: next.parts, command: next.command };
    lastRunDraft.value = draft;
    dispatchChat({ type: "run.requested" });
    startRunMutation.mutate(draft);
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
      clearAutoRetryState();
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
    rememberRunSession(cancelled);
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
  workspaceLoadGeneration++;
  clearFileTreeRetryTimers();
  entriesByDirectory.value = {};
  expandedDirectories.value = new Set();
  loadingPath.value = new Set();
  fileTreeError.value = null;
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
  // 模型和 Provider 是用户级运行偏好，刷新后切回 recent workspace 时不能清空。
  markdownPreviewMode.value = "off";
  // 切工作区时同步清掉任务消耗计时与上一轮终态展示，避免旧 Run 的 token/duration 残留。
  chatStartedAt.value = null;
  accumulatedTokens.value = 0;
  totalDurationMs.value = 0;
  lastDuration = undefined;
  lastTokens = 0;
  nowTick.value = Date.now();
  clearAutoRetryState();
  dispatchChat({ type: "reset" });
  // 切工作区时清掉个人工作区 ID，避免旧版本的空 ID 残留导致提交/推送指向错误目标。
  selectedWorkspaceSnapshot.value = undefined;
  currentPersonalWorkspaceId.value = undefined;
  currentPersonalWorkspaceBranch.value = undefined;
  workbench.resetWorkspaceView();
}

function rememberPersonalWorkspace(personalWorkspaceId?: string, personalWorkspaceBranch?: string) {
  currentPersonalWorkspaceId.value = personalWorkspaceId;
  currentPersonalWorkspaceBranch.value = personalWorkspaceBranch;
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

const tabPathToClose = ref<string | null>(null);
const showUnsavedConfirm = ref(false);

function handleCloseTab(path: string) {
  const tab = workbench.tabs.find((t) => t.path === path);
  if (tab && !tab.livePreview && tab.content !== tab.savedContent) {
    tabPathToClose.value = path;
    showUnsavedConfirm.value = true;
  } else {
    workbench.closeTab(path);
  }
}

function handleCloseTabs(paths: string[]) {
  for (const path of paths) {
    const tab = workbench.tabs.find((t) => t.path === path);
    if (!tab) continue;
    if (!tab.livePreview && tab.content !== tab.savedContent) {
      tabPathToClose.value = path;
      showUnsavedConfirm.value = true;
      return;
    }
    workbench.closeTab(path);
  }
}

function confirmCloseTab() {
  if (tabPathToClose.value) {
    workbench.closeTab(tabPathToClose.value);
  }
  showUnsavedConfirm.value = false;
  tabPathToClose.value = null;
}

function cancelCloseTab() {
  showUnsavedConfirm.value = false;
  tabPathToClose.value = null;
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
  selectedWorkspaceSnapshot.value = workspace;
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
      rememberPersonalWorkspace(defaultPw.personalWorkspaceId, defaultPw.personalWorkspaceBranch);
      feedback.value = { kind: "info", title: "已在该版本工作区", description: `${payload.version.version} (个人空间: default)` };
      return;
    }
    const workspace = await api.getWorkspace(runtimeWorkspaceId);
    await applyManagedWorkspace(workspace, {
      successTitle: "已切换应用版本",
      successDescription: `${payload.template.workspaceName} · ${payload.version.version} (个人空间: default)`
    });
    rememberPersonalWorkspace(defaultPw.personalWorkspaceId, defaultPw.personalWorkspaceBranch);
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

// 查询指定应用下的"默认进入工作空间"：只有 per-app recent 能反查到 versionId 时才进入 default 私人 worktree。
// 无历史或历史不带 versionId 时只选择应用，不自动创建/加载工作区。
async function pickDefaultWorkspaceForApp(appId: string): Promise<{ workspace: Workspace; isFallback: boolean; personalWorkspaceId?: string; personalWorkspaceBranch?: string } | null> {
  const recent = await api.getRecentManagedWorkspaceForApplication(appId);
  if (recent?.versionId) {
    // 登录/切应用默认加载是只读选择：只使用已存在的 default 私人工作区，不在无历史时创建或修复。
    const personalWorkspaces = await api.listPersonalWorkspaces(recent.versionId);
    const defaultPw = personalWorkspaces.find((workspace) =>
      workspace.workspaceName === "default" && Boolean(workspace.runtimeWorkspace?.workspaceId)
    );
    if (!defaultPw) {
      return null;
    }
    return {
      workspace: defaultPw.runtimeWorkspace,
      isFallback: false,
      personalWorkspaceId: defaultPw.personalWorkspaceId,
      personalWorkspaceBranch: defaultPw.branch
    };
  }
  return null;
}

// WorkbenchFooter / FigmaFileExplorer 上两级菜单展开模板时调用，触发版本懒加载。
function handleLoadVersions(templateId: string) {
  ensureAppVersionsLoaded(templateId);
}

function refreshCurrentWorkspacePanels() {
  if (!selectedWorkspace.value) return;
  void loadDirectory("", undefined, true);
  void refreshWorkspaceGitDiff();
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
    if (defaultPw.runtimeWorkspace?.workspaceId) {
      await applyManagedWorkspace(defaultPw.runtimeWorkspace, {
        successTitle: "已切换应用版本",
        successDescription: `${payload.template.workspaceName} · ${response.version}`
      });
      rememberPersonalWorkspace(defaultPw.personalWorkspaceId, defaultPw.personalWorkspaceBranch);
    } else {
      rememberPersonalWorkspace(defaultPw.personalWorkspaceId, defaultPw.personalWorkspaceBranch);
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
  if (selectingAppId === appId) {
    return;
  }
  const selectionSeq = ++appSelectionSeq;
  selectingAppId = appId;
  // 切换应用时先清空旧 workspace 状态，避免文件树继续展示上一个应用的 workspace 内容
  resetWorkspaceState();
  selectedWorkspaceId.value = undefined;
  selectedAppId.value = appId;
  try {
    // 只有当前用户当前应用 recent 能反查到 versionId 时，才加载对应 default 私人 worktree。
    // 无历史时只切应用并保持工作区空态，footer 仍可新增版本或选择私人工作区。
    const pick = await pickDefaultWorkspaceForApp(appId);
    if (selectionSeq !== appSelectionSeq) {
      return;
    }
    if (pick) {
      await applyManagedWorkspace(pick.workspace);
      if (selectionSeq !== appSelectionSeq) {
        return;
      }
      rememberPersonalWorkspace(pick.personalWorkspaceId, pick.personalWorkspaceBranch);
      return;
    }
    // 应用没有可用 recent/versionId 时保持空态，不回退到普通本机目录选择。
  } catch (error) {
    const currentApp = applicationCatalog.value.find((app) => app.appId === appId);
    feedback.value = errorFeedback("切换应用失败", error, { appId, appName: currentApp?.appName });
  } finally {
    if (selectionSeq === appSelectionSeq) {
      selectingAppId = undefined;
    }
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
  force = false,
  retryCount = 0,
  generation = workspaceLoadGeneration
) {
  if (!workspaceId || !workspaceLoadIsCurrent(
    workspaceId,
    generation,
    selectedWorkspaceIdRef.value,
    workspaceLoadGeneration
  )) {
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
    if (!workspaceLoadIsCurrent(workspaceId, generation, selectedWorkspaceIdRef.value, workspaceLoadGeneration)) {
      return;
    }
    entriesByDirectory.value = { ...entriesByDirectory.value, [path]: entries };
    if (path === "") {
      workspaceFileRouteReadyById.value = { ...workspaceFileRouteReadyById.value, [workspaceId]: true };
      // 根目录加载成功后清除面板内错误
      fileTreeError.value = null;
    }
  } catch (error) {
    if (!workspaceLoadIsCurrent(workspaceId, generation, selectedWorkspaceIdRef.value, workspaceLoadGeneration)) {
      return;
    }
    // 根目录加载失败：设置面板内错误，保留上次成功数据
    if (path === "") {
      if (error instanceof BackendApiError && ["OPENCODE_UNAVAILABLE", "OPENCODE_BAD_GATEWAY"].includes(error.code)) {
        workspaceFileRouteReadyById.value = { ...workspaceFileRouteReadyById.value, [workspaceId]: false };
      }
      // 指数退避重试：最多重试 3 次，间隔 1s, 2s, 4s
      if (retryCount < 3) {
        const delay = Math.pow(2, retryCount) * 1000;
        fileTreeError.value = `加载文件树失败，${delay / 1000} 秒后重试...`;
        const timer = setTimeout(() => {
          fileTreeRetryTimers.delete(timer);
          void loadDirectory(path, workspaceId, force, retryCount + 1, generation);
        }, delay);
        fileTreeRetryTimers.add(timer);
      } else {
        // 重试耗尽，显示错误和手动重试按钮
        fileTreeError.value = error instanceof BackendApiError ? error.message : "加载文件树失败";
      }
    } else {
      // 非根目录加载失败：从展开集合里把这条目录回滚掉
      if (expandedDirectories.value.has(path)) {
        const nextExpanded = new Set(expandedDirectories.value);
        nextExpanded.delete(path);
        expandedDirectories.value = nextExpanded;
      }
    }
  } finally {
    if (workspaceLoadIsCurrent(workspaceId, generation, selectedWorkspaceIdRef.value, workspaceLoadGeneration)) {
      const cleared = new Set(loadingPath.value);
      cleared.delete(path);
      loadingPath.value = cleared;
    }
  }
}

function clearFileTreeRetryTimers() {
  for (const timer of fileTreeRetryTimers) {
    clearTimeout(timer);
  }
  fileTreeRetryTimers.clear();
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

async function handleCreateEntry(directory: string, name: string, type: "file" | "directory") {
  if (!selectedWorkspace.value) {
    return;
  }
  const workspaceId = selectedWorkspace.value.workspaceId;
  // 判断目录使用的路径分隔符
  const sep = directory.includes("\\") ? "\\" : "/";
  const fullPath = directory ? `${directory}${sep}${name}` : name;
  try {
    if (type === "directory") {
      await api.createDirectory(workspaceId, fullPath);
      // 新创建的空目录默认没有子项，记录为空数组以避免显示展开箭头
      entriesByDirectory.value = { ...entriesByDirectory.value, [fullPath]: [] };
    } else {
      await api.writeFile(workspaceId, fullPath, "");
    }
    await loadDirectory(directory, undefined, true);
    if (type === "file") {
      await openFile(fullPath);
    }
  } catch (error) {
    feedback.value = errorFeedback(`创建${type === "file" ? "文件" : "文件夹"}失败`, error);
  }
}

async function handleDeleteEntry(path: string, type: "file" | "directory") {
  if (!selectedWorkspace.value) {
    return;
  }
  const workspaceId = selectedWorkspace.value.workspaceId;
  try {
    await api.deleteWorkspaceFile(workspaceId, path);
    // 同时支持 / 和 \ 作为路径分隔符
    const lastSepIndex = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
    const parentDir = lastSepIndex >= 0 ? path.slice(0, lastSepIndex) : "";

    // 先从父目录条目中移除被删除的项，确保前端立即更新
    const currentEntries = entriesByDirectory.value[parentDir] ?? [];
    const filtered = currentEntries.filter((e) => e.path !== path);
    entriesByDirectory.value = { ...entriesByDirectory.value, [parentDir]: filtered };

    // 如果被删除的是目录，同时清理其子目录缓存和展开状态
    if (type === "directory") {
      const nextEntries = { ...entriesByDirectory.value };
      for (const key of Object.keys(nextEntries)) {
        if (key === path || key.startsWith(`${path}/`) || key.startsWith(`${path}\\`)) {
          delete nextEntries[key];
        }
      }
      entriesByDirectory.value = nextEntries;
      if (expandedDirectories.value.has(path)) {
        const nextExpanded = new Set(expandedDirectories.value);
        nextExpanded.delete(path);
        expandedDirectories.value = nextExpanded;
      }
    }

    // 如果被删除的是当前打开的文件，关闭标签页
    if (type === "file" && activePath.value === path) {
      workbench.closeTab(`file:${path}`);
    }
  } catch (error) {
    feedback.value = errorFeedback(`删除${type === "file" ? "文件" : "文件夹"}失败`, error);
  }
}

async function handlePreviewContext(item: ChatContextItem) {
  if (!item.path) {
    return;
  }
  await openFile(item.path);
  if (item.type === "selection") {
    setTimeout(() => {
      codeEditorRef.value?.revealSelection({
        startLine: item.startLine,
        endLine: item.endLine,
        text: item.text
      });
    }, 150);
  }
}

function fileNameOf(path: string): string {
  return path.split(/[\\/]+/).filter(Boolean).at(-1) ?? path;
}

function notifyChatContextValidation(result: { ok: true } | { ok: false; reason: string }, successTitle?: string) {
  if (!result.ok) {
    feedback.value = { kind: "info", title: "无法添加上下文", description: result.reason };
    return;
  }
  if (successTitle) {
    feedback.value = { kind: "success", title: successTitle };
  }
}

function addCurrentSelectionToChatContext() {
  if (!activeTab.value?.path || !editorSelection.value?.text.trim()) {
    feedback.value = { kind: "info", title: "请先选中文本", description: "在编辑器中选中代码或文本后再添加到对话。" };
    return;
  }
  const tab = activeTab.value;
  const selection = editorSelection.value;
  const text = selection.text;
  const result = chatContextStore.addSelectionContext({
    id: createContextId(),
    type: "selection",
    source: "workspace",
    path: tab.path,
    fileName: fileNameOf(tab.path),
    language: languageFromPath(tab.path),
    startLine: selection.startLineNumber,
    endLine: selection.endLineNumber,
    text,
    charCount: text.length,
    createdAt: Date.now()
  });
  notifyChatContextValidation(result, "已添加选区上下文");
}

function looksBinaryContent(content: string): boolean {
  if (!content) return false;
  if (content.includes("\u0000")) return true;
  const sample = content.slice(0, 4096);
  let control = 0;
  for (let i = 0; i < sample.length; i += 1) {
    const code = sample.charCodeAt(i);
    if (code < 32 && code !== 9 && code !== 10 && code !== 13) {
      control += 1;
    }
  }
  return sample.length > 0 && control / sample.length > 0.08;
}

async function addWorkspaceFileToChatContext(path: string) {
  if (!selectedWorkspace.value) {
    feedback.value = { kind: "info", title: "未选择工作区", description: "请先切换到可用工作区。" };
    return;
  }
  try {
    const file = await api.readFile(selectedWorkspace.value.workspaceId, path);
    if (looksBinaryContent(file.content)) {
      feedback.value = { kind: "info", title: "暂不支持添加二进制文件", description: path };
      return;
    }
    const content = file.content;
    const lineCount = content.length === 0 ? 0 : content.split("\n").length;
    const result = chatContextStore.addFileContext({
      id: createContextId(),
      type: "file",
      source: "workspace",
      path,
      fileName: fileNameOf(path),
      language: languageFromPath(path),
      content,
      charCount: content.length,
      lineCount,
      sizeBytes: new Blob([content]).size,
      createdAt: Date.now()
    });
    notifyChatContextValidation(result, "已添加文件上下文");
  } catch (error) {
    feedback.value = errorFeedback("添加文件上下文失败", error);
  }
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
  if (readonlySessionReason.value) {
    feedback.value = { kind: "info", title: "当前会话只读", description: readonlySessionReason.value };
    return;
  }
  if (!opencodeProcessReady.value) {
    // 与聊天面板状态卡一致：按 serviceStatus 区分"未分配 / 未运行"提示
    const assignedServerName = opencodeProcessStatus.value?.linuxServerId?.trim();
    const assignedServiceAddress = opencodeProcessStatus.value?.serviceAddress?.trim() || opencodeProcessStatus.value?.baseUrl?.trim();
    const svc =
      opencodeProcessStatus.value?.serviceStatus ??
      (opencodeProcessStatus.value?.status === "READY"
        ? "RUNNING"
        : assignedServerName || assignedServiceAddress
          ? "NOT_RUNNING"
          : "UNASSIGNED");
    const procTitle =
      svc === "NOT_RUNNING"
        ? "TestAgent 专属进程未运行"
        : svc === "UNASSIGNED"
        ? "尚未分配 TestAgent 专属进程"
        : "请先初始化 TestAgent 进程";
    feedback.value = {
      kind: "info",
      title: procTitle,
      description: opencodeProcessStatus.value?.message ?? "正在检查当前用户可用进程"
    };
    return;
  }
  if (!selectedWorkspace.value) {
    feedback.value = { kind: "info", title: "未选择工作区", description: "请先切换到应用版本或个人工作区，再发送任务。" };
    return;
  }
  const sendValidation = validateChatSend(prompt.trim(), chatContextStore.items);
  if (!sendValidation.ok) {
    feedback.value = { kind: "info", title: "上下文过长", description: sendValidation.reason };
    return;
  }
  const chatContextParts = chatContextItemsToPromptParts(chatContextStore.items);
  // 显式上下文附件存在时，不再叠加旧的“当前活动编辑器/选区”隐式 PromptPart，
  // 避免同一选区或整个活动文件在本轮请求中重复进入模型上下文。
  const implicitEditorTab = chatContextStore.items.length === 0 ? activeTab.value : undefined;
  const implicitEditorSelection = chatContextStore.items.length === 0 ? editorSelection.value : undefined;
  const selectionContexts = chatContextStore.items.filter((item): item is Extract<ChatContextItem, { type: "selection" }> => item.type === "selection");
  const displayParts = buildPromptParts(prompt, implicitEditorTab, attachments, [...chatContextParts, ...diffContextParts.value], implicitEditorSelection);
  const displayPrompt = prompt.trim() || promptFromParts(displayParts);
  const rawSubmitPrompt = prompt.trim() || displayPrompt;
  // 选区文本直接作为结构化 prompt 发送，避免 opencode 将其回放成整文件附件或触发原生文件读取。
  const submitPrompt = selectionContexts.length > 0 ? serializeChatContexts(rawSubmitPrompt, selectionContexts) : rawSubmitPrompt;
  // prompt_async 有 parts 时只发送 parts；selection 必须进入 text part，不能只放在顶层 prompt。
  const parts = buildPromptParts(submitPrompt, implicitEditorTab, attachments, [...chatContextParts, ...diffContextParts.value], implicitEditorSelection);
  if (chatContextStore.items.length > 0) {
    console.debug("workspace_context_send_prepared", {
      component: "AgentWorkbench",
      action: "send_prompt",
      contextCount: chatContextStore.items.length,
      selectionContextCount: selectionContexts.length,
      attachmentsCount: attachments.length,
      diffContextCount: diffContextParts.value.length,
      partsCount: parts.length,
      promptChars: prompt.trim().length,
      contexts: summarizeChatContextItems(chatContextStore.items),
      parts: summarizePromptParts(parts)
    });
  }
  lastPrompt.value = submitPrompt;
  diffContextParts.value = [];
  dispatchChat({ type: "user.submitted", prompt: displayPrompt, parts });
  if (!submitPrompt) {
    return;
  }
  // 启动计时 + 重置任务消耗累计（lastDuration/lastTokens 保留上一轮终态以供刷新对比）
  chatStartedAt.value = Date.now();
  accumulatedTokens.value = 0;
  // 解析命令（包括 Skill Command，格式为 /skill-name）
  const command = parseCommand(prompt, promptMode.value);
  if (runtimeBusy.value) {
    followUpQueue.value = enqueueFollowUp(followUpQueue.value, createFollowUpDraft(submitPrompt, parts, undefined, command ?? undefined));
    feedback.value = { kind: "info", title: "Prompt 已排队", description: `等待当前 Run 完成后继续执行，队列 ${followUpQueue.value.length} 条` };
    chatContextStore.clearContexts();
    return;
  }
  // slash 技能和普通消息统一创建平台 Run，才能复用 SSE、刷新恢复和终止能力。
  const runDraft: AutoRetryRunDraft = { prompt: submitPrompt, parts, title: displayPrompt, command: command ?? undefined };
  lastRunDraft.value = runDraft;
  clearRunEventSseFeedback();
  dispatchChat({ type: "run.requested" });
  chatContextStore.clearContexts();
  startRunMutation.mutate(runDraft);
}

function summarizePromptParts(parts: PromptPart[]) {
  return parts.map((part) => {
    if (part.type === "file") {
      return {
        type: part.type,
        path: part.path,
        name: part.name,
        mimeType: part.mimeType,
        hasContent: Boolean(part.content),
        hasUrl: Boolean(part.url),
        source: part.source
          ? {
              contextType: part.source.contextType,
              startLine: part.source.startLine,
              endLine: part.source.endLine,
              hasText: Boolean(part.source.text)
            }
          : undefined
      };
    }
    if (part.type === "text") {
      return { type: part.type, charCount: part.text.length };
    }
    return { type: part.type };
  });
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

/** 失败后的所有重试入口统一复用最近一次 prompt，避免子组件事件无人接收。 */
function handleRetryRun() {
  const prompt = lastPrompt.value.trim();
  if (!prompt) {
    feedback.value = {
      kind: "info",
      title: "无法重试",
      description: "未找到上一条任务内容，请重新输入后发送"
    };
    return;
  }
  handleSend(prompt);
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
      centerMode.value = nextCenterModeAfterRunDiff(centerMode.value, diffSource.value);
      diffSource.value = "run";
      diffFiles.value = mergeDiffFiles(diffFiles.value, files);
      workbench.setSelectedDiffPath(files[0]?.path);
      files.forEach((file) => refreshParentDirectory(file.path));
      void refreshWorkspaceGitDiff();
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
      centerMode.value = nextCenterModeAfterRunDiff(centerMode.value, diffSource.value);
      diffSource.value = "session";
      diffFiles.value = mergeDiffFiles(diffFiles.value, files);
      workbench.setSelectedDiffPath(files[0]?.path);
      files.forEach((file) => refreshParentDirectory(file.path));
      void refreshWorkspaceGitDiff();
    }
  } else if (event.type === "run.succeeded" || event.type === "run.failed" || event.type === "run.cancelled") {
    if (event.type === "run.succeeded") {
      clearRunEventSseFeedback();
    }
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
    // 计算任务消耗统计：duration 由 chatStartedAt 锁定，tokens 仍优先取累计值；
    // 如果后端 payload 直接带上 tokens 字段，则覆盖一次（向后兼容未来后端实现）。
    if (chatStartedAt.value) {
      totalDurationMs.value += Date.now() - chatStartedAt.value;
      lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
      chatStartedAt.value = null;
    }
    const payload = event.payload as Record<string, unknown>;
    if (typeof payload.tokens === "number") {
      lastTokens = payload.tokens;
    }
    if (run.value?.sessionId) {
      // 实时 RunEvent 中的 message id 来自 opencode，反馈接口需要平台落库后的 messageId。
      // 终态后异步刷新 Session 快照，把 remoteMessageId 映射到平台 messageId 后再开放反馈按钮。
      void refreshPersistedFeedbackIdentities(run.value.sessionId);
    }
    // 触发 taskUsage 重新计算
    nowTick.value = Date.now();
  }
}

function hasUnmappedAssistantRemoteMessages(): boolean {
  return chatState.value.messages.some((message) => {
    if (message.role !== "assistant" || platformMessageIdForAgentMessage(message)) {
      return false;
    }
    const remoteMessageId = remoteMessageIdForAgentMessage(message);
    return Boolean(remoteMessageId && !platformMessageIdsByRemoteId.value[remoteMessageId]);
  });
}

async function refreshPersistedFeedbackIdentities(sessionId: string, attempt = 1): Promise<void> {
  try {
    const page = await api.listSessionMessages(sessionId, 1, 100, { refresh: false });
    if (session.value?.sessionId !== sessionId) {
      return;
    }
    const persistedMessages = dedupeSessionMessages(page.items);
    rememberPersistedMessageIdentities(persistedMessages);
    await loadFeedbacksForMessages(persistedMessages, sessionId);
    if (attempt < 3 && hasUnmappedAssistantRemoteMessages()) {
      setTimeout(() => void refreshPersistedFeedbackIdentities(sessionId, attempt + 1), 500);
    }
  } catch {
    if (attempt < 3 && session.value?.sessionId === sessionId) {
      setTimeout(() => void refreshPersistedFeedbackIdentities(sessionId, attempt + 1), 500);
    }
  }
}

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

// 双击 Tab 页：在左侧文件树中展开并滚动定位到对应文件
function handleLocateFile(path: string) {
  if (!path) return;
  workbench.setActivePath(path);
  expandPathToFile(path);
  void nextTick(() => {
    scrollToActiveFileTreeRow();
    setTimeout(scrollToActiveFileTreeRow, 100);
    setTimeout(scrollToActiveFileTreeRow, 300);
  });
}

function scrollToActiveFileTreeRow() {
  const activeRowEl = document.querySelector(".ta-file-tree-scroll .ta-file-tree-row.is-active") as HTMLElement | null;
  if (activeRowEl) {
    activeRowEl.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }
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
      nextFiles = await loadWorkspaceGitDiffFiles();
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

async function loadWorkspaceGitDiffFiles(): Promise<RunDiffFile[]> {
  if (!selectedWorkspace.value) return [];
  const gitDiff = await api.getWorkspaceGitDiff(selectedWorkspace.value.workspaceId);
  return gitDiff.files.map((file) => ({
    path: file.path,
    status: file.status,
    patch: file.patch,
    additions: file.additions,
    deletions: file.deletions
  }));
}

async function refreshOpenWorkspaceTabsFromDisk(paths?: string[]) {
  if (!selectedWorkspace.value) {
    return;
  }
  const workspaceId = selectedWorkspace.value.workspaceId;
  const pathFilter = paths && paths.length > 0 ? new Set(paths) : null;
  const workspaceTabs = workbench.tabs.filter(
    (tab: EditorTab) =>
      !tab.livePreview &&
      !isAgentFilePath(tab.path) &&
      (!pathFilter || pathFilter.has(tab.path))
  );
  const previousActivePath = activePath.value;
  for (const tab of workspaceTabs) {
    try {
      const file = await api.readFile(workspaceId, tab.path);
      workbench.openTab({
        ...tab,
        content: file.content,
        savedContent: file.content,
        readonly: file.readonly
      });
    } catch (error) {
      if (error instanceof BackendApiError && error.code === "NOT_FOUND") {
        workbench.closeTab(tab.path);
      }
    }
  }
  if (previousActivePath && workbench.tabs.some((tab: EditorTab) => tab.path === previousActivePath)) {
    workbench.setActivePath(previousActivePath);
  }
}

let workspaceGitDiffRefreshToken = 0;

async function refreshWorkspaceGitDiff(options: {
  reloadOpenFiles?: boolean;
  paths?: string[];
  files?: WorkspaceGitDiffFile[];
} = {}) {
  const token = ++workspaceGitDiffRefreshToken;
  const workspaceId = selectedWorkspaceIdRef.value;
  try {
    const nextFiles = options.files
      ? options.files.map((file) => ({
          path: file.path,
          status: file.status,
          patch: file.patch,
          additions: file.additions,
          deletions: file.deletions
        }))
      : await loadWorkspaceGitDiffFiles();
    if (token !== workspaceGitDiffRefreshToken || workspaceId !== selectedWorkspaceIdRef.value) return;
    vcsDiffFiles.value = nextFiles;
    if (diffSource.value === "vcs") {
      diffFiles.value = nextFiles;
      centerMode.value = nextCenterModeAfterVcsRefresh(centerMode.value, diffSource.value, nextFiles);
      if (!workbench.selectedDiffPath || !nextFiles.some((file) => file.path === workbench.selectedDiffPath)) {
        workbench.setSelectedDiffPath(nextFiles[0]?.path);
      }
    }
    if (options.reloadOpenFiles) {
      await refreshOpenWorkspaceTabsFromDisk(options.paths);
    }
  } catch {
    // 保存后刷新 Git diff 只是辅助 UI 同步；失败时保留"文件已保存"的主结果，
    // 用户仍可点击 Diff 刷新按钮看到后端返回的具体错误。
  }
}

async function handleOpenDiff(payload: string | {
  path: string;
  source: "vcs" | "agent";
  scope?: "PUBLIC" | "WORKSPACE";
  file?: RunDiffFile;
}) {
  if (typeof payload === "string") {
    await loadDiffSource("vcs");
    workbench.setSelectedDiffPath(payload);
  } else {
    if (payload.source === "vcs") {
      // Git 面板已经持有当前文件的 patch，优先直接打开，避免重复扫描仓库造成空白 VCS 过渡态。
      if (payload.file) {
        const cachedFiles = vcsDiffFiles.value.some((file) => file.path === payload.path)
          ? vcsDiffFiles.value
          : [payload.file];
        diffSource.value = "vcs";
        centerMode.value = "diff";
        diffFiles.value = cachedFiles;
        workbench.setSelectedDiffPath(payload.path);
        return;
      }
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
  const switchSeq = ++historySwitchSeq;
  historyLoadingSessionId.value = sessionId;
  const selected = sessionsItems.value.find((item) => item.sessionId === sessionId) ?? (await api.getSession(sessionId));
  const readonlyReason = await switchToHistorySessionWorkspace(selected);
  session.value = selected;
  readonlySessionReason.value = readonlyReason;
  // 切换会话后先清空上一轮任务的消耗统计，防止上一轮对话的耗时残留。
  chatStartedAt.value = null;
  accumulatedTokens.value = 0;
  totalDurationMs.value = 0;
  lastDuration = undefined;
  lastTokens = 0;
  nowTick.value = Date.now();
  clearAutoRetryState();
  try {
    const [treeSnapshot, page] = await Promise.all([
      api.getSessionTreeMessages(sessionId).catch(() => null),
      api.listSessionMessages(sessionId, 1, 100, { refresh: false })
    ]);
    if (switchSeq !== historySwitchSeq) {
      return;
    }
    const persistedMessages = dedupeSessionMessages(page.items);
    rememberPersistedMessageIdentities(persistedMessages);
    const restoredState = treeSnapshot ? chatStateFromSessionTreeSnapshot(treeSnapshot, persistedMessages) : null;
    if (restoredState && restoredState.messages.length > 0) {
      chatState.value = restoredState;
    } else {
      dispatchChat({ type: "reset", messages: messagesFromSessionMessages(persistedMessages) });
    }
    historyLoadingSessionId.value = null;
    // 正文是历史切换的首要结果；反馈状态随后补齐，不阻塞用户阅读。
    void loadFeedbacksForMessages(persistedMessages, sessionId);
    const restoredFiles = diffFilesFromSessionMessages(persistedMessages).map((file) => ({
      ...file,
      path: normalizeWorkspacePath(file.path) || file.path
    }));
    diffFiles.value = restoredFiles;

    // 寻找最新的 runId 从而恢复 Run 状态与文件 Diff
    const lastMsgWithRunId = [...persistedMessages].reverse().find((m) => m.runId);
    if (lastMsgWithRunId?.runId) {
      try {
        const [runDetail, diffDetail] = await Promise.all([
          api.getRun(lastMsgWithRunId.runId),
          api.getRunDiff(lastMsgWithRunId.runId).catch(() => ({ files: [] }))
        ]);
        run.value = runDetail;
        rememberRunSession(runDetail);
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

    // 优先通过 getActiveRun 获取当前最新活跃的非终态活动 Run（如正在后台运行的任务）来重建连接。
    await recoverActiveRunForSession(sessionId, "switch-session");

    feedback.value = { kind: "info", title: "已切换 Session", description: selected.title };
  } catch (error) {
    feedback.value = errorFeedback("加载 Session 消息失败", error);
  } finally {
    if (switchSeq === historySwitchSeq) {
      historyLoadingSessionId.value = null;
    }
  }
}

async function switchToHistorySessionWorkspace(selected: Session): Promise<string> {
  const expectedAppId = selected.workspaceContext?.appId?.trim();
  const requiresManagedWorkspace = Boolean(expectedAppId);
  try {
    let workspace = await api.getWorkspace(selected.workspaceId);
    try {
      // markRecentManagedWorkspace 会校验当前用户仍可进入历史会话所属应用，并回填版本/模板信息。
      const response = await api.markRecentManagedWorkspace(selected.workspaceId);
      if (response) {
        workspace = mergeRecentRuntimeResponse(workspace, response);
      }
    } catch (error) {
      if (error instanceof BackendApiError && error.code === "NOT_FOUND" && !requiresManagedWorkspace) {
        // 非托管旧工作区没有应用 recent 关系时允许直接切运行态工作区。
      } else {
        throw error;
      }
    }
    const nextAppId = workspace.appId || expectedAppId;
    if (nextAppId) {
      selectedAppId.value = nextAppId;
    }
    if (workspace.workspaceId !== selectedWorkspaceIdRef.value) {
      await switchWorkspace(workspace);
    } else {
      cacheWorkspace(workspace);
      selectedWorkspaceSnapshot.value = workspace;
      syncCurrentVersionFromWorkspace(workspace);
    }
    return "";
  } catch (error) {
    feedback.value = errorFeedback("切换 Session 工作区失败", error);
    return readonlyReasonForHistorySwitch(error);
  }
}

function readonlyReasonForHistorySwitch(error: unknown): string {
  if (error instanceof BackendApiError) {
    if (error.code === "FORBIDDEN") {
      return "你已不属于该会话所属应用，当前会话只读。";
    }
    if (error.code === "NOT_FOUND") {
      return "会话所属应用或工作空间已不可用，当前会话只读。";
    }
  }
  return "无法切换到会话所属工作空间，当前会话只读。";
}

function rememberCurrentRunAsBackgroundRuntimeState() {
  const currentSession = session.value;
  const currentRun = run.value;
  if (!currentSession || !currentRun || !isRunBusyStatus(currentRun.status)) {
    return;
  }
  const existing = sessionRuntimeState.value?.sessions.find((item) => item.sessionId === currentSession.sessionId);
  const nextState: SessionRuntimeState = {
    sessionId: currentSession.sessionId,
    runId: currentRun.runId,
    runStatus: currentRun.status,
    attention: existing?.attention ?? null,
    attentionEventId: existing?.attentionEventId ?? null,
    attentionAt: existing?.attentionAt ?? null,
    updatedAt: currentRun.updatedAt ?? new Date().toISOString()
  };
  const nextSessions = [
    nextState,
    ...(sessionRuntimeState.value?.sessions ?? []).filter((item) => item.sessionId !== currentSession.sessionId)
  ];
  const nextSummary: SessionRuntimeStateSummary = {
    runningCount: nextSessions.length,
    questionCount: nextSessions.filter((item) => item.attention === "QUESTION").length,
    sessions: nextSessions,
    generatedAt: new Date().toISOString()
  };
  sessionRuntimeState.value = nextSummary;
  queryClient.setQueryData(sessionRuntimeStateQueryKey, nextSummary);
}

function handleNewConversation() {
  rememberCurrentRunAsBackgroundRuntimeState();
  session.value = null;
  run.value = null;
  void queryClient.invalidateQueries({ queryKey: ["sessions"] });
  clearAutoRetryState();
  dispatchChat({ type: "reset" });
  messageFeedbacks.value = {};
  feedbackSubmitting.value = {};
  platformMessageIdsByRemoteId.value = {};
  readonlySessionReason.value = "";
  diffFiles.value = [];
  
  // 新建对话后清空任务消耗统计，防止上一轮对话的耗时残留。
  chatStartedAt.value = null;
  accumulatedTokens.value = 0;
  totalDurationMs.value = 0;
  lastDuration = undefined;
  lastTokens = 0;
  nowTick.value = Date.now();
}

async function loadFeedbacksForMessages(
  messages: Array<Pick<SessionMessage, "messageId" | "role" | "remoteMessageId">>,
  expectedSessionId?: string
) {
  rememberPersistedMessageIdentities(messages);
  const assistantMessageIds = messages
    .filter(message => message.role === "ASSISTANT" && isPlatformSessionMessageId(message.messageId))
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
  if (!expectedSessionId || session.value?.sessionId === expectedSessionId) {
    messageFeedbacks.value = loaded;
  }
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
    :joinable-apps="joinableApps"
    :selected-app-id="selectedAppId"
    :current-user-name="authStore.currentUser?.username"
    :current-user-role-labels="authStore.currentUser?.roleLabels"
    :opencode-process-status="opencodeProcessStatus"
    :opencode-process-loading="opencodeProcessInitialLoading"
    :runtime-inventory="runtimeInventoryForShell"
    @toggle-left-panel="leftPanelOpen = !leftPanelOpen"
    @toggle-right-panel="rightPanelOpen = !rightPanelOpen"
    @select-app="handleSelectApp"
    @refresh-opencode-process="refreshOpencodeProcessStatus"
    @logout="handleLogout"
    @join-app="handleJoinApp"
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
          class="managed-workspace-files"
          :workspace-name="selectedWorkspace?.name ?? '未选择工作区'"
          :workspace-root-path="selectedWorkspace?.rootPath"
          :entries-by-directory="entriesByDirectory"
          :expanded-directories="expandedDirectories"
          :active-path="activePath"
          :changed-files="vcsDiffFiles"
          :loading-path="loadingPath"
          :app-name="selectedManagedApplication?.appName"
          :app-templates="appTemplatesWithVersions"
          :selected-version-id="selectedVersionId"
          :loading-app-templates="loadingAppTemplates"
          :loading-app-versions="loadingAppVersions"
          :creating-version="creatingVersion"
          :can-write="isSuperAdmin"
          :api-base-url="apiBaseUrl"
          :workspace-id="selectedWorkspace?.workspaceId"
          :personal-workspace-id="currentPersonalWorkspaceId"
          :personal-workspace-branch="currentPersonalWorkspaceBranch"
          :show-server-workspace-switch="isSuperAdmin"
          :search-results="searchResults"
          :search-loading="searchLoading"
          :search-keyword="searchKeyword"
          :file-tree-error="fileTreeError"
          :user-id="authStore.currentUser?.userId"
          @toggle-directory="toggleDirectory"
          @open-file="openFile"
          @add-file-context="addWorkspaceFileToChatContext"
          @open-diff="handleOpenDiff"
          @refresh="refreshCurrentWorkspacePanels"
          @changes-refreshed="(payload) => refreshWorkspaceGitDiff({
            reloadOpenFiles: payload?.reloadOpenFiles ?? true,
            paths: payload?.paths,
            files: payload?.files
          })"
          @select-version="handleSelectVersion"
          @load-versions="handleLoadVersions"
          @create-version="handleCreateVersion"
          @open-agent-file="openAgentFile"
          @open-server-workspace-picker="openServerWorkspacePicker"
          @search="handleFileSearch"
          @create-entry="handleCreateEntry"
          @delete-entry="handleDeleteEntry"
        />
      </div>
      <div v-else class="managed-workspace-empty">
        <p>请选择应用后进入应用版本或个人工作区。</p>
      </div>
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
            :app-name="selectedManagedApplication?.appName"
            :templates="appTemplatesWithVersions"
            :selected-version-id="selectedVersionId"
            :personal-workspace-branch="currentPersonalWorkspaceBranch"
            :loading-templates="loadingAppTemplates"
            :loading-versions="loadingAppVersions"
            :creating-version="creatingVersion"
            :show-server-workspace-switch="isSuperAdmin"
            show-save
            @save="() => diffViewerRef?.handleSave()"
            @locate="handleLocateFile"
            @select-version="handleSelectVersion"
            @load-versions="handleLoadVersions"
            @create-version="handleCreateVersion"
            @open-server-workspace-picker="openServerWorkspacePicker"
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
          :app-name="selectedManagedApplication?.appName"
          :templates="appTemplatesWithVersions"
          :selected-version-id="selectedVersionId"
          :personal-workspace-branch="currentPersonalWorkspaceBranch"
          :loading-templates="loadingAppTemplates"
          :loading-versions="loadingAppVersions"
          :creating-version="creatingVersion"
          :show-server-workspace-switch="isSuperAdmin"
          :markdown-preview="markdownPreview"
          :markdown-preview-mode="markdownPreviewMode"
          @activate="(path: string) => workbench.setActivePath(path)"
          @locate-file="handleLocateFile"
          @close="handleCloseTab"
          @close-many="handleCloseTabs"
          @add-file-context="addWorkspaceFileToChatContext"
          @editor-action="() => {}"
          @save="() => activeTab && !activeTab.livePreview && saveMutation.mutate(activeTab)"
          @select-version="handleSelectVersion"
          @load-versions="handleLoadVersions"
          @create-version="handleCreateVersion"
          @open-server-workspace-picker="openServerWorkspacePicker"
          @update:markdown-preview="(value: boolean) => { if (!value) markdownPreviewMode = 'off'; else if (markdownPreviewMode === 'off') markdownPreviewMode = 'split'; }"
          @update:markdown-preview-mode="(mode: PreviewMode) => (markdownPreviewMode = mode)"
        >
          <CodeEditor
            ref="codeEditorRef"
            :path="activeTab?.path"
            :content="activeTab?.content"
            :dirty="activeTab && !activeTab.livePreview ? activeTab.content !== activeTab.savedContent : false"
            :readonly="activeTab?.readonly"
            :saving="saveMutation.isPending.value"
            :show-preview="markdownPreview"
            :preview-mode="markdownPreviewMode"
            @change="(content: string) => activeTab && workbench.updateTabContent(activeTab.path, content)"
            @save="() => activeTab && !activeTab.livePreview && saveMutation.mutate(activeTab)"
            @add-selection-context="addCurrentSelectionToChatContext"
            @selection-change="(selection: EditorSelectionContext | undefined) => (editorSelection = selection)"
          />
        </FigmaEditorArea>
      </main>
    </template>

    <template #chat>
      <div class="managed-chat-panel">
        <FigmaChatPanel
          :messages="chatMessagesForPanel"
          :streaming-text-by-part-id="chatState.streamingTextByPartId"
          :message-scopes-by-id="chatState.messageScopesById"
          :subagents-by-session-id="chatState.subagentsBySessionId"
          :subagent-by-task-part-id="chatState.subagentByTaskPartId"
          :running="runtimeBusy"
          :runtime-status="chatState.status ?? run?.status"
          :timeline-runtime-status="timelineRuntimeStatusForPanel"
          :title="chatTitle"
          :file-changes="diffFiles"
          :task-usage="taskUsage"
          :history="historyList"
          :history-search="sessionSearch"
          :history-total="sessionHistoryTotal"
          :history-has-more="sessionHistoryHasMore"
          :history-loading-more="sessionHistoryLoadingMore"
          :history-loading="Boolean(historyLoadingSessionId)"
          :history-running-count="sessionRuntimeState?.runningCount ?? 0"
          :history-question-count="sessionRuntimeState?.questionCount ?? 0"
          :readonly-reason="readonlySessionReason"
          :process-status="opencodeProcessStatus"
          process-required
          :process-loading="opencodeProcessInitialLoading"
          :process-refreshing="opencodeProcessRefreshing"
          :process-initializing="initializeOpencodeProcessMutation.isPending.value"
          :permissions="chatState.permissions"
          :questions="chatState.questions"
          :todos="chatState.todos"
          :chat-contexts="chatContextStore.items"
          :chat-context-total-chars="chatContextStore.totalCharCount"
          :chat-context-over-limit="chatContextStore.isOverLimit"
          :chat-context-error="chatContextStore.lastError"
          :selected-model-label="selectedModelLabel"
          :model-picker-disabled="false"
          :agents="agents"
          :agents-loading="agentsLoading"
          :agents-refreshing="agentsRefreshing"
          :agents-error="agentsError"
          :selected-agent="selectedAgent"
          :stop-disabled="!canStopRun"
          :stop-disabled-reason="stopDisabledReason"
          :models="models"
          :providers="providers"
          :selected-model="selectedModel"
          :message-feedbacks="messageFeedbacks"
          :feedback-submitting="feedbackSubmitting"
          :commands="commands"
          :raw-output-entries="currentRawOutputEntries"
          placeholder="描述测试任务，例如：跑 checkout 模块并分析失败原因"
          @send="(text: string) => handleSend(text)"
          @stop="handleStopRun"
          @retry="handleRetryRun"
          @new-conversation="handleNewConversation"
          @open-history="refreshHistoryOnOpen"
          @history-search-change="handleHistorySearchChange"
          @load-more-history="loadMoreHistory"
          @initialize-process="beginInitializeOpencodeProcess"
          @open-diff="(path: string) => { if (path) workbench.setSelectedDiffPath(path); centerMode = 'diff'; }"
          @open-file="openFile"
          @preview-context="handlePreviewContext"
          @reply-permission="(requestId: string, decision: 'once' | 'always' | 'reject') => replyPermissionMutation.mutate({ requestId, decision })"
          @reply-question="(requestId: string, answers: unknown[]) => replyQuestionMutation.mutate({ requestId, answers })"
          @reject-question="(requestId: string) => rejectQuestionMutation.mutate(requestId)"
          @select-session="(id: string) => switchSession(id)"
          @change-agent="selectRuntimeAgent"
          @refresh-agents="refreshAgentsCatalog"
          @select-model="(model) => selectRuntimeModel(model)"
          @submit-feedback="handleSubmitFeedback"
          @clear-raw-output="clearCurrentRawOutput"
          @remove-chat-context="chatContextStore.removeContext"
          @clear-chat-contexts="chatContextStore.clearContexts"
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
            @retry="handleRetryRun"
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

  <OpencodeProcessStartupDialog
    :open="processStartupDialogOpen"
    :action-label="processStartupActionLabel"
    :operation="processStartupOperation"
    @close="processStartupDialogOpen = false"
  />

  <!-- 未保存二次确认弹窗 -->
  <div v-if="showUnsavedConfirm" class="ta-confirm-backdrop" role="presentation">
    <div class="ta-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
      <div class="ta-confirm-body">
        <h3 id="confirm-title" class="ta-confirm-title">未保存的修改</h3>
        <p class="ta-confirm-desc">该文件有未保存的修改，关闭将丢失这些修改。确定要关闭吗？</p>
      </div>
      <div class="ta-confirm-footer">
        <button type="button" class="ta-btn-cancel" @click="cancelCloseTab">取消</button>
        <button type="button" class="ta-btn-confirm" @click="confirmCloseTab">确认关闭</button>
      </div>
    </div>
  </div>
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

.ta-confirm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(1px);
}

.ta-confirm-dialog {
  width: 320px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid var(--ta-border);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.ta-confirm-body {
  padding: 20px 20px 16px 20px;
}

.ta-confirm-title {
  font-size: 15px;
  font-weight: 600;
  color: #111;
  margin: 0;
}

.ta-confirm-desc {
  font-size: 13px;
  color: #666;
  margin: 8px 0 0 0;
  line-height: 1.5;
}

.ta-confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px 16px 20px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}

.ta-btn-cancel {
  font-size: 13px;
  font-weight: 500;
  color: #555;
  background: #fff;
  border: 1px solid #dcdcdc;
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.ta-btn-cancel:hover {
  background: #f5f5f5;
  border-color: #ccc;
}

.ta-btn-confirm {
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: #f97316; /* 橙色 */
  border: 1px solid transparent;
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
  transition: background-color 0.12s ease;
}

.ta-btn-confirm:hover {
  background: #ea580c; /* 深橙色 */
}
</style>
