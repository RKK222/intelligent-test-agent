import { mount, type VueWrapper } from "@vue/test-utils";
import { nextTick } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import FigmaChatPanel from "../src/components/FigmaChatPanel.vue";

// MarkdownView 包含防抖和动态 import；滚动回归只同步直出正文，其余时间线保持真实挂载。
const markdownViewStub = {
  props: ["source"],
  template: '<div class="ta-md-view">{{ source }}</div>'
};

const rootMessages = (rootAnswer = "主 Agent 初始正文", childOneAnswer = "子 Agent 一初始正文", childTwoAnswer = "子 Agent 二初始正文") => [
  {
    id: "root-user",
    messageId: "root-user",
    role: "user",
    text: "请并行分析前后端",
    createdAt: "2026-07-22T00:00:00Z"
  },
  {
    id: "root-answer",
    messageId: "root-answer",
    role: "assistant",
    text: rootAnswer,
    parts: [
      { partId: "root-text", type: "text", text: rootAnswer, status: "running" },
      {
        partId: "task-one",
        type: "tool",
        toolName: "task",
        callId: "call-one",
        status: "running",
        input: { description: "分析前端", subagent_type: "explore" }
      },
      {
        partId: "task-two",
        type: "tool",
        toolName: "task",
        callId: "call-two",
        status: "running",
        input: { description: "分析后端", subagent_type: "explore" }
      }
    ],
    createdAt: "2026-07-22T00:00:01Z"
  },
  {
    id: "child-one-user",
    messageId: "child-one-user",
    role: "user",
    text: "分析前端",
    createdAt: "2026-07-22T00:00:02Z"
  },
  {
    id: "child-one-answer",
    messageId: "child-one-answer",
    role: "assistant",
    text: childOneAnswer,
    parts: [{ partId: "child-one-text", type: "text", text: childOneAnswer, status: "running" }],
    createdAt: "2026-07-22T00:00:03Z"
  },
  {
    id: "child-two-user",
    messageId: "child-two-user",
    role: "user",
    text: "分析后端",
    createdAt: "2026-07-22T00:00:04Z"
  },
  {
    id: "child-two-answer",
    messageId: "child-two-answer",
    role: "assistant",
    text: childTwoAnswer,
    parts: [{ partId: "child-two-text", type: "text", text: childTwoAnswer, status: "running" }],
    createdAt: "2026-07-22T00:00:05Z"
  }
] as any[];

const messageScopesById = {
  "root-user": { sessionId: "root-a", rootSessionId: "root-a", isChildSession: false },
  "root-answer": { sessionId: "root-a", rootSessionId: "root-a", isChildSession: false },
  "child-one-user": {
    sessionId: "child-one",
    rootSessionId: "root-a",
    parentSessionId: "root-a",
    isChildSession: true,
    taskPartId: "task-one",
    taskCallId: "call-one"
  },
  "child-one-answer": {
    sessionId: "child-one",
    rootSessionId: "root-a",
    parentSessionId: "root-a",
    isChildSession: true,
    taskPartId: "task-one",
    taskCallId: "call-one"
  },
  "child-two-user": {
    sessionId: "child-two",
    rootSessionId: "root-a",
    parentSessionId: "root-a",
    isChildSession: true,
    taskPartId: "task-two",
    taskCallId: "call-two"
  },
  "child-two-answer": {
    sessionId: "child-two",
    rootSessionId: "root-a",
    parentSessionId: "root-a",
    isChildSession: true,
    taskPartId: "task-two",
    taskCallId: "call-two"
  }
};

const subagentsBySessionId = {
  "child-one": {
    sessionId: "child-one",
    parentSessionId: "root-a",
    taskMessageId: "root-answer",
    taskPartId: "task-one",
    taskCallId: "call-one",
    agentName: "Explore",
    title: "分析前端",
    status: "running",
    updatedAt: "2026-07-22T00:00:03Z"
  },
  "child-two": {
    sessionId: "child-two",
    parentSessionId: "root-a",
    taskMessageId: "root-answer",
    taskPartId: "task-two",
    taskCallId: "call-two",
    agentName: "Explore",
    title: "分析后端",
    status: "running",
    updatedAt: "2026-07-22T00:00:05Z"
  }
};

function mountPanel() {
  return mount(FigmaChatPanel, {
    props: {
      currentSessionId: "root-a",
      messages: rootMessages(),
      messageScopesById,
      subagentsBySessionId,
      subagentByTaskPartId: { "task-one": "child-one", "task-two": "child-two" },
      running: true,
      processStatus: { status: "READY", initializable: false, message: "ready" }
    } as any,
    global: { stubs: { MarkdownView: markdownViewStub } }
  });
}

