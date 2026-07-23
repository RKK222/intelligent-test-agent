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

function propsForUsage(totalTokens: number) {
  return {
    ...baseProps,
    messages: [{
      ...baseProps.messages[1],
      tokens: { input: totalTokens, output: 0 }
    }]
  };
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

  it("keeps remote-root metrics visible and counts the assistant only after text arrives", async () => {
    const platformSessionId = "ses_b278fa360be241b39e5382cd33b5f1ae";
    const remoteRootSessionId = "ses_0719c797fffeuNz55LEr5sU5bH";
    const assistantWithoutText = {
      id: "assistant_remote",
      messageId: "assistant_remote",
      role: "assistant" as const,
      text: "",
      tokens: { input: 9_518, output: 282, reasoning: 729, cacheRead: 43_008, cacheWrite: 0 },
      model: { id: "deepseek-chat", providerId: "deepseek" },
      parts: [{ partId: "part_reasoning", type: "reasoning" as const, text: "正在分析" }],
      createdAt: "2026-07-23T08:01:00Z"
    };
    const props = {
      ...baseProps,
      sessionId: platformSessionId,
      messages: [
        { id: "user_remote", messageId: "user_remote", role: "user" as const, text: "分析上下文", createdAt: "2026-07-23T08:00:00Z" },
        assistantWithoutText
      ],
      messageScopesById: {
        user_remote: { sessionId: remoteRootSessionId, rootSessionId: remoteRootSessionId, isChildSession: false },
        assistant_remote: { sessionId: remoteRootSessionId, rootSessionId: remoteRootSessionId, isChildSession: false }
      },
      selectedProvider: "deepseek",
      selectedModel: "deepseek/deepseek-chat",
      models: [{ id: "deepseek-chat", providerId: "deepseek", name: "DeepSeek Chat", contextLimit: 1_000_000 }],
      providers: [{ providerId: "deepseek", name: "DeepSeek" }]
    };
    const wrapper = mountContextUsage(props);

    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");
    const detail = getTeleportedDetail();
    expect(detail.text()).toContain("1 条");
    expect(detail.text()).toContain("53,537");
    expect(detail.text()).toContain("9,518");
    expect(detail.text()).toContain("282");
    expect(detail.text()).toContain("5%");

    await wrapper.setProps({
      messages: [{ ...props.messages[0] }, {
        ...assistantWithoutText,
        parts: [...assistantWithoutText.parts, { partId: "part_text", type: "text" as const, text: "统计完成" }]
      }]
    });

    expect(getTeleportedDetail().text()).toContain("2 条");
  });

  it("refreshes the open breakdown when an invisible assistant is added or reclassified", async () => {
    const remoteRootSessionId = "ses_remote_root";
    const visibleAssistant = {
      id: "assistant_visible",
      messageId: "assistant_visible",
      role: "assistant" as const,
      text: "已完成",
      tokens: { input: 100, output: 1 },
      model: { id: "claude-sonnet", providerId: "anthropic" },
      createdAt: "2026-07-23T08:01:00Z"
    };
    const toolReasoningAssistant = {
      id: "assistant_tool_reasoning",
      messageId: "assistant_tool_reasoning",
      role: "assistant" as const,
      text: "",
      parts: [
        { partId: "part_reasoning", type: "reasoning" as const, text: "正在分析" },
        {
          partId: "part_tool",
          type: "tool" as const,
          toolName: "read",
          status: "completed" as const,
          input: { path: "example.ts" },
          output: "O".repeat(80)
        }
      ],
      createdAt: "2026-07-23T08:02:00Z"
    };
    const props = {
      ...baseProps,
      messages: [
        { id: "user_root", messageId: "user_root", role: "user" as const, text: "分析上下文", createdAt: "2026-07-23T08:00:00Z" },
        visibleAssistant
      ],
      messageScopesById: {
        user_root: { sessionId: remoteRootSessionId, rootSessionId: remoteRootSessionId, isChildSession: false },
        assistant_visible: { sessionId: remoteRootSessionId, rootSessionId: remoteRootSessionId, isChildSession: false }
      }
    };
    const wrapper = mountContextUsage(props);
    const toolBreakdown = () => {
      const item = getTeleportedDetail().findAll('[data-testid="context-breakdown-item"]')
        .find((candidate) => candidate.text().includes("工具"));
      if (!item) throw new Error("未找到工具上下文拆分");
      return item.text();
    };

    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");
    expect(getTeleportedDetail().text()).toContain("2 条");
    expect(toolBreakdown()).toContain("0");

    await wrapper.setProps({
      messages: [...props.messages, toolReasoningAssistant],
      messageScopesById: {
        ...props.messageScopesById,
        assistant_tool_reasoning: {
          sessionId: remoteRootSessionId,
          rootSessionId: remoteRootSessionId,
          isChildSession: false
        }
      }
    });
    expect(getTeleportedDetail().text()).toContain("2 条");
    expect(toolBreakdown()).toContain("24");

    await wrapper.setProps({
      messageScopesById: {
        ...props.messageScopesById,
        assistant_tool_reasoning: {
          sessionId: "ses_child",
          rootSessionId: remoteRootSessionId,
          isChildSession: true
        }
      }
    });
    expect(getTeleportedDetail().text()).toContain("2 条");
    expect(toolBreakdown()).toContain("0");
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

    const trigger = wrapper.get('button[aria-label="查看会话上下文"]');
    const ring = wrapper.get('[data-testid="context-ring"]');
    expect(trigger.classes()).toContain("session-context-trigger--unknown");
    expect(trigger.attributes("data-usage-level")).toBe("unknown");
    expect(ring.attributes("data-usage-percent")).toBe("");
    expect(ring.find(".session-context-ring-value").exists()).toBe(false);
    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");
    const detail = getTeleportedDetail();
    expect(detail.get('[data-testid="context-limit"]').text()).toBe("—");
    expect(detail.get('[data-testid="context-breakdown-empty"]').text()).toContain("暂无可用的上下文拆分");
  });

  it.each([
    ["normal", 100_000, "rgb(164, 13, 188)"],
    ["warning", 120_000, "rgb(217, 119, 6)"],
    ["danger", 160_000, "rgb(220, 38, 38)"]
  ] as const)("applies the %s level class, data attribute and trigger color", (level, totalTokens, expectedColor) => {
    const wrapper = mountContextUsage(propsForUsage(totalTokens));
    const trigger = wrapper.get('button[aria-label="查看会话上下文"]');

    expect(trigger.classes()).toContain(`session-context-trigger--${level}`);
    expect(trigger.attributes("data-usage-level")).toBe(level);
    expect(getComputedStyle(trigger.element).color).toBe(expectedColor);
  });

  it("does not render a bordered drawer when no external width is available", async () => {
    const wrapper = mountContextUsage(baseProps, 0);
    const trigger = wrapper.get('button[aria-label="查看会话上下文"]');

    await trigger.trigger("click");

    expect(teleportedDetailExists()).toBe(false);
    expect(trigger.attributes("aria-expanded")).toBe("false");
  });
});
