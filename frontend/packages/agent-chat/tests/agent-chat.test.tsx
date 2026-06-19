import { fireEvent, render, screen } from "@testing-library/react";
import type { AgentMessage } from "@test-agent/shared-types";
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

  it("用蓝色用户气泡展示用户消息", () => {
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
    expect(bubble?.className).toContain("bg-[#1b2d5a]");
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
    expect(screen.getByText("读取运行报告")).toBeTruthy();
    expect(screen.getByText("调用技能 analyze_playwright")).toBeTruthy();
    expect(screen.getByText("read_file")).toBeTruthy();
    expect(screen.getByText("路径: reports/run-1287.json")).toBeTruthy();
    expect(screen.getByText("本次变更涉及 2 个文件")).toBeTruthy();
    expect(screen.getByText("tests/checkout.spec.ts")).toBeTruthy();
    expect(screen.getAllByText("MODIFIED")).toHaveLength(2);
    expect(screen.getAllByText("+2 -1")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "查看 Diff" })).toBeTruthy();
    expect(screen.getByText("修复建议")).toBeTruthy();
  });

  it("默认只展开最新关键卡片，并允许手动展开折叠", () => {
    renderAgentChat([
      card("diff-old", "diff", "旧 Diff", {
        files: [{ path: "old.ts", status: "modified", additions: 1, deletions: 0 }]
      }),
      card("tool-new", "tool", "工具调用完成", {
        toolName: "read_file",
        status: "completed",
        summary: "读取运行报告"
      })
    ]);

    const oldDiff = screen.getByTestId("timeline-card-diff-old");
    const latestTool = screen.getByTestId("timeline-card-tool-new");
    expect(oldDiff.hasAttribute("open")).toBe(false);
    expect(latestTool.hasAttribute("open")).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: "展开 旧 Diff" }));
    expect(oldDiff.hasAttribute("open")).toBe(true);
  });
});

function renderAgentChat(messages: AgentMessage[]) {
  return render(
    <AgentChat
      messages={messages}
      history={[]}
      onSend={vi.fn()}
      onOpenDiff={vi.fn()}
      onRetry={vi.fn()}
      onCancel={vi.fn()}
    />
  );
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
