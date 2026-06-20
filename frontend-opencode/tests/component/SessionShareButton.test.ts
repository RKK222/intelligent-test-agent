import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import SessionShareButton from "@/components/SessionShareButton.vue";
import { usePlatformStore } from "@/stores/platform";
import { useSessionStore } from "@/stores/session";

describe("SessionShareButton", () => {
  it("publishes and unpublishes the active session through backend-api", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();
    const session = useSessionStore();
    const calls: Array<[string, ...unknown[]]> = [];

    session.activeSession = {
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      title: "Fix flaky test",
      status: "idle",
      createdAt: "2026-06-20T00:00:00Z",
      updatedAt: "2026-06-20T00:00:00Z"
    };

    Object.defineProperty(platform, "api", {
      value: {
        shareSession: async (...args: unknown[]) => {
          calls.push(["share", ...args]);
          return { share: { url: "https://share.example/ses_1" } };
        },
        unshareSession: async (...args: unknown[]) => {
          calls.push(["unshare", ...args]);
          return true;
        }
      }
    });

    render(SessionShareButton, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Share session" }));
    expect(screen.getByText("Publish on web")).toBeInTheDocument();

    await fireEvent.click(screen.getByRole("button", { name: "Publish session" }));
    await waitFor(() => expect(calls).toContainEqual(["share", "ses_1"]));
    expect(screen.getByDisplayValue("https://share.example/ses_1")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View shared session" })).toHaveAttribute("href", "https://share.example/ses_1");

    await fireEvent.click(screen.getByRole("button", { name: "Unpublish session" }));
    await waitFor(() => expect(calls).toContainEqual(["unshare", "ses_1"]));

    expect(calls).toEqual([
      ["share", "ses_1"],
      ["unshare", "ses_1"]
    ]);
  });
});
