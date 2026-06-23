<script setup lang="ts">
import { computed, onScopeDispose, ref, shallowRef, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { AgentChat, buildComposerPromptParts, createInitialAgentChatRuntimeState, reduceAgentChatRuntime, type ComposerAttachment } from "@test-agent/agent-chat";
import { createBackendApiClient } from "@test-agent/backend-api";
import { DiffViewer } from "@test-agent/diff-viewer";
import { CodeEditor, type EditorSelectionContext } from "@test-agent/editor";
import { subscribeRunEvents } from "@test-agent/event-stream-client";
import { Bell, Code2, GitBranch, MessageSquare, TerminalSquare } from "lucide-vue-next";
import { Setting as ElSetting } from "@element-plus/icons-vue";
import type {
  AgentMessage,
  FileTreeEntry,
  MessagePart,
  PageResponse,
  PromptPart,
  Run,
  RunDiffFile,
  RunEvent,
  RuntimeResourceInfo,
  RuntimeToolInfo,
  Session,
  Workspace,
  WorkspaceDirectoryList
} from "@test-agent/shared-types";
import { TerminalPanel } from "@test-agent/terminal";
import { TestRunnerPanel } from "@test-agent/test-runner";
import { type Feedback } from "@test-agent/ui-kit";
import { useWorkbenchStore } from "@test-agent/workbench-shell";
import FigmaShell from "./FigmaShell.vue";
import FigmaFileExplorer from "./FigmaFileExplorer.vue";
import FigmaEditorArea from "./FigmaEditorArea.vue";
import FigmaChatPanel from "./FigmaChatPanel.vue";
import SettingsDialog from "./settings/SettingsDialog.vue";
import WorkspaceBootstrap from "./WorkspaceBootstrap.vue";
import WorkspaceDirectoryPickerDialog from "./WorkspaceDirectoryPickerDialog.vue";
import { notifyFeedback } from "./notify";
import { canStartFollowUp, createFollowUpDraft, dequeueFollowUp, enqueueFollowUp, isRunBusyStatus, type FollowUpDraft } from "./follow-up-queue";
import {
  buildPromptParts,
  diffFilesFromPayload,
  dispatchRuntimeResult,
  errorFeedback,
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
  syntheticEvent,
  text
} from "./workbench-utils";

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl });
const queryClient = useQueryClient();
const workbench = useWorkbenchStore();

// 工作台状态
const selectedWorkspaceId = ref<string | undefined>(undefined);
const entriesByDirectory = ref<Record<string, FileTreeEntry[]>>({});
const expandedDirectories = ref<Set<string>>(new Set());
const loadingPath = ref<string | null>(null);
const session = shallowRef<Session | null>(null);
const run = shallowRef<Run | null>(null);
const lastPrompt = ref("");
const selectedAgent = ref("");
const selectedProvider = ref("");
const selectedModel = ref("");
const promptMode = ref("build");
const logs = ref<string[]>([]);
const diffFiles = ref<RunDiffFile[]>([]);
const diffSource = ref<"run" | "session" | "vcs">("run");
const diffViewMode = ref<"split" | "unified">("split");
const centerMode = ref<"editor" | "diff">("editor");
const feedback = ref<Feedback | null>(null);
const sessionSearch = ref("");
const followUpQueue = ref<FollowUpDraft[]>([]);
const diffContextParts = ref<PromptPart[]>([]);
const editorSelection = ref<EditorSelectionContext | undefined>(undefined);
const bottomMode = ref<"run" | "terminal">("run");
const bottomDrawerOpen = ref(false);
const rightPanelOpen = ref(true);
const selectedAppId = ref("fgcms-psn");
const chatTitle = ref("生成测试案例");
// 任务消耗展示：duration 取 chatStartedAt 实时计算；tokens 从助手消息的 step-finish part
// 累计（opencode 每轮 step 结束会上报 tokens.total）；thought for 累计 reasoning part 的
// durationMs。Run 结束后保留最后值继续展示。Run 切换时清零。
const chatStartedAt = ref<number | null>(null);
const accumulatedTokens = ref(0);
const accumulatedReasoningMs = ref(0);
let lastDuration: string | undefined;
let lastTokens = 0;
let lastThoughtForMs = 0;
const nowTick = ref(Date.now());
const settingsOpen = ref(false);
const directoryPickerOpen = ref(false);
const directoryPickerLoading = ref(false);
const directoryPickerData = shallowRef<WorkspaceDirectoryList | null>(null);
// 实时追踪：开启后 agent 每次写文件（write/edit/apply_patch 工具完成）就把该文件以只读预览
// 打开在中间编辑器并读取磁盘最新内容刷新——agent 直接写盘，磁盘即最新。
const liveTrack = ref(false);
// 已跟随过的 tool partId，避免同一工具调用重复读盘刷新。
const liveFollowedParts = ref<Set<string>>(new Set());

