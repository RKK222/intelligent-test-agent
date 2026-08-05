<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  createInitialAgentChatRuntimeState,
  createOpencodeLikeState,
  OpencodeTimeline,
  type AgentChatRuntimeState
} from "@test-agent/agent-chat";
import { FileExplorer } from "@test-agent/file-explorer";
import type { BackendApiClient } from "@test-agent/backend-api";
import { copyTextToClipboard } from "@test-agent/ui-kit";
import type {
  CurrentUser,
  FilePreviewChunk,
  FileSearchResult,
  FileTreeEntry,
  PageResponse,
  Session,
  SessionTreeMessagesResponse,
  SupportAccessAuditEvent,
  SupportAccessGrant,
  UserManagementUser,
  Workspace
} from "@test-agent/shared-types";
import { chatStateFromSessionTreeSnapshot } from "../workbench-utils";

const props = defineProps<{
  currentUser: CurrentUser | null;
  activationSequence?: number;
}>();
const api = inject<BackendApiClient>("api")!;

type MainTab = "access" | "audit";
type ResourceTab = "sessions" | "workspaces";

const mainTab = ref<MainTab>("access");
const resourceTab = ref<ResourceTab>("sessions");
const grant = ref<SupportAccessGrant | null>(null);
const incidentId = ref("");
const incidentPrefilled = ref(false);
const incidentSuggestionSource = ref<"WORK_ORDER" | "GENERATED" | "">("");
const incidentSuggestionLoading = ref(false);
const reason = ref("");
const durationMinutes = ref(30);
const readOnlyAcknowledged = ref(false);
const issuing = ref(false);
const closing = ref(false);
const errorMessage = ref("");
const now = ref(Date.now());

const users = ref<UserManagementUser[]>([]);
const usersLoading = ref(false);
const targetUser = ref<UserManagementUser | null>(null);
const selectedTargetUserId = ref("");
const targetSelecting = ref(false);

const sessionQuery = ref("");
const includeArchived = ref(false);
const sessions = ref<PageResponse<Session>>({ items: [], page: 1, size: 30, total: 0 });
const sessionsLoading = ref(false);
const selectedSession = ref<Session | null>(null);
const transcriptState = ref<AgentChatRuntimeState>(createInitialAgentChatRuntimeState());
const transcript = computed(() => transcriptState.value.messages);
const transcriptLoading = ref(false);
const historyRepresentation = ref<string | null>(null);
const replayAvailable = ref<boolean | null>(null);
const detailsAvailableUntil = ref<string | null>(null);
const transcriptTraceIds = ref<string[]>([]);
const copiedDiagnosticValue = ref("");

const workspaces = ref<PageResponse<Workspace>>({ items: [], page: 1, size: 30, total: 0 });
const workspacesLoading = ref(false);
const selectedWorkspace = ref<Workspace | null>(null);
const entriesByDirectory = ref<Record<string, FileTreeEntry[]>>({});
const expandedDirectories = ref(new Set<string>());
const loadingPaths = ref(new Set<string>());
const activePath = ref("");
const fileSearchKeyword = ref("");
const fileSearchResults = ref<FileSearchResult[]>([]);
const fileSearchLoading = ref(false);
const preview = ref<FilePreviewChunk | null>(null);
const previewPath = ref("");
const previewLoading = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | undefined;

const audit = ref<PageResponse<SupportAccessAuditEvent>>({ items: [], page: 1, size: 50, total: 0 });
const auditLoading = ref(false);
const auditIncidentId = ref("");
const auditOutcome = ref("");

const actorLabel = computed(() => props.currentUser
  ? `${props.currentUser.username}（${props.currentUser.userId}）`
  : "-");
const supportActorKey = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN")
  ? props.currentUser.userId
  : "");