function installScrollMetrics(wrapper: VueWrapper, initialHeight = 1000) {
  const scroll = wrapper.get(".figma-chat-scroll");
  const element = scroll.element as HTMLElement;
  let scrollHeight = initialHeight;
  Object.defineProperty(element, "scrollHeight", { configurable: true, get: () => scrollHeight });
  Object.defineProperty(element, "clientHeight", { configurable: true, value: 300 });
  return {
    scroll,
    element,
    setScrollHeight(value: number) {
      scrollHeight = value;
    }
  };
}

function installClampedScrollMetrics(wrapper: VueWrapper, initialHeight = 1000) {
  const viewport = installScrollMetrics(wrapper, initialHeight);
  let scrollHeight = initialHeight;
  let scrollTop = 0;
  Object.defineProperty(viewport.element, "scrollHeight", { configurable: true, get: () => scrollHeight });
  Object.defineProperty(viewport.element, "scrollTop", {
    configurable: true,
    get: () => scrollTop,
    set: (value: number) => {
      scrollTop = Math.max(0, Math.min(value, scrollHeight - viewport.element.clientHeight));
    }
  });
  return {
    ...viewport,
    setScrollHeight(value: number) {
      scrollHeight = value;
      scrollTop = Math.min(scrollTop, Math.max(0, scrollHeight - viewport.element.clientHeight));
    }
  };
}

async function flushDelayedScroll() {
  await nextTick();
  await nextTick();
  vi.advanceTimersByTime(120);
  await nextTick();
}

async function userScroll(viewport: ReturnType<typeof installScrollMetrics>, scrollTop: number) {
  viewport.element.scrollTop = scrollTop;
  await viewport.scroll.trigger("scroll");
}

function subagentCard(wrapper: VueWrapper, title: string) {
  const card = wrapper.findAll(".oc-subagent-card").find((item) => item.text().includes(title));
  if (!card) throw new Error(`未找到子 Agent 卡片：${title}`);
  return card;
}