// Chat runtime：单一 reducer 维护，dispatch 闭包更新
const chatState = ref(createInitialAgentChatRuntimeState(initialMessages));
function dispatchChat(action: Parameters<typeof reduceAgentChatRuntime>[1]) {
  chatState.value = reduceAgentChatRuntime(chatState.value, action);
}

const tabs = computed(() => workbench.tabs);
const activePath = computed(() => workbench.activePath);
const selectedDiffPath = computed(() => workbench.selectedDiffPath);
const activeTab = computed(() => tabs.value.find((tab) => tab.path === activePath.value));
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
const selectedWorkspace = computed(() => workspaces.value.find((item) => item.workspaceId === selectedWorkspaceId.value) ?? workspaces.value[0]);
const selectedWorkspaceIdRef = computed(() => selectedWorkspace.value?.workspaceId);
const sessionSearchTrim = computed(() => sessionSearch.value.trim());

const sessionsQuery = useQuery({
  queryKey: ["sessions", selectedWorkspaceIdRef, sessionSearchTrim],
  enabled: () => Boolean(selectedWorkspaceIdRef.value) || Boolean(sessionSearchTrim.value),
  queryFn: () => {
    const query = sessionSearchTrim.value;
    return query ? api.listAllSessions(1, 30, query) : api.listSessions(selectedWorkspaceIdRef.value!, 1, 30);
  }
});

