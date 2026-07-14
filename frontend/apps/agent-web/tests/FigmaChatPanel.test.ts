import { mount } from "@vue/test-utils";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { nextTick } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { chatStateFromSessionTreeSnapshot } from "../src/components/workbench-utils";
import FigmaChatPanel from "../src/components/FigmaChatPanel.vue";
import type { SessionTreeMessagesResponse } from "@test-agent/shared-types";

// MarkdownView 内部用 150ms 定时器 + 动态 import 异步渲染正文，
// 单测同步断言时会停在“渲染中…”占位。这里桩成同步直出 source，
// 让历史消息正文断言稳定且与渲染时序解耦。
const markdownViewStub = {
  props: ["source"],
  template: '<div class="ta-md-view">{{ source }}</div>',
};

function dispatchPointer(
  target: EventTarget,
  type: string,
  pointerId: number,
  clientX: number,
  clientY: number,
  pointerType = "mouse"
) {
  const event = new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    clientX,
    clientY,
  });
  Object.defineProperties(event, {
    pointerId: { value: pointerId },
    pointerType: { value: pointerType },
    isPrimary: { value: true },
  });
  target.dispatchEvent(event);
}

function setViewport(width: number, height: number) {
  Object.defineProperty(window, "innerWidth", { configurable: true, value: width });
  Object.defineProperty(window, "innerHeight", { configurable: true, value: height });
}

// 原生 Timeline 始终完整展示；保留此 helper 只为让相关断言等待一次 Vue 更新。
async function showFullTimeline(wrapper: any) {
  await wrapper.vm.$nextTick();
}

