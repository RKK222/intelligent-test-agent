import { fireEvent, render, screen } from "@testing-library/react";
import type { AgentMessage } from "@test-agent/shared-types";
import type * as React from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { AgentChat } from "../src/AgentChat";

describe("AgentChat layout", () => {
  beforeAll(() => {
    class ResizeObserverStub {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
    vi.stubGlobal("ResizeObserver", ResizeObserverStub);
    HTMLElement.prototype.scrollTo = vi.fn();
  });

  it("为 Agent 对话线程保留独立可滚动区域", () => {
    render(
      <AgentChat
        messages={[]}
        history={[]}
        onSend={vi.fn()}
        onOpenDiff={vi.fn()}
        onRetry={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    const threadShell = screen.getByLabelText("Agent 对话线程");
    expect(threadShell.className).toContain("min-h-0");
    expect(threadShell.className).toContain("flex-1");
    expect(threadShell.className).toContain("overflow-hidden");
  });

  it("用右侧浅灰气泡展示用户消息", () => {
    renderAgentChat([
      {
        id: "u1",
        role: "user",
        text: "请分析 checkout 模块最近失败的 3 个用例",
        createdAt: "2026-06-19T00:00:00.000Z"
      }
    ]);

    expect(screen.getByText("用户")).toBeTruthy();
    const bubble = screen.getByText("请分析 checkout 模块最近失败的 3 个用例").closest("div");
    expect(bubble?.className).toContain("bg-[var(--ta-chat-user-bg)]");
    expect(bubble?.className).toContain("text-[var(--ta-chat-text)]");
  });

  it("将思考过程折叠展示，并把最终回答作为视觉重点", () => {
    renderAgentChat([
      {
        id: "a1",
        role: "assistant",
        text: "最终建议：增加等待条件。",
        createdAt: "2026-06-19T00:00:00.000Z",
        parts: [
          {
            partId: "reasoning_1",
            type: "reasoning",
            text: "先理解失败用例，再检查定位器。",
            status: "completed",
            title: "正在整理信息"
          },
          {
            partId: "answer_1",
            type: "text",
            text: "最终建议：增加等待条件。",
            status: "completed"
          }
        ]
      }
    ]);

    const answer = screen.getByText("最终建议：增加等待条件。").closest("[data-testid='answer-part']");
    expect(answer?.className).toContain("bg-[var(--ta-chat-answer-bg)]");
    expect(screen.getByText("思考状态")).toBeTruthy();
    expect(screen.getByTestId("reasoning-part-reasoning_1").hasAttribute("open")).toBe(false);

    fireEvent.click(screen.getByRole("button", { name: "展开思考状态" }));
    expect(screen.getByText("先理解失败用例，再检查定位器。")).toBeTruthy();
  });

  it("在线程内展示任务分解，并默认展开运行中任务", () => {
    renderAgentChat([], {
      todos: [
        {
          id: "task_1",
          text: "理解需求",
          status: "completed",
          summary: "已确认展示层级"
        },
        {
          id: "task_2",
          text: "整理信息结构",
          status: "in_progress",
          description: "归并 RunEvent 和 message part",
          steps: ["读取事件", "归并展示模型"]
        }
      ] as any
    });

    expect(screen.getByText("任务分解")).toBeTruthy();
    expect(screen.getByText("理解需求")).toBeTruthy();
    expect(screen.getByText("整理信息结构")).toBeTruthy();
    const runningTask = screen.getByTestId("task-item-task_2");
    expect(runningTask.hasAttribute("open")).toBe(true);
    expect(screen.getAllByText("归并 RunEvent 和 message part").length).toBeGreaterThanOrEqual(1);
  });

  it("将 skill 工具调用渲染为专门的 Skill 调用块", () => {
    renderAgentChat([
      {
        id: "a1",
        role: "assistant",
        text: "",
        createdAt: "2026-06-19T00:00:00.000Z",
        parts: [
          {
            partId: "tool_skill",
            type: "tool",
            toolName: "skill",
            status: "completed",
            input: { name: "frontend-design" },
            output: "Loaded skill: frontend-design",
            metadata: { purpose: "用于前端视觉方向" }
          }
        ]
      }
    ]);

    expect(screen.getByText("Skill 调用")).toBeTruthy();
    expect(screen.getByText("frontend-design")).toBeTruthy();
    expect(screen.getByText("用于前端视觉方向")).toBeTruthy();
  });

  it("用统一折叠卡展示规划、工具、Diff 和事件卡片", () => {
    renderAgentChat([
      card("plan-1", "plan", "执行计划", {
        steps: [
          { title: "读取运行报告", status: "done" },
          { title: "调用技能 analyze_playwright", status: "pending" }
        ]
      }),
      card("tool-1", "tool", "工具调用完成", {
        toolName: "read_file",
        path: "reports/run-1287.json",
        summary: "共 12 个用例，3 个失败",
        status: "completed",
        output: "失败原因均为选择器超时"
      }),
      card("diff-old", "diff", "旧 Diff", {
        files: [{ path: "old.ts", status: "modified", additions: 1, deletions: 0 }]
      }),
      card("diff-new", "diff", "Agent 提出了文件修改", {
        files: [
          { path: "tests/checkout.spec.ts", status: "modified", additions: 2, deletions: 1 },
          { path: "src/checkout.ts", status: "modified", additions: 2, deletions: 1 }
        ]
      }),
      card("event-1", "event", "修复建议", { summary: "建议增加等待条件并更新定位器" })
    ]);

    expect(screen.getByText("规划步骤")).toBeTruthy();
    expect(screen.getByText("工具调用完成")).toBeTruthy();
    expect(screen.getByText("本次变更涉及 2 个文件")).toBeTruthy();
    expect(screen.getByText("修复建议")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "展开 规划步骤" }));
    fireEvent.click(screen.getByRole("button", { name: "展开 修复建议" }));

    expect(screen.getByText("读取运行报告")).toBeTruthy();
    expect(screen.getByText("调用技能 analyze_playwright")).toBeTruthy();
    expect(screen.getByText("read_file")).toBeTruthy();
    expect(screen.getByText("路径: reports/run-1287.json")).toBeTruthy();
    expect(screen.getByText("tests/checkout.spec.ts")).toBeTruthy();
    expect(screen.getAllByText("MODIFIED")).toHaveLength(2);
    expect(screen.getAllByText("+2 -1")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "查看 Diff" })).toBeTruthy();
    expect(screen.getByText("建议增加等待条件并更新定位器")).toBeTruthy();
  });

  it("默认只展开最新关键卡片，并允许手动展开折叠", () => {
    renderAgentChat([
      card("diff-old", "diff", "旧 Diff", {
        files: [{ path: "old.ts", status: "modified", additions: 1, deletions: 0 }]
      }),
      card("diff-new", "diff", "新 Diff", {
        files: [{ path: "new.ts", status: "modified", additions: 2, deletions: 1 }]
      }),
      card("tool-new", "tool", "工具调用完成", {
        toolName: "read_file",
        status: "completed",
        summary: "读取运行报告"
      })
    ]);

    const oldDiff = screen.getByTestId("timeline-card-diff-old");
    const latestDiff = screen.getByTestId("timeline-card-diff-new");
    const latestTool = screen.getByTestId("timeline-card-tool-new");
    expect(oldDiff.hasAttribute("open")).toBe(false);
    expect(latestDiff.hasAttribute("open")).toBe(true);
    expect(latestTool.hasAttribute("open")).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: "展开 本次变更涉及 1 个文件" }));
    expect(oldDiff.hasAttribute("open")).toBe(true);
  });

  it("用户离开底部时保留阅读位置，并提示有新内容", () => {
    const { rerender } = renderAgentChat([
      {
        id: "a1",
        role: "assistant",
        text: "第一段回答",
        createdAt: "2026-06-19T00:00:00.000Z"
      }
    ]);
    const viewport = screen.getByTestId("agent-thread-viewport");
    Object.defineProperty(viewport, "clientHeight", { configurable: true, value: 100 });
    Object.defineProperty(viewport, "scrollHeight", { configurable: true, value: 320 });
    Object.defineProperty(viewport, "scrollTop", { configurable: true, writable: true, value: 20 });

    fireEvent.scroll(viewport);
    rerender(
      <AgentChat
        messages={[
          {
            id: "a1",
            role: "assistant",
            text: "第一段回答",
            createdAt: "2026-06-19T00:00:00.000Z"
          },
          {
            id: "a2",
            role: "assistant",
            text: "第二段回答",
            createdAt: "2026-06-19T00:00:01.000Z"
          }
        ]}
        history={[]}
        onSend={vi.fn()}
        onOpenDiff={vi.fn()}
        onRetry={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    expect(screen.getByRole("button", { name: "查看新内容" })).toBeTruthy();
  });
});

function renderAgentChat(messages: AgentMessage[], props: Partial<React.ComponentProps<typeof AgentChat>> = {}) {
  const view = (
    <AgentChat
      messages={messages}
      history={[]}
      onSend={vi.fn()}
      onOpenDiff={vi.fn()}
      onRetry={vi.fn()}
      onCancel={vi.fn()}
      {...props}
    />
  );
  const result = render(view);
  return {
    ...result,
    rerender: (next: React.ReactElement) => result.rerender(next)
  };
}

function card(
  id: string,
  cardType: Extract<AgentMessage, { role: "card" }>["cardType"],
  title: string,
  payload: Record<string, unknown>
): AgentMessage {
  return {
    id,
    role: "card",
    cardType,
    title,
    payload,
    createdAt: "2026-06-19T00:00:00.000Z"
  };
}
