import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { afterEach, describe, expect, it } from "vitest";
import SessionContextUsage from "../src/components/SessionContextUsage.vue";

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

describe("SessionContextUsage", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("shows only usage, total context and used amount in the hover card", async () => {
    const wrapper = mount(SessionContextUsage, { props: baseProps, attachTo: document.body });
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
    const wrapper = mount(SessionContextUsage, { props: baseProps });
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
    const wrapper = mount(SessionContextUsage, { props: baseProps, attachTo: document.body });
    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");

    const detail = wrapper.get('[role="dialog"][aria-label="会话上下文"]');
    expect(detail.text()).toContain("上下文统计会话");
    expect(detail.text()).toContain("Anthropic");
    expect(detail.text()).toContain("Claude Sonnet");
    expect(detail.text()).toContain("2 条");
    expect(detail.text()).toContain("200,000");
    expect(detail.text()).toContain("50%");
    expect(detail.text()).toContain("100,000");
    expect(detail.text()).toContain("输入 Token");
    expect(detail.text()).toContain("输出 Token");
    expect(wrapper.get('[data-testid="context-breakdown"]').element).toBeTruthy();
    expect(wrapper.findAll('[data-testid="context-breakdown-item"]')).toHaveLength(5);
  });

  it("closes with Escape or the close button and returns focus to the ring", async () => {
    const wrapper = mount(SessionContextUsage, { props: baseProps, attachTo: document.body });
    const trigger = wrapper.get<HTMLButtonElement>('button[aria-label="查看会话上下文"]');
    await trigger.trigger("click");
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await nextTick();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);

    await trigger.trigger("click");
    await wrapper.get('button[aria-label="关闭上下文详情"]').trigger("click");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);
  });

  it("closes automatically when the root session changes", async () => {
    const wrapper = mount(SessionContextUsage, { props: baseProps });
    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);

    await wrapper.setProps({ sessionId: "ses_other" });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });

  it("renders an empty ring and chart state when limit or usage is unavailable", async () => {
    const wrapper = mount(SessionContextUsage, {
      props: {
        ...baseProps,
        messages: [],
        selectedProvider: "local",
        selectedModel: "local/unknown",
        models: [],
        providers: [{ providerId: "local", name: "Local" }]
      }
    });

    expect(wrapper.get('[data-testid="context-ring"]').attributes("data-usage-percent")).toBe("");
    await wrapper.get('button[aria-label="查看会话上下文"]').trigger("click");
    expect(wrapper.get('[data-testid="context-limit"]').text()).toBe("—");
    expect(wrapper.get('[data-testid="context-breakdown-empty"]').text()).toContain("暂无可用的上下文拆分");
  });
});
