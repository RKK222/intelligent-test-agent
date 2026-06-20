import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import SettingsMcpPanel from "@/components/SettingsMcpPanel.vue";
import { usePlatformStore } from "@/stores/platform";

describe("SettingsMcpPanel", () => {
  it("renders MCP status and runs auth through backend-api actions", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();
    const calls: Array<[string, ...unknown[]]> = [];

    Object.defineProperty(platform, "api", {
      value: {
        getMcpStatus: async (...args: unknown[]) => {
          calls.push(["status", ...args]);
          return {
            github: { status: "needs_auth", error: "token expired" },
            filesystem: { status: "connected" }
          };
        },
        startMcpAuth: async (...args: unknown[]) => {
          calls.push(["auth", ...args]);
          return { url: "https://example.test/oauth" };
        },
        removeMcpAuth: async (...args: unknown[]) => {
          calls.push(["removeAuth", ...args]);
          return true;
        },
        connectMcp: async (...args: unknown[]) => {
          calls.push(["connect", ...args]);
          return true;
        },
        disconnectMcp: async (...args: unknown[]) => {
          calls.push(["disconnect", ...args]);
          return true;
        }
      }
    });

    render(SettingsMcpPanel, { props: { workspaceId: "wrk_1" }, global: { plugins: [pinia] } });

    expect(await screen.findByText("github")).toBeInTheDocument();
    expect(screen.getByText("filesystem")).toBeInTheDocument();
    expect(screen.getByText("Needs auth")).toBeInTheDocument();
    expect(screen.getByText("Connected")).toBeInTheDocument();
    expect(screen.getByText("token expired")).toBeInTheDocument();

    await fireEvent.click(screen.getByRole("button", { name: "Connect github" }));
    await waitFor(() => expect(calls).toContainEqual(["connect", "github", { workspaceId: "wrk_1" }]));

    await fireEvent.click(screen.getByRole("button", { name: "Disconnect filesystem" }));
    await waitFor(() => expect(calls).toContainEqual(["disconnect", "filesystem", { workspaceId: "wrk_1" }]));

    await fireEvent.click(screen.getByRole("button", { name: "Authenticate github" }));
    await waitFor(() => expect(calls).toContainEqual(["auth", "github", { workspaceId: "wrk_1" }]));
    expect(await screen.findByRole("link", { name: "Open github auth URL" })).toHaveAttribute(
      "href",
      "https://example.test/oauth"
    );

    await fireEvent.click(screen.getByRole("button", { name: "Remove github auth" }));
    await waitFor(() => expect(calls).toContainEqual(["removeAuth", "github"]));

    expect(calls).toEqual([
      ["status", "wrk_1"],
      ["connect", "github", { workspaceId: "wrk_1" }],
      ["status", "wrk_1"],
      ["disconnect", "filesystem", { workspaceId: "wrk_1" }],
      ["status", "wrk_1"],
      ["auth", "github", { workspaceId: "wrk_1" }],
      ["status", "wrk_1"],
      ["removeAuth", "github"],
      ["status", "wrk_1"]
    ]);
  });

  it("renders the opencode MCP empty state", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();

    Object.defineProperty(platform, "api", {
      value: {
        getMcpStatus: async () => ({})
      }
    });

    render(SettingsMcpPanel, { props: { workspaceId: "wrk_1" }, global: { plugins: [pinia] } });

    expect((await screen.findByText("No MCP servers configured.")).textContent).toBe("No MCP servers configured.");
  });
});
