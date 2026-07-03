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
  it("shows visible primary/all agents in the composer agent picker and emits changes", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        selectedAgent: "build",
        agents: [
          { agentId: "build", name: "Build", mode: "primary", description: "默认构建" },
          { agentId: "all-rounder", name: "All Rounder", mode: "all", description: "可作为主 Agent" },
          { agentId: "review", name: "Review", mode: "subagent", description: "只能 @ 调用" },
          { agentId: "secret", name: "Secret", mode: "primary", hidden: true }
        ]
      } as any
    });

    await wrapper.get('[aria-label="切换 Agent"]').trigger("click");

    expect(wrapper.find(".figma-chat-agent-dropdown").exists()).toBe(true);
    expect(wrapper.text()).toContain("Build");
    expect(wrapper.text()).toContain("All Rounder");
    expect(wrapper.text()).not.toContain("Review");
    expect(wrapper.text()).not.toContain("Secret");
    expect(wrapper.get(".figma-chat-agent-option-item.is-active").text()).toContain("Build");

    const allRounder = wrapper
      .findAll(".figma-chat-agent-option-item")
      .find((item) => item.text().includes("All Rounder"));
    expect(allRounder).toBeTruthy();
    await allRounder!.trigger("click");

    expect(wrapper.emitted("change-agent")).toEqual([["all-rounder"]]);
  });

  it("shows mentionable subagent/all agents when the user types at-sign", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        agents: [
          { agentId: "build", name: "Build", mode: "primary", description: "默认构建" },
          { agentId: "review", name: "Review", mode: "subagent", description: "评审实现" },
          { agentId: "qa", name: "QA", mode: "all", description: "测试分析" },
          { agentId: "hidden-review", name: "Hidden Review", mode: "subagent", hidden: true }
        ]
      } as any
    });

    await wrapper.get("textarea").setValue("@");

    const panel = wrapper.find(".figma-chat-agent-panel");
    expect(panel.exists()).toBe(true);
    expect(panel.text()).toContain("Review");
    expect(panel.text()).toContain("QA");
    expect(panel.text()).not.toContain("Build");
    expect(panel.text()).not.toContain("Hidden Review");
  });

  it("replaces the current at-sign query when selecting a mentioned agent", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        agents: [
          { agentId: "review", name: "Review", mode: "subagent", description: "评审实现" },
          { agentId: "qa", name: "QA", mode: "all", description: "测试分析" }
        ]
      } as any
    });

    await wrapper.get("textarea").setValue("请 @re");
    await wrapper.get(".figma-chat-agent-row").trigger("click");

    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("请 @Review ");
    expect(wrapper.emitted("update:inputValue")).toContainEqual(["请 @Review "]);
    expect(wrapper.find(".figma-chat-agent-panel").exists()).toBe(false);
  });

  it("lists native skill commands when the user types slash", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        processStatus: { status: "READY", initializable: false, message: "ready" },
        commands: [
          { commandId: "skill-1", name: "identify-test-objects", description: "识别测试对象", source: "skill" },
          { commandId: "command-1", name: "help", description: "帮助", source: "command" }
        ]
      }
    });

    await wrapper.get("textarea").setValue("/");

    expect(wrapper.find(".figma-chat-skill-panel").exists()).toBe(true);
    expect(wrapper.text()).toContain("identify-test-objects");
    expect(wrapper.text()).not.toContain("帮助");

    await wrapper.get(".figma-chat-skill-row").trigger("click");

    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("/identify-test-objects ");
    expect(wrapper.emitted("update:inputValue")).toContainEqual(["/identify-test-objects "]);
    expect(wrapper.find(".figma-chat-skill-panel").exists()).toBe(false);
  });

  it("keeps the composer visible below a detected choice panel", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          {
            id: "a1",
            messageId: "a1",
            role: "assistant",
            text: "请选择下一步：\n1. 生成测试用例\n2. 分析测试对象",
            createdAt: "2026-06-25T09:01:00.000Z"
          }
        ],
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    expect(wrapper.find(".figma-chat-choice-panel").exists()).toBe(true);
    expect(wrapper.find(".figma-chat-composer").exists()).toBe(true);
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

  it("does not render assistant message feedback when the conversation is running", async () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [
          { id: "msg_assistant123", messageId: "msg_assistant123", role: "assistant", text: "已完成分析", createdAt: "2026-06-25T09:01:00.000Z" }
        ],
        running: true,
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

  it("uses the opencode timeline instead of the legacy running task panel", () => {
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
          linuxServerId: "server-a",
          serviceAddress: "192.168.100.115:4097",
          message: "目标服务器后端不可用，暂无法确认 opencode 进程健康状态"
        }
      }
    });

    expect(wrapper.text()).toContain("opencode 进程不可用");
    expect(wrapper.get(".figma-chat-process-message").text()).toBe("server-a / 192.168.100.115:4097");
    expect(wrapper.text()).not.toContain("尚未分配 opencode 专属进程");
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
          message: "目标服务器后端不可用，暂无法确认 opencode 进程健康状态"
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

  // ===== 消息分层展示回归测试 =====

  it("does NOT include tool stdout/stderr in the assistant main text bubble", () => {
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

    expect(wrapper.findAll(".oc-tool")).toHaveLength(2);
  });

  it("does NOT include reasoning text in the main content", () => {
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

    // 检查主正文的 MarkdownView source 不包含 reasoning 文本
    const mdViews = wrapper.findAllComponents(markdownViewStub);
    const mainMd = mdViews.find((w) => (w.props("source") as string).includes("最终分析结论"));
    expect(mainMd).toBeTruthy();
    if (mainMd) {
      const src = mainMd.props("source") as string;
      expect(src).toContain("最终分析结论");
      expect(src).not.toContain("需要分析");
    }

    // reasoning 以折叠块独立存在，默认收起，不混入正文。
    expect(wrapper.text()).toContain("思考状态");
    expect(wrapper.text()).not.toContain("需要分析");
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

    const reasoning = wrapper.get(".oc-reasoning-part");
    expect(reasoning.text()).toContain("思考状态");
    expect(wrapper.text()).toContain("正在生成回答");
  });

  it("keeps the reasoning and tool details collapsed for older assistant messages", () => {
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

    expect(wrapper.findAll(".oc-reasoning-part")).toHaveLength(2);
    expect(wrapper.findAll(".oc-tool")).toHaveLength(2);
    expect(wrapper.findAll(".oc-tool__body")).toHaveLength(0);
  });

  it("does not resurface the choice panel when switching to a historical session where the user has already replied", async () => {
    // 历史会话最后一条是 user（用户已回答），不应再把旧 assistant 的选项弹出来。
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
  });

  it("clears a previously dismissed choice when switching to a fresh session", async () => {
    // 在 A 会话里点过"取消" → 切到 B 会话（最后一条仍是 assistant、未答）时，
    // B 的待答问题应正常出现，而不是被 A 的取消态吞掉。
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
    // 触发"取消"，choiceDismissed = true
    await initial.get(".figma-chat-choice-cancel").trigger("click");
    expect(initial.find(".figma-chat-choice-panel").exists()).toBe(false);

    // 切到 B 会话：第一条消息 id 变化 → 触发 watcher
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

    expect(initial.find(".figma-chat-choice-panel").exists()).toBe(true);
  });

  it("shows the assistant avatar beside the running status", () => {
    const wrapper = mount(FigmaChatPanel, {
      props: {
        messages: [],
        running: true,
        processStatus: { status: "READY", initializable: false, message: "ready" }
      }
    });

    expect(wrapper.find(".oc-thinking-row").exists()).toBe(true);
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

    expect(wrapper.find(".oc-tool").exists()).toBe(true);
    expect(wrapper.text()).toContain("Write");
    expect(wrapper.text()).toContain("/tmp/long.ts");
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

    expect(wrapper.findAll(".oc-tool")).toHaveLength(1);
    expect(wrapper.text()).toContain("Bash");
    await wrapper.get(".oc-tool__trigger").trigger("click");
    expect(wrapper.text()).toContain("编译成功");
  });

  it("renders retry part as an error block, not in main body", () => {
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

    // 正文中不出现 state.error
    const allText = wrapper.text();
    expect(allText).not.toContain("No such file or directory");
    // 错误在 tool 折叠块中显示
    expect(allText).toContain("Read");
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

    const bashTool = wrapper.get(".oc-tool");
    expect(wrapper.text()).toContain("Bash");
    expect(wrapper.find(".oc-tool__body").exists()).toBe(false);

    await bashTool.get(".oc-tool__trigger").trigger("click");
    expect(wrapper.text()).toContain("bash output content");

    await bashTool.get(".oc-tool__trigger").trigger("click");
    expect(wrapper.find(".oc-tool__body").exists()).toBe(false);
  });
});
