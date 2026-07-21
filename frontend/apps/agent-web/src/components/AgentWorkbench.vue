<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, onScopeDispose, provide, ref, shallowRef, watch } from "vue";
import { useRouter } from "vue-router";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/vue-query";
import {
  AgentChat,
  buildComposerPromptParts,
  createInitialAgentChatRuntimeState,
  promptPartsForUserDisplay,
  reduceAgentChatRuntime,
  type ComposerAttachment
} from "@test-agent/agent-chat";
import {
  BackendApiError,
  createBackendApiClient,
  type CreateNightExecutionTaskPayload,
  type RawHttpExchange
} from "@test-agent/backend-api";
import { DiffViewer, parseUnifiedPatch } from "@test-agent/diff-viewer";
import { CodeEditor, languageFromPath, type EditorSelectionContext } from "@test-agent/editor";
import { subscribeRunEvents, subscribeSessionRuntimeState, type RunEventRawMessage } from "@test-agent/event-stream-client";
import { BookOpenText, Code2, MessageSquare, Monitor } from "lucide-vue-next";
import { Setting as ElSetting } from "@element-plus/icons-vue";
import type {
  AgentMessage,
  AiRunFeedback,
  AiRunFeedbackPayload,
  ApplicationWorkspaceTemplate,
  ApplicationWorkspaceVersion,
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
  NightExecutionSlots,
  NightExecutionTask,
  NightExecutionTaskQueryResponse,
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
  WorkspaceGitDiffFile,
  WorkspaceViewEntry,
  WorkspaceViewWarning
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
import FirstLoginGuide from "./FirstLoginGuide.vue";
import FigmaFileExplorer from "./FigmaFileExplorer.vue";
import FigmaEditorArea from "./FigmaEditorArea.vue";
import {
  agentFileInfo,
  agentTabPath,
  isAgentFilePath,
  shouldReloadPersonalRuntimeCatalog,
  type AgentFileTabInfo,
  type AgentFileLoadRequest
} from "./agentFileLoad";
import {
  isReferenceFilePath,
  referenceFileInfo,
  referenceReadFailurePatch,
  referenceTabPath
} from "./referenceFileLoad";
import {
  collectWorkspaceViewWarnings,
  copiedWorkspaceFileTargetPath,
  migrateWorkspaceViewRefreshTargets,
  ROOT_WORKSPACE_VIEW_TARGET,
  referenceChatPath,
  revalidatedWorkspaceViewRefreshTarget,
  resolveWorkspaceViewLoadTarget,
  workspaceViewAncestorDirectoryIds,
  workspaceViewContextIsCurrent,
  workspaceViewEntries,
  workspaceFileRefreshSettlements,
  workspaceViewRefreshTargets,
  type WorkspaceViewLoadTarget,
  type WorkspaceViewWarningSnapshot
} from "./workspaceViewState";
import FigmaChatPanel from "./FigmaChatPanel.vue";
import HelpCenterDialog from "./HelpCenterDialog.vue";
import { buildManualQuestionPrompt, DEFAULT_HELP_TOPIC } from "./help-center";
import { type PreviewMode } from "./WorkbenchFooter.vue";
import OpencodeProcessStartupDialog from "./OpencodeProcessStartupDialog.vue";
import ReferenceConfigurationDialog from "./ReferenceConfigurationDialog.vue";
import { canShowReferenceConfiguration } from "./reference-configuration-access";
import SettingsDialog from "./settings/SettingsDialog.vue";
import ServerWorkspacePickerDialog from "./ServerWorkspacePickerDialog.vue";
import SystemManagementWrapper from "./SystemManagementWrapper.vue";
import WorkbenchFooter from "./WorkbenchFooter.vue";
import { notifyFeedback } from "./notify";
import { appendLatestRawOutputEntry, prepareRawOutputBody } from "./raw-output";
import {
  createRuntimeStateOutageTracker,
  type RuntimeStateFallbackLease
} from "./runtime-state-outage";
import {
  createClientRequestId,
  createConversationRunContextCache,
  startRunWithConversationContext
} from "./conversation-run-context";
import { useSideQuestionRun } from "./useSideQuestionRun";
import { canStartFollowUp, createFollowUpDraft, dequeueFollowUp, enqueueFollowUp, isRunBusyStatus, isRuntimeBusy, type FollowUpDraft } from "./follow-up-queue";
import {
  assistantSummaryMessageId,
  buildPromptParts,
  chatStateFromSessionTreeSnapshot,
  dedupeSessionMessages,
  diffFilesFromPayload,
  diffFilesFromSessionMessages,
  errorFeedback,
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
  PUBLIC_CONFIG_GATE_REFETCH_INTERVAL_MS,
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
  projectRootInteractionSession,
  reconcileCurrentTurnTodos,
  isSupersededInteractionAsk,
  runEventProjectionMode,
  runEventProjection,
  runEventSubscriptionRunId,
  runEventSubscriptionSessionId,
  runtimeResources,
  runtimeStatus,
  platformSessionTitleFromSynchronizedEventPayload,
  sessionTitleEventMatchesCurrentSession,
  sessionTitleFromFirstMessage,
  shouldResetAfterNightTaskClosure,
  shouldFailExhaustedRetry,
  syntheticEvent,
  text,
  workspaceRequirementReferences,
  workspaceRequirementStageDirectories,
  workspaceLoadIsCurrent,
  type AutoRetryRunDraft,
  type OpencodeAvailabilityState,
  type RetryDeadlineMap,
  type WorkspaceRequirementReference
} from "./workbench-utils";

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
// 只保存当前页面生命周期内的 binding 提示，避免刷新或切换用户后沿用旧服务器。
const routeLinuxServerId = ref("");
const routeLinuxServerResolved = ref(false);
const api = createBackendApiClient({
  baseUrl: apiBaseUrl,
  routeLinuxServerId: () => routeLinuxServerId.value,
  rawExchangeObserver: observeRawHttpExchange
});
const conversationRunContexts = createConversationRunContextCache((sessionId) => api.getRunContext(sessionId));
provide("api", api);
const queryClient = useQueryClient();
const workbench = useWorkbenchStore();
const authStore = useAuthStore();
const chatContextStore = useChatContextStore();
const router = useRouter();
const OPENCODE_PROCESS_START_OPERATION_POLL_INTERVAL_MS = 500;
const AGENT_CATALOG_REQUEST_TIMEOUT_MS = 8000;
const RUN_EVENT_SSE_ERROR_TITLE = "RunEvent SSE 连接异常";
const RUN_EVENT_TERMINAL_SETTLE_MS = 500;
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
const RUNTIME_STATE_RECOVERY_STABLE_MS = 5_000;
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
const isAppAdmin = computed(() =>
  isSuperAdmin.value || authStore.currentUser?.roles?.includes("APP_ADMIN") === true
);

const FIRST_LOGIN_GUIDE_STORAGE_VERSION = "v7";
const firstLoginGuideActive = ref(true);

function firstLoginGuideStorageKey(userId: string) {
  return `test-agent.onboarding.${FIRST_LOGIN_GUIDE_STORAGE_VERSION}:${userId}`;
}

function hasSeenFirstLoginGuide(userId: string) {
  try {
    return localStorage.getItem(firstLoginGuideStorageKey(userId)) === "seen";
  } catch {
    return false;
  }
}

watch(
  () => authStore.currentUser?.userId?.trim() || null,
  (userId) => {
    // 登录态尚未加载时先保持抑制，避免进程状态面板抢在引导组件之前闪现。
    if (!userId) return;
    firstLoginGuideActive.value = !hasSeenFirstLoginGuide(userId);
  },
  { immediate: true }
);

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
const workspaceViewDirectoryById = new Map<string, WorkspaceViewEntry>();
const workspaceViewNodeIdByTabPath = new Map<string, string>();
const workspaceViewWarningByDirectory = new Map<string, WorkspaceViewWarningSnapshot>();
const workspaceViewWarnings = ref<WorkspaceViewWarning[]>([]);
// 文件树面板内错误状态，不覆盖全局顶部反馈
const fileTreeError = ref<string | null>(null);
let workspaceLoadGeneration = 0;
let workspaceFileReadSequence = 0;
const latestWorkspaceFileReadByPath = new Map<string, number>();
let agentFileLoadGeneration = 0;
let agentFileReadSequence = 0;
const latestAgentFileReadByPath = new Map<string, number>();
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
// 对话 @ 文件候选独立于左侧文件搜索，避免输入提示覆盖文件树的搜索词和结果。
const workspaceFileCandidates = ref<FileSearchResult[]>([]);
const workspaceFileCandidatesLoading = ref(false);
let workspaceFileCandidateTimer: ReturnType<typeof setTimeout> | null = null;
let workspaceFileCandidateSeq = 0;
// # 候选只按当前个人 worktree 的“spec/需求项/阶段/同名子条目”聚合，每次重新打开面板时刷新。
const workspaceRequirementCandidates = ref<WorkspaceRequirementReference[]>([]);
const workspaceRequirementCandidatesLoading = ref(false);
let workspaceRequirementLoadSeq = 0;
const session = shallowRef<Session | null>(null);
const run = shallowRef<Run | null>(null);
const nightTasks = ref<NightExecutionTask[]>([]);
const nightVisibleFailure = shallowRef<NightExecutionTask | null>(null);
const nightSlots = shallowRef<NightExecutionSlots | null>(null);
const nightSlotsLoading = ref(false);
const nightTaskSubmitting = ref(false);
const nightTaskActionPending = ref<Record<string, boolean>>({});
const recentlyCreatedNightTask = shallowRef<NightExecutionTask | null>(null);
let nightTaskRefreshSequence = 0;
let nightSlotRequestSequence = 0;
let nightTaskPollingTimer: ReturnType<typeof setInterval> | null = null;
let nightCreateIdempotency: {
  signature: string;
  clientRequestId: string;
  runClientRequestId: string;
} | null = null;
// OpenCode 原生 title agent 会在 root Run 成功后异步发出 session.updated。
// 该 Run 处于待命名状态时，保留既有 Run SSE，直到后端确认标题已持久化或显式关闭监听。
const pendingSessionTitleRunId = ref<string | null>(null);
// 冲突终态可能在同一批 durable replay 中被根事实纠正；hold 先于 status 投影建立，避免连接抖动。
const terminalRunEventSubscriptionHoldRunId = ref<string | null>(null);
let terminalRunEventSubscriptionHoldTimer: ReturnType<typeof setTimeout> | null = null;
// 新 Run HTTP 响应返回前，旧 Run 可能仍因标题同步保持订阅；该 ID 用于截断其对话投影。
const supersededConversationRunId = ref<string | null>(null);
const rawEntriesBySessionId = ref<Record<string, RawOutputEntry[]>>({});
const rawRunSessionMap = ref<Record<string, string>>({});
const reportedRunEventStreamErrors = new Set<string>();
// legacy 终态重放只能为每个逻辑 Run 启动一条 messages -> feedback 批量恢复链。
const legacyFeedbackRecoveryRunIds = new Set<string>();
const lastPrompt = ref("");
const lastRunDraft = ref<AutoRetryRunDraft | null>(null);
// 仅当前页面真实发出的未决启动请求可以为 runtime-state 接管提供 Todo owner；跨标签页 Run 不猜归属。
const pendingRequestedRunUserMessageId = ref<string | null>(null);
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
// 所有个人运行态重载共用一把响应式锁，手动入口和自动保存入口不会并发 dispose。
const runtimeReloadLock = ref<"PUBLIC" | "WORKSPACE" | "REFERENCE" | null>(null);
const personalRuntimeReloading = computed<"PUBLIC" | "WORKSPACE" | null>(() =>
  runtimeReloadLock.value === "PUBLIC" || runtimeReloadLock.value === "WORKSPACE"
    ? runtimeReloadLock.value
    : null
);
// 后端权威闸门可能捕获前端 SSE 尚未到达的并发 Session；CONFLICT 时保留代次并等待空闲后重试。
const runtimeReloadConflictWaitingForIdle = ref(false);
let runtimeReloadConflictRetryTimer: ReturnType<typeof setTimeout> | null = null;
// 引用配置保存可能发生在 Run 执行中；记录代次并等用户全部 Session 空闲后 dispose。
const pendingReferenceRuntimeReloadRevision = ref(0);
let handledReferenceRuntimeReloadRevision = 0;
let pendingRuntimeReloadKind: "reference" | "agent" = "reference";
let pendingPublicRuntimeReloadTarget: Pick<AgentFileTabInfo, "worktreeId" | "linuxServerId"> | null = null;
let lastRuntimeReloadError: unknown | null = null;
const diffViewerRef = ref<InstanceType<typeof DiffViewer> | null>(null);
const isDiffDirty = ref(false);
const sessionSearch = ref("");
const sessionHistoryPage = ref(1);
const sessionHistoryItems = ref<Session[]>([]);
const sessionRuntimeState = shallowRef<SessionRuntimeStateSummary | null>(null);
// 历史会话的 pending 快照比 durable ask 回放更新；仅屏蔽快照之前已经失效的 requestId。
const interactionSnapshotBySessionId = new Map<string, {
  synchronizedAtMs: number;
  permissionRequestIds?: Set<string>;
  questionRequestIds?: Set<string>;
}>();
const runtimeStateOutages = createRuntimeStateOutageTracker(RUNTIME_STATE_RECOVERY_STABLE_MS);
let conversationInteractionGeneration = 0;
// 必须早于认证 token 的 immediate watch 初始化，统一 interaction 失效时才能安全释放历史切换锁。
const historyLoadingSessionId = ref<string | null>(null);
// 正文首屏可以提前结束 loading，但完整历史投影完成前仍必须独立阻止发送。
const historySwitchingSessionId = ref<string | null>(null);
let historySwitchSeq = 0;
let activeRunProbeSeq = 0;
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
type WorkspaceUndoOperation =
  | { kind: "delete"; paths: string[]; label: string }
  | { kind: "move"; sourcePath: string; targetPath: string; label: string };
// 撤销历史只属于当前个人 worktree，切换工作区后立即清空，避免跨 worktree 写入。
const workspaceUndoStack = ref<WorkspaceUndoOperation[]>([]);
let retryingWorkspaceAfterOpencodeReady = false;
let selectingAppId: string | undefined;
let appSelectionSeq = 0;
const readonlySessionReason = ref("");
const chatTitle = computed(() => session.value?.title ?? "");
const currentNightTask = computed<NightExecutionTask | null>(() => {
  const sessionId = session.value?.sessionId;
  const pending = (task: NightExecutionTask | null | undefined) =>
    task?.status === "SCHEDULED" || task?.status === "DISPATCHING" ? task : null;
  if (sessionId) {
    return pending(nightTasks.value.find((task) => task.sessionId === sessionId));
  }
  return pending(recentlyCreatedNightTask.value);
});
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
const firstLoginGuideSettingsMenu = ref<"appWorkspace" | "repository" | "personal">("appWorkspace");
const firstLoginGuideSettingsTab = ref<"members" | "repositories" | "workspaces" | undefined>();
const helpCenterOpen = ref(false);
const helpCenterTopic = ref("getting-started");
const firstLoginGuideRef = ref<InstanceType<typeof FirstLoginGuide> | null>(null);
const robotSideQuestion = useSideQuestionRun({
  api,
  baseUrl: apiBaseUrl,
  getAuthToken: () => authStore.token,
  getRouteLinuxServerId: () => routeLinuxServerId.value
});
const serverWorkspacePickerOpen = ref(false);
const referenceConfigurationOpen = ref(false);
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
const showReferenceConfiguration = computed(() =>
  canShowReferenceConfiguration({
    roles: authStore.currentUser?.roles,
    personalWorkspaceId: currentPersonalWorkspaceId.value,
    runtimeWorkspaceId: selectedWorkspace.value?.workspaceId,
    appId: selectedAppId.value
  })
);
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
  invalidateConversationInteraction();
  clearTerminalRunEventSubscriptionHold();
  window.removeEventListener("keydown", onWindowKeydown);
  clearFileTreeRetryTimers();
  if (workspaceFileCandidateTimer) {
    clearTimeout(workspaceFileCandidateTimer);
    workspaceFileCandidateTimer = null;
  }
  workspaceFileCandidateSeq++;
  workspaceRequirementLoadSeq++;
  runtimeStateOutages.reset();
  stopProcessStartupPolling();
});

// Chat runtime：单一 reducer 维护，dispatch 闭包更新
const chatState = ref(createInitialAgentChatRuntimeState(initialMessages));
const runFeedbacks = ref<Record<string, AiRunFeedback | null>>({});
const feedbackSubmitting = ref<Record<string, boolean>>({});
const platformMessageIdsByRemoteId = ref<Record<string, string>>({});
const assistantSummaryMessageIdsByRunId = ref<Record<string, string>>({});
function dispatchChat(action: Parameters<typeof reduceAgentChatRuntime>[1]) {
  const next = reduceAgentChatRuntime(chatState.value, action);
  chatState.value = next;
  return next;
}

function requestChatRun(userMessageId: string) {
  const supersededRunId = run.value?.runId;
  supersededConversationRunId.value = supersededRunId ?? null;
  pendingRequestedRunUserMessageId.value = userMessageId;
  dispatchChat({ type: "run.requested", userMessageId, supersededRunId });
}

function markConversationRunAdopted(runId: string, userMessageId?: string) {
  const knownOwner = chatState.value.todoUserMessageIdByRunId[runId];
  const pendingRequestOwner = supersededConversationRunId.value === runId
    ? undefined
    : pendingRequestedRunUserMessageId.value ?? undefined;
  const ownerUserMessageId = knownOwner ?? userMessageId ?? pendingRequestOwner;
  dispatchChat({ type: "run.adopted", runId, userMessageId: ownerUserMessageId });
  if (
    pendingRequestedRunUserMessageId.value
    && ownerUserMessageId === pendingRequestedRunUserMessageId.value
    && (userMessageId !== undefined || (!knownOwner && pendingRequestOwner !== undefined))
  ) {
    pendingRequestedRunUserMessageId.value = null;
  }
  if (supersededConversationRunId.value && supersededConversationRunId.value !== runId) {
    supersededConversationRunId.value = null;
  }
}