describe("FigmaChatPanel scoped scroll memory", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("finishes the mounted root restore before accepting an early reading position", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);

    // 首次 mounted 已完成 DOM 更新，恢复计时器应立即登记，不能拖到本轮 nextTick 之后才开始计时。
    vi.advanceTimersByTime(60);
    await nextTick();
    viewport.element.scrollTop = 320;
    await viewport.scroll.trigger("scroll");

    await wrapper.setProps({ messages: rootMessages("主 Agent 新增流式正文") });
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(320);
    expect(wrapper.text()).toContain("查看新内容");
    wrapper.unmount();
  });

  it("restores independent root and child reading positions while first entering child at the bottom", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();

    await userScroll(viewport, 260);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    expect(viewport.element.scrollTop).toBe(1000);

    await userScroll(viewport, 140);
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();
    expect(viewport.element.scrollTop).toBe(260);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    expect(viewport.element.scrollTop).toBe(140);
    wrapper.unmount();
  });

  it("returns an inactive root that was left at the bottom to its latest bottom", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 700);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 180);
    viewport.setScrollHeight(1400);
    await wrapper.setProps({ messages: rootMessages("主 Agent 在后台追加了正文") });
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(1400);
    expect(wrapper.text()).not.toContain("查看新内容");
    wrapper.unmount();
  });

  it("restores an inactive root that was scrolled up and marks changed body content", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 260);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 120);
    await wrapper.setProps({ messages: rootMessages("主 Agent 在后台追加了正文") });
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(260);
    expect(wrapper.text()).toContain("查看新内容");
    wrapper.unmount();
  });

  it("detects an inactive same-length body replacement instead of comparing only text length", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 260);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await wrapper.setProps({ messages: rootMessages("主 Agent 更新正文") });
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    expect("主 Agent 更新正文").toHaveLength("主 Agent 初始正文".length);
    expect(viewport.element.scrollTop).toBe(260);
    expect(wrapper.text()).toContain("查看新内容");
    wrapper.unmount();
  });

  it("includes the streaming overlay visible length in an inactive scope fingerprint", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 260);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await wrapper.setProps({ streamingTextByPartId: { "root-text": "追加流式正文" } });
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(260);
    expect(wrapper.text()).toContain("查看新内容");
    wrapper.unmount();
  });

  it("ignores body output from another child while reading the active child", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 210);

    await wrapper.setProps({ messages: rootMessages(undefined, undefined, "子 Agent 二追加了后台正文") });
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(210);
    expect(wrapper.text()).not.toContain("查看新内容");
    wrapper.unmount();
  });

  it("prevents a stale child restore from overwriting the root during rapid switching", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await nextTick();
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(280);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    expect(viewport.element.scrollTop).toBe(1000);
    wrapper.unmount();
  });

  it("does not overwrite an existing child snapshot when a later visit is switched away before restore", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 140);
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    await subagentCard(wrapper, "分析前端").trigger("click");
    await nextTick();
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(140);
    wrapper.unmount();
  });

  it("keeps a pending child restore when the shorter child DOM emits a clamp scroll event", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installClampedScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);

    await subagentCard(wrapper, "分析前端").trigger("click");
    await nextTick();
    viewport.setScrollHeight(300);
    await viewport.scroll.trigger("scroll");
    viewport.setScrollHeight(1000);
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(700);
    wrapper.unmount();
  });

  it("does not carry root wheel intent into a pending child snapshot restore", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installClampedScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 140);
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    await viewport.scroll.trigger("wheel");
    viewport.element.scrollTop = 300;
    await viewport.scroll.trigger("scroll");
    await subagentCard(wrapper, "分析前端").trigger("click");
    await nextTick();
    viewport.setScrollHeight(300);
    await viewport.scroll.trigger("scroll");
    viewport.setScrollHeight(1000);
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(140);
    wrapper.unmount();
  });

  it("does not let a root terminal transition replace a pending child snapshot restore", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 140);
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    await subagentCard(wrapper, "分析前端").trigger("click");
    await nextTick();
    await wrapper.setProps({ running: false });
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(140);
    wrapper.unmount();
  });

  it("does not let root history loading completion replace a pending child snapshot restore", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 140);
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    await subagentCard(wrapper, "分析前端").trigger("click");
    await nextTick();
    await wrapper.setProps({ historyLoading: true });
    await wrapper.setProps({ historyLoading: false });
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(140);
    wrapper.unmount();
  });

  it("does not let a root terminal transition replace a pending root snapshot restore", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();

    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await nextTick();
    await wrapper.setProps({ running: false });
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(280);
    wrapper.unmount();
  });

  it("does not let root history loading completion replace a pending root snapshot restore", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();

    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await nextTick();
    await wrapper.setProps({ historyLoading: true });
    await wrapper.setProps({ historyLoading: false });
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(280);
    wrapper.unmount();
  });

  it("lets an intentional wheel scroll to the bottom cancel a pending child restore", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 140);
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    await subagentCard(wrapper, "分析前端").trigger("click");
    await nextTick();
    await viewport.scroll.trigger("wheel");
    viewport.element.scrollTop = 700;
    await viewport.scroll.trigger("scroll");
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(700);
    expect(wrapper.text()).not.toContain("查看新内容");
    wrapper.unmount();
  });

  it("saves an intentional non-bottom position that takes over a pending child restore", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 140);
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    await subagentCard(wrapper, "分析前端").trigger("click");
    await nextTick();
    await viewport.scroll.trigger("wheel");
    viewport.element.scrollTop = 360;
    await viewport.scroll.trigger("scroll");
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(360);
    wrapper.unmount();
  });

  it("clears the new-content state when a shorter changed child clamps an old snapshot to the bottom", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installClampedScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 280);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 140);
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await flushDelayedScroll();

    viewport.setScrollHeight(400);
    await wrapper.setProps({ messages: rootMessages(undefined, "子 Agent 一缩短后的新正文") });
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(100);
    expect(wrapper.text()).not.toContain("查看新内容");
    wrapper.unmount();
  });

  it("clears snapshots and exits child when the real root session changes", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await flushDelayedScroll();
    await userScroll(viewport, 260);
    await subagentCard(wrapper, "分析前端").trigger("click");
    await flushDelayedScroll();
    await userScroll(viewport, 130);

    const rootBMessages = [
      { id: "root-b-user", role: "user", text: "新的真实会话", createdAt: "2026-07-22T01:00:00Z" },
      { id: "root-b-answer", role: "assistant", text: "新会话正文", createdAt: "2026-07-22T01:00:01Z" }
    ] as any[];
    await wrapper.setProps({
      currentSessionId: "root-b",
      messages: rootBMessages,
      messageScopesById: {
        "root-b-user": { sessionId: "root-b", rootSessionId: "root-b", isChildSession: false },
        "root-b-answer": { sessionId: "root-b", rootSessionId: "root-b", isChildSession: false }
      }
    });
    await flushDelayedScroll();
    expect(wrapper.find(".figma-chat-subagent-return").exists()).toBe(false);
    expect(viewport.element.scrollTop).toBe(1000);

    await userScroll(viewport, 310);
    await wrapper.setProps({ currentSessionId: "root-a", messages: rootMessages(), messageScopesById });
    await flushDelayedScroll();
    expect(viewport.element.scrollTop).toBe(1000);
    wrapper.unmount();
  });

  it("keeps the draft root snapshot when it receives its first real session id", async () => {
    vi.useFakeTimers();
    const wrapper = mountPanel();
    const viewport = installScrollMetrics(wrapper);
    await wrapper.setProps({ currentSessionId: "" });
    await flushDelayedScroll();
    await userScroll(viewport, 245);

    await wrapper.setProps({ currentSessionId: "root-created" });
    await flushDelayedScroll();

    expect(viewport.element.scrollTop).toBe(245);
    wrapper.unmount();
  });
});
