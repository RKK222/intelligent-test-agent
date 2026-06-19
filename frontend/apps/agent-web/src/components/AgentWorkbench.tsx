"use client";

import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as React from "react";
import { AgentChat, buildComposerPromptParts, createInitialAgentChatRuntimeState, reduceAgentChatRuntime, type ComposerAttachment } from "@test-agent/agent-chat";
import { BackendApiError, createBackendApiClient } from "@test-agent/backend-api";
import { DiffViewer } from "@test-agent/diff-viewer";
import { CodeEditor, type EditorSelectionContext } from "@test-agent/editor";
import { subscribeRunEvents } from "@test-agent/event-stream-client";
import { FileExplorer } from "@test-agent/file-explorer";
import type {
  AgentMessage,
  FileTreeEntry,
  ModelInfo,
  PromptPart,
  Run,
  RunDiffFile,
  RunEvent,
  RuntimeResourceInfo,
  RuntimeStatus,
  RuntimeToolInfo,
  Session,
  SessionMessage,
  Workspace
} from "@test-agent/shared-types";
import { TestRunnerPanel } from "@test-agent/test-runner";
import { Button, FeedbackBanner, Input, type Feedback } from "@test-agent/ui-kit";
import { useWorkbenchStore, WorkbenchShell } from "@test-agent/workbench-shell";
import { canStartFollowUp, createFollowUpDraft, dequeueFollowUp, enqueueFollowUp, isRunBusyStatus, type FollowUpDraft } from "./follow-up-queue";
import { buildEditorFilePromptPart } from "./prompt-context";

const queryClient = new QueryClient();
const apiBaseUrl = process.env.NEXT_PUBLIC_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";

export function AgentWorkbench() {
  return (
    <QueryClientProvider client={queryClient}>
      <WorkbenchRuntime />
    </QueryClientProvider>
  );
}

