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
import type {
  CurrentUser,
  FilePreviewChunk,
  FileSearchResult,
  FileTreeEntry,
  PageResponse,
  Session,
  SupportAccessAuditEvent,
  SupportAccessGrant,
  UserManagementUser,
  Workspace
} from "@test-agent/shared-types";
import { chatStateFromSessionTreeSnapshot } from "../workbench-utils";

const props = defineProps<{ currentUser: CurrentUser | null }>();
const api = inject<BackendApiClient>("api")!;

type MainTab = "access" | "audit";
type ResourceTab = "sessions" | "workspaces";

const mainTab = ref<MainTab>("access");
const resourceTab = ref<ResourceTab>("sessions");
const grant = ref<SupportAccessGrant | null>(null);
const incidentId = ref("");
const reason = ref("");
const durationMinutes = ref(30);
const readOnlyAcknowledged = ref(false);
const issuing = ref(false);
const closing = ref(false);
const errorMessage = ref("");
const now = ref(Date.now());

const userKeyword = ref("");
const users = ref<UserManagementUser[]>([]);
const usersLoading = ref(false);
const targetUser = ref<UserManagementUser | null>(null);
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

onMounted(() => {
  clockTimer = setInterval(() => { now.value = Date.now(); }, 1000);
  void prefillRecentIncident();
  void loadUsers();
});

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer);
  if (searchTimer) clearTimeout(searchTimer);
  const active = grant.value;
  if (active) {
    api.closeSupportAccessConnections(active.grantId);
    void api.revokeSupportAccessGrant(active.grantId, active.grantToken).catch(() => undefined);
    clearGrantState();
  }
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
    errorMessage.value = "排查授权已到期，请重新填写工单信息并授权";
  }
});

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/**
 * 只回填当前管理员最近一次已入库工单；请求返回前已有人工输入时不得覆盖。
 */
async function prefillRecentIncident() {
  if (!props.currentUser?.roles?.includes("SUPER_ADMIN")) return;
  const actorUserId = props.currentUser.userId;
  try {
    const suggestion = await api.getRecentSupportAccessIncident();
    if (props.currentUser?.userId === actorUserId && !incidentId.value.trim()) {
      incidentId.value = suggestion.incidentId?.trim() || "";
    }
  } catch {
    // 自动回填属于便利能力，失败时保持表单可手工填写，不遮挡主要排查流程。
  }
}

async function issueGrant() {
  if (!incidentId.value.trim() || !reason.value.trim() || !readOnlyAcknowledged.value) {
    errorMessage.value = "请填写工单号、排查原因并确认只读约束";
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
    closing.value = false;
  }
}

function clearGrantState() {
  grant.value = null;
  targetUser.value = null;
  includeArchived.value = false;
  selectedSession.value = null;
  resetTranscriptState();
  selectedWorkspace.value = null;
  entriesByDirectory.value = {};
  expandedDirectories.value = new Set();
  preview.value = null;
  previewPath.value = "";
}

async function loadUsers() {
  usersLoading.value = true;
  try {
    users.value = (await api.listUsers({ keyword: userKeyword.value.trim(), page: 1, size: 100 })).items;
  } catch (error) {
    errorMessage.value = errorText(error);
  } finally {
    usersLoading.value = false;
  }
}

