import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FigmaChatPanel from "../src/components/FigmaChatPanel.vue";

describe("FigmaChatPanel", () => {
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
      }
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
      }
    });

    expect(wrapper.findAll(".figma-chat-assistant")).toHaveLength(1);
    expect(wrapper.text()).toContain("在的，有什么可以帮你的？");
    expect(wrapper.text().match(/测试智能体/g)).toHaveLength(1);
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
    expect(wrapper.text()).toContain("19915 tokens");
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
});