function WorkbenchRuntime() {
  const api = React.useMemo(() => createBackendApiClient({ baseUrl: apiBaseUrl }), []);
  const queryClient = useQueryClient();
  const [selectedWorkspaceId, setSelectedWorkspaceId] = React.useState<string>();
  const [entriesByDirectory, setEntriesByDirectory] = React.useState<Record<string, FileTreeEntry[]>>({});
  const [expandedDirectories, setExpandedDirectories] = React.useState(() => new Set<string>());
  const [loadingPath, setLoadingPath] = React.useState<string | null>(null);
  const [session, setSession] = React.useState<Session | null>(null);
  const [run, setRun] = React.useState<Run | null>(null);
  const [lastPrompt, setLastPrompt] = React.useState("");
  const [selectedAgent, setSelectedAgent] = React.useState("");
  const [selectedProvider, setSelectedProvider] = React.useState("");
  const [selectedModel, setSelectedModel] = React.useState("");
  const [promptMode, setPromptMode] = React.useState("build");
  const [chatState, dispatchChat] = React.useReducer(
    reduceAgentChatRuntime,
    undefined,
    () => createInitialAgentChatRuntimeState(initialMessages)
  );
  const [logs, setLogs] = React.useState<string[]>([]);
  const [diffFiles, setDiffFiles] = React.useState<RunDiffFile[]>([]);
  const [diffSource, setDiffSource] = React.useState<"run" | "session" | "vcs">("run");
  const [diffViewMode, setDiffViewMode] = React.useState<"split" | "unified">("split");
  const [centerMode, setCenterMode] = React.useState<"editor" | "diff">("editor");
  const [feedback, setFeedback] = React.useState<Feedback | null>(null);
  const [sessionSearch, setSessionSearch] = React.useState("");
  const [followUpQueue, setFollowUpQueue] = React.useState<FollowUpDraft[]>([]);
  const [diffContextParts, setDiffContextParts] = React.useState<PromptPart[]>([]);
  const [editorSelection, setEditorSelection] = React.useState<EditorSelectionContext>();

  const { tabs, activePath, selectedDiffPath, openTab, closeTab, updateTabContent, markTabSaved, setActivePath, setSelectedDiffPath } =
    useWorkbenchStore();
  const activeTab = tabs.find((tab) => tab.path === activePath);

  const workspacesQuery = useQuery({
    queryKey: ["workspaces"],
    queryFn: () => api.listWorkspaces(1, 50)
  });
  const workspaces = workspacesQuery.data?.items ?? [];
  const selectedWorkspace = workspaces.find((item) => item.workspaceId === selectedWorkspaceId) ?? workspaces[0];

  const sessionsQuery = useQuery({
    queryKey: ["sessions", selectedWorkspace?.workspaceId, sessionSearch.trim()],
    enabled: Boolean(selectedWorkspace?.workspaceId) || Boolean(sessionSearch.trim()),
    queryFn: () => {
      const query = sessionSearch.trim();
      return query ? api.listAllSessions(1, 30, query) : api.listSessions(selectedWorkspace!.workspaceId, 1, 30);
    }
  });

  const agentsQuery = useQuery({
    queryKey: ["runtime", "agents", selectedWorkspace?.workspaceId],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => api.listAgents(selectedWorkspace!.workspaceId)
  });

  const modelsQuery = useQuery({
    queryKey: ["runtime", "models", selectedWorkspace?.workspaceId],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => api.listModels(selectedWorkspace!.workspaceId)
  });

  const providersQuery = useQuery({
    queryKey: ["runtime", "providers", selectedWorkspace?.workspaceId],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => api.listProviders(selectedWorkspace!.workspaceId)
  });

  const commandsQuery = useQuery({
    queryKey: ["runtime", "commands", selectedWorkspace?.workspaceId],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => api.listCommands(selectedWorkspace!.workspaceId)
  });

  const lspStatusQuery = useQuery({
    queryKey: ["runtime", "lsp", selectedWorkspace?.workspaceId],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => api.getLspStatus(selectedWorkspace!.workspaceId),
    refetchInterval: 30000
  });

  const mcpStatusQuery = useQuery({
    queryKey: ["runtime", "mcp", "status", selectedWorkspace?.workspaceId],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => api.getMcpStatus(selectedWorkspace!.workspaceId),
    refetchInterval: 30000
  });

  const mcpResourcesQuery = useQuery({
    queryKey: ["runtime", "mcp", "resources", selectedWorkspace?.workspaceId],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => api.getMcpResources(selectedWorkspace!.workspaceId)
  });

  const mcpToolsQuery = useQuery({
    queryKey: ["runtime", "mcp", "tools", selectedWorkspace?.workspaceId, selectedProvider, selectedModel],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => {
      const model = modelIdOnly(selectedModel);
      return api.getMcpTools(selectedWorkspace!.workspaceId, selectedProvider || undefined, model || undefined);
    }
  });

  const vcsStatusQuery = useQuery({
    queryKey: ["runtime", "vcs", "status", selectedWorkspace?.workspaceId],
    enabled: Boolean(selectedWorkspace?.workspaceId),
    queryFn: () => api.getVcsStatus(selectedWorkspace!.workspaceId),
    refetchInterval: 30000
  });

  React.useEffect(() => {
    if (!selectedWorkspaceId && selectedWorkspace?.workspaceId) {
      setSelectedWorkspaceId(selectedWorkspace.workspaceId);
    }
  }, [selectedWorkspace?.workspaceId, selectedWorkspaceId]);

  React.useEffect(() => {
    setEditorSelection(undefined);
  }, [activePath]);

  React.useEffect(() => {
    if (selectedWorkspace?.workspaceId) {
      void loadDirectory("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedWorkspace?.workspaceId]);

  React.useEffect(() => {
    if (!selectedAgent && agentsQuery.data?.[0]?.agentId) {
      setSelectedAgent(agentsQuery.data[0].agentId);
    }
  }, [agentsQuery.data, selectedAgent]);

  React.useEffect(() => {
    if (!selectedProvider && providersQuery.data?.[0]?.providerId) {
      setSelectedProvider(providersQuery.data[0].providerId);
    }
  }, [providersQuery.data, selectedProvider]);

  React.useEffect(() => {
    if (!selectedModel && modelsQuery.data?.[0]) {
      setSelectedModel(modelValue(modelsQuery.data[0]));
    }
  }, [modelsQuery.data, selectedModel]);

  React.useEffect(() => {
    if (!selectedProvider || !selectedModel || selectedModel.startsWith(`${selectedProvider}/`)) {
      return;
    }
    const nextModel = modelsQuery.data?.find((model) => model.providerId === selectedProvider);
    if (nextModel) {
      setSelectedModel(modelValue(nextModel));
    }
  }, [modelsQuery.data, selectedModel, selectedProvider]);

  React.useEffect(() => {
    if (!run || !["RUNNING", "CANCELLING"].includes(run.status)) {
      return;
    }
    const subscription = subscribeRunEvents({
      baseUrl: apiBaseUrl,
      runId: run.runId,
      onEvent: (event) => handleRunEvent(event),
      onStatus: (status) => setLogs((current) => [...current.slice(-200), `[sse] ${status}`]),
      onError: () => setFeedback({ kind: "error", title: "RunEvent SSE 连接异常", description: "前端会等待浏览器自动重连" })
    });
    return () => subscription.close();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run?.runId, run?.status]);

  const createWorkspaceMutation = useMutation({
    mutationFn: (payload: { name: string; rootPath: string }) => api.createWorkspace(payload),
    onSuccess: (workspace) => {
      setSelectedWorkspaceId(workspace.workspaceId);
      void queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    },
    onError: (error) => setFeedback(errorFeedback("创建 Workspace 失败", error))
  });

  const saveMutation = useMutation({
    mutationFn: async (tab: NonNullable<typeof activeTab>) => {
      if (!selectedWorkspace) {
        throw new Error("未选择 Workspace");
      }
      await api.writeFile(selectedWorkspace.workspaceId, tab.path, tab.content);
      return tab;
    },
    onSuccess: (tab) => {
      markTabSaved(tab.path, tab.content);
      setFeedback({ kind: "success", title: "文件已保存", description: tab.path });
    },
    onError: (error) => setFeedback(errorFeedback("保存文件失败", error))
  });

  const startRunMutation = useMutation({
    mutationFn: async (input: { prompt: string; parts: PromptPart[] }) => {
      if (!selectedWorkspace) {
        throw new Error("未选择 Workspace");
      }
      const activeSession =
        session ??
        (await api.createSession(selectedWorkspace.workspaceId, `Agent ${new Date().toLocaleTimeString("zh-CN", { hour12: false })}`));
      setSession(activeSession);
      return api.startRun({
        sessionId: activeSession.sessionId,
        prompt: input.prompt,
        parts: input.parts,
        agent: selectedAgent || undefined,
        model: selectedModel || undefined,
        mode: promptMode
      });
    },
    onSuccess: (started) => {
      setRun(started);
      setLogs((current) => [...current, `[run] ${started.runId} ${started.status}`]);
    },
    onError: (error) => setFeedback(errorFeedback("启动 Run 失败", error))
  });

  const commandMutation = useMutation({
    mutationFn: async (input: { command: string; arguments: string; prompt: string }) => {
      if (!session) {
        throw new Error("当前 Session 尚未绑定远端上下文，请先发送一次普通 prompt");
      }
      return api.runSessionCommand(session.sessionId, {
        command: input.command,
        arguments: input.arguments,
        agent: selectedAgent || undefined,
        model: selectedModel || undefined
      });
    },
    onSuccess: (result) => {
      setLogs((current) => [...current.slice(-200), "[command] completed"]);
      dispatchRuntimeResult(result, dispatchChat);
    },
    onError: (error) => setFeedback(errorFeedback("命令执行失败", error))
  });

  React.useEffect(() => {
    if (followUpQueue.length === 0 || !canStartFollowUp(run, startRunMutation.isPending || commandMutation.isPending)) {
      return;
    }
    const { next, queue } = dequeueFollowUp(followUpQueue);
    if (!next) {
      return;
    }
    setFollowUpQueue(queue);
    if (next.command && session) {
      commandMutation.mutate({ ...next.command, prompt: next.prompt });
    } else {
      startRunMutation.mutate({ prompt: next.prompt, parts: next.parts });
    }
  }, [commandMutation, followUpQueue, run, session, startRunMutation]);

  const updateSessionMutation = useMutation({
    mutationFn: async (input: { sessionId: string; title?: string; pinned?: boolean }) =>
      api.updateSession(input.sessionId, { title: input.title, pinned: input.pinned }),
    onSuccess: (updated) => {
      if (session?.sessionId === updated.sessionId) {
        setSession(updated);
      }
      void queryClient.invalidateQueries({ queryKey: ["sessions"] });
    },
    onError: (error) => setFeedback(errorFeedback("更新 Session 失败", error))
  });

  const deleteSessionMutation = useMutation({
    mutationFn: async (sessionId: string) => api.deleteSession(sessionId),
    onSuccess: (deleted) => {
      if (session?.sessionId === deleted.sessionId) {
        setSession(null);
        setRun(null);
        dispatchChat({ type: "reset" });
      }
      void queryClient.invalidateQueries({ queryKey: ["sessions"] });
    },
    onError: (error) => setFeedback(errorFeedback("删除 Session 失败", error))
  });

  const cancelRunMutation = useMutation({
    mutationFn: async () => {
      if (!run) {
        throw new Error("当前没有 Run");
      }
      return api.cancelRun(run.runId);
    },
    onSuccess: (cancelled) => setRun(cancelled),
    onError: (error) => setFeedback(errorFeedback("取消 Run 失败", error))
  });

  const acceptDiffMutation = useMutation({
    mutationFn: async () => {
      if (!run) {
        throw new Error("当前没有 Run");
      }
      return api.acceptRunDiff(run.runId);
    },
    onSuccess: (result) => setFeedback({ kind: "success", title: "已接受 Run 级 Diff", description: `${result.fileCount} 个文件` }),
    onError: (error) => setFeedback(errorFeedback("接受 Diff 失败", error))
  });

  const rejectDiffMutation = useMutation({
    mutationFn: async () => {
      if (!run) {
        throw new Error("当前没有 Run");
      }
      return api.rejectRunDiff(run.runId);
    },
    onSuccess: (result) => setFeedback({ kind: "success", title: "已拒绝 Run 级 Diff", description: `${result.fileCount} 个文件` }),
    onError: (error) => setFeedback(errorFeedback("拒绝 Diff 失败", error))
  });

  const replyPermissionMutation = useMutation({
    mutationFn: async (payload: { requestId: string; decision: "once" | "always" | "reject" }) => {
      if (!session) {
        throw new Error("当前没有 Session");
      }
      return api.replySessionPermission(session.sessionId, payload.requestId, { decision: payload.decision });
    },
    onSuccess: (_result, payload) => dispatchChat({ type: "permission.replied", requestId: payload.requestId }),
    onError: (error) => setFeedback(errorFeedback("权限回复失败", error))
  });

  const replyQuestionMutation = useMutation({
    mutationFn: async (payload: { requestId: string; answers: unknown[] }) => {
      if (!session) {
        throw new Error("当前没有 Session");
      }
      return api.replySessionQuestion(session.sessionId, payload.requestId, { answers: payload.answers });
    },
    onSuccess: (_result, payload) => dispatchChat({ type: "question.replied", requestId: payload.requestId }),
    onError: (error) => setFeedback(errorFeedback("提问回复失败", error))
  });

  const rejectQuestionMutation = useMutation({
    mutationFn: async (requestId: string) => {
      if (!session) {
        throw new Error("当前没有 Session");
      }
      return api.rejectSessionQuestion(session.sessionId, requestId);
    },
    onSuccess: (_result, requestId) => dispatchChat({ type: "question.replied", requestId }),
    onError: (error) => setFeedback(errorFeedback("拒绝提问失败", error))
  });

  async function loadDirectory(path: string) {
    if (!selectedWorkspace) {
      return;
    }
    setLoadingPath(path);
    try {
      const entries = await api.listFiles(selectedWorkspace.workspaceId, path);
      setEntriesByDirectory((current) => ({ ...current, [path]: entries }));
    } catch (error) {
      setFeedback(errorFeedback("加载文件树失败", error));
    } finally {
      setLoadingPath(null);
    }
  }

  async function openFile(path: string) {
    if (!selectedWorkspace) {
      return;
    }
    setCenterMode("editor");
    try {
      const file = await api.readFile(selectedWorkspace.workspaceId, path);
      openTab({
        id: `file:${path}`,
        path,
        title: path.split("/").at(-1) ?? path,
        content: file.content,
        savedContent: file.content,
        readonly: file.readonly
      });
    } catch (error) {
      setFeedback(errorFeedback("读取文件失败", error));
    }
  }

  function toggleDirectory(path: string) {
    setExpandedDirectories((current) => {
      const next = new Set(current);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
        if (!entriesByDirectory[path]) {
          void loadDirectory(path);
        }
      }
      return next;
    });
  }

  function handleSend(prompt: string, attachments: ComposerAttachment[] = []) {
    const parts = buildPromptParts(prompt, activeTab, attachments, diffContextParts, editorSelection);
    const displayPrompt = prompt.trim() || promptFromParts(parts);
    setLastPrompt(displayPrompt);
    setDiffContextParts([]);
    dispatchChat({ type: "user.submitted", prompt: displayPrompt, parts });
    const command = parseCommand(prompt, promptMode);
    const busy = isRunBusyStatus(run?.status) || startRunMutation.isPending || commandMutation.isPending;
    if (busy) {
      setFollowUpQueue((current) => enqueueFollowUp(current, createFollowUpDraft(displayPrompt, parts, undefined, command ?? undefined)));
      setFeedback({ kind: "info", title: "Prompt 已排队", description: `等待当前 Run 完成后继续执行，队列 ${followUpQueue.length + 1} 条` });
      return;
    }
    if (command && session) {
      commandMutation.mutate({ ...command, prompt: displayPrompt });
      return;
    }
    startRunMutation.mutate({ prompt: displayPrompt, parts });
  }

  function handleRunEvent(event: RunEvent) {
    setLogs((current) => [...current.slice(-200), `[${event.seq}] ${event.type}`]);
    dispatchChat({ type: "event", event });
    notifyOnAttention(event, selectedWorkspace, session);
    if (event.type === "assistant.message.delta") {
      return;
    } else if (event.type === "diff.proposed") {
      const files = diffFilesFromPayload(event.payload);
      if (files.length) {
        setDiffSource("run");
        setDiffFiles(files);
        setSelectedDiffPath(files[0]?.path);
      } else if (run) {
        void api.getRunDiff(run.runId).then((diff) => {
          setDiffFiles(diff.files);
          setSelectedDiffPath(diff.files[0]?.path);
        });
      }
    } else if (event.type === "session.diff") {
      const files = diffFilesFromPayload(event.payload);
      if (files.length) {
        setDiffSource("session");
        setDiffFiles(files);
        setSelectedDiffPath(files[0]?.path);
      }
    } else if (event.type === "run.succeeded" || event.type === "run.failed" || event.type === "run.cancelled") {
      setRun((current) => (current ? { ...current, status: event.type === "run.succeeded" ? "SUCCEEDED" : event.type === "run.failed" ? "FAILED" : "CANCELLED" } : current));
    }
  }

  async function loadDiffSource(source: "run" | "session" | "vcs") {
    setDiffSource(source);
    setCenterMode("diff");
    try {
      const nextFiles =
        source === "run"
          ? run
            ? (await api.getRunDiff(run.runId)).files
            : []
          : source === "session"
            ? session
              ? (await api.getSessionDiff(session.sessionId)).files
              : []
            : selectedWorkspace
              ? (await api.getVcsDiffFiles(selectedWorkspace.workspaceId)).files
              : [];
      setDiffFiles(nextFiles);
      setSelectedDiffPath(nextFiles[0]?.path);
    } catch (error) {
      setFeedback(errorFeedback("加载 Diff 失败", error));
    }
  }

  async function requestNotifications() {
    if (typeof window === "undefined" || !("Notification" in window)) {
      setFeedback({ kind: "info", title: "当前浏览器不支持通知" });
      return;
    }
    const result = await Notification.requestPermission();
    setFeedback({ kind: result === "granted" ? "success" : "info", title: result === "granted" ? "通知已开启" : "通知未开启" });
  }

  async function switchSession(sessionId: string) {
    const selected = sessionsQuery.data?.items.find((item) => item.sessionId === sessionId) ?? (await api.getSession(sessionId));
    setSession(selected);
    try {
      const page = await api.listSessionMessages(sessionId, 1, 100);
      dispatchChat({ type: "reset", messages: messagesFromSessionMessages(page.items) });
      setFeedback({ kind: "info", title: "已切换 Session", description: selected.title });
    } catch (error) {
      setFeedback(errorFeedback("加载 Session 消息失败", error));
    }
  }

  const left = selectedWorkspace ? (
    <FileExplorer
      workspaceName={selectedWorkspace.name}
      entriesByDirectory={entriesByDirectory}
      expandedDirectories={expandedDirectories}
      activePath={activePath}
      changedFiles={diffFiles}
      loadingPath={loadingPath}
      onToggleDirectory={toggleDirectory}
      onOpenFile={openFile}
      onOpenDiff={(path) => {
        setSelectedDiffPath(path);
        setCenterMode("diff");
      }}
      onRefresh={() => void loadDirectory("")}
    />
  ) : (
    <WorkspaceBootstrap loading={createWorkspaceMutation.isPending} onCreate={(payload) => createWorkspaceMutation.mutate(payload)} />
  );

  const center =
    centerMode === "diff" ? (
      <DiffViewer
        files={diffFiles}
        selectedPath={selectedDiffPath}
        source={diffSource}
        viewMode={diffViewMode}
        accepting={acceptDiffMutation.isPending}
        rejecting={rejectDiffMutation.isPending}
        feedback={feedback}
        onSelectFile={setSelectedDiffPath}
        onSourceChange={(source) => void loadDiffSource(source)}
        onViewModeChange={setDiffViewMode}
        onRefresh={() => void loadDiffSource(diffSource)}
        onAcceptRun={() => acceptDiffMutation.mutate()}
        onRejectRun={() => rejectDiffMutation.mutate()}
        onCurrentFileFeedback={(action, path) =>
          setFeedback({
            kind: "info",
            title: action === "accept-current" ? "已选中当前文件接受意图" : "已选中当前文件拒绝意图",
            description: `${path} 当前版本只支持 Run 级提交`
          })
        }
        onUseHunkContext={(part) => {
          setDiffContextParts((current) => [...current, part]);
          setFeedback({ kind: "info", title: "已引用当前 hunk", description: `${part.path ?? part.name} 将随下一条 Prompt 提交` });
        }}
      />
    ) : (
      <EditorPane
        tabs={tabs}
        activePath={activePath}
        onActivate={setActivePath}
        onClose={closeTab}
        editor={
          <CodeEditor
            path={activeTab?.path}
            content={activeTab?.content}
            dirty={activeTab ? activeTab.content !== activeTab.savedContent : false}
            readonly={activeTab?.readonly}
            saving={saveMutation.isPending}
            feedback={feedback}
            onChange={(content) => activeTab && updateTabContent(activeTab.path, content)}
            onSave={() => activeTab && saveMutation.mutate(activeTab)}
            onSelectionChange={setEditorSelection}
          />
        }
      />
    );

  const right = (
    <AgentChat
      messages={chatState.messages}
      history={historyItems(run, sessionsQuery.data?.items ?? [])}
      historySearch={sessionSearch}
      running={isRunBusyStatus(run?.status) || startRunMutation.isPending || commandMutation.isPending}
      onSend={handleSend}
      onOpenDiff={() => setCenterMode("diff")}
      onRetry={() => lastPrompt && handleSend(lastPrompt)}
      onCancel={() => cancelRunMutation.mutate()}
      permissions={chatState.permissions}
      questions={chatState.questions}
      todos={chatState.todos}
      onReplyPermission={(requestId, decision) => replyPermissionMutation.mutate({ requestId, decision })}
      onReplyQuestion={(requestId, answers) => replyQuestionMutation.mutate({ requestId, answers })}
      onRejectQuestion={(requestId) => rejectQuestionMutation.mutate(requestId)}
      agents={agentsQuery.data ?? []}
      models={modelsQuery.data ?? []}
      commands={commandsQuery.data ?? []}
      providers={providersQuery.data ?? []}
      resources={runtimeResources(mcpResourcesQuery.data, activeTab)}
      tools={mcpToolsQuery.data ?? []}
      runtimeStatus={runtimeStatus(session, run, selectedAgent, selectedModel, vcsStatusQuery.data, lspStatusQuery.data, mcpStatusQuery.data, mcpToolsQuery.data, mcpResourcesQuery.data)}
      selectedAgent={selectedAgent}
      selectedProvider={selectedProvider}
      selectedModel={selectedModel}
      mode={promptMode}
      onAgentChange={setSelectedAgent}
      onProviderChange={setSelectedProvider}
      onModelChange={setSelectedModel}
      onModeChange={setPromptMode}
      onRequestNotifications={() => void requestNotifications()}
      onHistorySearchChange={setSessionSearch}
      onSelectHistory={(sessionId) => void switchSession(sessionId)}
      onToggleHistoryPin={(sessionId, pinned) => updateSessionMutation.mutate({ sessionId, pinned })}
      onDeleteHistory={(sessionId) => deleteSessionMutation.mutate(sessionId)}
    />
  );

  const bottom = <TestRunnerPanel run={run} logs={logs} onCancel={() => cancelRunMutation.mutate()} onRetry={() => lastPrompt && handleSend(lastPrompt)} />;

  return (
    <>
      <WorkbenchShell
        workspaceName={selectedWorkspace?.name ?? "未选择 Workspace"}
        branchName="local"
        runStatus={run?.status ?? "IDLE"}
        left={left}
        center={center}
        right={right}
        bottom={bottom}
      />
      <div className="pointer-events-none fixed bottom-3 left-1/2 z-50 w-[min(560px,calc(100vw-24px))] -translate-x-1/2">
        <FeedbackBanner feedback={feedback} className="pointer-events-auto rounded-md border border-slate-800" />
      </div>
    </>
  );
}

function EditorPane({
  tabs,
  activePath,
  onActivate,
  onClose,
  editor
}: {
  tabs: { path: string; title: string; content: string; savedContent: string }[];
  activePath?: string;
  onActivate: (path: string) => void;
  onClose: (path: string) => void;
  editor: React.ReactNode;
}) {
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex h-9 items-end gap-1 overflow-x-auto border-b border-slate-800 bg-slate-950 px-2">
        {tabs.map((tab) => (
          <button
            key={tab.path}
            type="button"
            className={`flex h-8 max-w-[240px] items-center gap-2 rounded-t-md border border-b-0 px-2 font-mono text-[12px] ${
              activePath === tab.path ? "border-slate-700 bg-slate-900 text-slate-100" : "border-slate-800 bg-slate-950 text-slate-500"
            }`}
            onClick={() => onActivate(tab.path)}
          >
            <span className="truncate">{tab.title}</span>
            {tab.content !== tab.savedContent ? <span className="h-1.5 w-1.5 rounded-full bg-amber-400" /> : null}
            <span
              role="button"
              tabIndex={0}
              className="rounded px-1 text-slate-500 hover:bg-red-950 hover:text-red-200"
              onClick={(event) => {
                event.stopPropagation();
                onClose(tab.path);
              }}
            >
              x
            </span>
          </button>
        ))}
      </div>
      <div className="min-h-0 flex-1">{editor}</div>
    </div>
  );
}

function WorkspaceBootstrap({
  loading,
  onCreate
}: {
  loading: boolean;
  onCreate: (payload: { name: string; rootPath: string }) => void;
}) {
  const [name, setName] = React.useState("local-tests");
  const [rootPath, setRootPath] = React.useState("");
  return (
    <div className="flex h-full flex-col justify-center gap-3 p-4">
      <div>
        <div className="text-[13px] font-semibold text-slate-100">注册 Workspace</div>
        <div className="mt-1 text-[12px] text-slate-500">选择后端可访问的本地测试项目路径</div>
      </div>
      <Input value={name} onChange={(event) => setName(event.target.value)} placeholder="名称" />
      <Input value={rootPath} onChange={(event) => setRootPath(event.target.value)} placeholder="/Users/huang/workspace/my-tests" />
      <Button variant="primary" disabled={loading || !name.trim() || !rootPath.trim()} onClick={() => onCreate({ name, rootPath })}>
        {loading ? "创建中" : "创建"}
      </Button>
    </div>
  );
}

function diffFilesFromPayload(payload: Record<string, unknown>): RunDiffFile[] {
  const raw = Array.isArray(payload.diff) ? payload.diff : Array.isArray(payload.files) ? payload.files : [];
  return raw
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      path: String(item.path ?? item.file ?? ""),
      patch: String(item.patch ?? ""),
      additions: Number(item.additions ?? 0),
      deletions: Number(item.deletions ?? 0),
      status: String(item.status ?? "modified")
    }))
    .filter((item) => item.path.length > 0);
}