const agentsQuery = useQuery({
  queryKey: ["runtime", "agents", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
  queryFn: () => api.listAgents(selectedWorkspaceIdRef.value!)
});
const modelsQuery = useQuery({
  queryKey: ["runtime", "models", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
  queryFn: () => api.listModels(selectedWorkspaceIdRef.value!)
});
const providersQuery = useQuery({
  queryKey: ["runtime", "providers", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
  queryFn: () => api.listProviders(selectedWorkspaceIdRef.value!)
});
const commandsQuery = useQuery({
  queryKey: ["runtime", "commands", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
  queryFn: () => api.listCommands(selectedWorkspaceIdRef.value!)
});
const lspStatusQuery = useQuery({
  queryKey: ["runtime", "lsp", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
  queryFn: () => api.getLspStatus(selectedWorkspaceIdRef.value!),
  refetchInterval: 30000
});
const mcpStatusQuery = useQuery({
  queryKey: ["runtime", "mcp", "status", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
  queryFn: () => api.getMcpStatus(selectedWorkspaceIdRef.value!),
  refetchInterval: 30000
});
const mcpResourcesQuery = useQuery({
  queryKey: ["runtime", "mcp", "resources", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
  queryFn: () => api.getMcpResources(selectedWorkspaceIdRef.value!)
});
const mcpToolsQuery = useQuery({
  queryKey: ["runtime", "mcp", "tools", selectedWorkspaceIdRef, selectedProvider, selectedModel],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
  queryFn: () => {
    const model = modelIdOnly(selectedModel.value);
    return api.getMcpTools(selectedWorkspaceIdRef.value!, selectedProvider.value || undefined, model || undefined);
  }
});
const vcsStatusQuery = useQuery({
  queryKey: ["runtime", "vcs", "status", selectedWorkspaceIdRef],
  enabled: () => Boolean(selectedWorkspaceIdRef.value),
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
const sessionsItems = computed(() => sessionsQuery.data.value?.items ?? []);

const historyList = computed(() => historyItems(run.value, sessionsItems.value));
const resourcesList = computed(() => runtimeResources(mcpResourcesData.value, activeTab.value));
const runtimeStatusValue = computed(() =>
  runtimeStatus(session.value, run.value, selectedAgent.value, selectedModel.value, vcsStatusData.value, lspStatusData.value, mcpStatusData.value, mcpToolsData.value, mcpResourcesData.value)
);
const vcsCurrentBranch = computed(() => {
  const status = vcsStatusData.value as unknown;
  if (!status || typeof status !== "object") return undefined;
  const root = status as Record<string, unknown>;
  const data = (root.data as Record<string, unknown> | undefined) ?? root;
  const branch = typeof data.branch === "string" ? data.branch : undefined;
  return branch;
});
const vcsBranches = computed(() => {
  const current = vcsCurrentBranch.value;
  if (!current) return [];
  return [{ name: current, isCurrent: true }];
});

function handleChangeBranch(branch: string) {
  // TODO: 对接后端切换分支接口
  feedback.value = { kind: "info", title: "已切换分支", description: `当前分支：${branch}` };
}

// ===== 默认值与联动 effect =====
watch(selectedWorkspace, (sw) => {
  if (!selectedWorkspaceId.value && sw?.workspaceId) {
    selectedWorkspaceId.value = sw.workspaceId;
  }
});
watch(activePath, () => {
  editorSelection.value = undefined;
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
    selectedModel.value = modelValue(data[0]);
  }
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

const startRunMutation = useMutation({
  mutationFn: async (input: { prompt: string; parts: PromptPart[] }) => {
    if (!selectedWorkspace.value) {
      throw new Error("未选择 Workspace");
    }
    const activeSession =
      session.value ??
      (await api.createSession(selectedWorkspace.value.workspaceId, `Agent ${new Date().toLocaleTimeString("zh-CN", { hour12: false })}`));
    session.value = activeSession;
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
const taskUsage = computed<{ duration?: string; tokens?: number; thoughtFor?: string }>(() => {
  // 引用 nowTick 以触发每秒重算
  void nowTick.value;
  const usage: { duration?: string; tokens?: number; thoughtFor?: string } = {};
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
  [followUpQueue, run, session, () => startRunMutation.isPending.value, () => commandMutation.isPending.value],
  () => {
    if (followUpQueue.value.length === 0 || !canStartFollowUp(run.value, startRunMutation.isPending.value || commandMutation.isPending.value)) {
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
  loadingPath.value = null;
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
  liveFollowedParts.value = new Set();
  selectedAgent.value = "";
  selectedProvider.value = "";
  selectedModel.value = "";
  // 切工作区时同步清掉任务消耗计时与上一轮终态展示，避免旧 Run 的 token/duration 残留。
  chatStartedAt.value = null;
  accumulatedTokens.value = 0;
  accumulatedReasoningMs.value = 0;
  lastDuration = undefined;
  lastTokens = 0;
  lastThoughtForMs = 0;
  nowTick.value = Date.now();
  dispatchChat({ type: "reset" });
  workbench.resetWorkspaceView();
}

function cacheWorkspace(workspace: Workspace) {
  queryClient.setQueryData<PageResponse<Workspace>>(["workspaces"], (old) => {
    const previousItems = old?.items ?? [];
    const existed = previousItems.some((item) => item.workspaceId === workspace.workspaceId || item.rootPath === workspace.rootPath);
    const items = [
      workspace,
      ...previousItems.filter((item) => item.workspaceId !== workspace.workspaceId && item.rootPath !== workspace.rootPath)
    ];
    return {
      items,
      page: old?.page ?? 1,
      size: old?.size ?? Math.max(items.length, 50),
      total: old ? old.total + (existed ? 0 : 1) : items.length
    };
  });
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

async function switchWorkspace(workspace: Workspace) {
  resetWorkspaceState();
  cacheWorkspace(workspace);
  selectedWorkspaceId.value = workspace.workspaceId;
  void queryClient.invalidateQueries({ queryKey: ["workspaces"] });
  void queryClient.invalidateQueries({ queryKey: ["sessions"] });
  void queryClient.invalidateQueries({ queryKey: ["runtime"] });
  await loadDirectory("", workspace.workspaceId);
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

async function loadDirectory(path: string, workspaceId = selectedWorkspace.value?.workspaceId) {
  if (!workspaceId) {
    return;
  }
  loadingPath.value = path;
  try {
    const entries = await api.listFiles(workspaceId, path);
    entriesByDirectory.value = { ...entriesByDirectory.value, [path]: entries };
  } catch (error) {
    feedback.value = errorFeedback("加载文件树失败", error);
  } finally {
    loadingPath.value = null;
  }
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

function toggleDirectory(path: string) {
  const next = new Set(expandedDirectories.value);
  if (next.has(path)) {
    next.delete(path);
  } else {
    next.add(path);
    if (!entriesByDirectory.value[path]) {
      void loadDirectory(path);
    }
  }
  expandedDirectories.value = next;
}

function handleSend(prompt: string, attachments: ComposerAttachment[] = []) {
  const parts = buildPromptParts(prompt, activeTab.value, attachments, diffContextParts.value, editorSelection.value);
  const displayPrompt = prompt.trim() || promptFromParts(parts);
  lastPrompt.value = displayPrompt;
  diffContextParts.value = [];
  dispatchChat({ type: "user.submitted", prompt: displayPrompt, parts });
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
  if (command && session.value) {
    commandMutation.mutate({ ...command, prompt: displayPrompt });
    return;
  }
  startRunMutation.mutate({ prompt: displayPrompt, parts });
}

function handleStopRun() {
  cancelRunMutation.mutate();
  if (chatStartedAt.value) {
    lastDuration = formatDurationMs(Date.now() - chatStartedAt.value);
    chatStartedAt.value = null;
    // 触发 taskUsage 重新计算（duration 从 live 切到 last）
    nowTick.value = Date.now();
  }
}

function handleSelectApp(appId: string) {
  selectedAppId.value = appId;
  // TODO: 后续对接后端接口获取应用详情
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
    const files = diffFilesFromPayload(event.payload);
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
    const files = diffFilesFromPayload(event.payload);
    if (files.length) {
      diffSource.value = "session";
      diffFiles.value = mergeDiffFiles(diffFiles.value, files);
      workbench.setSelectedDiffPath(files[0]?.path);
    }
  } else if (event.type === "run.succeeded" || event.type === "run.failed" || event.type === "run.cancelled") {
    run.value = run.value
      ? { ...run.value, status: event.type === "run.succeeded" ? "SUCCEEDED" : event.type === "run.failed" ? "FAILED" : "CANCELLED" }
      : run.value;
    // 计算任务消耗统计：duration 由 chatStartedAt 锁定，tokens/thoughtFor 仍优先取累计值；
    // 如果后端 payload 直接带上 tokens 或 thoughtFor 字段，则覆盖一次（向后兼容未来后端实现）。
    if (chatStartedAt.value) {
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
const LIVE_WRITE_TOOLS = new Set(["write", "edit", "apply_patch", "str_replace", "multi_edit", "create_file"]);

// 把绝对路径或带 git 前缀的路径归一化为 workspace 相对路径。
function normalizeWorkspacePath(raw: string): string {
  const rootPath = selectedWorkspace.value?.rootPath ?? "";
  let p = raw.replace(/^([ab])\//, "");
  if (rootPath && (p === rootPath || p.startsWith(`${rootPath}/`))) {
    p = p.slice(rootPath.length).replace(/^\/+/, "");
  }
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
  const existing = tabs.value.find((tab) => tab.path === relPath);
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
// 仅在父目录已经缓存（用户已展开过）时刷新；未加载的目录交给 expandPathToFile 懒加载，
// 避免一次工具完成触发两次 listFiles 同一目录的重复请求。
function refreshParentDirectory(relPath: string) {
  if (!relPath || relPath.startsWith("/")) {
    return;
  }
  const segments = relPath.split("/").filter(Boolean);
  if (segments.length <= 1) {
    if (entriesByDirectory.value[""] !== undefined || expandedDirectories.value.size > 0) {
      void loadDirectory("");
    }
    return;
  }
  const parentPath = segments.slice(0, -1).join("/");
  if (entriesByDirectory.value[parentPath] !== undefined) {
    void loadDirectory(parentPath);
  }
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
          expandPathToFile(path);
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

async function loadDiffSource(source: "run" | "session" | "vcs") {
  diffSource.value = source;
  centerMode.value = "diff";
  try {
    const nextFiles =
      source === "run"
        ? run.value
          ? (await api.getRunDiff(run.value.runId)).files
          : []
        : source === "session"
          ? session.value
            ? (await api.getSessionDiff(session.value.sessionId)).files
            : []
          : selectedWorkspace.value
            ? (await api.getVcsDiffFiles(selectedWorkspace.value.workspaceId)).files
            : [];
    diffFiles.value = nextFiles;
    workbench.setSelectedDiffPath(nextFiles[0]?.path);
  } catch (error) {
    feedback.value = errorFeedback("加载 Diff 失败", error);
  }
}

async function requestNotifications() {
  if (typeof window === "undefined" || !("Notification" in window)) {
    feedback.value = { kind: "info", title: "当前浏览器不支持通知" };
    return;
  }
  const result = await Notification.requestPermission();
  feedback.value = { kind: result === "granted" ? "success" : "info", title: result === "granted" ? "通知已开启" : "通知未开启" };
}

async function switchSession(sessionId: string) {
  const selected = sessionsQuery.data.value?.items.find((item) => item.sessionId === sessionId) ?? (await api.getSession(sessionId));
  session.value = selected;
  try {
    const page = await api.listSessionMessages(sessionId, 1, 100);
    dispatchChat({ type: "reset", messages: messagesFromSessionMessages(page.items) });
    feedback.value = { kind: "info", title: "已切换 Session", description: selected.title };
  } catch (error) {
    feedback.value = errorFeedback("加载 Session 消息失败", error);
  }
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

function openBottomDrawer(mode: "run" | "terminal" = bottomMode.value) {
  bottomMode.value = mode;
  bottomDrawerOpen.value = true;
}
</script>

<template>
  <FigmaShell
    :workspace-name="selectedWorkspace?.name"
    :bottom-open="bottomDrawerOpen"
    :show-right-panel="rightPanelOpen"
    :selected-app-id="selectedAppId"
    @toggle-left-panel="() => {}"
    @toggle-right-panel="rightPanelOpen = !rightPanelOpen"
    @open-folder="openWorkspaceDirectoryPicker"
    @select-app="handleSelectApp"
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
            <Code2 class="figma-activity-icon" />
          </button>
          <button
            type="button"
            :class="['figma-activity-btn', centerMode === 'diff' && 'figma-activity-btn--active']"
            aria-label="打开 Diff"
            title="打开 Diff"
            @click="centerMode = 'diff'"
          >
            <GitBranch class="figma-activity-icon" />
          </button>
          <button
            type="button"
            :class="['figma-activity-btn', bottomDrawerOpen && 'figma-activity-btn--active']"
            aria-label="打开运行与终端"
            title="打开运行与终端"
            @click="openBottomDrawer()"
          >
            <TerminalSquare class="figma-activity-icon" />
          </button>
          <button
            type="button"
            class="figma-activity-btn"
            aria-label="请求通知权限"
            title="请求通知权限"
            @click="requestNotifications"
          >
            <Bell class="figma-activity-icon" />
          </button>
          <button
            type="button"
            :class="['figma-activity-btn', rightPanelOpen && 'figma-activity-btn--active']"
            aria-label="切换对话面板"
            title="切换对话面板"
            @click="rightPanelOpen = !rightPanelOpen"
          >
            <MessageSquare class="figma-activity-icon" />
          </button>
        </div>
        <button
          type="button"
          :class="['figma-activity-btn', settingsOpen && 'figma-activity-btn--active']"
          aria-label="系统设置"
          title="系统设置"
          @click="settingsOpen = true"
        >
          <ElSetting class="figma-activity-icon" />
        </button>
      </nav>
    </template>

    <template #files>
      <FigmaFileExplorer
        v-if="selectedWorkspace"
        :workspace-name="selectedWorkspace.name"
        :workspace-root-path="selectedWorkspace.rootPath"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :changed-files="diffFiles"
        :loading-path="loadingPath"
        :branches="vcsBranches"
        :current-branch="vcsCurrentBranch"
        @toggle-directory="toggleDirectory"
        @open-file="openFile"
        @open-diff="(path: string) => { workbench.setSelectedDiffPath(path); centerMode = 'diff'; }"
        @refresh="loadDirectory('')"
        @add-workspace="openWorkspaceDirectoryPicker"
        @change-branch="handleChangeBranch"
      />
      <WorkspaceBootstrap
        v-else
        :loading="createWorkspaceMutation.isPending.value"
        @create="(payload) => createWorkspaceMutation.mutate(payload)"
      />
    </template>

    <template #editor>
      <DiffViewer
        v-if="centerMode === 'diff'"
        :files="diffFiles"
        :selected-path="selectedDiffPath"
        :source="diffSource"
        :view-mode="diffViewMode"
        :accepting="acceptDiffMutation.isPending.value"
        :rejecting="rejectDiffMutation.isPending.value"
        :feedback="feedback"
        @select-file="(path: string) => workbench.setSelectedDiffPath(path)"
        @source-change="(source: 'run' | 'session' | 'vcs') => loadDiffSource(source)"
        @view-mode-change="(mode: 'split' | 'unified') => (diffViewMode = mode)"
        @refresh="loadDiffSource(diffSource)"
        @accept-run="acceptDiffMutation.mutate()"
        @reject-run="rejectDiffMutation.mutate()"
        @current-file-feedback="onCurrentFileFeedback"
        @use-hunk-context="onUseHunkContext"
      />
      <FigmaEditorArea
        v-else
        :tabs="tabs"
        :active-path="activePath"
        :breadcrumb-path="breadcrumbDisplay"
        :branches="vcsBranches"
        :current-branch="vcsCurrentBranch"
        :write-path="activeTab?.path"
        :updated-at="activeTab ? Date.now() / 1000 : undefined"
        :dirty="!!activeTab && !activeTab.livePreview && activeTab.content !== activeTab.savedContent"
        :readonly="!!activeTab?.readonly"
        :saving="saveMutation.isPending.value"
        @activate="(path: string) => workbench.setActivePath(path)"
        @close="(path: string) => workbench.closeTab(path)"
        @editor-action="() => {}"
        @change-branch="handleChangeBranch"
        @save="() => activeTab && !activeTab.livePreview && saveMutation.mutate(activeTab)"
      >
        <CodeEditor
          :path="activeTab?.path"
          :content="activeTab?.content"
          :dirty="activeTab && !activeTab.livePreview ? activeTab.content !== activeTab.savedContent : false"
          :readonly="activeTab?.readonly"
          :saving="saveMutation.isPending.value"
          :feedback="feedback"
          @change="(content: string) => activeTab && workbench.updateTabContent(activeTab.path, content)"
          @save="() => activeTab && !activeTab.livePreview && saveMutation.mutate(activeTab)"
          @selection-change="(selection: EditorSelectionContext | undefined) => (editorSelection = selection)"
        />
      </FigmaEditorArea>
    </template>

    <template #chat>
      <FigmaChatPanel
        :messages="chatState.messages"
        :running="runtimeBusy"
        :title="chatTitle"
        :file-changes="diffFiles"
        :task-usage="taskUsage"
        :history="historyList"
        placeholder="Ask the AI agent..."
        @send="(text: string) => handleSend(text)"
        @stop="handleStopRun"
        @new-conversation="() => handleSend('')"
        @close="rightPanelOpen = false"
      />
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
            :disabled="!session"
            disabled-reason="先发送一次 prompt 建立 Session 运行上下文后再连接终端"
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

  <SettingsDialog v-model="settingsOpen" />
</template>
