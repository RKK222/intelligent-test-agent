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
        setProviderAuth: async (...args: unknown[]) => calls.push(["set", ...args]),
        removeProviderAuth: async (...args: unknown[]) => calls.push(["remove", ...args])
      }
    });

    render(Harness, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Settings" }));

    expect(await screen.findByRole("dialog", { name: "Settings" })).toBeInTheDocument();
    expect(await screen.findByText("Not connected")).toBeInTheDocument();

    await fireEvent.update(screen.getByLabelText("Anthropic API key"), "sk-test");
    await fireEvent.click(screen.getByRole("button", { name: "Save Anthropic key" }));
    await fireEvent.click(screen.getByRole("button", { name: "Remove Anthropic auth" }));

    expect(calls).toEqual([
      ["list", "wrk_1"],
      ["set", "anthropic", { type: "api-key", key: "sk-test" }],
      ["list", "wrk_1"],
      ["remove", "anthropic"],
      ["list", "wrk_1"]
    ]);
  });
});