function errorFeedback(title: string, error: unknown): Feedback {
  if (error instanceof BackendApiError) {
    return { kind: "error", title, description: `${error.code}: ${error.message}`, traceId: error.traceId };
  }
  return { kind: "error", title, description: error instanceof Error ? error.message : "未知错误" };
}

function historyItems(run: Run | null, sessions: Session[]) {
  return [
    ...sessions.map((item) => ({
      id: item.sessionId,
      title: item.title,
      preview: `${item.agent ?? "agent"} ${item.model?.id ?? ""}`.trim() || "Session",
      status: item.status,
      updatedAt: new Date(item.updatedAt).toLocaleTimeString("zh-CN", { hour12: false }),
      pinned: item.pinned
    })),
    {
      id: run?.runId ?? "local",
      title: run ? `Run ${run.runId}` : "本地会话",
      preview: run ? `状态 ${run.status}` : "等待发起智能体任务",
      status: run?.status ?? "IDLE",
      updatedAt: new Date().toLocaleTimeString("zh-CN", { hour12: false })
    }
  ];
}

function messagesFromSessionMessages(messages: SessionMessage[]): AgentMessage[] {
  return messages.map((message) => ({
    id: message.messageId,
    messageId: message.messageId,
    role: message.role === "USER" ? "user" : "assistant",
    text: message.content,
    createdAt: message.createdAt
  }));
}

