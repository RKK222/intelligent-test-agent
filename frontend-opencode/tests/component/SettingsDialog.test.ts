import { fireEvent, render, screen } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent } from "vue";
import SettingsDialog from "@/components/SettingsDialog.vue";
import { usePlatformStore } from "@/stores/platform";
import { useWorkspaceStore } from "@/stores/workspace";

const Harness = defineComponent({
  components: { SettingsDialog },
  template: `<button data-settings-trigger type="button">Settings</button><SettingsDialog />`
});

describe("SettingsDialog", () => {
  it("closes the settings dialog with Escape", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();

    Object.defineProperty(platform, "api", {
      value: {
        listProviderAuth: async () => ({}),
        listWorktrees: async () => [],
        getMcpStatus: async () => []
      }
    });

    render(Harness, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    expect(await screen.findByRole("dialog", { name: "Settings" })).toBeInTheDocument();

    await fireEvent.keyDown(document, { key: "Escape" });

    expect(screen.queryByRole("dialog", { name: "Settings" })).not.toBeInTheDocument();
  });

  it("manages provider auth through backend-api actions", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const workspace = useWorkspaceStore();
    const platform = usePlatformStore();
    const calls: Array<[string, ...unknown[]]> = [];

    workspace.selectedWorkspaceId = "wrk_1";
    workspace.providers = [{ providerId: "anthropic", name: "Anthropic", status: "available" }];

    Object.defineProperty(platform, "api", {
      value: {
        listProviderAuth: async (...args: unknown[]) => {
          calls.push(["list", ...args]);
          return [{ providerId: "anthropic", status: "missing" }];
        },
        authorizeProviderOAuth: async (...args: unknown[]) => {
          calls.push(["oauth", ...args]);
          return { url: "https://auth.example/anthropic" };
        },
        completeProviderOAuth: async (...args: unknown[]) => calls.push(["callback", ...args]),
        setProviderAuth: async (...args: unknown[]) => calls.push(["set", ...args]),
        removeProviderAuth: async (...args: unknown[]) => calls.push(["remove", ...args])
      }
    });

    render(Harness, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Settings" }));

    expect(await screen.findByRole("dialog", { name: "Settings" })).toBeInTheDocument();
    expect(await screen.findByText("Not connected")).toBeInTheDocument();

    await fireEvent.update(screen.getByLabelText("Anthropic API key"), "sk-test");
    await fireEvent.click(screen.getByRole("button", { name: "Authorize Anthropic OAuth" }));
    expect(await screen.findByRole("link", { name: "Open Anthropic OAuth URL" })).toHaveAttribute(
      "href",
      "https://auth.example/anthropic"
    );
    await fireEvent.update(screen.getByLabelText("Anthropic OAuth code"), "code-123");
    await fireEvent.click(screen.getByRole("button", { name: "Complete Anthropic OAuth" }));
    await fireEvent.click(screen.getByRole("button", { name: "Save Anthropic key" }));
    await fireEvent.click(screen.getByRole("button", { name: "Remove Anthropic auth" }));

    expect(calls).toEqual([
      ["list", "wrk_1"],
      [
        "oauth",
        "anthropic",
        {
          method: 0,
          inputs: {
            callbackUrl: "http://localhost:3000/api/provider/anthropic/oauth/callback"
          }
        }
      ],
      ["callback", "anthropic", { method: 0, code: "code-123" }],
      ["list", "wrk_1"],
      ["set", "anthropic", { type: "api-key", key: "sk-test" }],
      ["list", "wrk_1"],
      ["remove", "anthropic"],
      ["list", "wrk_1"]
    ]);
  });

  it("submits provider OAuth method prompts with the selected opencode method index", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const workspace = useWorkspaceStore();
    const platform = usePlatformStore();
    const calls: Array<[string, ...unknown[]]> = [];

    workspace.selectedWorkspaceId = "wrk_1";
    workspace.providers = [{ providerId: "github-copilot", name: "Copilot", status: "available" }];

    Object.defineProperty(platform, "api", {
      value: {
        listProviderAuth: async (...args: unknown[]) => {
          calls.push(["list", ...args]);
          return {
            "github-copilot": [
              { type: "api", label: "API key" },
              {
                type: "oauth",
                label: "Browser login",
                prompts: [
                  {
                    type: "select",
                    key: "region",
                    message: "Region",
                    options: [
                      { label: "Public", value: "public" },
                      { label: "Enterprise", value: "enterprise", hint: "GitHub Enterprise" }
                    ]
                  },
                  {
                    type: "text",
                    key: "tenant",
                    message: "Tenant",
                    placeholder: "tenant id",
                    when: { key: "region", op: "eq", value: "enterprise" }
                  }
                ]
              }
            ]
          };
        },
        authorizeProviderOAuth: async (...args: unknown[]) => {
          calls.push(["oauth", ...args]);
          return { url: "https://auth.example/copilot", method: "code", instructions: "Paste the browser code" };
        },
        completeProviderOAuth: async (...args: unknown[]) => calls.push(["callback", ...args]),
        setProviderAuth: async (...args: unknown[]) => calls.push(["set", ...args]),
        removeProviderAuth: async (...args: unknown[]) => calls.push(["remove", ...args])
      }
    });

    render(Harness, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    expect(await screen.findByText("Browser login")).toBeInTheDocument();

    await fireEvent.update(screen.getByLabelText("Copilot Region"), "enterprise");
    await fireEvent.update(await screen.findByLabelText("Copilot Tenant"), "acme");
    await fireEvent.click(screen.getByRole("button", { name: "Authorize Copilot OAuth" }));

    expect(await screen.findByRole("link", { name: "Open Copilot OAuth URL" })).toHaveAttribute(
      "href",
      "https://auth.example/copilot"
    );
    expect(screen.getByText("Paste the browser code")).toBeInTheDocument();

    await fireEvent.update(screen.getByLabelText("Copilot OAuth code"), "oauth-code");
    await fireEvent.click(screen.getByRole("button", { name: "Complete Copilot OAuth" }));

    expect(calls).toContainEqual([
      "oauth",
      "github-copilot",
      {
        method: 1,
        inputs: {
          callbackUrl: "http://localhost:3000/api/provider/github-copilot/oauth/callback",
          region: "enterprise",
          tenant: "acme"
        }
      }
    ]);
    expect(calls).toContainEqual(["callback", "github-copilot", { method: 1, code: "oauth-code" }]);
  });
});
