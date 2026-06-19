"use client";

import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as React from "react";
import { AgentChat } from "@test-agent/agent-chat";
import { BackendApiError, createBackendApiClient } from "@test-agent/backend-api";
import { DiffViewer } from "@test-agent/diff-viewer";
import { CodeEditor } from "@test-agent/editor";
import { subscribeRunEvents } from "@test-agent/event-stream-client";
import { FileExplorer } from "@test-agent/file-explorer";
import type { AgentMessage, FileTreeEntry, Run, RunDiffFile, RunEvent, Session, Workspace } from "@test-agent/shared-types";
import { TestRunnerPanel } from "@test-agent/test-runner";
import { Button, FeedbackBanner, Input, type Feedback } from "@test-agent/ui-kit";
import { useWorkbenchStore, WorkbenchShell } from "@test-agent/workbench-shell";

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
  const [messages, setMessages] = React.useState<AgentMessage[]>(initialMessages);
  const [logs, setLogs] = React.useState<string[]>([]);
  const [diffFiles, setDiffFiles] = React.useState<RunDiffFile[]>([]);
  const [centerMode, setCenterMode] = React.useState<"editor" | "diff">("editor");
  const [feedback, setFeedback] = React.useState<Feedback | null>(null);

  const { tabs, activePath, selectedDiffPath, openTab, closeTab, updateTabContent, markTabSaved, setActivePath, setSelectedDiffPath } =
    useWorkbenchStore();
  const activeTab = tabs.find((tab) => tab.path === activePath);

  const workspacesQuery = useQuery({
    queryKey: ["workspaces"],
    queryFn: () => api.listWorkspaces(1, 50)
  });
  const workspaces = workspacesQuery.data?.items ?? [];
  const selectedWorkspace = workspaces.find((item) => item.workspaceId === selectedWorkspaceId) ?? workspaces[0];

  React.useEffect(() => {
    if (!selectedWorkspaceId && selectedWorkspace?.workspaceId) {
      setSelectedWorkspaceId(selectedWorkspace.workspaceId);
    }
  }, [selectedWorkspace?.workspaceId, selectedWorkspaceId]);

  React.useEffect(() => {
    if (selectedWorkspace?.workspaceId) {
      void loadDirectory("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedWorkspace?.workspaceId]);

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
    mutationFn: async (prompt: string) => {
      if (!selectedWorkspace) {
        throw new Error("未选择 Workspace");
      }
      const activeSession =
        session ??
        (await api.createSession(selectedWorkspace.workspaceId, `Agent ${new Date().toLocaleTimeString("zh-CN", { hour12: false })}`));
      setSession(activeSession);
      return api.startRun(activeSession.sessionId, prompt);
    },
    onSuccess: (started) => {
      setRun(started);
      setLogs((current) => [...current, `[run] ${started.runId} ${started.status}`]);
    },
    onError: (error) => setFeedback(errorFeedback("启动 Run 失败", error))
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

  function handleSend(prompt: string) {
    setLastPrompt(prompt);
    setMessages((current) => [
      ...current,
      { id: `user-${Date.now()}`, role: "user", text: prompt, createdAt: new Date().toISOString() }
    ]);
    startRunMutation.mutate(prompt);
  }

  function handleRunEvent(event: RunEvent) {
    setLogs((current) => [...current.slice(-200), `[${event.seq}] ${event.type}`]);
    if (event.type === "assistant.message.delta") {
      appendAssistantDelta(String(event.payload.text ?? event.payload.delta ?? ""));
    } else if (event.type === "tool.started" || event.type === "tool.finished") {
      appendCard("tool", event.type === "tool.started" ? "工具调用开始" : "工具调用完成", event.payload);
    } else if (event.type === "test.finished") {
      appendCard("test", "测试运行完成", event.payload);
    } else if (event.type === "diff.proposed") {
      const files = diffFilesFromPayload(event.payload);
      if (files.length) {
        setDiffFiles(files);
        setSelectedDiffPath(files[0]?.path);
      } else if (run) {
        void api.getRunDiff(run.runId).then((diff) => {
          setDiffFiles(diff.files);
          setSelectedDiffPath(diff.files[0]?.path);
        });
      }
      appendCard("diff", "Agent 提出了文件修改", { files });
    } else if (event.type === "run.succeeded" || event.type === "run.failed" || event.type === "run.cancelled") {
      setRun((current) => (current ? { ...current, status: event.type === "run.succeeded" ? "SUCCEEDED" : event.type === "run.failed" ? "FAILED" : "CANCELLED" } : current));
    } else if (event.type === "diff.accepted" || event.type === "diff.rejected") {
      appendCard("event", event.type === "diff.accepted" ? "Diff 已接受" : "Diff 已拒绝", event.payload);
    }
  }

  function appendAssistantDelta(text: string) {
    if (!text) {
      return;
    }
    setMessages((current) => {
      const last = current.at(-1);
      if (last?.role === "assistant") {
        return [...current.slice(0, -1), { ...last, text: `${last.text}${text}` }];
      }
      return [...current, { id: `assistant-${Date.now()}`, role: "assistant", text, createdAt: new Date().toISOString() }];
    });
  }

  function appendCard(cardType: Extract<AgentMessage, { role: "card" }>["cardType"], title: string, payload: Record<string, unknown>) {
    setMessages((current) => [
      ...current,
      { id: `${cardType}-${Date.now()}-${current.length}`, role: "card", cardType, title, payload, createdAt: new Date().toISOString() }
    ]);
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
        accepting={acceptDiffMutation.isPending}
        rejecting={rejectDiffMutation.isPending}
        feedback={feedback}
        onSelectFile={setSelectedDiffPath}
        onAcceptRun={() => acceptDiffMutation.mutate()}
        onRejectRun={() => rejectDiffMutation.mutate()}
        onCurrentFileFeedback={(action, path) =>
          setFeedback({
            kind: "info",
            title: action === "accept-current" ? "已选中当前文件接受意图" : "已选中当前文件拒绝意图",
            description: `${path} 当前版本只支持 Run 级提交`
          })
        }
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
          />
        }
      />
    );

  const right = (
    <AgentChat
      messages={messages}
      history={historyItems(run)}
      running={run?.status === "RUNNING" || run?.status === "CANCELLING"}
      onSend={handleSend}
      onOpenDiff={() => setCenterMode("diff")}
      onRetry={() => lastPrompt && handleSend(lastPrompt)}
      onCancel={() => cancelRunMutation.mutate()}
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

function historyItems(run: Run | null) {
  return [
    {
      id: run?.runId ?? "local",
      title: run ? `Run ${run.runId}` : "本地会话",
      preview: run ? `状态 ${run.status}` : "等待发起智能体任务",
      status: run?.status ?? "IDLE",
      updatedAt: new Date().toLocaleTimeString("zh-CN", { hour12: false })
    }
  ];
}

const initialMessages: AgentMessage[] = [
  {
    id: "welcome",
    role: "assistant",
    text: "已连接平台后端后，可以注册 Workspace、打开测试文件、发送 Agent 任务并查看 Diff。",
    createdAt: new Date().toISOString()
  }
];
