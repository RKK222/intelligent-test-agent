import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, within } from "@testing-library/vue";
import { nextTick } from "vue";
import type { AgentMessage, MessagePart } from "@test-agent/shared-types";
import OpencodeTimeline from "../src/opencode-like/components/OpencodeTimeline.vue";
import { createOpencodeLikeState } from "../src/opencode-like/state/adapter";
import AssistantThread from "../src/AssistantThread.vue";

const waitMarkdown = () => new Promise((resolve) => setTimeout(resolve, 400));

describe("OpencodeTimeline", () => {
  it("shows the assistant avatar for the initial thinking state", () => {
    const state = createOpencodeLikeState({
      messages: [],
      running: true
    });

    const { container, getByText } = render(OpencodeTimeline, { props: { state } });

    expect(getByText("思考中")).toBeTruthy();
    expect(container.querySelectorAll(".oc-assistant-frame__avatar")).toHaveLength(1);
    expect(container.querySelectorAll(".oc-assistant-frame__meta")).toHaveLength(0);
  });

  it("renders user rows, context tool groups, assistant parts and diff summary with oc classes", async () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "分析 checkout 失败"),
        assistantMessage("msg_assistant_1", [
          toolPart("part_read", "read", { filePath: "README.md" }),
          toolPart("part_grep", "grep", { pattern: "checkout", path: "src" }),
          textPart("part_answer", "定位到 checkout 表单校验失败。")
        ])
      ],
      diffFiles: [{ path: "src/checkout.ts", patch: "@@\n-old\n+new", additions: 1, deletions: 1, status: "modified" }]
    });

    const { container, getByText } = render(OpencodeTimeline, { props: { state } });
    await waitMarkdown();

    expect(container.querySelector(".oc-timeline-root")).toBeTruthy();
    expect(container.querySelector(".oc-user-message")).toBeTruthy();
    expect(container.querySelector(".oc-assistant-frame")).toBeTruthy();
    expect(container.querySelector(".oc-assistant-frame__meta")).toBeNull();
    expect(container.querySelector(".oc-context-group")).toBeTruthy();
    expect(container.querySelector(".oc-assistant-part")).toBeTruthy();
    expect(container.querySelector(".oc-diff-summary")).toBeTruthy();
    expect(getByText("文件修改 1")).toBeTruthy();
    expect(getByText("分析 checkout 失败")).toBeTruthy();
    expect(getByText("探索")).toBeTruthy();
    expect(getByText("读取 2 次")).toBeTruthy();
    expect(container.querySelector(".oc-context-group__trigger .oc-tool__status")?.textContent).toBe("已读取");
    await fireEvent.click(container.querySelector(".oc-context-group__trigger") as HTMLElement);
    expect(getByText("README.md")).toBeTruthy();
    expect(container.querySelector(".oc-text-part")).toBeTruthy();
    expect(container.querySelector(".oc-text-part .oc-icon-button")).toBeTruthy();
    expect(getByText("定位到 checkout 表单校验失败。")).toBeTruthy();
    await fireEvent.click(container.querySelector(".oc-diff-summary__header") as HTMLElement);
    expect(getByText("src/checkout.ts")).toBeTruthy();
  });

  it("keeps diff files collapsed by default and updates folded line totals when files change", async () => {
    vi.useFakeTimers();
    try {
      const initialState = createOpencodeLikeState({
        messages: [userMessage("msg_user_1", "生成文件")],
        diffFiles: [
          { path: "src/a.ts", patch: "", additions: 2, deletions: 1, status: "modified" },
          { path: "src/b.ts", patch: "", additions: 3, deletions: 4, status: "modified" }
        ]
      });

      const { container, getByText, queryByText, rerender } = render(OpencodeTimeline, {
        props: { state: initialState }
      });

      expect(container.querySelector(".oc-diff-summary__header")?.getAttribute("aria-expanded")).toBe("false");
      expect(getByText("+5")).toBeTruthy();
      expect(getByText("-5")).toBeTruthy();
      expect(queryByText("src/a.ts")).toBeNull();

      await fireEvent.click(container.querySelector(".oc-diff-summary__header") as HTMLElement);
      expect(container.querySelector(".oc-diff-summary__header")?.getAttribute("aria-expanded")).toBe("true");
      expect(getByText("src/a.ts")).toBeTruthy();

      await fireEvent.click(container.querySelector(".oc-diff-summary__header") as HTMLElement);
      expect(container.querySelector(".oc-diff-summary__header")?.getAttribute("aria-expanded")).toBe("false");

      const updatedState = createOpencodeLikeState({
        messages: [userMessage("msg_user_1", "生成文件")],
        diffFiles: [
          { path: "src/a.ts", patch: "", additions: 10, deletions: 0, status: "modified" },
          { path: "src/c.ts", patch: "", additions: 1, deletions: 2, status: "modified" }
        ]
      });
      await rerender({ state: updatedState });

      expect(getByText("+11")).toBeTruthy();
      expect(getByText("-2")).toBeTruthy();
      expect(queryByText("src/c.ts")).toBeNull();
      expect(container.querySelector(".oc-diff-summary__totals")?.classList.contains("is-bumping")).toBe(true);

      vi.advanceTimersByTime(360);
      await nextTick();

      expect(container.querySelector(".oc-diff-summary__totals")?.classList.contains("is-bumping")).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it("shows the conversation locator only after more than three user turns", async () => {
    const { container, rerender } = render(OpencodeTimeline, {
      props: {
        state: createOpencodeLikeState({
          messages: conversationMessages(3)
        })
      }
    });

    expect(container.querySelector('[data-testid="oc-conversation-locator-trigger"]')).toBeNull();

    await rerender({
      state: createOpencodeLikeState({
        messages: conversationMessages(4)
      })
    });

    expect(container.querySelector('[data-testid="oc-conversation-locator-trigger"]')).toBeTruthy();
  });

  it("opens a locator panel with turn summaries and file chips", async () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "第一轮：读取项目文档"),
        assistantMessage("msg_assistant_1", [textPart("part_answer_1", "已读取项目入口文档。")]),
        userMessage("msg_user_2", "第二轮：检查文件变更"),
        assistantMessage("msg_assistant_2", [
          textPart("part_answer_2", "已实现并提交：修复主子 Agent 时间线切换卡顿。"),
          filePart("part_file_1", "README.md"),
          toolPart("part_read_1", "read", { filePath: "session-log.md" }),
          toolPart("part_edit_1", "edit", { path: "src/main.ts" }),
          toolPart("part_write_1", "write", { file_path: "docs/README.md" })
        ]),
        userMessage("msg_user_3", "第三轮：补充验证"),
        assistantMessage("msg_assistant_3", [reasoningPart("part_reasoning_3", "先确认交互边界。")]),
        userMessage("msg_user_4", "第四轮：继续"),
        assistantMessage("msg_assistant_4", [textPart("part_answer_4", "超过三轮后显示定位器。")])
      ]
    });

    const { container, getByTestId } = render(OpencodeTimeline, { props: { state } });

    await fireEvent.click(getByTestId("oc-conversation-locator-trigger"));

    const panel = document.body.querySelector('[data-testid="oc-conversation-locator-panel"]') as HTMLElement;
    expect(panel).toBeTruthy();
    const panelQueries = within(panel);
    expect(panelQueries.getByText("第二轮：检查文件变更")).toBeTruthy();
    expect(panelQueries.getByText(/已实现并提交/)).toBeTruthy();
    expect(panelQueries.getByText("README.md")).toBeTruthy();
    expect(panelQueries.getByText("session-log.md")).toBeTruthy();
    expect(panelQueries.getByText("+2")).toBeTruthy();
    expect(container.querySelector(".oc-conversation-locator")).toBeTruthy();
  });

  it("scrolls to the selected turn from the locator panel", async () => {
    const scrollIntoView = vi.fn();
    const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
    HTMLElement.prototype.scrollIntoView = scrollIntoView;
    try {
      const state = createOpencodeLikeState({
        messages: conversationMessages(4)
      });

      const { getByTestId } = render(OpencodeTimeline, { props: { state } });

      await fireEvent.click(getByTestId("oc-conversation-locator-trigger"));
      const panel = document.body.querySelector('[data-testid="oc-conversation-locator-panel"]') as HTMLElement;
      await fireEvent.click(within(panel).getByRole("button", { name: "定位到第 3 轮对话" }));

      expect(scrollIntoView).toHaveBeenCalledTimes(1);
    } finally {
      HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
    }
  });

  it("shows the conversation locator through AssistantThread main rendering path", () => {
    const { container } = render(AssistantThread, {
      props: {
        messages: conversationMessages(4),
        commands: [],
        resources: [],
        running: false
      }
    });

    expect(container.querySelector('[data-testid="oc-conversation-locator-trigger"]')).toBeTruthy();
  });

  it("renders task tool parts as clickable subagent cards", async () => {
    const onSelectSubagent = vi.fn();
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "分析前后端结构"),
        assistantMessage("msg_assistant_1", [
          {
            ...toolPart("prt_task_backend", "task", {
              description: "Explore backend structure",
              subagent_type: "explore"
            }),
            callId: "call_task_backend",
            status: "running"
          }
        ])
      ],
      subagentsBySessionId: {
        ses_child_backend: {
          sessionId: "ses_child_backend",
          parentSessionId: "ses_root",
          taskMessageId: "msg_assistant_1",
          taskPartId: "prt_task_backend",
          taskCallId: "call_task_backend",
          agentName: "Explore",
          title: "Explore backend structure",
          status: "running",
          updatedAt: "2026-07-03T00:00:00Z"
        }
      },
      subagentByTaskPartId: { prt_task_backend: "ses_child_backend" }
    } as any);

    const { container, getByText } = render(OpencodeTimeline, {
      props: { state, onSelectSubagent }
    });

    expect(container.querySelector(".oc-subagent-card")).toBeTruthy();
    expect(getByText("Explore")).toBeTruthy();
    expect(getByText("Explore backend structure")).toBeTruthy();

    await fireEvent.click(container.querySelector(".oc-subagent-card") as HTMLElement);

    expect(onSelectSubagent).toHaveBeenCalledWith("ses_child_backend");
  });

  it("renders multiple task subagent cards directly without folding them into a tool group", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "分析前后端结构"),
        assistantMessage("msg_assistant_1", [
          {
            ...toolPart("prt_task_backend", "task", {
              description: "Explore backend structure",
              subagent_type: "explore"
            }),
            status: "running"
          },
          {
            ...toolPart("prt_task_frontend", "task", {
              description: "Explore frontend structure",
              subagent_type: "explore"
            }),
            status: "running"
          }
        ])
      ],
      subagentsBySessionId: {
        ses_backend: {
          sessionId: "ses_backend",
          taskMessageId: "msg_assistant_1",
          taskPartId: "prt_task_backend",
          agentName: "Explore",
          title: "Explore backend structure",
          status: "running",
          updatedAt: "2026-07-03T00:00:00Z"
        },
        ses_frontend: {
          sessionId: "ses_frontend",
          taskMessageId: "msg_assistant_1",
          taskPartId: "prt_task_frontend",
          agentName: "Explore",
          title: "Explore frontend structure",
          status: "running",
          updatedAt: "2026-07-03T00:00:00Z"
        }
      },
      subagentByTaskPartId: {
        prt_task_backend: "ses_backend",
        prt_task_frontend: "ses_frontend"
      }
    } as any);

    const { container, getByText } = render(OpencodeTimeline, { props: { state } });

    expect(container.querySelector("[data-testid='oc-tool-group']")).toBeNull();
    expect(container.querySelectorAll(".oc-subagent-card")).toHaveLength(2);
    expect(getByText("Explore backend structure")).toBeTruthy();
    expect(getByText("Explore frontend structure")).toBeTruthy();
  });

  it("renders pending native task parts as disabled agent rows", () => {
    const onSelectSubagent = vi.fn();
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "分析项目结构"),
        assistantMessage("msg_assistant_1", [
          {
            ...toolPart("prt_task_pending", "task", {}),
            status: "pending"
          }
        ])
      ]
    });

    const { container, getByText } = render(OpencodeTimeline, {
      props: { state, onSelectSubagent }
    });

    expect(container.querySelector(".oc-subagent-card")).toBeTruthy();
    expect(container.querySelector(".oc-subagent-card")?.hasAttribute("disabled")).toBe(true);
    expect(getByText("智能体")).toBeTruthy();
    expect(getByText("准备中")).toBeTruthy();
    expect(onSelectSubagent).not.toHaveBeenCalled();
  });

  it("switches between a large root context group and a child timeline without blank output placeholders", async () => {
    const rootReadParts = Array.from({ length: 88 }, (_, index) =>
      toolPart(`prt_read_${index}`, "read", { filePath: `frontend/src/file-${index}.ts` })
    );
    const messages: AgentMessage[] = [
      userMessage("msg_user_root", "分析前端结构"),
      assistantMessage("msg_root", [
        ...rootReadParts,
        textPart("prt_empty_root_1", "", "running"),
        textPart("prt_empty_root_2", "   ", "running"),
        {
          ...toolPart("prt_task_frontend", "task", {
            description: "Explore frontend structure",
            subagent_type: "explore"
          }),
          callId: "call_task_frontend",
          status: "running"
        }
      ]),
      userMessage("msg_child_user", "Explore frontend structure"),
      assistantMessage("msg_child_answer", [
        toolPart("prt_child_read", "read", { filePath: "frontend/README.md" }),
        textPart("prt_empty_child", "", "running"),
        textPart("prt_child_answer", "子 Agent 已读取前端目录。")
      ])
    ];
    const messageScopesById = {
      msg_user_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
      msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
      msg_child_user: {
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task_frontend"
      },
      msg_child_answer: {
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task_frontend"
      }
    };
    const subagentsBySessionId = {
      ses_child: {
        sessionId: "ses_child",
        parentSessionId: "ses_root",
        taskMessageId: "msg_root",
        taskPartId: "prt_task_frontend",
        taskCallId: "call_task_frontend",
        agentName: "Explore",
        title: "Explore frontend structure",
        status: "running",
        updatedAt: "2026-07-03T00:00:00Z"
      }
    };

    const { container, getByText, queryByText, queryAllByText } = render(AssistantThread, {
      props: {
        messages,
        commands: [],
        resources: [],
        running: true,
        messageScopesById,
        subagentsBySessionId,
        subagentByTaskPartId: { prt_task_frontend: "ses_child" }
      }
    });

    expect(getByText("读取 88 次")).toBeTruthy();
    expect(queryByText("子 Agent 已读取前端目录。")).toBeNull();

    await fireEvent.click(container.querySelector(".oc-subagent-card") as HTMLElement);
    await waitMarkdown();

    expect(getByText("子 Agent 已读取前端目录。")).toBeTruthy();
    expect(queryByText("读取 88 次")).toBeNull();

    await fireEvent.click(getByText("切换到主 Agent"));
    await waitMarkdown();

    expect(getByText("读取 88 次")).toBeTruthy();
    expect(queryByText("子 Agent 已读取前端目录。")).toBeNull();
    expect(queryAllByText("准备输出…")).toHaveLength(0);
    expect(queryByText("无内容")).toBeNull();
  });

  it("renders running text as a lightweight live preview instead of a Markdown loading placeholder", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "持续输出"),
        assistantMessage("msg_assistant_1", [textPart("part_streaming", "正在输出第一段", "running")])
      ],
      running: true
    });

    const { container, getByText, queryByText } = render(OpencodeTimeline, { props: { state } });

    expect(getByText("正在输出第一段")).toBeTruthy();
    expect(getByText("生成中")).toBeTruthy();
    expect(queryByText("准备输出…")).toBeNull();
    expect(container.querySelector(".markdown-body")).toBeNull();
  });

  it("shows one lightweight working row in a running child timeline before text output starts", async () => {
    const messages: AgentMessage[] = [
      userMessage("msg_user_root", "分析前端结构"),
      assistantMessage("msg_root", [
        {
          ...toolPart("prt_task_frontend", "task", {
            description: "Explore frontend structure",
            subagent_type: "explore"
          }),
          callId: "call_task_frontend",
          status: "running"
        }
      ]),
      userMessage("msg_child_user", "Explore frontend structure"),
      assistantMessage("msg_child_tools", [
        toolPart("prt_child_read", "read", { filePath: "frontend/README.md" }),
        textPart("prt_child_empty", "", "running")
      ])
    ];
    const messageScopesById = {
      msg_user_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
      msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
      msg_child_user: {
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task_frontend"
      },
      msg_child_tools: {
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task_frontend"
      }
    };
    const subagentsBySessionId = {
      ses_child: {
        sessionId: "ses_child",
        parentSessionId: "ses_root",
        taskMessageId: "msg_root",
        taskPartId: "prt_task_frontend",
        taskCallId: "call_task_frontend",
        agentName: "Explore",
        title: "Explore frontend structure",
        status: "running",
        updatedAt: "2026-07-03T00:00:00Z"
      }
    };

    const { container, getByText, queryAllByText } = render(AssistantThread, {
      props: {
        messages,
        commands: [],
        resources: [],
        running: true,
        messageScopesById,
        subagentsBySessionId,
        subagentByTaskPartId: { prt_task_frontend: "ses_child" }
      }
    });

    await fireEvent.click(container.querySelector(".oc-subagent-card") as HTMLElement);

    expect(getByText("正在工作")).toBeTruthy();
    expect(getByText("等待后续输出")).toBeTruthy();
    expect(queryAllByText("正在工作")).toHaveLength(1);
    expect(container.querySelector(".oc-working-status")).toBeTruthy();
  });

  it("uses the opencode-like timeline as the AssistantThread main rendering path", async () => {
    const messages: AgentMessage[] = [
      userMessage("msg_user_1", "分析 checkout 失败"),
      assistantMessage("msg_assistant_1", [textPart("part_answer", "定位到 checkout 表单校验失败。")])
    ];

    const { container, getByText } = render(AssistantThread, {
      props: {
        messages,
        commands: [],
        resources: [],
        running: false
      }
    });
    await waitMarkdown();

    expect(container.querySelector(".oc-timeline-root")).toBeTruthy();
    expect(container.querySelector(".oc-assistant-part")).toBeTruthy();
    expect(container.querySelector(".bg-\\[var\\(--ta-chat-message-bg\\)\\]")).toBeNull();
    expect(getByText("定位到 checkout 表单校验失败。")).toBeTruthy();
  });

  it("shows a collapsible todo panel above the composer", async () => {
    const messages: AgentMessage[] = [userMessage("msg_user_1", "实现 Todo 展示")];
    const { container, getByText, queryByText } = render(AssistantThread, {
      props: {
        messages,
        commands: [],
        resources: [],
        running: false,
        todos: [
          { id: "todo_1", text: "分析 SSE 字段", status: "pending", priority: "high" },
          { id: "todo_2", text: "实现面板", status: "in_progress", priority: "medium" },
          { id: "todo_3", text: "补充测试", status: "completed", priority: "low" },
          { id: "todo_4", text: "移除旧入口", status: "cancelled", priority: "low" },
          { id: "todo_5", text: "未知状态兼容", status: "blocked", priority: "high" }
        ]
      }
    });

    const thread = container.querySelector(".ta-assistant-thread");
    const todoPanel = container.querySelector(".oc-todo-panel");
    const composer = container.querySelector(".ta-composer-form");
    expect(todoPanel).toBeTruthy();
    expect(composer).toBeTruthy();
    expect(todoPanel?.parentElement).toBe(thread);
    expect(Array.from(thread?.children ?? []).indexOf(todoPanel as Element)).toBeLessThan(
      Array.from(thread?.children ?? []).indexOf(composer as Element)
    );
    expect(getByText("待处理 1")).toBeTruthy();
    expect(getByText("进行中 1")).toBeTruthy();
    expect(getByText("已完成 1")).toBeTruthy();
    expect(getByText("已取消 1")).toBeTruthy();
    expect(getByText("其他 1")).toBeTruthy();
    expect(getByText("共 5")).toBeTruthy();
    expect(queryByText("分析 SSE 字段")).toBeNull();

    await fireEvent.click(container.querySelector(".oc-todo-panel__header") as HTMLElement);

    expect(getByText("分析 SSE 字段")).toBeTruthy();
    expect(getByText("未知状态兼容")).toBeTruthy();
    expect(getByText("blocked")).toBeTruthy();
  });

  it("shows one assistant header for split assistant messages in the same user turn", async () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "你是谁"),
        assistantMessage("msg_assistant_reasoning", [reasoningPart("part_reasoning", "先判断用户意图。")]),
        assistantMessage("msg_assistant_tool", [toolPart("part_skill", "skill", { name: "using-superpowers" })]),
        assistantMessage("msg_assistant_answer", [textPart("part_answer", "我是测试智能体。")])
      ]
    });

    const { container, getByText } = render(OpencodeTimeline, { props: { state } });
    await waitMarkdown();

    expect(container.querySelectorAll(".oc-assistant-frame__avatar")).toHaveLength(1);
    expect(container.querySelectorAll(".oc-assistant-frame__meta")).toHaveLength(0);
    expect(getByText("我是测试智能体。")).toBeTruthy();
  });

  it("merges repeated reasoning rows across split assistant messages in one turn", async () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "你能干什么"),
        assistantMessage("msg_reasoning_1", [reasoningPart("part_reasoning_1", "先识别用户问题。")]),
        assistantMessage("msg_skill", [toolPart("part_skill", "skill", { name: "using-superpowers" })]),
        assistantMessage("msg_reasoning_2", [reasoningPart("part_reasoning_2", "再读取相关能力说明。")]),
        assistantMessage("msg_web", [toolPart("part_web", "webfetch", { url: "https://opencode.ai" })]),
        assistantMessage("msg_reasoning_3", [reasoningPart("part_reasoning_3", "最后组织回答。")]),
        assistantMessage("msg_answer", [textPart("part_answer", "我可以协助软件工程任务。")])
      ]
    });

    const { container, getByText } = render(OpencodeTimeline, { props: { state } });
    await waitMarkdown();

    expect(container.querySelectorAll(".oc-assistant-frame__avatar")).toHaveLength(1);
    expect(container.querySelectorAll(".oc-assistant-frame__meta")).toHaveLength(0);
    expect(container.querySelectorAll(".oc-reasoning-part .oc-disclosure__trigger")).toHaveLength(1);
    expect(container.querySelector(".oc-reasoning-part .oc-tool__status")?.textContent).toBe("已完成");
    expect(container.querySelector(".oc-reasoning-part .oc-tool__subtitle")?.textContent).toContain("最后组织回答。");
    expect(getByText("思考状态")).toBeTruthy();

    await fireEvent.click(container.querySelector(".oc-reasoning-part .oc-disclosure__trigger") as HTMLElement);
    await waitMarkdown();
    expect(getByText("先识别用户问题。")).toBeTruthy();
    expect(getByText("再读取相关能力说明。")).toBeTruthy();
    expect(getByText("最后组织回答。")).toBeTruthy();
  });

  it("merges repeated tool rows by tool type in one user turn", async () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "查找需求文档"),
        assistantMessage("msg_bash_1", [toolPart("part_bash_1", "bash", { command: "find . -name '*.md'" })]),
        assistantMessage("msg_read_1", [
          { ...toolPart("part_read_1", "read", { filePath: "/very/long/path/a.md" }), status: "failed" }
        ]),
        assistantMessage("msg_bash_2", [toolPart("part_bash_2", "bash", { command: "ls -la" })]),
        assistantMessage("msg_skill_1", [toolPart("part_skill_1", "skill", { name: "frontend-design" })]),
        assistantMessage("msg_skill_2", [toolPart("part_skill_2", "skill", { name: "code-reuse-first" })]),
        assistantMessage("msg_read_2", [
          { ...toolPart("part_read_2", "read", { filePath: "/very/long/path/b.md" }), status: "failed" }
        ]),
        assistantMessage("msg_answer", [textPart("part_answer", "已找到相关文档。")])
      ]
    });

    const { container, getByText } = render(OpencodeTimeline, { props: { state } });
    await waitMarkdown();

    expect(container.querySelectorAll('[data-testid="oc-tool-group"]')).toHaveLength(3);
    expect(container.querySelectorAll(".oc-tool-group__trigger")).toHaveLength(3);
    expect(getByText("命令行")).toBeTruthy();
    expect(getByText("技能")).toBeTruthy();
    expect(container.querySelectorAll(".oc-tool-group__trigger .oc-tool__subtitle")[0]?.textContent).toBe("2 次");
    expect(container.querySelectorAll(".oc-tool-group__trigger .oc-tool__status")[0]?.textContent).toBe("已读取");
    expect(container.querySelectorAll(".oc-tool-group__trigger .oc-tool__status")[1]?.textContent).toBe("失败");
    expect(getByText("已找到相关文档。")).toBeTruthy();
  });

  it("allows user to interrupt auto-scrolling by scrolling up", async () => {
    const initialMessages: AgentMessage[] = [
      userMessage("msg_user_1", "分析 checkout 失败"),
      assistantMessage("msg_assistant_1", [textPart("part_answer", "第一段描述")])
    ];

    const { container, rerender, queryByText } = render(AssistantThread, {
      props: {
        messages: initialMessages,
        commands: [],
        resources: [],
        running: true
      }
    });
    await waitMarkdown();

    const viewport = container.querySelector('[data-testid="agent-thread-viewport"]') as HTMLElement;
    expect(viewport).toBeTruthy();

    // 劫持原型链，防止 jsdom DOM patch 阶段重置 layout 属性导致误判 atBottom
    const originalScrollHeight = Object.getOwnPropertyDescriptor(Element.prototype, "scrollHeight");
    const originalClientHeight = Object.getOwnPropertyDescriptor(Element.prototype, "clientHeight");

    Object.defineProperty(Element.prototype, "scrollHeight", {
      get() { return 500; },
      configurable: true
    });
    Object.defineProperty(Element.prototype, "clientHeight", {
      get() { return 300; },
      configurable: true
    });
    viewport.scrollTop = 100; // 500 - 100 - 300 = 100 > 36 (非贴底)

    await fireEvent.scroll(viewport);

    // 模拟流式增量新消息到来
    const updatedMessages: AgentMessage[] = [
      userMessage("msg_user_1", "分析 checkout 失败"),
      assistantMessage("msg_assistant_1", [
        textPart("part_answer", "第一段描述"),
        textPart("part_answer2", "新增的流式回答")
      ])
    ];
    await rerender({ messages: updatedMessages });
    await waitMarkdown();

    try {
      expect(queryByText("查看新内容")).toBeTruthy();
    } finally {
      // 还原原型链描述符
      if (originalScrollHeight) {
        Object.defineProperty(Element.prototype, "scrollHeight", originalScrollHeight);
      } else {
        delete (Element.prototype as any).scrollHeight;
      }
      if (originalClientHeight) {
        Object.defineProperty(Element.prototype, "clientHeight", originalClientHeight);
      } else {
        delete (Element.prototype as any).clientHeight;
      }
    }
  });
});