function modelValue(model: ModelInfo) {
  return model.providerId ? `${model.providerId}/${model.id}` : model.id;
}

function modelIdOnly(value: string) {
  return value.includes("/") ? value.split("/").slice(1).join("/") : value;
}

function buildPromptParts(
  prompt: string,
  activeTab: { path: string; content: string } | undefined,
  attachments: ComposerAttachment[] = [],
  extraParts: PromptPart[] = [],
  editorSelection?: EditorSelectionContext
): PromptPart[] {
  const parts = buildComposerPromptParts(prompt, attachments);
  parts.push(...extraParts);
  const editorPart = buildEditorFilePromptPart(activeTab, editorSelection);
  if (editorPart) {
    parts.push(editorPart);
  }
  return parts;
}

function promptFromParts(parts: PromptPart[]) {
  const fileNames = parts
    .filter((part): part is Extract<PromptPart, { type: "file" }> => part.type === "file")
    .map((part) => part.name ?? part.path)
    .filter((value): value is string => Boolean(value));
  return fileNames.length ? `附件：${fileNames.join(", ")}` : "";
}

function parseCommand(prompt: string, mode: string) {
  if (mode.startsWith("command:")) {
    return { command: mode.slice("command:".length), arguments: prompt };
  }
  const match = /^\/([^\s]+)(?:\s+([\s\S]*))?$/.exec(prompt.trim());
  return match ? { command: match[1] ?? "", arguments: match[2] ?? "" } : null;
}

