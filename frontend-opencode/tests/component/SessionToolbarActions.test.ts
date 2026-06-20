import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import SessionToolbarActions from "@/components/SessionToolbarActions.vue";
import { useSessionStore } from "@/stores/session";

describe("SessionToolbarActions", () => {
  it("keeps abort disabled until a run is active", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/w/:workspaceId/session/:sessionId?", name: "session", component: { template: "<div />" } }]
    });
    await router.push("/w/wrk_1/session/ses_1");
    await router.isReady();

    const session = useSessionStore();
    session.activeSession = {
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      title: "Demo",
      status: "IDLE",
      createdAt: "2026-06-20T00:00:00Z",
      updatedAt: "2026-06-20T00:00:00Z"
    };
    session.abort = vi.fn();

    render(SessionToolbarActions, { global: { plugins: [pinia, router] } });

    const abort = screen.getByRole("button", { name: "Abort session" });
    expect(abort).toBeDisabled();
    expect(session.abort).not.toHaveBeenCalled();

    session.activeRun = {
      runId: "run_1",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      status: "RUNNING",
      createdAt: "2026-06-20T00:01:00Z",
      updatedAt: "2026-06-20T00:01:00Z"
    };
    await waitFor(() => expect(abort).not.toBeDisabled());
    await fireEvent.click(abort);
    expect(session.abort).toHaveBeenCalledOnce();
  });

  it("opens fork choices and dispatches session toolbar actions", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/w/:workspaceId/session/:sessionId?", name: "session", component: { template: "<div />" } }]
    });
    await router.push("/w/wrk_1/session/ses_1");
    await router.isReady();

    const session = useSessionStore();
    session.activeSession = {
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      title: "Demo",
      status: "RUNNING",
      createdAt: "2026-06-20T00:00:00Z",
      updatedAt: "2026-06-20T00:00:00Z",
      model: { id: "claude-sonnet-4", providerId: "anthropic" }
    };
    session.activeRun = {
      runId: "run_1",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      status: "RUNNING",
      createdAt: "2026-06-20T00:01:00Z",
      updatedAt: "2026-06-20T00:01:00Z"
    };
    session.messages = [
      {
        messageId: "msg_1",
        sessionId: "ses_1",
        role: "USER",
        content: "Fix flaky login test",
        createdAt: "2026-06-20T00:01:00Z"
      },
      {
        messageId: "msg_2",
        sessionId: "ses_1",
        role: "USER",
        content: "Add provider settings",
        createdAt: "2026-06-20T00:02:00Z"
      }
    ];
    session.forkFromMessage = vi.fn(async () => ({
      sessionId: "ses_child",
      workspaceId: "wrk_1",
      title: "Forked",
      status: "IDLE",
      createdAt: "2026-06-20T00:03:00Z",
      updatedAt: "2026-06-20T00:03:00Z"
    }));
    session.revertLatestUserMessage = vi.fn();
    session.compactSession = vi.fn();
    session.abort = vi.fn();

    render(SessionToolbarActions, { global: { plugins: [pinia, router] } });

    await fireEvent.click(screen.getByRole("button", { name: "Fork session" }));
    expect(screen.getByRole("dialog", { name: "Fork session" })).toBeInTheDocument();
    await fireEvent.click(screen.getByRole("button", { name: /Add provider settings/ }));

    await waitFor(() => expect(session.forkFromMessage).toHaveBeenCalledWith("msg_2"));
    await waitFor(() => expect(router.currentRoute.value.params.sessionId).toBe("ses_child"));

    await fireEvent.click(screen.getByRole("button", { name: "Revert latest user message" }));
    await fireEvent.click(screen.getByRole("button", { name: "Compact session" }));
    await fireEvent.click(screen.getByRole("button", { name: "Abort session" }));

    expect(session.revertLatestUserMessage).toHaveBeenCalledOnce();
    expect(session.compactSession).toHaveBeenCalledOnce();
    expect(session.abort).toHaveBeenCalledOnce();
  });
});
