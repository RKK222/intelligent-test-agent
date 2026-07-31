<script setup lang="ts">
import { Chat, ChatContent, ChatItem, ChatSender } from "@tdesign-vue-next/chat";
import type {
  ScopeSelector,
  WorkflowAgUiEvent,
  WorkflowApiClient,
  WorkflowConversation,
  WorkflowCurrentUser,
  WorkflowEventConnection,
  WorkflowMessage,
  WorkflowReport,
  WorkflowRepositoryGroup,
  WorkflowStructuredInput,
} from "@test-agent/workflow-api-client";
import {
  ArrowLeft,
  Bot,
  ChevronRight,
  CirclePlus,
  Clock3,
  PanelLeftClose,
  Search,
  ShieldCheck,
  Square,
} from "lucide-vue-next";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import ImpactRail from "./ImpactRail.vue";
import BaselineSelectionCard from "./BaselineSelectionCard.vue";
import LocalReanalysisCard from "./LocalReanalysisCard.vue";
import ReportPanel from "./ReportPanel.vue";
import WorkflowInputCard from "./WorkflowInputCard.vue";
import { initialWorkflowViewState, reduceAgUiEvent } from "./agui-state";

const props = defineProps<{ api: WorkflowApiClient }>();
const emit = defineEmits<{ back: [] }>();

const currentUser = ref<WorkflowCurrentUser>();
const conversations = ref<WorkflowConversation[]>([]);
const selectedConversation = ref<WorkflowConversation>();
const repositoryGroups = ref<WorkflowRepositoryGroup[]>([]);
const reports = ref<WorkflowReport[]>([]);
const selectedReportId = ref<string>();
const composer = ref("");
const loading = ref(true);
const sending = ref(false);
const reportLoading = ref(false);
const errorMessage = ref("");
const adminOwnerId = ref("");
const sidebarOpen = ref(true);
const view = reactive(initialWorkflowViewState());
let connection: WorkflowEventConnection | undefined;

const isSuperAdmin = computed(() => currentUser.value?.roles.includes("SUPER_ADMIN") ?? false);
const active = computed(() => ["QUEUED", "RUNNING", "WAITING_INPUT"].includes(view.runStatus ?? ""));
const canCancel = computed(() => ["QUEUED", "RUNNING", "WAITING_INPUT"].includes(view.runStatus ?? "") && Boolean(view.runId));
const workspaceUnavailable = computed(() =>
  ["EXPIRED", "CLEANUP_FAILED"].includes(view.workspaceStatus ?? ""),
);
const needsInitialInput = computed(() =>
  view.requiredInput.some((value) => ["repositories", "mode", "analysisMode", "analyzerIds", "intent"].includes(value)),
);
const statusLabel = computed(() => ({
  QUEUED: "等待调度", RUNNING: "分析中", WAITING_INPUT: "等待补充", SUCCEEDED: "已完成",
  PARTIAL_FAILED: "部分完成", FAILED: "失败", CANCELED: "已取消",
}[view.runStatus ?? ""] ?? "就绪"));

