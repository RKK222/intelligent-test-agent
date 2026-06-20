<script setup lang="ts">
import { computed, ref, shallowRef, watch } from "vue";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { AgentChat, buildComposerPromptParts, createInitialAgentChatRuntimeState, reduceAgentChatRuntime, type ComposerAttachment } from "@test-agent/agent-chat";
import { createBackendApiClient } from "@test-agent/backend-api";
import { DiffViewer } from "@test-agent/diff-viewer";
import { CodeEditor, type EditorSelectionContext } from "@test-agent/editor";
import { subscribeRunEvents } from "@test-agent/event-stream-client";
import { FileExplorer } from "@test-agent/file-explorer";
import type { AgentMessage, FileTreeEntry, PromptPart, Run, RunDiffFile, RunEvent, RuntimeResourceInfo, RuntimeToolInfo, Session, Workspace } from "@test-agent/shared-types";
import { TerminalPanel } from "@test-agent/terminal";
import { TestRunnerPanel } from "@test-agent/test-runner";
import { Button, FeedbackBanner, Input, type Feedback } from "@test-agent/ui-kit";
import { useWorkbenchStore, WorkbenchShell } from "@test-agent/workbench-shell";
import EditorPane from "./EditorPane.vue";
import WorkspaceBootstrap from "./WorkspaceBootstrap.vue";
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
  syntheticEvent
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
    void loadDirectory("");
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

async function loadDirectory(path: string) {
  if (!selectedWorkspace.value) {
    return;
  }
  loadingPath.value = path;
  try {
    const entries = await api.listFiles(selectedWorkspace.value.workspaceId, path);
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
</script>

<template>
  <WorkbenchShell
    :workspace-name="selectedWorkspace?.name ?? '未选择 Workspace'"
    branch-name="local"
    :run-status="run?.status ?? 'IDLE'"
  >
    <template #left>
      <FileExplorer
        v-if="selectedWorkspace"
        :workspace-name="selectedWorkspace.name"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :changed-files="diffFiles"
        :loading-path="loadingPath"
        @toggle-directory="toggleDirectory"
        @open-file="openFile"
        @open-diff="(path: string) => { workbench.setSelectedDiffPath(path); centerMode = 'diff'; }"
        @refresh="loadDirectory('')"
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
          :dirty="activeTab ? activeTab.content !== activeTab.savedContent : false"
          :readonly="activeTab?.readonly"
          :saving="saveMutation.isPending.value"
          :feedback="feedback"
          @change="(content: string) => activeTab && workbench.updateTabContent(activeTab.path, content)"
          @save="() => activeTab && saveMutation.mutate(activeTab)"
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
        @send="handleSend"
        @open-diff="centerMode = 'diff'"
        @retry="() => lastPrompt && handleSend(lastPrompt)"
        @cancel="cancelRunMutation.mutate()"
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
        <div class="flex h-9 shrink-0 items-center gap-1 border-b border-[var(--ta-border)] bg-[#eef0f3] px-2">
          <button
            type="button"
            :class="['rounded-md px-2 py-1 text-[12px]', bottomMode === 'run' ? 'bg-[#ffffff] text-[var(--ta-text)] shadow-[inset_0_-2px_0_var(--ta-accent)]' : 'text-[var(--ta-muted)] hover:bg-[#e7e9ed] hover:text-[var(--ta-text)]']"
            @click="bottomMode = 'run'"
          >运行</button>
          <button
            type="button"
            :class="['rounded-md px-2 py-1 text-[12px]', bottomMode === 'terminal' ? 'bg-[#ffffff] text-[var(--ta-text)] shadow-[inset_0_-2px_0_var(--ta-accent)]' : 'text-[var(--ta-muted)] hover:bg-[#e7e9ed] hover:text-[var(--ta-text)]']"
            @click="bottomMode = 'terminal'"
          >终端</button>
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
</template>
