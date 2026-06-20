import { fireEvent, render, screen } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import SessionDockStack from "@/components/SessionDockStack.vue";
import { useSessionStore } from "@/stores/session";

describe("SessionDockStack", () => {
  it("renders opencode-style composer docks and dispatches actions", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const session = useSessionStore();
    session.activeSession = {
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      title: "Demo",
      status: "RUNNING",
      createdAt: "2026-06-20T00:00:00Z",
      updatedAt: "2026-06-20T00:00:00Z"
    };
    session.permissions = [
      {
        requestId: "perm_1",
        sessionId: "ses_1",
        type: "edit",
        title: "Edit file",
        description: "Allow writing src/App.vue",
        pattern: "src/App.vue",
        createdAt: "2026-06-20T00:00:00Z"
      }
    ];
    session.questions = [
      {
        requestId: "q_1",
        sessionId: "ses_1",
        questions: [
          {
            questionId: "choice",
            text: "Which path should be updated?",
            kind: "single",
            options: [
              { id: "src", label: "src/App.vue" },
              { id: "test", label: "tests/App.test.ts" }
            ]
          }
        ],
        createdAt: "2026-06-20T00:00:00Z"
      }
    ];
    session.todos = [
      { id: "todo_1", text: "Inspect app shell", status: "completed" },
      { id: "todo_2", text: "Wire composer dock", status: "in_progress" }
    ];
    session.followups = [{ id: "follow_1", text: "Run the smoke test" }];
    session.revertItems = [{ id: "msg_1", text: "Restore changes from message msg_1" }];
    session.replyPermission = vi.fn();
    session.replyQuestion = vi.fn();
    session.sendFollowup = vi.fn();
    session.restoreRevert = vi.fn();

    render(SessionDockStack, { global: { plugins: [pinia] } });

    expect(screen.getByRole("region", { name: "Session requests" })).toBeInTheDocument();
    expect(screen.getByText("Edit file")).toBeInTheDocument();
    expect(screen.getByText("1 / 2 todos")).toBeInTheDocument();
    expect(screen.getByText("Run the smoke test")).toBeInTheDocument();
    expect(screen.getByText("Restore changes from message msg_1")).toBeInTheDocument();

    await fireEvent.click(screen.getByRole("button", { name: "Allow once" }));
    await fireEvent.click(screen.getByRole("radio", { name: "src/App.vue" }));
    await fireEvent.click(screen.getByRole("button", { name: "Submit answer" }));
    await fireEvent.click(screen.getByRole("button", { name: "Send now" }));
    await fireEvent.click(screen.getByRole("button", { name: "Restore" }));

    expect(session.replyPermission).toHaveBeenCalledWith("perm_1", "once");
    expect(session.replyQuestion).toHaveBeenCalledWith("q_1", [["src/App.vue"]]);
    expect(session.sendFollowup).toHaveBeenCalledWith("follow_1");
    expect(session.restoreRevert).toHaveBeenCalledWith("msg_1");
  });
});