onMounted(async () => {
  try {
    const [me, repositories, values] = await Promise.all([
      props.api.me(),
      props.api.repositories(),
      props.api.conversations(),
    ]);
    currentUser.value = me;
    repositoryGroups.value = repositories;
    conversations.value = values;
    if (values[0]) await selectConversation(values[0]);
  } catch (error) {
    errorMessage.value = safeMessage(error);
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => connection?.close());

watch(() => view.reportPublished, async (published) => {
  if (published && view.taskId) await loadReports(view.taskId);
});

watch(
  () => [view.taskId, view.runStatus] as const,
  async ([taskId, status]) => {
    if (taskId && ["SUCCEEDED", "PARTIAL_FAILED"].includes(status ?? "")) {
      await loadReports(taskId);
    }
  },
);

async function newConversation() {
  errorMessage.value = "";
  const created = await props.api.createConversation("代码变动影响分析");
  conversations.value = [created, ...conversations.value];
  await selectConversation(created);
}

async function selectConversation(conversation: WorkflowConversation) {
  connection?.close();
  selectedConversation.value = await props.api.conversation(conversation.id);
  resetView(selectedConversation.value.messages ?? []);
  reports.value = [];
  selectedReportId.value = undefined;
  connection = props.api.connectEvents(conversation.id, handleEvent);
  void connection.done.catch((error) => {
    errorMessage.value = safeMessage(error);
  });
}

function handleEvent(event: WorkflowAgUiEvent) {
  reduceAgUiEvent(view, event);
  if (event.type === "RUN_STARTED") void refreshConversations();
}

function resetView(messages: WorkflowMessage[]) {
  Object.assign(view, initialWorkflowViewState(), { messages: [...messages] });
}

async function refreshConversations(ownerUserId?: string) {
  conversations.value = await props.api.conversations(ownerUserId);
}

async function loadOtherOwner() {
  if (!adminOwnerId.value.trim()) return;
  await refreshConversations(adminOwnerId.value.trim());
  selectedConversation.value = undefined;
  resetView([]);
}

async function ensureConversation(): Promise<WorkflowConversation> {
  if (selectedConversation.value) return selectedConversation.value;
  const created = await props.api.createConversation("代码变动影响分析");
  conversations.value = [created, ...conversations.value];
  selectedConversation.value = created;
  connection = props.api.connectEvents(created.id, handleEvent);
  return created;
}

async function sendText(value: string) {
  const text = value.trim();
  if (!text || sending.value) return;
  await submitMessage(text);
}

async function submitStructured(input: WorkflowStructuredInput) {
  await submitMessage(composer.value.trim() || "开始代码变动影响分析", input);
}

async function submitLocal(selector: ScopeSelector) {
  await submitMessage(`请局部重新分析 ${selector.kind}：${selector.value}`, {
    scopeSelectors: [selector],
  });
}

async function submitCandidate(item: Record<string, unknown>, path: string) {
  const selector = (item.selector ?? {}) as Record<string, unknown>;
  await submitLocal({
    repositoryId: String(selector.repositoryId),
    kind: "FILE",
    value: path,
  });
}

async function submitMessage(text: string, structuredInput?: WorkflowStructuredInput) {
  errorMessage.value = "";
  sending.value = true;
  try {
    const conversation = await ensureConversation();
    const result = await props.api.submitMessage(conversation.id, {
      clientRequestId: requestId(),
      text,
      ...(structuredInput ? { structuredInput } : {}),
    });
    composer.value = "";
    view.requiredInput = result.requiredInput;
    if (result.runId) {
      view.runId = result.runId;
      view.taskId = result.taskId ?? undefined;
      view.runStatus = result.status ?? undefined;
      view.scopeInput = [];
      view.baselineInput = [];
      view.currentInput = undefined;
    }
    selectedConversation.value = await props.api.conversation(conversation.id);
    view.messages = [...(selectedConversation.value.messages ?? [])];
  } catch (error) {
    errorMessage.value = safeMessage(error);
  } finally {
    sending.value = false;
  }
}

async function cancelRun() {
  if (!view.runId) return;
  try {
    await props.api.cancelRun(view.runId);
    view.runStatus = "CANCELED";
  } catch (error) {
    errorMessage.value = safeMessage(error);
  }
}

async function loadReports(taskId: string) {
  reportLoading.value = true;
  try {
    reports.value = await props.api.reports(taskId);
    selectedReportId.value = reports.value.find((value) => value.current)?.id ?? reports.value[0]?.id;
  } finally {
    reportLoading.value = false;
  }
}

async function downloadReport(reportId: string) {
  const markdown = await props.api.downloadReport(reportId);
  const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `impact-report-${reportId}.md`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function formatDate(value: string) {
  if (!value) return "";
  return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function requestId() {
  const value = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}_${Math.random().toString(16).slice(2)}`;
  return `req_${value.replaceAll("-", "")}`;
}

function safeMessage(error: unknown) {
  return error instanceof Error ? error.message : "操作失败，请稍后重试";
}
</script>

<template>
  <main class="workflow-chat-root">
    <nav class="workflow-topbar">
      <div class="workflow-topbar-brand">
        <button type="button" class="workflow-icon-button" aria-label="返回工作台" @click="emit('back')"><ArrowLeft :size="18" /></button>
        <div class="workflow-brand-mark"><Bot :size="19" /></div>
        <div><strong>长程任务工作台</strong><span>Workflow Intelligence</span></div>
      </div>
      <div class="workflow-topbar-context">
        <span class="workflow-definition-pill"><i /> 代码变动影响分析</span>
        <span class="workflow-status" :data-status="view.runStatus"><i /> {{ statusLabel }}</span>
        <button v-if="canCancel" type="button" class="workflow-stop-button" @click="cancelRun"><Square :size="13" />取消任务</button>
      </div>
    </nav>

    <div class="workflow-shell" :class="{ 'sidebar-closed': !sidebarOpen }">
      <aside class="workflow-conversations">
        <header>
          <button type="button" class="workflow-new-button" @click="newConversation"><CirclePlus :size="17" />新建对话</button>
          <button type="button" class="workflow-icon-button mobile-hide" aria-label="收起会话列表" @click="sidebarOpen = false"><PanelLeftClose :size="17" /></button>
        </header>
        <div v-if="isSuperAdmin" class="workflow-admin-query">
          <ShieldCheck :size="14" /><input v-model="adminOwnerId" placeholder="按用户 ID 查看" @keydown.enter="loadOtherOwner" />
          <button type="button" aria-label="查询用户会话" @click="loadOtherOwner"><Search :size="14" /></button>
        </div>
        <p class="workflow-nav-label">最近任务</p>
        <div class="workflow-conversation-list">
          <button
            v-for="conversation in conversations"
            :key="conversation.id"
            type="button"
            :class="{ active: selectedConversation?.id === conversation.id }"
            @click="selectConversation(conversation)"
          >
            <span class="conversation-glyph">Δ</span>
            <span><strong>{{ conversation.title }}</strong><small><Clock3 :size="11" />{{ formatDate(conversation.createdAt) }}</small></span>
            <ChevronRight :size="14" />
          </button>
        </div>
        <footer><span>{{ currentUser?.username ?? "—" }}</span><small>{{ currentUser?.unifiedAuthId }}</small></footer>
      </aside>

      <section class="workflow-conversation-main">
        <button v-if="!sidebarOpen" type="button" class="workflow-sidebar-open" @click="sidebarOpen = true">会话</button>
        <header class="workflow-conversation-header">
          <div><p class="workflow-kicker">固定流程 · 可恢复</p><h1>{{ selectedConversation?.title ?? "代码变动影响分析" }}</h1></div>
          <ImpactRail :status="view.runStatus" :report-ready="reports.length > 0" :has-manifest="view.tools.length > 1" />
        </header>

        <div v-if="errorMessage" class="workflow-error-banner" role="alert">{{ errorMessage }}</div>
        <div v-if="loading" class="workflow-loading">正在连接独立工作流服务…</div>
        <div v-else class="workflow-thread">
          <div v-if="view.messages.length === 0" class="workflow-welcome">
            <span class="welcome-index">01</span>
            <p class="workflow-kicker">SCENARIO / CODE IMPACT</p>
            <h2>把分支变化，翻译成可验证的功能影响。</h2>
            <p>系统会冻结多仓库提交坐标，生成确定性 diff，再由代码智能体给出证据、风险与回归建议。</p>
          </div>

          <Chat class="workflow-td-chat" layout="single" :clear-history="false" :show-scroll-button="true">
            <ChatItem
              v-for="message in view.messages"
              :key="message.id"
              :role="message.role === 'user' ? 'user' : 'assistant'"
              :name="message.role === 'user' ? '你' : '影响分析智能体'"
              :datetime="formatDate(message.createdAt)"
              variant="base"
            >
              <template #content>
                <ChatContent
                  :role="message.role === 'user' ? 'user' : 'assistant'"
                  :content="message.role === 'assistant' ? { type: 'markdown', data: message.content } : message.content"
                />
              </template>
            </ChatItem>
          </Chat>

          <div v-if="view.tools.length" class="workflow-tool-strip">
            <span v-for="tool in view.tools.slice(-4)" :key="tool.id" :class="tool.status.toLowerCase()">
              <i /> {{ tool.name }} · {{ tool.status === "RUNNING" ? "执行中" : tool.status === "FAILED" ? "失败" : "完成" }}
            </span>
          </div>

          <WorkflowInputCard
            v-if="needsInitialInput"
            :repository-groups="repositoryGroups"
            :load-branches="api.branches.bind(api)"
            :disabled="sending || active"
            @submit="submitStructured"
          />

          <BaselineSelectionCard
            v-if="view.baselineInput.length"
            :baselines="view.baselineInput"
            :current-input="view.currentInput"
            :repository-groups="repositoryGroups"
            :disabled="sending"
            @submit="submitStructured"
          />

          <section v-if="view.scopeInput.length" class="workflow-scope-ambiguity">
            <p class="workflow-kicker">需要补充范围</p><h3>找到了多个可能的代码位置</h3>
            <div v-for="(item, index) in view.scopeInput" :key="index">
              <p>{{ (item.selector as Record<string, unknown>)?.value }} · 请选择准确文件</p>
              <button
                v-for="path in (item.candidatePaths as string[] ?? [])"
                :key="path"
                type="button"
                @click="submitCandidate(item, path)"
              >{{ path }}</button>
            </div>
          </section>

          <p v-if="reports.length && !active && workspaceUnavailable" class="workflow-retention-notice">
            源码工作区已过期或清理失败；现有报告仍可追问。需要读取源码时，请新建对话并重新选择仓库与分支。
          </p>

          <LocalReanalysisCard
            v-if="reports.length && !active && !workspaceUnavailable"
            :repository-groups="repositoryGroups"
            :disabled="sending"
            @submit="submitLocal"
          />
        </div>

        <footer class="workflow-composer">
          <ChatSender
            v-model="composer"
            :loading="sending || view.runStatus === 'RUNNING'"
            :disabled="needsInitialInput || view.runStatus === 'WAITING_INPUT'"
            placeholder="描述要分析的变更，或在报告完成后继续追问…"
            @send="sendText"
            @stop="cancelRun"
          />
          <span>Enter 发送 · Shift + Enter 换行 · 源码只读</span>
        </footer>
      </section>

      <ReportPanel
        :reports="reports"
        :selected-id="selectedReportId"
        :loading="reportLoading"
        @select="selectedReportId = $event"
        @download="downloadReport"
      />
    </div>
  </main>
</template>