async function selectTarget(user: UserManagementUser) {
  if (!grant.value) return;
  targetSelecting.value = true;
  errorMessage.value = "";
  try {
    await api.selectSupportAccessTarget(grant.value.grantToken, user.userId);
    targetUser.value = user;
    selectedSession.value = null;
    resetTranscriptState();
    selectedWorkspace.value = null;
    entriesByDirectory.value = {};
    preview.value = null;
    await Promise.all([loadSessions(1), loadWorkspaces(1)]);
  } catch (error) {
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
}
</script>

<template>
  <section class="support-panel">
    <header class="support-header">
      <div>
        <h2>问题排查只读访问</h2>
        <p>当前管理员身份不会切换；目标用户只决定查询范围。共享暗号未启用。</p>
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
          <strong>安全说明</strong>
          <span>入口快捷键不是认证因素。授权依赖当前 SUPER_ADMIN、有效登录会话和下方工单信息。</span>
        </div>
        <label>当前操作人<input :value="actorLabel" disabled /></label>
        <label>工单号<input v-model="incidentId" maxlength="128" placeholder="例如 INC-2026-00123" /></label>
        <label>排查原因<textarea v-model="reason" maxlength="1000" rows="4" placeholder="描述故障、影响和需要查看的范围" /></label>
        <label>授权时长
          <select v-model.number="durationMinutes">
            <option :value="5">5 分钟</option><option :value="15">15 分钟</option>
            <option :value="30">30 分钟</option><option :value="60">60 分钟</option>
            <option :value="120">120 分钟</option><option :value="240">240 分钟</option>
          </select>
        </label>
        <label class="grant-check"><input v-model="readOnlyAcknowledged" type="checkbox" />
          我确认仅用于问题排查，不写入、不上传、不进入终端/Agent 配置，也不批量导出。
        </label>
        <button class="primary" :disabled="issuing" @click="issueGrant">{{ issuing ? '授权中…' : '开启限时只读访问' }}</button>
      </div>

      <template v-else>
        <div class="access-banner">
          <span><b>操作人</b> {{ actorLabel }}</span>
          <span><b>目标</b> {{ targetUser ? `${targetUser.username}（${targetUser.userId}）` : '尚未选择' }}</span>
          <span><b>工单</b> {{ incidentId }}</span>
          <span><b>剩余</b> {{ countdownLabel }}</span>
          <span class="readonly-pill">只读</span>
          <button :disabled="closing" @click="closeGrant">{{ closing ? '关闭中…' : '关闭并撤销' }}</button>
        </div>

        <div class="support-layout">
          <aside class="target-picker">
            <h3>选择目标用户</h3>
            <div class="inline-search"><input v-model="userKeyword" placeholder="姓名 / 用户 ID / 统一认证号" @keyup.enter="loadUsers" /><button @click="loadUsers">查询</button></div>
            <div v-if="usersLoading" class="empty">加载中…</div>
            <button
              v-for="user in users"
              :key="user.userId"
              :class="['target-row', { active: targetUser?.userId === user.userId }]"
              :disabled="targetSelecting"
              @click="selectTarget(user)"
            >
              <strong>{{ user.username }}</strong><span>{{ user.userId }}</span><small>{{ user.unifiedAuthId }}</small>
            </button>
          </aside>

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
                    包含已归档会话（用户已删除或隐藏，数据未物理删除）
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
                      <strong>用户首页视角（只读）</strong>
                      <span>复用首页消息投影与时间线渲染，不切换管理员身份。</span>
                    </div>
                    <div class="representation">
                      <span>历史：{{ historyRepresentationLabel(historyRepresentation) }}</span>
                      <span>回放：{{ replayAvailable === true ? '可用' : replayAvailable === false ? '不可用' : '-' }}</span>
                      <span>详情保留至：{{ formatTime(detailsAvailableUntil) }}</span>
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
        <input v-model="auditIncidentId" placeholder="工单号" @keyup.enter="loadAudit(1)" />
        <select v-model="auditOutcome"><option value="">全部结果</option><option value="SUCCESS">SUCCESS</option><option value="FAILED">FAILED</option></select>
        <button @click="loadAudit(1)">查询</button>
      </div>
      <div v-if="auditLoading" class="empty">加载中…</div>
      <div class="audit-table-wrap">
        <table>
          <thead><tr><th>时间</th><th>操作人</th><th>目标</th><th>工单</th><th>动作</th><th>资源</th><th>结果</th><th>Trace</th></tr></thead>
          <tbody><tr v-for="event in audit.items" :key="event.eventId"><td>{{ formatTime(event.occurredAt) }}</td><td>{{ event.actorUsername }}<br><small>{{ event.actorUserId || '-' }}</small></td><td>{{ event.targetUsername || '-' }}<br><small>{{ event.targetUserId || '-' }}</small></td><td>{{ event.incidentId || '-' }}</td><td>{{ event.action }}</td><td>{{ event.resourceType }}<br><small>{{ event.resourceId || event.pathDigest || '-' }}</small></td><td :class="event.outcome === 'SUCCESS' ? 'ok' : 'fail'">{{ event.outcome }}<br><small>{{ event.errorCode || '' }}</small></td><td><small>{{ event.traceId }}</small></td></tr></tbody>
        </table>
      </div>
      <div class="pager"><button :disabled="audit.page <= 1" @click="loadAudit(audit.page - 1)">上一页</button><span>{{ audit.page }}</span><button :disabled="audit.page * audit.size >= audit.total" @click="loadAudit(audit.page + 1)">下一页</button></div>
    </section>
  </section>
</template>

<style scoped>
.support-panel { display:flex; flex-direction:column; min-height:0; height:100%; background:#f6f7f9; color:#20242a; font-size:13px; }
.support-header { display:flex; justify-content:space-between; gap:24px; align-items:flex-end; padding:18px 22px 14px; border-bottom:1px solid #dfe3e8; background:#fff; }
.support-header h2 { margin:0 0 4px; font-size:18px; }.support-header p { margin:0; color:#69717d; }
.support-tabs,.resource-tabs { display:flex; gap:4px; }.support-tabs button,.resource-tabs button { border:0; border-bottom:2px solid transparent; background:transparent; padding:8px 10px; color:#606975; cursor:pointer; }.support-tabs button.active,.resource-tabs button.active { color:#174ea6; border-color:#2563eb; }
.support-error { margin:10px 16px 0; padding:9px 12px; border:1px solid #f3b8b8; background:#fff1f1; color:#9f2d2d; border-radius:6px; }
.grant-form { width:min(620px,calc(100% - 40px)); margin:28px auto; padding:22px; border:1px solid #dfe3e8; border-radius:8px; background:#fff; box-shadow:0 5px 18px rgb(15 23 42 / 5%); display:grid; gap:14px; }
.grant-form label { display:grid; gap:6px; font-weight:600; }.grant-form input,.grant-form textarea,.grant-form select,.inline-search input,.audit-filter input,.audit-filter select { border:1px solid #cbd1d8; border-radius:5px; padding:8px 10px; background:#fff; color:inherit; font:inherit; }.grant-form textarea { resize:vertical; }.grant-check { grid-template-columns:auto 1fr!important; align-items:start; font-weight:400!important; }.grant-check input { margin-top:3px; }.grant-notice { display:flex; flex-direction:column; gap:4px; padding:10px 12px; border-left:3px solid #d97706; background:#fff8e8; }
button.primary { justify-self:start; border:1px solid #1d4ed8; border-radius:5px; background:#2563eb; color:#fff; padding:9px 16px; cursor:pointer; }button:disabled { opacity:.5; cursor:not-allowed!important; }
.access-banner { display:flex; align-items:center; gap:18px; padding:10px 16px; border-bottom:1px solid #f0c36d; background:#fff7dc; flex-wrap:wrap; }.access-banner button { margin-left:auto; }.readonly-pill { border:1px solid #d97706; border-radius:999px; padding:2px 8px; color:#9a5400; }
.support-layout { display:grid; grid-template-columns:240px 1fr; min-height:0; flex:1; }.target-picker { min-height:0; overflow:auto; border-right:1px solid #dfe3e8; background:#fff; padding:12px; }.target-picker h3 { margin:2px 0 10px; }.inline-search { display:flex; gap:6px; margin-bottom:10px; }.inline-search input { min-width:0; flex:1; }.inline-search button,.pager button,.audit-filter button,.access-banner button,.file-preview button { border:1px solid #c6ccd4; border-radius:5px; background:#fff; padding:6px 9px; cursor:pointer; }
.target-row,.resource-row { display:grid; width:100%; gap:2px; border:0; border-radius:5px; background:transparent; padding:8px; text-align:left; cursor:pointer; }.target-row:hover,.resource-row:hover,.target-row.active,.resource-row.active { background:#eaf1ff; }.target-row span,.resource-row span,.target-row small,.resource-row small { overflow:hidden; color:#66707d; text-overflow:ellipsis; white-space:nowrap; }
.archive-filter { display:flex; align-items:flex-start; gap:7px; margin:2px 4px 10px; color:#5f6874; font-size:12px; line-height:1.4; }.archive-filter input { margin-top:2px; }.archived-pill,.backend-pill { display:inline-block; border-radius:999px; padding:1px 6px; font-style:normal; font-size:11px; font-weight:600; }.archived-pill { border:1px solid #c7cdd5; background:#f2f4f6; color:#66707d; }.backend-pill.is-online { background:#e7f7ee; color:#177245; }.backend-pill.is-offline { background:#fff0f0; color:#a52b2b; }.backend-pill.is-unbound,.backend-pill.is-unknown { background:#f2f4f6; color:#66707d; }
.resource-panel { display:flex; flex-direction:column; min-width:0; min-height:0; }.resource-tabs { flex:0 0 auto; padding:5px 12px 0; border-bottom:1px solid #dfe3e8; background:#fff; }.session-layout { display:grid; grid-template-columns:300px 1fr; min-height:0; flex:1; }.workspace-layout { display:grid; grid-template-columns:270px 330px 1fr; min-height:0; flex:1; }.resource-list { min-height:0; overflow:auto; padding:10px; border-right:1px solid #dfe3e8; background:#fff; }.pager { display:flex; align-items:center; justify-content:center; gap:10px; padding:10px 0; }
.transcript { display:flex; min-height:0; overflow:auto; padding:14px 18px; flex-direction:column; }.user-view-heading { display:flex; align-items:baseline; justify-content:space-between; gap:16px; margin-bottom:10px; }.user-view-heading span { color:#69717d; font-size:12px; }.representation { display:flex; gap:16px; flex-wrap:wrap; margin-bottom:12px; padding:8px 10px; border:1px solid #d9e2f0; border-radius:5px; background:#f4f8ff; color:#536173; }.support-timeline { width:min(100%,980px); margin:0 auto; padding:4px 8px 18px; }.readonly-composer { position:sticky; bottom:-14px; margin-top:auto; padding:10px 0 2px; background:linear-gradient(to bottom,transparent,#f6f7f9 18%); }.readonly-composer textarea { width:100%; box-sizing:border-box; resize:none; border:1px solid #cbd1d8; border-radius:8px; padding:11px 12px; background:#eef1f4; color:#6b7280; font:inherit; }
.file-preview pre { margin:0; padding:10px 12px; overflow:auto; white-space:pre-wrap; word-break:break-word; font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,monospace; }
.file-tree { min-height:0; overflow:hidden; border-right:1px solid #dfe3e8; background:#fff; --ta-tree-bg:#fff; --ta-tree-text:#2c333a; --ta-tree-muted:#707985; --ta-tree-border-strong:#dfe3e8; }.file-preview { min-width:0; min-height:0; overflow:auto; background:#fff; }.file-preview header { display:flex; justify-content:space-between; gap:12px; padding:9px 12px; border-bottom:1px solid #e4e7eb; }.file-preview pre { min-height:calc(100% - 76px); white-space:pre; }.file-preview>button { margin:8px 12px 14px; }
.empty { padding:16px; color:#78818d; text-align:center; }.empty.large { display:grid; min-height:180px; place-items:center; }.audit-panel { min-height:0; overflow:auto; padding:14px 18px; }.audit-filter { display:flex; gap:8px; margin-bottom:12px; }.audit-table-wrap { overflow:auto; border:1px solid #dfe3e8; background:#fff; }.audit-table-wrap table { width:100%; border-collapse:collapse; white-space:nowrap; }.audit-table-wrap th,.audit-table-wrap td { padding:8px 10px; border-bottom:1px solid #e8ebef; text-align:left; vertical-align:top; }.audit-table-wrap th { position:sticky; top:0; background:#f5f6f8; }.audit-table-wrap small { color:#737d89; }.ok { color:#177245; }.fail { color:#a52b2b; }
@media (max-width:1100px) { .workspace-layout { grid-template-columns:230px 280px 1fr; }.session-layout { grid-template-columns:260px 1fr; } }
</style>