function runtimeResources(
  resources: RuntimeResourceInfo[] | undefined,
  activeTab: { path: string } | undefined
): RuntimeResourceInfo[] {
  const currentFile = activeTab
    ? [{ id: `editor:${activeTab.path}`, name: activeTab.path, uri: `file://${activeTab.path}`, type: "editor" }]
    : [];
  return [...currentFile, ...(resources ?? [])];
}

function runtimeStatus(
  session: Session | null,
  run: Run | null,
  selectedAgent: string,
  selectedModel: string,
  vcsStatus: unknown,
  lspStatus: unknown,
  mcpStatus: unknown,
  tools: RuntimeToolInfo[] | undefined,
  resources: RuntimeResourceInfo[] | undefined
): RuntimeStatus | undefined {
  if (!session && !run) {
    return undefined;
  }
  const branch = text(record(vcsStatus)?.branch) ?? text(record(record(vcsStatus)?.data)?.branch);
  const lspData = record(record(lspStatus)?.data) ?? record(lspStatus);
  const mcpData = record(mcpStatus);
  const mcpEntries = mcpData ? Object.values(mcpData).filter((item) => typeof item === "object" && item !== null) : [];
  const failedMcp = mcpEntries.some((item) => text(record(item)?.status) === "failed");
  const connectedMcp = mcpEntries.filter((item) => text(record(item)?.status) === "connected").length;
  return {
    sessionId: session?.sessionId ?? run?.sessionId ?? "local",
    runId: run?.runId,
    status: run?.status ?? session?.status ?? "IDLE",
    agent: selectedAgent ? { agentId: selectedAgent, name: selectedAgent } : undefined,
    model: selectedModel ? { id: modelIdOnly(selectedModel), providerId: selectedModel.includes("/") ? selectedModel.split("/")[0] : undefined } : undefined,
    tokens: session?.tokens,
    branch,
    lsp: lspData ? { status: text(lspData.status) ?? "ready", diagnostics: number(lspData.diagnostics) } : undefined,
    mcp: {
      status: failedMcp ? "failed" : connectedMcp > 0 ? "connected" : mcpEntries.length > 0 ? "available" : "unknown",
      tools: tools?.length,
      resources: resources?.length
    }
  };
}