const remainingSeconds = computed(() => {
  if (!grant.value) return 0;
  return Math.max(0, Math.ceil((Date.parse(grant.value.expiresAt) - now.value) / 1000));
});
const countdownLabel = computed(() => {
  const seconds = remainingSeconds.value;
  const minutes = Math.floor(seconds / 60);
  return `${String(minutes).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
});
const activeGrantRef = computed(() => grant.value
  ? { grantId: grant.value.grantId, grantToken: grant.value.grantToken }
  : null);
const canBrowseSelectedWorkspace = computed(() =>
  selectedWorkspace.value?.backendAvailability === "ONLINE");
const primaryTraceId = computed(() => transcriptTraceIds.value.at(-1) ?? "");
const supportTimelineState = computed(() => {
  const state = transcriptState.value;
  return createOpencodeLikeState({
    messages: state.messages,
    permissions: state.permissions,
    questions: state.questions,
    todos: state.todos,
    todoSnapshotsByUserMessageId: state.todoSnapshotsByUserMessageId,
    diff: state.diff,
    running: false,
    status: state.status,
    runtimeStatus: state.runtimeStatus,
    streamingTextByPartId: state.streamingTextByPartId,
    messageScopesById: state.messageScopesById,
    subagentsBySessionId: state.subagentsBySessionId,
    subagentByTaskPartId: state.subagentByTaskPartId,
    runStatusesByRunId: state.runStatusesByRunId
  });
});

let clockTimer: ReturnType<typeof setInterval> | undefined;
let incidentSuggestionRequest = 0;
let userSearchRequest = 0;
let copiedDiagnosticTimer: ReturnType<typeof setTimeout> | undefined;

onMounted(() => {
  clockTimer = setInterval(() => { now.value = Date.now(); }, 1000);
  void loadUsers();
});

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer);
  if (searchTimer) clearTimeout(searchTimer);
  if (copiedDiagnosticTimer) clearTimeout(copiedDiagnosticTimer);
  const active = grant.value;
  if (active) {
    api.closeSupportAccessConnections(active.grantId);
    void api.revokeSupportAccessGrant(active.grantId, active.grantToken).catch(() => undefined);
    clearGrantState();
  }
});

watch(supportActorKey, (actorUserId, previousActorUserId) => {
  if (actorUserId === previousActorUserId) return;
  // 组件可能先于登录用户资料挂载；身份稍后到达时仍要生成本次排查单号。
  if (previousActorUserId) {
    incidentId.value = "";
    incidentPrefilled.value = false;
    incidentSuggestionSource.value = "";
  }
  if (actorUserId) void loadIncidentSuggestion();
}, { immediate: true });

watch(() => props.activationSequence, (activationSequence, previousActivationSequence) => {
  if (activationSequence === previousActivationSequence || grant.value) return;
  // 再次三击 Shift 代表一轮新的排查上下文，必须取得新的排查单号。
  void loadIncidentSuggestion();
});

watch(() => props.currentUser?.roles, (roles) => {
  if (!roles?.includes("SUPER_ADMIN") && grant.value) {
    const active = grant.value;
    api.closeSupportAccessConnections(active.grantId);
    void api.revokeSupportAccessGrant(active.grantId, active.grantToken).catch(() => undefined);
    clearGrantState();
  }
}, { deep: true });

watch(remainingSeconds, (seconds) => {
  if (grant.value && seconds === 0) {
    api.closeSupportAccessConnections(grant.value.grantId);
    clearGrantState();
    resetGrantForm();
    void loadIncidentSuggestion();
    errorMessage.value = "排查授权已到期，请重新填写排查原因并授权";
  }
});

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/** 获取权威工单建议；当前后端没有工单数据源时会返回新的唯一排查单号。 */
async function loadIncidentSuggestion() {
  if (!props.currentUser?.roles?.includes("SUPER_ADMIN")) return;
  const actorUserId = props.currentUser.userId;
  const requestId = ++incidentSuggestionRequest;
  incidentSuggestionLoading.value = true;
  try {
    const suggestion = await api.getSupportAccessIncidentSuggestion();
    const suggestedIncidentId = suggestion.incidentId?.trim() || "";
    if (requestId === incidentSuggestionRequest
      && supportActorKey.value === actorUserId) {
      incidentId.value = suggestedIncidentId;
      incidentPrefilled.value = Boolean(suggestedIncidentId);
      incidentSuggestionSource.value = suggestion.source || "GENERATED";
      if (!suggestedIncidentId) {
        errorMessage.value = "未取得可用的排查单号，请点击重新生成";
      } else if (errorMessage.value === "生成排查单号失败，请点击重试"
        || errorMessage.value === "未取得可用的排查单号，请点击重新生成") {
        errorMessage.value = "";
      }
    }
  } catch {
    if (requestId === incidentSuggestionRequest && supportActorKey.value === actorUserId) {
      incidentId.value = "";
      incidentPrefilled.value = false;
      incidentSuggestionSource.value = "";
      errorMessage.value = "生成排查单号失败，请点击重试";
    }
  } finally {
    if (requestId === incidentSuggestionRequest) incidentSuggestionLoading.value = false;
  }
}

async function issueGrant() {
  if (!incidentId.value.trim() || !reason.value.trim() || !readOnlyAcknowledged.value) {
    errorMessage.value = "请等待生成排查单号、填写排查原因并确认只读约束";
    return;
  }
  issuing.value = true;
  errorMessage.value = "";
  try {
    grant.value = await api.issueSupportAccessGrant({
      incidentId: incidentId.value.trim(),
      reason: reason.value.trim(),
      durationMinutes: durationMinutes.value,
      readOnlyAcknowledged: true
    });
    now.value = Date.now();
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    issuing.value = false;
  }
}

async function closeGrant() {
  const active = grant.value;
  if (!active || closing.value) return;
  closing.value = true;
  errorMessage.value = "";
  try {
    await api.revokeSupportAccessGrant(active.grantId, active.grantToken);
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    clearGrantState();
    resetGrantForm();
    void loadIncidentSuggestion();
    closing.value = false;
  }
}

/** 撤销或到期后清除上一轮敏感上下文，再为下一轮生成新的排查单号。 */
function resetGrantForm() {
  incidentId.value = "";
  incidentPrefilled.value = false;
  incidentSuggestionSource.value = "";
  reason.value = "";
  durationMinutes.value = 30;
  readOnlyAcknowledged.value = false;
}

function clearGrantState() {
  grant.value = null;
  targetUser.value = null;
  selectedTargetUserId.value = "";
  includeArchived.value = false;
  selectedSession.value = null;
  resetTranscriptState();
  selectedWorkspace.value = null;
  entriesByDirectory.value = {};
  expandedDirectories.value = new Set();
  preview.value = null;
  previewPath.value = "";
}

/** 顶部远程下拉复用既有用户列表 API；只保留最近一次搜索结果，并始终保留已选目标。 */
async function loadUsers(keyword = "") {
  const requestId = ++userSearchRequest;
  usersLoading.value = true;
  try {
    const page = await api.listUsers({ keyword: keyword.trim(), page: 1, size: 30 });
    if (requestId !== userSearchRequest) return;
    const selected = targetUser.value;
    users.value = selected && !page.items.some((item) => item.userId === selected.userId)
      ? [selected, ...page.items]
      : page.items;
  } catch (error) {
    if (requestId === userSearchRequest) errorMessage.value = errorText(error);
  } finally {
    if (requestId === userSearchRequest) usersLoading.value = false;
  }
}

function targetOptionLabel(user: UserManagementUser): string {
  return `${user.username} · ${user.unifiedAuthId || "无统一认证号"} · ${user.userId}`;
}

async function selectTargetById(userId: string) {
  const user = users.value.find((item) => item.userId === userId);
  if (!user || user.userId === targetUser.value?.userId) return;
  await selectTarget(user);
}

async function selectTarget(user: UserManagementUser) {
  if (!grant.value) return;
  const previousTargetUserId = targetUser.value?.userId || "";
  targetSelecting.value = true;
  errorMessage.value = "";
  try {
    await api.selectSupportAccessTarget(grant.value.grantToken, user.userId);
    targetUser.value = user;
    selectedTargetUserId.value = user.userId;
    selectedSession.value = null;
    resetTranscriptState();
    selectedWorkspace.value = null;
    entriesByDirectory.value = {};
    preview.value = null;
    await Promise.all([loadSessions(1), loadWorkspaces(1)]);
  } catch (error) {
    selectedTargetUserId.value = previousTargetUserId;
    errorMessage.value = errorText(error);
  } finally {
    targetSelecting.value = false;
  }
}

async function loadSessions(page = sessions.value.page) {
  if (!grant.value || !targetUser.value) return;
  sessionsLoading.value = true;
  try {
    sessions.value = await api.listSupportAccessSessions(
      grant.value.grantToken,
      targetUser.value.userId,
      {
        q: sessionQuery.value.trim() || undefined,
        includeArchived: includeArchived.value,
        page,
        size: 30
      }
    );
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    sessionsLoading.value = false;
  }
}

async function openSession(session: Session) {
  if (!grant.value || !targetUser.value) return;
  selectedSession.value = session;
  transcriptLoading.value = true;
  resetTranscriptState();
  try {
    const snapshot = await api.getSupportAccessSessionTreeMessages(
      grant.value.grantToken,
      targetUser.value.userId,
      session.sessionId,
      session.status === "ARCHIVED"
    );
    // 与普通用户首页复用同一 Session tree reducer，保留 message part、工具、Todo 和子 Agent 投影。
    transcriptState.value = chatStateFromSessionTreeSnapshot(snapshot);
    transcriptTraceIds.value = traceIdsFromSnapshot(snapshot);
    historyRepresentation.value = snapshot.historyRepresentation ?? null;
    replayAvailable.value = snapshot.replayAvailable ?? null;
    detailsAvailableUntil.value = snapshot.detailsAvailableUntil ?? null;
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    transcriptLoading.value = false;
  }
}

async function loadWorkspaces(page = workspaces.value.page) {
  if (!grant.value || !targetUser.value) return;
  workspacesLoading.value = true;
  try {
    workspaces.value = await api.listSupportAccessWorkspaces(
      grant.value.grantToken,
      targetUser.value.userId,
      page,
      30
    );
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    workspacesLoading.value = false;
  }
}

async function selectWorkspace(workspace: Workspace) {
  if (workspace.backendAvailability !== "ONLINE") {
    errorMessage.value = workspace.backendAvailability === "OFFLINE"
      ? "目标服务器后端离线，当前不能读取该工作区文件"
      : workspace.backendAvailability === "UNBOUND"
        ? "工作区未绑定目标服务器，当前不能读取文件"
        : "目标服务器状态未知，当前不能读取该工作区文件";
    return;
  }
  selectedWorkspace.value = workspace;
  entriesByDirectory.value = {};
  expandedDirectories.value = new Set();
  preview.value = null;
  previewPath.value = "";
  await loadDirectory("");
}

async function loadDirectory(path: string) {
  if (!activeGrantRef.value || !targetUser.value || !selectedWorkspace.value || !canBrowseSelectedWorkspace.value) return;
  loadingPaths.value = new Set(loadingPaths.value).add(path);
  try {
    const entries = await api.listSupportWorkspaceFiles(
      activeGrantRef.value,
      targetUser.value.userId,
      selectedWorkspace.value.workspaceId,
      path
    );
    entriesByDirectory.value = { ...entriesByDirectory.value, [path]: entries };
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    const next = new Set(loadingPaths.value);
    next.delete(path);
    loadingPaths.value = next;
  }
}

async function toggleDirectory(path: string) {
  const next = new Set(expandedDirectories.value);
  if (next.has(path)) {
    next.delete(path);
  } else {
    if (!entriesByDirectory.value[path]) await loadDirectory(path);
    next.add(path);
  }
  expandedDirectories.value = next;
}

async function openFile(path: string) {
  if (!activeGrantRef.value || !targetUser.value || !selectedWorkspace.value) return;
  activePath.value = path;
  previewPath.value = path;
  previewLoading.value = true;
  preview.value = null;
  try {
    preview.value = await api.readSupportWorkspaceFilePreviewChunk(
      activeGrantRef.value,
      targetUser.value.userId,
      selectedWorkspace.value.workspaceId,
      path,
      { offset: 0 }
    );
  } catch (error) {
    errorMessage.value = `无法预览 ${path}：${errorText(error)}`;
  } finally {
    previewLoading.value = false;
  }
}

async function loadMorePreview() {
  if (!activeGrantRef.value || !targetUser.value || !selectedWorkspace.value || !preview.value || preview.value.eof) return;
  previewLoading.value = true;
  try {
    const next = await api.readSupportWorkspaceFilePreviewChunk(
      activeGrantRef.value,
      targetUser.value.userId,
      selectedWorkspace.value.workspaceId,
      previewPath.value,
      {
        offset: preview.value.nextOffset,
        expectedSize: preview.value.size,
        expectedLastModifiedMillis: preview.value.lastModifiedMillis
      }
    );
    preview.value = { ...next, content: preview.value.content + next.content };
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    previewLoading.value = false;
  }
}

function searchFiles(keyword: string) {
  fileSearchKeyword.value = keyword;
  if (searchTimer) clearTimeout(searchTimer);
  if (!keyword.trim()) {
    fileSearchResults.value = [];
    return;
  }
  searchTimer = setTimeout(() => void runFileSearch(), 250);
}

async function runFileSearch() {
  if (!activeGrantRef.value || !targetUser.value || !selectedWorkspace.value || !fileSearchKeyword.value.trim()) return;
  fileSearchLoading.value = true;
  try {
    fileSearchResults.value = await api.searchSupportWorkspaceFiles(
      activeGrantRef.value,
      targetUser.value.userId,
      selectedWorkspace.value.workspaceId,
      fileSearchKeyword.value.trim()
    );
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    fileSearchLoading.value = false;
  }
}

async function loadAudit(page = audit.value.page) {
  auditLoading.value = true;
  errorMessage.value = "";
  try {
    audit.value = await api.listSupportAccessAuditEvents({
      incidentId: auditIncidentId.value.trim() || undefined,
      outcome: auditOutcome.value || undefined,
      page,
      size: 50
    });
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    auditLoading.value = false;
  }
}

function formatTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function historyRepresentationLabel(value?: string | null): string {
  if (value === "FULL") return "完整历史";
  if (value === "SUMMARY") return "摘要";
  if (value === "LEGACY") return "历史原文（旧存储）";
  return value || "-";
}

function backendAvailabilityLabel(value?: string | null): string {
  if (value === "ONLINE") return "在线";
  if (value === "OFFLINE") return "离线";
  if (value === "UNBOUND") return "未绑定";
  return "状态未知";
}

/** 同一会话可能跨多轮 Run；保留事件中的全部唯一 traceId，并把最后一个作为最近排查线索。 */
function traceIdsFromSnapshot(snapshot: SessionTreeMessagesResponse): string[] {
  return [...new Set(snapshot.events
    .map((event) => event.traceId?.trim())
    .filter((traceId): traceId is string => Boolean(traceId)))];
}

async function copyDiagnosticValue(value: string) {
  if (!value || !await copyTextToClipboard(value)) return;
  copiedDiagnosticValue.value = value;
  if (copiedDiagnosticTimer) clearTimeout(copiedDiagnosticTimer);
  copiedDiagnosticTimer = setTimeout(() => {
    if (copiedDiagnosticValue.value === value) copiedDiagnosticValue.value = "";
  }, 1600);
}

function reloadSessionsWithArchiveFilter() {
  selectedSession.value = null;
  resetTranscriptState();
  historyRepresentation.value = null;
  replayAvailable.value = null;
  detailsAvailableUntil.value = null;
  void loadSessions(1);
}

function resetTranscriptState() {
  transcriptState.value = createInitialAgentChatRuntimeState();
  transcriptTraceIds.value = [];
  copiedDiagnosticValue.value = "";
}
</script>

<template>
  <section class="support-panel">
    <header class="support-header">
      <div class="support-heading">
        <span class="support-kicker">只读排查控制台</span>
        <h2>问题排查只读访问</h2>
        <p>保持管理员身份，用用户视角核对会话、Trace 和工作区状态。</p>
      </div>
      <div class="support-tabs">
        <button :class="{ active: mainTab === 'access' }" @click="mainTab = 'access'">排查访问</button>
        <button :class="{ active: mainTab === 'audit' }" @click="mainTab = 'audit'; loadAudit(1)">审计记录</button>
      </div>
    </header>

    <div v-if="errorMessage" class="support-error">{{ errorMessage }}</div>

    <template v-if="mainTab === 'access'">
      <div v-if="!grant" class="grant-form">
        <div class="grant-notice">
          <span class="notice-mark">READ ONLY</span>
          <div><strong>先建立一次限时排查上下文</strong>
            <span>入口快捷键不是认证因素；授权依赖当前 SUPER_ADMIN、有效登录会话和排查单号。</span>
          </div>
        </div>
        <div class="grant-fields">
          <label class="form-field">当前操作人<input :value="actorLabel" disabled /></label>
          <div class="form-field incident-field">
            <span class="field-label"><label for="support-incident-id">排查单号</label>
              <button type="button" :disabled="incidentSuggestionLoading" @click="loadIncidentSuggestion">
                {{ incidentSuggestionLoading ? '生成中…' : '生成新排查单号' }}
              </button>
            </span>
            <input id="support-incident-id" v-model="incidentId" maxlength="128" placeholder="正在生成…" readonly />
            <small v-if="incidentPrefilled">
              {{ incidentSuggestionSource === 'WORK_ORDER' ? '已读取当前有效工单' : '系统已生成唯一排查单号；重新生成后号码会变化' }}
            </small>
          </div>
          <label class="form-field form-field-wide">排查原因<textarea v-model="reason" maxlength="1000" rows="4" placeholder="描述故障、影响范围和需要核对的内容" /></label>
          <label class="form-field duration-field">授权时长
            <select v-model.number="durationMinutes">
              <option :value="5">5 分钟</option><option :value="15">15 分钟</option>
              <option :value="30">30 分钟</option><option :value="60">60 分钟</option>
              <option :value="120">120 分钟</option><option :value="240">240 分钟</option>
            </select>
          </label>
        </div>
        <label class="grant-check"><input v-model="readOnlyAcknowledged" type="checkbox" />
          <span>我确认仅用于问题排查，不写入、不上传、不进入终端/Agent 配置，也不批量导出。</span>
        </label>
        <div class="grant-actions">
          <button class="primary" :disabled="issuing || incidentSuggestionLoading || !incidentId" @click="issueGrant">{{ issuing ? '授权中…' : '开启限时只读访问' }}</button>
          <span>授权到期或离开页面后自动撤销。</span>
        </div>
      </div>

      <template v-else>
        <div class="access-banner">
          <span><small>操作人</small><b>{{ props.currentUser?.username || '-' }}</b></span>
          <div class="target-select" data-testid="support-target-select">
            <small>目标用户</small>
            <el-select
              v-model="selectedTargetUserId"
              aria-label="选择目标用户"
              class="target-select-control"
              filterable
              remote
              :remote-method="loadUsers"
              :loading="usersLoading || targetSelecting"
              :disabled="targetSelecting"
              placeholder="输入姓名、用户 ID 或统一认证号"
              @change="selectTargetById"
            >
              <el-option
                v-for="user in users"
                :key="user.userId"
                :label="targetOptionLabel(user)"
                :value="user.userId"
              >
                <div class="target-option">
                  <strong>{{ user.username }}</strong>
                  <span>{{ user.unifiedAuthId || '无统一认证号' }}</span>
                  <code>{{ user.userId }}</code>
                </div>
              </el-option>
            </el-select>
          </div>
          <span><small>排查单号</small><b>{{ incidentId }}</b></span>
          <span><small>剩余时间</small><b class="mono-value">{{ countdownLabel }}</b></span>
          <span class="readonly-pill">只读</span>
          <button :disabled="closing" @click="closeGrant">{{ closing ? '关闭中…' : '关闭并撤销' }}</button>
        </div>

        <div class="support-layout">
          <main class="resource-panel">
            <div v-if="!targetUser" class="empty large">选择一个目标用户后才能读取其关联会话和工作区。</div>
            <template v-else>
              <div class="resource-tabs">
                <button :class="{ active: resourceTab === 'sessions' }" @click="resourceTab = 'sessions'">会话（{{ sessions.total }}）</button>
                <button :class="{ active: resourceTab === 'workspaces' }" @click="resourceTab = 'workspaces'">工作区（{{ workspaces.total }}）</button>
              </div>

              <div v-if="resourceTab === 'sessions'" class="session-layout">
                <section class="resource-list">
                  <div class="inline-search"><input v-model="sessionQuery" placeholder="标题或 Session ID" @keyup.enter="loadSessions(1)" /><button @click="loadSessions(1)">查询</button></div>
                  <label class="archive-filter">
                    <input v-model="includeArchived" type="checkbox" @change="reloadSessionsWithArchiveFilter" />
                    <span>包含已归档会话（用户已删除或隐藏，数据未物理删除）</span>
                  </label>
                  <div v-if="sessionsLoading" class="empty">加载中…</div>
                  <button v-for="session in sessions.items" :key="session.sessionId" :class="['resource-row', { active: selectedSession?.sessionId === session.sessionId }]" @click="openSession(session)">
                    <strong>{{ session.title }} <em v-if="session.status === 'ARCHIVED'" class="archived-pill">已归档</em></strong><span>{{ session.sessionId }}</span><small>{{ formatTime(session.updatedAt) }}</small>
                  </button>
                  <div class="pager"><button :disabled="sessions.page <= 1" @click="loadSessions(sessions.page - 1)">上一页</button><span>{{ sessions.page }}</span><button :disabled="sessions.page * sessions.size >= sessions.total" @click="loadSessions(sessions.page + 1)">下一页</button></div>
                </section>
                <section class="transcript" data-testid="support-user-conversation-view">
                  <div v-if="!selectedSession" class="empty large">选择会话查看可恢复消息。</div>
                  <template v-else>
                    <div class="user-view-heading">
                      <div><span class="section-kicker">CONVERSATION</span><strong>用户首页视角</strong></div>
                      <span>与首页使用同一消息投影和时间线，不切换管理员身份。</span>
                    </div>
                    <div class="diagnostic-context" aria-label="会话排查标识" data-testid="support-session-diagnostics">
                      <div class="diagnostic-heading">
                        <strong>排查标识</strong>
                        <span>始终显示，用于关联服务日志</span>
                      </div>
                      <div class="diagnostic-id">
                        <span>会话 SESSION ID</span>
                        <code :title="selectedSession.sessionId">{{ selectedSession.sessionId }}</code>
                        <button type="button" @click="copyDiagnosticValue(selectedSession.sessionId)">
                          {{ copiedDiagnosticValue === selectedSession.sessionId ? '已复制' : '复制' }}
                        </button>
                      </div>
                      <div class="diagnostic-id">
                        <span>最近 TRACE ID</span>
                        <code :class="{ muted: !primaryTraceId }" :title="primaryTraceId || undefined">{{ primaryTraceId || '暂无可恢复 Trace' }}</code>
                        <button v-if="primaryTraceId" type="button" @click="copyDiagnosticValue(primaryTraceId)">
                          {{ copiedDiagnosticValue === primaryTraceId ? '已复制' : '复制' }}
                        </button>
                      </div>
                      <details v-if="transcriptTraceIds.length > 1" class="trace-list">
                        <summary>全部 {{ transcriptTraceIds.length }} 个 Trace</summary>
                        <div v-for="traceId in transcriptTraceIds" :key="traceId">
                          <code>{{ traceId }}</code>
                          <button type="button" @click="copyDiagnosticValue(traceId)">{{ copiedDiagnosticValue === traceId ? '已复制' : '复制' }}</button>
                        </div>
                      </details>
                      <div class="diagnostic-status">
                        <span><small>历史来源</small><b>{{ historyRepresentationLabel(historyRepresentation) }}</b></span>
                        <span><small>事件回放</small><b>{{ replayAvailable === true ? '可用' : replayAvailable === false ? '不可用' : '-' }}</b></span>
                        <span><small>详情保留至</small><b>{{ formatTime(detailsAvailableUntil) }}</b></span>
                      </div>
                    </div>
                    <div v-if="transcriptLoading" class="empty">恢复中…</div>
                    <div v-else-if="transcript.length" class="support-timeline">
                      <OpencodeTimeline :state="supportTimelineState" />
                    </div>
                    <div v-if="!transcriptLoading && transcript.length === 0" class="empty">当前保留链路没有可恢复正文。</div>
                    <div class="readonly-composer" aria-label="只读排查输入区">
                      <textarea disabled rows="1" value="只读排查视角，不能发送消息、调用工具或修改用户数据。" />
                    </div>
                  </template>
                </section>
              </div>

              <div v-else class="workspace-layout">
                <section class="resource-list">
                  <div v-if="workspacesLoading" class="empty">加载中…</div>
                  <button v-for="workspace in workspaces.items" :key="workspace.workspaceId" :class="['resource-row', { active: selectedWorkspace?.workspaceId === workspace.workspaceId }]" :disabled="workspace.backendAvailability !== 'ONLINE'" @click="selectWorkspace(workspace)">
                    <strong>{{ workspace.name }}</strong><span>{{ workspace.workspaceId }}</span>
                    <small><em :class="['backend-pill', `is-${(workspace.backendAvailability || 'UNKNOWN').toLowerCase()}`]">{{ backendAvailabilityLabel(workspace.backendAvailability) }}</em> {{ workspace.linuxServerId || '未绑定服务器' }}</small>
                  </button>
                  <div class="pager"><button :disabled="workspaces.page <= 1" @click="loadWorkspaces(workspaces.page - 1)">上一页</button><span>{{ workspaces.page }}</span><button :disabled="workspaces.page * workspaces.size >= workspaces.total" @click="loadWorkspaces(workspaces.page + 1)">下一页</button></div>
                </section>
                <section v-if="selectedWorkspace && canBrowseSelectedWorkspace" class="file-tree">
                  <FileExplorer
                    :workspace-name="selectedWorkspace.name"
                    :entries-by-directory="entriesByDirectory"
                    :expanded-directories="expandedDirectories"
                    :active-path="activePath"
                    :loading-path="loadingPaths"
                    :changed-files="[]"
                    :can-write="false"
                    :can-attach="false"
                    :can-download="false"
                    :search-results="fileSearchResults"
                    :search-loading="fileSearchLoading"
                    :search-keyword="fileSearchKeyword"
                    @toggle-directory="toggleDirectory"
                    @open-file="openFile"
                    @refresh="loadDirectory('')"
                    @search="searchFiles"
                  />
                </section>
                <section class="file-preview">
                  <div v-if="!selectedWorkspace" class="empty large">选择工作区后通过权威服务器只读浏览文件。</div>
                  <div v-else-if="!canBrowseSelectedWorkspace" class="empty large">目标服务器当前不可用，文件读取已禁用；不会切换到本机或其它服务器。</div>
                  <div v-else-if="!previewPath" class="empty large">选择文件查看文本预览；下载与加入对话均已关闭。</div>
                  <template v-else>
                    <header><span>{{ previewPath }}</span><small v-if="preview">{{ preview.content.length }} / {{ preview.size }} bytes</small></header>
                    <div v-if="previewLoading && !preview" class="empty">读取中…</div>
                    <pre v-else>{{ preview?.content || '' }}</pre>
                    <button v-if="preview && !preview.eof" :disabled="previewLoading" @click="loadMorePreview">{{ previewLoading ? '读取中…' : '继续读取' }}</button>
                  </template>
                </section>
              </div>
            </template>
          </main>
        </div>
      </template>
    </template>

    <section v-else class="audit-panel">
      <div class="audit-filter">
        <input v-model="auditIncidentId" placeholder="排查单号" @keyup.enter="loadAudit(1)" />
        <select v-model="auditOutcome"><option value="">全部结果</option><option value="SUCCESS">SUCCESS</option><option value="FAILED">FAILED</option></select>
        <button @click="loadAudit(1)">查询</button>
      </div>
      <div v-if="auditLoading" class="empty">加载中…</div>
      <div class="audit-table-wrap">
        <table>
          <thead><tr><th>时间</th><th>操作人</th><th>目标</th><th>排查单号</th><th>动作</th><th>资源</th><th>结果</th><th>Trace</th></tr></thead>
          <tbody><tr v-for="event in audit.items" :key="event.eventId"><td>{{ formatTime(event.occurredAt) }}</td><td>{{ event.actorUsername }}<br><small>{{ event.actorUserId || '-' }}</small></td><td>{{ event.targetUsername || '-' }}<br><small>{{ event.targetUserId || '-' }}</small></td><td>{{ event.incidentId || '-' }}</td><td>{{ event.action }}</td><td>{{ event.resourceType }}<br><small>{{ event.resourceId || event.pathDigest || '-' }}</small></td><td :class="event.outcome === 'SUCCESS' ? 'ok' : 'fail'">{{ event.outcome }}<br><small>{{ event.errorCode || '' }}</small></td><td><small>{{ event.traceId }}</small></td></tr></tbody>
        </table>
      </div>
      <div class="pager"><button :disabled="audit.page <= 1" @click="loadAudit(audit.page - 1)">上一页</button><span>{{ audit.page }}</span><button :disabled="audit.page * audit.size >= audit.total" @click="loadAudit(audit.page + 1)">下一页</button></div>
    </section>
  </section>
</template>

<style scoped>
.support-panel {
  --support-accent: var(--ta-shell-accent, #c8161d);
  --support-accent-strong: var(--ta-shell-accent-strong, #991b1b);
  --support-accent-soft: var(--ta-shell-accent-soft, #fdf2f2);
  --support-border: var(--ta-shell-border, #e5e7eb);
  --support-border-strong: var(--ta-shell-border-strong, #d1d5db);
  --support-ink: var(--ta-shell-text, #1f2937);
  --support-muted: var(--ta-shell-muted, #6b7280);
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  background: var(--ta-shell-canvas, #f0f4fa);
  color: var(--support-ink);
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.45;
}

.support-header {
  display: flex;
  min-height: 62px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  box-sizing: border-box;
  padding: 10px 20px 9px;
  border-bottom: 1px solid var(--support-border);
  background: #fff;
}
.support-heading { min-width: 0; }
.support-kicker,
.section-kicker {
  display: block;
  color: var(--support-accent);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: .13em;
}
.support-header h2 { margin: 1px 0 0; font-size: 15px; font-weight: 680; letter-spacing: .01em; }
.support-header p { margin: 1px 0 0; color: var(--support-muted); font-size: 12px; }
.support-tabs,
.resource-tabs { display: flex; align-items: center; gap: 2px; }
.support-tabs button,
.resource-tabs button {
  min-height: 34px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  padding: 7px 11px;
  color: var(--support-muted);
  cursor: pointer;
  font: 600 13px/1 var(--font-sans);
}
.support-tabs button:hover,
.resource-tabs button:hover { color: var(--support-ink); }
.support-tabs button.active,
.resource-tabs button.active { border-color: var(--support-accent); color: var(--support-accent-strong); }

.support-error {
  margin: 10px 16px 0;
  padding: 9px 12px;
  border: 1px solid #f3b8b8;
  border-radius: 6px;
  background: #fff1f1;
  color: #9f2d2d;
  font-size: 13px;
}
.grant-form {
  display: grid;
  width: min(760px, calc(100% - 40px));
  margin: 22px auto;
  padding: 0 22px 22px;
  gap: 16px;
  box-sizing: border-box;
  border: 1px solid var(--support-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--ta-shell-shadow, 0 1px 2px rgb(15 23 42 / 5%));
  overflow: hidden;
}
.grant-notice {
  display: grid;
  grid-template-columns: 74px 1fr;
  align-items: center;
  gap: 14px;
  margin: 0 -22px;
  padding: 15px 22px;
  border-bottom: 1px solid #f2d8d9;
  background: var(--support-accent-soft);
}
.notice-mark {
  color: var(--support-accent-strong);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: .1em;
}
.grant-notice strong { display: block; margin-bottom: 2px; font-size: 14px; }
.grant-notice div > span { display: block; color: #6f5658; font-size: 12px; }
.grant-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 18px; }
.form-field { display: grid; align-content: start; gap: 6px; color: #374151; font-size: 13px; font-weight: 650; }
.form-field-wide { grid-column: 1 / -1; }
.duration-field { max-width: 220px; }
.field-label { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.field-label label { font-weight: 650; }
.field-label button {
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--support-accent-strong);
  cursor: pointer;
  font: 600 12px/1.2 var(--font-sans);
}
.form-field small { color: var(--ta-ok, #3f7a5a); font-size: 11px; font-weight: 500; }
.grant-form input:not([type="checkbox"]),
.grant-form textarea,
.grant-form select,
.inline-search input,
.audit-filter input,
.audit-filter select {
  box-sizing: border-box;
  min-height: 36px;
  border: 1px solid var(--support-border-strong);
  border-radius: 6px;
  background: #fff;
  color: inherit;
  padding: 8px 10px;
  font: 14px/1.4 var(--font-sans);
}
.grant-form input:not([type="checkbox"]):focus,
.grant-form textarea:focus,
.grant-form select:focus,
.inline-search input:focus,
.audit-filter input:focus,
.audit-filter select:focus {
  border-color: #d1777b;
  outline: 2px solid #f8dfe0;
  outline-offset: 1px;
}
.grant-form input:not([type="checkbox"]):disabled,
.grant-form input:not([type="checkbox"])[readonly] { background: #f7f8fa; color: #4b5563; }
.grant-form textarea { min-height: 96px; resize: vertical; }
.grant-check { display: grid; grid-template-columns: 16px minmax(0, 1fr); align-items: start; gap: 8px; color: #4b5563; font-size: 13px; line-height: 1.5; }
.grant-check input,
.archive-filter input { width: 14px; height: 14px; min-height: 0; margin: 2px 0 0; padding: 0; accent-color: var(--support-accent); }
.grant-actions { display: flex; align-items: center; gap: 12px; }
.grant-actions > span { color: var(--support-muted); font-size: 12px; }
button.primary {
  min-height: 36px;
  border: 1px solid var(--support-accent-strong);
  border-radius: 6px;
  background: var(--support-accent);
  color: #fff;
  padding: 8px 15px;
  cursor: pointer;
  font: 650 13px/1 var(--font-sans);
}
button.primary:hover:not(:disabled) { background: var(--support-accent-strong); }
.support-panel button:disabled { cursor: not-allowed !important; opacity: .5; }

.access-banner {
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  padding: 9px 16px;
  border-bottom: 1px solid var(--support-border);
  background: #fff;
  box-shadow: inset 3px 0 0 var(--support-accent);
}
.access-banner > span:not(.readonly-pill) { display: grid; gap: 1px; }
.access-banner small { color: var(--support-muted); font-size: 10px; letter-spacing: .04em; }
.access-banner b { max-width: 230px; overflow: hidden; color: #263244; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.access-banner > button { margin-left: auto; }
.target-select { display: grid; min-width: 300px; max-width: 560px; flex: 1 1 420px; gap: 2px; }
.target-select-control { width: 100%; }
.target-select :deep(.el-select__wrapper) { min-height: 34px; border-radius: 6px; box-shadow: 0 0 0 1px var(--support-border-strong) inset; }
.target-select :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px #d1777b inset, 0 0 0 2px #f8dfe0; }
.target-select :deep(.el-select__selected-item) { font: 12px/1.4 var(--font-sans); }
.target-option { display: grid; grid-template-columns: minmax(80px, .8fr) minmax(100px, 1fr) minmax(160px, 1.5fr); align-items: center; gap: 10px; width: 100%; }
.target-option strong { overflow: hidden; color: #273244; text-overflow: ellipsis; white-space: nowrap; }
.target-option span { overflow: hidden; color: var(--support-muted); text-overflow: ellipsis; white-space: nowrap; }
.target-option code { overflow: hidden; color: #526176; font: 11px/1.4 var(--font-mono); text-overflow: ellipsis; white-space: nowrap; }
.mono-value { font-family: var(--font-mono); }
.readonly-pill { border: 1px solid #d69092; border-radius: 999px; background: var(--support-accent-soft); padding: 2px 8px; color: var(--support-accent-strong); font-size: 11px; font-weight: 700; }

.support-layout { display: flex; min-height: 0; flex: 1; }
.inline-search { display: flex; gap: 6px; margin-bottom: 10px; }
.inline-search input { min-width: 0; flex: 1; font-size: 13px; }
.inline-search button,
.pager button,
.audit-filter button,
.access-banner > button,
.file-preview button,
.diagnostic-id button,
.trace-list button {
  min-height: 30px;
  border: 1px solid var(--support-border-strong);
  border-radius: 5px;
  background: #fff;
  color: #4b5563;
  padding: 5px 9px;
  cursor: pointer;
  font: 600 12px/1 var(--font-sans);
}
.inline-search button:hover,
.pager button:hover:not(:disabled),
.audit-filter button:hover,
.access-banner > button:hover,
.file-preview button:hover,
.diagnostic-id button:hover,
.trace-list button:hover { border-color: #d69092; color: var(--support-accent-strong); }
.resource-row {
  display: grid;
  width: 100%;
  gap: 3px;
  box-sizing: border-box;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 0 5px 5px 0;
  background: transparent;
  padding: 8px 9px;
  text-align: left;
  cursor: pointer;
  font-family: var(--font-sans);
}
.resource-row:hover { background: #f7f8fa; }
.resource-row.active { border-left-color: var(--support-accent); background: var(--support-accent-soft); }
.resource-row strong { color: #283446; font-size: 13px; font-weight: 650; }
.resource-row span,
.resource-row small { overflow: hidden; color: var(--support-muted); text-overflow: ellipsis; white-space: nowrap; }
.resource-row span { font: 11px/1.35 var(--font-mono); }
.resource-row small { font-size: 11px; }
.archive-filter { display: grid; grid-template-columns: 16px minmax(0, 1fr); align-items: start; gap: 7px; margin: 2px 4px 10px; color: var(--support-muted); font-size: 12px; line-height: 1.4; }
.archived-pill,
.backend-pill { display: inline-block; border-radius: 999px; padding: 1px 6px; font-style: normal; font-size: 10px; font-weight: 700; }
.archived-pill { border: 1px solid #c7cdd5; background: #f2f4f6; color: #66707d; }
.backend-pill.is-online { background: #e7f7ee; color: #177245; }
.backend-pill.is-offline { background: #fff0f0; color: #a52b2b; }
.backend-pill.is-unbound,
.backend-pill.is-unknown { background: #f2f4f6; color: #66707d; }

.resource-panel { display: flex; width: 100%; min-width: 0; min-height: 0; flex-direction: column; }
.resource-tabs { flex: 0 0 auto; padding: 5px 12px 0; border-bottom: 1px solid var(--support-border); background: #fff; }
.session-layout { display: grid; grid-template-columns: 310px 1fr; min-height: 0; flex: 1; }
.workspace-layout { display: grid; grid-template-columns: 280px 330px 1fr; min-height: 0; flex: 1; }
.resource-list { min-height: 0; overflow: auto; padding: 10px; border-right: 1px solid var(--support-border); background: #fff; }
.pager { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 10px 0; color: var(--support-muted); font: 11px/1 var(--font-mono); }

.transcript { display: flex; min-height: 0; overflow: auto; flex-direction: column; padding: 16px 20px; background: var(--ta-chat-bg, #f5f5f5); }
.user-view-heading { display: flex; flex: 0 0 auto; align-items: flex-end; justify-content: space-between; gap: 16px; width: min(100%, 920px); margin: 0 auto 10px; }
.user-view-heading > div { display: grid; gap: 1px; }
.user-view-heading strong { color: #273244; font-size: 15px; font-weight: 680; }
.user-view-heading > span { color: var(--support-muted); font-size: 12px; text-align: right; }
.diagnostic-context {
  position: sticky;
  top: 0;
  z-index: 4;
  display: grid;
  flex: 0 0 auto;
  width: min(100%, 920px);
  margin: 0 auto 12px;
  box-sizing: border-box;
  border: 1px solid var(--support-border);
  border-top: 2px solid var(--support-accent);
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
  overflow: hidden;
}
.diagnostic-heading { display: flex; align-items: baseline; gap: 10px; padding: 8px 11px; border-bottom: 1px solid #f1d6d7; background: var(--support-accent-soft); }
.diagnostic-heading strong { color: var(--support-accent-strong); font-size: 13px; font-weight: 700; }
.diagnostic-heading span { color: #76595b; font-size: 12px; }
.diagnostic-id { display: grid; grid-template-columns: 132px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 42px; padding: 0 11px; border-bottom: 1px solid #edf0f3; }
.diagnostic-id > span { color: #4b5563; font: 700 11px/1 var(--font-sans); letter-spacing: .02em; }
.diagnostic-id code,
.trace-list code { overflow: hidden; color: #27364b; font: 600 13px/1.4 var(--font-mono); text-overflow: ellipsis; white-space: nowrap; }
.diagnostic-id code.muted { color: #9ca3af; font-family: var(--font-sans); }
.diagnostic-id button,
.trace-list button { min-height: 24px; padding: 4px 7px; font-size: 11px; }
.trace-list { border-bottom: 1px solid #edf0f3; }
.trace-list summary { padding: 8px 11px; color: var(--support-muted); cursor: pointer; font-size: 11px; }
.trace-list > div { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 6px 11px 6px 143px; border-top: 1px solid #f2f4f6; }
.diagnostic-status { display: flex; align-items: stretch; gap: 0; }
.diagnostic-status span { display: grid; min-width: 140px; gap: 2px; padding: 9px 11px; border-right: 1px solid #edf0f3; }
.diagnostic-status small { color: var(--support-muted); font-size: 10px; }
.diagnostic-status b { color: #334155; font-size: 12px; font-weight: 650; }
.support-timeline { width: min(100%, 920px); flex: 0 0 auto; margin: 0 auto; padding: 4px 8px 18px; }
.readonly-composer { position: sticky; bottom: -16px; width: min(100%, 920px); flex: 0 0 auto; margin: auto auto 0; padding: 12px 0 2px; background: linear-gradient(to bottom, transparent, var(--ta-chat-bg, #f5f5f5) 24%); }
.readonly-composer textarea { width: 100%; box-sizing: border-box; resize: none; border: 1px solid var(--support-border-strong); border-radius: 8px; padding: 10px 12px; background: #eef0f3; color: var(--support-muted); font: 13px/1.4 var(--font-sans); }

.file-preview pre { margin: 0; padding: 10px 12px; overflow: auto; white-space: pre-wrap; word-break: break-word; font: 12px/1.55 var(--font-mono); }
.file-tree { min-height: 0; overflow: hidden; border-right: 1px solid var(--support-border); background: #fff; --ta-tree-bg: #fff; --ta-tree-text: #2c333a; --ta-tree-muted: #707985; --ta-tree-border-strong: #dfe3e8; }
.file-preview { min-width: 0; min-height: 0; overflow: auto; background: #fff; }
.file-preview header { display: flex; justify-content: space-between; gap: 12px; padding: 9px 12px; border-bottom: 1px solid var(--support-border); }
.file-preview pre { min-height: calc(100% - 76px); white-space: pre; }
.file-preview > button { margin: 8px 12px 14px; }
.empty { padding: 16px; color: #78818d; text-align: center; }
.empty.large { display: grid; min-height: 180px; place-items: center; }

.audit-panel { min-height: 0; overflow: auto; padding: 14px 18px; }
.audit-filter { display: flex; gap: 8px; margin-bottom: 12px; }
.audit-table-wrap { overflow: auto; border: 1px solid var(--support-border); border-radius: 6px; background: #fff; }
.audit-table-wrap table { width: 100%; border-collapse: collapse; white-space: nowrap; font-size: 12px; }
.audit-table-wrap th,
.audit-table-wrap td { padding: 8px 10px; border-bottom: 1px solid #e8ebef; text-align: left; vertical-align: top; }
.audit-table-wrap th { position: sticky; top: 0; background: #f5f6f8; color: #4b5563; font-weight: 650; }
.audit-table-wrap small { color: var(--support-muted); font-family: var(--font-mono); font-size: 10px; }
.ok { color: #177245; }
.fail { color: #a52b2b; }

@media (max-width: 1100px) {
  .workspace-layout { grid-template-columns: 230px 280px 1fr; }
  .session-layout { grid-template-columns: 270px 1fr; }
  .diagnostic-status span { min-width: 0; flex: 1; }
}
@media (max-width: 820px) {
  .support-header { align-items: flex-start; flex-direction: column; gap: 8px; }
  .grant-fields { grid-template-columns: 1fr; }
  .form-field-wide { grid-column: auto; }
  .session-layout { grid-template-columns: 240px 1fr; }
  .target-select { min-width: 240px; max-width: none; flex-basis: 100%; }
  .user-view-heading { align-items: flex-start; flex-direction: column; gap: 4px; }
  .user-view-heading > span { text-align: left; }
  .diagnostic-id { grid-template-columns: 1fr auto; padding: 8px 10px; }
  .diagnostic-id > span { grid-column: 1 / -1; }
  .trace-list > div { padding-left: 10px; }
}
</style>
