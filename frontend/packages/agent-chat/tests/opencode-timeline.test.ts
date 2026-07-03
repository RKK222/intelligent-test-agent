import { describe, expect, it, vi } from "vitest";
import { fireEvent, render } from "@testing-library/vue";
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
    expect(getByText("查看文件")).toBeTruthy();
    expect(getByText("分析 checkout 失败")).toBeTruthy();
    expect(getByText("探索")).toBeTruthy();
    expect(getByText("读取 2 次")).toBeTruthy();
    expect(container.querySelector(".oc-context-group__trigger .oc-tool__status")?.textContent).toBe("已读取");
    await fireEvent.click(container.querySelector(".oc-context-group__trigger") as HTMLElement);
    expect(getByText("README.md")).toBeTruthy();
    expect(container.querySelector(".oc-text-part")).toBeTruthy();
    expect(container.querySelector(".oc-text-part .oc-icon-button")).toBeTruthy();
    expect(getByText("定位到 checkout 表单校验失败。")).toBeTruthy();
    expect(getByText("src/checkout.ts")).toBeTruthy();
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
    expect(getByText("bash")).toBeTruthy();
    expect(getByText("skill")).toBeTruthy();
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

function textPart(partId: string, text: string): Extract<MessagePart, { type: "text" }> {
  return { partId, type: "text", text, status: "completed" };
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