function clearAutoRetryState() {
  lastRunDraft.value = null;
  retryDeadlines.value = {};
  retryActionInFlightKey.value = null;
  autoRetryStarting.value = false;
  ignoredRunIds.value = new Set();
  supersededConversationRunId.value = null;
  pendingRequestedRunUserMessageId.value = null;
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
const activeWorkspaceViewNodeId = computed(() =>
  workbench.activePath ? workspaceViewNodeIdByTabPath.get(workbench.activePath) ?? workbench.activePath : undefined
);
const selectedDiffPath = computed(() => workbench.selectedDiffPath);
const activeTab = computed(() => tabs.value.find((tab: EditorTab) => tab.path === activePath.value));
const activeTabCopyPath = computed(() => {
  const tab = activeTab.value;
  if (!tab || !isAgentFilePath(tab.path)) return undefined;
  // Agent tab.path 是携带 workspace/worktree/server 的合成路由；没有绝对路径时也只回退到解码后的文件路径。
  return tab.absolutePath ?? agentFileInfo(tab.path).path;
});
const activeTabInitialLoading = computed(() =>
  activeTab.value?.loadState === "loading" && activeTab.value.hasLoadedSnapshot === false
);
const codeEditorRef = ref<any>(null);
const breadcrumbDisplay = computed(() => {
  if (!activePath.value) return "";
  const displayPath = isReferenceFilePath(activePath.value)
    ? (() => {
        const info = referenceFileInfo(activePath.value!);
        return referenceChatPath(info.referenceAlias, info.referencePath);
      })()
    : activePath.value;
  return displayPath.split(/[\\/]+/).filter(Boolean).join(" › ");
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

/** 待执行页是集中视图，按后端允许的最大页长逐页收齐，避免第 101 条任务静默消失。 */
async function listAllPendingNightExecutionTasks(): Promise<NightExecutionTaskQueryResponse> {
  const first = await api.listNightExecutionTasks({ page: 1, size: 200 });
  const tasksById = new Map(first.items.map((task) => [task.taskId, task]));
  const pageCount = Math.ceil(first.total / Math.max(first.size, 1));
  for (let page = 2; page <= pageCount; page += 1) {
    const next = await api.listNightExecutionTasks({ page, size: first.size });
    next.items.forEach((task) => tasksById.set(task.taskId, task));
  }
  return { ...first, items: [...tasksById.values()] };
}

async function refreshNightExecutionTasks(options: { reportError?: boolean } = {}) {
  if (!authStore.token) {
    nightTaskRefreshSequence += 1;
    nightTasks.value = [];
    nightVisibleFailure.value = null;
    recentlyCreatedNightTask.value = null;
    return;
  }
  const sequence = ++nightTaskRefreshSequence;
  const currentSessionId = session.value?.sessionId;
  try {
    const [allTasks, currentTasks] = await Promise.all([
      listAllPendingNightExecutionTasks(),
      currentSessionId
        ? api.listNightExecutionTasks({ sessionId: currentSessionId, page: 1, size: 20 })
        : Promise.resolve(null)
    ]);
    if (sequence !== nightTaskRefreshSequence) return;
    nightTasks.value = allTasks.items;
    nightVisibleFailure.value = currentTasks?.visibleFailure ?? null;
    if (recentlyCreatedNightTask.value) {
      const refreshed = allTasks.items.find((task) => task.taskId === recentlyCreatedNightTask.value?.taskId);
      recentlyCreatedNightTask.value = refreshed ?? null;
    }
  } catch (error) {
    if (sequence !== nightTaskRefreshSequence || !options.reportError) return;
    feedback.value = errorFeedback("查询夜间任务失败", error);
  }
}

async function requestNightExecutionSlots() {
  const sequence = ++nightSlotRequestSequence;
  nightSlotsLoading.value = true;
  try {
    const slots = await api.getNightExecutionSlots();
    if (sequence !== nightSlotRequestSequence) return;
    nightSlots.value = slots;
  } catch (error) {
    if (sequence !== nightSlotRequestSequence) return;
    nightSlots.value = null;
    feedback.value = errorFeedback("夜间执行暂不可用", error);
  } finally {
    if (sequence === nightSlotRequestSequence) nightSlotsLoading.value = false;
  }
}

function refreshNightTasksOnFocus() {
  if (document.visibilityState === "visible") void refreshNightExecutionTasks();
}

watch(
  [() => authStore.token, () => session.value?.sessionId],
  () => void refreshNightExecutionTasks(),
  { immediate: true }
);

onMounted(() => {
  window.addEventListener("focus", refreshNightTasksOnFocus);
  nightTaskPollingTimer = setInterval(() => void refreshNightExecutionTasks(), 30_000);
});

onBeforeUnmount(() => {
  window.removeEventListener("focus", refreshNightTasksOnFocus);
  if (nightTaskPollingTimer) {
    clearInterval(nightTaskPollingTimer);
    nightTaskPollingTimer = null;
  }
  nightTaskRefreshSequence += 1;
  nightSlotRequestSequence += 1;
});

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
// 方案 1：应用 Agent 与普通文件始终落在同一个版本个人 worktree。
// AgentConfig API 仍负责 `.opencode` 的目录与写权限，但 workspaceId 不再切到 feature 副本。
const selectedAgentConfigWorkspaceId = computed(() => selectedWorkspace.value?.workspaceId);

/** 路由切换会让旧响应失效，同时必须结束旧 tab 的 loading，保证返回原路由后可以重试。 */
function invalidateAgentFileLoadContext() {
  agentFileLoadGeneration++;
  latestAgentFileReadByPath.clear();
  for (const tab of workbench.tabs) {
    if (!isAgentFilePath(tab.path) || tab.loadState !== "loading") continue;
    if (workbench.tabHasLoadedSnapshot(tab) || editorTabIsDirty(tab)) {
      workbench.updateTab(tab.path, {
        loadState: "loaded",
        loadError: undefined,
        hasLoadedSnapshot: true
      });
      continue;
    }
    workbench.updateTab(tab.path, {
      loadState: "error",
      loadError: "Agent 文件路由已切换，请重试读取。",
      hasLoadedSnapshot: false
    });
  }
}

// Agent 文件路由上下文变化时立即废弃旧读取。flush=sync 确保子面板解析公共服务器后再发出的请求
// 捕获到的是新代次，而不是随后被异步 watch 误判为旧请求。
watch(
  () => [
    selectedAgentConfigWorkspaceId.value ?? "",
    workbench.publicWorktree?.worktreeId ?? "",
    workbench.publicWorktree?.linuxServerId ?? "",
    workbench.publicConfigLinuxServerId ?? ""
  ] as const,
  () => {
    invalidateAgentFileLoadContext();
  },
  { flush: "sync" }
);
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
  enabled: () => authStore.isAuthenticated() && routeLinuxServerResolved.value,
  queryFn: () => {
    const query = sessionSearchTrim.value;
    return api.listAllSessions(
      sessionHistoryPage.value,
      SESSION_HISTORY_PAGE_SIZE,
      query || undefined
    );
  }
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
  [() => authStore.token, routeLinuxServerId, routeLinuxServerResolved],
  ([token, linuxServerId, routeResolved], [oldToken], onCleanup) => {
    const subscriptionLinuxServerId = token === oldToken ? linuxServerId : "";
    const subscriptionRouteResolved = token === oldToken && routeResolved;
    if (token !== oldToken) {
      // context 与认证用户绑定，切换登录态必须丢弃页面内存缓存。
      invalidateConversationInteraction();
      conversationRunContexts.clear();
      runtimeStateOutages.reset();
      routeLinuxServerId.value = "";
      routeLinuxServerResolved.value = false;
    }
    if (!token || !subscriptionRouteResolved) {
      sessionRuntimeState.value = null;
      return;
    }
    const subscription = subscribeSessionRuntimeState({
      baseUrl: apiBaseUrl,
      token,
      linuxServerId: subscriptionLinuxServerId,
      onEvent: (summary) => {
        runtimeStateOutages.onSnapshot();
        activeRunProbeSeq += 1;
        sessionRuntimeState.value = summary;
        queryClient.setQueryData(sessionRuntimeStateQueryKey, summary);
        adoptRuntimeStateForCurrentSession(summary, "runtime-state-event");
      },
      onStatus: (status) => {
        logs.value = [...logs.value.slice(-200), `[runtime-state] ${status}`];
        if (status === "open") {
          runtimeStateOutages.onOpen();
        }
        if (status === "error") {
          runtimeStateOutages.onError();
          fallbackActiveRunOnce("runtime-state-unavailable");
        }
      }
    });
    onCleanup(() => {
      runtimeStateOutages.reset();
      subscription.close();
    });
  },
  { immediate: true }
);

/**
 * 用户级 runtime-state 已包含接管 RunEvent SSE 所需的 runId/status；直接构造前端 Run，避免再查数据库。
 */
function adoptRuntimeStateForCurrentSession(summary: SessionRuntimeStateSummary | null, reason: string): boolean {
  const currentSession = session.value;
  if (!currentSession || !summary) {
    return false;
  }
  const active = summary.sessions.find(
    (item) => item.sessionId === currentSession.sessionId && isRunBusyStatus(item.runStatus)
  );
  if (!active || ignoredRunIds.value.has(active.runId)) {
    return false;
  }
  const existing = run.value?.runId === active.runId ? run.value : null;
  if (!existing || existing.status !== active.runStatus || existing.updatedAt !== active.updatedAt) {
    const adopted: Run = {
      runId: active.runId,
      sessionId: currentSession.sessionId,
      workspaceId: currentSession.workspaceId,
      status: active.runStatus,
      createdAt: existing?.createdAt ?? active.updatedAt,
      updatedAt: active.updatedAt
    };
    run.value = adopted;
    markConversationRunAdopted(adopted.runId);
    rememberRunSession(adopted);
    logs.value = [...logs.value.slice(-200), `[run] recovered ${active.runId} ${active.runStatus} via ${reason}`];
  }
  return true;
}

function fallbackActiveRunOnce(reason: string) {
  const sessionId = session.value?.sessionId;
  if (!sessionId || isRunBusyStatus(run.value?.status)) {
    return;
  }
  const generation = runtimeStateOutages.takeFallback(sessionId);
  if (!generation) {
    return;
  }
  void recoverActiveRunForSession(
    sessionId,
    `${reason}-generation-${generation.outageGeneration}`,
    generation
  );
}

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
const publicConfigMessageGateQuery = useQuery({
  queryKey: computed(() => ["runtime", "opencode-process", "message-gate", authStore.token ?? ""] as const),
  enabled: opencodeProcessEnabled,
  queryFn: () => api.getMyOpencodeMessageGate(),
  retry: false,
  refetchOnWindowFocus: true,
  refetchInterval: PUBLIC_CONFIG_GATE_REFETCH_INTERVAL_MS
});
const opencodeProcessStatus = computed<UserOpencodeProcess | null>(() => {
  const process = opencodeProcessQuery.data.value;
  if (!process) {
    return null;
  }
  const gate = publicConfigMessageGateQuery.data.value;
  return gate
    ? {
        ...process,
        messageSendAllowed: gate.messageSendAllowed,
        messageSendBlockedReason: gate.messageSendBlockedReason,
        publicConfigRolloutId: gate.publicConfigRolloutId
      }
    : process;
});
watch(
  [opencodeProcessStatus, () => opencodeProcessQuery.isFetched.value],
  ([process, isFetched]) => {
    if (!isFetched) {
      return;
    }
    routeLinuxServerId.value = process?.linuxServerId?.trim() ?? "";
    routeLinuxServerResolved.value = true;
  },
  { immediate: true }
);
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
// 宠物问答不再要求先建立主对话：有对话时复用上下文，无对话时只要工作区和用户进程就绪即可查手册。
const robotQuestionAvailable = computed(() => opencodeProcessReady.value
  && opencodeProcessStatus.value?.messageSendAllowed !== false
  && Boolean(session.value?.sessionId || selectedWorkspaceIdRef.value));

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

function observeRawHttpExchange(exchange: RawHttpExchange) {
  if (!isConversationRawExchange(exchange)) {
    return;
  }
  const sessionId = extractRawExchangeSessionId(exchange);
  if (!sessionId) {
    return;
  }
  appendRawOutputEntry(sessionId, {
    id: nextRawOutputId("req"),
    kind: "request",
    title: `${exchange.method} ${exchange.path}`,
    method: exchange.method,
    path: exchange.path,
    traceId: exchange.traceId,
    body: exchange.requestBody ?? "",
    occurredAt: exchange.startedAt
  });

  appendRawOutputEntry(sessionId, {
    id: nextRawOutputId("res"),
    kind: "response",
    title: `${exchange.responseStatus ?? exchange.phase.toUpperCase()} ${exchange.method} ${exchange.path}`,
    method: exchange.method,
    path: exchange.path,
    status: exchange.responseStatus,
    traceId: exchange.responseHeaders?.["x-trace-id"] ?? exchange.traceId,
    contentType: exchange.responseHeaders?.["content-type"],
    body: exchange.responseText ?? exchange.errorMessage ?? "",
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
  appendRawOutputEntry(sessionId, {
    id: nextRawOutputId("sse"),
    kind: "sse",
    title: `${message.eventName}${message.lastEventId ? ` #${message.lastEventId}` : ""}`,
    eventName: message.eventName,
    runId: message.runId,
    traceId,
    body: message.data,
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
  const preparedBody = prepareRawOutputBody(entry.body, RAW_OUTPUT_BODY_LIMIT);
  const preparedEntry: RawOutputEntry = {
    ...entry,
    body: preparedBody.body,
    truncated: preparedBody.truncated
  };
  rawEntriesBySessionId.value = {
    ...rawEntriesBySessionId.value,
    [sessionId]: appendLatestRawOutputEntry(current, preparedEntry)
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

async function recoverActiveRunForSession(
  sessionId: string,
  reason: string,
  fallbackLease: RuntimeStateFallbackLease
): Promise<Run | null> {
  if (autoRetryStarting.value) {
    return null;
  }
  const seq = ++activeRunProbeSeq;
  try {
    const activeRun = await api.getActiveRun(sessionId);
    if (
      seq !== activeRunProbeSeq
      || session.value?.sessionId !== sessionId
      || !runtimeStateOutages.isCurrent(fallbackLease)
    ) {
      return null;
    }
    if (activeRun && isRunBusyStatus(activeRun.status)) {
      if (run.value?.runId !== activeRun.runId || run.value.status !== activeRun.status) {
        // 刷新、历史切换或启动请求仍未返回时，以后端 active-run 为准接管 SSE。
        run.value = activeRun;
        markConversationRunAdopted(activeRun.runId);
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
  // 切换文件或新打开文件时均重置预览状态为关闭，默认以编辑模式打开
  markdownPreviewMode.value = "off";
});
watch(selectedWorkspaceIdRef, (id, previous) => {
  if (previous && previous !== id) {
    void queryClient.cancelQueries({ queryKey: ["runtime", "agents", previous], exact: true });
    chatContextStore.clearContexts();
    workspaceUndoStack.value = [];
  }
  if (id) {
    workspaceFileRouteReadyById.value = { ...workspaceFileRouteReadyById.value, [id]: false };
    void loadDirectory("", id);
    void refreshWorkspaceGitDiff();
  }
}, { immediate: true });
watch(currentPersonalWorkspaceId, (id, previous) => {
  if (id !== previous) workspaceUndoStack.value = [];
});
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
    void refreshWorkspaceView(selectedWorkspaceId.value);
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

// ===== RunEvent SSE 订阅：仅标量身份变化时切换，同一 Run 的状态投影不重建连接 =====
const activeRunEventSubscriptionRunId = computed(() => runEventSubscriptionRunId(
  run.value,
  pendingSessionTitleRunId.value,
  terminalRunEventSubscriptionHoldRunId.value
));
const activeRunEventSubscriptionSessionId = computed(() => runEventSubscriptionSessionId(
  activeRunEventSubscriptionRunId.value,
  run.value,
  session.value?.sessionId
));

function holdTerminalRunEventSubscription(runId: string) {
  // 已关闭或已切换 Run 的连接晚到事件不能重新建立旧订阅。
  if (activeRunEventSubscriptionRunId.value !== runId) {
    return;
  }
  if (terminalRunEventSubscriptionHoldTimer) {
    clearTimeout(terminalRunEventSubscriptionHoldTimer);
  }
  terminalRunEventSubscriptionHoldRunId.value = runId;
  terminalRunEventSubscriptionHoldTimer = setTimeout(() => {
    terminalRunEventSubscriptionHoldTimer = null;
    if (terminalRunEventSubscriptionHoldRunId.value === runId) {
      terminalRunEventSubscriptionHoldRunId.value = null;
    }
  }, RUN_EVENT_TERMINAL_SETTLE_MS);
}

function clearTerminalRunEventSubscriptionHold() {
  if (terminalRunEventSubscriptionHoldTimer) {
    clearTimeout(terminalRunEventSubscriptionHoldTimer);
    terminalRunEventSubscriptionHoldTimer = null;
  }
  terminalRunEventSubscriptionHoldRunId.value = null;
}

watch(
  [activeRunEventSubscriptionRunId, activeRunEventSubscriptionSessionId, () => authStore.token, routeLinuxServerId],
  ([subscribedRunId, subscribedSessionId, token, linuxServerId], _old, onCleanup) => {
    if (!subscribedRunId || !subscribedSessionId || !token) {
      return;
    }
    const subscription = subscribeRunEvents({
      baseUrl: apiBaseUrl,
      runId: subscribedRunId,
      token,
      linuxServerId,
      onRawMessage: (message) => observeRawRunEventMessage(message, subscribedSessionId),
      onEvent: (event) => {
        if (ignoredRunIds.value.has(event.runId)) {
          return;
        }
        const projectionMode = runEventProjectionMode(
          event,
          subscribedRunId,
          run.value,
          supersededConversationRunId.value
        );
        if (projectionMode === "ignore") {
          return;
        }
        if (projectionMode === "title-only") {
          applyRunEventWorkbenchProjection(event, false, subscribedSessionId);
          return;
        }
        handleRunEvent(event, subscribedSessionId);
      },
      onStatus: (status) => {
        logs.value = [...logs.value.slice(-200), `[sse] ${status}`];
      },
      onError: () => {
        // 新请求尚未拿到 HTTP 响应时 run.value 仍可能指向旧 Run；旧订阅错误不能污染新轮。
        if (supersededConversationRunId.value === subscribedRunId) {
          return;
        }
        if (run.value?.runId === subscribedRunId && isRunBusyStatus(run.value.status)) {
          const message = "浏览器事件流连接异常，前端会等待自动重连；如后端确认失败，会继续收到 run.failed。";
          feedback.value = { kind: "error", title: RUN_EVENT_SSE_ERROR_TITLE, description: message };
          if (!reportedRunEventStreamErrors.has(subscribedRunId)) {
            reportedRunEventStreamErrors.add(subscribedRunId);
            observeRunEventStreamError(subscribedRunId, subscribedSessionId);
            dispatchChat({ type: "run.stream.error", runId: subscribedRunId, message });
          }
        }
      }
    });
    onCleanup(() => subscription.close());
  }
);

// 切换会话时释放旁路问答展示；主 Run 的恢复仍统一由 runtime-state 流和带 fence 的 fallback 接管。
watch(
  () => session.value?.sessionId,
  (sessionId, previousSessionId) => {
    if (sessionId !== previousSessionId) {
      legacyFeedbackRecoveryRunIds.clear();
    }
    robotSideQuestion.resetForSessionChange(sessionId);
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

/**
 * Agent 与 Skill 定义会被 OpenCode 工作区实例缓存。只在目录定义文件保存后重载运行态，
 * rules/templates 等普通资源仍按原保存链路处理，避免无关编辑打断当前实例。
 */
async function refreshRuntimeCatalogAfterAgentConfigSave(
  agent: AgentFileTabInfo
): Promise<unknown | null> {
  if (!shouldReloadPersonalRuntimeCatalog(agent.scope, agent.path) || !opencodeCatalogReady.value) {
    return null;
  }
  if (agent.scope === "PUBLIC") {
    if (!agent.worktreeId) {
      return new Error("公共 Agent 个人 worktree 路由缺失，无法只热加载当前用户");
    }
    // 公共个人配置不写入共享运行目录；后端先切换本人 Git 外固定配置链接，再 dispose 本人实例。
    pendingPublicRuntimeReloadTarget = {
      worktreeId: agent.worktreeId,
      linuxServerId: agent.linuxServerId
    };
  }
  lastRuntimeReloadError = null;
  pendingRuntimeReloadKind = "agent";
  pendingReferenceRuntimeReloadRevision.value += 1;
  if (userRuntimeBusy.value) {
    return null;
  }
  await reloadReferenceRuntimeIfIdle();
  return lastRuntimeReloadError;
}

/**
 * 手动验证个人 Agent/Skill 配置的运行态重载。
 * 公共配置必须先切换当前用户的公共 worktree 指针，再 dispose；应用配置只 dispose 当前用户。
 */
async function handlePersonalRuntimeReload(payload: {
  scope: "PUBLIC" | "WORKSPACE";
  worktreeId?: string;
  linuxServerId?: string;
  workspaceId?: string;
}) {
  if (runtimeReloadLock.value) return;
  if (payload.scope === "WORKSPACE" && !selectedWorkspaceIdRef.value) {
    feedback.value = {
      kind: "info",
      title: "个人配置未重载",
      description: "请先选择应用工作区，再重载个人运行态。"
    };
    return;
  }
  if (userRuntimeBusy.value) {
    feedback.value = {
      kind: "info",
      title: "当前任务运行中",
      description: "当前用户仍有运行中的 Session，结束后再重载个人运行态。"
    };
    return;
  }
  if (!opencodeProcessReady.value) {
    feedback.value = {
      kind: "info",
      title: payload.scope === "PUBLIC" ? "公共个人配置未重载" : "应用个人配置未重载",
      description: "当前 TestAgent 进程未就绪，无需 dispose；下次启动会读取最新配置。"
    };
    return;
  }
  const publicRuntimeRoute = payload.scope === "PUBLIC"
    ? {
        worktreeId: payload.worktreeId ?? workbench.publicWorktree?.worktreeId,
        linuxServerId: payload.linuxServerId
          ?? workbench.publicWorktree?.linuxServerId
          ?? workbench.publicConfigLinuxServerId
          ?? undefined
      }
    : null;
  if (payload.scope === "PUBLIC" && (!publicRuntimeRoute?.worktreeId || !publicRuntimeRoute.linuxServerId)) {
    feedback.value = {
      kind: "error",
      title: "公共个人配置未重载",
      description: "缺少当前用户公共 worktree 路由。"
    };
    return;
  }

  runtimeReloadLock.value = payload.scope;
  try {
    if (payload.scope === "PUBLIC") {
      const result = await api.reloadPublicPersonalAgentRuntime(publicRuntimeRoute!.worktreeId!, publicRuntimeRoute!.linuxServerId!);
      if (!result.reloaded) {
        throw new Error(result.message || "公共个人配置未重新加载");
      }
    } else {
      await api.disposeGlobal();
    }
    await Promise.all([agentsQuery.refetch(), commandsQuery.refetch()]);
    feedback.value = {
      kind: "success",
      title: payload.scope === "PUBLIC" ? "公共个人配置已重载" : "应用个人配置已重载",
      description: "已只刷新当前用户的 OpenCode 运行态；其他用户不受影响。"
    };
  } catch (error) {
    feedback.value = errorFeedback(
      payload.scope === "PUBLIC" ? "公共个人配置重载失败" : "应用个人配置重载失败",
      error
    );
  } finally {
    if (runtimeReloadLock.value === payload.scope) {
      runtimeReloadLock.value = null;
    }
    if (
      pendingReferenceRuntimeReloadRevision.value > handledReferenceRuntimeReloadRevision
      && !userRuntimeBusy.value
    ) {
      void reloadReferenceRuntimeIfIdle();
    }
  }
}

async function reloadReferenceRuntimeIfIdle(): Promise<void> {
  const targetRevision = pendingReferenceRuntimeReloadRevision.value;
  if (
    targetRevision <= handledReferenceRuntimeReloadRevision
    || runtimeReloadLock.value
    || runtimeReloadConflictWaitingForIdle.value
    || userRuntimeBusy.value
  ) {
    return;
  }
  const publicReloadTarget = pendingPublicRuntimeReloadTarget;
  if (!opencodeProcessReady.value || (!publicReloadTarget && !selectedWorkspaceIdRef.value)) {
    // 进程未运行时无需 dispose；下次受管启动会直接读取刚保存的磁盘配置和引用目录环境。
    handledReferenceRuntimeReloadRevision = targetRevision;
    lastRuntimeReloadError = null;
    feedback.value = {
      kind: "info",
      title: pendingRuntimeReloadKind === "reference" ? "引用配置已保存" : "Agent 配置已保存",
      description: "TestAgent 进程下次启动时会加载最新配置。"
    };
    return;
  }
  runtimeReloadLock.value = "REFERENCE";
  try {
    if (publicReloadTarget?.worktreeId) {
      const result = await api.reloadPublicPersonalAgentRuntime(
        publicReloadTarget.worktreeId,
        publicReloadTarget.linuxServerId
      );
      if (!result.reloaded) {
        throw new Error(result.message || "当前用户公共 Agent 配置未重新加载");
      }
    } else {
      // 应用个人配置仍由 OpenCode 原生 workspace 配置读取，只需释放当前进程缓存的实例。
      await api.disposeGlobal();
    }
    await Promise.all([agentsQuery.refetch(), commandsQuery.refetch()]);
    handledReferenceRuntimeReloadRevision = targetRevision;
    if (pendingReferenceRuntimeReloadRevision.value === targetRevision) {
      pendingPublicRuntimeReloadTarget = null;
    }
    lastRuntimeReloadError = null;
    feedback.value = {
      kind: "info",
      title: pendingRuntimeReloadKind === "reference" ? "引用配置已生效" : "Agent 配置已生效",
      description: "已重新加载当前用户的 TestAgent workspace 实例，无需重启专属进程。"
    };
  } catch (error) {
    if (error instanceof BackendApiError && error.code === "CONFLICT") {
      // 不消费 revision/公共 worktree 目标；SSE 若尚未观察到新 Run，短延迟后再复核一次。
      lastRuntimeReloadError = null;
      runtimeReloadConflictWaitingForIdle.value = true;
      scheduleRuntimeReloadConflictRetry();
      feedback.value = {
        kind: "info",
        title: pendingRuntimeReloadKind === "reference" ? "引用配置已保存" : "Agent 配置已保存",
        description: "当前用户有刚启动的 Session，结束后会自动重新加载运行态。"
      };
      return;
    }
    handledReferenceRuntimeReloadRevision = targetRevision;
    if (pendingReferenceRuntimeReloadRevision.value === targetRevision) {
      pendingPublicRuntimeReloadTarget = null;
    }
    lastRuntimeReloadError = error;
    feedback.value = errorFeedback(
      pendingRuntimeReloadKind === "reference"
        ? "引用配置已保存，但运行态重新加载失败"
        : "Agent 配置已保存，但运行态重新加载失败",
      error
    );
  } finally {
    if (runtimeReloadLock.value === "REFERENCE") {
      runtimeReloadLock.value = null;
    }
    if (
      pendingReferenceRuntimeReloadRevision.value > handledReferenceRuntimeReloadRevision
      && !runtimeReloadConflictWaitingForIdle.value
      && !userRuntimeBusy.value
    ) {
      void reloadReferenceRuntimeIfIdle();
    }
  }
}

function clearRuntimeReloadConflictRetryTimer() {
  if (runtimeReloadConflictRetryTimer === null) return;
  clearTimeout(runtimeReloadConflictRetryTimer);
  runtimeReloadConflictRetryTimer = null;
}

function resumeRuntimeReloadAfterConflict() {
  clearRuntimeReloadConflictRetryTimer();
  runtimeReloadConflictWaitingForIdle.value = false;
  void reloadReferenceRuntimeIfIdle();
}

function scheduleRuntimeReloadConflictRetry() {
  if (runtimeReloadConflictRetryTimer !== null) return;
  runtimeReloadConflictRetryTimer = setTimeout(() => {
    runtimeReloadConflictRetryTimer = null;
    if (!userRuntimeBusy.value && runtimeReloadConflictWaitingForIdle.value) {
      resumeRuntimeReloadAfterConflict();
    }
  }, 1000);
}

// ===== Mutations =====
// Agent 文件落盘后递增，由左侧 GitChangesPanel 监听并刷新公共/应用 Agent diff。
const agentConfigRevision = ref(0);

/** Agents 树创建或删除配置条目后复用保存动作的 revision 信号刷新 Git Diff。 */
function handleAgentConfigMutation(payload: {
  scope: "PUBLIC" | "WORKSPACE";
  paths: string[];
  deleted?: { path: string; type: "file" | "directory" };
  renamed?: { path: string; nextPath: string; type: "file" };
}) {
  if (payload.deleted) {
    const deletedPath = payload.deleted.path.replace(/\\/g, "/");
    for (const tab of [...workbench.tabs]) {
      if (!isAgentFilePath(tab.path)) continue;
      const file = agentFileInfo(tab.path);
      if (file.scope !== payload.scope) continue;
      const filePath = file.path.replace(/\\/g, "/");
      if (filePath === deletedPath
          || (payload.deleted.type === "directory" && filePath.startsWith(`${deletedPath}/`))) {
        workbench.closeTab(tab.path);
      }
    }
  }
  if (payload.renamed) {
    for (const tab of [...workbench.tabs]) {
      if (!isAgentFilePath(tab.path)) continue;
      const file = agentFileInfo(tab.path);
      if (file.scope !== payload.scope || file.path !== payload.renamed.path) continue;
      const nextTabPath = agentTabPath(
        file.scope,
        payload.renamed.nextPath,
        file.workspaceId,
        file.worktreeId,
        file.linuxServerId
      );
      const wasLoading = tab.loadState === "loading";
      const canRestoreSnapshot = agentTabHasLoadedSnapshot(tab) || editorTabIsDirty(tab);
      workbench.renameTab(tab.path, nextTabPath, payload.renamed.nextPath.split("/").at(-1) ?? payload.renamed.nextPath);
      if (wasLoading && canRestoreSnapshot) {
        workbench.updateTab(nextTabPath, {
          loadState: "loaded",
          loadError: undefined,
          hasLoadedSnapshot: true
        });
      } else if (wasLoading) {
        void openAgentFile({
          ...file,
          path: payload.renamed.nextPath,
          readonly: tab.readonly ?? true,
          activate: false,
          closeOnNotFound: true
        });
      }
    }
  }
  agentConfigRevision.value += 1;
}

const saveMutation = useMutation({
  mutationFn: async (tab: NonNullable<typeof activeTab.value>) => {
    if (isReferenceFilePath(tab.path)) {
      throw new Error("引用文件为只读，不能保存");
    }
    if (isAgentFilePath(tab.path)) {
      const agent = agentFileInfo(tab.path);
      if (agent.scope === "PUBLIC") {
        await api.writePublicAgentFile(agent.path, tab.content, agent.worktreeId, agent.linuxServerId);
      } else {
        if (!agent.workspaceId) {
          throw new Error("Agent 文件缺少 Workspace 路由");
        }
        await api.writeWorkspaceAgentFile(agent.workspaceId, agent.path, tab.content, agent.worktreeId);
      }
      return tab;
    }
    if (!selectedWorkspace.value) {
      throw new Error("未选择 Workspace");
    }
    await api.writeFile(selectedWorkspace.value.workspaceId, tab.path, tab.content);
    return tab;
  },
  onSuccess: async (tab) => {
    workbench.markTabSaved(tab.path, tab.content);
    const agentInfo = isAgentFilePath(tab.path) ? agentFileInfo(tab.path) : null;
    if (agentInfo) {
      agentConfigRevision.value += 1;
    }
    const catalogRefreshError = agentInfo
      ? await refreshRuntimeCatalogAfterAgentConfigSave(agentInfo)
      : null;
    feedback.value = catalogRefreshError
      ? errorFeedback("文件已保存，运行态目录刷新失败", catalogRefreshError)
      : {
          kind: "success",
          title: "文件已保存",
          description: userRuntimeBusy.value
            && agentInfo
            && shouldReloadPersonalRuntimeCatalog(agentInfo.scope, agentInfo.path)
            ? `${tab.path}；当前用户任务结束后会自动重新加载运行态。`
            : tab.path
        };
    if (!isAgentFilePath(tab.path)) {
      void refreshWorkspaceGitDiff();
    }
  },
  onError: (error) => {
    feedback.value = errorFeedback("保存文件失败", error);
  }
});

type StartRunDraft = {
  prompt: string;
  parts: PromptPart[];
  userMessageId: string;
  title?: string;
  command?: { command: string; arguments: string };
};

// 一次发送只允许修改其发起时的认证、会话和工作区；任何交互切换都会让旧异步结果失效。
type ConversationInteractionGuard = {
  generation: number;
  authToken: string | null;
  sessionId: string | null;
  workspaceId: string | null;
  runId: string | null;
};

type StartRunMutationRequest = {
  input: StartRunDraft;
  guard: ConversationInteractionGuard;
};

class StaleConversationInteractionError extends Error {
  constructor() {
    super("会话交互上下文已变化");
    this.name = "StaleConversationInteractionError";
  }
}

class StartRunMutationError extends Error {
  constructor(
    readonly originalError: unknown,
    readonly guard: ConversationInteractionGuard,
    readonly activeSessionId: string | null,
    readonly clientRequestId: string
  ) {
    super(originalError instanceof Error ? originalError.message : "启动 Run 失败");
    this.name = "StartRunMutationError";
  }
}

function invalidateConversationInteraction() {
  conversationInteractionGeneration += 1;
  activeRunProbeSeq += 1;
  // 非历史交互会取消当前 switch；递增 owner 代次可确保旧 finally 无权清除后来启动的新 switch。
  historySwitchSeq += 1;
  historyLoadingSessionId.value = null;
  historySwitchingSessionId.value = null;
}

function captureConversationInteraction(): ConversationInteractionGuard {
  return {
    generation: conversationInteractionGeneration,
    authToken: authStore.token ?? null,
    sessionId: session.value?.sessionId ?? null,
    workspaceId: selectedWorkspace.value?.workspaceId ?? null,
    runId: run.value?.runId ?? null
  };
}

function conversationInteractionIsCurrent(
  guard: ConversationInteractionGuard,
  expectedSessionId: string | null = guard.sessionId
): boolean {
  return guard.generation === conversationInteractionGeneration
    && guard.authToken === (authStore.token ?? null)
    && guard.workspaceId === (selectedWorkspaceIdRef.value ?? null)
    && expectedSessionId === (session.value?.sessionId ?? null);
}

function assertConversationInteractionCurrent(
  guard: ConversationInteractionGuard,
  expectedSessionId: string | null = guard.sessionId
) {
  if (!conversationInteractionIsCurrent(guard, expectedSessionId)) {
    throw new StaleConversationInteractionError();
  }
}

function ambiguousRunStartFailure(error: unknown): boolean {
  if (!(error instanceof BackendApiError)) {
    return true;
  }
  return error.status === 408 || error.status >= 500;
}

function runRecoveredAfterStartRequest(
  guard: ConversationInteractionGuard,
  sessionId: string,
  clientRequestId: string,
  error?: unknown
): boolean {
  const currentRun = run.value;
  if (
    !currentRun
    || !isRunBusyStatus(currentRun.status)
    || currentRun.sessionId !== sessionId
    || currentRun.runId === guard.runId
  ) {
    return false;
  }
  if (currentRun.clientRequestId) {
    return currentRun.clientRequestId === clientRequestId;
  }
  // Phase 1 的旧 runtime-state 摘要没有 clientRequestId，只对 HTTP 歧义错误使用同交互 busy Run 兜底。
  return error === undefined || ambiguousRunStartFailure(error);
}

const startRunMutation = useMutation({
  mutationFn: async ({ input, guard }: StartRunMutationRequest) => {
    const clientRequestId = createClientRequestId();
    let activeSessionId = guard.sessionId;
    try {
      assertConversationInteractionCurrent(guard);
      if (!opencodeProcessReady.value) {
        throw new Error("请先初始化 TestAgent 进程");
      }
      if (!guard.workspaceId) {
        throw new Error("未选择 Workspace");
      }
      let activeSession = session.value;
      if (!activeSession) {
        const createdSession = await api.createSession(
          guard.workspaceId,
          sessionTitleFromFirstMessage(input.title ?? input.prompt)
        );
        assertConversationInteractionCurrent(guard);
        activeSession = createdSession;
        activeSessionId = createdSession.sessionId;
        session.value = createdSession;
        void queryClient.invalidateQueries({ queryKey: ["sessions"] });
      }
      activeSessionId = activeSession.sessionId;
      assertConversationInteractionCurrent(guard, activeSessionId);
      const assertCurrent = () => assertConversationInteractionCurrent(guard, activeSessionId);
      const started = await startRunWithConversationContext({
        cache: conversationRunContexts,
        clientRequestId,
        assertCurrent,
        payload: {
          sessionId: activeSessionId,
          prompt: input.prompt,
          parts: input.parts,
          agent: selectedAgent.value || undefined,
          model: selectedModel.value || undefined,
          mode: promptMode.value,
          command: input.command?.command,
          arguments: input.command?.arguments
        },
        startRun: (payload) => {
          assertCurrent();
          return api.startRun(payload);
        }
      });
      assertCurrent();
      return { started, guard, activeSessionId, clientRequestId, userMessageId: input.userMessageId };
    } catch (error) {
      throw new StartRunMutationError(error, guard, activeSessionId, clientRequestId);
    }
  },
  onSuccess: ({ started, guard, activeSessionId, clientRequestId, userMessageId }) => {
    if (!conversationInteractionIsCurrent(guard, activeSessionId)) {
      return;
    }
    // 下一轮 Run 已启动时，旧 Run 的标题监听不能继续消费同一 root session 的后续事件。
    pendingSessionTitleRunId.value = null;
    if (runRecoveredAfterStartRequest(guard, activeSessionId, clientRequestId)) {
      return;
    }
    run.value = started;
    markConversationRunAdopted(started.runId, userMessageId);
    rememberRunSession(started);
    logs.value = [...logs.value, `[run] ${started.runId} ${started.status}`];
  },
  onError: (error) => {
    const failure = error instanceof StartRunMutationError ? error : null;
    const originalError = failure?.originalError ?? error;
    if (
      !failure
      || originalError instanceof StaleConversationInteractionError
      || !conversationInteractionIsCurrent(failure.guard, failure.activeSessionId)
    ) {
      return;
    }
    if (
      failure.activeSessionId
      && runRecoveredAfterStartRequest(
        failure.guard,
        failure.activeSessionId,
        failure.clientRequestId,
        originalError
      )
    ) {
      logs.value = [...logs.value.slice(-200), `[run] HTTP result ignored after runtime-state recovery ${failure.clientRequestId}`];
      return;
    }
    const startFailureFeedback = errorFeedback("启动 Run 失败", originalError);
    feedback.value = startFailureFeedback;
    dispatchChat({ type: "run.request.failed", message: startFailureFeedback.description });
    // Session 创建或 Run HTTP 提交失败时没有 RunEvent 终态，前端需要本地锁定本轮耗时。
    if (chatStartedAt.value) {
      totalDurationMs.value += Date.now() - chatStartedAt.value;
      lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
      chatStartedAt.value = null;
      nowTick.value = Date.now();
    }
  },
  onSettled: (_data, _error, request) => {
    if (pendingRequestedRunUserMessageId.value === request.input.userMessageId) {
      pendingRequestedRunUserMessageId.value = null;
    }
  }
});

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
// dispose 释放当前用户全部 Workspace Instance，不能只看当前页面的 Run；后端仍会在 dispose 前做权威复核。
const userRuntimeBusy = computed(() =>
  runtimeBusy.value || (sessionRuntimeState.value?.runningCount ?? 0) > 0
);
// 自动保存重载期间也锁住手动按钮，避免用户看到可用状态后再次发起 dispose。
const runtimeReloadBusy = computed(() =>
  userRuntimeBusy.value
  || runtimeReloadLock.value !== null
  || runtimeReloadConflictWaitingForIdle.value
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
onScopeDispose(() => {
  stopTick();
  clearRuntimeReloadConflictRetryTimer();
});
watch(runtimeBusy, (busy) => {
  if (busy) startTick();
  else {
    stopTick();
  }
}, { immediate: true });
watch(userRuntimeBusy, (busy) => {
  if (busy) return;
  if (runtimeReloadConflictWaitingForIdle.value) {
    resumeRuntimeReloadAfterConflict();
    return;
  }
  void reloadReferenceRuntimeIfIdle();
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
  // 远端 user message 可能已替换乐观 ID；重试必须复用 reducer 迁移后的当前 Run owner。
  const retryInput: AutoRetryRunDraft = {
    ...prepared.input,
    userMessageId: run.value?.runId
      ? chatState.value.todoUserMessageIdByRunId[run.value.runId] ?? prepared.input.userMessageId
      : prepared.input.userMessageId
  };
  lastRunDraft.value = retryInput;
  requestChatRun(retryInput.userMessageId);
  startRunMutation.mutate({ input: retryInput, guard: captureConversationInteraction() }, {
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
    const draft: AutoRetryRunDraft = {
      prompt: next.prompt,
      parts: next.parts,
      userMessageId: next.userMessageId,
      command: next.command
    };
    lastRunDraft.value = draft;
    requestChatRun(draft.userMessageId);
    startRunMutation.mutate({ input: draft, guard: captureConversationInteraction() });
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
    conversationRunContexts.invalidate(deleted.sessionId);
    if (session.value?.sessionId === deleted.sessionId) {
      pendingSessionTitleRunId.value = null;
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
  onError: (error, payload) => {
    if (error instanceof BackendApiError && error.code === "CONFLICT") {
      dispatchChat({ type: "permission.replied", requestId: payload.requestId });
      feedback.value = { kind: "info", title: "权限请求已失效", description: error.message };
      return;
    }
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
  onSuccess: (_result, payload) => dispatchChat({
    type: "question.replied",
    requestId: payload.requestId,
    answers: payload.answers
  }),
  onError: (error, payload) => {
    if (error instanceof BackendApiError && error.code === "CONFLICT") {
      dispatchChat({ type: "question.replied", requestId: payload.requestId });
      feedback.value = { kind: "info", title: "提问请求已失效", description: error.message };
      return;
    }
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
  onError: (error, requestId) => {
    if (error instanceof BackendApiError && error.code === "CONFLICT") {
      dispatchChat({ type: "question.replied", requestId });
      feedback.value = { kind: "info", title: "提问请求已失效", description: error.message };
      return;
    }
    feedback.value = errorFeedback("拒绝提问失败", error);
  }
});

const submitRunFeedbackMutation = useMutation({
  mutationFn: async (payload: AiRunFeedbackPayload & { runId: string }) =>
    putRunFeedbackWithProjectionRetry(payload.runId, {
      rating: payload.rating,
      reasonCode: payload.reasonCode,
      comment: payload.comment
    }),
  onMutate: payload => {
    feedbackSubmitting.value = { ...feedbackSubmitting.value, [payload.runId]: true };
  },
  onSuccess: (saved, payload) => {
    runFeedbacks.value = { ...runFeedbacks.value, [payload.runId]: saved };
    feedback.value = { kind: "success", title: "反馈已提交", description: payload.rating === "POSITIVE" ? "满意" : "不满意" };
  },
  onError: (error, payload) => {
    feedback.value = errorFeedback("提交反馈失败", error);
    feedbackSubmitting.value = { ...feedbackSubmitting.value, [payload.runId]: false };
  },
  onSettled: (_data, _error, payload) => {
    feedbackSubmitting.value = { ...feedbackSubmitting.value, [payload.runId]: false };
  }
});

/** 终态 SSE 可能略早于 Run 终态落库；只对该冲突做三次短退避，不重试其他错误。 */
async function putRunFeedbackWithProjectionRetry(runId: string, payload: AiRunFeedbackPayload): Promise<AiRunFeedback> {
  const delays = [0, 250, 500];
  let lastError: unknown;
  for (const delay of delays) {
    if (delay > 0) {
      await new Promise(resolve => setTimeout(resolve, delay));
    }
    try {
      return await api.putRunFeedback(runId, payload);
    } catch (error) {
      lastError = error;
      if (!(error instanceof BackendApiError)
        || error.code !== "CONFLICT"
        || String(error.details.runStatus ?? "").toUpperCase() === "SUCCEEDED") {
        throw error;
      }
    }
  }
  throw lastError;
}

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

/** 为服务器选择器内的超级管理员服务器终端签发一次性 ticket。 */
function createServerTerminalTicket(linuxServerId: string, confirmationText: string) {
  return api.createServerTerminalTicket(linuxServerId, {
    confirmationText,
    cols: 120,
    rows: 32
  });
}

function resetWorkspaceState() {
  // Workspace 切换后必须清掉旧根目录绑定的文件树、编辑器、Diff 与运行态，避免误操作旧路径。
  workspaceLoadGeneration++;
  latestWorkspaceFileReadByPath.clear();
  agentFileLoadGeneration++;
  latestAgentFileReadByPath.clear();
  clearFileTreeRetryTimers();
  entriesByDirectory.value = {};
  expandedDirectories.value = new Set();
  workspaceViewDirectoryById.clear();
  workspaceViewNodeIdByTabPath.clear();
  workspaceViewWarningByDirectory.clear();
  workspaceViewWarnings.value = [];
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
  workspaceFileCandidates.value = [];
  workspaceFileCandidatesLoading.value = false;
  if (workspaceFileCandidateTimer) {
    clearTimeout(workspaceFileCandidateTimer);
    workspaceFileCandidateTimer = null;
  }
  workspaceFileCandidateSeq++;
  workspaceRequirementCandidates.value = [];
  workspaceRequirementCandidatesLoading.value = false;
  workspaceRequirementLoadSeq++;
  session.value = null;
  run.value = null;
  nightVisibleFailure.value = null;
  recentlyCreatedNightTask.value = null;
  pendingSessionTitleRunId.value = null;
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
  // 引用弹窗绑定个人工作区；切仓必须立即卸载其轮询与迟到响应上下文。
  referenceConfigurationOpen.value = false;
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

function openReferenceConfiguration() {
  if (!showReferenceConfiguration.value || !selectedAppId.value || !selectedWorkspace.value) return;
  referenceConfigurationOpen.value = true;
}

async function refreshWorkspaceViewAfterReferenceSaved() {
  pendingRuntimeReloadKind = "reference";
  pendingReferenceRuntimeReloadRevision.value += 1;
  await refreshWorkspaceView();
  if (userRuntimeBusy.value) {
    feedback.value = {
      kind: "info",
      title: "引用配置已保存",
      description: "当前用户仍有运行中的 Session，结束后会自动重新加载 TestAgent workspace 实例。"
    };
    return;
  }
  await reloadReferenceRuntimeIfIdle();
}

type WorkspaceViewRefreshOptions = {
  targets?: readonly WorkspaceViewLoadTarget[];
  preserveLoadingPaths?: ReadonlySet<string>;
};

async function refreshWorkspaceView(
  workspaceId = selectedWorkspace.value?.workspaceId,
  options: WorkspaceViewRefreshOptions = {}
) {
  if (!workspaceId) return;
  const targets = options.targets ?? workspaceViewRefreshTargets(expandedDirectories.value, workspaceViewDirectoryById);
  for (const settlement of workspaceFileRefreshSettlements(
    workbench.tabs,
    (path) => isAgentFilePath(path) || options.preserveLoadingPaths?.has(path) === true
  )) {
    workbench.updateTab(settlement.path, settlement.patch);
  }
  latestWorkspaceFileReadByPath.clear();
  const generation = ++workspaceLoadGeneration;
  clearFileTreeRetryTimers();
  entriesByDirectory.value = {};
  workspaceViewDirectoryById.clear();
  workspaceViewWarningByDirectory.clear();
  workspaceViewWarnings.value = [];
  loadingPath.value = new Set();
  expandedDirectories.value = new Set();
  await loadDirectory(targets[0] ?? ROOT_WORKSPACE_VIEW_TARGET, workspaceId, true, 0, generation);
  // 逐层重放展开目录，使子目录能从刚加载的父目录中重新认领最新 locator。
  const restoredExpanded = new Set<string>();
  for (const previous of targets.slice(1)) {
    const current = revalidatedWorkspaceViewRefreshTarget(previous, workspaceViewDirectoryById);
    if (!current) continue;
    await loadDirectory(current, workspaceId, true, 0, generation);
    if (entriesByDirectory.value[current.id] !== undefined) {
      restoredExpanded.add(current.id);
      expandedDirectories.value = new Set(restoredExpanded);
    }
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

async function switchWorkspace(
  workspace: Workspace,
  options: { preserveConversationInteraction?: boolean; awaitDirectory?: boolean } = {}
) {
  if (!options.preserveConversationInteraction) {
    invalidateConversationInteraction();
  }
  resetWorkspaceState();
  cacheWorkspace(workspace);
  selectedWorkspaceId.value = workspace.workspaceId;
  selectedWorkspaceSnapshot.value = workspace;
  // 切到运行态 Workspace 后，反查当前 workspace 来自哪个应用版本，驱动两级菜单的高亮项。
  syncCurrentVersionFromWorkspace(workspace);
  void queryClient.invalidateQueries({ queryKey: ["workspaces"] });
  void queryClient.invalidateQueries({ queryKey: ["sessions"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime"] });
  const directoryLoad = loadDirectory("", workspace.workspaceId);
  if (options.awaitDirectory !== false) {
    await directoryLoad;
  } else {
    // 历史正文不应被工作区文件树阻塞；目录仍使用同一 generation 后台加载。
    void directoryLoad.catch(() => undefined);
  }
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
  invalidateConversationInteraction();
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
  void refreshWorkspaceView();
  void refreshWorkspaceGitDiff();
}

// 「+新增版本」流程：把 yyyyMMdd 和后端所需的 branch（非标准库）传给 createWorkspaceVersion。
// 成功后失效该模板下的版本查询，让 useQueries 重新拉取；同时把新版本切到工作区。
const creatingVersion = ref(false);
async function handleCreateVersion(payload: { template: ApplicationWorkspaceTemplate; version: string; branch?: string }) {
  invalidateConversationInteraction();
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
  invalidateConversationInteraction();
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
  invalidateConversationInteraction();
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
  requestedTarget: string | WorkspaceViewLoadTarget,
  workspaceId = selectedWorkspace.value?.workspaceId,
  force = false,
  retryCount = 0,
  generation = workspaceLoadGeneration
) {
  const target: WorkspaceViewLoadTarget = typeof requestedTarget === "string"
    ? requestedTarget === ""
      ? ROOT_WORKSPACE_VIEW_TARGET
      : resolveWorkspaceViewLoadTarget(requestedTarget, workspaceViewDirectoryById)
        ?? {
          id: requestedTarget,
          locator: { kind: "WORKSPACE", path: requestedTarget }
        }
    : requestedTarget;
  const cacheKey = target.id;
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
  if (loadingPath.value.has(cacheKey) || (!force && entriesByDirectory.value[cacheKey] !== undefined)) {
    return;
  }
  const nextLoading = new Set(loadingPath.value);
  nextLoading.add(cacheKey);
  loadingPath.value = nextLoading;
  try {
    const response = await api.listWorkspaceView(workspaceId, target.locator);
    const entries = workspaceViewEntries(response.entries);
    if (!workspaceLoadIsCurrent(workspaceId, generation, selectedWorkspaceIdRef.value, workspaceLoadGeneration)) {
      return;
    }
    entriesByDirectory.value = { ...entriesByDirectory.value, [cacheKey]: entries };
    for (const entry of response.entries) workspaceViewDirectoryById.set(entry.id, entry);
    workspaceViewWarningByDirectory.set(cacheKey, {
      warnings: response.warnings,
      truncated: response.truncated
    });
    workspaceViewWarnings.value = collectWorkspaceViewWarnings(workspaceViewWarningByDirectory);
    if (cacheKey === "") {
      workspaceFileRouteReadyById.value = { ...workspaceFileRouteReadyById.value, [workspaceId]: true };
      // 根目录加载成功后清除面板内错误
      fileTreeError.value = null;
    }
  } catch (error) {
    if (!workspaceLoadIsCurrent(workspaceId, generation, selectedWorkspaceIdRef.value, workspaceLoadGeneration)) {
      return;
    }
    // 根目录加载失败：设置面板内错误，保留上次成功数据
    if (cacheKey === "") {
      if (error instanceof BackendApiError && ["OPENCODE_UNAVAILABLE", "OPENCODE_BAD_GATEWAY"].includes(error.code)) {
        workspaceFileRouteReadyById.value = { ...workspaceFileRouteReadyById.value, [workspaceId]: false };
      }
      // 指数退避重试：最多重试 3 次，间隔 1s, 2s, 4s
      if (retryCount < 3) {
        const delay = Math.pow(2, retryCount) * 1000;
        fileTreeError.value = `加载文件树失败，${delay / 1000} 秒后重试...`;
        const timer = setTimeout(() => {
          fileTreeRetryTimers.delete(timer);
          void loadDirectory(target, workspaceId, force, retryCount + 1, generation);
        }, delay);
        fileTreeRetryTimers.add(timer);
      } else {
        // 重试耗尽，显示错误和手动重试按钮
        fileTreeError.value = error instanceof BackendApiError ? error.message : "加载文件树失败";
      }
    } else {
      // 非根目录加载失败：从展开集合里把这条目录回滚掉
      if (expandedDirectories.value.has(cacheKey)) {
        const nextExpanded = new Set(expandedDirectories.value);
        nextExpanded.delete(cacheKey);
        expandedDirectories.value = nextExpanded;
      }
    }
  } finally {
    if (workspaceLoadIsCurrent(workspaceId, generation, selectedWorkspaceIdRef.value, workspaceLoadGeneration)) {
      const cleared = new Set(loadingPath.value);
      cleared.delete(cacheKey);
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

/**
 * 为对话 @ 补全查询当前个人 worktree 文件。空关键字也交给后端受限搜索，
 * 因此刚输入 @ 时即可展示文件，同时不会在浏览器递归扫描目录。
 */
function handleWorkspaceFileCandidateSearch(query: string | null) {
  if (workspaceFileCandidateTimer) {
    clearTimeout(workspaceFileCandidateTimer);
    workspaceFileCandidateTimer = null;
  }
  const workspaceId = selectedWorkspace.value?.workspaceId;
  const seq = ++workspaceFileCandidateSeq;
  if (query === null || !workspaceId) {
    workspaceFileCandidates.value = [];
    workspaceFileCandidatesLoading.value = false;
    return;
  }
  workspaceFileCandidates.value = [];
  workspaceFileCandidatesLoading.value = true;
  workspaceFileCandidateTimer = setTimeout(async () => {
    workspaceFileCandidateTimer = null;
    try {
      const results = await api.searchFiles(workspaceId, query);
      if (seq === workspaceFileCandidateSeq && selectedWorkspace.value?.workspaceId === workspaceId) {
        workspaceFileCandidates.value = results;
      }
    } catch (error) {
      if (seq === workspaceFileCandidateSeq && selectedWorkspace.value?.workspaceId === workspaceId) {
        workspaceFileCandidates.value = [];
        feedback.value = errorFeedback("搜索对话文件失败", error);
      }
    } finally {
      if (seq === workspaceFileCandidateSeq && selectedWorkspace.value?.workspaceId === workspaceId) {
        workspaceFileCandidatesLoading.value = false;
      }
    }
  }, 180);
}

/**
 * 懒加载当前个人 worktree 的 spec 需求子条目。四个阶段目录继续分别通过平台 workspace.search 查询，
 * 前端只负责把同一需求项下的同名子条目聚合为一个可选择的业务上下文。
 */
async function loadWorkspaceRequirementCandidates() {
  const workspaceId = selectedWorkspace.value?.workspaceId;
  if (!workspaceId || workspaceRequirementCandidatesLoading.value) {
    return;
  }
  const seq = ++workspaceRequirementLoadSeq;
  workspaceRequirementCandidatesLoading.value = true;
  try {
    const results = (await Promise.all(
      workspaceRequirementStageDirectories.map((stage) => api.searchFiles(workspaceId, `/${stage}/`))
    )).flat();
    if (seq === workspaceRequirementLoadSeq && selectedWorkspace.value?.workspaceId === workspaceId) {
      workspaceRequirementCandidates.value = workspaceRequirementReferences(results);
    }
  } catch (error) {
    if (seq === workspaceRequirementLoadSeq && selectedWorkspace.value?.workspaceId === workspaceId) {
      workspaceRequirementCandidates.value = [];
      feedback.value = errorFeedback("读取需求子条目失败", error);
    }
  } finally {
    if (seq === workspaceRequirementLoadSeq && selectedWorkspace.value?.workspaceId === workspaceId) {
      workspaceRequirementCandidatesLoading.value = false;
    }
  }
}

type WorkspaceFileLoadOptions = {
  activate?: boolean;
  closeOnNotFound?: boolean;
  expectedContext?: WorkspaceFileLoadContext;
};

type WorkspaceFileLoadContext = {
  workspaceId: string;
  workspaceGeneration: number;
};

function workspaceFileLoadContextIsCurrent(context: WorkspaceFileLoadContext): boolean {
  return selectedWorkspaceIdRef.value === context.workspaceId
    && workspaceLoadGeneration === context.workspaceGeneration;
}

function workspaceFileReadIsCurrent(
  workspaceId: string,
  path: string,
  workspaceGeneration: number,
  requestGeneration: number
): boolean {
  return selectedWorkspaceIdRef.value === workspaceId
    && workspaceLoadGeneration === workspaceGeneration
    && latestWorkspaceFileReadByPath.get(path) === requestGeneration
    && workbench.tabs.some((tab: EditorTab) => tab.path === path);
}

function editorTabIsDirty(tab: EditorTab | undefined): boolean {
  return Boolean(tab && !tab.livePreview && tab.content !== tab.savedContent);
}

async function loadWorkspaceFile(path: string, options: WorkspaceFileLoadOptions = {}) {
  const workspace = selectedWorkspace.value;
  const context = options.expectedContext ?? (workspace
    ? { workspaceId: workspace.workspaceId, workspaceGeneration: workspaceLoadGeneration }
    : undefined);
  if (!workspace || !context || workspace.workspaceId !== context.workspaceId
    || !workspaceFileLoadContextIsCurrent(context)) {
    return;
  }
  const activate = options.activate !== false;
  const existing = workbench.tabs.find((tab: EditorTab) => tab.path === path);
  if (editorTabIsDirty(existing)) {
    // 未保存内容始终优先：重复打开只激活，不发起可能覆盖编辑内容的磁盘读取。
    if (activate) {
      centerMode.value = "editor";
      workbench.setActivePath(path);
    }
    return;
  }

  const workspaceId = context.workspaceId;
  // Workspace 代次与同路径请求代次共同隔离迟到响应，切换根目录或再次读取后旧结果直接作废。
  const workspaceGeneration = context.workspaceGeneration;
  const requestGeneration = ++workspaceFileReadSequence;
  latestWorkspaceFileReadByPath.set(path, requestGeneration);
  const hadLoadedCache = workbench.tabHasLoadedSnapshot(existing);
  const contentRevisionAtStart = existing?.contentRevision ?? 0;
  const loadingPatch = {
    loadState: "loading" as const,
    loadError: undefined,
    // 将 legacy loaded 身份固化到 tab，后续重叠刷新不能被瞬时 loading 状态抹掉。
    hasLoadedSnapshot: hadLoadedCache
  };
  if (activate) {
    centerMode.value = "editor";
    workbench.openTab({
      id: existing && !existing.livePreview ? existing.id : `file:${path}`,
      path,
      title: existing?.title ?? (path.split(/[\\/]+/).filter(Boolean).at(-1) ?? path),
      content: existing?.content ?? "",
      savedContent: existing?.savedContent ?? "",
      // 首次读取尚不知道最终权限，先按只读挂载；有缓存的后台刷新保持原编辑能力。
      readonly: hadLoadedCache ? existing?.readonly : true,
      livePreview: false,
      ...loadingPatch
    });
  } else if (existing) {
    workbench.updateTab(path, loadingPatch);
  } else {
    return;
  }

  try {
    const file = await api.readFile(workspaceId, path, !currentPersonalWorkspaceId.value);
    if (!workspaceFileReadIsCurrent(workspaceId, path, workspaceGeneration, requestGeneration)) {
      return;
    }
    const current = workbench.tabs.find((tab: EditorTab) => tab.path === path);
    if ((current?.contentRevision ?? 0) !== contentRevisionAtStart || editorTabIsDirty(current)) {
      // dirty 可能在读取完成前已被保存/回退为 clean；修订代次确保任何期间编辑都会让旧响应失效。
      workbench.updateTab(path, {
        loadState: "loaded",
        loadError: undefined,
        hasLoadedSnapshot: true
      });
      return;
    }
    workbench.updateTab(path, {
      content: file.content,
      savedContent: file.content,
      readonly: file.readonly,
      loadState: "loaded",
      loadError: undefined,
      hasLoadedSnapshot: true
    });
  } catch (error) {
    if (!workspaceFileReadIsCurrent(workspaceId, path, workspaceGeneration, requestGeneration)) {
      return;
    }
    const current = workbench.tabs.find((tab: EditorTab) => tab.path === path);
    if ((current?.contentRevision ?? 0) !== contentRevisionAtStart) {
      // 读取期间发生过编辑时，错误响应也已失去意义；结束 loading 并保留当前正文与保存基线。
      workbench.updateTab(path, {
        loadState: "loaded",
        loadError: undefined,
        hasLoadedSnapshot: true
      });
      return;
    }
    if (options.closeOnNotFound
      && error instanceof BackendApiError
      && error.code === "NOT_FOUND"
      && !editorTabIsDirty(current)) {
      workbench.closeTab(path);
      return;
    }
    const failure = errorFeedback("读取文件失败", error);
    if (hadLoadedCache || editorTabIsDirty(current)) {
      // 已有可用正文的刷新失败只提示错误，继续保留缓存与 savedContent。
      workbench.updateTab(path, {
        loadState: "loaded",
        loadError: failure.description,
        hasLoadedSnapshot: true
      });
      feedback.value = failure;
      return;
    }
    workbench.updateTab(path, {
      loadState: "error",
      loadError: failure.description,
      hasLoadedSnapshot: false
    });
  }
}

async function openFile(path: string) {
  await loadWorkspaceFile(path, { activate: true });
}

async function openWorkspaceViewFile(entry: WorkspaceViewEntry) {
  if (entry.source === "WORKSPACE") {
    const path = entry.workspacePath ?? entry.path;
    workspaceViewNodeIdByTabPath.set(path, entry.id);
    await loadWorkspaceFile(path, { activate: true });
    return;
  }
  const workspace = selectedWorkspace.value;
  const alias = entry.locator.referenceAlias ?? entry.referenceAliases[0];
  if (!workspace || !alias) {
    feedback.value = { kind: "error", title: "无法打开引用文件", description: "引用来源缺少稳定别名。" };
    return;
  }
  const tabPath = referenceTabPath({
    workspaceId: workspace.workspaceId,
    referenceAlias: alias,
    referencePath: entry.locator.path,
    logicalPath: entry.path
  });
  const existing = workbench.tabs.find((tab: EditorTab) => tab.path === tabPath);
  const hadLoadedCache = workbench.tabHasLoadedSnapshot(existing);
  const requestGeneration = ++workspaceFileReadSequence;
  const workspaceGeneration = workspaceLoadGeneration;
  latestWorkspaceFileReadByPath.set(tabPath, requestGeneration);
  workspaceViewNodeIdByTabPath.set(tabPath, entry.id);
  centerMode.value = "editor";
  workbench.openTab({
    id: existing?.id ?? `file:${tabPath}`,
    path: tabPath,
    title: entry.name,
    content: existing?.content ?? "",
    savedContent: existing?.savedContent ?? "",
    readonly: true,
    livePreview: false,
    loadState: "loading",
    loadError: undefined,
    hasLoadedSnapshot: hadLoadedCache
  });
  try {
    const file = await api.readWorkspaceViewFile(workspace.workspaceId, entry.locator);
    if (selectedWorkspaceIdRef.value !== workspace.workspaceId
      || workspaceLoadGeneration !== workspaceGeneration
      || latestWorkspaceFileReadByPath.get(tabPath) !== requestGeneration
      || !workbench.tabs.some((tab: EditorTab) => tab.path === tabPath)) return;
    workbench.updateTab(tabPath, {
      content: file.content,
      savedContent: file.content,
      readonly: true,
      loadState: "loaded",
      loadError: undefined,
      hasLoadedSnapshot: true
    });
  } catch (error) {
    if (selectedWorkspaceIdRef.value !== workspace.workspaceId
      || workspaceLoadGeneration !== workspaceGeneration
      || latestWorkspaceFileReadByPath.get(tabPath) !== requestGeneration) return;
    const failure = errorFeedback("读取引用文件失败", error);
    workbench.updateTab(tabPath, referenceReadFailurePatch(hadLoadedCache, failure.description));
  }
}

function normalizedAgentRouteValue(value?: string | null): string {
  return value ?? "";
}

function agentFileLoadContextIsCurrent(request: AgentFileLoadRequest): boolean {
  if (request.scope === "PUBLIC") {
    const currentWorktreeId = workbench.publicWorktree?.worktreeId;
    const currentLinuxServerId = workbench.publicWorktree?.linuxServerId
      ?? workbench.publicConfigLinuxServerId;
    return normalizedAgentRouteValue(request.worktreeId) === normalizedAgentRouteValue(currentWorktreeId)
      && normalizedAgentRouteValue(request.linuxServerId) === normalizedAgentRouteValue(currentLinuxServerId);
  }
  return Boolean(request.workspaceId)
    && request.workspaceId === selectedAgentConfigWorkspaceId.value
    && !request.worktreeId;
}

function agentFileReadIsCurrent(
  request: AgentFileLoadRequest,
  tabPath: string,
  contextGeneration: number,
  requestGeneration: number
): boolean {
  return agentFileLoadGeneration === contextGeneration
    && agentFileLoadContextIsCurrent(request)
    && latestAgentFileReadByPath.get(tabPath) === requestGeneration
    && workbench.tabs.some((tab: EditorTab) => tab.path === tabPath);
}

function agentFileLoadRequestFromTab(tab: EditorTab, activate: boolean): AgentFileLoadRequest | undefined {
  if (!isAgentFilePath(tab.path)) {
    return undefined;
  }
  const file = agentFileInfo(tab.path);
  const workspaceId = file.scope === "WORKSPACE" ? file.workspaceId : undefined;
  if (file.scope === "WORKSPACE" && !workspaceId) {
    return undefined;
  }
  return {
    ...file,
    absolutePath: tab.absolutePath,
    workspaceId,
    readonly: Boolean(tab.readonly),
    activate,
    closeOnNotFound: false
  };
}

function agentTabHasLoadedSnapshot(tab: EditorTab | undefined): boolean {
  if (!tab) {
    return false;
  }
  if (tab.hasLoadedSnapshot !== undefined) {
    return tab.hasLoadedSnapshot;
  }
  if (tab.loadState === "loaded") {
    return true;
  }
  // 旧版 Agent tab 没有三态标记；仅非空正文可作为缓存，历史空白 tab 必须重新走首次加载。
  return tab.loadState === undefined && Boolean(tab.content || tab.savedContent);
}

async function loadAgentFile(request: AgentFileLoadRequest) {
  if (!agentFileLoadContextIsCurrent(request)) {
    return;
  }
  const tabPath = agentTabPath(
    request.scope,
    request.path,
    request.workspaceId,
    request.worktreeId,
    request.linuxServerId
  );
  const existing = workbench.tabs.find((tab: EditorTab) => tab.path === tabPath);
  if (editorTabIsDirty(existing) && !request.replaceExistingDirty) {
    // 普通刷新只激活 dirty tab；仅用户已确认 Git 回退时允许用磁盘结果替换旧草稿。
    if (request.activate) {
      centerMode.value = "editor";
      workbench.setActivePath(tabPath);
    }
    return;
  }
  if (!request.activate && !existing) {
    return;
  }

  const contextGeneration = agentFileLoadGeneration;
  const requestGeneration = ++agentFileReadSequence;
  latestAgentFileReadByPath.set(tabPath, requestGeneration);
  const hadLoadedCache = agentTabHasLoadedSnapshot(existing);
  const contentRevisionAtStart = existing?.contentRevision ?? 0;
  const loadingPatch = {
    absolutePath: request.absolutePath ?? existing?.absolutePath,
    loadState: "loading" as const,
    loadError: undefined,
    hasLoadedSnapshot: hadLoadedCache
  };
  if (request.activate) {
    centerMode.value = "editor";
    workbench.openTab({
      id: existing?.id
        ?? `${request.scope.toLowerCase()}:agent:file:${request.workspaceId ?? "global"}:${request.worktreeId ?? "direct"}:${request.linuxServerId ?? "local"}:${request.path}`,
      path: tabPath,
      title: existing?.title ?? (request.path.split(/[\\/]+/).filter(Boolean).at(-1) ?? request.path),
      content: existing?.content ?? "",
      savedContent: existing?.savedContent ?? "",
      // AgentConfigPanel 已完成权限判断；首次错误后的重试也必须保留目标权限，不能被临时加载态锁死为只读。
      readonly: hadLoadedCache ? existing?.readonly : request.readonly,
      livePreview: false,
      ...loadingPatch
    });
  } else {
    workbench.updateTab(tabPath, loadingPatch);
  }

  try {
    const file = request.scope === "PUBLIC"
      ? await api.readPublicAgentFile(request.path, request.worktreeId, request.linuxServerId)
      : await api.readWorkspaceAgentFile(request.workspaceId!, request.path, request.worktreeId);
    if (!agentFileReadIsCurrent(request, tabPath, contextGeneration, requestGeneration)) {
      return;
    }
    const current = workbench.tabs.find((tab: EditorTab) => tab.path === tabPath);
    if ((current?.contentRevision ?? 0) !== contentRevisionAtStart
      || (!request.replaceExistingDirty && editorTabIsDirty(current))) {
      // 即使用户随后保存或回退为 clean，修订代次也会阻止读取期间的旧响应覆盖编辑结果。
      workbench.updateTab(tabPath, {
        loadState: "loaded",
        loadError: undefined,
        hasLoadedSnapshot: true
      });
      return;
    }
    workbench.updateTab(tabPath, {
      content: file.content,
      savedContent: file.content,
      readonly: request.readonly,
      loadState: "loaded",
      loadError: undefined,
      hasLoadedSnapshot: true
    });
  } catch (error) {
    if (!agentFileReadIsCurrent(request, tabPath, contextGeneration, requestGeneration)) {
      return;
    }
    const current = workbench.tabs.find((tab: EditorTab) => tab.path === tabPath);
    if ((current?.contentRevision ?? 0) !== contentRevisionAtStart) {
      workbench.updateTab(tabPath, {
        loadState: "loaded",
        loadError: undefined,
        hasLoadedSnapshot: true
      });
      return;
    }
    if (request.closeOnNotFound
      && error instanceof BackendApiError
      && error.code === "NOT_FOUND"
      && (request.replaceExistingDirty || !editorTabIsDirty(current))) {
      workbench.closeTab(tabPath);
      return;
    }
    const failure = errorFeedback("读取 Agent 文件失败", error);
    if (hadLoadedCache || editorTabIsDirty(current)) {
      workbench.updateTab(tabPath, {
        loadState: "loaded",
        loadError: failure.description,
        hasLoadedSnapshot: true
      });
      feedback.value = failure;
      return;
    }
    workbench.updateTab(tabPath, {
      loadState: "error",
      loadError: failure.description,
      hasLoadedSnapshot: false
    });
  }
}

function activateEditorTab(path: string) {
  const tab = workbench.tabs.find((item: EditorTab) => item.path === path);
  if (!tab) {
    return;
  }
  centerMode.value = "editor";
  if (isAgentFilePath(path)) {
    if (tab.loadState === "loading" || tab.loadState === "loaded") {
      workbench.setActivePath(path);
      return;
    }
    const request = agentFileLoadRequestFromTab(tab, true);
    if (request) {
      void loadAgentFile(request);
      return;
    }
    workbench.setActivePath(path);
    return;
  }
  if (isReferenceFilePath(path)) {
    if (tab.loadState === "error") void reloadReferenceTab(tab);
    else workbench.setActivePath(path);
    return;
  }
  if (tab.loadState === "error") {
    void loadWorkspaceFile(path, { activate: true });
    return;
  }
  // loading 不重复请求；loaded 与旧 tab（undefined）直接使用内存缓存。
  workbench.setActivePath(path);
}

function retryActiveFile() {
  const tab = activeTab.value;
  if (!tab) {
    return;
  }
  const request = agentFileLoadRequestFromTab(tab, true);
  if (request) {
    void loadAgentFile(request);
    return;
  }
  if (isReferenceFilePath(tab.path)) {
    void reloadReferenceTab(tab);
    return;
  }
  void openFile(tab.path);
}

async function reloadReferenceTab(tab: EditorTab) {
  const info = referenceFileInfo(tab.path);
  const nodeId = workspaceViewNodeIdByTabPath.get(tab.path);
  const known = nodeId ? workspaceViewDirectoryById.get(nodeId) : undefined;
  await openWorkspaceViewFile(known ?? {
    id: nodeId ?? tab.path,
    path: info.logicalPath,
    name: tab.title,
    type: "file",
    locator: { kind: "REFERENCE", path: info.referencePath, referenceAlias: info.referenceAlias },
    source: "REFERENCE",
    merged: true,
    collision: false,
    readonly: true,
    referenceAliases: [info.referenceAlias]
  });
}

async function handleCreateEntry(directory: string, name: string, type: "file" | "directory") {
  if (!selectedWorkspace.value) {
    return;
  }
  if (!currentPersonalWorkspaceId.value) {
    feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再修改应用文件。" };
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

function workspaceParentDirectory(path: string): string {
  const index = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  return index >= 0 ? path.slice(0, index) : "";
}

function workspacePathInDirectory(directory: string, name: string): string {
  if (!directory) return name;
  return `${directory}${directory.includes("\\") ? "\\" : "/"}${name}`;
}

/** 使用分块转换避免一次展开整个 Uint8Array 导致调用栈溢出。 */
async function workspaceUploadBase64(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer());
  const chunks: string[] = [];
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    chunks.push(String.fromCharCode(...bytes.subarray(offset, offset + 0x8000)));
  }
  return btoa(chunks.join(""));
}

async function handleCopyEntry(sourcePath: string, targetDirectory: string) {
  if (!selectedWorkspace.value || !currentPersonalWorkspaceId.value) {
    feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再复制文件。" };
    return;
  }
  const targetPath = copiedWorkspaceFileTargetPath(
    sourcePath,
    targetDirectory,
    entriesByDirectory.value,
    workspaceViewDirectoryById
  );
  try {
    await api.copyWorkspaceFile(selectedWorkspace.value.workspaceId, sourcePath, targetPath);
    await loadDirectory(targetDirectory, undefined, true);
    workspaceUndoStack.value.push({ kind: "delete", paths: [targetPath], label: `复制 ${targetPath}` });
    void refreshWorkspaceGitDiff();
    feedback.value = { kind: "success", title: "文件已复制", description: targetPath };
  } catch (error) {
    feedback.value = errorFeedback("复制工作区文件失败", error);
  }
}

async function handleMoveEntry(sourcePath: string, targetDirectory: string) {
  if (!selectedWorkspace.value || !currentPersonalWorkspaceId.value) {
    feedback.value = { kind: "info", title: "当前工作区条目只读", description: "请切换到个人 worktree 后再移动工作区条目。" };
    return;
  }
  const sourceDirectory = workspaceParentDirectory(sourcePath);
  if (sourceDirectory === targetDirectory) return;
  const targetPath = workspacePathInDirectory(targetDirectory, fileNameOf(sourcePath));
  try {
    const workspaceId = selectedWorkspace.value.workspaceId;
    const refreshTargets = migrateWorkspaceViewRefreshTargets(
      workspaceViewRefreshTargets(expandedDirectories.value, workspaceViewDirectoryById),
      sourcePath,
      targetPath
    );
    await api.moveWorkspaceFile(workspaceId, sourcePath, targetPath);
    const pendingReloadPaths = renameWorkspaceTreeEntry(sourcePath, targetPath, { deferLoadingReload: true });
    await refreshWorkspaceView(workspaceId, {
      targets: refreshTargets,
      preserveLoadingPaths: new Set(pendingReloadPaths)
    });
    for (const path of pendingReloadPaths) void loadWorkspaceFile(path, { activate: false });
    workspaceUndoStack.value.push({
      kind: "move",
      sourcePath: targetPath,
      targetPath: sourcePath,
      label: `移动 ${sourcePath}`
    });
    void refreshWorkspaceGitDiff();
    feedback.value = { kind: "success", title: "工作区条目已移动", description: targetPath };
  } catch (error) {
    feedback.value = errorFeedback("移动工作区条目失败", error);
  }
}

async function handleUploadFiles(directory: string, files: File[]) {
  if (!selectedWorkspace.value || !currentPersonalWorkspaceId.value) {
    feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再上传文件。" };
    return;
  }
  const workspaceId = selectedWorkspace.value.workspaceId;
  const failures: string[] = [];
  const uploadedPaths: string[] = [];
  let uploaded = 0;
  for (const file of files) {
    try {
      // 浏览器通常只提供 basename；再次截断路径分隔符，避免构造 File 时夹带目录片段。
      const targetPath = workspacePathInDirectory(directory, fileNameOf(file.name));
      await api.uploadWorkspaceFile(workspaceId, targetPath, await workspaceUploadBase64(file));
      uploaded += 1;
      uploadedPaths.push(targetPath);
    } catch (error) {
      const reason = error instanceof Error ? error.message : "上传失败";
      failures.push(`${file.name}：${reason}`);
    }
  }
  if (uploaded > 0) {
    await loadDirectory(directory, undefined, true);
    workspaceUndoStack.value.push({ kind: "delete", paths: uploadedPaths, label: `上传 ${uploaded} 个文件` });
    void refreshWorkspaceGitDiff();
  }
  if (failures.length > 0) {
    feedback.value = {
      kind: "error",
      title: uploaded > 0 ? "部分文件上传失败" : "上传文件失败",
      description: failures.join("；")
    };
    return;
  }
  feedback.value = { kind: "success", title: `已上传 ${uploaded} 个文件`, description: directory || "工作区根目录" };
}

/** 撤销本页面最近一次复制、移动或上传；所有逆操作仍走当前个人 worktree 的平台文件 RPC。 */
async function handleUndoWorkspaceFileOperation() {
  const workspace = selectedWorkspace.value;
  const operation = workspaceUndoStack.value.at(-1);
  if (!workspace || !currentPersonalWorkspaceId.value || !operation) return;

  try {
    if (operation.kind === "move") {
      const refreshTargets = migrateWorkspaceViewRefreshTargets(
        workspaceViewRefreshTargets(expandedDirectories.value, workspaceViewDirectoryById),
        operation.sourcePath,
        operation.targetPath
      );
      await api.moveWorkspaceFile(workspace.workspaceId, operation.sourcePath, operation.targetPath);
      const pendingReloadPaths = renameWorkspaceTreeEntry(
        operation.sourcePath,
        operation.targetPath,
        { deferLoadingReload: true }
      );
      await refreshWorkspaceView(workspace.workspaceId, {
        targets: refreshTargets,
        preserveLoadingPaths: new Set(pendingReloadPaths)
      });
      for (const path of pendingReloadPaths) void loadWorkspaceFile(path, { activate: false });
    } else {
      const originalPaths = [...operation.paths];
      const failedPaths: string[] = [];
      for (const path of [...originalPaths].reverse()) {
        try {
          await api.deleteWorkspaceFile(workspace.workspaceId, path);
        } catch {
          failedPaths.push(path);
        }
      }
      const directories = new Set(originalPaths.map(workspaceParentDirectory));
      await Promise.all([...directories].map((directory) => loadDirectory(directory, undefined, true)));
      if (failedPaths.length > 0) {
        operation.paths = failedPaths;
        throw new Error(`以下文件未能撤销：${failedPaths.join("、")}`);
      }
    }
    workspaceUndoStack.value.pop();
    void refreshWorkspaceGitDiff();
    feedback.value = { kind: "success", title: "已撤销文件操作", description: operation.label };
  } catch (error) {
    feedback.value = errorFeedback("撤销文件操作失败", error);
  }
}

function renameWorkspacePath(path: string, oldPath: string, nextPath: string): string {
  if (path === oldPath) {
    return nextPath;
  }
  const separator = oldPath.includes("\\") ? "\\" : "/";
  const prefix = `${oldPath}${separator}`;
  if (path.startsWith(prefix)) {
    return `${nextPath}${path.slice(oldPath.length)}`;
  }
  // 工作区相对路径通常使用 `/`；保留兼容 Windows 路径的归一化前缀判断。
  const normalizedPath = path.replace(/\\/g, "/");
  const normalizedOldPath = oldPath.replace(/\\/g, "/");
  if (normalizedPath.startsWith(`${normalizedOldPath}/`)) {
    return `${nextPath}${normalizedPath.slice(normalizedOldPath.length)}`;
  }
  return path;
}

function renameWorkspaceTreeEntry(
  path: string,
  nextPath: string,
  options: { deferLoadingReload?: boolean } = {}
): string[] {
  const nextEntriesByDirectory: Record<string, FileTreeEntry[]> = {};
  for (const [directory, entries] of Object.entries(entriesByDirectory.value)) {
    const nextDirectory = renameWorkspacePath(directory, path, nextPath);
    nextEntriesByDirectory[nextDirectory] = entries.map((entry) => ({
      ...entry,
      path: renameWorkspacePath(entry.path, path, nextPath)
    }));
  }
  entriesByDirectory.value = nextEntriesByDirectory;

  expandedDirectories.value = new Set(
    [...expandedDirectories.value].map((directory) => renameWorkspacePath(directory, path, nextPath))
  );
  loadingPath.value = new Set(
    [...loadingPath.value].map((loading) => renameWorkspacePath(loading, path, nextPath))
  );

  const openTabs = [...workbench.tabs];
  const targetPathsToReload: string[] = [];
  for (const tab of openTabs) {
    const renamedTabPath = renameWorkspacePath(tab.path, path, nextPath);
    if (renamedTabPath !== tab.path) {
      // 旧路径响应在 tab 改名后无法再命中；先显式失效，避免它与目标路径读取竞争。
      latestWorkspaceFileReadByPath.delete(tab.path);
      const wasLoading = tab.loadState === "loading";
      const canRestoreSnapshot = workbench.tabHasLoadedSnapshot(tab) || editorTabIsDirty(tab);
      const title = renamedTabPath.split(/[\\/]+/).filter(Boolean).at(-1) ?? renamedTabPath;
      workbench.renameTab(tab.path, renamedTabPath, title);
      if (wasLoading && canRestoreSnapshot) {
        workbench.updateTab(renamedTabPath, {
          loadState: "loaded",
          loadError: undefined,
          hasLoadedSnapshot: true
        });
      } else if (wasLoading) {
        targetPathsToReload.push(renamedTabPath);
      }
    }
  }
  // 普通重命名立即补读；移动需要先切换文件树代次，再由调用方在新代次下补读。
  if (!options.deferLoadingReload) {
    for (const targetPath of targetPathsToReload) {
      void loadWorkspaceFile(targetPath, { activate: false });
    }
  }
  const activePathAfterRename = workbench.activePath && renameWorkspacePath(workbench.activePath, path, nextPath);
  if (activePathAfterRename && activePathAfterRename !== workbench.activePath) {
    workbench.setActivePath(activePathAfterRename);
  }
  const selectedDiffPathAfterRename = workbench.selectedDiffPath && renameWorkspacePath(workbench.selectedDiffPath, path, nextPath);
  if (selectedDiffPathAfterRename && selectedDiffPathAfterRename !== workbench.selectedDiffPath) {
    workbench.setSelectedDiffPath(selectedDiffPathAfterRename);
  }
  return targetPathsToReload;
}

async function handleRenameEntry(path: string, name: string) {
  if (!selectedWorkspace.value) {
    return;
  }
  if (!currentPersonalWorkspaceId.value) {
    feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再修改应用文件。" };
    return;
  }
  const workspaceId = selectedWorkspace.value.workspaceId;
  const lastSepIndex = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  const parentDir = lastSepIndex >= 0 ? path.slice(0, lastSepIndex) : "";
  const separator = path.includes("\\") ? "\\" : "/";
  const nextPath = parentDir ? `${parentDir}${separator}${name}` : name;

  try {
    await api.renameWorkspaceFile(workspaceId, path, name);

    // 先更新内存树和已打开 tab；目录重命名时同时迁移已加载子树，用户无需刷新整棵工作区。
    renameWorkspaceTreeEntry(path, nextPath);
    const currentEntries = entriesByDirectory.value[parentDir] ?? [];
    entriesByDirectory.value = {
      ...entriesByDirectory.value,
      [parentDir]: currentEntries.map((entry) =>
        entry.path === nextPath ? { ...entry, name } : entry
      )
    };
    await refreshWorkspaceView(workspaceId);

    // 重命名会改变 Git diff 路径；刷新变更面板但不重新读取刚刚已经打开的文件内容。
    void refreshWorkspaceGitDiff();
    feedback.value = { kind: "success", title: "工作区条目已重命名", description: nextPath };
  } catch (error) {
    feedback.value = errorFeedback("重命名工作区条目失败", error);
  }
}

async function handleDeleteEntry(path: string, type: "file" | "directory") {
  if (!selectedWorkspace.value) {
    return;
  }
  if (!currentPersonalWorkspaceId.value) {
    feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再修改应用文件。" };
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

    // 如果被删除的是目录，同时清理其子目录缓存和所有后代展开状态。
    if (type === "directory") {
      const nextEntries = { ...entriesByDirectory.value };
      for (const key of Object.keys(nextEntries)) {
        if (key === path || key.startsWith(`${path}/`) || key.startsWith(`${path}\\`)) {
          delete nextEntries[key];
        }
      }
      entriesByDirectory.value = nextEntries;
      const normalizedPath = path.replace(/\\/g, "/");
      expandedDirectories.value = new Set(
        [...expandedDirectories.value].filter((directory) => {
          const normalizedDirectory = directory.replace(/\\/g, "/");
          return normalizedDirectory !== normalizedPath && !normalizedDirectory.startsWith(`${normalizedPath}/`);
        })
      );
    }
    await refreshWorkspaceView(workspaceId);

    // 删除目录时关闭目录内全部已打开文件，避免保留指向已不存在路径的编辑器标签。
    const normalizedDeletedPath = path.replace(/\\/g, "/");
    for (const tab of [...workbench.tabs]) {
      const normalizedTabPath = tab.path.replace(/\\/g, "/");
      if (normalizedTabPath === normalizedDeletedPath
          || (type === "directory" && normalizedTabPath.startsWith(`${normalizedDeletedPath}/`))) {
        workbench.closeTab(tab.path);
      }
    }
    void refreshWorkspaceGitDiff();
    feedback.value = {
      kind: "success",
      title: type === "directory" ? "文件夹已删除" : "文件已删除",
      description: path
    };
  } catch (error) {
    feedback.value = errorFeedback(`删除${type === "file" ? "文件" : "文件夹"}失败`, error);
  }
}

type CacheFileData = {
  title: string;
  content: string;
};

type SingleResponse = {
  data: {
    jumpUrl: string;
  };
};

async function handleCacheAndNavigate(path: string, type: "file" | "directory") {
  if (!selectedWorkspace.value) {
    return;
  }
  const workspaceId = selectedWorkspace.value.workspaceId;
  const appName = selectedManagedApplication.value?.appName ?? "";
  const now = new Date();
  const version = `${now.getFullYear()}年${now.getMonth() + 1}月`;
  const cacheDataUrl = import.meta.env.VITE_CACHE_DATA_URL ?? "";

  if (!cacheDataUrl) {
    ElMessage.error("缓存数据地址未配置");
    return;
  }

  try {
    let files: CacheFileData[] = [];
    let cacheType = "md";

    if (type === "directory") {
      if (path.includes("测试执行")) {
        cacheType = "json";
        files = await collectAllFilesInDirectory(workspaceId, path);
      } else {
        ElMessage.warning("仅测试执行目录支持文件夹缓存跳转");
        return;
      }
    } else {
      if (path.includes("测试设计")) {
        cacheType = "md";
        const fileContent = await api.readFile(workspaceId, path);
        files = [{ title: fileNameOf(path), content: fileContent.content }];
      } else if (path.includes("测试执行")) {
        cacheType = "json";
        const fileContent = await api.readFile(workspaceId, path);
        files = [{ title: fileNameOf(path), content: fileContent.content }];
      } else {
        ElMessage.warning("仅测试设计和测试执行目录下的文件支持单文件缓存跳转");
        return;
      }
    }

    if (files.length === 0) {
      ElMessage.warning("没有可缓存的文件");
      return;
    }

    const body = JSON.stringify({
      type: cacheType,
      appName,
      version,
      data: files,
    });

    const response = await fetch(`${cacheDataUrl}/aiTool/cacheData`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body,
    });

    const result = await response.json() as SingleResponse;
    console.log("============请求后台=====================", result);

    if (result.data?.jumpUrl) {
      window.open(result.data.jumpUrl, "_blank", "noopener,noreferrer");
    } else {
      ElMessage.error("获取跳转地址失败");
    }
  } catch (error) {
    console.error("缓存数据并跳转失败", error);
    ElMessage.error(error instanceof Error ? error.message : "缓存数据并跳转失败");
  }
}

async function collectAllFilesInDirectory(workspaceId: string, directoryPath: string): Promise<CacheFileData[]> {
  const files: CacheFileData[] = [];
  const queue: string[] = [directoryPath];

  while (queue.length > 0) {
    const currentPath = queue.shift()!;
    const entries = await api.listFiles(workspaceId, currentPath);

    for (const entry of entries) {
      if (entry.type === "directory") {
        queue.push(entry.path);
      } else {
        const fileContent = await api.readFile(workspaceId, entry.path);
        files.push({
          title: entry.name,
          content: fileContent.content,
        });
      }
    }
  }

  return files;
}

async function handlePreviewContext(item: ChatContextItem) {
  if (!item.path) {
    return;
  }
  if (item.openTarget?.locator.kind === "REFERENCE") {
    const alias = item.openTarget.locator.referenceAlias;
    if (!alias || item.openTarget.workspaceId !== selectedWorkspace.value?.workspaceId) return;
    await openWorkspaceViewFile({
      id: item.id,
      path: item.openTarget.logicalPath ?? item.openTarget.locator.path,
      name: item.fileName,
      type: "file",
      locator: item.openTarget.locator,
      source: "REFERENCE",
      merged: true,
      collision: false,
      readonly: true,
      referenceAliases: [alias]
    });
  } else {
    await openFile(item.path);
  }
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
  const reference = isReferenceFilePath(tab.path) ? referenceFileInfo(tab.path) : undefined;
  const selection = editorSelection.value;
  const text = selection.text;
  const result = chatContextStore.addSelectionContext({
    id: createContextId(),
    type: "selection",
    source: reference ? "reference" : "workspace",
    path: reference ? referenceChatPath(reference.referenceAlias, reference.referencePath) : tab.path,
    fileName: reference ? fileNameOf(reference.logicalPath) : fileNameOf(tab.path),
    language: languageFromPath(reference?.logicalPath ?? tab.path),
    startLine: selection.startLineNumber,
    endLine: selection.endLineNumber,
    text,
    charCount: text.length,
    createdAt: Date.now(),
    ...(reference ? {
      openTarget: {
        workspaceId: reference.workspaceId,
        locator: { kind: "REFERENCE" as const, path: reference.referencePath, referenceAlias: reference.referenceAlias },
        logicalPath: reference.logicalPath
      }
    } : {})
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

async function addWorkspaceFileToChatContext(path: string, silentSuccess = false): Promise<boolean> {
  if (!selectedWorkspace.value) {
    feedback.value = { kind: "info", title: "未选择工作区", description: "请先切换到可用工作区。" };
    return false;
  }
  try {
    const file = await api.readFile(selectedWorkspace.value.workspaceId, path);
    if (looksBinaryContent(file.content)) {
      feedback.value = { kind: "info", title: "暂不支持添加二进制文件", description: path };
      return false;
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
    notifyChatContextValidation(result, silentSuccess ? undefined : "已添加文件上下文");
    return result.ok;
  } catch (error) {
    feedback.value = errorFeedback("添加文件上下文失败", error);
    return false;
  }
}

async function addWorkspaceViewFileToChatContext(entry: WorkspaceViewEntry): Promise<boolean> {
  if (entry.source === "WORKSPACE") {
    return addWorkspaceFileToChatContext(entry.workspacePath ?? entry.path);
  }
  const workspace = selectedWorkspace.value;
  const alias = entry.locator.referenceAlias ?? entry.referenceAliases[0];
  if (!workspace || !alias) {
    feedback.value = { kind: "info", title: "无法添加引用文件", description: "引用来源缺少稳定别名。" };
    return false;
  }
  const workspaceGeneration = workspaceLoadGeneration;
  try {
    const file = await api.readWorkspaceViewFile(workspace.workspaceId, entry.locator);
    if (!workspaceViewContextIsCurrent(
      workspace.workspaceId,
      workspaceGeneration,
      selectedWorkspaceIdRef.value,
      workspaceLoadGeneration
    )) return false;
    if (looksBinaryContent(file.content)) {
      feedback.value = { kind: "info", title: "暂不支持添加二进制文件", description: entry.path };
      return false;
    }
    const content = file.content;
    const path = referenceChatPath(alias, entry.locator.path);
    const result = chatContextStore.addFileContext({
      id: `reference:${workspace.workspaceId}:${alias}:${entry.locator.path}`,
      type: "file",
      source: "reference",
      path,
      fileName: entry.name,
      language: languageFromPath(entry.path),
      content,
      charCount: content.length,
      lineCount: content.length === 0 ? 0 : content.split("\n").length,
      sizeBytes: new Blob([content]).size,
      createdAt: Date.now(),
      openTarget: {
        workspaceId: workspace.workspaceId,
        locator: entry.locator,
        logicalPath: entry.path
      }
    });
    notifyChatContextValidation(result, "已添加引用文件上下文");
    return result.ok;
  } catch (error) {
    feedback.value = errorFeedback("添加引用文件上下文失败", error);
    return false;
  }
}

/**
 * # 子条目选中后，把 spec/<需求项>/01-需求、02-设计、03-编码、04-测试下属于该子条目的全部文件逐个复用现有附件链路添加。
 * 单个文件仍沿用二进制、重复和容量校验，避免需求引用绕过对话上下文安全边界。
 */
async function addWorkspaceRequirementToChatContext(reference: WorkspaceRequirementReference) {
  let addedCount = 0;
  for (const path of reference.filePaths) {
    if (await addWorkspaceFileToChatContext(path, true)) {
      addedCount += 1;
    }
  }
  if (addedCount > 0) {
    feedback.value = {
      kind: "success",
      title: "已添加需求子条目上下文",
      description: `${reference.subitemName} · ${addedCount} 个文件`
    };
  }
}

async function openAgentFile(payload: AgentFileLoadRequest) {
  await loadAgentFile(payload);
}

/** Git 回退成功后按 tab 固化的 Agent 路由重读；未跟踪文件被删除时关闭对应 tab。 */
async function refreshDiscardedAgentFiles(payload: { scope: "PUBLIC" | "WORKSPACE"; paths: string[] }) {
  const paths = new Set(payload.paths);
  const tabs = workbench.tabs.filter((tab: EditorTab) => {
    if (!isAgentFilePath(tab.path)) return false;
    const file = agentFileInfo(tab.path);
    return file.scope === payload.scope && paths.has(file.path);
  });
  for (const tab of tabs) {
    const request = agentFileLoadRequestFromTab(tab, false);
    if (!request) continue;
    await loadAgentFile({
      ...request,
      closeOnNotFound: true,
      replaceExistingDirty: true
    });
  }
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

function toggleWorkspaceViewDirectory(entry: WorkspaceViewEntry) {
  const id = entry.id;
  workspaceViewDirectoryById.set(id, entry);
  if (loadingPath.value.has(id)) return;
  const next = new Set(expandedDirectories.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
    if (entriesByDirectory.value[id] === undefined) void loadDirectory(entry);
  }
  expandedDirectories.value = next;
}

function handleSend(prompt: string, attachments: ComposerAttachment[] = []) {
  // 历史切换完成前，当前 session 仍可能是上一会话；父层再次设防，避免绕过按钮状态误发 Run。
  if (historySwitchingSessionId.value) {
    return;
  }
  if (currentNightTask.value) {
    feedback.value = {
      kind: "info",
      title: "当前对话已有夜间任务",
      description: currentNightTask.value.status === "DISPATCHING"
        ? "任务正在启动，执行完成后可继续对话。"
        : "请先取消待执行任务，再在当前对话中发送新消息。"
    };
    return;
  }
  if (readonlySessionReason.value) {
    feedback.value = { kind: "info", title: "当前会话只读", description: readonlySessionReason.value };
    return;
  }
  if (opencodeProcessStatus.value?.messageSendAllowed === false) {
    feedback.value = {
      kind: "info",
      title: "公共 Agent/Skill 配置同步中",
      description: opencodeProcessStatus.value.messageSendBlockedReason
        ?? "旧会话排空并释放实例后将自动恢复发送"
    };
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
  // OpenCode 风格的 Timeline 只展示用户原始问题和附件标识；模型请求仍使用下方未裁剪的完整 parts。
  const submittedState = dispatchChat({
    type: "user.submitted",
    prompt: displayPrompt,
    parts: promptPartsForUserDisplay(parts)
  });
  const userMessageId = [...submittedState.messages].reverse().find((message) => message.role === "user")?.id;
  if (!submitPrompt) {
    return;
  }
  if (!userMessageId) {
    feedback.value = { kind: "error", title: "启动 Run 失败", description: "未能建立当前用户消息标识，请重新发送" };
    return;
  }
  // 启动计时 + 重置任务消耗累计（lastDuration/lastTokens 保留上一轮终态以供刷新对比）
  chatStartedAt.value = Date.now();
  accumulatedTokens.value = 0;
  // 解析命令（包括 Skill Command，格式为 /skill-name）
  const command = parseCommand(prompt, promptMode.value);
  if (runtimeBusy.value) {
    followUpQueue.value = enqueueFollowUp(
      followUpQueue.value,
      createFollowUpDraft(submitPrompt, parts, userMessageId, undefined, command ?? undefined)
    );
    feedback.value = { kind: "info", title: "Prompt 已排队", description: `等待当前 Run 完成后继续执行，队列 ${followUpQueue.value.length} 条` };
    chatContextStore.clearContexts();
    return;
  }
  // slash 技能和普通消息统一创建平台 Run，才能复用 SSE、刷新恢复和终止能力。
  const runDraft: AutoRetryRunDraft = {
    prompt: submitPrompt,
    parts,
    userMessageId,
    title: displayPrompt,
    command: command ?? undefined
  };
  lastRunDraft.value = runDraft;
  clearRunEventSseFeedback();
  requestChatRun(userMessageId);
  chatContextStore.clearContexts();
  startRunMutation.mutate({ input: runDraft, guard: captureConversationInteraction() });
}

/**
 * 夜间任务使用与立即发送相同的 prompt/上下文组装规则，但提交成功前不写入 Timeline，
 * 避免“尚未执行”的输入被误认为已经发送给模型。
 */
async function handleScheduleNight(payload: { prompt: string; slotStart: string }) {
  if (nightTaskSubmitting.value || historySwitchingSessionId.value) return;
  if (currentNightTask.value) {
    feedback.value = { kind: "info", title: "当前对话已有夜间任务", description: "请先取消原任务再重新安排。" };
    return;
  }
  if (readonlySessionReason.value) {
    feedback.value = { kind: "info", title: "当前会话只读", description: readonlySessionReason.value };
    return;
  }
  if (runtimeBusy.value) {
    feedback.value = { kind: "info", title: "当前任务仍在执行", description: "请等待当前任务结束后再安排夜间执行。" };
    return;
  }
  if (!opencodeProcessReady.value || opencodeProcessStatus.value?.messageSendAllowed === false) {
    feedback.value = {
      kind: "info",
      title: "暂不能安排夜间执行",
      description: opencodeProcessStatus.value?.messageSendBlockedReason
        ?? opencodeProcessStatus.value?.message
        ?? "请先初始化 TestAgent 进程。"
    };
    return;
  }
  const workspace = selectedWorkspace.value;
  if (!workspace) {
    feedback.value = { kind: "info", title: "未选择工作区", description: "请先切换到应用版本或个人工作区。" };
    return;
  }
  const validation = validateChatSend(payload.prompt.trim(), chatContextStore.items);
  if (!validation.ok) {
    feedback.value = { kind: "info", title: "上下文过长", description: validation.reason };
    return;
  }

  const guard = captureConversationInteraction();
  const chatContextParts = chatContextItemsToPromptParts(chatContextStore.items);
  const implicitEditorTab = chatContextStore.items.length === 0 ? activeTab.value : undefined;
  const implicitEditorSelection = chatContextStore.items.length === 0 ? editorSelection.value : undefined;
  const selectionContexts = chatContextStore.items.filter(
    (item): item is Extract<ChatContextItem, { type: "selection" }> => item.type === "selection"
  );
  const displayParts = buildPromptParts(
    payload.prompt,
    implicitEditorTab,
    [],
    [...chatContextParts, ...diffContextParts.value],
    implicitEditorSelection
  );
  const displayPrompt = payload.prompt.trim() || promptFromParts(displayParts);
  const rawSubmitPrompt = payload.prompt.trim() || displayPrompt;
  const submitPrompt = selectionContexts.length > 0
    ? serializeChatContexts(rawSubmitPrompt, selectionContexts)
    : rawSubmitPrompt;
  const parts = buildPromptParts(
    submitPrompt,
    implicitEditorTab,
    [],
    [...chatContextParts, ...diffContextParts.value],
    implicitEditorSelection
  );
  if (!submitPrompt) return;
  const command = parseCommand(payload.prompt, promptMode.value);
  const signature = JSON.stringify({
    sessionId: guard.sessionId,
    workspaceId: workspace.workspaceId,
    prompt: submitPrompt,
    parts,
    slotStart: payload.slotStart,
    agent: selectedAgent.value,
    model: selectedModel.value,
    mode: promptMode.value,
    command
  });
  if (!nightCreateIdempotency || nightCreateIdempotency.signature !== signature) {
    nightCreateIdempotency = {
      signature,
      clientRequestId: createClientRequestId(),
      runClientRequestId: createClientRequestId()
    };
  }
  const requestIds = nightCreateIdempotency;
  const request: CreateNightExecutionTaskPayload = {
    clientRequestId: requestIds.clientRequestId,
    runClientRequestId: requestIds.runClientRequestId,
    sessionId: guard.sessionId ?? undefined,
    workspaceId: workspace.workspaceId,
    sessionTitle: sessionTitleFromFirstMessage(displayPrompt),
    prompt: submitPrompt,
    parts,
    agent: selectedAgent.value || undefined,
    model: selectedModel.value || undefined,
    mode: promptMode.value,
    command: command?.command,
    arguments: command?.arguments,
    slotStart: payload.slotStart
  };

  nightTaskSubmitting.value = true;
  try {
    const created = await api.createNightExecutionTask(request);
    nightCreateIdempotency = null;
    nightTasks.value = [created, ...nightTasks.value.filter((task) => task.taskId !== created.taskId)];
    recentlyCreatedNightTask.value = created;
    if (!conversationInteractionIsCurrent(guard)) {
      void refreshNightExecutionTasks();
      return;
    }
    if (!session.value) {
      try {
        const createdSession = await api.getSession(created.sessionId);
        if (conversationInteractionIsCurrent(guard)) {
          session.value = createdSession;
        }
      } catch (error) {
        // 任务已经持久化成功；会话详情迟到不应把成功反馈改成失败，轮询会继续恢复。
        console.warn("夜间任务会话详情暂未恢复", error);
      }
    }
    if (session.value?.sessionId === created.sessionId) {
      recentlyCreatedNightTask.value = null;
    }
    chatContextStore.clearContexts();
    diffContextParts.value = [];
    feedback.value = {
      kind: "success",
      title: "夜间任务已安排",
      description: "任务会在所选 15 分钟时间段内自动启动。"
    };
    void queryClient.invalidateQueries({ queryKey: ["sessions"] });
    void refreshNightExecutionTasks();
  } catch (error) {
    const ambiguous = !(error instanceof BackendApiError) || error.status === 408 || error.status >= 500;
    if (!ambiguous) nightCreateIdempotency = null;
    feedback.value = errorFeedback("安排夜间任务失败", error);
    if (error instanceof BackendApiError && error.status === 409) void requestNightExecutionSlots();
    if (ambiguous) void refreshNightExecutionTasks();
  } finally {
    nightTaskSubmitting.value = false;
  }
}

function markNightTaskAction(taskId: string, pending: boolean) {
  const next = { ...nightTaskActionPending.value };
  if (pending) next[taskId] = true;
  else delete next[taskId];
  nightTaskActionPending.value = next;
}

async function handleAdjustNightTask(payload: { taskId: string; slotStart: string }) {
  if (nightTaskActionPending.value[payload.taskId]) return;
  markNightTaskAction(payload.taskId, true);
  try {
    const adjusted = await api.adjustNightExecutionTask(payload.taskId, payload.slotStart);
    nightTasks.value = nightTasks.value.map((task) => task.taskId === adjusted.taskId ? adjusted : task);
    if (recentlyCreatedNightTask.value?.taskId === adjusted.taskId) recentlyCreatedNightTask.value = adjusted;
    feedback.value = { kind: "success", title: "执行时间已调整", description: "系统已重新预留夜间容量。" };
    void requestNightExecutionSlots();
  } catch (error) {
    feedback.value = errorFeedback("调整夜间任务失败", error);
    if (error instanceof BackendApiError && error.status === 409) void requestNightExecutionSlots();
  } finally {
    markNightTaskAction(payload.taskId, false);
    void refreshNightExecutionTasks();
  }
}

async function handleCancelNightTask(taskId: string) {
  if (nightTaskActionPending.value[taskId]) return;
  const task = nightTasks.value.find((item) => item.taskId === taskId) ?? recentlyCreatedNightTask.value;
  markNightTaskAction(taskId, true);
  try {
    await api.cancelNightExecutionTask(taskId);
    nightTasks.value = nightTasks.value.filter((item) => item.taskId !== taskId);
    if (recentlyCreatedNightTask.value?.taskId === taskId) recentlyCreatedNightTask.value = null;
    feedback.value = { kind: "success", title: "夜间任务已取消", description: "当前对话现在可以继续发送消息。" };
    const persistedMessageCount = chatState.value.messages.filter((message) => message.id !== "welcome").length;
    if (task?.sessionId === session.value?.sessionId
        && shouldResetAfterNightTaskClosure(session.value, taskId, persistedMessageCount)) {
      handleNewConversation();
    }
    void requestNightExecutionSlots();
  } catch (error) {
    feedback.value = errorFeedback("取消夜间任务失败", error);
  } finally {
    markNightTaskAction(taskId, false);
    void refreshNightExecutionTasks();
  }
}

async function handleDismissNightTask(taskId: string) {
  if (nightTaskActionPending.value[taskId]) return;
  const failure = nightVisibleFailure.value?.taskId === taskId ? nightVisibleFailure.value : null;
  markNightTaskAction(taskId, true);
  try {
    await api.dismissNightExecutionTask(taskId);
    if (nightVisibleFailure.value?.taskId === taskId) nightVisibleFailure.value = null;
    const persistedMessageCount = chatState.value.messages.filter((message) => message.id !== "welcome").length;
    if (failure?.sessionId === session.value?.sessionId
        && shouldResetAfterNightTaskClosure(session.value, taskId, persistedMessageCount)) {
      handleNewConversation();
    }
  } catch (error) {
    feedback.value = errorFeedback("关闭夜间任务提示失败", error);
  } finally {
    markNightTaskAction(taskId, false);
    void refreshNightExecutionTasks();
  }
}

function openNightTaskSession(sessionId: string) {
  if (session.value?.sessionId === sessionId) return;
  void switchSession(sessionId);
}

function latestRemoteMessageId(): string | undefined {
  for (let index = chatState.value.messages.length - 1; index >= 0; index -= 1) {
    const message = chatState.value.messages[index];
    const remoteMessageId = remoteMessageIdForAgentMessage(message);
    if (remoteMessageId) {
      return remoteMessageId;
    }
  }
  return undefined;
}

async function submitRobotQuestion(question: string) {
  if (opencodeProcessStatus.value?.messageSendAllowed === false) {
    robotSideQuestion.error.value = opencodeProcessStatus.value.messageSendBlockedReason
      ?? "公共 Agent/Skill 配置正在同步，旧会话排空后将自动恢复发送";
    return;
  }
  if (session.value?.sessionId) {
    await robotSideQuestion.submit({
      sessionId: session.value.sessionId,
      question,
      messageId: latestRemoteMessageId(),
      model: selectedModel.value || undefined
    });
    return;
  }
  const workspaceId = selectedWorkspaceIdRef.value;
  if (!workspaceId || !opencodeProcessReady.value) {
    robotSideQuestion.error.value = "请先选择工作区并初始化 TestAgent 服务";
    return;
  }
  await robotSideQuestion.submit({
    workspaceId,
    question,
    model: selectedModel.value || undefined
  });
}

async function handleRobotSideQuestion(question: string) {
  const groundedQuestion = session.value?.sessionId
    ? question
    : buildManualQuestionPrompt(DEFAULT_HELP_TOPIC, question);
  await submitRobotQuestion(groundedQuestion);
}

async function handleManualQuestion(question: string) {
  await submitRobotQuestion(question);
}

function handleCloseRobotSideQuestion() {
  robotSideQuestion.reset();
}

/**
 * 顶部入口和具体功能按钮共用一个帮助中心，只通过 topic 决定初始章节。
 */
function openHelpCenter(topic = "getting-started") {
  helpCenterTopic.value = topic;
  helpCenterOpen.value = true;
}

function prepareFirstLoginGuide() {
  firstLoginGuideActive.value = true;
  settingsOpen.value = false;
  firstLoginGuideSettingsMenu.value = "appWorkspace";
  firstLoginGuideSettingsTab.value = undefined;
  leftPanelOpen.value = true;
  rightPanelOpen.value = true;
  centerMode.value = "editor";
}

function handleFirstLoginGuideSettingsStep(
  open: boolean,
  target?: "personal" | "repository" | "members" | "repositories" | "workspaces"
) {
  if (target === "personal") {
    firstLoginGuideSettingsMenu.value = "personal";
    firstLoginGuideSettingsTab.value = undefined;
  } else if (target === "repository") {
    firstLoginGuideSettingsMenu.value = "repository";
    firstLoginGuideSettingsTab.value = undefined;
  } else if (target) {
    firstLoginGuideSettingsMenu.value = "appWorkspace";
    firstLoginGuideSettingsTab.value = target;
  }
  settingsOpen.value = open;
}

function finishFirstLoginGuide() {
  firstLoginGuideActive.value = false;
  openHelpCenter("getting-started");
}

function dismissFirstLoginGuide() {
  firstLoginGuideActive.value = false;
}

async function restartFirstLoginGuide() {
  helpCenterOpen.value = false;
  prepareFirstLoginGuide();
  await nextTick();
  firstLoginGuideRef.value?.restart();
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

/** 失败后的所有重试入口复用原用户消息轮次，不追加第二条相同的乐观 user message。 */
function handleRetryRun() {
  const previousDraft = lastRunDraft.value;
  if (!previousDraft?.prompt.trim()) {
    feedback.value = {
      kind: "info",
      title: "无法重试",
      description: "未找到上一条任务内容，请重新输入后发送"
    };
    return;
  }
  const retryDraft: AutoRetryRunDraft = {
    ...previousDraft,
    userMessageId: run.value?.runId
      ? chatState.value.todoUserMessageIdByRunId[run.value.runId] ?? previousDraft.userMessageId
      : previousDraft.userMessageId
  };
  lastRunDraft.value = retryDraft;
  chatStartedAt.value = Date.now();
  accumulatedTokens.value = 0;
  clearRunEventSseFeedback();
  requestChatRun(retryDraft.userMessageId);
  startRunMutation.mutate({ input: retryDraft, guard: captureConversationInteraction() });
}

function handleRunEvent(event: RunEvent, subscribedSessionId?: string) {
  const projectedEvent = projectRootInteractionSession(event, subscribedSessionId);
  logs.value = [...logs.value.slice(-200), `[${projectedEvent.seq}] ${projectedEvent.type}`];
  if (isInteractionAskSupersededBySnapshot(projectedEvent)) {
    logs.value = [...logs.value.slice(-200), `[${projectedEvent.seq}] ignored stale interaction replay`];
    return;
  }
  dispatchChat({ type: "event", event: projectedEvent });
  const projection = runEventProjection(projectedEvent);
  if (projection.reset) {
    // reducer 已经原子完成“清空 + 快照重放”；Workbench 还需清掉独立维护的 Diff/工具跟随状态，
    // 再按相同顺序应用快照事件的页面副作用。恢复快照不重复触发桌面通知。
    diffFiles.value = [];
    diffSource.value = "run";
    liveFollowedParts.value = new Set();
    workbench.setSelectedDiffPath(undefined);
    for (const snapshotEvent of projection.events) {
      logs.value = [...logs.value.slice(-200), `[snapshot:${snapshotEvent.seq}] ${snapshotEvent.type}`];
      applyRunEventWorkbenchProjection(snapshotEvent, false, subscribedSessionId);
    }
    return;
  }
  applyRunEventWorkbenchProjection(projectedEvent, true, subscribedSessionId);
}

function isInteractionAskSupersededBySnapshot(event: RunEvent): boolean {
  if (event.type !== "permission.asked" && event.type !== "question.asked") {
    return false;
  }
  const eventSessionId = text(event.payload.sessionId) ?? text(event.payload.sessionID);
  if (!eventSessionId) {
    return false;
  }
  const snapshot = interactionSnapshotBySessionId.get(eventSessionId);
  return isSupersededInteractionAsk(
    event,
    snapshot?.synchronizedAtMs,
    event.type === "permission.asked" ? snapshot?.permissionRequestIds : snapshot?.questionRequestIds
  );
}

/** 将单条业务事件投影到 reducer 之外的 Workbench 状态。 */
function applyRunEventWorkbenchProjection(
  event: RunEvent,
  allowNotification: boolean,
  subscribedSessionId?: string
) {
  if (allowNotification) {
    notifyOnAttention(event, selectedWorkspace.value, session.value);
  }
  if (event.type === "session.updated") {
    const matchesCurrentSession = sessionTitleEventMatchesCurrentSession(subscribedSessionId, session.value?.sessionId);
    // 后端可能把 pending 标记与终态事件分开推送；只要仍是当前 root 会话，就保留同一条 SSE。
    if (event.payload.platformSessionTitlePending === true && matchesCurrentSession) {
      pendingSessionTitleRunId.value = event.runId;
    }
    const title = platformSessionTitleFromSynchronizedEventPayload(event.payload);
    const synchronizedCurrentRootTitle = Boolean(
      title
      && event.payload.isChildSession !== true
      && matchesCurrentSession
      && session.value
    );
    // 只有后端确认标题已写入平台 Session 后才刷新页面；同时拒绝子会话和历史切换后晚到的旧事件。
    if (synchronizedCurrentRootTitle && session.value) {
      const currentSessionId = session.value.sessionId;
      session.value = { ...session.value, title };
      sessionHistoryItems.value = sessionHistoryItems.value.map((item) =>
        item.sessionId === currentSessionId ? { ...item, title } : item
      );
      void queryClient.invalidateQueries({ queryKey: ["sessions"] });
    }
    if (
      pendingSessionTitleRunId.value === event.runId
      && (synchronizedCurrentRootTitle || event.payload.platformSessionTitleWatchClosed === true)
    ) {
      pendingSessionTitleRunId.value = null;
    }
  }
  if (event.type === "run.created") {
    const summaryMessageId = assistantSummaryMessageId(event.payload);
    if (summaryMessageId) {
      assistantSummaryMessageIdsByRunId.value = {
        ...assistantSummaryMessageIdsByRunId.value,
        [event.runId]: summaryMessageId
      };
    }
  }
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
    // 必须先建立 hold 再修改 run.status，否则标量订阅会在根纠正终态到达前关闭。
    holdTerminalRunEventSubscription(event.runId);
    if (event.type === "run.succeeded") {
      clearRunEventSseFeedback();
    }
    run.value = run.value
      ? { ...run.value, status: event.type === "run.succeeded" ? "SUCCEEDED" : event.type === "run.failed" ? "FAILED" : "CANCELLED" }
      : run.value;
    // Run 完成后刷新所有已展开的目录，确保新增/删除/修改的文件在左侧工作区立即可见。
    setTimeout(() => {
      void refreshWorkspaceView();
    }, 500);
    // 计算任务消耗统计：duration 由 chatStartedAt 锁定，tokens 仍优先取累计值；
    // 如果后端 payload 直接带上 tokens 字段，则覆盖一次（向后兼容未来后端实现）。
    if (chatStartedAt.value) {
      totalDurationMs.value += Date.now() - chatStartedAt.value;
      lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
      chatStartedAt.value = null;
    }
    const payload = event.payload as Record<string, unknown>;
    if (event.type === "run.succeeded" && payload.platformSessionTitlePending === true) {
      pendingSessionTitleRunId.value = event.runId;
    } else if (event.type !== "run.succeeded" && pendingSessionTitleRunId.value === event.runId) {
      pendingSessionTitleRunId.value = null;
    }
    if (typeof payload.tokens === "number") {
      lastTokens = payload.tokens;
    }
    if (run.value?.sessionId) {
      const assistantSummaryMessageId = assistantSummaryMessageIdsByRunId.value[event.runId];
      const latestAssistant = [...chatState.value.messages]
        .reverse()
        .find(message => message.role === "assistant");
      const remoteMessageId = latestAssistant?.role === "assistant"
        ? remoteMessageIdForAgentMessage(latestAssistant)
        : undefined;
      if (assistantSummaryMessageId) {
        if (remoteMessageId) {
          // REDIS_SUMMARY 在 Run 创建时即给出稳定平台 messageId；终态直接绑定，不轮询 Session 消息表。
          platformMessageIdsByRemoteId.value = {
            ...platformMessageIdsByRemoteId.value,
            [remoteMessageId]: assistantSummaryMessageId
          };
          void loadFeedbacksForRunIds([event.runId], run.value.sessionId);
        }
      } else {
        // legacy 后端没有稳定摘要 ID，继续使用原有短期兼容查询。
        startLegacyFeedbackRecovery(event.runId, run.value.sessionId);
      }
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

function startLegacyFeedbackRecovery(runId: string, sessionId: string) {
  if (legacyFeedbackRecoveryRunIds.has(runId)) {
    return;
  }
  legacyFeedbackRecoveryRunIds.add(runId);
  void refreshPersistedFeedbackIdentities(runId, sessionId);
}

async function refreshPersistedFeedbackIdentities(runId: string, sessionId: string, attempt = 1): Promise<void> {
  try {
    const page = await api.listSessionMessages(sessionId, 1, 100, { refresh: false });
    if (session.value?.sessionId !== sessionId) {
      return;
    }
    const persistedMessages = dedupeSessionMessages(page.items);
    rememberPersistedMessageIdentities(persistedMessages);
    await loadFeedbacksForMessages(persistedMessages, sessionId);
    if (attempt < 3 && hasUnmappedAssistantRemoteMessages()) {
      setTimeout(() => void refreshPersistedFeedbackIdentities(runId, sessionId, attempt + 1), 500);
    }
  } catch {
    if (attempt < 3 && session.value?.sessionId === sessionId) {
      setTimeout(() => void refreshPersistedFeedbackIdentities(runId, sessionId, attempt + 1), 500);
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
    void refreshWorkspaceView();
    return;
  }
  const parentPath = segments.slice(0, -1).join("/");
  void loadDirectory(parentPath, undefined, true);
}

// 展开文件树到目标文件：把所有祖先目录加入 expandedDirectories 并按需懒加载。
async function expandPathToFile(relPath: string) {
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
    const target = resolveWorkspaceViewLoadTarget(acc, workspaceViewDirectoryById);
    if (!target) return;
    next.add(target.id);
    expandedDirectories.value = new Set(next);
    if (!entriesByDirectory.value[target.id]) await loadDirectory(target);
  }
  expandedDirectories.value = next;
}

/** 引用 tab 使用精确叶子节点反向展开，避免合并路径或同名文件定位到工作区副本。 */
async function expandWorkspaceViewNodeToFile(tabPath: string): Promise<boolean> {
  const nodeId = workspaceViewNodeIdByTabPath.get(tabPath);
  if (!nodeId) return false;
  const ancestorIds = workspaceViewAncestorDirectoryIds(nodeId, entriesByDirectory.value);
  if (!ancestorIds) return false;

  const next = new Set(expandedDirectories.value);
  for (const ancestorId of ancestorIds) {
    const target = workspaceViewDirectoryById.get(ancestorId);
    if (!target) return false;
    next.add(ancestorId);
    expandedDirectories.value = new Set(next);
    if (entriesByDirectory.value[ancestorId] === undefined) {
      await loadDirectory(target);
    }
  }
  expandedDirectories.value = next;
  return true;
}

// 编辑器定位：引用文件按稳定节点展开，普通文件沿用相对路径；完成后再滚动。
async function handleLocateFile(path: string) {
  if (!path) return;
  workbench.setActivePath(path);
  if (isReferenceFilePath(path)) {
    await expandWorkspaceViewNodeToFile(path);
  } else {
    await expandPathToFile(path);
  }
  await nextTick();
  scrollToActiveFileTreeRow();
  setTimeout(scrollToActiveFileTreeRow, 100);
  setTimeout(scrollToActiveFileTreeRow, 300);
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
        const wksDiff = await api.getWorkspaceAgentDiff(selectedWorkspace.value.workspaceId).catch(() => ({ files: [] }));
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
  const workspace = selectedWorkspace.value;
  if (!workspace) {
    return;
  }
  const context: WorkspaceFileLoadContext = {
    workspaceId: workspace.workspaceId,
    workspaceGeneration: workspaceLoadGeneration
  };
  const pathFilter = paths && paths.length > 0 ? new Set(paths) : null;
  const workspaceTabs = workbench.tabs.filter(
    (tab: EditorTab) =>
      !tab.livePreview &&
      !isAgentFilePath(tab.path) &&
      !isReferenceFilePath(tab.path) &&
      (!pathFilter || pathFilter.has(tab.path))
  );
  for (const tab of workspaceTabs) {
    // 批量刷新跨越 await；每轮前后都核对起始 Workspace，切换后立即终止旧循环。
    if (!workspaceFileLoadContextIsCurrent(context)) {
      break;
    }
    await loadWorkspaceFile(tab.path, {
      activate: false,
      closeOnNotFound: true,
      expectedContext: context
    });
    if (!workspaceFileLoadContextIsCurrent(context)) {
      break;
    }
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
        if (!agent.workspaceId) {
          throw new Error("Agent 文件缺少 Workspace 路由");
        }
        await api.writeWorkspaceAgentFile(agent.workspaceId, agent.path, content, agent.worktreeId);
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
    const agentInfo = isAgentFilePath(path) ? agentFileInfo(path) : null;
    if (agentInfo) {
      agentConfigRevision.value += 1;
    }
    const catalogRefreshError = agentInfo
      ? await refreshRuntimeCatalogAfterAgentConfigSave(agentInfo)
      : null;
    feedback.value = catalogRefreshError
      ? errorFeedback("文件已保存，运行态目录刷新失败", catalogRefreshError)
      : {
          kind: "success",
          title: "文件已保存",
          description: userRuntimeBusy.value
            && agentInfo
            && shouldReloadPersonalRuntimeCatalog(agentInfo.scope, agentInfo.path)
            ? `${path}；当前用户任务结束后会自动重新加载运行态。`
            : path
        };
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
  // 历史切换必须释放旧 Run 的标题待定订阅，避免晚到事件改写新打开的会话。
  pendingSessionTitleRunId.value = null;
  invalidateConversationInteraction();
  const historyInteractionGeneration = conversationInteractionGeneration;
  const switchSeq = historySwitchSeq;
  // 所有 await 后复用同一 guard，避免较慢的旧历史请求覆盖后点击的新会话。
  const switchIsCurrent = () => switchSeq === historySwitchSeq
    && historyInteractionGeneration === conversationInteractionGeneration;
  historyLoadingSessionId.value = sessionId;
  historySwitchingSessionId.value = sessionId;
  let selected = sessionsItems.value.find((item) => item.sessionId === sessionId);
  if (!selected) {
    selected = await api.getSession(sessionId);
    if (!switchIsCurrent()) {
      return;
    }
  }
  // 历史消息和当前交互快照先取；大树快照/Todo 作为增强，避免历史记录首屏被串行请求拖慢。
  const historyMessagesPromise = api.listSessionMessages(sessionId, 1, 100, { refresh: false });
  const historyInteractionsPromise = Promise.all([
    api.listSessionPermissions(sessionId).catch(() => null),
    api.listSessionQuestions(sessionId).catch(() => null)
  ]);
  const historyEnrichmentPromise = Promise.all([
    api.getSessionTreeMessages(sessionId).catch(() => null),
    api.getSessionTodo(sessionId).catch(() => null)
  ]);
  // 工作区切换失败/被新会话取消时仍消费拒绝，避免后台请求产生 unhandled rejection。
  void historyMessagesPromise.catch(() => undefined);
  void historyInteractionsPromise.catch(() => undefined);
  void historyEnrichmentPromise.catch(() => undefined);
  const readonlyReason = await switchToHistorySessionWorkspace(selected, switchIsCurrent);
  if (!switchIsCurrent() || readonlyReason === null) {
    return;
  }
  session.value = selected;
  readonlySessionReason.value = readonlyReason;
  // 工作区校验已通过后立即清理上一 Session 的交互 dock；新 Session 的 pending 快照随后再填充。
  dispatchChat({ type: "reset" });
  if (!readonlyReason) {
    // 历史会话切到可交互工作区后预取一次；立即发送会复用同一个 in-flight Promise。
    void conversationRunContexts.get(sessionId).catch((error) => {
      console.warn("预取会话运行上下文失败", error);
    });
  }
  const adoptedRuntimeRun = adoptRuntimeStateForCurrentSession(sessionRuntimeState.value, "switch-session");
  // 只有 runtime-state 标记为运行中时才做后台终态校准，避免每个已结束历史会话都增加一次请求。
  const activeRunPromise = adoptedRuntimeRun
    ? api.getActiveRun(sessionId).catch(() => undefined)
    : Promise.resolve(undefined);
  void activeRunPromise.catch(() => undefined);
  if (!adoptedRuntimeRun) {
    fallbackActiveRunOnce("switch-session-stream-unavailable");
  }
  // 切换会话后先清空上一轮任务的消耗统计，防止上一轮对话的耗时残留。
  chatStartedAt.value = null;
  accumulatedTokens.value = 0;
  totalDurationMs.value = 0;
  lastDuration = undefined;
  lastTokens = 0;
  nowTick.value = Date.now();
  clearAutoRetryState();
  try {
    const page = await historyMessagesPromise;
    if (!switchIsCurrent()) {
      return;
    }
    const persistedMessages = dedupeSessionMessages(page.items);
    rememberPersistedMessageIdentities(persistedMessages);
    // 先以分页消息渲染正文，树快照和 Todo 作为后续增强；避免大历史树把首屏卡住。
    dispatchChat({ type: "reset", messages: messagesFromSessionMessages(persistedMessages) });
    // 视觉 loading 只等待数据库正文；实时 interaction 校准继续后台完成，发送锁仍由 switching 状态持有。
    historyLoadingSessionId.value = null;
    const [livePermissions, liveQuestions] = await historyInteractionsPromise;
    if (!switchIsCurrent()) {
      return;
    }
    // 历史事件树可能保留已经失效的 ask；以 OpenCode 当前 pending 列表覆盖交互请求，
    // 避免展示无法提交的旧 requestId，同时保留接口暂时不可用时的历史降级展示。
    if (livePermissions !== null || liveQuestions !== null) {
      chatState.value = {
        ...chatState.value,
        permissions: livePermissions === null
          ? chatState.value.permissions
          : livePermissions.filter((item) => item.sessionId === sessionId),
        questions: liveQuestions === null
          ? chatState.value.questions
          : liveQuestions.filter((item) => item.sessionId === sessionId),
      };
    }
    if (livePermissions !== null || liveQuestions !== null) {
      interactionSnapshotBySessionId.set(sessionId, {
        synchronizedAtMs: Date.now(),
        permissionRequestIds: livePermissions === null
          ? undefined
          : new Set(livePermissions.map((item) => item.requestId)),
        questionRequestIds: liveQuestions === null
          ? undefined
          : new Set(liveQuestions.map((item) => item.requestId))
      });
    }
    // 当前交互快照已可用；后续树/待办/Run 详情只影响完整投影和发送锁，不再影响正文首屏。
    const [treeSnapshot, liveTodos] = await historyEnrichmentPromise;
    if (!switchIsCurrent()) {
      return;
    }
    const restoredState = treeSnapshot ? chatStateFromSessionTreeSnapshot(treeSnapshot, persistedMessages) : null;
    if (restoredState && restoredState.messages.length > 0) {
      chatState.value = restoredState;
      if (livePermissions !== null || liveQuestions !== null || liveTodos !== null) {
        chatState.value = {
          ...chatState.value,
          permissions: livePermissions === null
            ? chatState.value.permissions
            : livePermissions.filter((item) => item.sessionId === sessionId),
          questions: liveQuestions === null
            ? chatState.value.questions
            : liveQuestions.filter((item) => item.sessionId === sessionId),
          todos: chatState.value.todos
        };
      }
      if (liveTodos !== null) {
        chatState.value = reconcileCurrentTurnTodos(chatState.value, liveTodos);
      }
    } else if (liveTodos !== null) {
      chatState.value = reconcileCurrentTurnTodos(chatState.value, liveTodos);
    }
    // 正文可以先展示，但发送锁必须保留到关联 Run/Diff 投影完成，避免迟到历史详情覆盖新 Run。
    // 反馈状态独立异步补齐，不延长这把锁。
    void loadFeedbacksForMessages(persistedMessages, sessionId, switchIsCurrent);
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
        if (!switchIsCurrent()) {
          return;
        }
        run.value = runDetail;
        rememberRunSession(runDetail);
        const runFiles = (diffDetail.files ?? []).map((file) => ({
          ...file,
          path: normalizeWorkspacePath(file.path) || file.path
        }));
        diffFiles.value = mergeDiffFiles(restoredFiles, runFiles);
      } catch (runErr) {
        if (switchIsCurrent()) {
          console.error("加载关联 Run 失败", runErr);
        }
      }
    } else {
      run.value = null;
    }

    // 历史 Run 详情可能覆盖切换开始时从 runtime-state 接管的活跃 Run，加载结束后再以实时摘要校正一次。
    if (!switchIsCurrent()) {
      return;
    }
    const restoredRuntimeRun = adoptRuntimeStateForCurrentSession(sessionRuntimeState.value, "switch-session-loaded");
    if (!restoredRuntimeRun) {
      fallbackActiveRunOnce("switch-session-loaded-stream-unavailable");
    }
    // runtime-state 是摘要，不是终态事实；历史打开后再读一次 active-run，避免旧摘要把已结束 Run 复活。
    void activeRunPromise.then((activeRun) => {
      if (!switchIsCurrent() || activeRun === undefined) {
        return;
      }
      if (activeRun && isRunBusyStatus(activeRun.status)) {
        run.value = activeRun;
        markConversationRunAdopted(activeRun.runId);
        rememberRunSession(activeRun);
        return;
      }
      if (session.value?.sessionId === sessionId) {
        run.value = null;
      }
    }).catch(() => undefined);

    feedback.value = { kind: "info", title: "已切换 Session", description: selected.title };
  } catch (error) {
    if (switchIsCurrent()) {
      feedback.value = errorFeedback("加载 Session 消息失败", error);
    }
  } finally {
    if (switchIsCurrent()) {
      historyLoadingSessionId.value = null;
      historySwitchingSessionId.value = null;
    }
  }
}

async function switchToHistorySessionWorkspace(
  selected: Session,
  interactionIsCurrent: () => boolean
): Promise<string | null> {
  const expectedAppId = selected.workspaceContext?.appId?.trim();
  const requiresManagedWorkspace = Boolean(expectedAppId);
  try {
    let workspace = await api.getWorkspace(selected.workspaceId);
    if (!interactionIsCurrent()) {
      return null;
    }
    try {
      // markRecentManagedWorkspace 会校验当前用户仍可进入历史会话所属应用，并回填版本/模板信息。
      const response = await api.markRecentManagedWorkspace(selected.workspaceId);
      if (!interactionIsCurrent()) {
        return null;
      }
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
    if (!interactionIsCurrent()) {
      return null;
    }
    if (nextAppId) {
      selectedAppId.value = nextAppId;
    }
    if (workspace.workspaceId !== selectedWorkspaceIdRef.value) {
      if (!interactionIsCurrent()) {
        return null;
      }
      await switchWorkspace(workspace, { preserveConversationInteraction: true, awaitDirectory: false });
      if (!interactionIsCurrent()) {
        return null;
      }
    } else {
      if (!interactionIsCurrent()) {
        return null;
      }
      cacheWorkspace(workspace);
      selectedWorkspaceSnapshot.value = workspace;
      syncCurrentVersionFromWorkspace(workspace);
    }
    return "";
  } catch (error) {
    if (!interactionIsCurrent()) {
      return null;
    }
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
  invalidateConversationInteraction();
  rememberCurrentRunAsBackgroundRuntimeState();
  pendingSessionTitleRunId.value = null;
  session.value = null;
  run.value = null;
  nightVisibleFailure.value = null;
  recentlyCreatedNightTask.value = null;
  void queryClient.invalidateQueries({ queryKey: ["sessions"] });
  clearAutoRetryState();
  dispatchChat({ type: "reset" });
  runFeedbacks.value = {};
  feedbackSubmitting.value = {};
  platformMessageIdsByRemoteId.value = {};
  assistantSummaryMessageIdsByRunId.value = {};
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
  messages: Array<Pick<SessionMessage, "messageId" | "role" | "remoteMessageId" | "runId">>,
  expectedSessionId?: string,
  interactionIsCurrent: () => boolean = () => true
) {
  rememberPersistedMessageIdentities(messages);
  const uniqueIds = [...new Set(messages.map(message => message.runId).filter((runId): runId is string => Boolean(runId)))];
  await loadFeedbacksForRunIds(uniqueIds, expectedSessionId, interactionIsCurrent);
}

async function loadFeedbacksForRunIds(
  runIds: string[],
  expectedSessionId?: string,
  interactionIsCurrent: () => boolean = () => true
) {
  const loaded: Record<string, AiRunFeedback | null> = {};
  const statuses: Record<string, string> = {};
  try {
    for (let index = 0; index < runIds.length; index += 100) {
      const states = await api.queryMyRunFeedbacks({ runIds: runIds.slice(index, index + 100) });
      for (const state of states) {
        loaded[state.runId] = state.feedback ?? null;
        statuses[state.runId] = state.runStatus;
      }
    }
  } catch {
    // 历史反馈加载失败不隐藏入口；已由消息/RunEvent 恢复的成功状态继续用于展示。
  }
  if (interactionIsCurrent() && (!expectedSessionId || session.value?.sessionId === expectedSessionId)) {
    runFeedbacks.value = { ...runFeedbacks.value, ...loaded };
    if (Object.keys(statuses).length > 0) {
      dispatchChat({ type: "run.statuses.loaded", statuses });
    }
  }
}

function handleSubmitFeedback(payload: AiRunFeedbackPayload & { runId: string }) {
  submitRunFeedbackMutation.mutate(payload);
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
    :can-play-pet-games="isSuperAdmin"
    :can-manage-public-agent-config="isSuperAdmin"
    :can-manage-workspace-agent-config="isAppAdmin"
    :personal-runtime-reloading="personalRuntimeReloading"
    :runtime-busy="runtimeReloadBusy"
    :opencode-process-status="opencodeProcessStatus"
    :opencode-process-loading="opencodeProcessInitialLoading"
    :opencode-process-initializing="initializeOpencodeProcessMutation.isPending.value"
    show-process-status-in-pet
    :onboarding-active="firstLoginGuideActive"
    :side-question-answer="robotSideQuestion.answer.value"
    :side-question-error="robotSideQuestion.error.value"
    :side-question-loading="robotSideQuestion.loading.value"
    :side-question-progress="robotSideQuestion.progress.value"
    :side-question-available="robotQuestionAvailable"
    :side-question-manual-mode="!session?.sessionId"
    :runtime-inventory="runtimeInventoryForShell"
    @toggle-left-panel="leftPanelOpen = !leftPanelOpen"
    @toggle-right-panel="rightPanelOpen = !rightPanelOpen"
    @select-app="handleSelectApp"
    @refresh-opencode-process="refreshOpencodeProcessStatus"
    @initialize-process="beginInitializeOpencodeProcess"
    @logout="handleLogout"
    @join-app="handleJoinApp"
    @robot-side-question="handleRobotSideQuestion"
    @close-robot-side-question="handleCloseRobotSideQuestion"
    @personal-runtime-reload="handlePersonalRuntimeReload"
    @open-help="openHelpCenter"
  >
    <template #activity>
      <nav class="figma-activity-nav" aria-label="工作台活动栏">
        <div class="figma-activity-top">
          <button
            type="button"
            :class="['figma-activity-btn', centerMode === 'editor' && 'figma-activity-btn--active']"
            data-onboarding="editor-button"
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
            data-onboarding="settings"
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
          :active-path="activeWorkspaceViewNodeId"
          :changed-files="vcsDiffFiles"
          :loading-path="loadingPath"
          :app-name="selectedManagedApplication?.appName"
          :app-templates="appTemplatesWithVersions"
          :selected-version-id="selectedVersionId"
          :loading-app-templates="loadingAppTemplates"
          :loading-app-versions="loadingAppVersions"
          :creating-version="creatingVersion"
          :can-write="!!currentPersonalWorkspaceId"
          :can-undo="workspaceUndoStack.length > 0"
          :can-manage-agent-config="isAppAdmin"
          :can-manage-public-config="isSuperAdmin"
          :api-base-url="apiBaseUrl"
          :route-linux-server-id="routeLinuxServerId"
          :workspace-id="selectedWorkspace?.workspaceId"
          :agent-config-workspace-id="selectedAgentConfigWorkspaceId"
          :personal-workspace-id="currentPersonalWorkspaceId"
          :personal-workspace-branch="currentPersonalWorkspaceBranch"
          :agent-config-revision="agentConfigRevision"
          :personal-runtime-reloading="personalRuntimeReloading"
          :runtime-busy="runtimeReloadBusy"
          :show-server-workspace-switch="isSuperAdmin"
          :show-reference-configuration="showReferenceConfiguration"
          :search-results="searchResults"
          :search-loading="searchLoading"
          :search-keyword="searchKeyword"
          :file-tree-error="fileTreeError"
          :workspace-view-warnings="workspaceViewWarnings"
          :user-id="authStore.currentUser?.userId"
          :backend-java-server-ip="opencodeProcessStatus?.backendJavaServerIp"
          @toggle-directory="toggleDirectory"
          @toggle-view-directory="toggleWorkspaceViewDirectory"
          @open-file="openFile"
          @open-view-file="openWorkspaceViewFile"
          @add-file-context="addWorkspaceFileToChatContext"
          @add-view-file-context="addWorkspaceViewFileToChatContext"
          @open-diff="handleOpenDiff"
          @refresh="refreshCurrentWorkspacePanels"
          @changes-refreshed="(payload) => refreshWorkspaceGitDiff({
            reloadOpenFiles: payload?.reloadOpenFiles ?? true,
            paths: payload?.paths,
            files: payload?.files
          })"
          @agent-files-discarded="refreshDiscardedAgentFiles"
          @agent-config-mutated="handleAgentConfigMutation"
          @personal-runtime-reload="handlePersonalRuntimeReload"
          @select-version="handleSelectVersion"
          @load-versions="handleLoadVersions"
          @create-version="handleCreateVersion"
          @open-agent-file="openAgentFile"
          @open-server-workspace-picker="openServerWorkspacePicker"
          @open-reference-configuration="openReferenceConfiguration"
          @search="handleFileSearch"
          @create-entry="handleCreateEntry"
          @delete-entry="handleDeleteEntry"
          @rename-entry="handleRenameEntry"
          @copy-entry="handleCopyEntry"
          @move-entry="handleMoveEntry"
          @upload-files="handleUploadFiles"
          @undo-entry="handleUndoWorkspaceFileOperation"
          @cache-and-navigate="handleCacheAndNavigate"
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
            :workspace-root-path="selectedWorkspace?.rootPath"
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
          :copy-path="activeTabCopyPath"
          :workspace-root-path="selectedWorkspace?.rootPath"
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
          @activate="activateEditorTab"
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
          @cache-and-navigate="(path: string) => handleCacheAndNavigate(path, 'file')"
        >
          <div
            class="relative h-full min-h-0"
            data-testid="file-load-state"
            :data-state="activeTab?.loadState ?? (activeTab ? 'loaded' : 'idle')"
          >
            <CodeEditor
              v-if="!activeTabInitialLoading"
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
            >
              <template #empty-actions>
                <button
                  type="button"
                  class="managed-editor-home-help"
                  data-testid="workbench-home-help"
                  @click="openHelpCenter('getting-started')"
                >
                  <BookOpenText :size="15" />
                  打开用户手册
                </button>
              </template>
            </CodeEditor>
            <div
              v-if="activeTab?.loadState === 'loading'"
              class="pointer-events-none absolute inset-0 z-10 flex items-center justify-center bg-white/85 text-sm text-slate-500"
              role="status"
            >
              正在读取文件…
            </div>
            <div
              v-else-if="activeTab?.loadState === 'error'"
              class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 bg-white text-sm text-slate-500"
            >
              <div class="font-medium text-slate-700">读取文件失败</div>
              <div class="max-w-[520px] px-6 text-center text-xs text-slate-400">{{ activeTab.loadError }}</div>
              <button
                type="button"
                class="rounded border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50"
                aria-label="重试读取文件"
                @click="retryActiveFile"
              >
                重试
              </button>
            </div>
            <div
              v-else-if="activeTab?.loadError"
              class="pointer-events-none absolute inset-x-3 top-3 z-10 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700"
              role="status"
            >
              刷新文件失败，已保留上次内容：{{ activeTab.loadError }}
            </div>
          </div>
        </FigmaEditorArea>
      </main>
    </template>

    <template #chat>
      <div class="managed-chat-panel">
        <FigmaChatPanel
          :panel-visible="rightPanelOpen"
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
          :history-submit-blocked="Boolean(historySwitchingSessionId)"
          :history-running-count="sessionRuntimeState?.runningCount ?? 0"
          :history-question-count="sessionRuntimeState?.questionCount ?? 0"
          :readonly-reason="readonlySessionReason"
          :process-status="opencodeProcessStatus"
          process-status-placement="pet"
          process-required
          :process-loading="opencodeProcessInitialLoading"
          :process-refreshing="opencodeProcessRefreshing"
          :process-initializing="initializeOpencodeProcessMutation.isPending.value"
          :permissions="chatState.permissions"
          :questions="chatState.questions"
          :current-session-id="session?.sessionId"
          :current-session-source-type="session?.sourceType"
          :night-tasks="nightTasks"
          :current-night-task="currentNightTask"
          :night-visible-failure="nightVisibleFailure"
          :night-slots="nightSlots"
          :night-slots-loading="nightSlotsLoading"
          :night-task-submitting="nightTaskSubmitting"
          :night-task-action-pending="nightTaskActionPending"
          :todos="chatState.todos"
          :todo-snapshots-by-user-message-id="chatState.todoSnapshotsByUserMessageId"
          :chat-contexts="chatContextStore.items"
          :chat-context-total-chars="chatContextStore.totalCharCount"
          :chat-context-over-limit="chatContextStore.isOverLimit"
          :chat-context-error="chatContextStore.lastError"
          :selected-model-label="selectedModelLabel"
          :model-picker-disabled="false"
          :agents="agents"
          :workspace-file-candidates="workspaceFileCandidates"
          :workspace-file-candidates-loading="workspaceFileCandidatesLoading"
          :workspace-requirement-references="workspaceRequirementCandidates"
          :workspace-requirement-references-loading="workspaceRequirementCandidatesLoading"
          :agents-loading="agentsLoading"
          :agents-refreshing="agentsRefreshing"
          :agents-error="agentsError"
          :selected-agent="selectedAgent"
          :stop-disabled="!canStopRun"
          :stop-disabled-reason="stopDisabledReason"
          :models="models"
          :providers="providers"
          :selected-model="selectedModel"
          :run-feedbacks="runFeedbacks"
          :feedback-submitting="feedbackSubmitting"
          :run-statuses-by-run-id="chatState.runStatusesByRunId"
          :commands="commands"
          :raw-output-entries="currentRawOutputEntries"
          placeholder="描述测试任务，例如：跑 checkout 模块并分析失败原因"
          @send="(text: string) => handleSend(text)"
          @stop="handleStopRun"
          @retry="handleRetryRun"
          @new-conversation="handleNewConversation"
          @request-night-slots="requestNightExecutionSlots"
          @request-night-tasks="refreshNightExecutionTasks({ reportError: true })"
          @schedule-night="handleScheduleNight"
          @adjust-night-task="handleAdjustNightTask"
          @cancel-night-task="handleCancelNightTask"
          @dismiss-night-task="handleDismissNightTask"
          @open-night-task-session="openNightTaskSession"
          @open-history="refreshHistoryOnOpen"
          @history-search-change="handleHistorySearchChange"
          @load-more-history="loadMoreHistory"
          @initialize-process="beginInitializeOpencodeProcess"
          @open-help="openHelpCenter"
          @open-diff="(path: string) => { if (path) workbench.setSelectedDiffPath(path); centerMode = 'diff'; }"
          @open-file="openFile"
          @preview-context="handlePreviewContext"
          @reply-permission="(requestId: string, decision: 'once' | 'always' | 'reject') => replyPermissionMutation.mutate({ requestId, decision })"
          @reply-question="(requestId: string, answers: unknown[]) => replyQuestionMutation.mutate({ requestId, answers })"
          @reject-question="(requestId: string) => rejectQuestionMutation.mutate(requestId)"
          @select-session="(id: string) => switchSession(id)"
          @change-agent="selectRuntimeAgent"
          @refresh-agents="refreshAgentsCatalog"
          @search-workspace-files="handleWorkspaceFileCandidateSearch"
          @load-workspace-requirements="loadWorkspaceRequirementCandidates"
          @add-workspace-file-context="addWorkspaceFileToChatContext"
          @add-workspace-requirement-context="addWorkspaceRequirementToChatContext"
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
    :server-terminal-enabled="isSuperAdmin"
    :terminal-base-url="apiBaseUrl"
    :create-server-terminal-ticket="createServerTerminalTicket"
    @close="serverWorkspacePickerOpen = false"
    @select-server="selectServerWorkspaceServer"
    @navigate="(path: string) => loadServerWorkspaceDirectories(path)"
    @select="selectServerWorkspaceDirectory"
  />

  <ReferenceConfigurationDialog
    :open="referenceConfigurationOpen"
    :app-id="selectedAppId ?? ''"
    :workspace-id="selectedWorkspace?.workspaceId ?? ''"
    @close="referenceConfigurationOpen = false"
    @saved="refreshWorkspaceViewAfterReferenceSaved"
  />

  <SettingsDialog
    :open="settingsOpen"
    :current-user="authStore.currentUser"
    :route-linux-server-id="routeLinuxServerId"
    :initial-app-id="selectedAppId"
    :initial-menu-key="firstLoginGuideActive ? firstLoginGuideSettingsMenu : undefined"
    :initial-app-tab="firstLoginGuideActive ? firstLoginGuideSettingsTab : undefined"
    @close="settingsOpen = false"
  />

  <HelpCenterDialog
    :open="helpCenterOpen"
    :initial-topic="helpCenterTopic"
    :side-question-available="robotQuestionAvailable"
    :side-question-answer="robotSideQuestion.answer.value"
    :side-question-error="robotSideQuestion.error.value"
    :side-question-loading="robotSideQuestion.loading.value"
    :side-question-progress="robotSideQuestion.progress.value"
    @close="helpCenterOpen = false"
    @ask-pet="handleManualQuestion"
    @start-guide="restartFirstLoginGuide"
  />

  <FirstLoginGuide
    ref="firstLoginGuideRef"
    :user-id="authStore.currentUser?.userId"
    :app-admin="isAppAdmin"
    @prepare="prepareFirstLoginGuide"
    @settings-step="handleFirstLoginGuideSettingsStep"
    @dismiss="dismissFirstLoginGuide"
    @finish="finishFirstLoginGuide"
  />

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

.managed-editor-home-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  margin-top: 16px;
  padding: 0 13px;
  border: 1px solid var(--ta-border-strong, #cbd5e1);
  border-radius: 8px;
  background: #fff;
  color: var(--ta-text, #27384b);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.managed-editor-home-help:hover,
.managed-editor-home-help:focus-visible {
  border-color: var(--ta-accent, #315b75);
  color: var(--ta-accent, #315b75);
  outline: none;
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
