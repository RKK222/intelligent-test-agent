import { fireEvent, render, screen } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import PromptComposer from "@/components/PromptComposer.vue";
import { usePlatformStore } from "@/stores/platform";
import { usePromptStore } from "@/stores/prompt";
import { useWorkspaceStore } from "@/stores/workspace";

describe("PromptComposer", () => {
  it("inserts slash commands from the platform command catalog", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const workspace = useWorkspaceStore();
    workspace.commands = [
      { commandId: "compact", name: "compact", description: "Summarize the session" },
      { commandId: "fork", name: "fork", description: "Fork from message" }
    ];
    workspace.loadCommands = vi.fn();

    const view = render(PromptComposer, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Open slash commands" }));
    expect(screen.getByRole("dialog", { name: "Slash commands" })).toBeInTheDocument();

    await fireEvent.update(screen.getByLabelText("Search slash commands"), "comp");
    await fireEvent.click(screen.getByRole("button", { name: "/compact Summarize the session" }));

    expect(screen.getByPlaceholderText("Ask opencode to inspect, edit, test, or explain this workspace...")).toHaveValue("/compact");
    await fireEvent.submit(screen.getByRole("form", { name: "Prompt composer" }));

    expect(view.emitted("submit")).toHaveLength(1);
    expect(workspace.loadCommands).toHaveBeenCalledOnce();
  });

  it("selects runtime agent and model without adding prompt parts", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const prompt = usePromptStore();
    const workspace = useWorkspaceStore();
    workspace.agents = [
      { agentId: "build", name: "Build", mode: "primary" },
      { agentId: "review", name: "Review", mode: "subagent" }
    ];
    workspace.models = [
      { id: "claude-sonnet-4", providerId: "anthropic", name: "Claude Sonnet 4" },
      { id: "gpt-5.1", providerId: "openai", name: "GPT-5.1" }
    ];

    render(PromptComposer, { global: { plugins: [pinia] } });

    await fireEvent.update(screen.getByLabelText("Agent"), "review");
    await fireEvent.update(screen.getByLabelText("Model"), "openai/gpt-5.1");

    expect(prompt.snapshot()).toMatchObject({
      agent: "review",
      model: "openai/gpt-5.1"
    });
    expect(screen.getByText("0 parts")).toBeInTheDocument();
  });

  it("selects attachments and @ references through the platform fs catalog", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();
    const workspace = useWorkspaceStore();
    const calls: Array<[string | undefined, string]> = [];
    workspace.selectedWorkspaceId = "wrk_1";

    Object.defineProperty(platform, "api", {
      value: {
        findRuntimeFiles: async (workspaceId?: string, query = "") => {
          calls.push([workspaceId, query]);
          return {
            data: [
              { path: "src/main.ts", name: "main.ts", type: "file" },
              { path: "src/components", name: "components", type: "directory" }
            ]
          };
        }
      }
    });

    render(PromptComposer, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Attach file" }));
    expect(screen.getByRole("dialog", { name: "Attach workspace file" })).toBeInTheDocument();
    await fireEvent.click(await screen.findByRole("button", { name: "Attach src/main.ts file" }));
    expect(screen.getByText("src/main.ts")).toBeInTheDocument();
    await fireEvent.click(screen.getByRole("button", { name: "Remove src/main.ts attachment" }));
    expect(screen.queryByText("src/main.ts")).not.toBeInTheDocument();

    await fireEvent.click(screen.getByRole("button", { name: "Mention file or symbol" }));
    expect(screen.getByRole("dialog", { name: "Mention workspace file" })).toBeInTheDocument();
    await fireEvent.update(screen.getByLabelText("Search workspace files"), "main");
    await fireEvent.click(await screen.findByRole("button", { name: "Mention src/main.ts file" }));
    expect(screen.getByText("@src/main.ts")).toBeInTheDocument();
    await fireEvent.click(screen.getByRole("button", { name: "Remove src/main.ts reference" }));
    expect(screen.queryByText("@src/main.ts")).not.toBeInTheDocument();
    expect(screen.getByText("0 parts")).toBeInTheDocument();
    expect(calls).toContainEqual(["wrk_1", ""]);
    expect(calls).toContainEqual(["wrk_1", "main"]);
  });
});