function dispatchRuntimeResult(
  result: unknown,
  dispatch: React.Dispatch<Parameters<typeof reduceAgentChatRuntime>[1]>
) {
  const raw = record(result);
  if (!raw) {
    return;
  }
  const info = record(raw.info) ?? record(raw.message) ?? raw;
  const messageId = text(info.id) ?? text(info.messageId) ?? text(info.messageID) ?? `command-${Date.now()}`;
  const occurredAt = new Date().toISOString();
  dispatch({
    type: "event",
    event: syntheticEvent("message.updated", {
      message: {
        id: messageId,
        role: "assistant",
        text: text(info.text) ?? text(info.content) ?? "",
        createdAt: text(record(info.time)?.created) ?? occurredAt
      }
    }, occurredAt)
  });
  const parts = Array.isArray(raw.parts) ? raw.parts : [];
  parts
    .filter((part): part is Record<string, unknown> => typeof part === "object" && part !== null)
    .forEach((part, index) => {
      dispatch({
        type: "event",
        event: syntheticEvent("message.part.updated", {
          messageId,
          part: { id: text(part.id) ?? `part-${index}`, ...part }
        }, occurredAt)
      });
    });
}

function syntheticEvent(type: string, payload: Record<string, unknown>, occurredAt: string): RunEvent {
  return {
    eventId: `local-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    runId: "local",
    seq: Date.now(),
    type,
    traceId: "trace_local",
    occurredAt,
    payload
  };
}

function notifyOnAttention(event: RunEvent, workspace: Workspace | undefined, session: Session | null) {
  if (typeof window === "undefined" || !("Notification" in window) || Notification.permission !== "granted") {
    return;
  }
  if (event.type === "permission.asked" || event.type === "question.asked") {
    const title = event.type === "permission.asked" ? "权限请求" : "Agent 提问";
    const body = `${workspace?.name ?? "Workspace"} / ${session?.title ?? "Session"}`;
    new Notification(title, { body });
  }
  if (event.type === "run.succeeded" || event.type === "run.failed") {
    new Notification(event.type === "run.succeeded" ? "Run 完成" : "Run 失败", { body: session?.title ?? event.runId });
  }
}

function record(value: unknown) {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function text(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function number(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

const initialMessages: AgentMessage[] = [
  {
    id: "welcome",
    role: "assistant",
    text: "已连接平台后端后，可以注册 Workspace、打开测试文件、发送 Agent 任务并查看 Diff。",
    createdAt: new Date().toISOString()
  }
];
