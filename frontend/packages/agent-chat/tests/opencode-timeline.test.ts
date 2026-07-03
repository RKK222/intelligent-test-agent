import { describe, expect, it } from "vitest";
import { fireEvent, render } from "@testing-library/vue";
import { nextTick } from "vue";
import type { AgentMessage, MessagePart } from "@test-agent/shared-types";
import OpencodeTimeline from "../src/opencode-like/components/OpencodeTimeline.vue";
import { createOpencodeLikeState } from "../src/opencode-like/state/adapter";
import AssistantThread from "../src/AssistantThread.vue";

const waitMarkdown = () => new Promise((resolve) => setTimeout(resolve, 400));

describe("OpencodeTimeline", () => {
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
    expect(container.querySelector(".oc-context-group")).toBeTruthy();
    expect(container.querySelector(".oc-assistant-part")).toBeTruthy();
    expect(container.querySelector(".oc-diff-summary")).toBeTruthy();
    expect(getByText("分析 checkout 失败")).toBeTruthy();
    expect(getByText("已探索")).toBeTruthy();
    expect(getByText("读取 2 次")).toBeTruthy();
    await fireEvent.click(container.querySelector(".oc-context-group__trigger") as HTMLElement);
    expect(getByText("README.md")).toBeTruthy();
    expect(getByText("定位到 checkout 表单校验失败。")).toBeTruthy();
    expect(getByText("src/checkout.ts")).toBeTruthy();
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

function toolPart(
  partId: string,
  toolName: string,
  input: Record<string, unknown>
): Extract<MessagePart, { type: "tool" }> {
  return { partId, type: "tool", toolName, status: "completed", input };
}
