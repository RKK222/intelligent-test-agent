import { DOMWrapper, enableAutoUnmount, mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { afterEach, describe, expect, it } from "vitest";
import SessionContextUsage from "../src/components/SessionContextUsage.vue";

enableAutoUnmount(afterEach);

const detailSelector = '[role="dialog"][aria-label="会话上下文"]';

function getTeleportedDetail() {
  const element = document.body.querySelector(detailSelector);
  if (!element) throw new Error("未找到传送到 body 的会话上下文抽屉");
  return new DOMWrapper(element);
}

function teleportedDetailExists() {
  return document.body.querySelector(detailSelector) !== null;
}

const baseProps = {
  sessionId: "ses_context",
  sessionTitle: "上下文统计会话",
  messages: [
    { id: "u1", messageId: "u1", role: "user" as const, text: "分析测试失败", createdAt: "2026-07-23T08:00:00Z" },
    {
      id: "a1",
      messageId: "a1",
      role: "assistant" as const,
      text: "已经完成分析",
      tokens: { input: 90_000, output: 8_000, reasoning: 1_000, cacheRead: 500, cacheWrite: 500 },
      model: { id: "claude-sonnet", providerId: "anthropic" },
      createdAt: "2026-07-23T08:01:00Z"
    }
  ],
  selectedProvider: "anthropic",
  selectedModel: "anthropic/claude-sonnet",
  models: [{ id: "claude-sonnet", providerId: "anthropic", name: "Claude Sonnet", contextLimit: 200_000 }],
  providers: [{ providerId: "anthropic", name: "Anthropic" }]
};

function mountContextUsage(props: Record<string, unknown> = baseProps, conversationLeft = 900) {
  const host = document.createElement("div");
  host.className = "figma-chat-root";
  host.getBoundingClientRect = () => ({
    x: conversationLeft,
    y: 40,
    top: 40,
    left: conversationLeft,
    right: conversationLeft + 450,
    bottom: 760,
    width: 450,
    height: 720,
    toJSON: () => ({})
  });
  document.body.append(host);
  return mount(SessionContextUsage, { props: props as any, attachTo: host });
}

describe("SessionContextUsage", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("shows only usage, total context and used amount in the hover card", async () => {
    const wrapper = mountContextUsage();
    const trigger = wrapper.get('button[aria-label="查看会话上下文"]');

    await trigger.trigger("mouseenter");
    const tooltip = wrapper.get('[role="tooltip"]');
    expect(tooltip.text()).toContain("使用率");
    expect(tooltip.text()).toContain("总上下文");
    expect(tooltip.text()).toContain("已使用");
    expect(tooltip.text()).not.toContain("Token");
    expect(tooltip.text()).not.toContain("费用");
  });

  it("associates the tooltip with the trigger and dismisses it with Escape", async () => {
    const wrapper = mountContextUsage();
    const trigger = wrapper.get('button[aria-label="查看会话上下文"]');
    await trigger.trigger("focus");

    const tooltip = wrapper.get('[role="tooltip"]');
    expect(trigger.attributes("aria-describedby")).toBe(tooltip.attributes("id"));
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await nextTick();
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false);
    expect(trigger.attributes("aria-describedby")).toBeUndefined();
  });

  it("opens the detail with metrics and a five-category chart", async () => {
    const wrapper = mountContextUsage();
    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");

    const detail = getTeleportedDetail();
    expect(detail.text()).toContain("上下文统计会话");
    expect(detail.text()).toContain("Anthropic");
    expect(detail.text()).toContain("Claude Sonnet");
    expect(detail.text()).toContain("2 条");
    expect(detail.text()).toContain("200,000");
    expect(detail.text()).toContain("50%");
    expect(detail.text()).toContain("100,000");
    expect(detail.text()).toContain("输入 Token");
    expect(detail.text()).toContain("输出 Token");
    expect(detail.get('[data-testid="context-breakdown"]').element).toBeTruthy();
    expect(detail.findAll('[data-testid="context-breakdown-item"]')).toHaveLength(5);
  });

  it("opens the detail as a drawer outside the conversation", async () => {
    const wrapper = mountContextUsage();
    const trigger = wrapper.get<HTMLButtonElement>('button[aria-label="查看会话上下文"]');

    await trigger.trigger("click");
    const detail = getTeleportedDetail();
    expect(detail.attributes("data-side")).toBe("left");
    expect(detail.attributes("data-placement")).toBe("left-of-conversation");
    expect(detail.classes()).toContain("session-context-external-drawer");
    expect(trigger.attributes("aria-haspopup")).toBe("dialog");
    expect(trigger.attributes("aria-controls")).toBe(detail.attributes("id"));
  });

  it("closes when the ring is clicked again", async () => {
    const wrapper = mountContextUsage();
    const trigger = wrapper.get<HTMLButtonElement>('button[aria-label="查看会话上下文"]');

    await trigger.trigger("click");
    expect(trigger.attributes("aria-expanded")).toBe("true");

    await trigger.trigger("click");
    await nextTick();
    expect(teleportedDetailExists()).toBe(false);
    expect(trigger.attributes("aria-expanded")).toBe("false");
    expect(document.activeElement).toBe(trigger.element);
  });

  it("closes with Escape or the close button and returns focus to the ring", async () => {
    const wrapper = mountContextUsage();
    const trigger = wrapper.get<HTMLButtonElement>('button[aria-label="查看会话上下文"]');
    await trigger.trigger("click");
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await nextTick();
    expect(teleportedDetailExists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);

    await trigger.trigger("click");
    await getTeleportedDetail().get('button[aria-label="关闭上下文详情"]').trigger("click");
    expect(teleportedDetailExists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);
  });

  it("closes automatically when the root session changes", async () => {
    const wrapper = mountContextUsage();
    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");
    expect(teleportedDetailExists()).toBe(true);

    await wrapper.setProps({ sessionId: "ses_other" });
    expect(teleportedDetailExists()).toBe(false);
  });

  it("closes the teleported drawer when the conversation panel is hidden", async () => {
    const wrapper = mountContextUsage({ ...baseProps, panelVisible: true });
    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");
    expect(teleportedDetailExists()).toBe(true);

    await wrapper.setProps({ panelVisible: false });
    expect(teleportedDetailExists()).toBe(false);
  });

  it("renders an empty ring and chart state when limit or usage is unavailable", async () => {
    const wrapper = mountContextUsage({
      ...baseProps,
      messages: [],
      selectedProvider: "local",
      selectedModel: "local/unknown",
      models: [],
      providers: [{ providerId: "local", name: "Local" }]
    });

    expect(wrapper.get('[data-testid="context-ring"]').attributes("data-usage-percent")).toBe("");
    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");
    const detail = getTeleportedDetail();
    expect(detail.get('[data-testid="context-limit"]').text()).toBe("—");
    expect(detail.get('[data-testid="context-breakdown-empty"]').text()).toContain("暂无可用的上下文拆分");
  });

  it("does not render a bordered drawer when no external width is available", async () => {
    const wrapper = mountContextUsage(baseProps, 0);
    const trigger = wrapper.get('button[aria-label="查看会话上下文"]');

    await trigger.trigger("click");

    expect(teleportedDetailExists()).toBe(false);
    expect(trigger.attributes("aria-expanded")).toBe("false");
  });
});
