import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import SettingsWorktreePanel from "@/components/SettingsWorktreePanel.vue";
import { usePlatformStore } from "@/stores/platform";

describe("SettingsWorktreePanel", () => {
  it("manages opencode worktrees through backend-api actions", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();
    const calls: Array<[string, ...unknown[]]> = [];

    Object.defineProperty(platform, "api", {
      value: {
        listWorktrees: async (...args: unknown[]) => {
          calls.push(["list", ...args]);
          return [{ name: "feature ui", branch: "feature/vue", directory: "/repo/.worktrees/feature-vue" }];
        },
        createWorktree: async (...args: unknown[]) => {
          calls.push(["create", ...args]);
          return { name: "review-ui", branch: "review-ui", directory: "/repo/.worktrees/review-ui" };
        },
        resetWorktree: async (...args: unknown[]) => {
          calls.push(["reset", ...args]);
          return true;
        },
        removeWorktree: async (...args: unknown[]) => {
          calls.push(["remove", ...args]);
          return true;
        }
      }
    });

    render(SettingsWorktreePanel, { props: { workspaceId: "wrk_1" }, global: { plugins: [pinia] } });

    expect(await screen.findByText("feature ui")).toBeInTheDocument();
    expect(screen.getByText("feature/vue")).toBeInTheDocument();
    expect(screen.getByText("/repo/.worktrees/feature-vue")).toBeInTheDocument();

    await fireEvent.update(screen.getByLabelText("Worktree name"), "review-ui");
    await fireEvent.update(screen.getByLabelText("Startup command"), "pnpm install");
    await fireEvent.click(screen.getByRole("button", { name: "Create worktree" }));
    await waitFor(() =>
      expect(calls).toContainEqual(["create", { workspaceId: "wrk_1", name: "review-ui", startCommand: "pnpm install" }])
    );

    await fireEvent.click(screen.getByRole("button", { name: "Reset /repo/.worktrees/feature-vue" }));
    await waitFor(() =>
      expect(calls).toContainEqual(["reset", { workspaceId: "wrk_1", directory: "/repo/.worktrees/feature-vue" }])
    );

    await fireEvent.click(screen.getByRole("button", { name: "Remove /repo/.worktrees/feature-vue" }));
    await waitFor(() =>
      expect(calls).toContainEqual(["remove", { workspaceId: "wrk_1", directory: "/repo/.worktrees/feature-vue" }])
    );

    expect(calls).toEqual([
      ["list", "wrk_1"],
      ["create", { workspaceId: "wrk_1", name: "review-ui", startCommand: "pnpm install" }],
      ["list", "wrk_1"],
      ["reset", { workspaceId: "wrk_1", directory: "/repo/.worktrees/feature-vue" }],
      ["list", "wrk_1"],
      ["remove", { workspaceId: "wrk_1", directory: "/repo/.worktrees/feature-vue" }],
      ["list", "wrk_1"]
    ]);
  });

  it("renders an empty state when no worktrees are available", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();

    Object.defineProperty(platform, "api", {
      value: {
        listWorktrees: async () => []
      }
    });

    render(SettingsWorktreePanel, { props: { workspaceId: "wrk_1" }, global: { plugins: [pinia] } });

    expect((await screen.findByText("No sandbox worktrees yet.")).textContent).toBe("No sandbox worktrees yet.");
  });
});
