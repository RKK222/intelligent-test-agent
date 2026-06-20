<script setup lang="ts">
import { computed, ref, shallowRef, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { AgentChat, buildComposerPromptParts, createInitialAgentChatRuntimeState, reduceAgentChatRuntime, type ComposerAttachment } from "@test-agent/agent-chat";
import { createBackendApiClient } from "@test-agent/backend-api";
import { DiffViewer } from "@test-agent/diff-viewer";
import { CodeEditor, type EditorSelectionContext } from "@test-agent/editor";
import { subscribeRunEvents } from "@test-agent/event-stream-client";
import { FileExplorer } from "@test-agent/file-explorer";
import { Bell, Code2, GitBranch, PanelBottom, TerminalSquare } from "lucide-vue-next";
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
import { FeedbackBanner, type Feedback } from "@test-agent/ui-kit";
import { useWorkbenchStore, WorkbenchShell } from "@test-agent/workbench-shell";
import EditorPane from "./EditorPane.vue";
import WorkspaceBootstrap from "./WorkspaceBootstrap.vue";
import WorkspaceDirectoryPickerDialog from "./WorkspaceDirectoryPickerDialog.vue";
import { canStartFollowUp, createFollowUpDraft, dequeueFollowUp, enqueueFollowUp, isRunBusyStatus, type FollowUpDraft } from "./follow-up-queue";
import {
  buildPromptParts,
  diffFilesFromPayload,
  dispatchRuntimeResult,
  errorFeedback,
  historyItems,
  initialMessages,
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
      title: path.split("/").at(-1) ?? path,
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

function handleRunEvent(event: RunEvent) {
  logs.value = [...logs.value.slice(-200), `[${event.seq}] ${event.type}`];
  dispatchChat({ type: "event", event });
  notifyOnAttention(event, selectedWorkspace.value, session.value);
  if (event.type === "assistant.message.delta") {
    return;
  } else if (event.type === "diff.proposed") {
    const files = diffFilesFromPayload(event.payload);
    if (files.length) {
      diffSource.value = "run";
      diffFiles.value = files;
      workbench.setSelectedDiffPath(files[0]?.path);
    } else if (run.value) {
      void api.getRunDiff(run.value.runId).then((diff) => {
        diffFiles.value = diff.files;
        workbench.setSelectedDiffPath(diff.files[0]?.path);
      });
    }
  } else if (event.type === "session.diff") {
    const files = diffFilesFromPayload(event.payload);
    if (files.length) {
      diffSource.value = "session";
      diffFiles.value = files;
      workbench.setSelectedDiffPath(files[0]?.path);
    }
  } else if (event.type === "run.succeeded" || event.type === "run.failed" || event.type === "run.cancelled") {
    run.value = run.value
      ? { ...run.value, status: event.type === "run.succeeded" ? "SUCCEEDED" : event.type === "run.failed" ? "FAILED" : "CANCELLED" }
      : run.value;
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
async function openLivePreview(relPath: string) {
  if (!selectedWorkspace.value) {
    return;
  }
  expandPathToFile(relPath);
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
      title: relPath.split("/").at(-1) ?? relPath,
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

// 扫描对话中的 tool part：新完成的写文件工具 → 读盘刷新预览。
function scanLiveToolParts() {
  if (!liveTrack.value) {
    return;
  }
  for (const message of chatState.value.messages) {
    if (message.role !== "assistant") {
      continue;
    }
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
      void openLivePreview(path);
    }
  }
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
  <WorkbenchShell
    :workspace-name="selectedWorkspace?.name ?? '未选择 Workspace'"
    branch-name="local"
    :run-status="run?.status ?? 'IDLE'"
    :bottom-open="bottomDrawerOpen"
    :bottom-height="190"
  >
    <template #activity>
      <nav class="flex h-full flex-col items-center justify-between py-2" aria-label="工作台活动栏">
        <div class="flex flex-col items-center gap-4">
          <button
            type="button"
            :class="['ta-activity-button', centerMode === 'editor' && 'is-active']"
            aria-label="打开编辑器"
            title="打开编辑器"
            @click="centerMode = 'editor'"
          >
            <Code2 class="h-[22px] w-[22px]" />
          </button>
          <button
            type="button"
            :class="['ta-activity-button', centerMode === 'diff' && 'is-active']"
            aria-label="打开 Diff"
            title="打开 Diff"
            @click="centerMode = 'diff'"
          >
            <GitBranch class="h-[22px] w-[22px]" />
          </button>
          <button
            type="button"
            :class="['ta-activity-button', bottomDrawerOpen && 'is-active']"
            aria-label="打开运行与终端"
            title="打开运行与终端"
            @click="openBottomDrawer()"
          >
            <TerminalSquare class="h-[22px] w-[22px]" />
          </button>
          <button
            type="button"
            class="ta-activity-button"
            aria-label="请求通知权限"
            title="请求通知权限"
            @click="requestNotifications"
          >
            <Bell class="h-[22px] w-[22px]" />
          </button>
        </div>
        <button
          type="button"
          :class="['ta-activity-button', bottomDrawerOpen && 'is-active']"
          aria-label="切换底部面板"
          title="切换底部面板"
          @click="bottomDrawerOpen = !bottomDrawerOpen"
        >
          <PanelBottom class="h-[22px] w-[22px]" />
        </button>
      </nav>
    </template>

    <template #left>
      <FileExplorer
        v-if="selectedWorkspace"
        :workspace-name="selectedWorkspace.name"
        :workspace-root-path="selectedWorkspace.rootPath"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :changed-files="diffFiles"
        :loading-path="loadingPath"
        @toggle-directory="toggleDirectory"
        @open-file="openFile"
        @open-diff="(path: string) => { workbench.setSelectedDiffPath(path); centerMode = 'diff'; }"
        @refresh="loadDirectory('')"
        @add-workspace="openWorkspaceDirectoryPicker"
      />
      <WorkspaceBootstrap v-else :loading="createWorkspaceMutation.isPending.value" @create="(payload) => createWorkspaceMutation.mutate(payload)" />
    </template>

    <template #center>
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
      <EditorPane
        v-else
        :tabs="tabs"
        :active-path="activePath"
        @activate="(path: string) => workbench.setActivePath(path)"
        @close="(path: string) => workbench.closeTab(path)"
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
      </EditorPane>
    </template>

    <template #right>
      <AgentChat
        :messages="chatState.messages"
        :history="historyList"
        :history-search="sessionSearch"
        :running="runtimeBusy"
        :permissions="chatState.permissions"
        :questions="chatState.questions"
        :todos="chatState.todos"
        :agents="agents"
        :models="models"
        :commands="commands"
        :providers="providers"
        :resources="resourcesList"
        :tools="mcpToolsData"
        :runtime-status="runtimeStatusValue"
        :selected-agent="selectedAgent"
        :selected-provider="selectedProvider"
        :selected-model="selectedModel"
        :mode="promptMode"
        :live-track="liveTrack"
        @send="handleSend"
        @open-diff="centerMode = 'diff'"
        @retry="() => lastPrompt && handleSend(lastPrompt)"
        @cancel="cancelRunMutation.mutate()"
        @toggle-live-track="liveTrack = !liveTrack"
        @reply-permission="(id: string, decision: 'once' | 'always' | 'reject') => replyPermissionMutation.mutate({ requestId: id, decision })"
        @reply-question="(id: string, answers: unknown[]) => replyQuestionMutation.mutate({ requestId: id, answers })"
        @reject-question="(id: string) => rejectQuestionMutation.mutate(id)"
        @agent-change="(v: string) => (selectedAgent = v)"
        @provider-change="(v: string) => (selectedProvider = v)"
        @model-change="(v: string) => (selectedModel = v)"
        @mode-change="(v: string) => (promptMode = v)"
        @request-notifications="requestNotifications"
        @history-search-change="(v: string) => (sessionSearch = v)"
        @select-history="(id: string) => switchSession(id)"
        @toggle-history-pin="(id: string, pinned: boolean) => updateSessionMutation.mutate({ sessionId: id, pinned })"
        @delete-history="(id: string) => deleteSessionMutation.mutate(id)"
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
  </WorkbenchShell>

  <div class="pointer-events-none fixed bottom-3 left-1/2 z-50 w-[min(560px,calc(100vw-24px))] -translate-x-1/2">
    <FeedbackBanner :feedback="feedback" class="pointer-events-auto rounded-md border border-slate-800" />
  </div>
  <WorkspaceDirectoryPickerDialog
    :open="directoryPickerOpen"
    :directory="directoryPickerData"
    :loading="directoryPickerLoading"
    @close="directoryPickerOpen = false"
    @navigate="loadWorkspaceDirectories"
    @select="selectWorkspaceDirectory"
  />
</template>
