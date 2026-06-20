import { fireEvent, render, screen } from "@testing-library/vue";
import SessionTimeline from "@/components/SessionTimeline.vue";

describe("SessionTimeline", () => {
  it("renders assistant message parts as opencode-style timeline blocks", () => {
    render(SessionTimeline, {
      props: {
        messages: [
          {
            messageId: "msg_1",
            sessionId: "ses_1",
            role: "ASSISTANT",
            content: "Inspected workspaceDone",
            createdAt: "2026-06-20T00:00:00Z",
            parts: [
              { partId: "reason_1", type: "reasoning", title: "Thinking", text: "Inspecting package scripts", status: "running" },
              { partId: "tool_1", type: "tool", toolName: "grep", status: "running", input: { query: "vite" }, output: "vite.config.ts" },
              { partId: "file_1", type: "file", path: "src/App.vue", name: "App.vue" },
              { partId: "text_1", type: "text", text: "Done", status: "completed" },
              { partId: "evt_1", type: "event", eventType: "session.status", payload: { status: "RUNNING" } }
            ]
          }
        ]
      }
    });

    expect(screen.getByRole("article", { name: "opencode message" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Reasoning Thinking" })).toHaveTextContent("Inspecting package scripts");
    expect(screen.getByRole("region", { name: "Tool grep" })).toHaveTextContent("vite.config.ts");
    expect(screen.getByRole("region", { name: "File src/App.vue" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Event session.status" })).toHaveTextContent("RUNNING");
    expect(screen.getByText("Done")).toBeInTheDocument();
  });

  it("collapses completed reasoning and tool parts until the user expands them", async () => {
    render(SessionTimeline, {
      props: {
        messages: [
          {
            messageId: "msg_2",
            sessionId: "ses_1",
            role: "ASSISTANT",
            content: "Finished",
            createdAt: "2026-06-20T00:01:00Z",
            parts: [
              { partId: "reason_done", type: "reasoning", title: "Thinking", text: "Hidden reasoning", status: "completed" },
              { partId: "tool_done", type: "tool", toolName: "bash", status: "completed", input: { command: "pnpm test" }, output: "hidden output" },
              { partId: "text_done", type: "text", text: "Finished", status: "completed" }
            ]
          }
        ]
      }
    });

    expect(screen.queryByText("Hidden reasoning")).not.toBeInTheDocument();
    expect(screen.queryByText("hidden output")).not.toBeInTheDocument();

    await fireEvent.click(screen.getByRole("button", { name: "Expand Reasoning Thinking" }));
    await fireEvent.click(screen.getByRole("button", { name: "Expand Tool bash" }));

    expect(screen.getByText("Hidden reasoning")).toBeInTheDocument();
    expect(screen.getByText("hidden output")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Collapse Reasoning Thinking" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("button", { name: "Collapse Tool bash" })).toHaveAttribute("aria-expanded", "true");
  });
});