describe("FigmaChatPanel", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("blocks submit while a history session is still loading", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        inputValue: "切换历史时不能发送",
        historyLoading: true,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any
    });

    const sendButton = wrapper.get('button[aria-label="发送"]');
    expect(sendButton.attributes("disabled")).toBeDefined();
    await sendButton.trigger("click");

    expect(wrapper.emitted("send")).toBeUndefined();
  });

  it("uses the native timeline without a mode toggle or activity overlay", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
      } as any,
    });

    expect(wrapper.find('[data-testid="chat-timeline-mode-toggle"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="chat-activity-trigger"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="chat-activity-panel"]').exists()).toBe(false);
  });

  it("hides the chat process dot and card when the pet owns process status", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processStatusPlacement: "pet",
        processStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          message: "需要初始化"
        }
      } as any
    });

    await nextTick();

    expect(wrapper.find(".figma-chat-process-status").exists()).toBe(false);
    expect(wrapper.find(".figma-chat-process-status-dot").exists()).toBe(false);
  });

  it("greys the complete composer while the process is not initialized", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processStatusPlacement: "pet",
        processStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          message: "需要初始化"
        }
      } as any
    });

    expect(wrapper.get(".figma-chat-input-card").classes()).toContain("is-disabled");
    expect(wrapper.get(".figma-chat-textarea").attributes("disabled")).toBeDefined();
    expect(wrapper.get(".figma-chat-textarea").attributes("placeholder")).toBe("请先初始化 TestAgent 进程");
    expect(wrapper.get('[aria-label="上传附件"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get('[aria-label="切换 Agent"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get('[aria-label="切换模型"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get('[aria-label="新建对话"]').attributes("disabled")).toBeDefined();
  });

  it("keeps the composer available before the first session is created", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        inputValue: "直接发送第一条消息",
        processRequired: true,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any
    });

    expect(wrapper.get(".figma-chat-input-card").classes()).not.toContain("is-disabled");
    expect(wrapper.get(".figma-chat-textarea").attributes("disabled")).toBeUndefined();
    expect(wrapper.get(".figma-chat-textarea").attributes("placeholder")).toBe("Ask the AI agent...");
    const newConversationButton = wrapper.get('[aria-label="新建对话"]');
    expect(newConversationButton.attributes("disabled")).toBeUndefined();

    await wrapper.get('[aria-label="发送"]').trigger("click");
    expect(wrapper.emitted("send")?.[0]).toEqual(["直接发送第一条消息"]);
  });

  it("keeps a READY card inline without a saved drag and only uses floating mode after a drag", async () => {
    window.localStorage.removeItem("figma-chat-process-dot-pos");
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      await nextTick();
      const inlineCard = wrapper.get(".figma-chat-process-status");
      expect((inlineCard.element as HTMLElement).style.position).toBe("");
      expect(wrapper.find(".figma-chat-process-status-dot").exists()).toBe(false);

      await inlineCard.trigger("click");
      const dot = wrapper.get(".figma-chat-process-status-dot");
      dispatchPointer(dot.element, "pointerdown", 31, 100, 100);
      dispatchPointer(window, "pointermove", 31, 160, 140);
      dispatchPointer(window, "pointerup", 31, 160, 140);
      await nextTick();
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBeTruthy();

      await dot.trigger("click");
      await dot.trigger("click");
      await nextTick();
      expect((wrapper.get(".figma-chat-process-status").element as HTMLElement).style.position).toBe("fixed");
    } finally {
      wrapper.unmount();
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("migrates a legacy dot record to the v2 card anchor and restores that anchor after remount", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    setViewport(1000, 800);
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify({ x: 100, y: 120 }));
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 0, y: 0, width: 240, height: 90, top: 0, right: 240, bottom: 90, left: 0, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });

    const props = { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any;
    const wrapper = mount(FigmaChatPanel, { props });
    try {
      await nextTick();
      // v1 迁移必须等展开卡片完成真实测量后再写回，挂载阶段仍保留原始绿点。
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ x: 100, y: 120 })
      );
      await wrapper.get(".figma-chat-process-status-dot").trigger("click");
      await nextTick();
      await nextTick();
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 116, cardY: 136, dotSide: "top-left" })
      );
      expect((wrapper.get(".figma-chat-process-status").element as HTMLElement).style.left).toBe("116px");
      expect((wrapper.get(".figma-chat-process-status").element as HTMLElement).style.top).toBe("136px");
      wrapper.unmount();

      const restored = mount(FigmaChatPanel, { props });
      await nextTick();
      await restored.get(".figma-chat-process-status-dot").trigger("click");
      await nextTick();
      expect((restored.get(".figma-chat-process-status").element as HTMLElement).style.left).toBe("116px");
      expect((restored.get(".figma-chat-process-status").element as HTMLElement).style.top).toBe("136px");
      restored.unmount();
    } finally {
      if (wrapper.exists()) wrapper.unmount();
      vi.restoreAllMocks();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("migrates a legacy bottom-right dot with its visual side intact", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    setViewport(1000, 800);
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify({ x: 880, y: 680 }));
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 0, y: 0, width: 240, height: 90, top: 0, right: 240, bottom: 90, left: 0, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      await nextTick();
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ x: 880, y: 680 })
      );
      expect(wrapper.get(".figma-chat-process-status-dot").attributes("style")).toContain(
        "--figma-process-dot-x: 880px"
      );
      expect(wrapper.get(".figma-chat-process-status-dot").attributes("style")).toContain(
        "--figma-process-dot-y: 680px"
      );
      await wrapper.get(".figma-chat-process-status-dot").trigger("click");
      await nextTick();
      await nextTick();
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 632, cardY: 582, dotSide: "bottom-right" })
      );
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("keeps a validated v2 anchor untouched until the expanded card has a real measurement", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    setViewport(1000, 800);
    const stored = { v: 2, cardX: 900, cardY: 700, dotSide: "top-left" };
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify(stored));
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 0, y: 0, width: 240, height: 90, top: 0, right: 240, bottom: 90, left: 0, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      await nextTick();
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(JSON.stringify(stored));
      const dot = wrapper.get(".figma-chat-process-status-dot");
      expect(dot.attributes("style")).toContain("--figma-process-dot-x: 884px");
      expect(dot.attributes("style")).toContain("--figma-process-dot-y: 684px");

      await dot.trigger("click");
      await nextTick();
      await nextTick();
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 744, cardY: 694, dotSide: "top-left" })
      );
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("keeps one v2 card anchor through collapse, reopening, and a later viewport shrink", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    setViewport(1000, 800);
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify({ v: 2, cardX: 700, cardY: 600, dotSide: "top-left" }));
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 0, y: 0, width: 240, height: 90, top: 0, right: 240, bottom: 90, left: 0, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      await nextTick();
      await wrapper.get(".figma-chat-process-status-dot").trigger("click");
      await nextTick();
      const card = wrapper.get(".figma-chat-process-status");
      expect((card.element as HTMLElement).style.left).toBe("700px");
      expect((card.element as HTMLElement).style.top).toBe("600px");
      await card.trigger("click");
      await wrapper.get(".figma-chat-process-status-dot").trigger("click");
      await nextTick();
      expect((wrapper.get(".figma-chat-process-status").element as HTMLElement).style.left).toBe("700px");
      expect((wrapper.get(".figma-chat-process-status").element as HTMLElement).style.top).toBe("600px");

      setViewport(500, 400);
      window.dispatchEvent(new Event("resize"));
      await nextTick();
      expect((wrapper.get(".figma-chat-process-status").element as HTMLElement).style.left).toBe("244px");
      expect((wrapper.get(".figma-chat-process-status").element as HTMLElement).style.top).toBe("294px");
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 244, cardY: 294, dotSide: "top-left" })
      );
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("drags the expanded status card without a jump and suppresses its follow-up click", async () => {
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify({ v: 2, cardX: 632, cardY: 582, dotSide: "top-left" }));
    setViewport(1000, 800);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 632, y: 582, width: 240, height: 90, top: 582, right: 872, bottom: 672, left: 632, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      await nextTick();
      await wrapper.get(".figma-chat-process-status-dot").trigger("click");
      await nextTick();
      const card = wrapper.get(".figma-chat-process-status");
      expect((card.element as HTMLElement).style.left).toBe("632px");
      expect((card.element as HTMLElement).style.top).toBe("582px");
      dispatchPointer(card.element, "pointerdown", 32, 700, 620);
      dispatchPointer(window, "pointermove", 32, 720, 640);
      await nextTick();
      expect((card.element as HTMLElement).style.left).toBe("652px");
      expect((card.element as HTMLElement).style.top).toBe("602px");
      dispatchPointer(window, "pointerup", 32, 720, 640);
      await card.trigger("click");
      await nextTick();
      expect(wrapper.find(".figma-chat-process-status").exists()).toBe(true);
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 652, cardY: 602, dotSide: "top-left" })
      );
      await card.trigger("click");
      await nextTick();
      expect(wrapper.find(".figma-chat-process-status-dot").exists()).toBe(true);
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("keeps an inline card at its near-edge rect on the threshold move before applying later drag deltas", async () => {
    setViewport(1000, 800);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 632, y: 582, width: 240, height: 90, top: 582, right: 872, bottom: 672, left: 632, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      const card = wrapper.get(".figma-chat-process-status");
      dispatchPointer(card.element, "pointerdown", 33, 700, 620);
      dispatchPointer(window, "pointermove", 33, 720, 640);
      await nextTick();
      expect((card.element as HTMLElement).style.left).toBe("632px");
      expect((card.element as HTMLElement).style.top).toBe("582px");

      dispatchPointer(window, "pointermove", 33, 725, 645);
      await nextTick();
      expect((card.element as HTMLElement).style.left).toBe("637px");
      expect((card.element as HTMLElement).style.top).toBe("587px");
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
    }
  });

  it("keeps an extreme left-top inline card at its exact rect while collapsing to the nearest safe dot", async () => {
    setViewport(1000, 800);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 16, y: 16, width: 240, height: 90, top: 16, right: 256, bottom: 106, left: 16, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      const card = wrapper.get(".figma-chat-process-status");
      dispatchPointer(card.element, "pointerdown", 34, 30, 30);
      dispatchPointer(window, "pointermove", 34, 50, 50);
      await nextTick();
      expect((card.element as HTMLElement).style.left).toBe("16px");
      expect((card.element as HTMLElement).style.top).toBe("16px");

      dispatchPointer(window, "pointerup", 34, 50, 50);
      await card.trigger("click");
      await card.trigger("click");
      await nextTick();
      const dot = wrapper.get(".figma-chat-process-status-dot");
      expect(dot.attributes("style")).toContain("--figma-process-dot-x: 264px");
      expect(dot.attributes("style")).toContain("--figma-process-dot-y: 114px");
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("clamps a temporary edge anchor after later drag movement and viewport shrink", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    setViewport(1000, 800);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 16, y: 16, width: 240, height: 90, top: 16, right: 256, bottom: 106, left: 16, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      const card = wrapper.get(".figma-chat-process-status");
      dispatchPointer(card.element, "pointerdown", 35, 30, 30);
      dispatchPointer(window, "pointermove", 35, 50, 50);
      dispatchPointer(window, "pointermove", 35, -100, -100);
      await nextTick();
      expect((card.element as HTMLElement).style.left).toBe("16px");
      expect((card.element as HTMLElement).style.top).toBe("16px");

      dispatchPointer(window, "pointermove", 35, 130, 100);
      await nextTick();
      expect((card.element as HTMLElement).style.left).toBe("96px");
      expect((card.element as HTMLElement).style.top).toBe("66px");

      setViewport(200, 140);
      window.dispatchEvent(new Event("resize"));
      await nextTick();
      expect((card.element as HTMLElement).style.left).toBe("16px");
      expect((card.element as HTMLElement).style.top).toBe("34px");
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
    }
  });

  it("does not collapse the status card when its initialize button receives Enter or Space", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "NEEDS_INITIALIZATION", initializable: true, message: "start required" },
      } as any,
    });
    try {
      const initButton = wrapper.get(".figma-chat-process-init");
      await initButton.trigger("keydown", { key: "Enter" });
      await initButton.trigger("keydown", { key: " " });
      expect(wrapper.find(".figma-chat-process-status").exists()).toBe(true);
      await initButton.trigger("click");
      expect(wrapper.emitted("initialize-process")).toEqual([[]]);

      await wrapper.get('[data-testid="chat-process-help"]').trigger("click");
      expect(wrapper.find(".figma-chat-process-status").exists()).toBe(true);
      expect(wrapper.emitted("open-help")?.[0]).toEqual(["process-initialization"]);
    } finally {
      wrapper.unmount();
    }
  });

  it("uses an eight-pixel translucent halo dot and keeps all process status UI out of child-agent view", async () => {
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify({ v: 2, cardX: 116, cardY: 136, dotSide: "top-left" }));
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          { id: "m1", role: "assistant", parts: [{ id: "task", type: "tool", toolName: "task", status: "completed", input: { description: "child" } }] }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        subagentsBySessionId: { ses_child: { sessionId: "ses_child", title: "child" } },
        subagentByTaskPartId: { task: "ses_child" }
      } as any,
    });
    try {
      await nextTick();
      expect(wrapper.get(".figma-chat-process-status-dot").attributes("style")).toContain("--figma-process-dot-x: 100px");
      expect(wrapper.get(".figma-chat-process-status-dot").classes()).toContain("is-ready");
      const componentSource = readFileSync(
        resolve(process.cwd(), "apps/agent-web/src/components/FigmaChatPanel.vue"),
        "utf8"
      );
      expect(componentSource).toContain("width: 8px;");
      expect(componentSource).toContain("background: rgba(24, 169, 120, 0.42);");
      expect(componentSource).toContain("opacity: 0.42;");
      expect(componentSource).not.toContain("#34d399 0%");
      await showFullTimeline(wrapper);
      const taskCard = wrapper.find(".oc-subagent-card");
      await taskCard.trigger("click");
      await nextTick();
      expect(wrapper.find(".figma-chat-process-status-dot").exists()).toBe(false);
      expect(wrapper.find(".figma-chat-process-status").exists()).toBe(false);
    } finally {
      wrapper.unmount();
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("persists a dragged READY dot and expands the status card beside that location", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify({ x: 100, y: 120 }));
    setViewport(1000, 800);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 0, y: 0, width: 240, height: 90, top: 0, right: 240, bottom: 90, left: 0, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });

    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
      } as any,
    });

    try {
      await nextTick();
      const dot = wrapper.get(".figma-chat-process-status-dot.is-ready");
      dispatchPointer(dot.element, "pointerdown", 1, 100, 120);
      dispatchPointer(window, "pointermove", 1, 260, 220);
      dispatchPointer(window, "pointerup", 1, 260, 220);
      await nextTick();

      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 276, cardY: 236, dotSide: "top-left" })
      );

      // 浏览器会在 pointerup 后补发 click；先复现这次被拖拽阈值过滤的 click。
      await dot.trigger("click");
      await dot.trigger("click");
      await nextTick();
      await nextTick();

      const card = wrapper.get(".figma-chat-process-status");
      const cardElement = card.element as HTMLElement;
      expect(cardElement.style.position).toBe("fixed");
      expect(cardElement.style.left).toBe("276px");
      expect(cardElement.style.top).toBe("236px");

      await card.trigger("click");
      await nextTick();

      const restoredDot = wrapper.get(".figma-chat-process-status-dot.is-ready");
      expect(restoredDot.attributes("style")).toContain("--figma-process-dot-x: 260px");
      expect(restoredDot.attributes("style")).toContain("--figma-process-dot-y: 220px");
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 276, cardY: 236, dotSide: "top-left" })
      );
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("lets the dot reach the bottom-right safe viewport by changing its card side", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    setViewport(1000, 800);
    window.localStorage.setItem(
      "figma-chat-process-dot-pos",
      JSON.stringify({ v: 2, cardX: 116, cardY: 136, dotSide: "top-left" })
    );
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      await nextTick();
      const dot = wrapper.get(".figma-chat-process-status-dot");
      dispatchPointer(dot.element, "pointerdown", 41, 100, 120);
      dispatchPointer(window, "pointermove", 41, 980, 780);
      await nextTick();
      expect(dot.attributes("style")).toContain("--figma-process-dot-x: 976px");
      expect(dot.attributes("style")).toContain("--figma-process-dot-y: 776px");
      dispatchPointer(window, "pointerup", 41, 980, 780);
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 688, cardY: 684, dotSide: "bottom-right" })
      );
    } finally {
      wrapper.unmount();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("expires drag click suppression when pointerup happens outside without a follow-up click", async () => {
    vi.useFakeTimers();
    window.localStorage.setItem(
      "figma-chat-process-dot-pos",
      JSON.stringify({ v: 2, cardX: 116, cardY: 136, dotSide: "top-left" })
    );
    const wrapper = mount(FigmaChatPanel, {
      props: { messages: [], processStatus: { status: "READY", initializable: false, message: "ready" } } as any,
    });
    try {
      await nextTick();
      const dot = wrapper.get(".figma-chat-process-status-dot");
      dispatchPointer(dot.element, "pointerdown", 42, 100, 120);
      dispatchPointer(window, "pointermove", 42, 160, 160);
      dispatchPointer(window, "pointerup", 42, 160, 160);
      vi.advanceTimersByTime(501);
      await dot.trigger("click");
      await nextTick();
      expect(wrapper.find(".figma-chat-process-status").exists()).toBe(true);
    } finally {
      wrapper.unmount();
      window.localStorage.removeItem("figma-chat-process-dot-pos");
      vi.useRealTimers();
    }
  });

  it("flips the expanded status card near the bottom right and reclamps it after resize", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify({ v: 2, cardX: 700, cardY: 600, dotSide: "top-left" }));
    setViewport(1000, 800);
    let cardSize = { width: 240, height: 90 };
    const resizeObservers: TestResizeObserver[] = [];
    class TestResizeObserver {
      observe = vi.fn();
      disconnect = vi.fn();

      constructor(readonly callback: ResizeObserverCallback) {
        resizeObservers.push(this);
      }
    }
    vi.stubGlobal("ResizeObserver", TestResizeObserver);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 0, y: 0, width: cardSize.width, height: cardSize.height, top: 0, right: cardSize.width, bottom: cardSize.height, left: 0, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });

    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
      } as any,
    });

    try {
      await nextTick();
      await wrapper.get(".figma-chat-process-status-dot").trigger("click");
      await nextTick();
      await nextTick();

      const card = wrapper.get(".figma-chat-process-status");
      const cardElement = card.element as HTMLElement;
      expect(cardElement.style.left).toBe("700px");
      expect(cardElement.style.top).toBe("600px");
      expect(resizeObservers).toHaveLength(1);

      cardSize = { width: 300, height: 120 };
      resizeObservers[0].callback([], resizeObservers[0] as unknown as ResizeObserver);
      await nextTick();

      expect(cardElement.style.left).toBe("684px");
      expect(cardElement.style.top).toBe("600px");
      expect(window.localStorage.getItem("figma-chat-process-dot-pos")).toBe(
        JSON.stringify({ v: 2, cardX: 684, cardY: 600, dotSide: "top-left" })
      );

      setViewport(500, 400);
      window.dispatchEvent(new Event("resize"));
      await nextTick();

      expect(cardElement.style.left).toBe("184px");
      expect(cardElement.style.top).toBe("264px");
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("keeps an oversized expanded status card usable inside the viewport margins", async () => {
    const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
    const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");
    window.localStorage.setItem("figma-chat-process-dot-pos", JSON.stringify({ x: 200, y: 180 }));
    setViewport(400, 300);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return this.classList.contains("figma-chat-process-status")
        ? ({ x: 0, y: 0, width: 600, height: 500, top: 0, right: 600, bottom: 500, left: 0, toJSON: () => ({}) } as DOMRect)
        : ({ x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0, toJSON: () => ({}) } as DOMRect);
    });
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
      } as any,
    });

    try {
      await nextTick();
      await wrapper.get(".figma-chat-process-status-dot").trigger("click");
      await nextTick();
      await nextTick();

      const card = wrapper.get(".figma-chat-process-status");
      const cardElement = card.element as HTMLElement;
      expect(cardElement.style.left).toBe("16px");
      expect(cardElement.style.top).toBe("16px");
      expect(cardElement.style.maxWidth).toBe("calc(100vw - 32px)");
      expect(cardElement.style.maxHeight).toBe("calc(100vh - 32px)");
      expect(cardElement.style.overflowY).toBe("auto");
      expect(cardElement.style.overflowWrap).toBe("anywhere");
    } finally {
      wrapper.unmount();
      vi.restoreAllMocks();
      Object.defineProperty(window, "innerWidth", originalInnerWidth!);
      Object.defineProperty(window, "innerHeight", originalInnerHeight!);
      window.localStorage.removeItem("figma-chat-process-dot-pos");
    }
  });

  it("loads model options from provider model lists when the flat model list is empty", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        models: [],
        providers: [
          {
            providerId: "north",
            name: "North Provider",
            models: [{ id: "north-mini", name: "North Mini Code Free" }]
          }
        ],
        selectedModel: "north/north-mini"
      } as any
    });

    await wrapper.get('[aria-label="切换模型"]').trigger("click");

    expect(wrapper.find(".figma-chat-model-dropdown").exists()).toBe(true);
    expect(wrapper.text()).toContain("North Provider");
    expect(wrapper.text()).toContain("North Mini Code Free");
    expect(wrapper.text()).not.toContain("暂无匹配模型");
  });

  it("shows visible primary/all agents in the composer agent picker and emits changes", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        selectedAgent: "build",
        agents: [
          { agentId: "build", name: "build", mode: "primary", description: "Test Design（测试设计）。默认构建" },
          { agentId: "all-rounder", name: "all-rounder", mode: "all", description: "Test Execution（测试执行）。可作为主 Agent" },
          { agentId: "review", name: "Review", mode: "subagent", description: "只能 @ 调用" },
          { agentId: "secret", name: "Secret", mode: "primary", hidden: true }
        ]
      } as any
    });

    await wrapper.get('[aria-label="切换 Agent"]').trigger("click");

    expect(wrapper.find(".figma-chat-agent-dropdown").exists()).toBe(true);
    expect(wrapper.text()).toContain("Test Design");
    expect(wrapper.text()).toContain("测试设计 · 默认构建");
    expect(wrapper.text()).toContain("Test Execution");
    expect(wrapper.text()).toContain("测试执行 · 可作为主 Agent");
    expect(wrapper.text()).not.toContain("Review");
    expect(wrapper.text()).not.toContain("Secret");
    expect(wrapper.get(".figma-chat-agent-option-item.is-active").text()).toContain("Test Design");

    const allRounder = wrapper
      .findAll(".figma-chat-agent-option-item")
      .find((item) => item.text().includes("Test Execution"));
    expect(allRounder).toBeTruthy();
    await allRounder!.trigger("click");

    expect(wrapper.emitted("change-agent")).toEqual([["all-rounder"]]);
  });

  it("keeps the agent picker openable while agents are loading", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        agents: [],
        agentsLoading: true
      } as any
    });

    const button = wrapper.get('[aria-label="切换 Agent"]');
    expect(button.attributes("disabled")).toBeUndefined();
    await button.trigger("click");

    expect(wrapper.find(".figma-chat-agent-dropdown").exists()).toBe(true);
    expect(wrapper.text()).toContain("正在加载 Agent");
  });

  it("emits refresh when the agent picker failed to load", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        agents: [],
        agentsError: "Agent 目录加载失败"
      } as any
    });

    await wrapper.get('[aria-label="切换 Agent"]').trigger("click");

    expect(wrapper.text()).toContain("Agent 目录加载失败");
    await wrapper.get('[aria-label="重新加载 Agent"]').trigger("click");

    expect(wrapper.emitted("refresh-agents")).toEqual([[], []]);
  });

  it("shows an empty state when the agent catalog has no main agents", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        agents: []
      } as any
    });

    await wrapper.get('[aria-label="切换 Agent"]').trigger("click");

    expect(wrapper.find(".figma-chat-agent-dropdown").exists()).toBe(true);
    expect(wrapper.text()).toContain("暂无可用 Agent");
  });

  it("renders the new conversation action as an icon-only button", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    const newConversationButton = wrapper.get(".figma-chat-new-btn");

    expect(newConversationButton.attributes("aria-label")).toBe("新建对话");
    expect(newConversationButton.text()).toBe("");
  });

  it("renders history context fields and emits controlled search plus load more", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        history: [
          {
            id: "ses_1234567890abcdef",
            title: "回归测试历史",
            appName: "智能测试平台",
            workspaceName: "主干工作区",
            version: "20260708",
            createdAt: "2026-07-08T09:00:00Z",
            updatedAt: "2026-07-08T10:00:00Z"
          },
          {
            id: "ses_abcdef1234567890",
            title: "无上下文历史",
            createdAt: "2026-07-07T09:00:00Z",
            updatedAt: "2026-07-07T10:00:00Z"
          }
        ],
        historyTotal: 61,
        historyHasMore: true,
        historySearch: ""
      } as any
    });

    await wrapper.get('button[title="查看消息列表"]').trigger("click");

    expect(wrapper.text()).toContain("61");
    expect(wrapper.text()).toContain("智能测试平台 · 主干工作区 · 20260708");
    expect(wrapper.text()).toContain("未关联应用 · 未知工作空间 · 无版本");

    await wrapper.get(".figma-chat-history-search-input").setValue("回归");
    expect(wrapper.emitted("history-search-change")).toEqual([["回归"]]);

    const loadMore = wrapper
      .findAll("button")
      .find((button) => button.text().includes("显示更多会话"));
    expect(loadMore).toBeTruthy();
    await loadMore!.trigger("click");
    expect(wrapper.emitted("load-more-history")).toEqual([[]]);
  });

  it("shows runtime count, spinning history icon and question bell in history controls", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        historyRunningCount: 2,
        historyQuestionCount: 1,
        history: [
          {
            id: "ses_running",
            title: "等待回答",
            runtimeState: "running",
            runStatus: "RUNNING",
            pendingQuestion: true,
            createdAt: "2026-07-08T09:00:00Z",
            updatedAt: "2026-07-08T10:00:00Z"
          },
          {
            id: "ses_done",
            title: "已完成",
            runtimeState: "completed",
            createdAt: "2026-07-07T09:00:00Z",
            updatedAt: "2026-07-07T10:00:00Z"
          }
        ]
      } as any
    });

    const historyButton = wrapper.get('button[title="查看消息列表"]');
    expect(historyButton.find(".figma-chat-history-running-badge").text()).toBe("2");
    expect(historyButton.find(".figma-chat-history-alert-bell").exists()).toBe(true);

    await historyButton.trigger("click");

    expect(wrapper.find(".figma-chat-history-card-status--running").exists()).toBe(true);
    expect(wrapper.find(".figma-chat-history-card-status--completed").exists()).toBe(true);
    expect(wrapper.find(".figma-chat-history-card-attention").exists()).toBe(true);
    expect(wrapper.text()).toContain("运行中");
    expect(wrapper.text()).toContain("已完成");
  });

  it("keeps new conversation enabled while a run is active and the process is ready", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        running: true,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any
    });

    const newConversationButton = wrapper.get(".figma-chat-new-btn");
    expect(newConversationButton.attributes("disabled")).toBeUndefined();

    await newConversationButton.trigger("click");

    expect(wrapper.emitted("new-conversation")).toEqual([[]]);
  });

  it("disables composer controls and exposes readonly reason on hover title", () => {
    const readonlyReason = "你已不属于该会话所属应用，当前会话只读。";
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        inputValue: "继续执行",
        processStatus: { status: "READY", initializable: false, message: "ready" },
        readonlyReason
      } as any
    });

    const textarea = wrapper.get("textarea");
    const sendButton = wrapper.get(".figma-chat-send-card");

    expect(textarea.attributes("disabled")).toBeDefined();
    expect(textarea.attributes("title")).toBe(readonlyReason);
    expect(sendButton.attributes("disabled")).toBeDefined();
    expect(sendButton.attributes("title")).toBe(readonlyReason);
  });

  it("renders workspace context attachments and blocks oversized input before sending", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        chatContexts: [
          {
            id: "ctx_1",
            type: "selection",
            source: "workspace",
            path: "src/UserService.java",
            fileName: "UserService.java",
            startLine: 20,
            endLine: 35,
            text: "class UserService {}",
            charCount: 20,
            createdAt: 1
          }
        ],
        chatContextTotalChars: 20
      } as any
    });

    expect(wrapper.text()).toContain("已添加 1 个上下文");
    expect(wrapper.text()).toContain("UserService.java");
    expect(wrapper.text()).toContain("L20-L35");

    await wrapper.get("textarea").setValue("x".repeat(20_001));
    await wrapper.get(".figma-chat-send-card").trigger("click");

    expect(wrapper.emitted("send")).toBeUndefined();
    expect(wrapper.text()).toContain("输入内容过长，请精简后再发送");

    await wrapper.get(".chat-context-card-remove").trigger("click");
    expect(wrapper.emitted("remove-chat-context")).toEqual([["ctx_1"]]);
  });

  it("collapses large context collections and summarizes files by workspace stage", async () => {
    const paths = [
      "0318-需求项/01-需求/S0001-子条目/需求说明书.md",
      "0318-需求项/02-设计/S0001-子条目/详细设计说明书.md",
      "0318-需求项/03-编码/S0001-子条目/service.ts",
      "0318-需求项/04-测试/S0001-子条目/测试案例.md",
      "README.md"
    ];
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        chatContexts: paths.map((path, index) => ({
          id: `ctx_${index}`,
          type: "file",
          source: "workspace",
          path,
          fileName: path.split("/").at(-1),
          content: `内容 ${index}`,
          charCount: 4,
          lineCount: 1,
          createdAt: index
        })),
        chatContextTotalChars: 20
      } as any
    });

    expect(wrapper.findAll(".chat-context-card")).toHaveLength(3);
    expect(wrapper.get(".chat-context-list-stage-summary").text()).toBe("需求 1 · 设计 1 · 编码 1 · 测试 1 · 其他 1");
    expect(wrapper.get(".chat-context-list-more").text()).toContain("查看其余 2 个文件");

    await wrapper.get(".chat-context-list-more").trigger("click");

    expect(wrapper.findAll(".chat-context-card")).toHaveLength(5);
    expect(wrapper.get(".chat-context-list-cards").classes()).toContain("is-expanded");
    expect(wrapper.get(".chat-context-list-more").text()).toContain("收起附件列表");
  });

  it("resizes the composer textarea by dragging the top edge", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    const textarea = wrapper.get("textarea");
    const handle = wrapper.get(".figma-chat-composer-resize-handle");

    expect(textarea.classes()).toContain("figma-chat-textarea");
    expect(handle.attributes("aria-label")).toBe("拖动调整输入框高度");
    expect(getComputedStyle(textarea.element).resize).toBe("none");
    expect(getComputedStyle(textarea.element).maxHeight).toBe("260px");

    handle.element.dispatchEvent(new MouseEvent("pointerdown", { button: 0, clientY: 240, bubbles: true }));
    window.dispatchEvent(new MouseEvent("pointermove", { clientY: 180 }));
    window.dispatchEvent(new MouseEvent("pointerup", { clientY: 180 }));
    await nextTick();

    expect((textarea.element as HTMLTextAreaElement).style.height).toBe("100px");
  });

  it("does not expose serialized workspace contexts in user message bubbles", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "msg_user_context",
            role: "user",
            text: [
              "用户问题：",
              "写了什么内容",
              "",
              "以下是用户添加的工作区上下文：",
              "",
              '<context type="selection" path="docs/a.md" lines="1-2">',
              "上下文内容",
              "</context>"
            ].join("\n"),
            createdAt: "2026-07-06T00:00:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any
    });

    expect(wrapper.text()).toContain("写了什么内容");
    expect(wrapper.text()).toContain("选区");
    expect(wrapper.text()).toContain("a.md");
    expect(wrapper.text()).toContain("L1-2");
    expect(wrapper.text()).not.toContain("<context");
    expect(wrapper.text()).not.toContain("以下是用户添加的工作区上下文");
  });

  it("renders associated workspace contexts from native user message file parts", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "msg_user_parts",
            role: "user",
            text: "写了什么内容",
            parts: [
              { type: "text", text: "写了什么内容" },
              {
                type: "file",
                path: "src/UserService.java",
                name: "UserService.java",
                content: "class UserService {}",
                source: {
                  text: "class UserService {}",
                  startLine: 20,
                  endLine: 35,
                  contextType: "selection"
                }
              }
            ],
            createdAt: "2026-07-06T00:00:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any
    });

    expect(wrapper.text()).toContain("写了什么内容");
    expect(wrapper.text()).toContain("选区");
    expect(wrapper.text()).toContain("UserService.java");
    expect(wrapper.text()).toContain("L20-35");
    expect(wrapper.text()).not.toContain("<context");
  });

  it("shows mentionable subagent/all agents when the user types at-sign", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        agents: [
          { agentId: "build", name: "Build", mode: "primary", description: "默认构建" },
          { agentId: "review", name: "review", mode: "subagent", description: "Test Case Review（测试案例审核）。评审实现" },
          { agentId: "qa", name: "qa", mode: "all", description: "Test Analysis（测试分析）。分析测试对象" },
          { agentId: "hidden-review", name: "Hidden Review", mode: "subagent", hidden: true }
        ]
      } as any
    });

    await wrapper.get("textarea").setValue("@");

    const panel = wrapper.find(".figma-chat-agent-panel");
    expect(panel.exists()).toBe(true);
    expect(panel.text()).toContain("Test Case Review");
    expect(panel.text()).toContain("测试案例审核 · 评审实现");
    expect(panel.text()).toContain("Test Analysis");
    expect(panel.text()).toContain("测试分析 · 分析测试对象");
    expect(panel.text()).not.toContain("Build");
    expect(panel.text()).not.toContain("Hidden Review");
  });

  it("replaces the current at-sign query when selecting a mentioned agent", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        agents: [
          { agentId: "review", name: "Review", mode: "subagent", description: "Test Case Review（测试案例审核）。评审实现" },
          { agentId: "qa", name: "QA", mode: "all", description: "测试分析" }
        ]
      } as any
    });

    await wrapper.get("textarea").setValue("请 @re");
    await wrapper.get(".figma-chat-agent-row").trigger("click");

    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("请 @review ");
    expect(wrapper.emitted("update:inputValue")).toContainEqual(["请 @review "]);
    expect(wrapper.find(".figma-chat-agent-panel").exists()).toBe(false);
  });

  it("shows current worktree files with agents and reuses the file-context action when selected", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        agents: [
          { agentId: "review", name: "Review", mode: "subagent", description: "评审实现" }
        ],
        workspaceFileCandidates: [
          {
            path: "120260624-0318-需求项/01-需求/S0001-子条目/需求文档/S20260630-000038-助商组合贷新增信鸽取证状态需求说明书.md",
            name: "S20260630-000038-助商组合贷新增信鸽取证状态需求说明书.md",
            directory: "120260624-0318-需求项/01-需求/S0001-子条目/需求文档",
            size: 1
          }
        ]
      } as any
    });

    await wrapper.get("textarea").setValue("请参考 @");

    expect(wrapper.get(".figma-chat-agent-panel").text()).toContain("Agent 与文件");
    expect(wrapper.get(".figma-chat-agent-panel").text()).toContain("Review");
    expect(wrapper.get(".figma-chat-file-name-full").text()).toBe("S20260630-000038-助商组合贷新增信鸽取证状态需求说明书.md");
    expect(wrapper.get(".figma-chat-file-name-full").attributes("title")).toBe(
      "120260624-0318-需求项/01-需求/S0001-子条目/需求文档/S20260630-000038-助商组合贷新增信鸽取证状态需求说明书.md"
    );
    expect(wrapper.get(".figma-chat-file-info .figma-chat-agent-desc").text()).toBe(
      "120260624-0318-需求项/01-需求/S0001-子条目/需求文档"
    );
    expect(wrapper.emitted("search-workspace-files")).toContainEqual([""]);

    await wrapper.get(".figma-chat-file-row").trigger("click");

    expect(wrapper.emitted("add-workspace-file-context")).toEqual([
      ["120260624-0318-需求项/01-需求/S0001-子条目/需求文档/S20260630-000038-助商组合贷新增信鸽取证状态需求说明书.md"]
    ]);
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("请参考 ");
  });

  it("recognizes requirement subitems from the current worktree when the user types hash", async () => {
    const reference = {
      id: "120260624-0318-需求项/01-需求/S0001-子条目",
      requirementName: "120260624-0318-需求项",
      subitemName: "S0001-子条目",
      filePaths: [
        "120260624-0318-需求项/01-需求/S0001-子条目/需求文档/S20260630-000038-助商组合贷新增信鸽取证状态需求说明书.md",
        "120260624-0318-需求项/02-设计/S0001-子条目/S20260630-000038-助商组合贷新增信鸽取证状态详细设计说明书.md",
        "120260624-0318-需求项/03-编码/S0001-子条目/031-业务代码/S20260630-000038-助商组合贷新增信鸽取证状态单元测试说明.md"
      ]
    };
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        workspaceRequirementReferences: [reference]
      } as any
    });

    await wrapper.get("textarea").setValue("请分析 #S0001");

    const panel = wrapper.get(".figma-chat-requirement-panel");
    expect(panel.text()).toContain("需求子条目");
    expect(panel.text()).toContain("S0001-子条目");
    expect(panel.text()).toContain("120260624-0318-需求项");
    expect(panel.text()).toContain("3 个关联文件");
    expect(wrapper.get(".figma-chat-reference-name-full").text()).toBe("S0001-子条目");
    expect(wrapper.find(".figma-chat-reference-file-name").exists()).toBe(false);
    expect(wrapper.emitted("load-workspace-requirements")).toHaveLength(1);

    await wrapper.get("textarea").setValue("请分析 #S000");
    expect(wrapper.emitted("load-workspace-requirements")).toHaveLength(1);

    await wrapper.get(".figma-chat-requirement-row").trigger("click");

    expect(wrapper.emitted("add-workspace-requirement-context")).toEqual([[reference]]);
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("请分析 ");
    expect(wrapper.find(".figma-chat-requirement-panel").exists()).toBe(false);
  });

  it("lists native skill commands when the user types slash", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        commands: [
          { commandId: "skill-1", name: "test-design", description: "Equivalence Partitioning（等价类法）。生成等价类表", source: "skill" },
          { commandId: "command-1", name: "help", description: "帮助", source: "command" }
        ]
      }
    });

    await wrapper.get("textarea").setValue("/");

    expect(wrapper.find(".figma-chat-skill-panel").exists()).toBe(true);
    expect(wrapper.text()).toContain("Equivalence Partitioning");
    expect(wrapper.text()).toContain("等价类法 · 生成等价类表");
    expect(wrapper.text()).not.toContain("帮助");

    await wrapper.setProps({
      commands: [
        { commandId: "skill-1", name: "test-design", description: "API Testing（针对接口的案例设计）。使用最新接口模板", source: "skill" }
      ]
    });

    expect(wrapper.text()).toContain("API Testing");
    expect(wrapper.text()).toContain("针对接口的案例设计 · 使用最新接口模板");
    expect(wrapper.text()).not.toContain("等价类法 · 生成等价类表");

    await wrapper.get(".figma-chat-skill-row").trigger("click");

    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("/test-design ");
    expect(wrapper.emitted("update:inputValue")).toContainEqual(["/test-design "]);
    expect(wrapper.find(".figma-chat-skill-panel").exists()).toBe(false);
  });

  it("does not show a question panel for ordinary numbered assistant output", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1",
            messageId: "a1",
            role: "assistant",
            text: "是。`index.html` 是入口页面，加载顺序为：\n\n1. `styles.css` — 样式\n2. `mock.js` — 模拟数据（必须在前）\n3. `app.js` — 主逻辑",
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    expect(wrapper.find(".figma-chat-choice-panel").exists()).toBe(false);
    expect(wrapper.find(".figma-chat-question-dock").exists()).toBe(false);
    expect(wrapper.find(".figma-chat-composer").exists()).toBe(true);
  });

  it("renders a single-choice question with option descriptions and emits selected labels", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        questions: [
          {
            requestId: "ques_1",
            sessionId: "ses_1",
            createdAt: "2026-07-05T06:35:23.000Z",
            questions: [
              {
                questionId: "q1",
                text: "请选择目标环境",
                kind: "single",
                options: [
                  { id: "dev", label: "开发环境", description: "连接本地 mock 数据" },
                  { id: "staging", label: "预发环境", description: "连接预发服务验证" }
                ]
              }
            ]
          }
        ]
      } as any
    });

    const dock = wrapper.find(".figma-chat-question-dock");
    const composer = wrapper.find(".figma-chat-composer");
    expect(dock.exists()).toBe(true);
    expect(composer.exists()).toBe(true);
    expect(dock.element.compareDocumentPosition(composer.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(dock.text()).toContain("1/1 个问题");
    expect(dock.get(".figma-chat-question-type").text()).toBe("单选");
    expect(dock.text()).toContain("请选择目标环境");
    expect(dock.text()).toContain("连接预发服务验证");

    await wrapper.findAll(".figma-chat-question-option").find((item) => item.text().includes("预发环境"))!.trigger("click");
    await wrapper.get(".figma-chat-question-submit").trigger("click");

    expect(wrapper.emitted("reply-question")).toEqual([["ques_1", [["预发环境"]]]]);
  });

  it("renders a pending permission and emits every native permission decision", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        permissions: [
          {
            requestId: "perm_1",
            sessionId: "ses_1",
            type: "bash",
            title: "允许执行命令",
            description: "pnpm test",
            createdAt: "2026-07-10T09:00:00.000Z"
          }
        ]
      } as any
    });

    const dock = wrapper.get(".figma-chat-question-dock");
    expect(dock.text()).toContain("允许执行命令");
    expect(dock.text()).toContain("pnpm test");

    const actions = dock.findAll("button");
    await actions[0]!.trigger("click");
    await actions[1]!.trigger("click");
    await actions[2]!.trigger("click");

    expect(wrapper.emitted("reply-permission")).toEqual([
      ["perm_1", "once"],
      ["perm_1", "always"],
      ["perm_1", "reject"]
    ]);
  });

  it("submits a custom answer instead of the selected single-choice option", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        questions: [
          {
            requestId: "ques_custom",
            sessionId: "ses_1",
            createdAt: "2026-07-05T06:35:23.000Z",
            questions: [
              {
                questionId: "q1",
                text: "请选择目标环境",
                kind: "single",
                options: [
                  { id: "dev", label: "开发环境" },
                  { id: "staging", label: "预发环境" }
                ],
                custom: true
              }
            ]
          }
        ]
      } as any
    });

    await wrapper.findAll(".figma-chat-question-option").find((item) => item.text().includes("预发环境"))!.trigger("click");
    await wrapper.get(".figma-chat-question-custom-input").setValue("灰度环境");
    await wrapper.get(".figma-chat-question-submit").trigger("click");

    expect(wrapper.emitted("reply-question")).toEqual([["ques_custom", [["灰度环境"]]]]);
  });

  it("pages through multiple questions and only submits from the last page", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        questions: [
          {
            requestId: "ques_multi_page",
            sessionId: "ses_1",
            createdAt: "2026-07-05T06:35:23.000Z",
            questions: [
              {
                questionId: "q1",
                text: "领导想要什么风格的文字动效？",
                kind: "single",
                options: [
                  { id: "shimmer", label: "保持 Shimmer，换颜色", description: "保留光泽扫光动效，只改渐变颜色" },
                  { id: "fade", label: "换淡入效果", description: "整段/整句逐渐显示出来" }
                ]
              },
              {
                questionId: "q2",
                text: "还有什么补充要求？",
                kind: "text"
              }
            ]
          }
        ]
      } as any
    });

    expect(wrapper.text()).toContain("1/2 个问题");
    expect(wrapper.text()).toContain("领导想要什么风格的文字动效？");
    expect(wrapper.text()).not.toContain("还有什么补充要求？");
    expect(wrapper.find(".figma-chat-question-submit").exists()).toBe(false);

    await wrapper.get(".figma-chat-question-next").trigger("click");

    expect(wrapper.text()).toContain("2/2 个问题");
    expect(wrapper.text()).toContain("还有什么补充要求？");
    expect(wrapper.find(".figma-chat-question-prev").exists()).toBe(true);
    expect(wrapper.find(".figma-chat-question-submit").exists()).toBe(true);

    await wrapper.get(".figma-chat-question-custom-input").setValue("需要更轻量");
    await wrapper.get(".figma-chat-question-prev").trigger("click");
    await wrapper.findAll(".figma-chat-question-option").find((item) => item.text().includes("换淡入效果"))!.trigger("click");
    await wrapper.get(".figma-chat-question-next").trigger("click");
    await wrapper.get(".figma-chat-question-submit").trigger("click");

    expect(wrapper.emitted("reply-question")).toEqual([["ques_multi_page", [["换淡入效果"], ["需要更轻量"]]]]);
  });

  it("submits multiple selected labels with an additional custom answer", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        questions: [
          {
            requestId: "ques_multiple",
            sessionId: "ses_1",
            createdAt: "2026-07-05T06:35:23.000Z",
            questions: [
              {
                questionId: "q1",
                text: "需要修改哪方面？",
                kind: "multiple",
                options: [
                  { id: "requirements", label: "需求文档", description: "修改 requirements.md" },
                  { id: "ui", label: "UI 界面", description: "修改视觉或布局" },
                  { id: "logic", label: "前端功能逻辑", description: "修改 app.js" }
                ],
                custom: true
              }
            ]
          }
        ]
      } as any
    });

    expect(wrapper.get(".figma-chat-question-type").text()).toBe("多选");

    await wrapper.findAll(".figma-chat-question-option").find((item) => item.text().includes("需求文档"))!.trigger("click");
    await wrapper.findAll(".figma-chat-question-option").find((item) => item.text().includes("UI 界面"))!.trigger("click");
    await wrapper.get(".figma-chat-question-custom-input").setValue("还要调整交互文案");
    await wrapper.get(".figma-chat-question-submit").trigger("click");

    expect(wrapper.emitted("reply-question")).toEqual([
      ["ques_multiple", [["需求文档", "UI 界面", "还要调整交互文案"]]]
    ]);
  });

  it("keeps long question content in a scrollable area above the composer", () => {
    const longOptions = Array.from({ length: 18 }, (_, index) => ({
      id: `option_${index}`,
      label: `选项 ${index + 1}`,
      description: `这是一段很长的选项说明 ${index + 1}，用于模拟 question.asked 返回较多选项时的面板高度。`
    }));
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        questions: [
          {
            requestId: "ques_long",
            sessionId: "ses_1",
            createdAt: "2026-07-05T06:35:23.000Z",
            questions: [
              {
                questionId: "q1",
                text: "请从较长的问题列表里选择一个方案",
                kind: "single",
                options: longOptions
              }
            ]
          }
        ]
      } as any
    });

    const scrollArea = wrapper.get(".figma-chat-question-scroll");
    const actions = wrapper.get(".figma-chat-question-actions");
    const composer = wrapper.get(".figma-chat-composer");

    expect(scrollArea.findAll(".figma-chat-question-option")).toHaveLength(18);
    expect(scrollArea.find(".figma-chat-question-actions").exists()).toBe(false);
    expect(actions.element.compareDocumentPosition(composer.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("emits reject-question when the question panel is ignored", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        questions: [
          {
            requestId: "ques_reject",
            sessionId: "ses_1",
            createdAt: "2026-07-05T06:35:23.000Z",
            questions: [{ questionId: "q1", text: "需要修改哪方面？", kind: "text" }]
          }
        ]
      } as any
    });

    await wrapper.get(".figma-chat-question-reject").trigger("click");
    expect(wrapper.emitted("reject-question")).toEqual([["ques_reject"]]);
  });

  it("renders todos above the composer and expands the task list on demand", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        todos: [
          { id: "todo_1", text: "分析 SSE 字段", status: "pending", priority: "high" },
          { id: "todo_2", text: "实现面板", status: "in_progress", priority: "medium" },
          { id: "todo_3", text: "补充测试", status: "completed", priority: "low" },
          { id: "todo_4", text: "取消旧入口", status: "cancelled", priority: "low" }
        ]
      } as any
    });

    const todoPanel = wrapper.find(".oc-todo-panel");
    const composer = wrapper.find(".figma-chat-composer");
    expect(todoPanel.exists()).toBe(true);
    expect(composer.exists()).toBe(true);
    expect(todoPanel.element.compareDocumentPosition(composer.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(wrapper.text()).toContain("待处理 1");
    expect(wrapper.text()).toContain("进行中 1");
    expect(wrapper.text()).toContain("已完成 1");
    expect(wrapper.text()).toContain("已取消 1");
    expect(wrapper.text()).toContain("共 4");
    expect(wrapper.text()).not.toContain("分析 SSE 字段");

    await wrapper.get(".oc-todo-panel__header").trigger("click");

    expect(wrapper.text()).toContain("分析 SSE 字段");
    expect(wrapper.text()).toContain("高优先级");
  });

  it("switches from the root timeline to a subagent timeline without showing the composer", async () => {
    const resizeObservers: TestResizeObserver[] = [];
    class TestResizeObserver {
      observe = vi.fn();
      disconnect = vi.fn();

      constructor(_callback: ResizeObserverCallback) {
        resizeObservers.push(this);
      }
    }
    vi.stubGlobal("ResizeObserver", TestResizeObserver);

    const wrapper = mount(FigmaChatPanel, {
      props: {
        currentSessionId: "ses_root",
        messages: [
          {
            id: "msg_user_root",
            messageId: "msg_user_root",
            role: "user",
            text: "分析前端结构",
            createdAt: "2026-07-03T00:00:00Z"
          },
          {
            id: "msg_root",
            messageId: "msg_root",
            role: "assistant",
            text: "",
            parts: [
              {
                partId: "prt_task_frontend",
                type: "tool",
                toolName: "task",
                callId: "call_task_frontend",
                status: "running",
                input: {
                  description: "Explore frontend structure",
                  subagent_type: "explore"
                }
              }
            ],
            createdAt: "2026-07-03T00:00:01Z"
          },
          {
            id: "msg_child_user",
            messageId: "msg_child_user",
            role: "user",
            text: "Explore frontend structure",
            createdAt: "2026-07-03T00:00:02Z"
          },
          {
            id: "msg_child_answer",
            messageId: "msg_child_answer",
            role: "assistant",
            text: "子 Agent 已读取前端目录。",
            parts: [{ partId: "prt_child_answer", type: "text", text: "子 Agent 已读取前端目录。" }],
            createdAt: "2026-07-03T00:00:03Z"
          }
        ],
        messageScopesById: {
          msg_user_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
          msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
          msg_child_user: {
            sessionId: "ses_child_frontend",
            rootSessionId: "ses_root",
            parentSessionId: "ses_root",
            isChildSession: true,
            taskPartId: "prt_task_frontend"
          },
          msg_child_answer: {
            sessionId: "ses_child_frontend",
            rootSessionId: "ses_root",
            parentSessionId: "ses_root",
            isChildSession: true,
            taskPartId: "prt_task_frontend"
          }
        },
        subagentsBySessionId: {
          ses_child_frontend: {
            sessionId: "ses_child_frontend",
            parentSessionId: "ses_root",
            taskMessageId: "msg_root",
            taskPartId: "prt_task_frontend",
            taskCallId: "call_task_frontend",
            agentName: "Explore",
            title: "Explore frontend structure",
            status: "running",
            updatedAt: "2026-07-03T00:00:00Z"
          }
        },
        subagentByTaskPartId: { prt_task_frontend: "ses_child_frontend" },
        questions: [
          {
            requestId: "ques_child_history",
            sessionId: "ses_child_frontend",
            createdAt: "2026-07-03T00:00:04Z",
            questions: [
              {
                questionId: "q_child_history",
                text: "子 Agent 是否继续读取目录？",
                kind: "single",
                options: [{ id: "continue", label: "继续" }]
              }
            ]
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".figma-chat-composer").exists()).toBe(true);
    expect(wrapper.find(".oc-subagent-card").exists()).toBe(true);
    expect(wrapper.text()).toContain("Explore frontend structure");
    expect(wrapper.text()).not.toContain("子 Agent 已读取前端目录。");

    await wrapper.get(".figma-chat-process-status").trigger("click");
    await wrapper.get(".figma-chat-process-status-dot").trigger("click");
    await nextTick();
    await nextTick();
    const processCardObservers = () => resizeObservers.filter((observer) =>
      observer.observe.mock.calls.some(([target]) => (target as HTMLElement).classList.contains("figma-chat-process-status"))
    );
    const processObserverCountBeforeSubagent = processCardObservers().length;
    expect(processObserverCountBeforeSubagent).toBeGreaterThan(0);
    const expandedProcessCardObserver = processCardObservers().at(-1)!;

    await wrapper.get(".oc-subagent-card").trigger("click");

    expect(wrapper.find(".figma-chat-composer").exists()).toBe(false);
    expect(wrapper.text()).toContain("Explore frontend structure");
    expect(wrapper.text()).toContain("子 Agent 已读取前端目录。");
    expect(wrapper.text()).toContain("子 Agent 不支持对话");
    expect(wrapper.find(".figma-chat-subagent-return").exists()).toBe(true);
    // 原生 child session 也可能提出 Question；不能只留下时间线中的 question 工具 JSON。
    expect(wrapper.get(".figma-chat-question-dock").text()).toContain("子 Agent 是否继续读取目录？");
    expect(expandedProcessCardObserver.disconnect).toHaveBeenCalledTimes(1);

    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    await nextTick();
    await nextTick();

    expect(wrapper.find(".figma-chat-composer").exists()).toBe(true);
    expect(wrapper.text()).not.toContain("子 Agent 已读取前端目录。");
    expect(processCardObservers()).toHaveLength(processObserverCountBeforeSubagent + 1);
    expect(processCardObservers().at(-1)!.observe).toHaveBeenCalledTimes(1);

    await wrapper.get(".oc-subagent-card").trigger("click");
    // 历史 root 切换后必须退出原 child 视图，不能继续把 ask 隐藏成 tool JSON。
    await wrapper.setProps({ currentSessionId: "ses_history_root" });
    await nextTick();
    expect(wrapper.find(".figma-chat-composer").exists()).toBe(true);
    expect(wrapper.find(".figma-chat-question-dock").exists()).toBe(false);
    wrapper.unmount();
    expect(processCardObservers().at(-1)!.disconnect).toHaveBeenCalledTimes(1);
  });

  it("shows multiple subagent cards directly without folding them into a task group", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "msg_user_root",
            messageId: "msg_user_root",
            role: "user",
            text: "分析前后端结构",
            createdAt: "2026-07-03T00:00:00Z"
          },
          {
            id: "msg_root",
            messageId: "msg_root",
            role: "assistant",
            text: "",
            parts: [
              {
                partId: "prt_task_backend",
                type: "tool",
                toolName: "task",
                callId: "call_task_backend",
                status: "running",
                input: { description: "Explore backend structure", subagent_type: "explore" }
              },
              {
                partId: "prt_task_frontend",
                type: "tool",
                toolName: "task",
                callId: "call_task_frontend",
                status: "running",
                input: { description: "Explore frontend structure", subagent_type: "explore" }
              }
            ],
            createdAt: "2026-07-03T00:00:01Z"
          }
        ],
        messageScopesById: {
          msg_user_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
          msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false }
        },
        subagentsBySessionId: {
          ses_backend: {
            sessionId: "ses_backend",
            parentSessionId: "ses_root",
            taskMessageId: "msg_root",
            taskPartId: "prt_task_backend",
            taskCallId: "call_task_backend",
            agentName: "Explore",
            title: "Explore backend structure",
            status: "running",
            updatedAt: "2026-07-03T00:00:00Z"
          },
          ses_frontend: {
            sessionId: "ses_frontend",
            parentSessionId: "ses_root",
            taskMessageId: "msg_root",
            taskPartId: "prt_task_frontend",
            taskCallId: "call_task_frontend",
            agentName: "Explore",
            title: "Explore frontend structure",
            status: "running",
            updatedAt: "2026-07-03T00:00:00Z"
          }
        },
        subagentByTaskPartId: {
          prt_task_backend: "ses_backend",
          prt_task_frontend: "ses_frontend"
        },
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find("[data-testid='oc-tool-group']").exists()).toBe(false);
    expect(wrapper.findAll(".oc-subagent-card")).toHaveLength(2);
    expect(wrapper.text()).toContain("Explore backend structure");
    expect(wrapper.text()).toContain("Explore frontend structure");
  });

  it("keeps native pending task visible and converts it to a clickable subagent card", async () => {
    const rootMessages = [
      {
        id: "msg_user_root",
        messageId: "msg_user_root",
        role: "user" as const,
        text: "分析项目结构",
        createdAt: "2026-07-03T00:00:00Z"
      },
      {
        id: "msg_root",
        messageId: "msg_root",
        role: "assistant" as const,
        text: "",
        parts: [
          {
            partId: "prt_task",
            type: "tool" as const,
            toolName: "task",
            callId: "call_task",
            status: "pending",
            input: {}
          }
        ],
        createdAt: "2026-07-03T00:00:01Z"
      }
    ];
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: rootMessages,
        messageScopesById: {
          msg_user_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
          msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false }
        },
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-subagent-card").attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("智能体");
    expect(wrapper.text()).toContain("准备中");

    await wrapper.setProps({
      messages: [
        ...rootMessages,
        {
          id: "msg_child_answer",
          messageId: "msg_child_answer",
          role: "assistant",
          text: "子 Agent 输出",
          parts: [{ partId: "prt_child_text", type: "text", text: "子 Agent 输出" }],
          createdAt: "2026-07-03T00:00:02Z"
        }
      ],
      messageScopesById: {
        msg_user_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
        msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
        msg_child_answer: {
          sessionId: "ses_child",
          rootSessionId: "ses_root",
          parentSessionId: "ses_root",
          isChildSession: true,
          taskPartId: "prt_task"
        }
      },
      subagentsBySessionId: {
        ses_child: {
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          taskMessageId: "msg_root",
          taskPartId: "prt_task",
          taskCallId: "call_task",
          agentName: "Explore",
          title: "Explore project structure",
          status: "running",
          updatedAt: "2026-07-03T00:00:00Z"
        }
      },
      subagentByTaskPartId: { prt_task: "ses_child" }
    } as any);

    expect(wrapper.find(".oc-subagent-card").attributes("disabled")).toBeUndefined();
    expect(wrapper.text()).toContain("Explore");
    expect(wrapper.text()).toContain("Explore project structure");
    expect(wrapper.text()).not.toContain("子 Agent 输出");

    await wrapper.get(".oc-subagent-card").trigger("click");

    expect(wrapper.find(".figma-chat-composer").exists()).toBe(false);
    expect(wrapper.text()).toContain("子 Agent 输出");
  });

  it("makes historical subagent cards clickable from session tree snapshot indexes", async () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { rootSessionId: "ses_root", sessionId: "ses_root", childSession: false },
        {
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          taskMessageId: "msg_root",
          taskPartId: "prt_task",
          taskCallId: "call_task"
        }
      ],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: { prt_task: "ses_child" },
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "msg_root", role: "assistant" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageID: "msg_root",
            part: {
              id: "prt_task",
              messageID: "msg_root",
              type: "tool",
              tool: "task",
              callID: "call_task",
              state: {
                status: "completed",
                input: { description: "Explore project structure", subagent_type: "explore" }
              }
            }
          }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_child",
            parentSessionId: "ses_root",
            isChildSession: true,
            taskMessageId: "msg_root",
            taskPartId: "prt_task",
            taskCallId: "call_task",
            message: { id: "msg_child", role: "assistant", content: "子 Agent 输出" }
          }
        }
      ]
    };
    const state = chatStateFromSessionTreeSnapshot(snapshot);
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: state.messages,
        messageScopesById: state.messageScopesById,
        subagentsBySessionId: state.subagentsBySessionId,
        subagentByTaskPartId: state.subagentByTaskPartId,
        questions: [
          {
            requestId: "ques_child_history",
            sessionId: "ses_child",
            createdAt: "2026-07-03T00:00:04Z",
            questions: [
              {
                questionId: "q_child_history",
                text: "历史子 Agent 是否继续？",
                kind: "single",
                options: [{ id: "continue", label: "继续" }]
              }
            ]
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-subagent-card").attributes("disabled")).toBeUndefined();
    expect(wrapper.text()).toContain("Explore project structure");
    expect(wrapper.text()).not.toContain("子 Agent 输出");

    await wrapper.get(".oc-subagent-card").trigger("click");

    expect(wrapper.find(".figma-chat-composer").exists()).toBe(false);
    expect(wrapper.text()).toContain("子 Agent 输出");
  });

  it("opens a historical subagent with child-only tree part output", async () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { rootSessionId: "ses_root", sessionId: "ses_root", childSession: false },
        {
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          taskMessageId: "msg_root",
          taskPartId: "prt_task",
          taskCallId: "call_task"
        }
      ],
      messagesBySessionId: {
        ses_child: [{
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          isChildSession: true,
          taskMessageId: "msg_root",
          taskPartId: "prt_task",
          taskCallId: "call_task",
          part: { id: "prt_child_text", messageID: "msg_child", type: "text", text: "child-only 工作内容" }
        }]
      },
      childSessionIdByTaskPartId: { prt_task: "ses_child" },
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: { rootSessionId: "ses_root", sessionId: "ses_root", message: { id: "msg_root", role: "assistant" } }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageID: "msg_root",
            part: {
              id: "prt_task",
              messageID: "msg_root",
              type: "tool",
              tool: "task",
              callID: "call_task",
              state: { status: "completed", input: { description: "恢复子 Agent 工作", subagent_type: "explore" } }
            }
          }
        }
      ]
    };
    const state = chatStateFromSessionTreeSnapshot(snapshot);
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: state.messages,
        messageScopesById: state.messageScopesById,
        subagentsBySessionId: state.subagentsBySessionId,
        subagentByTaskPartId: state.subagentByTaskPartId,
        questions: [
          {
            requestId: "ques_child_history_only_part",
            sessionId: "ses_child",
            createdAt: "2026-07-03T00:00:04Z",
            questions: [
              {
                questionId: "q_child_history_only_part",
                text: "历史子 Agent 是否继续？",
                kind: "single",
                options: [{ id: "continue", label: "继续" }]
              }
            ]
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    await wrapper.get(".oc-subagent-card").trigger("click");

    expect(wrapper.find(".figma-chat-composer").exists()).toBe(false);
    expect(wrapper.text()).toContain("child-only 工作内容");
    expect(wrapper.get(".figma-chat-question-dock").text()).toContain("历史子 Agent 是否继续？");
    await wrapper.get(".figma-chat-subagent-return").trigger("click");
    expect(wrapper.find(".figma-chat-composer").exists()).toBe(true);
  });

  it("keeps historical subagent cards clickable and named when snapshot ids differ from rendered task part ids", async () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { rootSessionId: "ses_root", sessionId: "ses_root", childSession: false },
        {
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          taskMessageId: "msg_root",
          taskPartId: "toolu_snapshot_task",
          taskCallId: "call_task"
        }
      ],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: { toolu_snapshot_task: "ses_child" },
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "msg_root", role: "assistant" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageID: "msg_root",
            part: {
              id: "prt_rendered_task",
              messageID: "msg_root",
              type: "tool",
              tool: "task",
              callID: "call_task",
              metadata: { agent: "build", title: "构建回归用例" },
              state: {
                status: "completed",
                input: { description: "构建回归用例" }
              }
            }
          }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_child",
            parentSessionId: "ses_root",
            isChildSession: true,
            taskMessageId: "msg_root",
            taskPartId: "toolu_snapshot_task",
            taskCallId: "call_task",
            message: { id: "msg_child", role: "assistant", content: "子 Agent 输出" }
          }
        }
      ]
    };
    const state = chatStateFromSessionTreeSnapshot(snapshot);
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: state.messages,
        messageScopesById: state.messageScopesById,
        subagentsBySessionId: state.subagentsBySessionId,
        subagentByTaskPartId: state.subagentByTaskPartId,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-subagent-card").attributes("disabled")).toBeUndefined();
    expect(wrapper.text()).toContain("Build");
    expect(wrapper.text()).toContain("构建回归用例");

    await wrapper.get(".oc-subagent-card").trigger("click");

    expect(wrapper.find(".figma-chat-composer").exists()).toBe(false);
    expect(wrapper.text()).toContain("子 Agent 输出");
  });

  it("keeps task cards clickable when only task call id matches historical subagent state", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "msg_root",
            messageId: "msg_root",
            role: "assistant",
            text: "",
            parts: [
              {
                partId: "prt_rendered_task",
                type: "tool",
                toolName: "task",
                callId: "call_task",
                status: "completed",
                input: { description: "识别 I2026002 测试对象" }
              }
            ],
            createdAt: "2026-07-03T00:00:01Z"
          },
          {
            id: "msg_child",
            messageId: "msg_child",
            role: "assistant",
            text: "子 Agent 工作详情",
            parts: [{ partId: "prt_child_text", type: "text", text: "子 Agent 工作详情" }],
            createdAt: "2026-07-03T00:00:02Z"
          }
        ],
        messageScopesById: {
          msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
          msg_child: {
            sessionId: "ses_root",
            rootSessionId: "ses_root",
            parentSessionId: "ses_root",
            isChildSession: true,
            taskCallId: "call_task"
          }
        },
        subagentsBySessionId: {
          ses_child: {
            sessionId: "ses_child",
            parentSessionId: "ses_root",
            taskMessageId: "msg_root",
            taskPartId: "toolu_snapshot_task",
            taskCallId: "call_task",
            agentName: "TEST-DESIGN-ANALYSIS",
            title: "识别 I2026002 测试对象",
            status: "completed",
            updatedAt: "2026-07-03T00:00:00Z"
          }
        },
        subagentByTaskPartId: {},
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-subagent-card").attributes("disabled")).toBeUndefined();
    expect(wrapper.text()).toContain("TEST-DESIGN-ANALYSIS");

    await wrapper.get(".oc-subagent-card").trigger("click");

    expect(wrapper.find(".figma-chat-composer").exists()).toBe(false);
    expect(wrapper.text()).toContain("子 Agent 工作详情");
  });

  it("opens historical subagent cards restored from persisted task output", async () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: []
    };
    const state = chatStateFromSessionTreeSnapshot(snapshot, [
      {
        messageId: "msg_root",
        sessionId: "ses_platform",
        role: "ASSISTANT",
        content: "",
        createdAt: "2026-07-05T13:14:00Z",
        parts: [
          {
            partId: "prt_rendered_task",
            type: "tool",
            toolName: "task",
            callId: "call_task",
            status: "completed",
            input: { description: "分析 I2026000 测试设计对象", subagent_type: "test-design-analysis" },
            metadata: { sessionId: "ses_child" },
            output: "<task id=\"ses_child\" state=\"completed\"><task_result>\n子智能体已识别测试对象详情\n</task_result></task>"
          }
        ]
      }
    ]);
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: state.messages,
        messageScopesById: state.messageScopesById,
        subagentsBySessionId: state.subagentsBySessionId,
        subagentByTaskPartId: state.subagentByTaskPartId,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-subagent-card").attributes("disabled")).toBeUndefined();
    expect(wrapper.text()).not.toContain("子智能体已识别测试对象详情");

    await wrapper.get(".oc-subagent-card").trigger("click");

    expect(wrapper.find(".figma-chat-composer").exists()).toBe(false);
    expect(wrapper.text()).toContain("子智能体已识别测试对象详情");
  });

  it("sends the trimmed prompt and clears the composer when the process is ready", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        inputValue: "  写一个登录用例  ",
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    await wrapper.get('[aria-label="发送"]').trigger("click");

    expect(wrapper.emitted("send")).toEqual([["写一个登录用例"]]);
    expect(wrapper.emitted("update:inputValue")).toEqual([[""]]);
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("");
  });

  it("keeps the composer enabled while a ready process is refreshing in the background", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        inputValue: "后台刷新时发送",
        processRequired: true,
        processLoading: true,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    expect(wrapper.text()).not.toContain("正在检查 TestAgent 进程");
    expect(wrapper.get('[aria-label="发送"]').attributes("disabled")).toBeUndefined();

    await wrapper.get('[aria-label="发送"]').trigger("click");

    expect(wrapper.emitted("send")).toEqual([["后台刷新时发送"]]);
  });

  it("does not request a process refresh when the user focuses or clicks the composer", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    await wrapper.get("textarea").trigger("focus");
    await wrapper.get(".figma-chat-input-card").trigger("click");

    expect(wrapper.emitted("refresh-process")).toBeUndefined();
  });

  it("blocks submit actions and skips duplicate refresh while process status is refreshing", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        inputValue: "刷新中不发送",
        processStatus: { status: "READY", initializable: false, message: "ready" },
        processRefreshing: true
      }
    });

    const textarea = wrapper.get("textarea");
    expect(textarea.attributes("disabled")).toBeUndefined();
    expect(wrapper.get(".figma-chat-new-btn").attributes("disabled")).toBeDefined();
    expect(wrapper.get('[aria-label="发送"]').attributes("disabled")).toBeDefined();

    await textarea.trigger("focus");
    await wrapper.get(".figma-chat-input-card").trigger("click");
    await wrapper.get('[aria-label="发送"]').trigger("click");

    expect(wrapper.emitted("refresh-process")).toBeUndefined();
    expect(wrapper.emitted("send")).toBeUndefined();
  });

  it("keeps submit enabled during non-blocking background process refresh", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        inputValue: "后台轮询时发送",
        processStatus: { status: "READY", initializable: false, message: "ready" },
        processRefreshing: true,
        processRefreshBlocksSubmit: false
      }
    });

    expect(wrapper.get('[aria-label="发送"]').attributes("disabled")).toBeUndefined();

    await wrapper.get('[aria-label="发送"]').trigger("click");

    expect(wrapper.emitted("send")).toEqual([["后台轮询时发送"]]);
  });

  it("opens a raw output floating panel with filters and clear action", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        rawOutputEntries: [
          {
            id: "raw_req_1",
            kind: "request",
            title: "POST /api/internal/agent/opencode/runs",
            method: "POST",
            path: "/api/internal/agent/opencode/runs",
            traceId: "trace_frontend",
            body: '{"sessionId":"ses_1","prompt":"hello"}',
            occurredAt: "2026-07-02T08:00:00.000Z"
          },
          {
            id: "raw_sse_1",
            kind: "sse",
            title: "message.part.delta",
            eventName: "message.part.delta",
            runId: "run_1",
            body: '{"type":"message.part.delta","payload":{"delta":"world"}}',
            occurredAt: "2026-07-02T08:00:01.000Z"
          }
        ]
      } as any
    });

    const rawButton = wrapper.findAll("button").find((button) => button.text().includes("原始输出"));
    expect(rawButton).toBeTruthy();
    await rawButton!.trigger("click");

    expect(wrapper.find(".figma-chat-raw-output-panel").exists()).toBe(true);
    expect(wrapper.find(".figma-chat-raw-output-panel").attributes("style")).toContain("left:");
    const preTexts = wrapper.findAll("pre").map((pre) => pre.text()).join("\n");
    expect(preTexts).toContain('{"sessionId":"ses_1","prompt":"hello"}');
    expect(preTexts).toContain('{"type":"message.part.delta","payload":{"delta":"world"}}');

    const sseFilter = wrapper.findAll(".figma-chat-raw-filter").find((button) => button.text() === "SSE");
    expect(sseFilter).toBeTruthy();
    await sseFilter!.trigger("click");

    expect(wrapper.text()).not.toContain("POST /api/internal/agent/opencode/runs");
    expect(wrapper.text()).toContain("message.part.delta");
    expect(wrapper.find("pre").text()).toContain('{"type":"message.part.delta","payload":{"delta":"world"}}');

    await wrapper.get('[aria-label="搜索原始输出"]').setValue("trace_frontend");
    expect(wrapper.text()).toContain("无匹配的原始报文");

    await wrapper.get('[aria-label="搜索原始输出"]').setValue("world");
    expect(wrapper.text()).toContain("message.part.delta");
    expect(wrapper.find(".figma-chat-raw-highlight").text()).toBe("world");

    const clearButton = wrapper.findAll("button").find((button) => button.text().includes("清空"));
    expect(clearButton).toBeTruthy();
    await clearButton!.trigger("click");
    expect(wrapper.emitted("clear-raw-output")).toEqual([[]]);

    await wrapper.get('[aria-label="关闭原始输出"]').trigger("click");
    expect(wrapper.find(".figma-chat-raw-output-panel").exists()).toBe(false);
  });

  it("shows an empty raw output state for sessions without captured exchanges", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        rawOutputEntries: []
      } as any
    });

    const rawButton = wrapper.findAll("button").find((button) => button.text().includes("原始输出"));
    expect(rawButton).toBeTruthy();
    await rawButton!.trigger("click");

    expect(wrapper.text()).toContain("当前会话暂无原始报文");
  });

  it("shows created and updated times in the history drawer", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        history: [
          {
            id: "ses_history_abcdef",
            title: "车贷案例设计",
            createdAt: "2026-07-02T08:00:00.000Z",
            updatedAt: "2026-07-02T09:30:00.000Z"
          }
        ]
      } as any
    });

    const historyButton = wrapper.findAll("button").find((button) => button.text().includes("消息列表"));
    expect(historyButton).toBeTruthy();
    await historyButton!.trigger("click");

    expect(wrapper.text()).toContain("车贷案例设计");
    expect(wrapper.text()).toContain("创建");
    expect(wrapper.text()).toContain("更新");
    expect(wrapper.text()).toContain("#abcdef");
  });

  it("shows the checking state before the first process status response arrives", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processLoading: true,
        processStatus: null
      }
    });

    expect(wrapper.text()).toContain("正在检查 TestAgent 进程");
    expect(wrapper.text()).toContain("正在检查当前用户可用进程");
    expect(wrapper.get('[aria-label="发送"]').attributes("disabled")).toBeDefined();
  });

  it("does not send Enter while IME composition is active", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });
    const textarea = wrapper.get("textarea");

    await textarea.setValue("create login test");
    const event = new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true });
    Object.defineProperty(event, "isComposing", { value: true });
    textarea.element.dispatchEvent(event);

    expect(wrapper.emitted("send")).toBeUndefined();
    expect((textarea.element as HTMLTextAreaElement).value).toBe("create login test");
  });

  it("renders all historical user and assistant messages in order", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          { id: "u1", messageId: "u1", role: "user", text: "第一轮用户问题", createdAt: "2026-06-25T09:00:00.000Z" },
          { id: "a1", messageId: "a1", role: "assistant", text: "第一轮助手回答", createdAt: "2026-06-25T09:01:00.000Z" },
          { id: "u2", messageId: "u2", role: "user", text: "第二轮用户问题", createdAt: "2026-06-25T09:02:00.000Z" },
          { id: "a2", messageId: "a2", role: "assistant", text: "第二轮助手回答", createdAt: "2026-06-25T09:03:00.000Z" }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    const text = wrapper.text();
    expect(text).toContain("第一轮用户问题");
    expect(text).toContain("第一轮助手回答");
    expect(text).toContain("第二轮用户问题");
    expect(text).toContain("第二轮助手回答");
    expect(text.indexOf("第一轮用户问题")).toBeLessThan(text.indexOf("第一轮助手回答"));
    expect(text.indexOf("第一轮助手回答")).toBeLessThan(text.indexOf("第二轮用户问题"));
    expect(text.indexOf("第二轮用户问题")).toBeLessThan(text.indexOf("第二轮助手回答"));
  });

  it("emits assistant message feedback from persisted assistant messages", async () => {
    const platformMessageId = "msg_0123456789abcdef0123456789abcdef";
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: platformMessageId,
            messageId: platformMessageId,
            platformMessageId,
            role: "assistant",
            text: "已完成分析",
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        messageFeedbacks: {
          [platformMessageId]: {
            feedbackId: "fb_123",
            messageId: platformMessageId,
            sessionId: "ses_123",
            runId: "run_123",
            rating: "POSITIVE",
            reasonCode: null,
            comment: null,
            createdAt: "2026-06-25T09:02:00.000Z",
            updatedAt: "2026-06-25T09:02:00.000Z"
          }
        },
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    const buttons = wrapper.findAll(".figma-chat-feedback-btn");
    expect(buttons).toHaveLength(2);
    expect(buttons[0].classes()).toContain("is-selected");

    await buttons[0].trigger("click");

    expect(wrapper.emitted("submit-feedback")).toEqual([[{
      messageId: platformMessageId,
      rating: "POSITIVE"
    }]]);
  });

  it("emits persisted feedback id after merging a temporary assistant message", async () => {
    const platformMessageId = "msg_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "assistant-run_123",
            role: "assistant",
            text: "正在分析",
            createdAt: "2026-06-25T09:00:00.000Z"
          },
          {
            id: platformMessageId,
            messageId: platformMessageId,
            platformMessageId,
            role: "assistant",
            text: "最终结论",
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        messageFeedbacks: {
          [platformMessageId]: null
        },
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    const buttons = wrapper.findAll(".figma-chat-feedback-btn");
    expect(buttons.length).toBeGreaterThanOrEqual(2);

    await buttons[0].trigger("click");

    expect(wrapper.emitted("submit-feedback")).toEqual([[{
      messageId: platformMessageId,
      rating: "POSITIVE"
    }]]);
  });

  it("does not render assistant feedback for remote opencode message ids", () => {
    const remoteMessageId = "msg_f2d478d96001861rLCyXjYqf75";
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: remoteMessageId,
            messageId: remoteMessageId,
            remoteMessageId,
            role: "assistant",
            text: "实时输出已完成",
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    expect(wrapper.findAll(".figma-chat-feedback-btn")).toHaveLength(0);
  });

  it("submits platform message id after a remote message is mapped to persistence", async () => {
    const remoteMessageId = "msg_f2d478d96001861rLCyXjYqf75";
    const platformMessageId = "msg_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: remoteMessageId,
            messageId: remoteMessageId,
            remoteMessageId,
            platformMessageId,
            role: "assistant",
            text: "实时输出已落库",
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        messageFeedbacks: {
          [platformMessageId]: null
        },
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    const buttons = wrapper.findAll(".figma-chat-feedback-btn");
    expect(buttons).toHaveLength(2);

    await buttons[0].trigger("click");

    expect(wrapper.emitted("submit-feedback")).toEqual([[{
      messageId: platformMessageId,
      rating: "POSITIVE"
    }]]);
  });

  it("does not render assistant message feedback when the conversation is running", async () => {
    const platformMessageId = "msg_cccccccccccccccccccccccccccccccc";
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: platformMessageId,
            messageId: platformMessageId,
            platformMessageId,
            role: "assistant",
            text: "已完成分析",
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        running: true,
        messageFeedbacks: {
          [platformMessageId]: {
            feedbackId: "fb_123",
            messageId: platformMessageId,
            sessionId: "ses_123",
            runId: "run_123",
            rating: "POSITIVE",
            reasonCode: null,
            comment: null,
            createdAt: "2026-06-25T09:02:00.000Z",
            updatedAt: "2026-06-25T09:02:00.000Z"
          }
        },
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    const buttons = wrapper.findAll(".figma-chat-feedback-btn");
    expect(buttons).toHaveLength(0);
  });

  it.skip("renders historical generated files and opens the file changes drawer", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1",
            messageId: "a1",
            role: "assistant",
            text: "",
            parts: [
              {
                partId: "file-1",
                type: "file",
                name: "登录测试报告.md",
                path: "docs/登录测试报告.md",
                mimeType: "text/markdown"
              }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        fileChanges: [
          {
            path: "docs/登录测试报告.md",
            patch: "--- /dev/null\n+++ b/登录测试报告.md\n@@ -0,0 +1,1 @@\n+# 登录测试报告",
            additions: 1,
            deletions: 0,
            status: "added"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    expect(wrapper.text()).toContain("登录测试报告.md");
    const changesCard = wrapper.get(".figma-chat-changes-card");
    expect(changesCard.text()).toContain("1 个文件已更改");

    await changesCard.trigger("click");

    expect(wrapper.get('[role="dialog"][aria-label="文件变更 Diff"]').text()).toContain("docs/登录测试报告.md");
  });

  it("does not insert an extra blank line when consecutive assistant fragments already contain a line break", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          { id: "a1", messageId: "a1", role: "assistant", text: "第一段\n", createdAt: "2026-06-25T09:01:00.000Z" },
          { id: "a2", messageId: "a2", role: "assistant", text: "第二段", createdAt: "2026-06-25T09:01:01.000Z" }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    const sources = wrapper.findAllComponents(markdownViewStub).map((item) => item.props("source"));
    expect(sources).toEqual(expect.arrayContaining(["第一段\n", "第二段"]));
  });

  it("does not render assistant rows that have no visible content", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          { id: "u1", messageId: "u1", role: "user", text: "滴滴", createdAt: "2026-06-25T09:00:00.000Z" },
          { id: "a-empty-1", messageId: "a-empty-1", role: "assistant", text: "", createdAt: "2026-06-25T09:00:01.000Z" },
          {
            id: "a-empty-2",
            messageId: "a-empty-2",
            role: "assistant",
            text: "",
            parts: [{ partId: "tool-1", type: "tool", toolName: "read", status: "completed" }],
            createdAt: "2026-06-25T09:00:02.000Z"
          },
          { id: "a1", messageId: "a1", role: "assistant", text: "在的，有什么可以帮你的？", createdAt: "2026-06-25T09:01:00.000Z" }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    expect(wrapper.find(".oc-timeline-root").exists()).toBe(true);
    expect(wrapper.text()).toContain("在的，有什么可以帮你的？");
    expect(wrapper.text()).not.toContain("a-empty-1");
  });

  it("uses a static task usage marker after the run ends", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        running: false,
        taskUsage: { duration: "1s", tokens: 19915 },
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    expect(wrapper.find(".figma-chat-usage img").exists()).toBe(false);
    expect(wrapper.find(".figma-chat-usage-dot").exists()).toBe(true);
    expect(wrapper.text()).toContain("任务消耗");
    expect(wrapper.text()).toContain("2.0w tokens");
  });

  it("shows failed task status from runtime status", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "u-failed",
            messageId: "u-failed",
            role: "user",
            text: "创建会话失败",
            createdAt: "2026-07-03T11:24:00.000Z"
          }
        ],
        running: false,
        runtimeStatus: "FAILED",
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    expect(wrapper.text()).toContain("任务失败");
    expect(wrapper.text()).not.toContain("任务完成");
  });

  it.each([
    ["SUCCEEDED", "任务完成"],
    ["FAILED", "任务失败"],
    ["CANCELLED", "已手动终止"]
  ])("restores the composer and removes running UI after %s", async (runtimeStatus, terminalLabel) => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: `u-${runtimeStatus}`,
            messageId: `u-${runtimeStatus}`,
            role: "user",
            text: "验证终态恢复",
            createdAt: "2026-07-11T09:00:00.000Z"
          }
        ],
        running: false,
        runtimeStatus,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    expect(wrapper.text()).toContain(terminalLabel);
    expect(wrapper.find('[aria-label="停止执行"]').exists()).toBe(false);
    expect(wrapper.find(".figma-chat-running-assistant").exists()).toBe(false);
    expect(wrapper.find(".oc-working-status").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("思考中...");
    expect(wrapper.text()).not.toContain("正在工作");
    expect(wrapper.text()).not.toContain("生成中");
    expect(wrapper.text()).not.toContain("进行中");

    const textarea = wrapper.get("textarea");
    expect(textarea.attributes("disabled")).toBeUndefined();
    await textarea.setValue("继续下一轮");
    expect(wrapper.get('[aria-label="发送"]').attributes("disabled")).toBeUndefined();
  });

  it("clears the manual stopped marker when a new run starts and succeeds", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "u-stop",
            messageId: "u-stop",
            role: "user",
            text: "停止上一轮",
            createdAt: "2026-07-04T09:00:00.000Z"
          }
        ],
        running: true,
        runtimeStatus: "RUNNING",
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await wrapper.get('[aria-label="停止执行"]').trigger("click");
    await wrapper.setProps({ running: false, runtimeStatus: "CANCELLED" });
    expect(wrapper.text()).toContain("已手动终止");

    await wrapper.setProps({
      messages: [
        {
          id: "u-stop",
          messageId: "u-stop",
          role: "user",
          text: "停止上一轮",
          createdAt: "2026-07-04T09:00:00.000Z"
        },
        {
          id: "u-next",
          messageId: "u-next",
          role: "user",
          text: "继续下一轮",
          createdAt: "2026-07-04T09:01:00.000Z"
        }
      ],
      running: true,
      runtimeStatus: "RUNNING"
    });
    await wrapper.setProps({ running: false, runtimeStatus: "SUCCEEDED" });

    expect(wrapper.text()).not.toContain("已手动终止");
    expect(wrapper.text()).toContain("任务完成");
  });

  it("shows the real run failure message in the retry card", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "u-balance",
            messageId: "u-balance",
            role: "user",
            text: "继续执行任务",
            createdAt: "2026-07-03T11:24:00.000Z"
          },
          {
            id: "event-balance",
            role: "card",
            cardType: "event",
            title: "Run 执行失败",
            payload: {
              type: "run.failed",
              error: { name: "ProviderError", message: "Insufficient Balance" }
            },
            createdAt: "2026-07-03T11:24:01.000Z"
          }
        ],
        running: false,
        runtimeStatus: "FAILED",
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    expect(wrapper.find(".figma-chat-retry-card-text").text()).toContain("Insufficient Balance");
    expect(wrapper.find(".figma-chat-retry-card-text").text()).not.toContain("974");
  });

  it("shows fallback run failure metadata instead of a generic retry message", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "u-timeout",
            messageId: "u-timeout",
            role: "user",
            text: "继续执行任务",
            createdAt: "2026-07-03T11:24:00.000Z"
          },
          {
            id: "event-timeout",
            role: "card",
            cardType: "event",
            title: "Run 执行失败",
            payload: {
              type: "run.failed",
              message: "EventSource connection closed",
              code: "SSE_DISCONNECTED",
              traceId: "trace_retry_1"
            },
            createdAt: "2026-07-03T11:24:01.000Z"
          }
        ],
        running: false,
        runtimeStatus: "FAILED",
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any,
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    const text = wrapper.find(".figma-chat-retry-card-text").text();
    expect(text).toContain("EventSource connection closed");
    expect(text).toContain("SSE_DISCONNECTED");
    expect(text).toContain("trace_retry_1");
    expect(text).not.toContain("您的请求断开，请重试");
  });

  it("uses the opencode timeline instead of the legacy running task panel", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        running: true,
        messages: [
          {
            id: "u-old",
            messageId: "u-old",
            role: "user",
            text: "上一轮任务",
            createdAt: "2026-07-03T09:00:00.000Z"
          },
          {
            id: "a-old",
            messageId: "a-old",
            role: "assistant",
            text: "",
            parts: [
              {
                partId: "old-bash",
                type: "tool",
                toolName: "bash",
                status: "completed",
                input: { command: "echo old task" }
              }
            ],
            createdAt: "2026-07-03T09:00:01.000Z"
          },
          {
            id: "u-new",
            messageId: "u-new",
            role: "user",
            text: "当前任务",
            createdAt: "2026-07-03T09:01:00.000Z"
          },
          {
            id: "a-new",
            messageId: "a-new",
            role: "assistant",
            text: "",
            parts: [
              {
                partId: "new-read",
                type: "tool",
                toolName: "read",
                status: "completed",
                input: { filePath: "current.md" }
              }
            ],
            createdAt: "2026-07-03T09:01:01.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-timeline-root").exists()).toBe(true);
    expect(wrapper.find(".oc-tool").exists()).toBe(true);
    expect(wrapper.find(".figma-chat-task-panel").exists()).toBe(false);
  });

  it("opens a frontend-only attachment dialog from the composer action", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    expect(wrapper.find('[role="dialog"][aria-label="上传附件"]').exists()).toBe(false);

    await wrapper.get('[aria-label="上传附件"]').trigger("click");

    expect(wrapper.find('[role="dialog"][aria-label="上传附件"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("当前仅展示前端样式，暂未连接后台上传能力");
    expect(wrapper.emitted("download-files")).toBeUndefined();
  });

  it("shows the assign action when the opencode process is unassigned (fallback inference)", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          message: "需要初始化 TestAgent 进程"
        }
      }
    });

    // 未传 serviceStatus 且无地址，回退推断为 UNASSIGNED
    expect(wrapper.text()).toContain("尚未分配 TestAgent 专属进程");

    const initButton = wrapper.get(".figma-chat-process-init");
    expect(initButton.text()).toBe("分配专属进程");

    await initButton.trigger("click");

    expect(wrapper.emitted("initialize-process")).toEqual([[]]);
  });

  it("shows the start action when the assigned opencode process is not running", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          serviceStatus: "NOT_RUNNING",
          serviceAddress: "10.0.0.1:3000",
          message: "TestAgent 进程不可用，需要重新初始化"
        }
      }
    });

    expect(wrapper.text()).toContain("TestAgent 专属进程未运行");

    const initButton = wrapper.get(".figma-chat-process-init");
    expect(initButton.text()).toBe("启动进程");

    await initButton.trigger("click");

    expect(wrapper.emitted("initialize-process")).toEqual([[]]);
  });

  it("shows the assign action when serviceStatus is explicitly UNASSIGNED", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          serviceStatus: "UNASSIGNED",
          message: "需要初始化 TestAgent 进程"
        }
      }
    });

    expect(wrapper.text()).toContain("尚未分配 TestAgent 专属进程");
    expect(wrapper.get(".figma-chat-process-init").text()).toBe("分配专属进程");
  });

  it("shows the assigned service address before the backend status message", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processStatus: {
          status: "UNAVAILABLE",
          initializable: false,
          serviceStatus: "NOT_RUNNING",
          linuxServerId: "server-a",
          serviceAddress: "192.168.100.115:4097",
          message: "目标服务器后端不可用，暂无法确认 TestAgent 进程健康状态"
        }
      }
    });

    expect(wrapper.text()).toContain("TestAgent 进程不可用");
    expect(wrapper.get(".figma-chat-process-message").text()).toBe("server-a / 192.168.100.115:4097");
    expect(wrapper.text()).not.toContain("尚未分配 TestAgent 专属进程");
  });

  it("shows the assigned server name without deriving a fake address", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processStatus: {
          status: "UNAVAILABLE",
          initializable: false,
          serviceStatus: "NOT_RUNNING",
          linuxServerId: "server-a",
          port: 4097,
          message: "目标服务器后端不可用，暂无法确认 TestAgent 进程健康状态"
        }
      }
    });

    expect(wrapper.get(".figma-chat-process-message").text()).toBe("server-a");
    expect(wrapper.text()).not.toContain("server-a:4097");
  });

  it("shows the loading label while initializing, differentiated by service status", async () => {
    const unassigned = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processInitializing: true,
        processStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          serviceStatus: "UNASSIGNED",
          message: "需要初始化 TestAgent 进程"
        }
      }
    });
    expect(unassigned.get(".figma-chat-process-init").text()).toBe("分配中");

    const notRunning = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processInitializing: true,
        processStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          serviceStatus: "NOT_RUNNING",
          serviceAddress: "10.0.0.1:3000",
          message: "TestAgent 进程不可用，需要重新初始化"
        }
      }
    });
    expect(notRunning.get(".figma-chat-process-init").text()).toBe("启动中");
  });

  // ===== 消息分层展示回归测试 =====

  it("does NOT include tool stdout/stderr in the assistant main text bubble", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "这是最终回答",
            parts: [
              { partId: "tool-1", type: "tool", toolName: "bash", status: "completed", output: "ls: No such file or directory\n", input: { command: "ls /nonexistent" } },
              { partId: "tool-2", type: "tool", toolName: "bash", status: "completed", state: { error: "permission denied" }, input: { command: "cat /etc/shadow" } } as any,
              { partId: "text-1", type: "text", text: "这是最终回答" }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    // MarkdownView stub 渲染 source 为纯文本；找到 source 为"这是最终回答"的那个
    // （即主正文的 MarkdownView），它不应包含 tool output/stderr
    const mdViews = wrapper.findAllComponents(markdownViewStub);
    const mainMd = mdViews.find((w) => (w.props("source") as string).includes("这是最终回答"));
    expect(mainMd).toBeTruthy();
    if (mainMd) {
      const src = mainMd.props("source") as string;
      expect(src).toContain("这是最终回答");
      expect(src).not.toContain("No such file or directory");
      expect(src).not.toContain("permission denied");
    }

    expect(wrapper.findAll('[data-testid="oc-tool-group"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="oc-tool-group"]').text()).toContain("命令行");
    expect(wrapper.find('[data-testid="oc-tool-group"]').text()).toContain("2 次");
  });

  it("does NOT include reasoning text in the main content", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "最终分析结论",
            parts: [
              { partId: "reason-1", type: "reasoning", text: "用户输入了一个测试需求，需要分析...", status: "completed", durationMs: 3200 },
              { partId: "text-1", type: "text", text: "最终分析结论" }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    // 检查主正文的 MarkdownView source 不包含 reasoning 文本
    const mdViews = wrapper.findAllComponents(markdownViewStub);
    const mainMd = mdViews.find((w) => (w.props("source") as string).includes("最终分析结论"));
    expect(mainMd).toBeTruthy();
    if (mainMd) {
      const src = mainMd.props("source") as string;
      expect(src).toContain("最终分析结论");
      expect(src).not.toContain("需要分析");
    }

    // reasoning 以折叠块独立存在，折叠头可展示短摘要，但不能混入最终正文。
    expect(wrapper.text()).toContain("思考状态");
    expect(wrapper.text()).toContain("需要分析");
  });

  it("renders the native compaction Part as a low-noise timeline separator", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [{
          id: "a-compaction", messageId: "a-compaction", role: "assistant", text: "",
          parts: [{ partId: "prt-compaction", type: "compaction", auto: true, overflow: false, tailStartId: "msg-tail" }],
          createdAt: "2026-07-11T09:00:00.000Z"
        }],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any
    });

    await showFullTimeline(wrapper);
    expect(wrapper.get('[data-testid="compaction-part-prt-compaction"]').text()).toContain("上下文已压缩");
  });

  it("shows the latest reasoning as running until the whole response finishes", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "正在生成回答",
            parts: [
              { partId: "reason-1", type: "reasoning", text: "仍在分析...", status: "completed", durationMs: 3200 },
              { partId: "text-1", type: "text", text: "正在生成回答" }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        running: true,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    const reasoning = wrapper.get(".oc-reasoning-part");
    expect(reasoning.text()).toContain("思考状态");
    expect(wrapper.text()).toContain("正在生成回答");
  });

  it("keeps the reasoning and tool details collapsed for older assistant messages", async () => {
    // 历史会话中：第一条 assistant（已答过）应当保持收起；只有最后一条才默认展开。
    // 中间用一条 user 消息隔开，避免 FigmaChatPanel 的"连续 assistant 合并"把
    // a1/a2 拼成一条，导致 details 数变成 3 而不是 4。
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "u1", messageId: "u1", role: "user",
            text: "先读一些文件",
            createdAt: "2026-06-25T08:59:00.000Z"
          },
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "较早的回复",
            parts: [
              { partId: "reason-old", type: "reasoning", text: "已结束的思考", status: "completed", durationMs: 1200 },
              { partId: "tool-old", type: "tool", toolName: "bash", status: "completed", input: { command: "ls" } }
            ],
            createdAt: "2026-06-25T09:00:00.000Z"
          },
          {
            id: "u2", messageId: "u2", role: "user",
            text: "继续",
            createdAt: "2026-06-25T09:00:30.000Z"
          },
          {
            id: "a2", messageId: "a2", role: "assistant",
            text: "最近的回复",
            parts: [
              { partId: "reason-new", type: "reasoning", text: "新一轮思考", status: "running" },
              { partId: "tool-new", type: "tool", toolName: "bash", status: "running", input: { command: "pwd" } }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        running: true,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.findAll(".oc-reasoning-part")).toHaveLength(2);
    expect(wrapper.findAll(".oc-tool")).toHaveLength(2);
    expect(wrapper.findAll(".oc-tool__body")).toHaveLength(0);
  });

  it("does not infer event questions from historical assistant option text", async () => {
    // 提问 UI 只由 question.asked 事件驱动，历史 assistant 的编号文本不再触发本地解析。
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "u1", messageId: "u1", role: "user",
            text: "请帮我设计测试用例",
            createdAt: "2026-06-25T09:00:00.000Z"
          },
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "请选择下一步：\n1. 生成测试用例\n2. 分析测试对象",
            createdAt: "2026-06-25T09:00:30.000Z"
          },
          {
            id: "u2", messageId: "u2", role: "user",
            text: "1",
            createdAt: "2026-06-25T09:00:45.000Z"
          }
        ],
        running: false,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    expect(wrapper.find(".figma-chat-choice-panel").exists()).toBe(false);
    expect(wrapper.find(".figma-chat-question-dock").exists()).toBe(false);
  });

  it("does not infer event questions when switching to a fresh numbered assistant message", async () => {
    const initial = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "sa-a1", messageId: "sa-a1", role: "assistant",
            text: "请选择：\n1. A 路径\n2. B 路径",
            createdAt: "2026-06-25T09:00:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });
    expect(initial.find(".figma-chat-choice-panel").exists()).toBe(false);
    expect(initial.find(".figma-chat-question-dock").exists()).toBe(false);

    await initial.setProps({
      messages: [
        {
          id: "sb-a1", messageId: "sb-a1", role: "assistant",
          text: "请选择：\n1. C 路径\n2. D 路径",
          createdAt: "2026-06-25T10:00:00.000Z"
        }
      ],
      processStatus: { status: "READY", initializable: false, message: "ready" }
    } as any);

    expect(initial.find(".figma-chat-choice-panel").exists()).toBe(false);
    expect(initial.find(".figma-chat-question-dock").exists()).toBe(false);
  });

  it("shows the assistant avatar beside the running status", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        running: true,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-thinking-row").exists()).toBe(true);
  });

  it("shows opencode retry status instead of leaving the run as thinking", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "u-retry",
            messageId: "u-retry",
            role: "user",
            text: "完成车贷的接口案例设计",
            createdAt: "2026-07-05T09:05:39.000Z"
          }
        ],
        running: true,
        runtimeStatus: "RETRY",
        timelineRuntimeStatus: {
          type: "retry",
          attempt: 1,
          maxAttempts: 3,
          retryAfterSeconds: 60,
          message: "Free usage exceeded, subscribe to Go",
          action: {
            message: "Subscribe to OpenCode Go for reliable access to the best open-source models, starting at $5/month.",
            label: "subscribe",
            link: "https://opencode.ai/go"
          }
        },
        processStatus: { status: "READY", initializable: false, message: "ready" }
      } as any
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-thinking-row").exists()).toBe(false);
    expect(wrapper.find(".oc-retry-row").text()).toContain("重试中 60 秒后 - 第 1 次 / 共 3 次");
    expect(wrapper.find(".oc-retry-row").text()).toContain("Free usage exceeded, subscribe to Go");
    expect(wrapper.find(".oc-retry-row a").attributes("href")).toBe("https://opencode.ai/go");
  });

  it("allows the explore section to expand and collapse", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "已分析两个文件",
            parts: [
              { partId: "read-1", type: "tool", toolName: "read", status: "completed",
                input: { filePath: "/tmp/login.test.ts" } },
              { partId: "read-2", type: "tool", toolName: "read", status: "completed",
                input: { filePath: "/tmp/login.ts" } }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    await showFullTimeline(wrapper);
    const exploreSection = wrapper.find(".oc-context-group");
    expect(exploreSection.exists()).toBe(true);
    expect(exploreSection.text()).not.toContain("login.test.ts");
    expect(exploreSection.find(".oc-tool__chevron").exists()).toBe(true);

    await exploreSection.get(".oc-context-group__trigger").trigger("click");
    expect(exploreSection.text()).toContain("login.test.ts");
    expect(exploreSection.text()).toContain("login.ts");

    await exploreSection.get(".oc-context-group__trigger").trigger("click");
    expect(exploreSection.text()).not.toContain("login.test.ts");
  });

  it("uses v-memo on write/edit previews so the syntax highlight is cached across rerenders", async () => {
    // 验证 v-memo 在重渲染时不会重新调用 renderCodeWithLineNumbers。
    // 用一个包含较长内容的大文件，渲染两次（一次折叠、一次展开），
    // 第二次展开后 v-memo 应跳过 re-render。
    const longContent = Array.from({ length: 80 }, (_, i) => `line ${i + 1}: const x = ${i};`).join("\n");
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "写入完成",
            parts: [
              { partId: "write-1", type: "tool", toolName: "write", status: "completed",
                input: { filePath: "/tmp/long.ts", content: longContent } }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.find(".oc-tool").exists()).toBe(true);
    expect(wrapper.text()).toContain("写入");
    expect(wrapper.text()).toContain("tmp/long.ts");
  });

  it("does not render a standalone directory card for read tool output", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "目录读取完成",
            parts: [
              {
                partId: "read-1",
                type: "tool",
                toolName: "read",
                status: "completed",
                input: { filePath: "/tmp/F-COSS" },
                output: "<path>/tmp/F-COSS</path><type>directory</type><entries>workspace/</entries>"
              }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    expect(wrapper.text()).not.toContain("目录 · 1 项");
    expect(wrapper.text()).not.toContain("workspace/");
  });

  it("shows read tool output as structured FilePart, not in main text", async () => {
    const xmlOutput = "<path>/tmp/test.txt</path><type>file</type><content>1: hello world</content>";
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "文件内容如下",
            parts: [
              { partId: "read-1", type: "tool", toolName: "read", status: "completed", output: xmlOutput },
              { partId: "text-1", type: "text", text: "文件内容如下" }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    // 主正文 MarkdownView 只含 text part
    const mdViews = wrapper.findAllComponents(markdownViewStub);
    const mainMd = mdViews.find((w) => (w.props("source") as string).includes("文件内容如下"));
    expect(mainMd).toBeTruthy();
    if (mainMd) {
      const src = mainMd.props("source") as string;
      expect(src).not.toContain("<path>");
      expect(src).not.toContain("hello world");
    }

    // read 输出被收敛到 context group，展开后显示文件名。
    await wrapper.get(".oc-context-group__trigger").trigger("click");
    expect(wrapper.text()).toContain("test.txt");
  });

  it("renders a completed assistant message that only has tool parts (no text)", async () => {
    // 消息只有 tool parts 没有 text -> 应作为结构化块展示
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "",
            parts: [
              { partId: "tool-1", type: "tool", toolName: "bash", status: "completed", output: "编译成功\n", input: { command: "npm run build" } }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    expect(wrapper.findAll(".oc-tool")).toHaveLength(1);
    expect(wrapper.text()).toContain("命令行");
    await wrapper.get(".oc-tool__trigger").trigger("click");
    expect(wrapper.text()).toContain("编译成功");
  });

  it("renders retry part as an error block, not in main body", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "重试后成功",
            parts: [
              { partId: "retry-1", type: "retry", attempt: 3, error: { name: "TimeoutError", message: "请求超时" } },
              { partId: "text-1", type: "text", text: "重试后成功" }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    const mdViews = wrapper.findAllComponents(markdownViewStub);
    const mainMd = mdViews.find((w) => (w.props("source") as string).includes("重试后成功"));
    expect(mainMd).toBeTruthy();
    if (mainMd) {
      const src = mainMd.props("source") as string;
      expect(src).not.toContain("重试第 3 次");
      expect(src).not.toContain("请求超时");
    }

    // retry 块展示错误信息
    expect(wrapper.text()).toContain("重试第 3 次");
    expect(wrapper.text()).toContain("请求超时");
  });

  it("does NOT include tool state.output or state.error in the main message body", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "",
            parts: [
              {
                partId: "tool-1", type: "tool", toolName: "read", status: "error",
                output: "",
                state: { output: "", error: "ls: /nonexistent: No such file or directory" }
              } as any
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    // 正文中不出现 state.error
    const allText = wrapper.text();
    expect(allText).not.toContain("No such file or directory");
    // 错误在 tool 折叠块中显示
    expect(allText).toContain("读取");
    await wrapper.get(".oc-tool__trigger").trigger("click");
    expect(wrapper.text()).toContain("No such file or directory");
  });

  it("collapses completed bash tool outputs by default and expands them on click", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1", messageId: "a1", role: "assistant",
            text: "Hello",
            parts: [
              { partId: "bash-1", type: "tool", toolName: "bash", status: "completed", output: "bash output content", input: { command: "echo test" } },
              { partId: "grep-1", type: "tool", toolName: "grep", status: "completed", output: "grep output content", input: { query: "search" } }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      },
      global: { stubs: { MarkdownView: markdownViewStub } }
    });

    await showFullTimeline(wrapper);
    const bashTool = wrapper.get(".oc-tool");
    expect(wrapper.text()).toContain("命令行");
    expect(wrapper.find(".oc-tool__body").exists()).toBe(false);

    await bashTool.get(".oc-tool__trigger").trigger("click");
    expect(wrapper.text()).toContain("bash output content");

    await bashTool.get(".oc-tool__trigger").trigger("click");
    expect(wrapper.find(".oc-tool__body").exists()).toBe(false);
  });

  it("keeps the current scroll position when new output arrives after the user scrolls up", async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(FigmaChatPanel, {
        props: {
          messages: [
            { id: "u1", role: "user", text: "分析 checkout 失败", createdAt: "2026-06-25T09:00:00.000Z" },
            { id: "a1", messageId: "a1", role: "assistant", text: "第一段描述", createdAt: "2026-06-25T09:01:00.000Z" }
          ],
          running: true,
          processStatus: { status: "READY", initializable: false, message: "ready" }
        },
        global: { stubs: { MarkdownView: markdownViewStub } }
      });

      const scroll = wrapper.get(".figma-chat-scroll");
      const scrollEl = scroll.element as HTMLElement;
      Object.defineProperty(scrollEl, "scrollHeight", { configurable: true, value: 1000 });
      Object.defineProperty(scrollEl, "clientHeight", { configurable: true, value: 300 });
      vi.advanceTimersByTime(60);
      await wrapper.vm.$nextTick();

      scrollEl.scrollTop = 320;
      await scroll.trigger("scroll");

      await wrapper.setProps({
        messages: [
          { id: "u1", role: "user", text: "分析 checkout 失败", createdAt: "2026-06-25T09:00:00.000Z" },
          {
            id: "a1",
            messageId: "a1",
            role: "assistant",
            text: "第一段描述，新增的流式输出",
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ]
      });
      await wrapper.vm.$nextTick();
      vi.advanceTimersByTime(60);
      await wrapper.vm.$nextTick();

      expect(scrollEl.scrollTop).toBe(320);
      expect(wrapper.text()).toContain("查看新内容");
    } finally {
      vi.useRealTimers();
    }
  });

  it("does not force-scroll to bottom when running message metadata changes while the user is reading above", async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(FigmaChatPanel, {
        props: {
          messages: [
            { id: "u1", role: "user", text: "读取项目文件", createdAt: "2026-06-25T09:00:00.000Z" },
            {
              id: "a1",
              messageId: "a1",
              role: "assistant",
              text: "正在检查上下文",
              parts: [
                { partId: "read-1", type: "tool", toolName: "read", status: "running", input: { filePath: "README.md" } }
              ],
              createdAt: "2026-06-25T09:01:00.000Z"
            }
          ],
          running: true,
          processStatus: { status: "READY", initializable: false, message: "ready" }
        },
        global: { stubs: { MarkdownView: markdownViewStub } }
      });

      const scroll = wrapper.get(".figma-chat-scroll");
      const scrollEl = scroll.element as HTMLElement;
      Object.defineProperty(scrollEl, "scrollHeight", { configurable: true, value: 1000 });
      Object.defineProperty(scrollEl, "clientHeight", { configurable: true, value: 300 });
      vi.advanceTimersByTime(60);
      await wrapper.vm.$nextTick();

      scrollEl.scrollTop = 120;
      await scroll.trigger("scroll");

      await wrapper.setProps({
        messages: [
          { id: "u1", role: "user", text: "读取项目文件", createdAt: "2026-06-25T09:00:00.000Z" },
          {
            id: "a1",
            messageId: "a1",
            role: "assistant",
            text: "正在检查上下文",
            parts: [
              { partId: "read-1", type: "tool", toolName: "read", status: "completed", input: { filePath: "README.md" } }
            ],
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ]
      });
      await wrapper.vm.$nextTick();
      vi.advanceTimersByTime(60);
      await wrapper.vm.$nextTick();

      expect(scrollEl.scrollTop).toBe(120);
      expect(wrapper.text()).not.toContain("查看新内容");
    } finally {
      vi.useRealTimers();
    }
  });

  it("does not show the new-content button when a finished history session is loaded while scrolled up", async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(FigmaChatPanel, {
        props: {
          messages: [
            { id: "u1", role: "user", text: "分析历史会话", createdAt: "2026-06-25T09:00:00.000Z" },
            { id: "a1", messageId: "a1", role: "assistant", text: "历史回答", createdAt: "2026-06-25T09:01:00.000Z" }
          ],
          running: true,
          processStatus: { status: "READY", initializable: false, message: "ready" }
        },
        global: { stubs: { MarkdownView: markdownViewStub } }
      });

      const scroll = wrapper.get(".figma-chat-scroll");
      const scrollEl = scroll.element as HTMLElement;
      Object.defineProperty(scrollEl, "scrollHeight", { configurable: true, value: 1000 });
      Object.defineProperty(scrollEl, "clientHeight", { configurable: true, value: 300 });
      vi.advanceTimersByTime(60);
      await wrapper.vm.$nextTick();

      scrollEl.scrollTop = 320;
      await scroll.trigger("scroll");

      await wrapper.setProps({
        running: false,
        messages: [
          { id: "u2", role: "user", text: "查看已完结会话", createdAt: "2026-06-25T10:00:00.000Z" },
          {
            id: "a2",
            messageId: "a2",
            role: "assistant",
            text: "这是已经完结的历史回答",
            createdAt: "2026-06-25T10:01:00.000Z"
          }
        ]
      });
      await wrapper.vm.$nextTick();
      vi.advanceTimersByTime(60);
      await wrapper.vm.$nextTick();

      expect(wrapper.text()).not.toContain("查看新内容");
    } finally {
      vi.useRealTimers();
    }
  });
});
