import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, within } from "@testing-library/vue";
import { nextTick } from "vue";
import type { AgentMessage, MessagePart, PromptPart } from "@test-agent/shared-types";
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
    expect(getByText("checkout.ts")).toBeTruthy();
  });

  it("renders only the original question for serialized workspace context prompts", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage(
          "msg_user_context",
          [
            "用户问题：",
            "写了什么内容",
            "",
            "以下是用户添加的工作区上下文：",
            "",
            '<context type="file" path="docs/api.md">',
            "接口说明",
            "</context>"
          ].join("\n")
        )
      ]
    });

    const { container, getByText, queryByText } = render(OpencodeTimeline, { props: { state } });

    expect(getByText("写了什么内容")).toBeTruthy();
    expect(getByText("文件")).toBeTruthy();
    expect(getByText("api.md")).toBeTruthy();
    expect(queryByText(/<context/)).toBeNull();
    expect(container.querySelector(".oc-user-message__bubble")?.textContent).not.toContain("以下是用户添加的工作区上下文");
  });

  it("renders selection and file chips from user prompt parts without exposing serialized context", () => {
    const serializedPrompt = [
      "用户问题：",
      "能看到什么内容",
      "",
      "以下是用户添加的工作区上下文：",
      "",
      '<context type="selection" path="99-测试数据/Git冲突处理/冲突文件.md" lines="5-5">',
      "应用分支上的修改，用于制造合并冲突。",
      "</context>"
    ].join("\n");
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_context_part", "能看到什么内容", [
          { type: "text", text: serializedPrompt },
          {
            type: "file",
            path: "docs/api.md",
            name: "api.md",
            content: "# API",
            source: { contextType: "file" }
          }
        ])
      ]
    });

    const { container, getByText, queryByText } = render(OpencodeTimeline, { props: { state } });

    expect(getByText("能看到什么内容")).toBeTruthy();
    expect(getByText("选区")).toBeTruthy();
    expect(getByText("冲突文件.md")).toBeTruthy();
    expect(getByText("L5-5")).toBeTruthy();
    expect(getByText("文件")).toBeTruthy();
    expect(getByText("api.md")).toBeTruthy();
    expect(queryByText(/<context/)).toBeNull();
    expect(container.querySelector(".oc-user-message__bubble")?.textContent).not.toContain("以下是用户添加的工作区上下文");
  });

  it("opens reasoning details as plain text without mounting MarkdownView", async () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_reasoning", "分析流程"),
        assistantMessage("msg_assistant_reasoning", [
          reasoningPart(
            "part_reasoning",
            ["第一步：读取上下文", "", "- 保留原始换行", "- 不触发 Markdown 渲染器"].join("\n")
          )
        ])
      ]
    });

    const { container, getByText, queryByTestId } = render(OpencodeTimeline, { props: { state } });

    expect(getByText("思考状态")).toBeTruthy();
    expect(container.querySelector(".oc-reasoning-part__plain")).toBeNull();

    await fireEvent.click(container.querySelector(".oc-reasoning-part .oc-disclosure__trigger") as HTMLElement);

    const plain = container.querySelector(".oc-reasoning-part__plain");
    expect(plain?.textContent).toContain("第一步：读取上下文");
    expect(plain?.textContent).toContain("- 不触发 Markdown 渲染器");
    expect(queryByTestId("md-view")).toBeNull();
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
      expect(getByText("a.ts")).toBeTruthy();

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

  it("keeps step-start metadata on the same assistant line as reasoning", async () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "这个文件里有什么内容"),
        assistantMessage("msg_assistant_reasoning", [
          { partId: "part_step_start", type: "step-start", snapshot: "start" },
          reasoningPart("part_reasoning", "先读取文件内容。")
        ])
      ]
    });

    const { container, getByText } = render(OpencodeTimeline, { props: { state } });
    await waitMarkdown();

    const frame = container.querySelector(".oc-assistant-frame.has-header");
    expect(frame).toBeTruthy();
    expect(frame?.querySelector(".oc-assistant-frame__avatar")).toBeTruthy();
    expect(frame?.textContent).toContain("思考状态");
    expect(container.querySelectorAll(".oc-assistant-frame")).toHaveLength(1);
    expect(container.querySelector(".oc-unknown-part")).toBeNull();
    expect(getByText("思考状态")).toBeTruthy();
  });

  it("keeps native internal Parts in state without rendering fallback JSON", () => {
    const hiddenMarker = "E2E_NATIVE_INTERNAL_MARKER";
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_internal", "校验原生内部 Part"),
        assistantMessage("msg_assistant_internal", [
          { partId: "part_subtask", type: "subtask", prompt: hiddenMarker, description: hiddenMarker, agent: "build" },
          { partId: "part_step_finish", type: "step-finish", reason: hiddenMarker },
          { partId: "part_snapshot", type: "snapshot", snapshot: hiddenMarker },
          { partId: "part_patch", type: "patch", hash: hiddenMarker, files: [`${hiddenMarker}.txt`] },
          { partId: "part_agent", type: "agent", name: hiddenMarker },
          { partId: "part_retry", type: "retry", attempt: 1, error: { message: "retry fallback is intentionally visible" } },
          { partId: "part_file", type: "file", path: `${hiddenMarker}.txt`, name: `${hiddenMarker}.txt` }
        ])
      ]
    });

    const { container, queryByText } = render(OpencodeTimeline, { props: { state } });

    // 数据仍在状态树中，用于恢复/审计；只是不作为原生 assistant timeline 卡片呈现。
    expect(state.partsByMessageId.msg_assistant_internal).toHaveLength(7);
    expect(queryByText(hiddenMarker, { exact: false })).toBeNull();
    expect(container.querySelector(".oc-file-part")).toBeNull();
    expect(container.querySelector(".oc-unknown-part")).toBeNull();
  });

  it("keeps the existing retry compatibility row visible without falling back to JSON", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_retry_part", "重试失败信息仍可见"),
        assistantMessage("msg_assistant_retry_part", [
          { partId: "part_retry_visible", type: "retry", attempt: 3, error: { message: "请求超时" } }
        ])
      ]
    });

    const { container, getByText } = render(OpencodeTimeline, { props: { state } });

    expect(getByText("重试第 3 次")).toBeTruthy();
    expect(getByText("请求超时")).toBeTruthy();
    expect(container.querySelector(".oc-unknown-part")).toBeNull();
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
    const plain = container.querySelector(".oc-reasoning-part__plain");
    expect(plain?.textContent).toContain("先识别用户问题。");
    expect(plain?.textContent).toContain("再读取相关能力说明。");
    expect(plain?.textContent).toContain("最后组织回答。");
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

  it("renders each question call separately with structured selected answers", async () => {
    const answeredQuestion = {
      ...toolPart("part_question_1", "question", {
        questions: [
          {
            header: "部署环境",
            question: "请选择部署环境",
            options: [
              { label: "测试环境", description: "仅部署到测试集群" },
              { label: "生产环境", description: "部署到正式生产集群" }
            ]
          },
          {
            header: "验证范围",
            question: "请选择验证范围",
            multiple: true,
            options: [
              { label: "接口测试", description: "执行接口回归" },
              { label: "页面测试", description: "执行页面回归" }
            ]
          },
          {
            header: "补充说明",
            question: "请输入其他环境",
            options: [{ label: "无", description: "不增加其他环境" }]
          }
        ]
      }),
      metadata: {
        answers: [["测试环境"], ["接口测试", "页面测试"], ["预发布环境"]]
      }
    } satisfies Extract<MessagePart, { type: "tool" }>;
    const pendingQuestion = {
      ...toolPart("part_question_2", "question", {
        questions: [{ header: "发布时间", question: "请选择发布时间", options: [] }]
      }),
      status: "running"
    } satisfies Extract<MessagePart, { type: "tool" }>;
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "确认发布参数"),
        assistantMessage("msg_question_1", [answeredQuestion]),
        assistantMessage("msg_read", [toolPart("part_read", "read", { filePath: "README.md" })]),
        assistantMessage("msg_question_2", [pendingQuestion])
      ]
    });

    const { container, getByText, queryByText } = render(OpencodeTimeline, { props: { state } });

    const questionTools = container.querySelectorAll('[data-testid="oc-question-tool"]');
    expect(questionTools).toHaveLength(2);
    expect(questionTools[0]?.querySelector(".oc-tool__status")?.textContent).toBe("已回答");
    expect(questionTools[1]?.querySelector(".oc-tool__status")?.textContent).toBe("进行中");
    expect(container.querySelector(".oc-context-group__trigger .oc-tool__status")?.textContent).toBe("已读取");
    expect(queryByText("预发布环境")).toBeNull();

    await fireEvent.click(questionTools[0]?.querySelector(".oc-tool__trigger") as HTMLElement);

    expect(getByText("部署环境")).toBeTruthy();
    expect(getByText("请选择部署环境")).toBeTruthy();
    expect(getByText("测试环境")).toBeTruthy();
    expect(getByText("仅部署到测试集群")).toBeTruthy();
    expect(getByText("接口测试")).toBeTruthy();
    expect(getByText("执行接口回归")).toBeTruthy();
    expect(getByText("页面测试")).toBeTruthy();
    expect(getByText("执行页面回归")).toBeTruthy();
    expect(getByText("预发布环境")).toBeTruthy();
    expect(queryByText("不增加其他环境")).toBeNull();

    await fireEvent.click(questionTools[1]?.querySelector(".oc-tool__trigger") as HTMLElement);
    expect(within(questionTools[1] as HTMLElement).getAllByText("请选择发布时间")).toHaveLength(2);
    expect(within(questionTools[1] as HTMLElement).getByText("等待回答")).toBeTruthy();
  });

  it("does not invent answer content when a completed question lacks answer metadata", async () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "恢复历史提问"),
        assistantMessage("msg_question_1", [
          toolPart("part_question_1", "question", {
            questions: [{ header: "历史问题", question: "请选择历史答案", options: [{ label: "选项 A", description: "历史选项" }] }]
          })
        ])
      ]
    });

    const { container, getByText, queryByText } = render(OpencodeTimeline, { props: { state } });
    const questionTool = container.querySelector('[data-testid="oc-question-tool"]') as HTMLElement;

    expect(questionTool.querySelector(".oc-tool__status")?.textContent).toBe("已回答");
    await fireEvent.click(questionTool.querySelector(".oc-tool__trigger") as HTMLElement);
    expect(getByText("暂无回答详情")).toBeTruthy();
    expect(queryByText("历史选项")).toBeNull();
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

function userMessage(id: string, text: string, parts?: PromptPart[]): Extract<AgentMessage, { role: "user" }> {
  return { id, messageId: id, role: "user", text, parts, createdAt: "2026-07-03T00:00:00Z" };
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
