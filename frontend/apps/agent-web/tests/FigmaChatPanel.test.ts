import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FigmaChatPanel from "../src/components/FigmaChatPanel.vue";

// MarkdownView 内部用 150ms 定时器 + 动态 import 异步渲染正文，
// 单测同步断言时会停在“渲染中…”占位。这里桩成同步直出 source，
// 让历史消息正文断言稳定且与渲染时序解耦。
const markdownViewStub = {
  props: ["source"],
  template: '<div class="ta-md-view">{{ source }}</div>',
};

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

    expect(wrapper.text()).not.toContain("正在检查 opencode 进程");
    expect(wrapper.get('[aria-label="发送"]').attributes("disabled")).toBeUndefined();

    await wrapper.get('[aria-label="发送"]').trigger("click");

    expect(wrapper.emitted("send")).toEqual([["后台刷新时发送"]]);
  });

  it("requests a process refresh when the user focuses the composer textarea", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    await wrapper.get("textarea").trigger("focus");

    expect(wrapper.emitted("refresh-process")).toEqual([[]]);
  });

  it("requests a process refresh when the user clicks the composer card", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    await wrapper.get(".figma-chat-input-card").trigger("click");

    expect(wrapper.emitted("refresh-process")).toEqual([[]]);
  });

  it("deduplicates focus and click refresh requests from the same interaction", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    await wrapper.get("textarea").trigger("focus");
    await wrapper.get(".figma-chat-input-card").trigger("click");

    expect(wrapper.emitted("refresh-process")).toEqual([[]]);
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

  it("shows the checking state before the first process status response arrives", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processRequired: true,
        processLoading: true,
        processStatus: null
      }
    });

    expect(wrapper.text()).toContain("正在检查 opencode 进程");
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
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          { id: "msg_assistant123", messageId: "msg_assistant123", role: "assistant", text: "已完成分析", createdAt: "2026-06-25T09:01:00.000Z" }
        ],
        messageFeedbacks: {
          msg_assistant123: {
            feedbackId: "fb_123",
            messageId: "msg_assistant123",
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
      messageId: "msg_assistant123",
      rating: "POSITIVE"
    }]]);
  });

  it("renders historical generated files and opens the file changes drawer", async () => {
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

    expect(wrapper.getComponent(markdownViewStub).props("source")).toBe("第一段\n第二段");
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
    expect(wrapper.text()).toContain("2.0w tokens");
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
          message: "需要初始化 opencode 进程"
        }
      }
    });

    // 未传 serviceStatus 且无地址，回退推断为 UNASSIGNED
    expect(wrapper.text()).toContain("尚未分配 opencode 专属进程");

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
          message: "opencode 进程不可用，需要重新初始化"
        }
      }
    });

    expect(wrapper.text()).toContain("opencode 专属进程未运行");

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
          message: "需要初始化 opencode 进程"
        }
      }
    });

    expect(wrapper.text()).toContain("尚未分配 opencode 专属进程");
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
          serviceAddress: "192.168.100.115:4097",
          message: "目标服务器后端不可用，暂无法确认 opencode 进程健康状态"
        }
      }
    });

    expect(wrapper.text()).toContain("opencode 进程不可用");
    expect(wrapper.get(".figma-chat-process-message").text()).toBe("192.168.100.115:4097");
    expect(wrapper.text()).not.toContain("尚未分配 opencode 专属进程");
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
          message: "需要初始化 opencode 进程"
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
          message: "opencode 进程不可用，需要重新初始化"
        }
      }
    });
    expect(notRunning.get(".figma-chat-process-init").text()).toBe("启动中");
  });
});