function userMessage(id: string, text: string): Extract<AgentMessage, { role: "user" }> {
  return { id, messageId: id, role: "user", text, createdAt: "2026-07-03T00:00:00Z" };
}

function assistantMessage(id: string, parts: MessagePart[]): Extract<AgentMessage, { role: "assistant" }> {
  return { id, messageId: id, role: "assistant", text: "", parts, createdAt: "2026-07-03T00:00:01Z" };
}

function textPart(partId: string, text: string, status = "completed"): Extract<MessagePart, { type: "text" }> {
  return { partId, type: "text", text, status };
}

function reasoningPart(partId: string, text: string): Extract<MessagePart, { type: "reasoning" }> {
  return { partId, type: "reasoning", text, status: "completed" };
}

function toolPart(
  partId: string,
  toolName: string,
  input: Record<string, unknown>
): Extract<MessagePart, { type: "tool" }> {
  return { partId, type: "tool", toolName, status: "completed", input };
}

function filePart(partId: string, path: string): Extract<MessagePart, { type: "file" }> {
  return { partId, type: "file", path, name: path };
}

function conversationMessages(turns: number): AgentMessage[] {
  return Array.from({ length: turns }, (_, index): AgentMessage[] => {
    const turn = index + 1;
    return [
      userMessage(`msg_user_${turn}`, `第 ${turn} 轮对话`),
      assistantMessage(`msg_assistant_${turn}`, [textPart(`part_answer_${turn}`, `第 ${turn} 轮回答摘要。`)])
    ];
  }).flat();
}
