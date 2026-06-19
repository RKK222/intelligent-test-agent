import { render, screen } from "@testing-library/react";
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
});
