<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, onScopeDispose, provide, ref, shallowRef, toRaw, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/vue-query";
import {
  AgentChat,
  buildComposerPromptParts,
  createInitialAgentChatRuntimeState,
  displayTextFromUserPrompt,
  promptPartsForUserDisplay,
  reduceAgentChatRuntime,
  routeWorkspaceAttachmentsForModel,
  workspaceFileToPromptAttachment,
  type ComposerAttachment
} from "@test-agent/agent-chat";
import {
  BackendApiError,
  createBackendApiClient,
  createSessionShareApiClient,
  type AppSourceProgressConnection,
  type CreateNightExecutionTaskPayload,
  type RawHttpExchange
} from "@test-agent/backend-api";
import { DiffViewer, parseUnifiedPatch } from "@test-agent/diff-viewer";
import { CodeEditor, languageFromPath, type EditorSelectionContext } from "@test-agent/editor";
import {
  subscribeRunEvents,
  subscribeSessionRuntimeState,
  subscribeSessionShareRuntimeState,
  subscribeUserNotifications,
  type RunEventRawMessage
} from "@test-agent/event-stream-client";
import { BookOpenText, Boxes, BrainCircuit, FileWarning, GitCompareArrows, LayoutDashboard, MessageSquare, Monitor, Wrench } from "lucide-vue-next";
import { Setting as ElSetting } from "@element-plus/icons-vue";
import type {
  AgentMessage,
  AppSourceMaterializationPayload,
  AppSourceOpenResult,
  AppSourceOperation,
  AppSourceProgressEvent,
  AppSourceRepositorySummary,
  AppSourceTreeSnapshot,
  AiRunFeedback,
  AiRunFeedbackPayload,
  ApplicationWorkspaceTemplate,
  ApplicationWorkspaceVersion,
  FilePreviewChunk,
  FilePreviewChunkRequest,
  FileSearchResult,
  FileTreeEntry,
  ManagedApplication,
  MemoryUsageView,
  MessagePart,
  PageResponse,
  PromptPart,
  Run,
  RunDiffFile,
  RunEvent,
  RuntimeResourceInfo,
  RuntimeToolInfo,
  ModelInfo,
  NightExecutionScheduleMode,
  NightExecutionSlots,
  NightExecutionTask,
  NightExecutionTaskQueryResponse,
  PersonalWorkspaceGitPullResult,
  ProviderInfo,
  ResendMetadata,
  Session,
  SessionCollaborationShare,
  SessionShareAccess,
  SessionShareRuntimeState,
  SharedSessionListItem,
  UserNotification,
  SessionMessage,
  SessionRuntimeState,
  SessionRuntimeStateSummary,
  OpencodeProcessStartOperation,
  UserOpencodeProcess,
  Workspace,
  WorkspaceBackendServer,
  WorkspaceDirectoryList,
  WorkspaceGitDiffFile,
  WorkspaceGitUpdateBlocker,
  WorkspaceViewEntry,
  WorkspaceViewWarning
} from "@test-agent/shared-types";
import { TerminalPanel } from "@test-agent/terminal";
import { TestRunnerPanel } from "@test-agent/test-runner";
import { Spinner, type Feedback } from "@test-agent/ui-kit";
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
import type { UserNotificationFilter } from "./UserNotificationCenter.vue";
import FirstLoginGuide from "./FirstLoginGuide.vue";
import FigmaFileExplorer from "./FigmaFileExplorer.vue";
import AppSourceDialog from "./AppSourceDialog.vue";
import AppSourcePicker from "./AppSourcePicker.vue";
import {
  appSourceIntentAuthorityMatches,
  appSourceProgressBelongsToObservation,
  appSourcePurposeUpdateAllowed,
  appSourceRecoveryFailureInvalidatesRecent,
  appSourceTreeAuthorityMatches,
  appSourceWorkspaceCapabilities,
  claimAppSourceTerminalOperation,
  diffFileCanWrite,
  ordinaryWorkspaceCanWrite,
  personalWorkspaceRuntimeContext,
  sourceContextFromOpen,
  type AppSourceProgressAuthority,
  type AppSourceIntentAuthority,
  type AppSourceTreeAuthority,
  type AppSourceWorkspaceContext,
  type PersonalWorkspaceRuntimeContext,
  type SelectedWorkspaceKind
} from "./app-source-workspace";
import FigmaEditorArea from "./FigmaEditorArea.vue";
import FileUploadOverlay from "./FileUploadOverlay.vue";
import {
  initialFileUploadOverlayState,
  type FileUploadOverlayState
} from "./fileUploadOverlayState";
import { formatPreviewBytes, progressivePreviewRequired } from "./fileProgressivePreview";
import {
  assertCompleteWorkspaceViewDownload,
  concatWorkspaceDownloadChunks,
  createWorkspaceFileBlob,
  createZipBlob,
  decodeWorkspaceBinaryChunk,
  downloadBlob,
  finalizeWorkspaceDownloadFiles,
  formatDownloadTimestamp,
  type WorkspaceDownloadCandidate
} from "./workspace-download";
import {
  agentConfigMutationReloadTarget,
  agentFileInfo,
  agentTabPath,
  isAgentFilePath,
  shouldReloadPersonalRuntimeCatalog,
  type AgentConfigMutation,
  type AgentFileTabInfo,
  type AgentFileLoadRequest,
  type PublicWorktreeMountRequest
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
import SessionShareDialog from "./SessionShareDialog.vue";
import { sessionCollaborationShareIsActive } from "./session-share-management";
import HelpCenterDialog from "./HelpCenterDialog.vue";
import { buildManualQuestionPrompt, DEFAULT_HELP_TOPIC } from "./help-center";
import { type PreviewMode } from "./WorkbenchFooter.vue";
import {
  normalizePhysicalAbsolutePath,
  workspacePhysicalRootPath
} from "./physical-path";
import OpencodeProcessStartupDialog from "./OpencodeProcessStartupDialog.vue";
import PersonalWorkspacePullDialog, {
  type PersonalWorkspacePullDialogPhase,
  type PersonalWorkspacePullDialogResult,
  type PersonalWorkspacePullDisposeStatus
} from "./PersonalWorkspacePullDialog.vue";
import ReferenceConfigurationDialog from "./ReferenceConfigurationDialog.vue";
import { canShowReferenceConfiguration } from "./reference-configuration-access";
import SettingsDialog from "./settings/SettingsDialog.vue";
import ServerWorkspacePickerDialog from "./ServerWorkspacePickerDialog.vue";
import { readServerWorkspacePickerTabState } from "./server-workspace-picker-tab";
import SystemManagementWrapper from "./SystemManagementWrapper.vue";
import { createSupportAccessShortcut } from "./support-access-shortcut";
import AgentSkillHub from "./AgentSkillHub.vue";
import ToolboxPanel from "./ToolboxPanel.vue";
import MemoryCenter from "./MemoryCenter.vue";
import {
  isRoutedCenterMode,
  routeCenterTransition,
  routedCenterModeFromRouteName,
  transitionImmersivePanels,
  type NonRoutedCenterMode,
  type RoutedCenterMode,
  type WorkbenchCenterMode
} from "./toolbox-navigation";
import WorkbenchFooter from "./WorkbenchFooter.vue";
import { notifyError, notifyFeedback } from "./notify";
import { launchLobehubInNewTab } from "./lobehub-launch";
import { releaseFeatures } from "../release-features";
import { appendLatestRawOutputEntry, prepareRawOutputBody, upsertLatestRawOutputSession } from "./raw-output";
import { formatBeijingDateTimeInput } from "../utils/night-execution-schedule";
import { blobSha256Hex } from "../utils/sha256";
import {
  createRuntimeStateOutageTracker,
  type RuntimeStateFallbackLease
} from "./runtime-state-outage";
import {
  createClientRequestId,
  createConversationRunContextCache,
  startRunWithConversationContext
} from "./conversation-run-context";
import { useBatchTestCaseGeneration } from "./useBatchTestCaseGeneration";
import type { BatchExecutionControls, BatchGenerationRequest } from "./batch-test-case-generation";
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
  looksBinaryContent,
  modelIdOnly,
  modelValue,
  nextCenterModeAfterRunDiff,
  nextCenterModeAfterVcsRefresh,
  notifyOnAttention,
  OPENCODE_HEALTH_REFETCH_INTERVAL_MS,
  PUBLIC_CONFIG_GATE_REFETCH_INTERVAL_MS,
  SESSION_HISTORY_PAGE_SIZE,
  OPENCODE_RUNTIME_CAPABILITY_REFETCH_INTERVAL_MS,
  OPENCODE_VCS_STATUS_REFETCH_INTERVAL_MS,
  opencodeAvailabilityFromHealth,
  opencodeAvailabilityFromProcess,
  opencodeHealthRequestFromProcess,
  opencodeProcessRouteResolution,
  parseCommand,
  promptFromParts,
  resolveRetryDeadline,
  retryCountdownSeconds,
  projectRootInteractionSession,
  reconcileCurrentTurnTodos,
  replaceRootSessionInteractions,
  isSupersededInteractionAsk,
  runEventProjectionMode,
  runEventProjection,
  runEventSubscriptionRunId,
  runEventSubscriptionSessionId,
  runtimeResources,
  runtimeStatus,
  runtimeCatalogRecoveryAllowed,
  platformSessionTitleFromSynchronizedEventPayload,
  sessionTitleEventMatchesCurrentSession,
  sessionTitleFromFirstMessage,
  scheduledRunTiming,
  shouldResetAfterNightTaskClosure,
  shouldRefreshRuntimeCatalogAfterMessageGate,
  syntheticEvent,
  text,
  workspaceRequirementReferences,
  workspaceRequirementStageDirectories,
  workspaceAttachmentTargetPath,
  workspaceLoadIsCurrent,
  type ChatRunDraft,
  type OpenCodeTuiCommandName,
  type OpencodeAvailabilityState,
  type RetryDeadlineMap,
  type WorkspaceRequirementReference
} from "./workbench-utils";

const props = defineProps<{
  sessionShareId?: string;
  initialShareAccess?: SessionShareAccess;
}>();

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const SCM_GMP_PERMISSION_APPLICATION_URL = "https://scm-gmp.sdc.cs.icbc/icbc/gmp/index.jsp#@";
// 只保存当前页面生命周期内的 binding 提示，避免刷新或切换用户后沿用旧服务器。
const routeLinuxServerId = ref("");
const shareMode = computed(() => Boolean(props.sessionShareId?.trim()));
const shareAccess = ref<SessionShareAccess | null>(props.initialShareAccess ?? null);
const shareRuntimeState = ref<SessionShareRuntimeState | null>(null);
const routeLinuxServerResolved = ref(shareMode.value);
const publicWorktreeMountRequest = ref<PublicWorktreeMountRequest | null>(null);
let publicWorktreeMountRevision = 0;
const ordinaryApi = createBackendApiClient({
  baseUrl: apiBaseUrl,
  routeLinuxServerId: () => routeLinuxServerId.value,
  rawExchangeObserver: observeRawHttpExchange
});
const api = props.sessionShareId
  ? createSessionShareApiClient({
      baseUrl: apiBaseUrl,
      shareId: props.sessionShareId,
      routeLinuxServerId: () => routeLinuxServerId.value,
      rawExchangeObserver: observeRawHttpExchange
    })
  : ordinaryApi;
const conversationRunContexts = createConversationRunContextCache((sessionId) => api.getRunContext(sessionId));
provide("api", api);
const queryClient = useQueryClient();
const workbench = useWorkbenchStore();
const authStore = useAuthStore();
const chatContextStore = useChatContextStore();
const route = useRoute();
const router = useRouter();
const OPENCODE_PROCESS_START_OPERATION_POLL_INTERVAL_MS = 500;
const AGENT_CATALOG_REQUEST_TIMEOUT_MS = 8000;
const MANAGED_APPLICATION_MEMBERSHIP_REFETCH_INTERVAL_MS = 30_000;
const RUNTIME_CATALOG_RECOVERY_REFETCH_INTERVAL_MS = 3_000;
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

