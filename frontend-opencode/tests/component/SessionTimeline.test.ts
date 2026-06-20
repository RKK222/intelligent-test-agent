import { render, screen } from "@testing-library/vue";
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
              { partId: "tool_1", type: "tool", toolName: "grep", status: "completed", input: { query: "vite" }, output: "vite.config.ts" },
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
});