const isSuperAdmin = computed(() => !shareMode.value && authStore.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const canUseLobehub = computed(() => releaseFeatures.lobehub && isSuperAdmin.value);
const canUseWorkflow = computed(() => releaseFeatures.workflow && isSuperAdmin.value);
const isAppAdmin = computed(() =>
  !shareMode.value && (isSuperAdmin.value || authStore.currentUser?.roles?.includes("APP_ADMIN") === true)
);

const FIRST_LOGIN_GUIDE_STORAGE_VERSION = "v7";
const PERSONAL_PULL_CONFIRM_STORAGE_VERSION = "v1";
const firstLoginGuideActive = ref(true);
const lobehubLaunching = ref(false);

async function openLobehub() {
  if (!canUseLobehub.value || lobehubLaunching.value) return;
  lobehubLaunching.value = true;
  try {
    await launchLobehubInNewTab(api);
  } catch (error) {
    notifyError("无法打开通用问答", error);
  } finally {
    lobehubLaunching.value = false;
  }
}

async function openWorkflowChat() {
  if (!canUseWorkflow.value) return;
  await router.push({ name: "workflow-chat" });
}

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

function personalPullConfirmStorageKey(userId: string) {
  return `test-agent.personal-pull-confirm.${PERSONAL_PULL_CONFIRM_STORAGE_VERSION}:${userId}`;
}

function hasDismissedPersonalPullConfirm(userId: string | undefined) {
  if (!userId) return false;
  try {
    return localStorage.getItem(personalPullConfirmStorageKey(userId)) === "dismissed";
  } catch {
    return false;
  }
}

function dismissPersonalPullConfirm(userId: string | undefined) {
  if (!userId) return;
  try {
    localStorage.setItem(personalPullConfirmStorageKey(userId), "dismissed");
  } catch {
    // 浏览器禁用本地存储时只影响“不再提示”偏好，不阻断实际拉取。
  }
}

watch(
  () => authStore.currentUser?.userId?.trim() || null,
  (userId) => {
    // 登录态尚未加载时先保持抑制，避免进程状态面板抢在引导组件之前闪现。
    if (!userId) return;
    firstLoginGuideActive.value = !shareMode.value && !hasSeenFirstLoginGuide(userId);
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
const downloadingEntryId = ref<string | undefined>();
let workspaceDownloadSequence = 0;
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
// 普通工作台由会话所属人进入，通过分享管理快照补齐协作者姓名；分享模式则使用 access participants。
const ordinarySessionShare = shallowRef<SessionCollaborationShare | null>(null);
const run = shallowRef<Run | null>(null);
const sessionShareDialogOpen = ref(false);
const nightTasks = ref<NightExecutionTask[]>([]);
const nightVisibleFailure = shallowRef<NightExecutionTask | null>(null);
const nightSlots = shallowRef<NightExecutionSlots | null>(null);
const nightSlotsLoading = ref(false);
const nightTaskSubmitting = ref(false);
const nightTaskActionPending = ref<Record<string, boolean>>({});
const recentlyCreatedNightTask = shallowRef<NightExecutionTask | null>(null);
let nightTaskRefreshSequence = 0;
let nightSlotRequestSequence = 0;
let ordinarySessionShareLoadSequence = 0;
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
const centerMode = ref<WorkbenchCenterMode>("editor");
const supportAccessRequested = ref(false);
const supportAccessShortcut = createSupportAccessShortcut();
const centerModeBeforeHub = ref<Exclude<WorkbenchCenterMode, "hub">>("editor");
const centerModeBeforeRoute = ref<NonRoutedCenterMode>("editor");
const hubUpdateCount = ref(0);
let hubUpdateTimer: ReturnType<typeof setInterval> | null = null;
const feedback = ref<Feedback | null>(null);
type RuntimeReloadOutcome = "RELOADED" | "NOT_RUNNING" | "WAITING_IDLE" | "FAILED" | "NO_PENDING";
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
let pendingAgentCatalogReloadId: string | null = null;
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
const runtimeStateRunReconciliations = new Set<string>();
// 同一后端同步信号可能在 SSE 重连后重放；成功同步后不再重复读取，失败时允许后续事件继续补偿。
const refreshedAuthoritativeRunIds = new Set<string>();
const authoritativeRunRefreshes = new Map<string, Promise<boolean>>();
type HistorySwitchRunEventBuffer = {
  switchSeq: number;
  sessionId: string;
  runId?: string;
  events: RunEvent[];
};
let historySwitchRunEventBuffer: HistorySwitchRunEventBuffer | null = null;
const followUpQueue = ref<FollowUpDraft[]>([]);
const retryDeadlines = ref<RetryDeadlineMap>({});
const resendStarting = ref(false);
type ResendEditDraft = {
  sessionId: string;
  sourceRunId: string;
  expectedRemoteMessageId: string;
  sourceMessageId: string;
};
const resendEditDraft = shallowRef<ResendEditDraft | null>(null);
const composerInputValue = ref("");
const ignoredRunIds = ref<Set<string>>(new Set());
const diffContextParts = ref<PromptPart[]>([]);
const editorSelection = ref<EditorSelectionContext | undefined>(undefined);
const bottomMode = ref<"run" | "terminal">("run");
const bottomDrawerOpen = ref(false);
const leftPanelOpen = ref(true);
const rightPanelOpen = ref(true);
const savedLeftPanelOpen = ref(true);
const savedRightPanelOpen = ref(true);
const savedBottomDrawerOpen = ref(false);
let restoringRoutedCenterMode = false;

function clearRunEventSseFeedback() {
  if (feedback.value?.title === RUN_EVENT_SSE_ERROR_TITLE) {
    feedback.value = null;
  }
}

watch(centerMode, (newMode, oldMode) => {
  // 活动栏沉浸式页面以 URI 为权威；后台事件不得切走当前深链接页面。
  if (restoringRoutedCenterMode) {
    restoringRoutedCenterMode = false;
    return;
  }
  const routedMode = routedCenterModeFromRouteName(route.name);
  if (routedMode && newMode !== routedMode) {
    restoringRoutedCenterMode = true;
    centerMode.value = routedMode;
    return;
  }

  const next = transitionImmersivePanels({
    leftOpen: leftPanelOpen.value,
    rightOpen: rightPanelOpen.value,
    bottomOpen: bottomDrawerOpen.value,
    savedLeftOpen: savedLeftPanelOpen.value,
    savedRightOpen: savedRightPanelOpen.value,
    savedBottomOpen: savedBottomDrawerOpen.value
  }, newMode, oldMode);
  leftPanelOpen.value = next.leftOpen;
  rightPanelOpen.value = next.rightOpen;
  bottomDrawerOpen.value = next.bottomOpen;
  savedLeftPanelOpen.value = next.savedLeftOpen;
  savedRightPanelOpen.value = next.savedRightOpen;
  savedBottomDrawerOpen.value = next.savedBottomOpen;
});

watch((): RoutedCenterMode | null => routedCenterModeFromRouteName(route.name), (routeMode) => {
  const next = routeCenterTransition(routeMode, centerMode.value, centerModeBeforeRoute.value);
  centerModeBeforeRoute.value = next.beforeRoute;
  centerMode.value = next.mode;
}, { immediate: true });

async function selectActivityCenterMode(mode: WorkbenchCenterMode) {
  if (isRoutedCenterMode(mode)) {
    if (route.name !== mode) {
      await router.push({ name: mode });
    } else if (centerMode.value !== mode) {
      centerMode.value = mode;
    }
    return;
  }
  if (routedCenterModeFromRouteName(route.name)) {
    await router.push({ name: "workbench" });
  }
  centerMode.value = mode;
}

async function toggleMemories() {
  if (route.name === "memories") {
    await selectActivityCenterMode(centerModeBeforeRoute.value);
    return;
  }
  await selectActivityCenterMode("memories");
}

/** SUPER_ADMIN 可在工作台任意位置三击 Shift，直接进入仍需二次授权的问题排查页。 */
async function openSupportAccessFromShortcut() {
  await selectActivityCenterMode("system");
  supportAccessRequested.value = true;
}

async function toggleToolbox() {
  if (route.name === "toolbox") {
    await selectActivityCenterMode(centerModeBeforeRoute.value);
    return;
  }
  await selectActivityCenterMode("toolbox");
}

async function toggleAgentSkillHub() {
  if (route.name === "hub") {
    await selectActivityCenterMode(centerModeBeforeHub.value);
    return;
  }
  if (centerMode.value !== "hub") {
    centerModeBeforeHub.value = centerMode.value;
  }
  await selectActivityCenterMode("hub");
}

function handleHubChanged(paths: string[]) {
  if (!appSourceCapabilities.value.canPublishApplicationAgentConfig) return;
  handleAgentConfigMutation({
    scope: "WORKSPACE",
    paths,
    workspaceId: selectedWorkspaceIdRef.value
  });
}

async function refreshHubUpdateCount() {
  if (shareMode.value || !authStore.token || !selectedWorkspaceId.value) {
    hubUpdateCount.value = 0;
    return;
  }
  try {
    hubUpdateCount.value = (await api.getAgentSkillHubUpdateCount(selectedWorkspaceId.value)).count;
  } catch {
    // Hub 角标轮询失败不打断编辑器；进入 Hub 后会显示可操作错误。
  }
}

watch([() => authStore.token, selectedWorkspaceId], () => void refreshHubUpdateCount(), { immediate: true });

onMounted(() => {
  hubUpdateTimer = setInterval(() => void refreshHubUpdateCount(), 60_000);
});

onBeforeUnmount(() => {
  if (hubUpdateTimer) clearInterval(hubUpdateTimer);
  hubUpdateTimer = null;
});

const selectedAppId = ref<string | undefined>(undefined);
// 工作区语义不能再从 personalWorkspaceId 是否存在反推；源码快照也使用真实 Workspace。
const selectedWorkspaceKind = ref<SelectedWorkspaceKind>("MANAGED");
const appSourceContext = ref<AppSourceWorkspaceContext | null>(null);
const appSourceCapabilities = computed(() => appSourceWorkspaceCapabilities(selectedWorkspaceKind.value));
const appSourcePickerOpen = ref(false);
const appSourcePickerLoading = ref(false);
const appSourcePickerError = ref<string | null>(null);
const appSourceRepositories = shallowRef<AppSourceRepositorySummary[]>([]);
const selectedAppSourceRepository = ref<AppSourceRepositorySummary | null>(null);
const appSourceDialogOpen = ref(false);
const appSourceBranches = shallowRef<string[]>([]);
const appSourceBranchesLoading = ref(false);
const appSourceBranchesError = ref<string | null>(null);
const appSourceTreeSnapshot = ref<AppSourceTreeSnapshot | null>(null);
const appSourceTreeBranch = ref<string | null>(null);
const appSourceTreeLoading = ref(false);
const appSourceTreeError = ref<string | null>(null);
const appSourceOperation = ref<AppSourceOperation | null>(null);
const appSourceSubmitting = ref(false);
const appSourceRetentionUpdating = ref(false);
const appSourceRetentionError = ref<string | null>(null);
const appSourceMaterializationError = ref<string | null>(null);
const appSourceProgressError = ref<string | null>(null);
let appSourceProgressConnection: AppSourceProgressConnection | null = null;
let appSourceProgressConnectAbortController: AbortController | null = null;
let appSourceProgressReconnectTimer: ReturnType<typeof setTimeout> | null = null;
let appSourceProgressReconnectAttempt = 0;
let appSourceProgressConnectInFlight = false;
let appSourceProgressConnectRequestToken = 0;
let appSourceProgressConnectionEpoch = 0;
let activeAppSourceProgressConnectionEpoch = 0;
let appSourceIntentAuthorityToken = 0;
let appSourceIntentAuthority: AppSourceIntentAuthority | null = null;
let appSourceRepositoryListAuthorityToken = 0;
let appSourceDialogAuthorityToken = 0;
let appSourceTreeAuthorityToken = 0;
let appSourceTreeAuthority: AppSourceTreeAuthority | null = null;
let appSourceTreePendingRequests = 0;
let appSourceProgressAuthorityToken = 0;
let appSourceProgressAuthority: AppSourceProgressAuthority | null = null;
const handledAppSourceTerminalOperations = new Set<string>();
let appSourceRecoveryChecked = false;
let appSourceRecoveryInFlight: Promise<void> | null = null;
// null 表示成员应用目录尚未完成首次加载；加载完成后用于阻止撤权期间的迟到响应重新展示旧工作区。
const visibleManagedApplicationIds = shallowRef<ReadonlySet<string> | null>(null);
// 当前选中版本对应的默认个人工作区 ID，供 GitChangesPanel 调用 publishPersonalWorkspace。
const currentPersonalWorkspaceId = ref<string | undefined>(undefined);
const currentPersonalWorkspaceBranch = ref<string | undefined>(undefined);
const canWriteSelectedWorkspace = computed(() => ordinaryWorkspaceCanWrite(
  selectedWorkspaceKind.value,
  currentPersonalWorkspaceId.value,
  selectedWorkspaceId.value
) || (shareMode.value && shareAccess.value?.canChat === true && shareRuntimeState.value?.active !== false));
const personalPullBlockState = ref<{
  personalWorkspaceId: string;
  files: WorkspaceGitUpdateBlocker[];
} | null>(null);
const currentPersonalPullBlockingFiles = computed(() => {
  const blockState = personalPullBlockState.value;
  if (!blockState || blockState.personalWorkspaceId !== currentPersonalWorkspaceId.value) {
    return [];
  }
  return blockState.files;
});
type WorkspaceUndoOperation =
  | { kind: "delete"; paths: string[]; label: string }
  | { kind: "move"; sourcePath: string; targetPath: string; label: string };
// 撤销历史只属于当前个人 worktree，切换工作区后立即清空，避免跨 worktree 写入。
const workspaceUndoStack = ref<WorkspaceUndoOperation[]>([]);
const workspaceUploadOverlay = ref<FileUploadOverlayState | null>(null);
// 聊天附件复用工作区上传会话，上传成功后保留原始 File 对应的 PromptPart，提交时随 Run 一并发送。
const chatAttachments = ref<ComposerAttachment[]>([]);
// 聊天附件属于平台专用资产，固定放在个人 worktree 的专用目录，不能污染工作区根目录。
const CHAT_ATTACHMENT_DIRECTORY = ".testagent/attachments";
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
// 任务消耗展示：普通任务沿用页面内 chatStartedAt；晚间任务从后端 Run 时间戳恢复，确保页面外执行后仍可展示。
// tokens 从助手消息的 step-finish part 累计（opencode 每轮 step 结束会上报 tokens.total）。
const chatStartedAt = ref<number | null>(null);
const accumulatedTokens = ref(0);
const totalDurationMs = ref(0);
let lastDuration: string | undefined;
let lastTokens = 0;
const nowTick = ref(Date.now());
const scheduledRunTimingHydrationRunIds = new Set<string>();
const settingsOpen = ref(false);
let settingsOpenedFromActivity = false;
watch(() => route.name === "settings", (isSettingsRoute, wasSettingsRoute) => {
  if (isSettingsRoute) {
    settingsOpen.value = true;
  } else if (wasSettingsRoute) {
    settingsOpen.value = false;
    settingsOpenedFromActivity = false;
  }
}, { immediate: true });
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
const fileExplorerRef = ref<InstanceType<typeof FigmaFileExplorer> | null>(null);
const serverWorkspacePickerLoading = ref(false);
const serverWorkspaceServers = shallowRef<WorkspaceBackendServer[]>([]);
const serverWorkspaceDirectory = shallowRef<WorkspaceDirectoryList | null>(null);
const selectedServerWorkspaceServerId = ref<string | undefined>(undefined);
const serverWorkspacePickerNewTabUrl = computed(() => router.resolve({
  name: "workbench",
  query: {
    serverWorkspacePicker: "1"
  }
}).href);
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

/** 捕获阶段识别排查手势，避免子控件 stopPropagation 后顶层收不到 Shift。 */
function onSupportAccessShortcutKeydown(event: KeyboardEvent) {
  if (!isSuperAdmin.value) {
    supportAccessShortcut.reset();
  } else if (supportAccessShortcut.handleKeydown(event)) {
    event.preventDefault();
    void openSupportAccessFromShortcut();
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
  window.addEventListener("keydown", onSupportAccessShortcutKeydown, true);
  window.addEventListener("keydown", onWindowKeydown);
  window.addEventListener("focus", refreshAppSourceAuthorizationOnFocus);
});
onBeforeUnmount(() => {
  invalidateConversationInteraction();
  clearTerminalRunEventSubscriptionHold();
  window.removeEventListener("keydown", onSupportAccessShortcutKeydown, true);
  window.removeEventListener("keydown", onWindowKeydown);
  window.removeEventListener("focus", refreshAppSourceAuthorizationOnFocus);
  teardownAppSourceInteractions();
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
const memoryUsageByRunId = ref<Record<string, MemoryUsageView[]>>({});
const feedbackSubmitting = ref<Record<string, boolean>>({});
const platformMessageIdsByRemoteId = ref<Record<string, string>>({});
const assistantSummaryMessageIdsByRunId = ref<Record<string, string>>({});
function dispatchChat(action: Parameters<typeof reduceAgentChatRuntime>[1]) {
  const next = reduceAgentChatRuntime(chatState.value, action);
  chatState.value = next;
  return next;
}

function rememberAuthoritativeRunRefresh(runId: string): void {
  refreshedAuthoritativeRunIds.add(runId);
  if (refreshedAuthoritativeRunIds.size > 100) {
    const oldest = refreshedAuthoritativeRunIds.values().next();
    if (!oldest.done) {
      refreshedAuthoritativeRunIds.delete(oldest.value);
    }
  }
}

/**
 * 后端只在替代 USER、重发状态和 Session 修订均提交后发出变化信号；这里据此读取一次平台数据库快照，
 * 原位替换该 USER，不切换 Session、不清空时间线，也不改变用户当前滚动位置。
 */
async function refreshAuthoritativeResendUser(
  sessionId: string,
  resend: ResendMetadata
): Promise<boolean> {
  const replacementRunId = resend.replacementRunId;
  if (refreshedAuthoritativeRunIds.has(replacementRunId)) {
    return true;
  }
  const pending = authoritativeRunRefreshes.get(replacementRunId);
  if (pending) {
    return pending;
  }
  const refresh = (async () => {
    try {
      const page = await api.listSessionMessages(sessionId, 1, 100, { refresh: false });
      if (session.value?.sessionId !== sessionId) {
        return false;
      }
      const persistedMessages = dedupeSessionMessages(page.items);
      const authoritative = [...persistedMessages].reverse().find((message) =>
        message.role === "USER" && message.runId === replacementRunId
      );
      if (authoritative) {
        const projected = messagesFromSessionMessages([authoritative])[0];
        if (projected?.role === "user") {
          rememberPersistedMessageIdentities([authoritative]);
          dispatchChat({
            type: "run.resend.user.synchronized",
            resend,
            message: projected
          });
          rememberAuthoritativeRunRefresh(replacementRunId);
          return true;
        }
      }
    } catch {
      // 读取失败不记成功；后端 SSE 或 RunEvent 重连重放时仍可再次同步。
    }
    return false;
  })();
  authoritativeRunRefreshes.set(replacementRunId, refresh);
  try {
    return await refresh;
  } finally {
    if (authoritativeRunRefreshes.get(replacementRunId) === refresh) {
      authoritativeRunRefreshes.delete(replacementRunId);
    }
  }
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
  retryDeadlines.value = {};
  resendStarting.value = false;
  resendEditDraft.value = null;
  composerInputValue.value = "";
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

const shareParticipantNameByUserId = computed(() => new Map([
  ...(ordinarySessionShare.value?.members ?? []).map((member) => [member.userId, member.username] as const),
  ...(shareAccess.value?.participants ?? []).map((participant) => [participant.userId, participant.username] as const)
]));

const chatMessagesForPanel = computed<AgentMessage[]>(() =>
  chatState.value.messages.map((message) => {
    if (message.role === "card") return message;
    const platformMessageId = platformMessageIdForAgentMessage(message);
    if (message.role === "user") {
      const currentRun = run.value;
      const currentResend = currentRun?.resend;
      const belongsToCurrentResend = currentRun && currentResend
        && (message.runId === currentRun.runId || message.runId === currentResend.sourceRunId);
      const resend = belongsToCurrentResend
        ? currentResend
        : message.resend;
      // 兼容旧替代 Run 被执行所属人覆盖的历史数据；共享重发审计中的 requester
      // 是后端确认过的实际发送人，展示和按钮权限都必须优先使用它。
      const senderUserId = resend?.requestedBySharedUser
        ? resend.requesterUserId ?? message.senderUserId
        : message.senderUserId;
      const senderUsername = senderUserId
        ? shareParticipantNameByUserId.value.get(senderUserId)
        : undefined;
      return platformMessageId || resend || senderUsername || senderUserId !== message.senderUserId
        ? {
            ...message,
            platformMessageId,
            resend,
            senderUserId,
            senderUsername: resend?.requestedBySharedUser
              ? resend.requesterUsername ?? senderUsername ?? message.senderUsername
              : senderUsername ?? message.senderUsername,
            senderUnifiedAuthId: resend?.requestedBySharedUser
              ? resend.requesterUnifiedAuthId ?? message.senderUnifiedAuthId
              : message.senderUnifiedAuthId,
            sentBySharedUser: resend?.requestedBySharedUser ? true : message.sentBySharedUser
          }
        : message;
    }
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
  // Agent tab.path 是合成路由；真实绝对路径尚未返回时禁用复制，不再回退内部相对路径。
  return normalizePhysicalAbsolutePath(tab.absolutePath);
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
  // 分享模式的 session/workspace 已由后端精确授权，不依赖被分享人的应用成员关系。
  if (shareMode.value) {
    const fromList = workspaces.value.find((item) => item.workspaceId === selectedWorkspaceId.value);
    if (fromList) return fromList;
    const snapshot = selectedWorkspaceSnapshot.value;
    return snapshot?.workspaceId === selectedWorkspaceId.value ? snapshot : undefined;
  }
  const appId = selectedAppId.value;
  if (!appId || (visibleManagedApplicationIds.value && !visibleManagedApplicationIds.value.has(appId))) {
    return undefined;
  }
  const belongsToSelectedApplication = (workspace: Workspace) =>
    !workspace.appId || workspace.appId === appId;
  const fromList = workspaces.value.find((item) => item.workspaceId === selectedWorkspaceId.value);
  if (fromList && belongsToSelectedApplication(fromList)) return fromList;
  const snapshot = selectedWorkspaceSnapshot.value;
  if (
    snapshot
    && snapshot.workspaceId === selectedWorkspaceId.value
    && belongsToSelectedApplication(snapshot)
  ) {
    return snapshot;
  }
  return undefined;
});
const selectedWorkspacePhysicalRootPath = computed(() => workspacePhysicalRootPath(selectedWorkspace.value));
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
  enabled: () => !shareMode.value,
  retry: false,
  // 成员撤权不删除物理 worktree；前台定期刷新成员目录并在失权后收起旧工作区。
  refetchOnWindowFocus: "always",
  refetchInterval: MANAGED_APPLICATION_MEMBERSHIP_REFETCH_INTERVAL_MS,
  refetchIntervalInBackground: false
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
  enabled: () => !shareMode.value && authStore.isAuthenticated() && routeLinuxServerResolved.value,
  queryFn: () => {
    const query = sessionSearchTrim.value;
    return api.listAllSessions(
      sessionHistoryPage.value,
      SESSION_HISTORY_PAGE_SIZE,
      query || undefined
    );
  }
});

const sharedSessionsQuery = useQuery({
  queryKey: ["sessions", "shared-with-me"],
  enabled: () => !shareMode.value && authStore.isAuthenticated(),
  queryFn: () => ordinaryApi.listSharedSessions(1, 50),
  staleTime: 15_000
});
const sharedSessionItems = computed<SharedSessionListItem[]>(() => sharedSessionsQuery.data.value?.items ?? []);

const USER_NOTIFICATION_PAGE_SIZE = 20;
const notificationFilter = ref<UserNotificationFilter>("UNREAD");
const notificationItems = ref<UserNotification[]>([]);
const notificationPage = ref(1);
const notificationTotal = ref(0);
const notificationUnreadCount = ref(0);
const notificationsLoading = ref(false);
const notificationsLoadingMore = ref(false);
const notificationsError = ref<string | null>(null);
const notificationsHasMore = computed(() => notificationItems.value.length < notificationTotal.value);
let notificationLoadSequence = 0;

/** 通知正文始终从分页接口读取；SSE 仅作为低敏刷新信号和未读数快照。 */
async function refreshUserNotifications() {
  if (shareMode.value || !authStore.token) return;
  const sequence = ++notificationLoadSequence;
  notificationsLoading.value = true;
  notificationsError.value = null;
  try {
    const result = await ordinaryApi.listUserNotifications(
      1,
      USER_NOTIFICATION_PAGE_SIZE,
      notificationFilter.value === "UNREAD"
    );
    if (sequence !== notificationLoadSequence) return;
    notificationItems.value = result.items;
    notificationPage.value = result.page;
    notificationTotal.value = result.total;
    notificationUnreadCount.value = result.unreadCount;
  } catch (error) {
    if (sequence !== notificationLoadSequence) return;
    notificationsError.value = error instanceof BackendApiError
      ? error.message
      : error instanceof Error ? error.message : "通知读取失败";
  } finally {
    if (sequence === notificationLoadSequence) notificationsLoading.value = false;
  }
}

async function loadMoreUserNotifications() {
  if (notificationsLoadingMore.value || !notificationsHasMore.value) return;
  const sequence = notificationLoadSequence;
  notificationsLoadingMore.value = true;
  notificationsError.value = null;
  try {
    const nextPage = notificationPage.value + 1;
    const result = await ordinaryApi.listUserNotifications(
      nextPage,
      USER_NOTIFICATION_PAGE_SIZE,
      notificationFilter.value === "UNREAD"
    );
    if (sequence !== notificationLoadSequence) return;
    const seen = new Set(notificationItems.value.map((item) => item.notificationId));
    notificationItems.value = [
      ...notificationItems.value,
      ...result.items.filter((item) => !seen.has(item.notificationId))
    ];
    notificationPage.value = result.page;
    notificationTotal.value = result.total;
    notificationUnreadCount.value = result.unreadCount;
  } catch (error) {
    notificationsError.value = error instanceof BackendApiError
      ? error.message
      : error instanceof Error ? error.message : "加载更多通知失败";
  } finally {
    notificationsLoadingMore.value = false;
  }
}

function handleNotificationFilter(filter: UserNotificationFilter) {
  notificationFilter.value = filter;
  notificationItems.value = [];
  notificationPage.value = 1;
  notificationTotal.value = 0;
  void refreshUserNotifications();
}

/** 分享通知不提前调用通用已读接口，必须等新标签页鉴权访问成功后由后端统一标记。 */
function handleOpenNotification(notification: UserNotification) {
  if (!notification.actionAvailable || notification.actionType !== "SESSION_SHARE") return;
  openSharedSession(notification.actionTargetId);
}

watch(
  [() => authStore.token, shareMode],
  ([token, isShare], _previous, onCleanup) => {
    // 使旧身份尚未结束的分页请求失效，避免它们在清空后重新写入旧用户快照。
    notificationLoadSequence += 1;
    // 登录身份或工作台模式切换时先清空旧快照，避免短暂展示上一用户的通知。
    notificationItems.value = [];
    notificationPage.value = 1;
    notificationTotal.value = 0;
    notificationUnreadCount.value = 0;
    notificationsLoading.value = false;
    notificationsLoadingMore.value = false;
    notificationsError.value = null;
    if (!token || isShare) {
      return;
    }
    let refreshTimer: ReturnType<typeof setTimeout> | null = null;
    const scheduleRefresh = () => {
      if (refreshTimer) return;
      refreshTimer = setTimeout(() => {
        refreshTimer = null;
        void refreshUserNotifications();
      }, 80);
    };
    const subscription = subscribeUserNotifications({
      baseUrl: apiBaseUrl,
      token,
      onEvent: (update) => {
        notificationUnreadCount.value = update.unreadCount;
        // UPDATED 可能不改变未读数；每个 30 秒快照也必须回源，才能补偿丢失的广播。
        scheduleRefresh();
      }
    });
    void refreshUserNotifications();
    onCleanup(() => {
      subscription.close();
      if (refreshTimer) clearTimeout(refreshTimer);
    });
  },
  { immediate: true }
);

function refreshSharedSessions() {
  if (!shareMode.value && !sharedSessionsQuery.isFetching.value) void sharedSessionsQuery.refetch();
}

function handleSessionShareUpdated(updated: SessionCollaborationShare) {
  ordinarySessionShare.value = updated;
  refreshSharedSessions();
}

watch(
  () => session.value?.sessionId,
  (sessionId) => {
    const sequence = ++ordinarySessionShareLoadSequence;
    ordinarySessionShare.value = null;
    if (shareMode.value || !sessionId) return;
    // 姓名目录是消息展示增强项；读取失败不能阻断所属人打开普通会话。
    void ordinaryApi.getSessionCollaborationShare(sessionId).then((current) => {
      if (sequence === ordinarySessionShareLoadSequence && session.value?.sessionId === sessionId) {
        ordinarySessionShare.value = current;
      }
    }).catch(() => undefined);
  },
  { immediate: true }
);

/** 分享会话使用独立标签页，避免普通工作台与分享工作台复用页面初始化状态。 */
function openSharedSession(shareId: string) {
  const targetUrl = router.resolve(`/s/${encodeURIComponent(shareId)}`).href;
  window.open(targetUrl, "_blank", "noopener,noreferrer");
}

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
      resetRawOutputCache();
      runtimeStateOutages.reset();
      routeLinuxServerId.value = "";
      routeLinuxServerResolved.value = shareMode.value;
      publicWorktreeMountRequest.value = null;
    }
    if (shareMode.value || !token || !subscriptionRouteResolved) {
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
        const adopted = adoptRuntimeStateForCurrentSession(summary, "runtime-state-event");
        if (!adopted) {
          void reconcileCurrentRunAfterRuntimeState(summary);
        }
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

watch(
  [() => authStore.token, () => props.sessionShareId],
  ([token, sessionShareId], _old, onCleanup) => {
    if (!token || !sessionShareId || !shareMode.value) return;
    const subscription = subscribeSessionShareRuntimeState({
      baseUrl: apiBaseUrl,
      token,
      shareId: sessionShareId,
      onEvent: (state, meta) => {
        const previous = shareRuntimeState.value;
        shareRuntimeState.value = state;
        if (shareAccess.value) {
          shareAccess.value = {
            ...shareAccess.value,
            version: state.version,
            canChat: state.canChat,
            expiresAt: state.expiresAt
          };
        }
        readonlySessionReason.value = state.active
          ? (state.canChat ? "" : "当前分享权限为只读，不能修改工作区或发送消息。")
          : shareInvalidReason(state.reason);
        const activeRun = state.activeRun && isRunBusyStatus(state.activeRun.status)
          ? state.activeRun
          : null;
        const sessions: SessionRuntimeState[] = activeRun ? [{
          sessionId: state.sessionId,
          runId: activeRun.runId,
          runStatus: activeRun.status,
          updatedAt: activeRun.updatedAt
        }] : [];
        sessionRuntimeState.value = {
          runningCount: sessions.length,
          questionCount: 0,
          permissionCount: 0,
          sessions,
          generatedAt: state.generatedAt
        };
        if (activeRun) {
          // 分享状态携带完整 Run 归因；只在存在活跃 Run 时直接接管，消失时交给终态对账，不能提前清空。
          run.value = activeRun;
          markConversationRunAdopted(activeRun.runId);
          rememberRunSession(activeRun);
        }
        if (state.active) {
          const adopted = adoptRuntimeStateForCurrentSession(
            sessionRuntimeState.value,
            "session-share-runtime-state"
          );
          if (!adopted) {
            void reconcileCurrentRunAfterRuntimeState(sessionRuntimeState.value);
          }
        } else {
          run.value = null;
        }
        const sessionRevisionChanged = Boolean(
          previous?.sessionUpdatedAt
          && state.sessionUpdatedAt
          && previous.sessionUpdatedAt !== state.sessionUpdatedAt
        );
        if (sessionRevisionChanged && state.active && session.value?.sessionId === state.sessionId) {
          if (activeRun?.resend) {
            // 撤回重发只同步替代 USER；整段 switchSession 会让 A/B 时间线 reset 并强制滚动到底。
            void refreshAuthoritativeResendUser(state.sessionId, activeRun.resend);
          } else if (!activeRun) {
            // compact 没有 active Run 和可原位归并的消息边界，继续按 revision 刷新权威历史。
            void switchSession(state.sessionId, {
              refreshSnapshot: true,
              completionFeedback: {
                kind: "info",
                title: "共享对话已更新",
                description: "已同步其他参与者产生的上下文压缩或消息变更。"
              }
            });
          }
        }
        if (!state.active || previous?.version !== state.version || previous?.canChat !== state.canChat) {
          api.closeWorkspaceFileConnections(state.workspaceId);
        }
        if (state.active && previous?.version !== state.version) {
          void api.getSessionShareAccess().then((latest) => {
            shareAccess.value = latest;
          }).catch(() => undefined);
        }
        logs.value = [...logs.value.slice(-200), `[session-share] ${meta.eventName}`];
      },
      onStatus: (status) => {
        logs.value = [...logs.value.slice(-200), `[session-share] ${status}`];
      }
    });
    onCleanup(() => subscription.close());
  },
  { immediate: true }
);

onMounted(() => {
  const access = shareAccess.value;
  if (!shareMode.value || !access) return;
  routeLinuxServerResolved.value = true;
  readonlySessionReason.value = access.canChat
    ? ""
    : "当前分享权限为只读，不能修改工作区或发送消息。";
  void switchSession(access.sessionId);
});

watch(
  [
    () => typeof route.query.sessionId === "string" ? route.query.sessionId.trim() : "",
    routeLinuxServerResolved
  ],
  ([sessionId, routeResolved]) => {
    if (shareMode.value || !routeResolved || !sessionId || session.value?.sessionId === sessionId) return;
    void switchSession(sessionId);
  },
  { immediate: true }
);

function shareInvalidReason(reason?: string | null): string {
  if (reason === "EXPIRED" || reason === "SESSION_SHARE_EXPIRED") return "该分享链接已过期。";
  if (reason === "REVOKED" || reason === "SESSION_SHARE_REVOKED") return "该分享已被会话所属人取消。";
  if (reason === "REMOVED" || reason === "SESSION_SHARE_MEMBER_REMOVED") return "你已被移出该分享会话。";
  return "当前分享权限已失效。";
}

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
      updatedAt: active.updatedAt,
      sourceType: existing?.sourceType,
      sourceRefId: existing?.sourceRefId,
      resend: active.resend ?? existing?.resend
    };
    run.value = adopted;
    markConversationRunAdopted(adopted.runId);
    rememberRunSession(adopted);
    logs.value = [...logs.value.slice(-200), `[run] recovered ${active.runId} ${active.runStatus} via ${reason}`];
  }
  return true;
}

/**
 * 页面休眠或断网期间可能错过单 Run 的终态事件；用户级运行态快照已不再包含当前 busy Run 时，
 * 读取该 Run 的权威详情并复用既有终态投影，避免隔夜返回后仍被旧 RUNNING 状态锁住发送按钮。
 */
async function reconcileCurrentRunAfterRuntimeState(summary: SessionRuntimeStateSummary): Promise<void> {
  const currentSession = session.value;
  const currentRun = run.value;
  if (
    !currentSession
    || !currentRun
    || currentRun.sessionId !== currentSession.sessionId
    || !isRunBusyStatus(currentRun.status)
    || startRunMutation.isPending.value
    || resendStarting.value
    || ignoredRunIds.value.has(currentRun.runId)
    || runtimeStateRunReconciliations.has(currentRun.runId)
    || summary.sessions.some((item) => item.sessionId === currentSession.sessionId && isRunBusyStatus(item.runStatus))
  ) {
    return;
  }
  const snapshotAt = Date.parse(summary.generatedAt);
  const runUpdatedAt = Date.parse(currentRun.updatedAt);
  if (Number.isFinite(snapshotAt) && Number.isFinite(runUpdatedAt) && snapshotAt < runUpdatedAt) {
    // 旧快照可能与新 Run 启动响应交错到达，不能用较早的用户级快照结束较新的 Run。
    return;
  }

  runtimeStateRunReconciliations.add(currentRun.runId);
  try {
    let detail: Run | null = null;
    let lastError: unknown = null;
    for (let attempt = 0; attempt < 3 && !detail; attempt += 1) {
      if (attempt > 0) {
        await new Promise((resolve) => setTimeout(resolve, attempt * 500));
      }
      if (session.value?.sessionId !== currentSession.sessionId || run.value?.runId !== currentRun.runId) {
        return;
      }
      try {
        detail = await api.getRun(currentRun.runId);
      } catch (error) {
        // runtime-state 未变化时服务端只发送 heartbeat；这里做有限重试，避免一次瞬时 HTTP 失败永久保留发送锁。
        lastError = error;
      }
    }
    if (!detail) {
      throw lastError;
    }
    if (
      session.value?.sessionId !== currentSession.sessionId
      || run.value?.runId !== currentRun.runId
      || isRunBusyStatus(detail.status)
      || sessionRuntimeState.value?.sessions.some(
        (item) => item.sessionId === currentSession.sessionId && isRunBusyStatus(item.runStatus)
      )
    ) {
      return;
    }
    const eventType = detail.status === "SUCCEEDED"
      ? "run.succeeded"
      : detail.status === "FAILED"
        ? "run.failed"
        : detail.status === "CANCELLED"
          ? "run.cancelled"
          : null;
    if (!eventType) {
      return;
    }
    const terminalEvent: RunEvent = {
      eventId: `local-runtime-state-reconcile-${detail.runId}-${detail.updatedAt}`,
      runId: detail.runId,
      seq: Date.now(),
      type: eventType,
      traceId: "trace_runtime_state_reconcile",
      occurredAt: detail.updatedAt,
      payload: { status: detail.status, recoveredFrom: "runtime-state" }
    };
    // 隔夜恢复只校准页面状态，不补发桌面完成通知；其余计时、反馈和目录刷新仍复用统一终态链路。
    handleRunEvent(terminalEvent, currentSession.sessionId, false);
    if (run.value?.runId === detail.runId) {
      run.value = detail;
    }
    logs.value = [...logs.value.slice(-200), `[run] reconciled ${detail.runId} ${detail.status} via runtime-state`];
  } catch (error) {
    console.warn("校准已结束 Run 状态失败", error);
  } finally {
    runtimeStateRunReconciliations.delete(currentRun.runId);
  }
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

const opencodeProcessEnabled = computed(() => !shareMode.value && authStore.isAuthenticated());
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
  [opencodeProcessStatus, () => opencodeProcessQuery.status.value],
  ([process, queryStatus]) => {
    if (shareMode.value) {
      routeLinuxServerResolved.value = true;
      routeLinuxServerId.value = "";
      return;
    }
    const resolution = opencodeProcessRouteResolution(process, queryStatus);
    routeLinuxServerResolved.value = resolution.resolved;
    if (resolution.resolved) {
      routeLinuxServerId.value = resolution.linuxServerId;
    }
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
const opencodeProcessReady = computed(() => shareMode.value || opencodeHealthReady.value);
const batchTestCaseGeneration = useBatchTestCaseGeneration({
  api,
  conversationContexts: conversationRunContexts,
  references: () => workspaceRequirementCandidates.value,
  workspaceId: () => selectedWorkspace.value?.workspaceId,
  agent: () => selectedAgent.value || undefined,
  model: () => selectedModel.value || undefined,
  mode: () => promptMode.value || undefined,
  nightSlots: () => nightSlots.value
});
const batchTestCaseRunning = batchTestCaseGeneration.running;
const batchTestCaseItemStates = batchTestCaseGeneration.itemStates;
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
  // 先取消已经在途的恢复查询，避免它们在后端进入 STARTING 后继续发起强状态探测。
  void queryClient.cancelQueries({ queryKey: ["runtime", "models"] });
  void queryClient.cancelQueries({ queryKey: ["runtime", "providers"] });
  processStartupActionLabel.value =
    opencodeProcessStatus.value?.serviceStatus === "NOT_RUNNING" ? "启动进程" : "分配专属进程";
  processStartupOperation.value = initialProcessStartupOperation(operationId);
  processStartupDialogOpen.value = true;
  startProcessStartupPolling(operationId);
  initializeOpencodeProcessMutation.mutate(operationId);
}

async function confirmProcessInitializationBeforeWorkspaceAction(actionLabel: string): Promise<boolean> {
  if (opencodeProcessReady.value) {
    return true;
  }
  if (initializeOpencodeProcessMutation.isPending.value || processStartupDialogOpen.value) {
    feedback.value = {
      kind: "info",
      title: "TestAgent 进程正在初始化",
      description: `初始化完成后请重新${actionLabel}。`
    };
    return false;
  }
  const processStatus = opencodeProcessStatus.value;
  if (!processStatus) {
    feedback.value = {
      kind: "info",
      title: "正在检查 TestAgent 进程",
      description: `状态确认后请重新${actionLabel}。`
    };
    if (!opencodeProcessQuery.isFetching.value) {
      void opencodeProcessQuery.refetch();
    }
    return false;
  }
  // 只有后端明确允许初始化时才展示确认框；健康检查失败或无可用容器不能伪装成可恢复操作。
  if (processStatus.status !== "NEEDS_INITIALIZATION" || !processStatus.initializable) {
    const description = processStatus.status === "READY"
      ? "TestAgent 进程健康检查尚未通过，请稍后刷新进程状态后重试。"
      : processStatus.message || "当前没有可初始化的 TestAgent 进程，请联系管理员检查容器和服务器状态。";
    if (!opencodeProcessQuery.isFetching.value) {
      void opencodeProcessQuery.refetch();
    }
    await ElMessageBox.alert(description, "TestAgent 进程当前不可用", {
      type: "warning",
      confirmButtonText: "我知道了",
      autofocus: false
    }).catch(() => undefined);
    return false;
  }
  const startupAction = processStatus.serviceStatus === "NOT_RUNNING" ? "启动" : "初始化";
  const confirmed = await ElMessageBox.confirm(
    `${actionLabel}前需要先${startupAction} TestAgent 专属进程。完成后请重新${actionLabel}，是否现在${startupAction}？`,
    `请先${startupAction} TestAgent 进程`,
    {
      type: "info",
      confirmButtonText: `${startupAction}进程`,
      cancelButtonText: "取消",
      autofocus: false
    }
  ).then(() => true).catch(() => false);
  if (!confirmed) {
    return false;
  }
  beginInitializeOpencodeProcess();
  return false;
}

// 拆分就绪条件：不同能力依赖不同条件
// 1. 模型和 Provider：依赖用户 opencode 进程，不依赖 workspace
const authReady = computed(() => authStore.isAuthenticated());
// 分享目录必须显式携带授权中的固定 Workspace，既满足后端精确范围校验，也隔离普通目录缓存。
const runtimeCatalogWorkspaceId = computed(() => shareMode.value
  ? shareAccess.value?.workspaceId?.trim() || undefined
  : undefined
);
const runtimeCatalogRecoveryReady = computed(() =>
  runtimeCatalogRecoveryAllowed(
    authReady.value,
    opencodeProcessReady.value,
    processStartupOperation.value
  ) && (!shareMode.value || Boolean(runtimeCatalogWorkspaceId.value))
);
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

// 模型和 Provider 在进程 READY 后加载；未初始化页面不发起无效 503 轮询。
const modelsQuery = useQuery({
  queryKey: computed(() => ["runtime", "models", runtimeCatalogWorkspaceId.value ?? ""] as const),
  enabled: runtimeCatalogRecoveryReady,
  queryFn: ({ queryKey }) => api.listModels(queryKey[2] || undefined),
  retry: false,
  refetchOnWindowFocus: "always",
  // 服务重启窗口可能先返回空目录或请求失败；仅在目录为空时短轮询，恢复后立即停止。
  refetchInterval: (query) => runtimeCatalogRecoveryRefetchInterval(query.state.data)
});
const providersQuery = useQuery({
  queryKey: computed(() => ["runtime", "providers", runtimeCatalogWorkspaceId.value ?? ""] as const),
  enabled: runtimeCatalogRecoveryReady,
  queryFn: ({ queryKey }) => api.listProviders(queryKey[2] || undefined),
  retry: false,
  refetchOnWindowFocus: "always",
  refetchInterval: (query) => runtimeCatalogRecoveryRefetchInterval(query.state.data)
});

function runtimeCatalogRecoveryRefetchInterval(data: unknown) {
  return Array.isArray(data) && data.length > 0
    ? false
    : RUNTIME_CATALOG_RECOVERY_REFETCH_INTERVAL_MS;
}

// Agent、Command 需要 opencode READY + workspace
const agentsQuery = useQuery({
  queryKey: computed(() => ["runtime", "agents", selectedWorkspaceIdRef.value ?? ""] as const),
  enabled: opencodeCatalogReady,
  queryFn: ({ signal, queryKey }) =>
    api.listAgents(String(queryKey[2]), { signal, timeoutMs: AGENT_CATALOG_REQUEST_TIMEOUT_MS }),
  retry: false
});
const commandsQuery = useQuery({
  queryKey: computed(() => ["runtime", "commands", selectedWorkspaceIdRef.value ?? ""] as const),
  enabled: opencodeCatalogReady,
  // 请求使用 query key 固化的 workspace，避免应用切换后迟到请求读取到新的当前值。
  queryFn: ({ queryKey }) => api.listCommands(String(queryKey[2])),
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

/** 后台配置排空完成后统一刷新 Agent/Command，避免 primary/subagent 继续使用旧实例目录。 */
function refreshRuntimeCatalogAfterMessageGate(
  gate: typeof publicConfigMessageGateQuery.data.value,
  previous?: typeof publicConfigMessageGateQuery.data.value
) {
  if (!shouldRefreshRuntimeCatalogAfterMessageGate(previous, gate, pendingAgentCatalogReloadId)) return;
  pendingAgentCatalogReloadId = null;
  void Promise.all([agentsQuery.refetch(), commandsQuery.refetch()]);
}

watch(publicConfigMessageGateQuery.data, (gate, previous) => {
  refreshRuntimeCatalogAfterMessageGate(gate, previous);
});

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
  // `/experimental/tool` 返回当前模型可用的完整 runtime tool 目录，并非仅 MCP tool。
  tools: mcpToolsData.value.map((tool) => ({
    id: tool.toolId,
    name: tool.name,
    status: tool.source,
    description: tool.description
  })),
  mcp: runtimeMcpItems(mcpStatusData.value),
  plugins: commands.value
    .filter((command) => command.source === "plugin")
    .map((command) => ({
      id: command.commandId,
      name: command.name,
      description: command.description
    })),
  mcpResources: (mcpResourcesData.value ?? []).map((resource) => ({
    id: resource.id,
    name: resource.name,
    status: resource.type,
    description: resource.uri
  }))
}));

/** 左侧 Hub 与顶栏资源盘点复用相同查询，只在用户显式刷新时重新拉取运行态目录。 */
function refreshRuntimeHubCatalog() {
  void Promise.all([mcpStatusQuery.refetch(), mcpToolsQuery.refetch(), mcpResourcesQuery.refetch()]);
}
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
const sessionPermissionAttentionCount = computed(() => {
  const summary = sessionRuntimeState.value;
  if (!summary) return 0;
  return summary.permissionCount
    ?? summary.sessions.filter((item) => item.attention === "PERMISSION").length;
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

const historyList = computed(() => {
  const items = historyItems(run.value, sessionsItems.value, runtimeStatesBySessionId.value);
  const currentShare = ordinarySessionShare.value;
  if (!currentShare) return items;
  const isExpired = !sessionCollaborationShareIsActive(currentShare, new Date(nowTick.value));
  return items.map((item) => {
    if (item.id === currentShare.sessionId) {
      return {
        ...item,
        isShared: true,
        shareStatus: currentShare.status,
        shareExpired: isExpired,
      };
    }
    return item;
  });
});

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
  updateRawOutputSession(
    sessionId,
    appendLatestRawOutputEntry(current, preparedEntry)
  );
}

function clearCurrentRawOutput() {
  const sessionId = session.value?.sessionId;
  if (!sessionId) {
    return;
  }
  updateRawOutputSession(sessionId, []);
}

/** 更新会话级原始输出并同步淘汰已离开最近 20 个会话窗口的 Run 映射。 */
function updateRawOutputSession(sessionId: string, entries: RawOutputEntry[]) {
  const update = upsertLatestRawOutputSession(rawEntriesBySessionId.value, sessionId, entries);
  rawEntriesBySessionId.value = update.entriesBySessionId;
  if (update.evictedSessionIds.length === 0) {
    return;
  }
  const evictedSessionIds = new Set(update.evictedSessionIds);
  rawRunSessionMap.value = Object.fromEntries(
    Object.entries(rawRunSessionMap.value).filter(([, mappedSessionId]) => !evictedSessionIds.has(mappedSessionId))
  );
}

function removeRawOutputSession(sessionId: string) {
  rawEntriesBySessionId.value = Object.fromEntries(
    Object.entries(rawEntriesBySessionId.value).filter(([candidate]) => candidate !== sessionId)
  );
  rawRunSessionMap.value = Object.fromEntries(
    Object.entries(rawRunSessionMap.value).filter(([, mappedSessionId]) => mappedSessionId !== sessionId)
  );
}

function resetRawOutputCache() {
  rawEntriesBySessionId.value = {};
  rawRunSessionMap.value = {};
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
  if (resendStarting.value) {
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
  if (!appSourceRecoveryChecked) {
    void recoverRecentAppSource();
    return;
  }
  const preferredAppId =
    globalRecentAppId.value && apps.some((app) => app.appId === globalRecentAppId.value)
      ? globalRecentAppId.value
      : apps[0]?.appId;
  if (preferredAppId) {
    void handleSelectApp(preferredAppId);
  }
}
watch(
  () => managedApplicationsQuery.data.value,
  (applications, previousApplications) => {
    if (!applications) return;
    const nextVisibleApplicationIds = new Set(applications.map((app) => app.appId));
    visibleManagedApplicationIds.value = nextVisibleApplicationIds;
    const currentAppId = selectedAppId.value;
    if (currentAppId && !nextVisibleApplicationIds.has(currentAppId)) {
      const revokedAppName = previousApplications?.find((app) => app.appId === currentAppId)?.appName ?? currentAppId;
      // 失权响应到达后同时废弃旧的应用选择异步链、文件树、编辑器和运行上下文；
      // 服务器 worktree 与历史 Session 只保留在后端，不再作为当前可操作工作区展示。
      appSelectionSeq += 1;
      selectingAppId = undefined;
      invalidateConversationInteraction();
      teardownAppSourceInteractions();
      resetWorkspaceState();
      selectedWorkspaceId.value = undefined;
      selectedAppId.value = undefined;
      selectedWorkspaceKind.value = "MANAGED";
      appSourceContext.value = null;
      void api.clearRecentAppSource().catch(() => undefined);
      feedback.value = {
        kind: "info",
        title: "应用权限已变更",
        description: `你已不再是 ${revokedAppName} 的成员，原工作区已从工作台隐藏；服务器数据与历史会话仍保留。`
      };
    }
    trySelectDefaultApp();
  },
  { immediate: true }
);
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
    if (selectedWorkspaceKind.value === "MANAGED") void refreshWorkspaceGitDiff();
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
      sessionShareId: props.sessionShareId,
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
        bufferHistorySwitchRunEvent(event, subscribedSessionId);
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
  if (!restoreScheduledRunTiming(r) && shouldHydrateScheduledRunTiming(r)) {
    hydrateScheduledRunTiming(r);
  }
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

/** 手动公共重载已完成同一次保存待办时，立即消费该代次，避免 finally 再触发第二次 dispose。 */
function consumePendingPublicRuntimeReload(
  targetRevision: number | null,
  route: { worktreeId?: string; linuxServerId?: string }
) {
  if (targetRevision === null || targetRevision <= handledReferenceRuntimeReloadRevision) return;
  const pendingTarget = pendingPublicRuntimeReloadTarget;
  if (!pendingTarget?.worktreeId || pendingTarget.worktreeId !== route.worktreeId) return;
  if (pendingTarget.linuxServerId && pendingTarget.linuxServerId !== route.linuxServerId) return;

  handledReferenceRuntimeReloadRevision = targetRevision;
  if (pendingReferenceRuntimeReloadRevision.value === targetRevision) {
    pendingPublicRuntimeReloadTarget = null;
  }
  lastRuntimeReloadError = null;
  runtimeReloadConflictWaitingForIdle.value = false;
  clearRuntimeReloadConflictRetryTimer();
}

/**
 * 手动验证个人 Agent/Skill 配置的运行态重载。
 * 公共配置先切换当前用户的公共 worktree 指针；应用配置先合并 feature 固定提交；随后才 dispose 当前用户。
 */
async function handlePersonalRuntimeReload(payload: {
  scope: "PUBLIC" | "WORKSPACE";
  worktreeId?: string;
  linuxServerId?: string;
  workspaceId?: string;
}) {
  if (!appSourceCapabilities.value.canPublishApplicationAgentConfig && payload.scope === "WORKSPACE") {
    feedback.value = { kind: "info", title: "源码快照不发布应用 Agent", description: "请先返回应用工作区再更新应用 Agent 配置。" };
    return;
  }
  if (runtimeReloadLock.value) return;
  if (payload.scope === "WORKSPACE" && (!selectedWorkspaceIdRef.value || !currentPersonalWorkspaceId.value)) {
    feedback.value = {
      kind: "info",
      title: "个人配置未重载",
      description: "请先进入当前版本的个人工作区，再更新应用个人配置。"
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
  const pendingPublicTargetAtStart = pendingPublicRuntimeReloadTarget;
  const pendingPublicTargetMatches = pendingPublicTargetAtStart !== null
    && pendingPublicTargetAtStart.worktreeId === publicRuntimeRoute?.worktreeId
    && (!pendingPublicTargetAtStart.linuxServerId
      || pendingPublicTargetAtStart.linuxServerId === publicRuntimeRoute?.linuxServerId);
  const pendingPublicReloadRevision = payload.scope === "PUBLIC" && pendingPublicTargetMatches
    ? pendingReferenceRuntimeReloadRevision.value
    : null;

  runtimeReloadLock.value = payload.scope;
  try {
    if (payload.scope === "WORKSPACE") {
      // 应用配置更新必须先复用现有 Git 合并链路；仅 dispose 不会把 feature 固定提交带入个人 worktree。
      const sync = await api.syncApplicationToPersonal(currentPersonalWorkspaceId.value!, { files: [] });
      if (sync.status.toUpperCase() !== "SUCCEEDED") {
        throw new Error("应用 feature 更新未合并；请在 Diff 中处理个人变更或 Git 冲突后重试。");
      }
      agentConfigRevision.value += 1;
      fileExplorerRef.value?.refreshAll();
      refreshCurrentWorkspacePanels();
    }
    if (!opencodeProcessReady.value) {
      feedback.value = {
        kind: "info",
        title: payload.scope === "PUBLIC" ? "公共个人配置将在启动时加载" : "应用个人配置已同步",
        description: payload.scope === "PUBLIC"
          ? "当前 TestAgent 进程未就绪；未提交内容已保留，下次受管启动会自动加载公共个人 worktree。"
          : "应用 feature 更新已合并；TestAgent 进程下次启动时会读取最新配置。"
      };
      return;
    }
    if (payload.scope === "PUBLIC") {
      const result = await api.reloadPublicPersonalAgentRuntime(publicRuntimeRoute!.worktreeId!, publicRuntimeRoute!.linuxServerId!);
      if (!result.reloaded) {
        throw new Error(result.message || "公共个人配置未重新加载");
      }
      // 后端已完成指针切换和 dispose；即使后续目录刷新失败也不能再次释放同一运行态。
      consumePendingPublicRuntimeReload(pendingPublicReloadRevision, publicRuntimeRoute!);
    } else {
      await api.disposeGlobal();
    }
    await Promise.all([agentsQuery.refetch(), commandsQuery.refetch()]);
    feedback.value = {
      kind: "success",
      title: payload.scope === "PUBLIC" ? "公共个人配置已更新" : "应用个人配置已更新",
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

async function reloadReferenceRuntimeIfIdle(options: { quiet?: boolean } = {}): Promise<RuntimeReloadOutcome> {
  const targetRevision = pendingReferenceRuntimeReloadRevision.value;
  if (targetRevision <= handledReferenceRuntimeReloadRevision) return "NO_PENDING";
  if (runtimeReloadLock.value || runtimeReloadConflictWaitingForIdle.value || userRuntimeBusy.value) {
    return "WAITING_IDLE";
  }
  const publicReloadTarget = pendingPublicRuntimeReloadTarget;
  if (!opencodeProcessReady.value || (!publicReloadTarget && !selectedWorkspaceIdRef.value)) {
    // 进程未运行时无需 dispose；下次受管启动会直接读取刚保存的磁盘配置和引用目录环境。
    handledReferenceRuntimeReloadRevision = targetRevision;
    lastRuntimeReloadError = null;
    if (!options.quiet) {
      feedback.value = {
        kind: "info",
        title: pendingRuntimeReloadKind === "reference" ? "引用配置已保存" : "Agent 配置已保存",
        description: "TestAgent 进程下次启动时会加载最新配置。"
      };
    }
    return "NOT_RUNNING";
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
    if (!options.quiet) {
      feedback.value = {
        kind: "info",
        title: pendingRuntimeReloadKind === "reference" ? "引用配置已生效" : "Agent 配置已生效",
        description: "已重新加载当前用户的 TestAgent workspace 实例，无需重启专属进程。"
      };
    }
    return "RELOADED";
  } catch (error) {
    if (error instanceof BackendApiError && error.code === "CONFLICT") {
      // 不消费 revision/公共 worktree 目标；SSE 若尚未观察到新 Run，短延迟后再复核一次。
      lastRuntimeReloadError = null;
      runtimeReloadConflictWaitingForIdle.value = true;
      scheduleRuntimeReloadConflictRetry();
      if (!options.quiet) {
        feedback.value = {
          kind: "info",
          title: pendingRuntimeReloadKind === "reference" ? "引用配置已保存" : "Agent 配置已保存",
          description: "当前用户有刚启动的 Session，结束后会自动重新加载运行态。"
        };
      }
      return "WAITING_IDLE";
    }
    handledReferenceRuntimeReloadRevision = targetRevision;
    if (pendingReferenceRuntimeReloadRevision.value === targetRevision) {
      pendingPublicRuntimeReloadTarget = null;
    }
    lastRuntimeReloadError = error;
    if (!options.quiet) {
      feedback.value = errorFeedback(
        pendingRuntimeReloadKind === "reference"
          ? "引用配置已保存，但运行态重新加载失败"
          : "Agent 配置已保存，但运行态重新加载失败",
        error
      );
    }
    return "FAILED";
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
  // 首次冲突已经提示；后台空闲复核保持静默，避免每秒重复弹出同一 dispose 提示。
  void reloadReferenceRuntimeIfIdle({ quiet: true });
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

/** Agents 树变更后刷新 Git Diff，并让目录定义复用保存动作的个人运行态热加载。 */
function handleAgentConfigMutation(payload: AgentConfigMutation) {
  if (!appSourceCapabilities.value.canPublishApplicationAgentConfig && payload.scope === "WORKSPACE") return;
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
  const reloadTarget = agentConfigMutationReloadTarget(payload);
  if (reloadTarget) {
    // 新建、上传、改名和删除继续复用保存文件的个人热加载与任务忙碌队列。
    void refreshRuntimeCatalogAfterAgentConfigSave(reloadTarget).then((error) => {
      if (error) {
        feedback.value = errorFeedback("Agent 配置已变更，但运行态目录刷新失败", error);
      }
    });
  }
}

const saveMutation = useMutation({
  mutationFn: async (tab: NonNullable<typeof activeTab.value>) => {
    if (isReferenceFilePath(tab.path)) {
      throw new Error("引用文件为只读，不能保存");
    }
    if (isAgentFilePath(tab.path)) {
      const agent = agentFileInfo(tab.path);
      if (agent.scope === "WORKSPACE" && !appSourceCapabilities.value.canPublishApplicationAgentConfig) {
        throw new Error("源码快照不能保存或发布应用 Agent 配置");
      }
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
    if (!canWriteSelectedWorkspace.value) {
      throw new Error("当前 Workspace 不允许写入");
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
          description: (userRuntimeBusy.value || runtimeReloadConflictWaitingForIdle.value)
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
  historySwitchRunEventBuffer = null;
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
      const preparation = status.publicWorktreePreparation;
      const worktreeId = preparation?.worktreeId?.trim();
      const linuxServerId = preparation?.linuxServerId?.trim();
      if (preparation?.ready === true && worktreeId && linuxServerId) {
        // 初始化可能复用原服务器，不能只依赖服务器 prop 变化；显式通知配置树按返回 ID 重新挂载。
        publicWorktreeMountRequest.value = {
          revision: ++publicWorktreeMountRevision,
          worktreeId,
          linuxServerId
        };
      }
      if (status.publicWorktreePreparation?.ready === false) {
        feedback.value = {
          kind: "info",
          title: "TestAgent 进程可用，公共个人 worktree 未准备完成",
          description: status.publicWorktreePreparation.message
        };
      }
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
  isRuntimeBusy(
    run.value?.status,
    chatState.value.status,
    startRunMutation.isPending.value,
    pendingRequestedRunUserMessageId.value !== null
  )
);
const collaborativeSessionActive = computed(() =>
  shareMode.value
  || sessionCollaborationShareIsActive(ordinarySessionShare.value, new Date(nowTick.value))
);
const resendableMessageId = computed(() => {
  if (runtimeBusy.value || resendStarting.value) return undefined;
  const sourceRun = run.value;
  if (!sourceRun || !["SUCCEEDED", "FAILED", "CANCELLED"].includes(sourceRun.status)) return undefined;
  const sourceMessage = [...chatState.value.messages].reverse().find(
    (message): message is Extract<AgentMessage, { role: "user" }> => {
    if (message.role !== "user") return false;
    const scope = chatState.value.messageScopesById[message.messageId ?? message.id];
    return scope?.isChildSession !== true;
    }
  );
  if (!sourceMessage || sourceMessage.runId !== sourceRun.runId || !sourceMessage.remoteMessageId) return undefined;
  const actorUserId = shareMode.value
    ? shareAccess.value?.actorUserId
    : authStore.currentUser?.userId;
  // 所属关系只决定 OpenCode 由谁执行，不能授权所属人改写其他参与者已经发送的问题。
  const sourceSenderUserId = runActorUserId(sourceRun) ?? sourceMessage.senderUserId ?? undefined;
  if (!actorUserId || (sourceSenderUserId ? sourceSenderUserId !== actorUserId : shareMode.value)) return undefined;
  if (shareMode.value && shareAccess.value?.canChat !== true) return undefined;
  if (["WAITING", "REVERTING", "REVERTED"].includes(sourceMessage.resend?.status ?? "")) return undefined;
  return sourceMessage.remoteMessageId;
});
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
function runActorUserId(candidate: Run | null | undefined): string | undefined {
  if (candidate?.resend?.requestedBySharedUser && candidate.resend.requesterUserId) {
    return candidate.resend.requesterUserId;
  }
  return candidate?.messageSenderUserId ?? undefined;
}
const canStopRun = computed(() => Boolean(
  run.value
  && isRunBusyStatus(run.value.status)
  && !cancelRunMutation.isPending.value
  && (!shareMode.value || runActorUserId(run.value) === shareAccess.value?.actorUserId)
));
const stopDisabledReason = computed(() => {
  if (cancelRunMutation.isPending.value) return "正在终止";
  if (!run.value) return "当前没有可终止的运行";
  if (!isRunBusyStatus(run.value.status)) return "当前运行已结束";
  if (shareMode.value && runActorUserId(run.value) !== shareAccess.value?.actorUserId) {
    return "仅会话所属人或本次消息发送人可以停止";
  }
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

/** 使用后端 Run 的权威时间恢复晚间任务计时；普通对话仍保留原有累计计时语义。 */
function restoreScheduledRunTiming(value: Run | null | undefined): boolean {
  const timing = scheduledRunTiming(value);
  if (!value || !timing) {
    return false;
  }
  scheduledRunTimingHydrationRunIds.add(value.runId);
  nowTick.value = Date.now();
  if (timing.completedDurationMs !== undefined) {
    chatStartedAt.value = null;
    totalDurationMs.value = timing.completedDurationMs;
    lastDuration = formatDurationMs(timing.completedDurationMs);
    return true;
  }
  chatStartedAt.value = timing.startedAtMs;
  totalDurationMs.value = 0;
  lastDuration = undefined;
  return true;
}

/** runtime-state 只有摘要；首次晚间 Run 需补读现有 Run 详情，取得真实 createdAt/sourceType。 */
function shouldHydrateScheduledRunTiming(value: Run | null | undefined): value is Run {
  if (
    !value
    || value.sourceType === "SCHEDULED_TASK"
    || session.value?.sourceType !== "SCHEDULED_TASK"
    || historySwitchingSessionId.value === session.value.sessionId
    || scheduledRunTimingHydrationRunIds.has(value.runId)
  ) {
    return false;
  }
  const owningUserMessage = [...chatState.value.messages]
    .reverse()
    .find((message): message is Extract<AgentMessage, { role: "user" }> => (
      message.role === "user" && message.runId === value.runId
    ));
  const hasAnyUserMessage = chatState.value.messages.some((message) => message.role === "user");
  return owningUserMessage?.sourceType === "SCHEDULED_TASK" || !hasAnyUserMessage;
}

function hydrateScheduledRunTiming(value: Run) {
  const expectedSessionId = value.sessionId;
  scheduledRunTimingHydrationRunIds.add(value.runId);
  void api.getRun(value.runId).then((detail) => {
    if (session.value?.sessionId !== expectedSessionId || run.value?.runId !== detail.runId) {
      return;
    }
    run.value = detail;
    rememberRunSession(detail);
  }).catch((error) => {
    scheduledRunTimingHydrationRunIds.delete(value.runId);
    console.warn("恢复晚间任务运行时长失败", error);
  });
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
      return;
    }
    const resolved = resolveRetryDeadline(retryDeadlines.value, status, Date.now());
    if (resolved.deadlines !== retryDeadlines.value) {
      retryDeadlines.value = resolved.deadlines;
    }
  },
  { immediate: true }
);

async function retryLastRun(editedPrompt: string) {
  const currentSession = session.value;
  const sourceRun = run.value;
  const draft = resendEditDraft.value;
  if (!currentSession || !sourceRun || !draft
      || currentSession.sessionId !== draft.sessionId
      || sourceRun.runId !== draft.sourceRunId
      || !sourceRun.status
      || !["SUCCEEDED", "FAILED", "CANCELLED"].includes(sourceRun.status)) {
    feedback.value = { kind: "info", title: "无法撤销重发", description: "仅支持会话最后一条已结束的用户消息。" };
    return;
  }
  resendStarting.value = true;
  const clientRequestId = createClientRequestId();
  try {
    let context = await conversationRunContexts.get(currentSession.sessionId);
    let response;
    try {
      response = await api.createRunResend(currentSession.sessionId, {
        expectedRemoteMessageId: draft.expectedRemoteMessageId,
        expectedRunId: draft.sourceRunId,
        contextToken: context.contextToken,
        clientRequestId,
        editedPrompt
      });
    } catch (error) {
      const code = error && typeof error === "object" ? (error as { code?: unknown }).code : undefined;
      if (code !== "CONVERSATION_CONTEXT_REQUIRED" && code !== "CONVERSATION_CONTEXT_EXPIRED") throw error;
      conversationRunContexts.invalidate(currentSession.sessionId);
      context = await conversationRunContexts.get(currentSession.sessionId);
      response = await api.createRunResend(currentSession.sessionId, {
        expectedRemoteMessageId: draft.expectedRemoteMessageId,
        expectedRunId: draft.sourceRunId,
        contextToken: context.contextToken,
        clientRequestId,
        editedPrompt
      });
    }
    run.value = response.replacementRun;
    dispatchChat({ type: "run.resend.requested", resend: response.resend, editedPrompt });
    // 用户轮次已接管到替代 Run，远端新消息 ID 会在 started 后原位替换旧边界。
    markConversationRunAdopted(response.replacementRun.runId, draft.sourceMessageId);
    rememberRunSession(response.replacementRun);
    clearRunEventSseFeedback();
    chatStartedAt.value = Date.now();
    accumulatedTokens.value = 0;
    resendEditDraft.value = null;
    composerInputValue.value = "";
  } catch (error) {
    feedback.value = errorFeedback("撤销重发失败", error);
  } finally {
    resendStarting.value = false;
  }
}

// follow-up 队列：Run 空闲且有排队 prompt 时自动出队执行
watch(
  [followUpQueue, run, session, () => startRunMutation.isPending.value, opencodeProcessReady, collaborativeSessionActive],
  () => {
    if (
      collaborativeSessionActive.value ||
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
    const draft: ChatRunDraft = {
      prompt: next.prompt,
      parts: next.parts,
      userMessageId: next.userMessageId,
      command: next.command
    };
    requestChatRun(draft.userMessageId);
    startRunMutation.mutate({ input: draft, guard: captureConversationInteraction() });
  }
);

watch(collaborativeSessionActive, (active) => {
  if (active && followUpQueue.value.length > 0) {
    // 分享启用后禁止继续消费此前的本地队列，避免其他参与方看到隐式自动发送。
    followUpQueue.value = [];
  }
});

const updateSessionMutation = useMutation({
  mutationFn: async (input: { sessionId: string; title?: string; pinned?: boolean }) =>
    api.updateSession(input.sessionId, { title: input.title, pinned: input.pinned }),
  onSuccess: (updated, input) => {
    if (session.value?.sessionId === updated.sessionId) {
      session.value = {
        ...session.value,
        ...updated,
        workspaceContext: updated.workspaceContext ?? session.value.workspaceContext
      };
    }
    // PATCH 单会话响应允许缺少 workspaceContext；保留历史列表已有上下文并立即投影置顶结果。
    sessionHistoryItems.value = sessionHistoryItems.value.map((item) =>
      item.sessionId === updated.sessionId
        ? {
            ...item,
            ...updated,
            workspaceContext: updated.workspaceContext ?? item.workspaceContext
          }
        : item
    );
    // 加载过后续页时，置顶会改变分页边界；回到第一页重新对齐服务端权威顺序，避免重复或漏项。
    if (typeof input.pinned === "boolean" && sessionHistoryPage.value !== 1) {
      sessionHistoryPage.value = 1;
    }
    void queryClient.invalidateQueries({ queryKey: ["sessions"] });
  },
  onError: (error) => {
    feedback.value = errorFeedback("更新 Session 失败", error);
  }
});

const historyPinningSessionId = computed(() => {
  const input = updateSessionMutation.variables.value;
  return updateSessionMutation.isPending.value && typeof input?.pinned === "boolean"
    ? input.sessionId
    : null;
});

function handleToggleSessionPinned(sessionId: string, pinned: boolean) {
  if (historyPinningSessionId.value) return;
  updateSessionMutation.mutate({ sessionId, pinned });
}

const deleteSessionMutation = useMutation({
  mutationFn: async (sessionId: string) => api.deleteSession(sessionId),
  onSuccess: (deleted) => {
    conversationRunContexts.invalidate(deleted.sessionId);
    removeRawOutputSession(deleted.sessionId);
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
  workspaceDownloadSequence++;
  downloadingEntryId.value = undefined;
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
  chatAttachments.value = [];
  // 引用弹窗绑定个人工作区；切仓必须立即卸载其轮询与迟到响应上下文。
  referenceConfigurationOpen.value = false;
  workbench.resetWorkspaceView();
}

function rememberPersonalWorkspace(personalWorkspaceId?: string, personalWorkspaceBranch?: string) {
  currentPersonalWorkspaceId.value = personalWorkspaceId;
  currentPersonalWorkspaceBranch.value = personalWorkspaceBranch;
}

function personalWorkspaceContext(
  personalWorkspaceId?: string,
  personalWorkspaceBranch?: string
): PersonalWorkspaceRuntimeContext | undefined {
  if (!personalWorkspaceId) return undefined;
  return {
    personalWorkspaceId,
    personalWorkspaceBranch: personalWorkspaceBranch ?? ""
  };
}

async function resolvePersonalWorkspaceRuntimeContext(
  workspace: Workspace
): Promise<PersonalWorkspaceRuntimeContext | undefined> {
  if (!workspace.versionId) return undefined;
  const personalWorkspaces = await api.listPersonalWorkspaces(workspace.versionId);
  return personalWorkspaceRuntimeContext(workspace.workspaceId, personalWorkspaces);
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
  const leftPhysicalPath = workspacePhysicalRootPath(left);
  const rightPhysicalPath = workspacePhysicalRootPath(right);
  return Boolean(leftPhysicalPath)
    && leftPhysicalPath === rightPhysicalPath
    && (left.linuxServerId ?? "") === (right.linuxServerId ?? "");
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

function openServerWorkspacePicker() {
  return openServerWorkspacePickerAt();
}

async function openServerWorkspacePickerAt(initial?: { serverId?: string; path?: string }) {
  if (!isSuperAdmin.value) return;
  serverWorkspacePickerOpen.value = true;
  serverWorkspacePickerLoading.value = true;
  serverWorkspaceDirectory.value = null;
  try {
    const servers = await api.listWorkspaceBackendServers();
    serverWorkspaceServers.value = servers;
    const preferred = servers.find((server) => server.linuxServerId === initial?.serverId)
      ?? servers.find((server) => server.sameAsAgent)
      ?? servers[0];
    selectedServerWorkspaceServerId.value = preferred?.linuxServerId;
    if (preferred) {
      await loadServerWorkspaceDirectories(initial?.path || preferred.defaultDirectory || undefined, preferred);
    }
  } catch (error) {
    feedback.value = errorFeedback("加载后端服务器失败", error);
  } finally {
    serverWorkspacePickerLoading.value = false;
  }
}

let serverWorkspacePickerRouteHandled = false;
watch(
  [isSuperAdmin, () => route.query.serverWorkspacePicker],
  ([superAdmin, requested]) => {
    if (serverWorkspacePickerRouteHandled || !superAdmin || requested !== "1") return;
    serverWorkspacePickerRouteHandled = true;
    void openServerWorkspacePickerAt(readServerWorkspacePickerTabState());
  },
  { immediate: true }
);

function openReferenceConfiguration() {
  if (!showReferenceConfiguration.value || !selectedAppId.value || !selectedWorkspace.value) return;
  referenceConfigurationOpen.value = true;
}

function newAppSourceOperationId() {
  const randomPart = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID().replace(/-/g, "").slice(0, 14)
    : Math.random().toString(36).slice(2, 16);
  return `aso_${Date.now().toString(36)}_${randomPart}`;
}

function closeAppSourceProgressObservation() {
  if (appSourceProgressReconnectTimer) clearTimeout(appSourceProgressReconnectTimer);
  appSourceProgressReconnectTimer = null;
  appSourceProgressReconnectAttempt = 0;
  appSourceProgressConnectRequestToken += 1;
  appSourceProgressConnectInFlight = false;
  activeAppSourceProgressConnectionEpoch = ++appSourceProgressConnectionEpoch;
  appSourceProgressConnectAbortController?.abort();
  appSourceProgressConnectAbortController = null;
  appSourceProgressConnection?.close();
  appSourceProgressConnection = null;
  appSourceProgressAuthorityToken += 1;
  appSourceProgressAuthority = null;
}

function invalidateAppSourceIntentAuthority() {
  appSourceIntentAuthorityToken += 1;
  appSourceIntentAuthority = null;
}

function beginAppSourceIntent(
  appId: string | undefined,
  repositoryId: string | undefined,
  generation: number | null | undefined,
  workspaceKind: SelectedWorkspaceKind
) {
  const authority: AppSourceIntentAuthority = {
    token: ++appSourceIntentAuthorityToken,
    appId,
    repositoryId,
    generation,
    workspaceKind
  };
  appSourceIntentAuthority = authority;
  return authority;
}

function appSourceIntentIsCurrent(authority: AppSourceIntentAuthority) {
  return appSourceIntentAuthorityMatches(authority, appSourceIntentAuthority);
}

/** managed/source 共用同一 selection authority；新托管意图从入口处就取消所有 pending source。 */
function beginManagedWorkspaceIntent(appId = selectedAppId.value) {
  teardownAppSourceInteractions();
  return beginAppSourceIntent(appId, undefined, null, "MANAGED");
}

function invalidateAppSourceTreeAuthority() {
  appSourceTreeAuthorityToken += 1;
  appSourceTreeAuthority = null;
  appSourceTreeSnapshot.value = null;
  appSourceTreeBranch.value = null;
  appSourceTreeLoading.value = false;
  appSourceTreePendingRequests = 0;
}

/**
 * 源码入口、四步弹窗与进度连接属于同一应用选择；切应用/撤权/返回托管工作区时一次性失效。
 */
function teardownAppSourceInteractions(options: { preserveWorkspaceSelectionIntent?: boolean } = {}) {
  appSourcePickerOpen.value = false;
  appSourcePickerLoading.value = false;
  appSourcePickerError.value = null;
  appSourceDialogOpen.value = false;
  appSourceDialogAuthorityToken += 1;
  appSourceBranches.value = [];
  appSourceBranchesLoading.value = false;
  appSourceBranchesError.value = null;
  appSourceTreeError.value = null;
  appSourceMaterializationError.value = null;
  appSourceRetentionError.value = null;
  appSourceProgressError.value = null;
  appSourceRepositories.value = [];
  selectedAppSourceRepository.value = null;
  appSourceOperation.value = null;
  appSourceSubmitting.value = false;
  appSourceRetentionUpdating.value = false;
  appSourceRecoveryInFlight = null;
  appSourceRepositoryListAuthorityToken += 1;
  if (!options.preserveWorkspaceSelectionIntent) invalidateAppSourceIntentAuthority();
  invalidateAppSourceTreeAuthority();
  closeAppSourceProgressObservation();
}

async function loadAppSourceRepositories() {
  const appId = selectedAppId.value;
  if (!appId) return;
  const workspaceKind = selectedWorkspaceKind.value;
  const authority = ++appSourceRepositoryListAuthorityToken;
  appSourcePickerLoading.value = true;
  appSourcePickerError.value = null;
  try {
    const repositories = await api.listAppSourceRepositories(appId);
    if (
      authority !== appSourceRepositoryListAuthorityToken
      || selectedAppId.value !== appId
      || selectedWorkspaceKind.value !== workspaceKind
    ) return;
    appSourceRepositories.value = repositories;
  } catch (error) {
    if (authority !== appSourceRepositoryListAuthorityToken) return;
    appSourcePickerError.value = errorFeedback("加载应用源码失败", error).description ?? "暂时无法读取源码状态";
  } finally {
    if (authority === appSourceRepositoryListAuthorityToken) appSourcePickerLoading.value = false;
  }
}

function openAppSourcePicker() {
  if (!selectedAppId.value) return;
  appSourcePickerOpen.value = true;
  // 统一菜单展开时已经开始刷新列表；沿用该请求可避免用户紧接着点“管理”产生重复并发。
  if (!appSourcePickerLoading.value) void loadAppSourceRepositories();
}

function closeAppSourcePicker() {
  appSourcePickerOpen.value = false;
  appSourcePickerLoading.value = false;
  appSourceRepositoryListAuthorityToken += 1;
  invalidateAppSourceIntentAuthority();
}

async function activateAppSourceOpenResult(
  result: AppSourceOpenResult,
  authority: AppSourceIntentAuthority
) {
  if (!appSourceIntentIsCurrent(authority)) return false;
  if (!applicationCatalog.value.some((app) => app.appId === result.appId)) {
    throw new Error("当前应用权限已失效");
  }
  const context = sourceContextFromOpen(result);
  if (
    selectedWorkspaceKind.value === "APP_SOURCE"
    && appSourceContext.value?.workspaceId === context.workspaceId
    && appSourceContext.value.generation === context.generation
  ) {
    // focus 校验命中同一服务端 generation 时只更新授权上下文，不重置文件、Session 或终端。
    appSourceContext.value = context;
    return true;
  }
  const workspace = await api.getWorkspace(result.workspaceId);
  if (!appSourceIntentIsCurrent(authority)) return false;
  selectedAppId.value = result.appId;
  const switched = await switchWorkspace(workspace, {
    kind: "APP_SOURCE",
    isCurrent: () => appSourceIntentIsCurrent(authority)
  });
  if (!switched || !appSourceIntentIsCurrent(authority)) return false;
  appSourceContext.value = context;
  rememberPersonalWorkspace(undefined, undefined);
  return true;
}

async function openAppSourceRepository(repository: AppSourceRepositorySummary, generation = repository.generation) {
  const appId = selectedAppId.value;
  if (!appId || generation === null || generation === undefined || !repository.openable) return;
  const authority = beginAppSourceIntent(appId, repository.repositoryId, generation, "APP_SOURCE");
  try {
    const result = await api.openAppSource(appId, repository.repositoryId, generation);
    if (
      !appSourceIntentIsCurrent(authority)
      || result.appId !== appId
      || result.repositoryId !== repository.repositoryId
      || result.generation !== generation
    ) return;
    const activated = await activateAppSourceOpenResult(result, authority);
    if (!activated || !appSourceIntentIsCurrent(authority)) return;
    appSourcePickerOpen.value = false;
    feedback.value = {
      kind: "info",
      title: "已打开源码快照",
      description: `${repository.name} · generation ${result.generation}`
    };
  } catch (error) {
    if (!appSourceIntentIsCurrent(authority)) return;
    feedback.value = errorFeedback("打开源码快照失败", error);
  }
}

async function loadAppSourceBranches(repository = selectedAppSourceRepository.value) {
  const appId = selectedAppId.value;
  if (!appId || !repository) return;
  const dialogAuthority = appSourceDialogAuthorityToken;
  appSourceBranchesLoading.value = true;
  appSourceBranchesError.value = null;
  appSourceTreeError.value = null;
  try {
    const branches = await api.listAppSourceBranches(appId, repository.repositoryId);
    if (
      dialogAuthority !== appSourceDialogAuthorityToken
      || !appSourceDialogOpen.value
      || selectedAppId.value !== appId
      || selectedAppSourceRepository.value?.repositoryId !== repository.repositoryId
    ) return;
    appSourceBranches.value = branches;
    const targetBranch = repository.branch ?? branches[0];
    // 分支已经可用时立即结束分支加载态；目录树是独立慢请求，不能继续锁住可检索的分支控件。
    if (targetBranch) void loadAppSourceTree(targetBranch, "");
  } catch (error) {
    if (dialogAuthority === appSourceDialogAuthorityToken) {
      appSourceBranchesError.value = errorFeedback("加载源码分支失败", error).description ?? "源码分支暂时不可用";
    }
  } finally {
    if (dialogAuthority === appSourceDialogAuthorityToken) appSourceBranchesLoading.value = false;
  }
}

function replaceTreeChildren(
  nodes: AppSourceTreeSnapshot["nodes"],
  path: string,
  children: AppSourceTreeSnapshot["nodes"]
): AppSourceTreeSnapshot["nodes"] {
  return nodes.map((node) => node.path === path
    ? { ...node, children }
    : { ...node, children: replaceTreeChildren(node.children, path, children) });
}

async function loadAppSourceTree(branch: string, path: string) {
  const appId = selectedAppId.value;
  const repository = selectedAppSourceRepository.value;
  if (!appId || !repository) return;
  if (
    !appSourceTreeAuthority
    || appSourceTreeAuthority.appId !== appId
    || appSourceTreeAuthority.repositoryId !== repository.repositoryId
    || appSourceTreeAuthority.branch !== branch
  ) {
    appSourceTreeAuthority = {
      token: ++appSourceTreeAuthorityToken,
      appId,
      repositoryId: repository.repositoryId,
      branch
    };
    appSourceTreeSnapshot.value = null;
    appSourceTreeBranch.value = null;
    appSourceTreePendingRequests = 0;
    appSourceTreeError.value = null;
  }
  // 同一 authority 同时覆盖根树与该分支的懒加载子目录；切 branch/repo/close 后整体失效。
  const requestAuthority = appSourceTreeAuthority;
  appSourceTreePendingRequests += 1;
  appSourceTreeLoading.value = true;
  appSourceTreeError.value = null;
  try {
    const snapshot = await api.getAppSourceTreeSnapshot(appId, repository.repositoryId, branch, path || ".");
    if (!appSourceTreeAuthorityMatches(requestAuthority, appSourceTreeAuthority)) return;
    if (path && appSourceTreeSnapshot.value && appSourceTreeSnapshot.value.targetCommit !== snapshot.targetCommit) {
      // child 漂移会失效整棵树的 authority；其它并发 child 即使随后返回也不能伪装成 root。
      invalidateAppSourceTreeAuthority();
      appSourceTreeError.value = "固定提交已变化，请重新加载当前分支目录树";
      return;
    }
    if (!path || !appSourceTreeSnapshot.value) {
      appSourceTreeSnapshot.value = snapshot;
      appSourceTreeBranch.value = branch;
    } else {
      appSourceTreeSnapshot.value = {
        targetCommit: snapshot.targetCommit,
        nodes: replaceTreeChildren(appSourceTreeSnapshot.value.nodes, path, snapshot.nodes)
      };
    }
  } catch (error) {
    if (appSourceTreeAuthorityMatches(requestAuthority, appSourceTreeAuthority)) {
      appSourceTreeError.value = error instanceof BackendApiError && error.code === "REQUEST_TIMEOUT"
        ? `源码目录读取超时，分支列表仍可检索和切换，请重试当前分支（traceId: ${error.traceId}）`
        : errorFeedback("加载源码目录失败", error).description ?? "源码目录暂时不可用";
    }
  } finally {
    if (appSourceTreeAuthorityMatches(requestAuthority, appSourceTreeAuthority)) {
      appSourceTreePendingRequests = Math.max(0, appSourceTreePendingRequests - 1);
      appSourceTreeLoading.value = appSourceTreePendingRequests > 0;
    }
  }
}

async function applyCompletedAppSourceOperation(operation: AppSourceOperation) {
  if (
    selectedAppId.value !== operation.appId
    || selectedAppSourceRepository.value?.repositoryId !== operation.repositoryId
  ) return;
  const authority = beginAppSourceIntent(
    operation.appId,
    operation.repositoryId,
    operation.targetGeneration,
    "APP_SOURCE"
  );
  try {
    // PARTIAL_FAILED 也可能已包含本机 READY 副本；是否可打开必须由服务端 open 权威判断。
    const result = await api.openAppSource(operation.appId, operation.repositoryId, operation.targetGeneration);
    if (
      !appSourceIntentIsCurrent(authority)
      || result.appId !== operation.appId
      || result.repositoryId !== operation.repositoryId
      || result.generation !== operation.targetGeneration
    ) return;
    await activateAppSourceOpenResult(result, authority);
  } catch (error) {
    if (!appSourceIntentIsCurrent(authority)) return;
    if (appSourceRecoveryFailureInvalidatesRecent(error)) {
      await fallbackToManagedWorkspace("新 generation 在本机不可打开，已返回应用工作区。");
      return;
    }
    appSourceProgressError.value = errorFeedback("打开新源码 generation 失败", error).description
      ?? "服务暂时不可用，可稍后重试打开源码。";
  } finally {
    // 终态刷新只服务于本次 source selection；用户已开始打开其它源码/托管工作区后不得再抢占 intent。
    if (appSourceIntentIsCurrent(authority)) await loadAppSourceRepositories();
  }
}

function currentAppSourceProgressContext() {
  return {
    dialogOpen: appSourceDialogOpen.value,
    appId: selectedAppId.value,
    repositoryId: selectedAppSourceRepository.value?.repositoryId,
    repositoryGeneration: selectedAppSourceRepository.value?.generation ?? null
  };
}

function appSourceProgressIdentity(event: AppSourceProgressEvent) {
  const operation = "operation" in event ? event.operation : undefined;
  return {
    operationId: event.operationId,
    appId: operation?.appId,
    repositoryId: operation?.repositoryId,
    targetGeneration: operation?.targetGeneration
  };
}

function completeAppSourceOperationOnce(operation: AppSourceOperation, authority: AppSourceProgressAuthority) {
  if (
    appSourceProgressAuthority?.token !== authority.token
    || !appSourceProgressBelongsToObservation(authority, currentAppSourceProgressContext(), {
      operationId: operation.operationId,
      appId: operation.appId,
      repositoryId: operation.repositoryId,
      targetGeneration: operation.targetGeneration
    })
    || !claimAppSourceTerminalOperation(handledAppSourceTerminalOperations, operation.operationId)
  ) return;
  closeAppSourceProgressObservation();
  if (["SUCCEEDED", "PARTIAL_FAILED"].includes(operation.status)) {
    void applyCompletedAppSourceOperation(operation);
  } else {
    void loadAppSourceRepositories();
  }
}

function appSourceProgressAuthorityIsCurrent(authority: AppSourceProgressAuthority) {
  return appSourceProgressAuthority?.token === authority.token
    && appSourceProgressBelongsToObservation(authority, currentAppSourceProgressContext(), {
      operationId: authority.operationId,
      appId: authority.appId,
      repositoryId: authority.repositoryId,
      targetGeneration: authority.targetGeneration
    });
}

function appSourceProgressConnectionIsCurrent(
  authority: AppSourceProgressAuthority,
  connectionEpoch: number
) {
  return connectionEpoch === activeAppSourceProgressConnectionEpoch
    && appSourceProgressAuthorityIsCurrent(authority);
}

function scheduleAppSourceProgressReconnect(authority: AppSourceProgressAuthority) {
  if (!appSourceProgressAuthorityIsCurrent(authority) || appSourceProgressReconnectTimer) return;
  const delay = Math.min(250 * (2 ** appSourceProgressReconnectAttempt), 4_000);
  appSourceProgressReconnectAttempt += 1;
  appSourceProgressReconnectTimer = setTimeout(() => {
    appSourceProgressReconnectTimer = null;
    if (!appSourceProgressAuthorityIsCurrent(authority)) return;
    if (appSourceProgressConnectInFlight) {
      scheduleAppSourceProgressReconnect(authority);
      return;
    }
    void connectAppSourceProgressObservation(authority);
  }, delay);
}

function handleAppSourceProgress(
  event: AppSourceProgressEvent,
  authority: AppSourceProgressAuthority,
  connectionEpoch: number
) {
  if (
    !appSourceProgressConnectionIsCurrent(authority, connectionEpoch)
    || !appSourceProgressBelongsToObservation(
      authority,
      currentAppSourceProgressContext(),
      appSourceProgressIdentity(event)
    )
  ) return;
  if (event.type === "failed" && !event.operation) {
    // 当前连接一旦失败就先作废其 epoch；重连退避和新 snapshot 等待期间也不能接收该 socket 的迟到帧。
    activeAppSourceProgressConnectionEpoch = ++appSourceProgressConnectionEpoch;
    appSourceProgressError.value = `${event.errorMessage}${event.traceId ? `（traceId: ${event.traceId}）` : ""}`;
    appSourceProgressConnection?.close();
    appSourceProgressConnection = null;
    scheduleAppSourceProgressReconnect(authority);
    return;
  }
  if (event.operation) {
    appSourceProgressReconnectAttempt = 0;
    appSourceOperation.value = event.operation;
    if (["SUCCEEDED", "PARTIAL_FAILED", "FAILED"].includes(event.operation.status)) {
      completeAppSourceOperationOnce(event.operation, authority);
    }
  }
}

async function connectAppSourceProgressObservation(authority: AppSourceProgressAuthority) {
  if (!appSourceProgressAuthorityIsCurrent(authority)) return;
  if (appSourceProgressConnectInFlight) {
    scheduleAppSourceProgressReconnect(authority);
    return;
  }
  const connectRequestToken = ++appSourceProgressConnectRequestToken;
  let connectionEpoch = 0;
  appSourceProgressConnectInFlight = true;
  try {
    // 首连与每次重连都先串行读取数据库 snapshot，再申请一次性新 ticket。
    const snapshot = await api.getAppSourceOperation(authority.operationId);
    if (
      !appSourceProgressAuthorityIsCurrent(authority)
      || !appSourceProgressBelongsToObservation(authority, currentAppSourceProgressContext(), {
        operationId: snapshot.operationId,
        appId: snapshot.appId,
        repositoryId: snapshot.repositoryId,
        targetGeneration: snapshot.targetGeneration
      })
    ) return;
    appSourceOperation.value = snapshot;
    if (!["PENDING", "RUNNING"].includes(snapshot.status)) {
      completeAppSourceOperationOnce(snapshot, authority);
      return;
    }
    connectionEpoch = ++appSourceProgressConnectionEpoch;
    activeAppSourceProgressConnectionEpoch = connectionEpoch;
    appSourceProgressConnectAbortController?.abort();
    appSourceProgressConnectAbortController = new AbortController();
    appSourceProgressConnection?.close();
    appSourceProgressConnection = null;
    const connection = await api.connectAppSourceProgress(
      authority.operationId,
      (event) => handleAppSourceProgress(event, authority, connectionEpoch),
      { signal: appSourceProgressConnectAbortController.signal }
    );
    if (!appSourceProgressConnectionIsCurrent(authority, connectionEpoch)) {
      connection.close();
      return;
    }
    appSourceProgressConnection = connection;
    appSourceProgressError.value = null;
  } catch (error) {
    if (
      (connectionEpoch === 0
        ? appSourceProgressAuthorityIsCurrent(authority)
        : appSourceProgressConnectionIsCurrent(authority, connectionEpoch))
      && (error as { name?: string }).name !== "AbortError"
    ) {
      appSourceProgressError.value = errorFeedback("恢复源码进度失败", error).description ?? "请关闭并重新打开后重试";
      scheduleAppSourceProgressReconnect(authority);
    }
  } finally {
    if (connectRequestToken === appSourceProgressConnectRequestToken) {
      appSourceProgressConnectInFlight = false;
    }
  }
}

async function observeAppSourceOperation(operation: AppSourceOperation) {
  const repository = selectedAppSourceRepository.value;
  if (
    !appSourceDialogOpen.value
    || !repository
    || selectedAppId.value !== operation.appId
    || repository.repositoryId !== operation.repositoryId
  ) return;
  closeAppSourceProgressObservation();
  const authority: AppSourceProgressAuthority = {
    token: ++appSourceProgressAuthorityToken,
    operationId: operation.operationId,
    appId: operation.appId,
    repositoryId: operation.repositoryId,
    repositoryGeneration: repository.generation ?? null,
    targetGeneration: operation.targetGeneration
  };
  appSourceProgressAuthority = authority;
  appSourceOperation.value = operation;
  appSourceProgressError.value = null;
  if (!["PENDING", "RUNNING"].includes(operation.status)) {
    completeAppSourceOperationOnce(operation, authority);
    return;
  }
  await connectAppSourceProgressObservation(authority);
}

async function openAppSourceDownloadDialog(repository?: AppSourceRepositorySummary) {
  if (!appSourceRepositories.value.some((item) => item.manageable)) {
    feedback.value = { kind: "info", title: "没有可下载的版本库", description: "当前应用没有你可管理的源码版本库。" };
    return;
  }
  closeAppSourcePicker();
  closeAppSourceProgressObservation();
  invalidateAppSourceTreeAuthority();
  appSourceDialogAuthorityToken += 1;
  selectedAppSourceRepository.value = null;
  appSourceDialogOpen.value = true;
  appSourceBranches.value = [];
  appSourceBranchesError.value = null;
  appSourceTreeError.value = null;
  appSourceMaterializationError.value = null;
  appSourceRetentionError.value = null;
  appSourceOperation.value = null;
  appSourceSubmitting.value = false;
  appSourceRetentionUpdating.value = false;
  appSourceProgressError.value = null;
  // 菜单点击某个未下载版本库时直接选中它；顶部“管理”入口仍保持无预选的全量管理页。
  if (repository) selectAppSourceRepository(repository);
}

function selectAppSourceRepository(repository: AppSourceRepositorySummary) {
  if (!appSourceDialogOpen.value || !repository.manageable) return;
  const current = appSourceRepositories.value.find((item) => item.repositoryId === repository.repositoryId);
  if (!current?.manageable) return;
  closeAppSourceProgressObservation();
  invalidateAppSourceTreeAuthority();
  appSourceDialogAuthorityToken += 1;
  selectedAppSourceRepository.value = current;
  appSourceBranches.value = [];
  appSourceBranchesError.value = null;
  appSourceTreeError.value = null;
  appSourceMaterializationError.value = null;
  appSourceRetentionError.value = null;
  appSourceProgressError.value = null;
  appSourceOperation.value = current.latestOperation ?? null;
  appSourceSubmitting.value = false;
  appSourceRetentionUpdating.value = false;
  void loadAppSourceBranches(current);
  if (current.latestOperation && ["PENDING", "RUNNING"].includes(current.latestOperation.status)) {
    void observeAppSourceOperation(current.latestOperation);
  }
}

function closeAppSourceDialog() {
  appSourceDialogOpen.value = false;
  appSourceDialogAuthorityToken += 1;
  invalidateAppSourceTreeAuthority();
  closeAppSourceProgressObservation();
  selectedAppSourceRepository.value = null;
  appSourceBranches.value = [];
  appSourceBranchesError.value = null;
  appSourceTreeError.value = null;
  appSourceMaterializationError.value = null;
  appSourceRetentionError.value = null;
  appSourceOperation.value = null;
  appSourceSubmitting.value = false;
  appSourceRetentionUpdating.value = false;
}

async function updateAppSourceRetention(retentionHours: number) {
  const appId = selectedAppId.value;
  const repository = selectedAppSourceRepository.value;
  const generation = repository?.generation;
  if (!appId || !repository || !generation || appSourceRetentionUpdating.value) return;
  const dialogAuthority = appSourceDialogAuthorityToken;
  appSourceRetentionUpdating.value = true;
  appSourceRetentionError.value = null;
  try {
    const result = await api.updateAppSourceRetention(appId, repository.repositoryId, {
      expectedGeneration: generation,
      retentionHours
    });
    if (
      dialogAuthority !== appSourceDialogAuthorityToken
      || selectedAppId.value !== appId
      || selectedAppSourceRepository.value?.repositoryId !== repository.repositoryId
      || result.repositoryId !== repository.repositoryId
      || result.generation !== generation
    ) return;
    const updatedRepository = { ...repository, expiresAt: result.expiresAt };
    appSourceRepositories.value = appSourceRepositories.value.map((item) =>
      item.repositoryId === repository.repositoryId ? updatedRepository : item);
    selectedAppSourceRepository.value = updatedRepository;
    if (
      appSourceContext.value?.appId === appId
      && appSourceContext.value.repositoryId === repository.repositoryId
      && appSourceContext.value.generation === generation
    ) {
      appSourceContext.value = { ...appSourceContext.value, expiresAt: result.expiresAt };
    }
    feedback.value = {
      kind: "info",
      title: "源码保留期已更新",
      description: `generation ${generation} 将于 ${result.expiresAt} 到期`
    };
  } catch (error) {
    if (dialogAuthority !== appSourceDialogAuthorityToken) return;
    appSourceRetentionError.value = errorFeedback("更新源码保留期失败", error).description ?? "请刷新后重试";
  } finally {
    if (dialogAuthority === appSourceDialogAuthorityToken) appSourceRetentionUpdating.value = false;
  }
}

async function materializeAppSource(payload: Omit<AppSourceMaterializationPayload, "operationId">) {
  const appId = selectedAppId.value;
  const repository = selectedAppSourceRepository.value;
  if (!appId || !repository || appSourceSubmitting.value) return;
  if (!appSourcePurposeUpdateAllowed(repository, payload.purpose)) {
    appSourceMaterializationError.value = "已有团队源码不能降级为个人源码";
    return;
  }
  const visiblePathTypes = new Map<string, "FILE" | "DIRECTORY">();
  const indexTree = (nodes: AppSourceTreeSnapshot["nodes"]) => {
    for (const node of nodes) {
      visiblePathTypes.set(node.path, node.type === "directory" ? "DIRECTORY" : "FILE");
      indexTree(node.children ?? []);
    }
  };
  indexTree(appSourceTreeSnapshot.value?.nodes ?? []);
  const currentTree = appSourceTreeAuthority
    && appSourceTreeAuthority.appId === appId
    && appSourceTreeAuthority.repositoryId === repository.repositoryId
    && appSourceTreeAuthority.branch === payload.branch
    && appSourceTreeBranch.value === payload.branch
    && appSourceTreeSnapshot.value?.targetCommit === payload.expectedTreeCommit
    && payload.selectedPaths.length > 0
    && payload.selectedPaths.every((item) => visiblePathTypes.get(item.path) === item.type);
  if (!currentTree) {
    appSourceMaterializationError.value = "分支、固定提交或目录选择已变化，请返回上一步重新确认。";
    return;
  }
  appSourceMaterializationError.value = null;
  const dialogAuthority = appSourceDialogAuthorityToken;
  appSourceSubmitting.value = true;
  try {
    const operation = await api.materializeAppSource(appId, repository.repositoryId, {
      operationId: newAppSourceOperationId(),
      ...payload
    });
    if (
      dialogAuthority !== appSourceDialogAuthorityToken
      || selectedAppId.value !== appId
      || selectedAppSourceRepository.value?.repositoryId !== repository.repositoryId
    ) return;
    await observeAppSourceOperation(operation);
  } catch (error) {
    if (dialogAuthority !== appSourceDialogAuthorityToken) return;
    appSourceMaterializationError.value = errorFeedback("提交源码任务失败", error).description ?? "请检查选择后重试";
  } finally {
    if (dialogAuthority === appSourceDialogAuthorityToken) appSourceSubmitting.value = false;
  }
}

async function retryAppSourceOperation(operation: AppSourceOperation) {
  if (
    appSourceSubmitting.value
    || !["PARTIAL_FAILED", "FAILED"].includes(operation.status)
    || selectedAppId.value !== operation.appId
    || selectedAppSourceRepository.value?.repositoryId !== operation.repositoryId
  ) return;
  const dialogAuthority = appSourceDialogAuthorityToken;
  appSourceSubmitting.value = true;
  try {
    const retry = await api.retryAppSourceReplicas(operation.appId, operation.repositoryId, {
      operationId: newAppSourceOperationId(),
      expectedGeneration: operation.targetGeneration
    });
    if (
      dialogAuthority !== appSourceDialogAuthorityToken
      || selectedAppId.value !== operation.appId
      || selectedAppSourceRepository.value?.repositoryId !== operation.repositoryId
    ) return;
    await observeAppSourceOperation(retry);
  } catch (error) {
    if (dialogAuthority !== appSourceDialogAuthorityToken) return;
    appSourceProgressError.value = errorFeedback("重试源码副本失败", error).description ?? "请稍后重试";
  } finally {
    if (dialogAuthority === appSourceDialogAuthorityToken) appSourceSubmitting.value = false;
  }
}

async function fallbackToManagedWorkspace(reason?: string) {
  teardownAppSourceInteractions();
  selectedWorkspaceKind.value = "MANAGED";
  appSourceContext.value = null;
  const appId = selectedAppId.value;
  const clearRecent = api.clearRecentAppSource().catch(() => undefined);
  if (appId) await handleSelectApp(appId);
  else trySelectDefaultApp();
  await clearRecent;
  if (reason) feedback.value = { kind: "info", title: "源码工作区已失效", description: reason };
}

async function recoverRecentAppSource(force = false) {
  if (!authStore.token || (!force && appSourceRecoveryChecked)) return;
  if (appSourceRecoveryInFlight) return appSourceRecoveryInFlight;
  const initialKind = selectedWorkspaceKind.value;
  const initialContext = appSourceContext.value;
  const lookupAuthority = beginAppSourceIntent(
    initialContext?.appId ?? selectedAppId.value,
    initialContext?.repositoryId,
    initialContext?.generation ?? null,
    initialKind
  );
  let activeRecoveryAuthority = lookupAuthority;
  let recovery!: Promise<void>;
  recovery = (async () => {
    try {
      const recent = await api.getRecentAppSource();
      if (!appSourceIntentIsCurrent(lookupAuthority)) return;
      if (!recent) {
        if (initialKind === "APP_SOURCE") {
          await fallbackToManagedWorkspace("服务端已不再保留当前源码选择，已返回应用工作区。");
        }
        return;
      }
      if (!applicationCatalog.value.some((app) => app.appId === recent.appId)) {
        await api.clearRecentAppSource();
        return;
      }
      const openAuthority = beginAppSourceIntent(
        recent.appId,
        recent.repositoryId,
        recent.generation,
        "APP_SOURCE"
      );
      activeRecoveryAuthority = openAuthority;
      // recent 只提供逻辑身份；每次刷新/focus 必须重新 open，让服务端校验权限、expiry 与本机副本。
      const opened = await api.openAppSource(recent.appId, recent.repositoryId, recent.generation);
      if (
        !appSourceIntentIsCurrent(openAuthority)
        || opened.appId !== recent.appId
        || opened.repositoryId !== recent.repositoryId
        || opened.generation !== recent.generation
      ) return;
      await activateAppSourceOpenResult(opened, openAuthority);
    } catch (error) {
      if (!appSourceIntentIsCurrent(activeRecoveryAuthority)) return;
      if (appSourceRecoveryFailureInvalidatesRecent(error)) {
        if (selectedWorkspaceKind.value === "APP_SOURCE") {
          await fallbackToManagedWorkspace("源码已过期、权限变化或本机副本不可用。");
        } else {
          try { await api.clearRecentAppSource(); } catch { /* recent 清理失败由下一次 focus 再校验。 */ }
        }
      } else {
        feedback.value = errorFeedback("源码工作区暂时无法校验", error);
      }
    } finally {
      appSourceRecoveryChecked = true;
      if (appSourceRecoveryInFlight === recovery) appSourceRecoveryInFlight = null;
      if (selectedWorkspaceKind.value !== "APP_SOURCE") trySelectDefaultApp();
    }
  })();
  appSourceRecoveryInFlight = recovery;
  return recovery;
}

function refreshAppSourceAuthorizationOnFocus() {
  if (appSourceRecoveryChecked) void recoverRecentAppSource(true);
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
  options: {
    preserveConversationInteraction?: boolean;
    awaitDirectory?: boolean;
    kind?: SelectedWorkspaceKind;
    isCurrent?: () => boolean;
    personalWorkspaceContext?: PersonalWorkspaceRuntimeContext;
  } = {}
) {
  const isCurrent = options.isCurrent ?? (() => true);
  if (!isCurrent()) return false;
  const nextKind = options.kind ?? "MANAGED";
  if (nextKind === "MANAGED" && selectedWorkspaceKind.value === "APP_SOURCE") {
    // 切回托管版本或非应用个人工作区必须清独立 recent；失败也不能继续暴露旧源码能力。
    teardownAppSourceInteractions({ preserveWorkspaceSelectionIntent: true });
    void api.clearRecentAppSource().catch(() => undefined);
    selectedWorkspaceKind.value = "MANAGED";
    appSourceContext.value = null;
  }
  if (!isCurrent()) return false;
  if (nextKind === "APP_SOURCE") {
    // 源码快照没有托管 Git pull 能力；切换边界立即移除旧确认/结果弹窗。
    resetPersonalPullDialog();
  }
  if (!options.preserveConversationInteraction) {
    invalidateConversationInteraction();
  }
  resetWorkspaceState();
  // 个人 worktree 身份和运行态 Workspace 必须在同一个同步切换边界写入，避免目录加载期间误降级为只读。
  rememberPersonalWorkspace(
    options.personalWorkspaceContext?.personalWorkspaceId,
    options.personalWorkspaceContext?.personalWorkspaceBranch
  );
  cacheWorkspace(workspace);
  selectedWorkspaceId.value = workspace.workspaceId;
  selectedWorkspaceSnapshot.value = workspace;
  selectedWorkspaceKind.value = nextKind;
  // 切到运行态 Workspace 后，反查当前 workspace 来自哪个应用版本，驱动两级菜单的高亮项。
  if (nextKind === "MANAGED") syncCurrentVersionFromWorkspace(workspace);
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
  return isCurrent();
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
      version.workspaceRootPath === workspacePhysicalRootPath(workspace)
    );
    if (hit) {
      currentVersionFromWorkspace.value = hit.versionId;
      return;
    }
  }
  // 没有匹配到时不主动清空：可能是用户刚切换应用、版本尚未加载完，避免菜单高亮闪烁。
}

// 切换到某个应用版本：先只读校验当前用户对关联 Git 版本库的访问权限，再通过
// ensureDefaultPersonalWorkspace 确保用户拥有默认个人工作区。同一用户同一版本复用 default 空间，避免重复创建。
async function handleSelectVersion(payload: { template: ApplicationWorkspaceTemplate; version: ApplicationWorkspaceVersion }) {
  if (!appSourceCapabilities.value.canSelectApplicationVersion) {
    feedback.value = { kind: "info", title: "源码快照不能切换应用版本", description: "请先返回应用工作区。" };
    return;
  }
  if (!await confirmProcessInitializationBeforeWorkspaceAction("切换应用版本")) {
    return;
  }
  const selectionAuthority = beginManagedWorkspaceIntent(payload.version.appId ?? selectedAppId.value);
  const selectionIsCurrent = () => appSourceIntentIsCurrent(selectionAuthority);
  try {
    const gitAccess = await api.checkWorkspaceVersionGitAccess(payload.version.versionId);
    if (!selectionIsCurrent()) return;
    if (!gitAccess.accessible) {
      if (gitAccess.reason === "SSH_KEY_MISSING") {
        await ElMessageBox.alert(
          `当前账号尚未配置 Git SSH key，暂时无法访问版本库「${gitAccess.repositoryName}」。请先在“设置 → 个人设置”中配置 SSH key，再重新选择该版本。`,
          "需要配置 Git SSH key",
          { type: "warning", confirmButtonText: "我知道了", autofocus: false }
        ).catch(() => undefined);
      } else {
        await ElMessageBox.confirm(
          `当前账号没有版本库「${gitAccess.repositoryName}」的读取权限。请前往 ${SCM_GMP_PERMISSION_APPLICATION_URL} 申请该版本库权限，权限开通后再重新选择。`,
          "需要申请版本库权限",
          {
            type: "warning",
            confirmButtonText: "前往申请",
            cancelButtonText: "稍后申请",
            autofocus: false
          }
        ).then(() => {
          window.open(SCM_GMP_PERMISSION_APPLICATION_URL, "_blank", "noopener,noreferrer");
        }).catch(() => undefined);
      }
      return;
    }
    invalidateConversationInteraction();
    const defaultPw = await api.ensureDefaultPersonalWorkspace(payload.version.versionId);
    if (!selectionIsCurrent()) return;
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
    if (!selectionIsCurrent()) return;
    const applied = await applyManagedWorkspace(workspace, selectionIsCurrent, {
      successTitle: "已切换应用版本",
      successDescription: `${payload.template.workspaceName} · ${payload.version.version} (个人空间: default)`
    }, personalWorkspaceContext(defaultPw.personalWorkspaceId, defaultPw.personalWorkspaceBranch));
    if (!applied || !selectionIsCurrent()) return;
  } catch (error) {
    if (selectionIsCurrent()) feedback.value = errorFeedback("切换应用版本失败", error);
  }
}

// 统一"记录最近使用 + 切到运行态 Workspace"流程：先调 markRecentManagedWorkspace 让 user→app→workspace
// 持久化到 user_application_workspace_preferences / user_global_workspace_preferences，再切工作台。
// 后端在校验通过后会返回最新的 WorkspaceRuntimeResponse（已回填 appId/versionId/applicationWorkspaceId），
// 前端用这个响应回写 versionId/applicationWorkspaceId 到本次切到的工作区，确保重新登录或换电脑登录时
// 左下角"切换工作空间"按钮能立刻显示当前所在的应用版本与模板，而不必等模板 versions 异步加载完成。
// 非托管工作区（不属于任何应用）的 markRecent 会抛 NOT_FOUND，忽略该错误即可，不阻塞切换。
async function applyManagedWorkspace(
  workspace: Workspace,
  isCurrent: () => boolean,
  feedbackDetail?: { successTitle: string; successDescription: string },
  knownPersonalWorkspaceContext?: PersonalWorkspaceRuntimeContext
) {
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
  // 应用目录刷新可能在 recent 请求期间撤销当前应用；迟到响应不得把已隐藏工作区重新挂回页面。
  if (!isCurrent()) return false;
  const resolvedPersonalWorkspaceContext = knownPersonalWorkspaceContext
    ?? await resolvePersonalWorkspaceRuntimeContext(resolvedWorkspace);
  if (!isCurrent()) return false;
  const switched = await switchWorkspace(resolvedWorkspace, {
    isCurrent,
    personalWorkspaceContext: resolvedPersonalWorkspaceContext
  });
  if (!switched || !isCurrent()) return false;
  if (feedbackDetail) {
    feedback.value = { kind: "info", title: feedbackDetail.successTitle, description: feedbackDetail.successDescription };
  }
  // 注意：原「切到运行态工作区后回查 (userId, appId, workspaceId) 维度的最近 VCS 分支偏好」
  // 逻辑（loadBranchPreferenceOnEnter）已随 footer 的「选择分支」/「记住当前分支」入口下线一起移除；
  // 分支信息仍由 runtimeStatus 从 vcs.status 拉取并展示在右侧 Agent 面板。
  return true;
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
const pullingPersonalWorkspace = ref(false);

type PersonalPullDialogState = {
  open: boolean;
  phase: PersonalWorkspacePullDialogPhase;
  personalWorkspaceId: string | null;
  pullResult: PersonalWorkspaceGitPullResult | null;
  result: PersonalWorkspacePullDialogResult | null;
  errorTitle: string;
  errorDescription: string;
};

const personalPullDialog = ref<PersonalPullDialogState>({
  open: false,
  phase: "CONFIRM",
  personalWorkspaceId: null,
  pullResult: null,
  result: null,
  errorTitle: "",
  errorDescription: ""
});

function resetPersonalPullDialog() {
  personalPullDialog.value = {
    open: false,
    phase: "CONFIRM",
    personalWorkspaceId: null,
    pullResult: null,
    result: null,
    errorTitle: "",
    errorDescription: ""
  };
}

/** 后端返回仓库级阻塞文件；前端只做兼容归一化，不猜测其它用户或 workspace。 */
function personalPullBlockers(error: unknown): WorkspaceGitUpdateBlocker[] {
  if (!(error instanceof BackendApiError) || error.details.reason !== "LOCAL_CHANGES") return [];
  const files = error.details.blockingFiles;
  if (!Array.isArray(files)) return [];
  return files.flatMap((file) => {
    if (!file || typeof file !== "object") return [];
    const candidate = file as Record<string, unknown>;
    if (typeof candidate.path !== "string" || !candidate.path.trim()) return [];
    return [{
      path: candidate.path,
      rawStatus: typeof candidate.rawStatus === "string" ? candidate.rawStatus : undefined,
      applicationWorkspaceId: typeof candidate.applicationWorkspaceId === "string"
        ? candidate.applicationWorkspaceId
        : undefined,
      workspaceName: typeof candidate.workspaceName === "string" ? candidate.workspaceName : undefined,
      directoryPath: typeof candidate.directoryPath === "string" ? candidate.directoryPath : undefined
    }];
  });
}

function personalPullDisposeResult(
  response: PersonalWorkspaceGitPullResult,
  runtimeOutcome: RuntimeReloadOutcome
): Pick<PersonalWorkspacePullDialogResult, "disposeStatus" | "disposeMessage"> {
  if (!response.agentConfigChanged) {
    return {
      disposeStatus: "NOT_REQUIRED",
      disposeMessage: "本次没有更新应用 Agent 文件，无需 dispose。"
    };
  }
  if (response.runtimeReloadStatus === "SCHEDULED") {
    return {
      disposeStatus: "WAITING_IDLE",
      disposeMessage: "应用 Agent 已更新；后台已登记当前用户，Session 空闲后会自动 dispose。"
    };
  }
  if (response.runtimeReloadStatus === "NOT_RUNNING") {
    return {
      disposeStatus: "NOT_RUNNING",
      disposeMessage: "应用 Agent 已更新；当前 TestAgent 进程未运行，下次启动会直接加载。"
    };
  }
  if (response.runtimeReloadStatus === "FAILED") {
    return {
      disposeStatus: "FAILED",
      disposeMessage: "应用 Agent 文件已更新，但后台未能登记运行态重载，请稍后重试应用 Agent 更新。"
    };
  }
  const outcomes: Record<RuntimeReloadOutcome, {
    disposeStatus: PersonalWorkspacePullDisposeStatus;
    disposeMessage: string;
  }> = {
    RELOADED: {
      disposeStatus: "DISPOSED",
      disposeMessage: "应用 Agent 已更新，已 dispose 当前用户的空闲运行态；其他用户不受影响。"
    },
    NOT_RUNNING: {
      disposeStatus: "NOT_RUNNING",
      disposeMessage: "应用 Agent 已更新；当前 TestAgent 进程未运行，无需立即 dispose，下次启动会直接加载。"
    },
    WAITING_IDLE: {
      disposeStatus: "WAITING_IDLE",
      disposeMessage: "应用 Agent 已更新；当前用户仍有运行中的 Session，结束后会自动 dispose。"
    },
    FAILED: {
      disposeStatus: "FAILED",
      disposeMessage: "应用 Agent 文件已更新，但当前用户运行态 dispose 失败，请稍后重试应用 Agent 更新。"
    },
    NO_PENDING: {
      disposeStatus: "DISPOSED",
      disposeMessage: "应用 Agent 已更新，当前用户运行态已由同一刷新流程处理。"
    }
  };
  return outcomes[runtimeOutcome];
}

function closePersonalPullDialog() {
  if (pullingPersonalWorkspace.value) return;
  personalPullDialog.value.open = false;
}

function cancelPersonalPullDialog() {
  if (personalPullDialog.value.phase !== "CONFIRM") return;
  personalPullDialog.value.open = false;
}

function confirmPersonalPull(doNotShowAgain: boolean) {
  const personalWorkspaceId = personalPullDialog.value.personalWorkspaceId;
  if (
    !appSourceCapabilities.value.canUseGitPublication
    || !personalWorkspaceId
    || personalPullDialog.value.phase !== "CONFIRM"
  ) return;
  if (doNotShowAgain) dismissPersonalPullConfirm(authStore.currentUser?.userId);
  personalPullDialog.value.phase = "PULLING";
  void executePersonalWorkspacePull(personalWorkspaceId);
}

/**
 * 远端拉取更新当前 owner 在该应用下的整棵个人 worktree；确认偏好只跳过说明页，
 * 每次仍展示 fetch → merge → 刷新 → 当前用户运行态处理的真实结果。
 */
function handlePullPersonalWorkspace(personalWorkspaceId: string) {
  if (!appSourceCapabilities.value.canUseGitPublication) return;
  if (
    pullingPersonalWorkspace.value
    || personalPullDialog.value.open
    || personalWorkspaceId !== currentPersonalWorkspaceId.value
  ) return;
  const skipConfirm = hasDismissedPersonalPullConfirm(authStore.currentUser?.userId);
  personalPullDialog.value = {
    open: true,
    phase: skipConfirm ? "PULLING" : "CONFIRM",
    personalWorkspaceId,
    pullResult: null,
    result: null,
    errorTitle: "",
    errorDescription: ""
  };
  if (skipConfirm) void executePersonalWorkspacePull(personalWorkspaceId);
}

async function executePersonalWorkspacePull(personalWorkspaceId: string) {
  if (pullingPersonalWorkspace.value) return;
  if (
    personalWorkspaceId !== currentPersonalWorkspaceId.value
    || personalPullDialog.value.personalWorkspaceId !== personalWorkspaceId
  ) {
    personalPullDialog.value.phase = "FAILED";
    personalPullDialog.value.errorTitle = "当前个人工作区已变化";
    personalPullDialog.value.errorDescription = "请关闭弹框，在当前工作区重新点击“拉取远程”。";
    return;
  }
  pullingPersonalWorkspace.value = true;
  try {
    const response = await api.gitPullPersonalWorkspace(personalWorkspaceId);
    personalPullDialog.value.pullResult = response;
    personalPullDialog.value.phase = "FINALIZING";
    if (personalPullBlockState.value?.personalWorkspaceId === personalWorkspaceId) {
      personalPullBlockState.value = null;
    }
    agentConfigRevision.value += 1;
    fileExplorerRef.value?.refreshAll();
    refreshCurrentWorkspacePanels();
    let runtimeOutcome: RuntimeReloadOutcome = "NO_PENDING";
    if (response.runtimeReloadStatus === "SCHEDULED" && response.runtimeReloadId) {
      pendingAgentCatalogReloadId = response.runtimeReloadId;
      // 主动读取一次门禁，兼容后台任务在下一次 5 秒轮询前已经完成的情况。
      void publicConfigMessageGateQuery.refetch().then((result) => {
        refreshRuntimeCatalogAfterMessageGate(result.data);
      });
    }
    // 兼容滚动升级期间的旧后端；新版后端返回 runtimeReloadStatus 后由持久化 rollout 接管。
    if (response.agentConfigChanged && !response.runtimeReloadStatus) {
      pendingRuntimeReloadKind = "agent";
      pendingReferenceRuntimeReloadRevision.value += 1;
      runtimeOutcome = await reloadReferenceRuntimeIfIdle({ quiet: true });
    }
    const dispose = personalPullDisposeResult(response, runtimeOutcome);
    personalPullDialog.value.result = { ...response, ...dispose };
    personalPullDialog.value.phase = "SUCCEEDED";
    feedback.value = {
      kind: "success",
      title: response.updated ? "已更新到远程最新版本" : "当前已是远程最新版本",
      description: response.updated
        ? `已更新 ${response.changedFiles.length} 个文件；${dispose.disposeMessage}`
        : "当前用户的应用 workspace 与应用 Agent 已是远程最新版本。"
    };
  } catch (error) {
    const blockers = personalPullBlockers(error);
    if (blockers.length > 0) {
      personalPullBlockState.value = { personalWorkspaceId, files: blockers };
    }
    fileExplorerRef.value?.refreshChanges();
    refreshCurrentWorkspacePanels();
    const failure = errorFeedback("拉取远程失败", error);
    personalPullDialog.value.phase = "FAILED";
    personalPullDialog.value.errorTitle = failure.title;
    personalPullDialog.value.errorDescription = failure.description ?? "请检查提示后重试。";
    feedback.value = failure;
  } finally {
    pullingPersonalWorkspace.value = false;
  }
}

async function handleCreateVersion(payload: { template: ApplicationWorkspaceTemplate; version: string; branch?: string }) {
  if (!appSourceCapabilities.value.canSelectApplicationVersion) {
    feedback.value = { kind: "info", title: "源码快照不能新增应用版本", description: "请先返回应用工作区。" };
    return;
  }
  const appId = selectedAppId.value;
  if (!appId) {
    feedback.value = { kind: "error", title: "未选择应用", description: "请先选择要新增版本的应用。" };
    return;
  }
  if (!await confirmProcessInitializationBeforeWorkspaceAction("新增应用版本")) {
    return;
  }
  invalidateConversationInteraction();
  const selectionAuthority = beginManagedWorkspaceIntent(appId);
  const selectionIsCurrent = () => appSourceIntentIsCurrent(selectionAuthority);
  creatingVersion.value = true;
  try {
    const response = await api.createWorkspaceVersion(appId, payload.template.workspaceId, {
      version: payload.version,
      branch: payload.branch
    });
    if (!selectionIsCurrent()) return;
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
    if (!selectionIsCurrent()) return;
    if (defaultPw.runtimeWorkspace?.workspaceId) {
      const applied = await applyManagedWorkspace(defaultPw.runtimeWorkspace, selectionIsCurrent, {
        successTitle: "已切换应用版本",
        successDescription: `${payload.template.workspaceName} · ${response.version}`
      }, personalWorkspaceContext(defaultPw.personalWorkspaceId, defaultPw.personalWorkspaceBranch));
      if (!applied || !selectionIsCurrent()) return;
    } else {
      rememberPersonalWorkspace(defaultPw.personalWorkspaceId, defaultPw.personalWorkspaceBranch);
      feedback.value = {
        kind: "info",
        title: "新增版本成功",
        description: `${payload.template.workspaceName} · ${response.version}`
      };
    }
  } catch (error) {
    if (selectionIsCurrent()) feedback.value = errorFeedback("新增版本失败", error);
  } finally {
    creatingVersion.value = false;
  }
}

async function handleSelectApp(appId: string) {
  if (selectingAppId === appId || !applicationCatalog.value.some((app) => app.appId === appId)) {
    return;
  }
  invalidateConversationInteraction();
  const leavingAppSource = selectedWorkspaceKind.value === "APP_SOURCE";
  teardownAppSourceInteractions();
  if (leavingAppSource) {
    void api.clearRecentAppSource().catch(() => undefined);
    selectedWorkspaceKind.value = "MANAGED";
    appSourceContext.value = null;
  }
  const selectionSeq = ++appSelectionSeq;
  selectingAppId = appId;
  // 切换应用时先清空旧 workspace 状态，避免文件树继续展示上一个应用的 workspace 内容
  resetWorkspaceState();
  selectedWorkspaceId.value = undefined;
  selectedAppId.value = appId;
  const workspaceSelectionAuthority = beginAppSourceIntent(appId, undefined, null, "MANAGED");
  const selectionIsCurrent = () =>
    selectionSeq === appSelectionSeq
    && selectedAppId.value === appId
    && applicationCatalog.value.some((app) => app.appId === appId)
    && appSourceIntentIsCurrent(workspaceSelectionAuthority);
  try {
    // 只有当前用户当前应用 recent 能反查到 versionId 时，才加载对应 default 私人 worktree。
    // 无历史时只切应用并保持工作区空态，footer 仍可新增版本或选择私人工作区。
    const pick = await pickDefaultWorkspaceForApp(appId);
    if (!selectionIsCurrent()) {
      return;
    }
    if (pick) {
      const applied = await applyManagedWorkspace(
        pick.workspace,
        selectionIsCurrent,
        undefined,
        personalWorkspaceContext(pick.personalWorkspaceId, pick.personalWorkspaceBranch)
      );
      if (!applied || !selectionIsCurrent()) {
        return;
      }
      return;
    }
    // 应用没有可用 recent/versionId 时保持空态，不回退到普通本机目录选择。
  } catch (error) {
    const currentApp = applicationCatalog.value.find((app) => app.appId === appId);
    if (selectionIsCurrent()) {
      feedback.value = errorFeedback("切换应用失败", error, { appId, appName: currentApp?.appName });
    }
  } finally {
    if (selectionSeq === appSelectionSeq) {
      selectingAppId = undefined;
    }
  }
}

async function selectServerWorkspaceDirectory(payload: { server: WorkspaceBackendServer; path: string }) {
  const selectionAuthority = beginManagedWorkspaceIntent();
  const selectionIsCurrent = () => appSourceIntentIsCurrent(selectionAuthority);
  invalidateConversationInteraction();
  serverWorkspacePickerLoading.value = true;
  try {
    const existing = workspaces.value.find(
      (item) => workspacePhysicalRootPath(item) === payload.path
        && item.linuxServerId === payload.server.linuxServerId
    );
    const workspace =
      existing ??
      (await api.createServerWorkspace(payload.server, {
        name: workspaceNameFromPath(payload.path),
        rootPath: payload.path
      }));
    if (!selectionIsCurrent()) return;
    const switched = await applyManagedWorkspace(workspace, selectionIsCurrent);
    if (!switched || !selectionIsCurrent()) return;
    serverWorkspacePickerOpen.value = false;
    serverWorkspaceDirectory.value = null;
  } catch (error) {
    if (selectionIsCurrent()) feedback.value = errorFeedback("切换服务器 Workspace 失败", error);
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

function progressivePreviewPatch(chunk: FilePreviewChunk) {
  return {
    content: chunk.content,
    savedContent: chunk.content,
    readonly: true as const,
    loadState: "loaded" as const,
    loadError: undefined,
    hasLoadedSnapshot: true,
    progressivePreview: {
      size: chunk.size,
      warningThresholdBytes: chunk.warningThresholdBytes,
      loadedBytes: chunk.nextOffset,
      nextOffset: chunk.nextOffset,
      lastModifiedMillis: chunk.lastModifiedMillis,
      eof: chunk.eof,
      loading: false,
      loadingAll: false,
      loadError: undefined
    }
  };
}

function requireProgressivePreviewChunk(chunk: FilePreviewChunk, expectedOffset: number) {
  if (chunk.offset !== expectedOffset
    || chunk.nextOffset < chunk.offset
    || (!chunk.eof && chunk.nextOffset === chunk.offset)
    || chunk.nextOffset > chunk.size) {
    throw new Error("大文件预览分段响应无效，请重新打开文件");
  }
}

/** 按 tab 自带的稳定路由读取下一段，避免大文件预览退回物理路径或错误 scope。 */
async function readProgressivePreviewChunk(
  tab: EditorTab,
  preview: FilePreviewChunkRequest
): Promise<FilePreviewChunk> {
  if (isAgentFilePath(tab.path)) {
    const info = agentFileInfo(tab.path);
    if (info.scope === "PUBLIC") {
      return api.readPublicAgentFilePreviewChunk(
        info.path,
        preview,
        info.worktreeId,
        info.linuxServerId
      );
    }
    if (!info.workspaceId) {
      throw new Error("应用 Agent 大文件预览缺少工作区路由");
    }
    return api.readWorkspaceAgentFilePreviewChunk(
      info.workspaceId,
      info.path,
      preview,
      info.worktreeId
    );
  }
  if (isReferenceFilePath(tab.path)) {
    const info = referenceFileInfo(tab.path);
    return api.readWorkspaceViewFilePreviewChunk(
      info.workspaceId,
      { kind: "REFERENCE", path: info.referencePath, referenceAlias: info.referenceAlias },
      preview
    );
  }
  const workspace = selectedWorkspace.value;
  if (!workspace) {
    throw new Error("大文件预览缺少当前工作区");
  }
  return api.readFilePreviewChunk(workspace.workspaceId, tab.path, preview);
}

async function startProgressivePreview(
  tabPath: string,
  isCurrent: () => boolean,
  failureTitle: string
) {
  const tab = workbench.tabs.find((item: EditorTab) => item.path === tabPath);
  if (!tab) return;
  try {
    const chunk = await readProgressivePreviewChunk(tab, { offset: 0 });
    requireProgressivePreviewChunk(chunk, 0);
    if (!isCurrent()) return;
    workbench.updateTab(tabPath, progressivePreviewPatch(chunk));
  } catch (error) {
    if (!isCurrent()) return;
    const failure = errorFeedback(failureTitle, error);
    workbench.updateTab(tabPath, {
      loadState: "error",
      loadError: failure.description,
      hasLoadedSnapshot: false,
      progressivePreview: undefined
    });
  }
}

const progressivePreviewLoadSequenceByPath = new Map<string, number>();

/** 加载一段或持续加载到 EOF；持续模式每段让出事件循环，页面仍能刷新进度。 */
async function loadMoreActivePreview(loadAll: boolean) {
  const initial = activeTab.value;
  const state = initial?.progressivePreview;
  if (!initial || !state || state.eof || state.loading) return;
  const tabPath = initial.path;
  const sequence = (progressivePreviewLoadSequenceByPath.get(tabPath) ?? 0) + 1;
  progressivePreviewLoadSequenceByPath.set(tabPath, sequence);
  workbench.updateTab(tabPath, {
    progressivePreview: { ...state, loading: true, loadingAll: loadAll, loadError: undefined }
  });
  try {
    while (true) {
      const current = workbench.tabs.find((tab: EditorTab) => tab.path === tabPath);
      const currentState = current?.progressivePreview;
      if (!current || !currentState || progressivePreviewLoadSequenceByPath.get(tabPath) !== sequence) return;
      const chunk = await readProgressivePreviewChunk(current, {
        offset: currentState.nextOffset,
        expectedSize: currentState.size,
        expectedLastModifiedMillis: currentState.lastModifiedMillis
      });
      requireProgressivePreviewChunk(chunk, currentState.nextOffset);
      const latest = workbench.tabs.find((tab: EditorTab) => tab.path === tabPath);
      const latestState = latest?.progressivePreview;
      if (!latest || !latestState || progressivePreviewLoadSequenceByPath.get(tabPath) !== sequence) return;
      const content = latest.content + chunk.content;
      workbench.updateTab(tabPath, {
        content,
        savedContent: content,
        progressivePreview: {
          ...latestState,
          size: chunk.size,
          warningThresholdBytes: chunk.warningThresholdBytes,
          loadedBytes: chunk.nextOffset,
          nextOffset: chunk.nextOffset,
          lastModifiedMillis: chunk.lastModifiedMillis,
          eof: chunk.eof,
          loading: loadAll && !chunk.eof,
          loadingAll: loadAll && !chunk.eof,
          loadError: undefined
        }
      });
      if (!loadAll || chunk.eof) return;
      await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    }
  } catch (error) {
    const current = workbench.tabs.find((tab: EditorTab) => tab.path === tabPath);
    if (!current?.progressivePreview || progressivePreviewLoadSequenceByPath.get(tabPath) !== sequence) return;
    const failure = errorFeedback("继续加载大文件失败", error);
    workbench.updateTab(tabPath, {
      progressivePreview: {
        ...current.progressivePreview,
        loading: false,
        loadingAll: false,
        loadError: failure.description
      }
    });
  } finally {
    if (progressivePreviewLoadSequenceByPath.get(tabPath) === sequence) {
      progressivePreviewLoadSequenceByPath.delete(tabPath);
    }
  }
}

function progressivePreviewPercent(preview: NonNullable<EditorTab["progressivePreview"]>): number {
  if (preview.size <= 0) return 100;
  return Math.min(100, Math.max(0, Math.round((preview.loadedBytes / preview.size) * 100)));
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
    const file = await api.readFile(workspaceId, path, !canWriteSelectedWorkspace.value);
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
      hasLoadedSnapshot: true,
      progressivePreview: undefined
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
    const largePreview = progressivePreviewRequired(error);
    if (largePreview) {
      await startProgressivePreview(
        path,
        () => workspaceFileReadIsCurrent(workspaceId, path, workspaceGeneration, requestGeneration),
        "读取大文件预览失败"
      );
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
      hasLoadedSnapshot: true,
      progressivePreview: undefined
    });
  } catch (error) {
    if (selectedWorkspaceIdRef.value !== workspace.workspaceId
      || workspaceLoadGeneration !== workspaceGeneration
      || latestWorkspaceFileReadByPath.get(tabPath) !== requestGeneration) return;
    const largePreview = progressivePreviewRequired(error);
    if (largePreview) {
      await startProgressivePreview(
        tabPath,
        () => selectedWorkspaceIdRef.value === workspace.workspaceId
          && workspaceLoadGeneration === workspaceGeneration
          && latestWorkspaceFileReadByPath.get(tabPath) === requestGeneration
          && workbench.tabs.some((tab: EditorTab) => tab.path === tabPath),
        "读取引用大文件预览失败"
      );
      return;
    }
    const failure = errorFeedback("读取引用文件失败", error);
    workbench.updateTab(tabPath, referenceReadFailurePatch(hadLoadedCache, failure.description));
  }
}

function isWorkspaceViewEntry(entry: FileTreeEntry): entry is WorkspaceViewEntry {
  const candidate = entry as Partial<WorkspaceViewEntry>;
  return typeof candidate.id === "string"
    && Boolean(candidate.locator)
    && typeof candidate.source === "string";
}

function relativeDownloadPath(path: string, rootPath: string): string {
  const normalizedPath = path.replaceAll("\\", "/").replace(/^\/+|\/+$/g, "");
  const normalizedRoot = rootPath.replaceAll("\\", "/").replace(/^\/+|\/+$/g, "");
  if (normalizedRoot && normalizedPath.startsWith(`${normalizedRoot}/`)) {
    return normalizedPath.slice(normalizedRoot.length + 1);
  }
  if (normalizedRoot && normalizedPath === normalizedRoot) {
    return normalizedPath.split("/").at(-1) ?? normalizedPath;
  }
  return normalizedPath || path.split(/[\\/]+/).filter(Boolean).at(-1) || "file";
}

async function readWorkspaceFileForDownload(
  workspaceId: string,
  path: string
): Promise<Uint8Array<ArrayBuffer>> {
  const chunks: Uint8Array<ArrayBuffer>[] = [];
  let offset = 0;
  let expectedSize: number | undefined;
  let expectedLastModifiedMillis: number | undefined;
  while (true) {
    const chunk = await api.readFileBinaryChunk(workspaceId, path, {
      offset,
      expectedSize,
      expectedLastModifiedMillis
    });
    chunks.push(decodeWorkspaceBinaryChunk(chunk, offset));
    expectedSize = chunk.size;
    expectedLastModifiedMillis = chunk.lastModifiedMillis;
    if (chunk.eof) return concatWorkspaceDownloadChunks(chunks, chunk.size);
    offset = chunk.nextOffset;
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
  }
}

async function readWorkspaceViewFileForDownload(
  workspaceId: string,
  locator: WorkspaceViewEntry["locator"]
): Promise<Uint8Array<ArrayBuffer>> {
  const chunks: Uint8Array<ArrayBuffer>[] = [];
  let offset = 0;
  let expectedSize: number | undefined;
  let expectedLastModifiedMillis: number | undefined;
  while (true) {
    const chunk = await api.readWorkspaceViewFileBinaryChunk(workspaceId, locator, {
      offset,
      expectedSize,
      expectedLastModifiedMillis
    });
    chunks.push(decodeWorkspaceBinaryChunk(chunk, offset));
    expectedSize = chunk.size;
    expectedLastModifiedMillis = chunk.lastModifiedMillis;
    if (chunk.eof) return concatWorkspaceDownloadChunks(chunks, chunk.size);
    offset = chunk.nextOffset;
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
  }
}

type WorkspaceDownloadRoot = Pick<WorkspaceViewEntry, "path" | "locator"> & { collision?: boolean };

async function collectWorkspaceViewDownloadFiles(
  workspaceId: string,
  root: WorkspaceDownloadRoot
): Promise<ReturnType<typeof finalizeWorkspaceDownloadFiles>> {
  const files: WorkspaceDownloadCandidate[] = [];
  const directories: WorkspaceViewEntry["locator"][] = [root.locator];
  let collisionDetected = root.collision === true;
  while (directories.length > 0) {
    const locator = directories.shift()!;
    const response = await api.listWorkspaceView(workspaceId, locator);
    assertCompleteWorkspaceViewDownload(response);
    for (const entry of response.entries) {
      collisionDetected ||= entry.collision;
      if (entry.type === "directory") {
        directories.push(entry.locator);
        continue;
      }
      if (entry.locator.kind === "COMPOSITE") {
        throw new Error("组合目录定位器不能作为文件下载");
      }
      files.push({
        path: relativeDownloadPath(entry.path, root.path),
        content: await readWorkspaceViewFileForDownload(workspaceId, entry.locator),
        source: entry.locator.kind,
        referenceAlias: entry.locator.referenceAlias
      });
    }
  }
  return finalizeWorkspaceDownloadFiles(files, collisionDetected);
}

async function handleDownloadEntry(entry: FileTreeEntry) {
  const workspace = selectedWorkspace.value;
  if (!workspace) return;
  if (downloadingEntryId.value) return;
  const viewEntry = isWorkspaceViewEntry(entry) ? entry : undefined;
  const entryId = viewEntry?.id ?? entry.path;
  const downloadSequence = ++workspaceDownloadSequence;
  const isCurrentDownload = () => downloadSequence === workspaceDownloadSequence
    && selectedWorkspace.value?.workspaceId === workspace.workspaceId;
  downloadingEntryId.value = entryId;
  try {
    if (entry.type === "directory") {
      const files = await collectWorkspaceViewDownloadFiles(
        workspace.workspaceId,
        viewEntry ?? {
          path: entry.path,
          locator: { kind: "WORKSPACE", path: entry.path },
          collision: false
        }
      );
      if (!isCurrentDownload()) return;
      downloadBlob(
        createZipBlob(files),
        `${entry.name}-${formatDownloadTimestamp()}.zip`
      );
      feedback.value = {
        kind: "success",
        title: "文件夹已下载",
        description: `${entry.name}（${files.length} 个文件）`
      };
      return;
    }

    const content = viewEntry
      ? await readWorkspaceViewFileForDownload(workspace.workspaceId, viewEntry.locator)
      : await readWorkspaceFileForDownload(workspace.workspaceId, entry.path);
    if (!isCurrentDownload()) return;
    downloadBlob(createWorkspaceFileBlob(content), entry.name);
    feedback.value = { kind: "success", title: "文件已下载", description: entry.name };
  } catch (error) {
    if (isCurrentDownload()) feedback.value = errorFeedback("下载工作区条目失败", error);
  } finally {
    if (downloadSequence === workspaceDownloadSequence && downloadingEntryId.value === entryId) {
      downloadingEntryId.value = undefined;
    }
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
      hasLoadedSnapshot: true,
      progressivePreview: undefined
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
    const largePreview = progressivePreviewRequired(error);
    if (largePreview) {
      await startProgressivePreview(
        tabPath,
        () => agentFileReadIsCurrent(request, tabPath, contextGeneration, requestGeneration),
        "读取 Agent 大文件预览失败"
      );
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
  if (!canWriteSelectedWorkspace.value) {
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

async function handleCopyEntry(
  sourcePath: string,
  targetDirectory: string,
  options: { quiet?: boolean } = {}
): Promise<boolean> {
  if (!selectedWorkspace.value || !canWriteSelectedWorkspace.value) {
    if (!options.quiet) {
      feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再复制文件。" };
    }
    return false;
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
    if (!options.quiet) feedback.value = { kind: "success", title: "文件已复制", description: targetPath };
    return true;
  } catch (error) {
    if (!options.quiet) feedback.value = errorFeedback("复制工作区文件失败", error);
    return false;
  }
}

async function handleCopyEntries(sourcePaths: string[], targetDirectory: string) {
  let copied = 0;
  const failed: string[] = [];
  for (const sourcePath of sourcePaths) {
    if (await handleCopyEntry(sourcePath, targetDirectory, { quiet: true })) copied += 1;
    else failed.push(sourcePath);
  }
  if (failed.length > 0) {
    feedback.value = {
      kind: "error",
      title: copied > 0 ? "部分工作区文件复制失败" : "复制工作区文件失败",
      description: failed.join("、")
    };
  } else if (copied > 0) {
    feedback.value = { kind: "success", title: `已复制 ${copied} 个工作区文件`, description: targetDirectory || "工作区根目录" };
  }
}

async function handleMoveEntry(
  sourcePath: string,
  targetDirectory: string,
  options: { quiet?: boolean } = {}
): Promise<boolean> {
  if (!selectedWorkspace.value || !canWriteSelectedWorkspace.value) {
    if (!options.quiet) {
      feedback.value = { kind: "info", title: "当前工作区条目只读", description: "请切换到个人 worktree 后再移动工作区条目。" };
    }
    return false;
  }
  const sourceDirectory = workspaceParentDirectory(sourcePath);
  if (sourceDirectory === targetDirectory) return false;
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
    if (!options.quiet) feedback.value = { kind: "success", title: "工作区条目已移动", description: targetPath };
    return true;
  } catch (error) {
    if (!options.quiet) feedback.value = errorFeedback("移动工作区条目失败", error);
    return false;
  }
}

/** 多选拖动逐项复用已验证的单条移动链路，任一失败不回滚此前成功项，并明确报告部分成功。 */
async function handleMoveEntries(sourcePaths: string[], targetDirectory: string) {
  let moved = 0;
  const failed: string[] = [];
  for (const sourcePath of sourcePaths) {
    if (await handleMoveEntry(sourcePath, targetDirectory, { quiet: true })) moved += 1;
    else failed.push(sourcePath);
  }
  if (failed.length > 0) {
    feedback.value = {
      kind: "error",
      title: moved > 0 ? "部分工作区条目移动失败" : "移动工作区条目失败",
      description: failed.join("、")
    };
  } else if (moved > 0) {
    feedback.value = { kind: "success", title: `已移动 ${moved} 个工作区条目`, description: targetDirectory || "工作区根目录" };
  }
}

type WorkspaceUploadResult = {
  uploaded: number;
  uploadedPaths: string[];
  failures: string[];
};

type WorkspaceUploadOptions = {
  resolveTargetPath?: (file: File, index: number) => string;
  reuseExistingTarget?: (file: File, targetPath: string) => Promise<boolean>;
  onUploaded?: (file: File, targetPath: string) => Promise<void> | void;
};

/**
 * 工作区普通文件和聊天附件共用这一条分片上传链路；回调只在目标文件发布成功后执行。
 * 上传完成后统一刷新目录、撤销栈和 Git diff，避免聊天入口另起一套文件写入逻辑。
 */
async function uploadWorkspaceFiles(
  directory: string,
  files: File[],
  options: WorkspaceUploadOptions = {}
): Promise<WorkspaceUploadResult> {
  const workspace = selectedWorkspace.value;
  if (!workspace || files.length === 0) {
    return { uploaded: 0, uploadedPaths: [], failures: [] };
  }
  if (workspaceUploadOverlay.value) {
    return { uploaded: 0, uploadedPaths: [], failures: ["已有文件正在上传，请稍后重试"] };
  }
  const workspaceId = workspace.workspaceId;
  const failures: string[] = [];
  const uploadedPaths: string[] = [];
  let uploaded = 0;
  let completedBytes = 0;
  workspaceUploadOverlay.value = initialFileUploadOverlayState(files);
  try {
    for (const [index, file] of files.entries()) {
      workspaceUploadOverlay.value = {
        ...workspaceUploadOverlay.value!,
        fileName: file.name,
        fileIndex: index + 1,
        fileUploadedBytes: 0,
        fileBytes: file.size,
        completedBytes
      };
      try {
        // 浏览器通常只提供 basename；再次截断路径分隔符，避免构造 File 时夹带目录片段。
        const targetPath = options.resolveTargetPath?.(file, index)
          ?? workspacePathInDirectory(directory, fileNameOf(file.name));
        const reused = await options.reuseExistingTarget?.(file, targetPath) === true;
        if (!reused) {
          await api.uploadWorkspaceFile(workspaceId, targetPath, file, (progress) => {
            if (!workspaceUploadOverlay.value) return;
            workspaceUploadOverlay.value = {
              ...workspaceUploadOverlay.value,
              fileUploadedBytes: progress.uploadedBytes,
              fileBytes: progress.totalBytes
            };
          });
          uploaded += 1;
          uploadedPaths.push(targetPath);
        } else if (workspaceUploadOverlay.value) {
          workspaceUploadOverlay.value = {
            ...workspaceUploadOverlay.value,
            fileUploadedBytes: file.size,
            fileBytes: file.size
          };
        }
        try {
          await options.onUploaded?.(file, targetPath);
        } catch (error) {
          const reason = error instanceof Error ? error.message : "附件处理失败";
          failures.push(`${file.name}：文件${reused ? "已复用" : "已上传"}，但未能加入附件（${reason}）`);
        }
      } catch (error) {
        const reason = error instanceof Error ? error.message : "上传失败";
        failures.push(`${file.name}：${reason}`);
      } finally {
        completedBytes += file.size;
      }
    }
    if (uploaded > 0) {
      await loadDirectory(directory, undefined, true);
      workspaceUndoStack.value.push({ kind: "delete", paths: uploadedPaths, label: `上传 ${uploaded} 个文件` });
      void refreshWorkspaceGitDiff();
    }
    return { uploaded, uploadedPaths, failures };
  } finally {
    workspaceUploadOverlay.value = null;
  }
}

async function handleUploadFiles(directory: string, files: File[]) {
  if (!selectedWorkspace.value || !canWriteSelectedWorkspace.value) {
    feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再上传文件。" };
    return;
  }
  if (files.length === 0) return;
  try {
    const result = await uploadWorkspaceFiles(directory, files);
    if (result.failures.length > 0) {
      feedback.value = {
        kind: "error",
        title: result.uploaded > 0 ? "部分文件上传失败" : "上传文件失败",
        description: result.failures.join("；")
      };
      return;
    }
    feedback.value = { kind: "success", title: `已上传 ${result.uploaded} 个文件`, description: directory || "工作区根目录" };
  } catch (error) {
    feedback.value = errorFeedback("上传文件失败", error);
  }
}

/** 聊天附件落到专用工作区目录，Run 只携带唯一物理路径和原始展示名。 */
async function handleChatAttachmentUpload(files: File[]) {
  if (!selectedWorkspace.value || !canWriteSelectedWorkspace.value) {
    feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再上传聊天附件。" };
    return;
  }
  if (files.length === 0) return;
  const beforeCount = chatAttachments.value.length;
  try {
    const workspaceId = selectedWorkspace.value.workspaceId;
    // 目录创建仍走现有工作区文件 WebSocket RPC；createDirectories 可安全处理已存在目录。
    await api.createDirectory(workspaceId, CHAT_ATTACHMENT_DIRECTORY);
    const targetPaths = new Map<File, string>();
    for (const file of files) {
      const sha256 = await blobSha256Hex(file);
      targetPaths.set(
        file,
        workspaceAttachmentTargetPath(CHAT_ATTACHMENT_DIRECTORY, file.name, `sha256_${sha256}`)
      );
    }
    const result = await uploadWorkspaceFiles(CHAT_ATTACHMENT_DIRECTORY, files, {
      resolveTargetPath: (file) => targetPaths.get(file)
        ?? workspaceAttachmentTargetPath(CHAT_ATTACHMENT_DIRECTORY, file.name, "sha256_missing"),
      reuseExistingTarget: async (file, targetPath) => {
        const status = await api.fileStatus(workspaceId, targetPath);
        if (status.exists !== true) return false;
        if (status.directory === true || status.size !== file.size) {
          throw new Error("附件内容指纹路径已存在，但文件状态不一致，请检查工作区附件目录");
        }
        return true;
      },
      onUploaded: (file, targetPath) => {
        const attachment = workspaceFileToPromptAttachment(file, targetPath);
        if (!chatAttachments.value.some((item) => item.id === attachment.id)) {
          chatAttachments.value = [...chatAttachments.value, attachment];
        }
      }
    });
    const addedCount = chatAttachments.value.length - beforeCount;
    if (result.failures.length > 0) {
      feedback.value = {
        kind: "error",
        title: addedCount > 0 ? "部分聊天附件上传失败" : "聊天附件上传失败",
        description: result.failures.join("；")
      };
      return;
    }
    feedback.value = {
      kind: "success",
      title: `已添加 ${addedCount} 个聊天附件`,
      description: `文件已保存到工作区 ${CHAT_ATTACHMENT_DIRECTORY}，并会随下一条任务提交。`
    };
  } catch (error) {
    feedback.value = errorFeedback("上传聊天附件失败", error);
  }
}

function handleRemoveChatAttachment(id: string) {
  chatAttachments.value = chatAttachments.value.filter((attachment) => attachment.id !== id);
}

/** 撤销本页面最近一次复制、移动或上传；所有逆操作仍走当前个人 worktree 的平台文件 RPC。 */
async function handleUndoWorkspaceFileOperation() {
  const workspace = selectedWorkspace.value;
  const operation = workspaceUndoStack.value.at(-1);
  if (!workspace || !canWriteSelectedWorkspace.value || !operation) return;

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
  if (!canWriteSelectedWorkspace.value) {
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

async function handleDeleteEntry(
  path: string,
  type: "file" | "directory",
  options: { quiet?: boolean } = {}
): Promise<boolean> {
  if (!selectedWorkspace.value) {
    return false;
  }
  if (!canWriteSelectedWorkspace.value) {
    if (!options.quiet) {
      feedback.value = { kind: "info", title: "当前工作区只读", description: "请切换到个人 worktree 后再修改应用文件。" };
    }
    return false;
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
    if (!options.quiet) {
      feedback.value = {
        kind: "success",
        title: type === "directory" ? "文件夹已删除" : "文件已删除",
        description: path
      };
    }
    return true;
  } catch (error) {
    if (!options.quiet) feedback.value = errorFeedback(`删除${type === "file" ? "文件" : "文件夹"}失败`, error);
    return false;
  }
}

/** 右键批量删除逐项复用单条清理逻辑，保证目录缓存、打开标签和 Git Diff 均按成功项收敛。 */
async function handleDeleteEntries(entries: { path: string; type: "file" | "directory" }[]) {
  let deleted = 0;
  const failed: string[] = [];
  for (const entry of entries) {
    if (await handleDeleteEntry(entry.path, entry.type, { quiet: true })) deleted += 1;
    else failed.push(entry.path);
  }
  if (failed.length > 0) {
    feedback.value = {
      kind: "error",
      title: deleted > 0 ? "部分工作区条目删除失败" : "删除工作区条目失败",
      description: failed.join("、")
    };
  } else if (deleted > 0) {
    feedback.value = { kind: "success", title: `已删除 ${deleted} 个工作区条目`, description: "工作区文件树已刷新" };
  }
}

type CacheFileData = {
  title: string;
  content: string;
};

function extractItemNo(filePath: string): string | undefined {
  const segments = filePath.split(/[\\/]+/).filter(Boolean);
  for (const seg of segments) {
    if (seg.startsWith("S") || seg.startsWith("s")) {
      const parts = seg.split("-");
      if (parts.length >= 2 && /^\d+$/.test(parts[1] ?? "")) {
        return `${parts[0]}-${parts[1]}`;
      }
    }
  }
  return undefined;
}

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

    const itemNo = extractItemNo(path);

    const body = JSON.stringify({
      type: cacheType,
      appName,
      version,
      itemNo,
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

async function handleCacheAndNavigateEntries(entries: { path: string; type: "file" | "directory" }[]) {
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

  if (entries.length === 0) {
    ElMessage.warning("没有选中的文件");
    return;
  }

  try {
    const files: CacheFileData[] = [];
    let cacheType = "md";
    let hasValidFile = false;

    for (const entry of entries) {
      if (entry.type === "directory") {
        if (entry.path.includes("测试执行")) {
          cacheType = "json";
          const dirFiles = await collectAllFilesInDirectory(workspaceId, entry.path);
          files.push(...dirFiles);
          hasValidFile = true;
        }
      } else {
        if (entry.path.includes("测试设计")) {
          cacheType = "md";
          const fileContent = await api.readFile(workspaceId, entry.path);
          files.push({ title: fileNameOf(entry.path), content: fileContent.content });
          hasValidFile = true;
        } else if (entry.path.includes("测试执行")) {
          cacheType = "json";
          const fileContent = await api.readFile(workspaceId, entry.path);
          files.push({ title: fileNameOf(entry.path), content: fileContent.content });
          hasValidFile = true;
        }
      }
    }

    if (!hasValidFile) {
      ElMessage.warning("仅测试设计和测试执行目录下的文件支持缓存跳转");
      return;
    }

    if (files.length === 0) {
      ElMessage.warning("没有可缓存的文件");
      return;
    }

    const firstValidEntry = entries.find((e) => e.path.includes("测试设计") || e.path.includes("测试执行"));
    const itemNo = firstValidEntry ? extractItemNo(firstValidEntry.path) : undefined;

    const body = JSON.stringify({
      type: cacheType,
      appName,
      version,
      itemNo,
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
    console.log("============请求后台（多选）=====================", result);

    if (result.data?.jumpUrl) {
      window.open(result.data.jumpUrl, "_blank", "noopener,noreferrer");
    } else {
      ElMessage.error("获取跳转地址失败");
    }
  } catch (error) {
    console.error("多选文件缓存数据并跳转失败", error);
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
  if (collaborativeSessionActive.value && runtimeBusy.value) {
    feedback.value = {
      kind: "info",
      title: "当前会话正在运行",
      description: "分享会话运行期间所有参与者都需等待，不能排队或再次发送。"
    };
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
  if (resendEditDraft.value) {
    const editedPrompt = prompt.trim();
    const resendValidation = validateChatSend(editedPrompt, []);
    if (!resendValidation.ok) {
      feedback.value = { kind: "info", title: "无法重新发送", description: resendValidation.reason };
      return;
    }
    if (!editedPrompt) {
      feedback.value = { kind: "info", title: "无法重新发送", description: "请保留或输入要重新发送的消息内容。" };
      return;
    }
    void retryLastRun(editedPrompt);
    return;
  }
  const sendValidation = validateChatSend(prompt.trim(), chatContextStore.items);
  if (!sendValidation.ok) {
    feedback.value = { kind: "info", title: "上下文过长", description: sendValidation.reason };
    return;
  }
  const routedAttachments = routeWorkspaceAttachmentsForModel(attachments, selectedModelInfo.value);
  const chatContextParts = chatContextItemsToPromptParts(chatContextStore.items);
  // 显式上下文附件存在时，不再叠加旧的“当前活动编辑器/选区”隐式 PromptPart，
  // 避免同一选区或整个活动文件在本轮请求中重复进入模型上下文。
  const implicitEditorTab = chatContextStore.items.length === 0 ? activeTab.value : undefined;
  const implicitEditorSelection = chatContextStore.items.length === 0 ? editorSelection.value : undefined;
  const selectionContexts = chatContextStore.items.filter((item): item is Extract<ChatContextItem, { type: "selection" }> => item.type === "selection");
  const displayParts = buildPromptParts(prompt, implicitEditorTab, routedAttachments, [...chatContextParts, ...diffContextParts.value], implicitEditorSelection);
  const displayPrompt = prompt.trim() || promptFromParts(displayParts);
  const rawSubmitPrompt = prompt.trim() || displayPrompt;
  // 选区文本直接作为结构化 prompt 发送，避免 opencode 将其回放成整文件附件或触发原生文件读取。
  const submitPrompt = selectionContexts.length > 0 ? serializeChatContexts(rawSubmitPrompt, selectionContexts) : rawSubmitPrompt;
  // prompt_async 有 parts 时只发送 parts；selection 必须进入 text part，不能只放在顶层 prompt。
  const parts = buildPromptParts(submitPrompt, implicitEditorTab, routedAttachments, [...chatContextParts, ...diffContextParts.value], implicitEditorSelection);
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
    chatAttachments.value = [];
    return;
  }
  // slash 技能和普通消息统一创建平台 Run，才能复用 SSE、刷新恢复和终止能力。
  const runDraft: ChatRunDraft = {
    prompt: submitPrompt,
    parts,
    userMessageId,
    title: displayPrompt,
    command: command ?? undefined
  };
  clearRunEventSseFeedback();
  requestChatRun(userMessageId);
  chatContextStore.clearContexts();
  chatAttachments.value = [];
  startRunMutation.mutate({ input: runDraft, guard: captureConversationInteraction() });
}

/**
 * 批量流程只消费与 # 同源的候选并使用局部上下文，不切换当前 Session，也不清空输入框和附件。
 */
async function handleBatchTestCaseGeneration(request: BatchGenerationRequest, controls: BatchExecutionControls) {
  if (batchTestCaseRunning.value) {
    controls.reject();
    return;
  }
  if (!selectedWorkspace.value) {
    feedback.value = { kind: "info", title: "未选择工作区", description: "请先切换到应用版本或个人工作区。" };
    controls.reject();
    return;
  }
  if (!opencodeProcessReady.value || opencodeProcessStatus.value?.messageSendAllowed === false) {
    feedback.value = {
      kind: "info",
      title: "暂不能批量生成",
      description: opencodeProcessStatus.value?.messageSendBlockedReason
        ?? opencodeProcessStatus.value?.message
        ?? "请先初始化 TestAgent 进程。"
    };
    controls.reject();
    return;
  }
  if (request.scheduleMode === "ADMIN_CUSTOM" && !isSuperAdmin.value) {
    feedback.value = { kind: "error", title: "无权使用测试定时", description: "仅超级管理员可以自定义执行时间。" };
    controls.reject();
    return;
  }

  try {
    const result = await batchTestCaseGeneration.execute(request);
    void queryClient.invalidateQueries({ queryKey: ["sessions"] });
    if (request.executionMode === "scheduled") void refreshNightExecutionTasks();
    if (result.capacityConflict) void requestNightExecutionSlots();
    feedback.value = result.failed === 0
      ? {
          kind: "success",
          title: request.executionMode === "immediate" ? "批量案例已开始执行" : "批量定时任务已创建",
          description: `共 ${result.succeeded} 个子条目已成功提交。`
        }
      : {
          kind: "info",
          title: "批量处理已完成",
          description: `${result.succeeded} 项成功，${result.failed} 项失败，可在弹层中仅重试失败项。`
        };
  } catch (error) {
    controls.reject();
    feedback.value = errorFeedback("批量生成测试案例失败", error);
    if (error instanceof BackendApiError && error.status === 409) void requestNightExecutionSlots();
  }
}

/** 用户主动结束进度页后清理前端批次身份；已创建的服务端对象不受影响。 */
function resetBatchTestCaseGeneration() {
  batchTestCaseGeneration.reset();
}

/**
 * 夜间任务使用与立即发送相同的 prompt/上下文组装规则，但提交成功前不写入 Timeline，
 * 避免“尚未执行”的输入被误认为已经发送给模型。
 */
async function handleScheduleNight(payload: {
  prompt: string;
  scheduleMode: NightExecutionScheduleMode;
  slotStart: string;
  attachments?: ComposerAttachment[];
}) {
  if (nightTaskSubmitting.value || historySwitchingSessionId.value) return;
  if (payload.scheduleMode === "ADMIN_CUSTOM" && !isSuperAdmin.value) {
    feedback.value = { kind: "error", title: "无权使用测试定时", description: "仅超级管理员可以自定义执行时间。" };
    return;
  }
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
  const attachments = routeWorkspaceAttachmentsForModel(
    payload.attachments ?? chatAttachments.value,
    selectedModelInfo.value
  );
  const chatContextParts = chatContextItemsToPromptParts(chatContextStore.items);
  const implicitEditorTab = chatContextStore.items.length === 0 ? activeTab.value : undefined;
  const implicitEditorSelection = chatContextStore.items.length === 0 ? editorSelection.value : undefined;
  const selectionContexts = chatContextStore.items.filter(
    (item): item is Extract<ChatContextItem, { type: "selection" }> => item.type === "selection"
  );
  const displayParts = buildPromptParts(
    payload.prompt,
    implicitEditorTab,
    attachments,
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
    attachments,
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
    scheduleMode: payload.scheduleMode,
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
    scheduleMode: payload.scheduleMode,
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
    chatAttachments.value = [];
    diffContextParts.value = [];
    feedback.value = {
      kind: "success",
      title: payload.scheduleMode === "ADMIN_CUSTOM" ? "测试定时已安排" : "夜间任务已安排",
      description: payload.scheduleMode === "ADMIN_CUSTOM"
        ? `计划于 ${formatBeijingDateTimeInput(new Date(payload.slotStart)).replace("T", " ")}（北京时间）启动。`
        : "任务会在所选 15 分钟时间段内自动启动。"
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
  const existingTask = nightTasks.value.find((task) => task.taskId === payload.taskId)
    ?? (recentlyCreatedNightTask.value?.taskId === payload.taskId ? recentlyCreatedNightTask.value : null);
  const customSchedule = existingTask?.scheduleMode === "ADMIN_CUSTOM";
  if (customSchedule && !isSuperAdmin.value) {
    feedback.value = { kind: "error", title: "无权调整测试定时", description: "仅超级管理员可以调整自定义执行时间。" };
    return;
  }
  markNightTaskAction(payload.taskId, true);
  try {
    const adjusted = await api.adjustNightExecutionTask(payload.taskId, payload.slotStart);
    nightTasks.value = nightTasks.value.map((task) => task.taskId === adjusted.taskId ? adjusted : task);
    if (recentlyCreatedNightTask.value?.taskId === adjusted.taskId) recentlyCreatedNightTask.value = adjusted;
    feedback.value = {
      kind: "success",
      title: "执行时间已调整",
      description: customSchedule ? "测试定时已更新，不占用夜间容量。" : "系统已重新预留夜间容量。"
    };
    if (!customSchedule) void requestNightExecutionSlots();
  } catch (error) {
    feedback.value = errorFeedback("调整夜间任务失败", error);
    if (!customSchedule && error instanceof BackendApiError && error.status === 409) void requestNightExecutionSlots();
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
    if (task?.scheduleMode !== "ADMIN_CUSTOM") void requestNightExecutionSlots();
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

async function openSettingsRoute() {
  if (route.name === "settings") {
    settingsOpen.value = true;
    return;
  }
  settingsOpenedFromActivity = true;
  try {
    await router.push({ name: "settings" });
  } catch (error) {
    settingsOpenedFromActivity = false;
    throw error;
  }
}

function closeSettings() {
  settingsOpen.value = false;
  // 设置页可能修改工作空间启用状态，关闭后仅刷新切换模板，不改变当前工作空间。
  void queryClient.invalidateQueries({ queryKey: ["managed-workspace", "app-templates"] });
  if (route.name !== "settings") return;

  // 活动栏打开时返回原页面；直接访问 /settings 时替换为工作台，避免退出 SPA。
  if (settingsOpenedFromActivity) {
    settingsOpenedFromActivity = false;
    router.back();
    return;
  }
  void router.replace({ name: "workbench" });
}

function refreshManagedWorkspaceCatalog() {
  // 异步创建操作真正成功时立即刷新底部选择器；不能只依赖关闭设置时可能过早的刷新。
  void queryClient.invalidateQueries({ queryKey: ["managed-workspace", "app-templates"] });
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
  if (!canStopRun.value) {
    feedback.value = { kind: "info", title: "无权停止当前运行", description: stopDisabledReason.value };
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

/** 撤回入口先把上一条消息恢复到输入框，用户确认或修改后才创建替代 Run。 */
function handleRetryRun() {
  if (!resendableMessageId.value) {
    feedback.value = {
      kind: "info",
      title: "无权撤回重发",
      description: shareMode.value
        ? "共享对话仅允许最后一条消息的实际发送人在可对话状态下操作。"
        : "仅消息实际发送人可以撤回最后一条已结束的用户消息。"
    };
    return;
  }
  const sourceRun = run.value;
  const sourceMessage = [...chatState.value.messages].reverse().find(
    (message): message is Extract<AgentMessage, { role: "user" }> => message.role === "user"
      && message.runId === sourceRun?.runId
      && message.remoteMessageId === resendableMessageId.value
  );
  if (!session.value || !sourceRun || !sourceMessage?.remoteMessageId) {
    feedback.value = { kind: "info", title: "无法撤回重发", description: "没有找到可编辑的最后一条用户消息。" };
    return;
  }
  resendEditDraft.value = {
    sessionId: session.value.sessionId,
    sourceRunId: sourceRun.runId,
    expectedRemoteMessageId: sourceMessage.remoteMessageId,
    sourceMessageId: sourceMessage.id
  };
  composerInputValue.value = displayTextFromUserPrompt(sourceMessage.text);
}

function cancelResendEdit() {
  if (resendStarting.value) return;
  resendEditDraft.value = null;
  composerInputValue.value = "";
}

function handleRunEvent(event: RunEvent, subscribedSessionId?: string, allowNotification = true) {
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
  applyRunEventWorkbenchProjection(projectedEvent, allowNotification, subscribedSessionId);
}

/**
 * 历史基线与 RunEvent 并发加载时保留当前 Run 的实时事件；迟到的消息页或会话树覆盖基线后会立即重放。
 * 新 reset 已包含当时完整物化状态，因此可以丢弃它之前的缓存，避免长时间历史加载无限积累事件。
 */
function bufferHistorySwitchRunEvent(event: RunEvent, subscribedSessionId: string) {
  const buffer = historySwitchRunEventBuffer;
  if (
    !buffer
    || buffer.switchSeq !== historySwitchSeq
    || buffer.sessionId !== subscribedSessionId
    || historySwitchingSessionId.value !== subscribedSessionId
  ) {
    return;
  }
  if (buffer.runId && buffer.runId !== event.runId) {
    // 替代 Run 在历史加载期间接管订阅时，旧 Run 的缓存必须整体让位；旧连接晚到事件已在投影守卫处被拒绝。
    buffer.events = [];
  }
  buffer.runId = event.runId;
  if (event.type === "run.snapshot.reset") {
    buffer.events = [event];
    return;
  }
  if (!buffer.events.some((item) => item.eventId === event.eventId)) {
    buffer.events.push(event);
  }
}

/** 历史状态被整块替换后，无通知地重放同一批实时事件，恢复思考、工具、Todo 与运行状态。 */
function replayHistorySwitchRunEvents(buffer: HistorySwitchRunEventBuffer) {
  if (
    historySwitchRunEventBuffer !== buffer
    || buffer.switchSeq !== historySwitchSeq
    || session.value?.sessionId !== buffer.sessionId
  ) {
    return;
  }
  for (const event of buffer.events) {
    handleRunEvent(event, buffer.sessionId, false);
  }
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
  if (event.type === "run.resend.scheduled" || event.type === "run.resend.started" || event.type === "run.resend.failed") {
    const resend = resendMetadataFromRunEvent(event);
    const replacementRunId = text(event.payload.replacementRunId) ?? event.runId;
    const current = run.value;
    const currentSession = session.value;
    if (resend && currentSession) {
      const replacementStatus = event.type === "run.resend.scheduled"
        ? "PENDING"
        : event.type === "run.resend.started" ? "RUNNING" : "FAILED";
      run.value = {
        runId: replacementRunId,
        sessionId: currentSession.sessionId,
        workspaceId: current?.workspaceId ?? currentSession.workspaceId,
        storageMode: current?.runId === replacementRunId ? current.storageMode : undefined,
        status: replacementStatus,
        createdAt: current?.runId === replacementRunId ? current.createdAt : event.occurredAt,
        updatedAt: event.occurredAt,
        sourceType: current?.sourceType ?? currentSession.sourceType,
        sourceRefId: current?.sourceRefId,
        resend
      };
      rememberRunSession(run.value);
    }
    if (event.type === "run.resend.started") {
      // reducer 已原子移除源 Run 明细；这里同步清理独立维护的文件与跟随投影，再接管替代 Run。
      diffFiles.value = [];
      diffSource.value = "run";
      liveFollowedParts.value = new Set();
      workbench.setSelectedDiffPath(undefined);
      chatStartedAt.value = Date.now();
      accumulatedTokens.value = 0;
      void refreshWorkspaceGitDiff();
      void refreshWorkspaceView();
      if (subscribedSessionId && session.value?.sessionId === subscribedSessionId && resend) {
        // 所属人与分享参与人统一原位补齐平台 USER；revision 先到时复用同一 in-flight 任务。
        void refreshAuthoritativeResendUser(subscribedSessionId, resend);
      }
    }
    if (event.type === "run.resend.failed") {
      feedback.value = {
        kind: "error",
        title: "撤销重发失败",
        description: "本次重发未投递，原会话轮次已按安全恢复结果保留。"
      };
    }
    return;
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
      if (centerMode.value !== "hub" && centerMode.value !== "toolbox" && centerMode.value !== "memories") {
        centerMode.value = nextCenterModeAfterRunDiff(centerMode.value, diffSource.value);
      }
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
      if (centerMode.value !== "hub" && centerMode.value !== "toolbox" && centerMode.value !== "memories") {
        centerMode.value = nextCenterModeAfterRunDiff(centerMode.value, diffSource.value);
      }
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
      ? {
          ...run.value,
          status: event.type === "run.succeeded" ? "SUCCEEDED" : event.type === "run.failed" ? "FAILED" : "CANCELLED",
          updatedAt: event.occurredAt
        }
      : run.value;
    // Run 完成后刷新所有已展开的目录，确保新增/删除/修改的文件在左侧工作区立即可见。
    setTimeout(() => {
      void refreshWorkspaceView();
    }, 500);
    // 计算任务消耗统计：duration 由 chatStartedAt 锁定，tokens 仍优先取累计值；
    // 如果后端 payload 直接带上 tokens 字段，则覆盖一次（向后兼容未来后端实现）。
    if (!restoreScheduledRunTiming(run.value) && chatStartedAt.value) {
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

function resendMetadataFromRunEvent(event: RunEvent): ResendMetadata | undefined {
  const resendId = text(event.payload.resendId);
  const sourceRunId = text(event.payload.sourceRunId);
  const replacementRunId = text(event.payload.replacementRunId) ?? event.runId;
  const trigger = text(event.payload.trigger);
  const executeAt = text(event.payload.executeAt);
  const totalAttempt = typeof event.payload.totalAttempt === "number" ? event.payload.totalAttempt : undefined;
  const automaticAttempt = typeof event.payload.automaticAttempt === "number" ? event.payload.automaticAttempt : undefined;
  const automaticLimit = typeof event.payload.automaticLimit === "number" ? event.payload.automaticLimit : undefined;
  if (!resendId || !sourceRunId || !trigger || !executeAt
      || totalAttempt === undefined || automaticAttempt === undefined || automaticLimit === undefined) {
    return undefined;
  }
  return {
    resendId,
    trigger,
    totalAttempt,
    automaticAttempt,
    automaticLimit,
    status: text(event.payload.status) ?? (event.type === "run.resend.failed" ? "FAILED" : "WAITING"),
    executeAt,
    sourceRunId,
    replacementRunId,
    requesterUserId: text(event.payload.requesterUserId),
    requesterUsername: text(event.payload.requesterUsername),
    requesterUnifiedAuthId: text(event.payload.requesterUnifiedAuthId),
    requestedBySharedUser: event.payload.requestedBySharedUser === true
  };
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
  const rootPath = selectedWorkspacePhysicalRootPath.value ?? "";
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
  if (selectedWorkspaceKind.value === "APP_SOURCE" && (source === "vcs" || source === "agent")) return;
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
  // handler 层与组件隐藏双重守卫，防止源码模式通过迟到事件或编程调用触发 Git API。
  if (selectedWorkspaceKind.value === "APP_SOURCE") {
    vcsDiffFiles.value = [];
    return;
  }
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
      if (centerMode.value !== "hub" && centerMode.value !== "toolbox" && centerMode.value !== "memories") {
        centerMode.value = nextCenterModeAfterVcsRefresh(centerMode.value, diffSource.value, nextFiles);
      }
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
  if (selectedWorkspaceKind.value === "APP_SOURCE") return;
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
      // 发布失败后的应用 Agent 已经完成本地提交，后端工作树 diff 为空；
      // 复用变更面板保留的提交前 patch，确保点击“待推送”文件仍能看到本轮差异。
      if (payload.file && !diffFiles.value.some((file) => file.path === payload.path)) {
        diffFiles.value = [...diffFiles.value, payload.file];
      }
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

function canSaveDiffFile(path: string | undefined) {
  if (!path) return false;
  const agent = isAgentFilePath(path) ? agentFileInfo(path) : null;
  return diffFileCanWrite({
    workspaceKind: selectedWorkspaceKind.value,
    ordinaryWorkspaceWritable: canWriteSelectedWorkspace.value,
    agentScope: agent?.scope ?? null,
    isSuperAdmin: isSuperAdmin.value,
    isAppAdmin: isAppAdmin.value
  });
}

const canSaveSelectedDiffFile = computed(() => canSaveDiffFile(selectedDiffPath.value));

const saveDiffFileMutation = useMutation({
  mutationFn: async ({ path, content }: { path: string; content: string }) => {
    if (!canSaveDiffFile(path)) {
      throw new Error("当前 Workspace 不允许保存该 Diff 文件");
    }
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
    if (!canWriteSelectedWorkspace.value) {
      throw new Error("当前 Workspace 不允许写入");
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
          description: (userRuntimeBusy.value || runtimeReloadConflictWaitingForIdle.value)
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
  if (!canSaveDiffFile(path)) {
    feedback.value = {
      kind: "info",
      title: "当前文件不可保存",
      description: "源码快照只允许保存普通源码文件，不能修改 Agent 配置。"
    };
    return;
  }
  saveDiffFileMutation.mutate({ path, content });
}

async function switchSession(
  sessionId: string,
  options: { refreshSnapshot?: boolean; completionFeedback?: Feedback } = {}
) {
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
  const historyMessagesPromise = api.listSessionMessages(sessionId, 1, 100, { refresh: options.refreshSnapshot ?? false });
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
  const liveRunEvents: HistorySwitchRunEventBuffer = {
    switchSeq,
    sessionId,
    events: []
  };
  historySwitchRunEventBuffer = liveRunEvents;
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
    const restoredFiles = diffFilesFromSessionMessages(persistedMessages).map((file) => ({
      ...file,
      path: normalizeWorkspacePath(file.path) || file.path
    }));
    diffFiles.value = restoredFiles;
    replayHistorySwitchRunEvents(liveRunEvents);
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
        permissions: replaceRootSessionInteractions(chatState.value.permissions, livePermissions, sessionId),
        questions: replaceRootSessionInteractions(chatState.value.questions, liveQuestions, sessionId),
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
      replayHistorySwitchRunEvents(liveRunEvents);
      if (livePermissions !== null || liveQuestions !== null || liveTodos !== null) {
        chatState.value = {
          ...chatState.value,
          permissions: replaceRootSessionInteractions(chatState.value.permissions, livePermissions, sessionId),
          questions: replaceRootSessionInteractions(chatState.value.questions, liveQuestions, sessionId),
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
        const currentRun = run.value;
        if (!currentRun || !isRunBusyStatus(currentRun.status) || currentRun.runId === runDetail.runId) {
          // runtime-state 已接管其它活动 Run 时，历史消息关联的旧终态只能补 Diff，不能抢走停止权限和 SSE 身份。
          run.value = runDetail;
          rememberRunSession(runDetail);
        }
        const runFiles = (diffDetail.files ?? []).map((file) => ({
          ...file,
          path: normalizeWorkspacePath(file.path) || file.path
        }));
        // RunEvent 可能已在历史详情请求期间补入实时 Diff；以当前投影为基线合并，不能退回旧快照。
        diffFiles.value = mergeDiffFiles(diffFiles.value, runFiles);
      } catch (runErr) {
        if (switchIsCurrent()) {
          console.error("加载关联 Run 失败", runErr);
        }
      }
    } else if (!isRunBusyStatus(run.value?.status)) {
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

    feedback.value = options.completionFeedback ?? { kind: "info", title: "已切换 Session", description: selected.title };
  } catch (error) {
    if (switchIsCurrent()) {
      feedback.value = errorFeedback("加载 Session 消息失败", error);
    }
  } finally {
    if (switchIsCurrent()) {
      historyLoadingSessionId.value = null;
      historySwitchingSessionId.value = null;
      if (historySwitchRunEventBuffer === liveRunEvents) {
        historySwitchRunEventBuffer = null;
      }
    }
  }
}

async function switchToHistorySessionWorkspace(
  selected: Session,
  interactionIsCurrent: () => boolean
): Promise<string | null> {
  if (shareMode.value) {
    try {
      const workspace = await api.getWorkspace(selected.workspaceId);
      if (!interactionIsCurrent()) return null;
      const switched = await switchWorkspace(workspace, {
        preserveConversationInteraction: true,
        awaitDirectory: false,
        kind: "MANAGED",
        isCurrent: interactionIsCurrent
      });
      if (!switched || !interactionIsCurrent()) return null;
      return shareAccess.value?.canChat
        ? ""
        : "当前分享权限为只读，不能修改工作区或发送消息。";
    } catch (error) {
      if (!interactionIsCurrent()) return null;
      feedback.value = errorFeedback("打开分享会话工作区失败", error);
      return "分享会话所属工作区暂时不可用。";
    }
  }
  const expectedAppId = selected.workspaceContext?.appId?.trim();
  const requiresManagedWorkspace = Boolean(expectedAppId);
  const selectionAuthority = beginManagedWorkspaceIntent(expectedAppId || selectedAppId.value);
  const selectionIsCurrent = () => interactionIsCurrent() && appSourceIntentIsCurrent(selectionAuthority);
  try {
    let workspace = await api.getWorkspace(selected.workspaceId);
    if (!selectionIsCurrent()) {
      return null;
    }
    try {
      // markRecentManagedWorkspace 会校验当前用户仍可进入历史会话所属应用，并回填版本/模板信息。
      const response = await api.markRecentManagedWorkspace(selected.workspaceId);
      if (!selectionIsCurrent()) {
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
    if (!selectionIsCurrent()) {
      return null;
    }
    if (nextAppId) {
      selectedAppId.value = nextAppId;
    }
    const personalWorkspaceContext = await resolvePersonalWorkspaceRuntimeContext(workspace);
    if (!selectionIsCurrent()) {
      return null;
    }
    if (workspace.workspaceId !== selectedWorkspaceIdRef.value) {
      if (!selectionIsCurrent()) {
        return null;
      }
      const switched = await switchWorkspace(workspace, {
        preserveConversationInteraction: true,
        awaitDirectory: false,
        isCurrent: selectionIsCurrent,
        personalWorkspaceContext
      });
      if (!switched || !selectionIsCurrent()) {
        return null;
      }
    } else {
      if (!selectionIsCurrent()) {
        return null;
      }
      cacheWorkspace(workspace);
      selectedWorkspaceSnapshot.value = workspace;
      rememberPersonalWorkspace(
        personalWorkspaceContext?.personalWorkspaceId,
        personalWorkspaceContext?.personalWorkspaceBranch
      );
      syncCurrentVersionFromWorkspace(workspace);
    }
    return "";
  } catch (error) {
    if (!selectionIsCurrent()) {
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
    permissionCount: nextSessions.filter((item) => item.attention === "PERMISSION").length,
    sessions: nextSessions,
    generatedAt: new Date().toISOString()
  };
  sessionRuntimeState.value = nextSummary;
  queryClient.setQueryData(sessionRuntimeStateQueryKey, nextSummary);
}

function handleNewConversation() {
  if (shareMode.value) {
    // 分享路由固定绑定所属人的会话；新建对话必须先回到当前用户自己的工作台。
    void router.push({ name: "workbench" });
    return;
  }
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
  memoryUsageByRunId.value = {};
  feedbackSubmitting.value = {};
  platformMessageIdsByRemoteId.value = {};
  assistantSummaryMessageIdsByRunId.value = {};
  readonlySessionReason.value = "";
  diffFiles.value = [];
  // 新建对话后不能把上一轮尚未发送的文件附件带入新 Session。
  chatAttachments.value = [];
  
  // 新建对话后清空任务消耗统计，防止上一轮对话的耗时残留。
  chatStartedAt.value = null;
  accumulatedTokens.value = 0;
  totalDurationMs.value = 0;
  lastDuration = undefined;
  lastTokens = 0;
  nowTick.value = Date.now();
}

const nativeCommandInFlight = ref(false);
type CompactProgressStatus = "running" | "success";
const compactProgressStatus = ref<CompactProgressStatus | null>(null);
let compactProgressDismissTimer: ReturnType<typeof setTimeout> | null = null;

function clearCompactProgressDismissTimer() {
  if (compactProgressDismissTimer) {
    clearTimeout(compactProgressDismissTimer);
    compactProgressDismissTimer = null;
  }
}

function dismissCompactProgressAfterSuccess() {
  clearCompactProgressDismissTimer();
  compactProgressDismissTimer = setTimeout(() => {
    compactProgressStatus.value = null;
    compactProgressDismissTimer = null;
  }, 2600);
}

onBeforeUnmount(() => {
  clearCompactProgressDismissTimer();
});

function nativeSessionActionAllowed(action: string, options: { allowBusy?: boolean } = {}): Session | null {
  const currentSession = session.value;
  if (!currentSession) {
    feedback.value = { kind: "info", title: `无法${action}`, description: "请先发送一条消息建立会话。" };
    return null;
  }
  if (readonlySessionReason.value) {
    feedback.value = { kind: "info", title: `无法${action}`, description: readonlySessionReason.value };
    return null;
  }
  if (!options.allowBusy && runtimeBusy.value) {
    feedback.value = { kind: "info", title: `暂不能${action}`, description: "请等待当前任务结束或先停止任务。" };
    return null;
  }
  return currentSession;
}

/**
 * 执行需要平台 Session API 的 OpenCode TUI 命令。
 * 本地导航类命令由 FigmaChatPanel 处理；这里集中守住会话身份、只读状态和运行态约束。
 */
async function handleNativeTuiCommand(command: OpenCodeTuiCommandName) {
  if (nativeCommandInFlight.value) {
    feedback.value = { kind: "info", title: "原生命令执行中", description: "请等待当前命令完成。" };
    return;
  }
  const actionLabels: Partial<Record<OpenCodeTuiCommandName, string>> = {
    compact: "压缩上下文",
    rename: "重命名会话"
  };
  const actionLabel = actionLabels[command];
  if (!actionLabel) return;
  // 会话标题是平台元数据，运行中的任务不影响重命名；压缩仍必须等待当前任务结束。
  const currentSession = nativeSessionActionAllowed(actionLabel, { allowBusy: command === "rename" });
  if (!currentSession) return;

  nativeCommandInFlight.value = true;
  let compactSucceeded = false;
  try {
    if (command === "rename") {
      const result = await ElMessageBox.prompt("请输入新的会话名称", "重命名会话", {
        inputValue: currentSession.title,
        inputPlaceholder: "会话名称",
        confirmButtonText: "保存",
        cancelButtonText: "取消",
        inputValidator: (value: string) => value.trim().length > 0 || "会话名称不能为空"
      });
      const title = String(result.value).trim();
      if (title === currentSession.title) {
        feedback.value = { kind: "info", title: "会话名称未变化", description: title };
        return;
      }
      const updated = await updateSessionMutation.mutateAsync({ sessionId: currentSession.sessionId, title });
      feedback.value = { kind: "success", title: "会话已重命名", description: updated.title };
      return;
    }
    if (command === "compact") {
      const modelID = selectedModelInfo.value?.id ?? modelIdOnly(selectedModel.value);
      const providerID = selectedModelInfo.value?.providerId ?? selectedProvider.value;
      if (!modelID || !providerID) {
        feedback.value = { kind: "info", title: "无法压缩上下文", description: "请先选择包含供应商信息的模型。" };
        return;
      }
      // 原生 summarize 会等待模型生成摘要；持久进度条覆盖整个长请求，完成后再原位收束为成功态。
      clearCompactProgressDismissTimer();
      compactProgressStatus.value = "running";
      feedback.value = { kind: "info", title: "正在压缩上下文", description: currentSession.title };
      await api.compactSession(currentSession.sessionId, { providerID, modelID });
      const completionFeedback: Feedback = { kind: "success", title: "上下文已压缩", description: currentSession.title };
      await switchSession(currentSession.sessionId, {
        refreshSnapshot: true,
        completionFeedback
      });
      // switchSession 会在消息刷新失败或被更新的会话选择作废时自行返回；只有它采用本次完成反馈才展示成功态。
      if (toRaw(feedback.value) !== completionFeedback) return;
      compactSucceeded = true;
      compactProgressStatus.value = "success";
      return;
    }
  } catch (error) {
    if (command === "rename" && (error === "cancel" || error === "close")) return;
    feedback.value = errorFeedback(`${actionLabel}失败`, error);
  } finally {
    if (command === "compact") {
      if (compactSucceeded) {
        dismissCompactProgressAfterSuccess();
      } else {
        compactProgressStatus.value = null;
      }
    }
    nativeCommandInFlight.value = false;
  }
}

/** 执行 OpenCode TUI 的 !command，并刷新远端 Session 投影以显示 shell 工具结果。 */
async function handleNativeShellCommand(command: string) {
  if (nativeCommandInFlight.value) {
    feedback.value = { kind: "info", title: "原生命令执行中", description: "请等待当前命令完成。" };
    return;
  }
  const currentSession = nativeSessionActionAllowed("执行 Shell 命令");
  if (!currentSession) return;
  nativeCommandInFlight.value = true;
  try {
    const modelID = selectedModelInfo.value?.id ?? modelIdOnly(selectedModel.value);
    const providerID = selectedModelInfo.value?.providerId ?? selectedProvider.value;
    await api.runSessionShell(currentSession.sessionId, {
      command,
      agent: selectedAgent.value || "build",
      ...(modelID && providerID ? { model: { providerID, modelID } } : {})
    });
    await switchSession(currentSession.sessionId, {
      refreshSnapshot: true,
      completionFeedback: { kind: "success", title: "Shell 命令已执行", description: command }
    });
  } catch (error) {
    feedback.value = errorFeedback("执行 Shell 命令失败", error);
  } finally {
    nativeCommandInFlight.value = false;
  }
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
  await loadMemoryUsageForRunIds(runIds, expectedSessionId, interactionIsCurrent);
}

/** 记忆使用记录走批量 HTTP 恢复；白名单未开放或服务降级时静默保持无徽标。 */
async function loadMemoryUsageForRunIds(
  runIds: string[],
  expectedSessionId?: string,
  interactionIsCurrent: () => boolean = () => true
) {
  if (runIds.length === 0) return;
  const loaded: Record<string, MemoryUsageView[]> = Object.fromEntries(runIds.map((runId) => [runId, []]));
  try {
    for (let index = 0; index < runIds.length; index += 200) {
      const usages = await api.queryQaMemoryRunUsage(runIds.slice(index, index + 200));
      for (const usage of usages) {
        (loaded[usage.runId] ??= []).push(usage);
      }
    }
  } catch {
    return;
  }
  if (interactionIsCurrent() && (!expectedSessionId || session.value?.sessionId === expectedSessionId)) {
    memoryUsageByRunId.value = { ...memoryUsageByRunId.value, ...loaded };
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
  <div
    v-if="shareMode && shareRuntimeState?.active === false"
    class="session-share-invalid-overlay"
    role="alertdialog"
    aria-modal="true"
    aria-label="分享会话已失效"
  >
    <div>
      <strong>分享会话已失效</strong>
      <p>{{ shareInvalidReason(shareRuntimeState.reason) }}</p>
      <button type="button" @click="router.replace({ name: 'workbench' })">返回我的工作台</button>
    </div>
  </div>
  <FigmaShell
    :workspace-name="selectedWorkspace?.name"
    :fixed-workspace="shareMode"
    :bottom-open="bottomDrawerOpen"
    :show-left-panel="leftPanelOpen"
    :show-right-panel="rightPanelOpen"
    :apps="shellApps"
    :joinable-apps="joinableApps"
    :selected-app-id="selectedAppId"
    :app-templates="appTemplatesWithVersions"
    :show-app-source="Boolean(selectedManagedApplication)"
    :workspace-kind="selectedWorkspaceKind"
    :app-source-repositories="appSourceRepositories"
    :loading-app-source-repositories="appSourcePickerLoading"
    :app-source-repositories-error="appSourcePickerError"
    :selected-app-source-repository-id="appSourceContext?.repositoryId"
    :selected-workspace-template-id="selectedWorkspaceKind === 'MANAGED' ? (selectedWorkspace?.applicationWorkspaceId ?? undefined) : undefined"
    :selected-version-id="selectedWorkspaceKind === 'MANAGED' ? selectedVersionId : undefined"
    :loading-app-templates="loadingAppTemplates"
    :loading-app-versions="loadingAppVersions"
    :help-center-open="helpCenterOpen"
    :current-user-name="authStore.currentUser?.username"
    :current-user-role-labels="authStore.currentUser?.roleLabels"
    :can-play-pet-games="isSuperAdmin"
    :can-manage-public-agent-config="isSuperAdmin"
    :can-manage-workspace-agent-config="isAppAdmin && appSourceCapabilities.canPublishApplicationAgentConfig"
    :personal-runtime-reloading="personalRuntimeReloading"
    :runtime-busy="runtimeReloadBusy"
    :opencode-process-status="opencodeProcessStatus"
    :opencode-process-loading="opencodeProcessInitialLoading"
    :opencode-process-initializing="initializeOpencodeProcessMutation.isPending.value"
    :show-process-status-in-pet="!shareMode"
    :onboarding-active="firstLoginGuideActive"
    :side-question-answer="robotSideQuestion.answer.value"
    :side-question-error="robotSideQuestion.error.value"
    :side-question-loading="robotSideQuestion.loading.value"
    :side-question-progress="robotSideQuestion.progress.value"
    :side-question-available="robotQuestionAvailable"
    :side-question-manual-mode="!session?.sessionId"
    :runtime-inventory="runtimeInventoryForShell"
    :notifications="notificationItems"
    :notification-unread-count="notificationUnreadCount"
    :notification-filter="notificationFilter"
    :notifications-loading="notificationsLoading"
    :notifications-loading-more="notificationsLoadingMore"
    :notifications-has-more="notificationsHasMore"
    :notifications-error="notificationsError"
    @toggle-left-panel="leftPanelOpen = !leftPanelOpen"
    @toggle-right-panel="rightPanelOpen = !rightPanelOpen"
    @select-app="handleSelectApp"
    @load-versions="handleLoadVersions"
    @select-version="handleSelectVersion"
    @open-app-source="openAppSourcePicker"
    @load-app-source-repositories="loadAppSourceRepositories"
    @open-app-source-repository="openAppSourceRepository"
    @manage-app-source-repository="openAppSourceDownloadDialog"
    @return-managed-workspace="fallbackToManagedWorkspace()"
    @refresh-opencode-process="refreshOpencodeProcessStatus"
    @initialize-process="beginInitializeOpencodeProcess"
    @logout="handleLogout"
    @join-app="handleJoinApp"
    @robot-side-question="handleRobotSideQuestion"
    @close-robot-side-question="handleCloseRobotSideQuestion"
    @personal-runtime-reload="handlePersonalRuntimeReload"
    @open-help="openHelpCenter"
    @notification-filter="handleNotificationFilter"
    @refresh-notifications="refreshUserNotifications"
    @load-more-notifications="loadMoreUserNotifications"
    @open-notification="handleOpenNotification"
  >
    <template #activity>
      <nav v-if="!shareMode" class="figma-activity-nav" aria-label="工作台活动栏">
        <div class="figma-activity-top">
          <button
            type="button"
            :class="['figma-activity-btn figma-activity-btn--editor', centerMode === 'editor' && 'figma-activity-btn--active']"
            data-onboarding="editor-button"
            aria-label="打开工作台"
            title="工作台"
            @click="selectActivityCenterMode('editor')"
          >
            <LayoutDashboard class="figma-activity-icon" :stroke-width="1.5" />
            <span class="figma-activity-text">工作台</span>
          </button>
          <button
            type="button"
            :class="['figma-activity-btn figma-activity-btn--toolbox', centerMode === 'toolbox' && 'figma-activity-btn--active']"
            aria-label="工具盒子"
            title="工具箱"
            data-testid="toolbox-activity-button"
            @click="toggleToolbox"
          >
            <Wrench class="figma-activity-icon" :stroke-width="1.5" />
            <span class="figma-activity-text">工具箱</span>
          </button>
          <button
            type="button"
            :class="['figma-activity-btn figma-activity-btn--memories', centerMode === 'memories' && 'figma-activity-btn--active']"
            aria-label="长期记忆"
            title="记忆中心"
            data-testid="memory-activity-button"
            @click="toggleMemories"
          >
            <BrainCircuit class="figma-activity-icon" :stroke-width="1.5" />
            <span class="figma-activity-text">记忆</span>
          </button>
          <button
            v-if="canUseLobehub"
            type="button"
            class="figma-activity-btn figma-activity-btn--qa"
            aria-label="通用问答"
            :title="lobehubLaunching ? '正在进入通用问答' : '通用问答'"
            data-testid="lobehub-activity-button"
            :disabled="lobehubLaunching"
            @click="openLobehub"
          >
            <MessageSquare class="figma-activity-icon" :stroke-width="1.5" />
            <span class="figma-activity-text">问答</span>
          </button>
          <button
            v-if="canUseWorkflow"
            type="button"
            class="figma-activity-btn figma-activity-btn--workflow"
            aria-label="长程任务工作台"
            title="长任务"
            data-testid="workflow-chat-activity-button"
            @click="openWorkflowChat"
          >
            <GitCompareArrows class="figma-activity-icon" :stroke-width="1.5" />
            <span class="figma-activity-text">长任务</span>
          </button>
          <button
            v-if="isSuperAdmin"
            type="button"
            :class="['figma-activity-btn figma-activity-btn--system', centerMode === 'system' && 'figma-activity-btn--active']"
            aria-label="系统管理"
            title="控制台"
            @click="selectActivityCenterMode('system')"
          >
            <Monitor class="figma-activity-icon" :stroke-width="1.5" />
            <span class="figma-activity-text">控制台</span>
          </button>
          <button
            type="button"
            :class="['figma-activity-btn figma-activity-btn--hub hub-activity-button', centerMode === 'hub' && 'figma-activity-btn--active']"
            aria-label="Agent、Skill、MCP 与 Tool Hub"
            title="能力库"
            data-testid="agent-skill-hub-button"
            @click="toggleAgentSkillHub"
          >
            <Boxes class="figma-activity-icon" :stroke-width="1.5" />
            <span class="figma-activity-text">能力库</span>
            <span v-if="hubUpdateCount > 0" class="hub-activity-badge" aria-label="有可用更新">
              {{ hubUpdateCount > 99 ? '99+' : hubUpdateCount }}
            </span>
          </button>
        </div>
        <div class="figma-activity-bottom">
          <button
            type="button"
            :class="['figma-activity-btn figma-activity-btn--settings', settingsOpen && 'figma-activity-btn--active']"
            data-onboarding="settings"
            aria-label="系统设置"
            title="设置"
            @click="openSettingsRoute"
          >
            <ElSetting class="figma-activity-icon" />
            <span class="figma-activity-text">设置</span>
          </button>
        </div>
      </nav>
    </template>

    <template #files>
      <div v-if="selectedManagedApplication || selectedWorkspace" class="managed-workspace-layout">
        <FigmaFileExplorer
          ref="fileExplorerRef"
          class="managed-workspace-files"
          :workspace-name="selectedWorkspace?.name ?? '未选择工作区'"
          :workspace-root-path="selectedWorkspacePhysicalRootPath"
          :entries-by-directory="entriesByDirectory"
          :expanded-directories="expandedDirectories"
          :active-path="activeWorkspaceViewNodeId"
          :changed-files="vcsDiffFiles"
          :loading-path="loadingPath"
          :downloading-entry-id="downloadingEntryId"
          :app-name="selectedManagedApplication?.appName"
          :app-templates="appTemplatesWithVersions"
          :selected-version-id="selectedWorkspaceKind === 'MANAGED' ? selectedVersionId : undefined"
          :loading-app-templates="loadingAppTemplates"
          :loading-app-versions="loadingAppVersions"
          :creating-version="creatingVersion"
          :pulling-personal-workspace="pullingPersonalWorkspace"
          :can-write="canWriteSelectedWorkspace"
          :can-undo="workspaceUndoStack.length > 0"
          :can-manage-agent-config="appSourceCapabilities.canPublishApplicationAgentConfig && isAppAdmin"
          :can-manage-public-config="selectedWorkspaceKind === 'MANAGED' && isSuperAdmin"
          :api-base-url="apiBaseUrl"
          :route-linux-server-id="routeLinuxServerId"
          :route-linux-server-resolved="routeLinuxServerResolved"
          :public-worktree-mount-request="publicWorktreeMountRequest"
          :workspace-id="selectedWorkspace?.workspaceId"
          :agent-config-workspace-id="selectedAgentConfigWorkspaceId"
          :personal-workspace-id="currentPersonalWorkspaceId"
          :personal-pull-blocking-files="currentPersonalPullBlockingFiles"
          :personal-workspace-branch="currentPersonalWorkspaceBranch"
          :agent-config-revision="agentConfigRevision"
          :personal-runtime-reloading="personalRuntimeReloading"
          :runtime-busy="runtimeReloadBusy"
          :show-server-workspace-switch="isSuperAdmin"
          :show-reference-configuration="selectedWorkspaceKind === 'MANAGED' && showReferenceConfiguration"
          :workspace-kind="selectedWorkspaceKind"
          :app-source-context="appSourceContext"
          :app-source-repositories="appSourceRepositories"
          :loading-app-source-repositories="appSourcePickerLoading"
          :app-source-repositories-error="appSourcePickerError"
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
          @pull-personal-workspace="handlePullPersonalWorkspace"
          @open-agent-file="openAgentFile"
          @open-server-workspace-picker="openServerWorkspacePicker"
          @open-reference-configuration="openReferenceConfiguration"
          @open-app-source="openAppSourcePicker"
          @load-app-source-repositories="loadAppSourceRepositories"
          @open-app-source-repository="openAppSourceRepository"
          @manage-app-source-repository="openAppSourceDownloadDialog"
          @return-managed-workspace="fallbackToManagedWorkspace()"
          @search="handleFileSearch"
          @create-entry="handleCreateEntry"
          @delete-entry="handleDeleteEntry"
          @delete-entries="handleDeleteEntries"
          @rename-entry="handleRenameEntry"
          @copy-entry="handleCopyEntry"
          @download-entry="handleDownloadEntry"
          @copy-entries="handleCopyEntries"
          @move-entry="handleMoveEntry"
          @move-entries="handleMoveEntries"
          @upload-files="handleUploadFiles"
          @undo-entry="handleUndoWorkspaceFileOperation"
          @cache-and-navigate="handleCacheAndNavigate"
          @cache-and-navigate-entries="handleCacheAndNavigateEntries"
        />
      </div>
      <div v-else class="managed-workspace-empty">
        <p>请选择应用后进入应用版本或个人工作区。</p>
      </div>
    </template>

    <template #editor>
      <main class="managed-editor-main">
        <template v-if="centerMode === 'toolbox'">
          <ToolboxPanel />
        </template>
        <template v-else-if="centerMode === 'memories'">
          <MemoryCenter
            :selected-app-id="selectedAppId"
            :can-manage-team="isAppAdmin"
            @open-skill-hub="toggleAgentSkillHub"
          />
        </template>
        <template v-else-if="centerMode === 'hub'">
          <AgentSkillHub
            :selected-app-id="selectedAppId"
            :workspace-id="selectedWorkspace?.workspaceId"
            :can-manage="isAppAdmin && appSourceCapabilities.canPublishApplicationAgentConfig"
            :can-classify-skills="isSuperAdmin"
            :runtime-mcp="runtimeInventoryForShell.mcp"
            :runtime-tools="runtimeInventoryForShell.tools"
            @update-count="hubUpdateCount = $event"
            @changed="handleHubChanged"
            @refresh-runtime="refreshRuntimeHubCatalog"
          />
        </template>
        <template v-else-if="centerMode === 'diff'">
          <div class="flex-1 min-h-0 min-w-0">
            <DiffViewer
              ref="diffViewerRef"
              :files="diffFiles"
              :selected-path="selectedDiffPath"
              :source="diffSource"
              :view-mode="diffViewMode"
              :accepting="acceptDiffMutation.isPending.value"
              :rejecting="rejectDiffMutation.isPending.value"
              :writable="canSaveSelectedDiffFile"
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
            :workspace-root-path="selectedWorkspacePhysicalRootPath"
            :dirty="isDiffDirty"
            :saving="saveDiffFileMutation.isPending.value"
            :readonly="!canSaveSelectedDiffFile"
            :app-name="selectedManagedApplication?.appName"
            :templates="appTemplatesWithVersions"
            :selected-version-id="selectedVersionId"
            :personal-workspace-branch="currentPersonalWorkspaceBranch"
            :loading-templates="loadingAppTemplates"
            :loading-versions="loadingAppVersions"
            :creating-version="creatingVersion"
            :show-server-workspace-switch="isSuperAdmin"
            :workspace-kind="selectedWorkspaceKind"
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
            <SystemManagementWrapper
              :current-user="authStore.currentUser"
              :support-access-requested="supportAccessRequested"
              @support-access-opened="supportAccessRequested = false"
            />
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
          :workspace-root-path="selectedWorkspacePhysicalRootPath"
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
          :workspace-kind="selectedWorkspaceKind"
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
            :data-state="activeTab?.progressivePreview ? 'progressive-preview' : (activeTab?.loadState ?? (activeTab ? 'loaded' : 'idle'))"
          >
            <CodeEditor
              v-if="!activeTabInitialLoading"
              ref="codeEditorRef"
              :path="activeTab?.path"
              :content="activeTab?.content"
              :dirty="activeTab && !activeTab.livePreview ? activeTab.content !== activeTab.savedContent : false"
              :readonly="activeTab?.readonly"
              :progressive-append="!!activeTab?.progressivePreview"
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
              class="pointer-events-none absolute inset-0 z-10 flex flex-col items-center justify-center bg-slate-50/70 backdrop-blur-sm"
              role="status"
            >
              <!-- 醒目的文件加载动画卡片，包含外圈渐变旋转、呼吸内圈和点阵 Spinner -->
              <div class="flex flex-col items-center gap-5 p-8 rounded-2xl bg-white/90 border border-slate-100 shadow-[0_12px_40px_-12px_rgba(0,0,0,0.12)] max-w-sm w-[85%] text-center animate-file-load-card">
                <!-- 动画容器 -->
                <div class="relative flex items-center justify-center w-16 h-16">
                  <!-- 外圈旋转渐变色 -->
                  <div class="absolute inset-0 rounded-full border-2 border-transparent border-t-indigo-500 border-r-purple-500 animate-spin" style="animation-duration: 1.2s;"></div>
                  <!-- 内圈呼吸背景 -->
                  <div class="absolute inset-2 rounded-full bg-gradient-to-tr from-indigo-50 to-purple-50 animate-pulse"></div>
                  <!-- 点阵 Spinner 核心 -->
                  <Spinner class="relative z-10 w-6 h-6 text-indigo-600" />
                </div>
                
                <div class="flex flex-col gap-1">
                  <div class="text-sm font-semibold bg-gradient-to-r from-indigo-600 to-purple-600 bg-clip-text text-transparent select-none">
                    正在读取文件
                  </div>
                  <div class="text-xs text-slate-400 select-none flex items-center gap-1 justify-center">
                    <span class="inline-block w-1.5 h-1.5 rounded-full bg-indigo-500/80 animate-pulse"></span>
                    <span>正在努力加载，请稍候</span>
                  </div>
                </div>
              </div>
            </div>
            <div
              v-else-if="activeTab?.progressivePreview"
              class="absolute inset-x-3 top-3 z-10 rounded-lg border border-amber-200 bg-amber-50/95 px-3 py-2.5 shadow-sm backdrop-blur"
              role="status"
              aria-live="polite"
              data-testid="file-progressive-preview"
            >
              <div class="flex items-start gap-2.5">
                <FileWarning class="mt-0.5 shrink-0 text-amber-600" :size="17" :stroke-width="1.7" aria-hidden="true" />
                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
                    <div class="text-xs font-medium text-amber-900">大文件只读预览</div>
                    <div class="font-mono text-[11px] text-amber-800/80">
                      {{ formatPreviewBytes(activeTab.progressivePreview.loadedBytes) }} /
                      {{ formatPreviewBytes(activeTab.progressivePreview.size) }}
                      · {{ progressivePreviewPercent(activeTab.progressivePreview) }}%
                    </div>
                  </div>
                  <p class="mt-1 text-[11px] leading-4 text-amber-800/80">
                    文件超过 {{ formatPreviewBytes(activeTab.progressivePreview.warningThresholdBytes) }}，已分段加载。
                    加载全部内容可能占用较多内存，并导致编辑器明显卡顿。
                  </p>
                  <div class="mt-2 h-1 overflow-hidden rounded-full bg-amber-200/70" aria-hidden="true">
                    <div
                      class="h-full rounded-full bg-amber-500 transition-[width] duration-200"
                      :style="{ width: `${progressivePreviewPercent(activeTab.progressivePreview)}%` }"
                    />
                  </div>
                  <div class="mt-2 flex flex-wrap items-center gap-2">
                    <template v-if="!activeTab.progressivePreview.eof">
                      <button
                        type="button"
                        class="rounded border border-amber-300 bg-white px-2.5 py-1 text-[11px] font-medium text-amber-900 hover:bg-amber-100 disabled:cursor-wait disabled:opacity-60"
                        :disabled="activeTab.progressivePreview.loading"
                        @click="loadMoreActivePreview(false)"
                      >
                        继续加载一段
                      </button>
                      <button
                        type="button"
                        class="rounded border border-amber-500 bg-amber-500 px-2.5 py-1 text-[11px] font-medium text-white hover:bg-amber-600 disabled:cursor-wait disabled:opacity-60"
                        :disabled="activeTab.progressivePreview.loading"
                        @click="loadMoreActivePreview(true)"
                      >
                        {{ activeTab.progressivePreview.loadingAll ? "正在加载全部…" : "加载全部（可能卡顿）" }}
                      </button>
                    </template>
                    <span v-else class="text-[11px] font-medium text-emerald-700">已加载全部内容</span>
                    <span v-if="activeTab.progressivePreview.loadError" class="text-[11px] text-red-700">
                      {{ activeTab.progressivePreview.loadError }}
                    </span>
                  </div>
                </div>
              </div>
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
          :shared-sessions="sharedSessionItems"
          :shared-sessions-loading="sharedSessionsQuery.isFetching.value"
          :fixed-session="shareMode"
          :can-manage-share="!shareMode && Boolean(session?.sessionId)"
          :history-search="sessionSearch"
          :history-total="sessionHistoryTotal"
          :history-has-more="sessionHistoryHasMore"
          :history-loading-more="sessionHistoryLoadingMore"
          :history-loading="Boolean(historyLoadingSessionId)"
          :history-pinning-session-id="historyPinningSessionId"
          :history-submit-blocked="Boolean(historySwitchingSessionId)"
          :history-running-count="sessionRuntimeState?.runningCount ?? 0"
          :history-question-count="sessionRuntimeState?.questionCount ?? 0"
          :history-permission-count="sessionPermissionAttentionCount"
          :readonly-reason="readonlySessionReason"
          :process-status="opencodeProcessStatus"
          process-status-placement="pet"
          :process-required="!shareMode"
          :process-loading="opencodeProcessInitialLoading"
          :process-refreshing="opencodeProcessRefreshing"
          :process-initializing="initializeOpencodeProcessMutation.isPending.value"
          :permissions="chatState.permissions"
          :questions="chatState.questions"
          :current-session-id="session?.sessionId"
          :current-user-id="authStore.currentUser?.userId"
          :current-session-source-type="session?.sourceType"
          :resendable-message-id="resendableMessageId"
          :input-value="composerInputValue"
          :resend-editing="Boolean(resendEditDraft)"
          :resend-submitting="resendStarting"
          :compact-status="compactProgressStatus"
          :night-tasks="nightTasks"
          :current-night-task="currentNightTask"
          :night-visible-failure="nightVisibleFailure"
          :night-slots="nightSlots"
          :night-slots-loading="nightSlotsLoading"
          :night-task-submitting="nightTaskSubmitting"
          :night-task-action-pending="nightTaskActionPending"
          :can-schedule-custom-time="isSuperAdmin"
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
          :batch-item-states="batchTestCaseItemStates"
          :batch-running="batchTestCaseRunning"
          :chat-attachments="chatAttachments"
          :chat-attachments-uploading="!!workspaceUploadOverlay"
          :agents-loading="agentsLoading"
          :agents-refreshing="agentsRefreshing"
          :agents-error="agentsError"
          :selected-agent="selectedAgent"
          :stop-disabled="!canStopRun"
          :stop-disabled-reason="stopDisabledReason"
          :models="models"
          :providers="providers"
          :selected-provider="selectedProvider"
          :selected-model="selectedModel"
          :run-feedbacks="runFeedbacks"
          :memory-usage-by-run-id="memoryUsageByRunId"
          :feedback-submitting="feedbackSubmitting"
          :run-statuses-by-run-id="chatState.runStatusesByRunId"
          :commands="commands"
          :raw-output-entries="currentRawOutputEntries"
          placeholder="描述测试任务，例如：跑 checkout 模块并分析失败原因"
          @send="(text: string, attachments?: ComposerAttachment[]) => handleSend(text, attachments)"
          @update:input-value="(value: string) => { composerInputValue = value; }"
          @cancel-resend-edit="cancelResendEdit"
          @upload-chat-attachments="handleChatAttachmentUpload"
          @remove-chat-attachment="handleRemoveChatAttachment"
          @stop="handleStopRun"
          @retry="handleRetryRun"
          @new-conversation="handleNewConversation"
          @native-command="handleNativeTuiCommand"
          @run-shell="handleNativeShellCommand"
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
          @toggle-session-pinned="handleToggleSessionPinned"
          @initialize-process="beginInitializeOpencodeProcess"
          @open-help="openHelpCenter"
          @open-diff="(path: string) => { if (path) workbench.setSelectedDiffPath(path); centerMode = 'diff'; }"
          @open-file="openFile"
          @preview-context="handlePreviewContext"
          @reply-permission="(requestId: string, decision: 'once' | 'always' | 'reject') => replyPermissionMutation.mutate({ requestId, decision })"
          @reply-question="(requestId: string, answers: unknown[]) => replyQuestionMutation.mutate({ requestId, answers })"
          @reject-question="(requestId: string) => rejectQuestionMutation.mutate(requestId)"
          @select-session="(id: string) => switchSession(id)"
          @select-shared-session="openSharedSession"
          @request-shared-sessions="refreshSharedSessions"
          @manage-share="sessionShareDialogOpen = true"
          @change-agent="selectRuntimeAgent"
          @refresh-agents="refreshAgentsCatalog"
          @search-workspace-files="handleWorkspaceFileCandidateSearch"
          @load-workspace-requirements="loadWorkspaceRequirementCandidates"
          @execute-batch-test-cases="handleBatchTestCaseGeneration"
          @reset-batch-test-cases="resetBatchTestCaseGeneration"
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
            :cancel-disabled="!canStopRun"
            :retry-disabled="!resendableMessageId"
            @cancel="handleStopRun"
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

  <AppSourcePicker
    v-if="!shareMode"
    :open="appSourcePickerOpen"
    :repositories="appSourceRepositories"
    :loading="appSourcePickerLoading"
    :error="appSourcePickerError"
    @close="closeAppSourcePicker"
    @retry="loadAppSourceRepositories"
    @download="openAppSourceDownloadDialog"
    @open-source="openAppSourceRepository"
  />

  <AppSourceDialog
    v-if="!shareMode"
    :open="appSourceDialogOpen"
    :repositories="appSourceRepositories"
    :repository="selectedAppSourceRepository"
    :branches="appSourceBranches"
    :branches-loading="appSourceBranchesLoading"
    :branches-error="appSourceBranchesError"
    :tree-snapshot="appSourceTreeSnapshot"
    :tree-branch="appSourceTreeBranch"
    :tree-loading="appSourceTreeLoading"
    :tree-error="appSourceTreeError"
    :operation="appSourceOperation"
    :submitting="appSourceSubmitting"
    :retention-updating="appSourceRetentionUpdating"
    :retention-error="appSourceRetentionError"
    :materialization-error="appSourceMaterializationError"
    :progress-error="appSourceProgressError"
    @close="closeAppSourceDialog"
    @select-repository="selectAppSourceRepository"
    @load-branches="loadAppSourceBranches"
    @load-tree="loadAppSourceTree"
    @update-retention="updateAppSourceRetention"
    @materialize="materializeAppSource"
    @retry="retryAppSourceOperation"
  />

  <PersonalWorkspacePullDialog
    v-if="!shareMode"
    :open="personalPullDialog.open"
    :phase="personalPullDialog.phase"
    :app-name="selectedManagedApplication?.appName"
    :branch="currentPersonalWorkspaceBranch"
    :pull-result="personalPullDialog.pullResult"
    :result="personalPullDialog.result"
    :error-title="personalPullDialog.errorTitle"
    :error-description="personalPullDialog.errorDescription"
    @confirm="confirmPersonalPull"
    @cancel="cancelPersonalPullDialog"
    @close="closePersonalPullDialog"
  />

  <ServerWorkspacePickerDialog
    v-if="!shareMode"
    :open="serverWorkspacePickerOpen"
    :servers="serverWorkspaceServers"
    :selected-server-id="selectedServerWorkspaceServerId"
    :directory="serverWorkspaceDirectory"
    :loading="serverWorkspacePickerLoading"
    :current-agent-linux-server-id="opencodeProcessStatus?.linuxServerId"
    :server-terminal-enabled="isSuperAdmin"
    :terminal-base-url="apiBaseUrl"
    :create-server-terminal-ticket="createServerTerminalTicket"
    :new-tab-url="serverWorkspacePickerNewTabUrl"
    @close="serverWorkspacePickerOpen = false"
    @select-server="selectServerWorkspaceServer"
    @navigate="(path: string) => loadServerWorkspaceDirectories(path)"
    @select="selectServerWorkspaceDirectory"
  />

  <ReferenceConfigurationDialog
    v-if="!shareMode"
    :open="referenceConfigurationOpen"
    :app-id="selectedAppId ?? ''"
    :workspace-id="selectedWorkspace?.workspaceId ?? ''"
    @close="referenceConfigurationOpen = false"
    @saved="refreshWorkspaceViewAfterReferenceSaved"
  />

  <SettingsDialog
    v-if="!shareMode"
    :open="settingsOpen"
    :current-user="authStore.currentUser"
    :route-linux-server-id="routeLinuxServerId"
    :initial-app-id="selectedAppId"
    :initial-menu-key="firstLoginGuideActive ? firstLoginGuideSettingsMenu : undefined"
    :initial-app-tab="firstLoginGuideActive ? firstLoginGuideSettingsTab : undefined"
    @close="closeSettings"
    @workspace-catalog-changed="refreshManagedWorkspaceCatalog"
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
    v-if="!shareMode"
    ref="firstLoginGuideRef"
    :user-id="authStore.currentUser?.userId"
    :app-admin="isAppAdmin"
    @prepare="prepareFirstLoginGuide"
    @settings-step="handleFirstLoginGuideSettingsStep"
    @dismiss="dismissFirstLoginGuide"
    @finish="finishFirstLoginGuide"
  />

  <OpencodeProcessStartupDialog
    v-if="!shareMode"
    :open="processStartupDialogOpen"
    :action-label="processStartupActionLabel"
    :operation="processStartupOperation"
    @close="processStartupDialogOpen = false"
  />

  <SessionShareDialog
    v-if="!shareMode"
    :open="sessionShareDialogOpen"
    :session="session"
    :api="ordinaryApi"
    :pending-task-count="currentNightTask ? 1 : 0"
    @close="sessionShareDialogOpen = false"
    @updated="handleSessionShareUpdated"
  />

  <FileUploadOverlay v-if="workspaceUploadOverlay" v-bind="workspaceUploadOverlay" />

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
.session-share-invalid-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: grid;
  place-content: center;
  background: rgba(245, 247, 251, 0.94);
  backdrop-filter: blur(5px);
}

.session-share-invalid-overlay > div {
  display: grid;
  width: min(420px, calc(100vw - 32px));
  justify-items: center;
  gap: 10px;
  border: 1px solid #e4e7ec;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(16, 24, 40, 0.14);
  padding: 30px;
  text-align: center;
}

.session-share-invalid-overlay strong { color: #1d2939; font-size: 19px; }
.session-share-invalid-overlay p { margin: 0; color: #667085; }
.session-share-invalid-overlay button {
  margin-top: 6px;
  border: 0;
  border-radius: 8px;
  background: #315ed8;
  color: white;
  padding: 9px 15px;
  cursor: pointer;
}

.hub-activity-button {
  position: relative;
}

.hub-activity-badge {
  position: absolute;
  top: 1px;
  right: 1px;
  min-width: 14px;
  height: 14px;
  border: 1.5px solid var(--ta-shell-canvas, #f8f9fa);
  border-radius: 999px;
  background: var(--ta-shell-accent, #c8161d);
  padding: 0 3px;
  color: #ffffff;
  font-size: 9px;
  font-weight: 700;
  line-height: 11px;
  text-align: center;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

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

/* 醒目的文件加载卡片淡入及缩放动画，提升首屏过渡体验 */
@keyframes file-load-card-in {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(8px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.animate-file-load-card {
  animation: file-load-card-in 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
</style>
