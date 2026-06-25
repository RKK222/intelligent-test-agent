import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
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
    await fireEvent.click(screen.getByRole("option", { name: "/compact Summarize the session" }));

    expect(screen.getByPlaceholderText("Ask opencode to inspect, edit, test, or explain this workspace...")).toHaveValue("/compact");
    await fireEvent.submit(screen.getByRole("form", { name: "Prompt composer" }));

    expect(view.emitted("submit")).toHaveLength(1);
    expect(workspace.loadCommands).toHaveBeenCalledOnce();
  });

  it("navigates slash commands with the keyboard and inserts argument templates", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const workspace = useWorkspaceStore();
    workspace.commands = [
      { commandId: "compact", name: "compact", description: "Summarize the session" },
      { commandId: "review", name: "review", description: "Review staged changes", arguments: "--quick" }
    ];
    workspace.loadCommands = vi.fn();

    render(PromptComposer, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Open slash commands" }));
    const search = screen.getByLabelText("Search slash commands");

    expect(screen.getByRole("option", { name: "/compact Summarize the session" })).toHaveAttribute("aria-selected", "true");
    await fireEvent.keyDown(search, { key: "ArrowDown" });
    expect(screen.getByRole("option", { name: "/review Review staged changes" })).toHaveAttribute("aria-selected", "true");
    await fireEvent.keyDown(search, { key: "Enter" });

    expect(screen.queryByRole("dialog", { name: "Slash commands" })).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("Ask opencode to inspect, edit, test, or explain this workspace...")).toHaveValue("/review --quick");

    await fireEvent.click(screen.getByRole("button", { name: "Open slash commands" }));
    await fireEvent.keyDown(screen.getByLabelText("Search slash commands"), { key: "Escape" });

    expect(screen.queryByRole("dialog", { name: "Slash commands" })).not.toBeInTheDocument();
  });

  it("opens slash commands when typing a slash trigger in the textarea", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const workspace = useWorkspaceStore();
    workspace.commands = [
      { commandId: "review", name: "review", description: "Review staged changes" },
      { commandId: "compact", name: "compact", description: "Summarize the session" }
    ];
    workspace.loadCommands = vi.fn();

    render(PromptComposer, { global: { plugins: [pinia] } });

    await fireEvent.update(screen.getByPlaceholderText("Ask opencode to inspect, edit, test, or explain this workspace..."), "/rev");

    expect(screen.getByRole("dialog", { name: "Slash commands" })).toBeInTheDocument();
    expect(screen.getByLabelText("Search slash commands")).toHaveValue("rev");
    expect(screen.getByRole("option", { name: "/review Review staged changes" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "/compact Summarize the session" })).not.toBeInTheDocument();
    expect(workspace.loadCommands).toHaveBeenCalledOnce();
  });

  it("fills slash command parameters through a generated form", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const workspace = useWorkspaceStore();
    workspace.commands = [
      {
        commandId: "review",
        name: "review",
        description: "Review staged changes",
        arguments: "<target> [--quick] [--base <branch>]"
      }
    ];
    workspace.loadCommands = vi.fn();

    render(PromptComposer, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Open slash commands" }));
    await fireEvent.click(screen.getByRole("option", { name: "/review Review staged changes" }));

    const textarea = screen.getByPlaceholderText("Ask opencode to inspect, edit, test, or explain this workspace...");
    expect(screen.getByRole("region", { name: "Command parameters" })).toHaveTextContent("/review");
    expect(textarea).toHaveValue("/review");

    await fireEvent.update(screen.getByLabelText("target"), "staged");
    await fireEvent.click(screen.getByLabelText("--quick"));
    await fireEvent.update(screen.getByLabelText("branch"), "main");

    expect(textarea).toHaveValue("/review staged --quick --base main");

    await fireEvent.click(screen.getByRole("button", { name: "Close command parameters" }));
    expect(screen.queryByRole("region", { name: "Command parameters" })).not.toBeInTheDocument();
  });

  it("submits with Enter and keeps Shift+Enter for multiline editing", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const view = render(PromptComposer, { global: { plugins: [pinia] } });
    const textarea = screen.getByPlaceholderText("Ask opencode to inspect, edit, test, or explain this workspace...");

    await fireEvent.update(textarea, "Run tests");
    await fireEvent.keyDown(textarea, { key: "Enter", shiftKey: true });
    expect(view.emitted("submit")).toBeUndefined();

    await fireEvent.keyDown(textarea, { key: "Enter" });
    expect(view.emitted("submit")).toHaveLength(1);
  });

  it("switches to a stop button while a run is active", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const view = render(PromptComposer, { props: { running: true }, global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Stop output" }));

    expect(view.emitted("cancel")).toHaveLength(1);
    expect(view.emitted("submit")).toBeUndefined();
  });

  it("navigates prompt history with ArrowUp and ArrowDown at textarea boundaries", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const prompt = usePromptStore();
    prompt.history = ["Second prompt", "First prompt"];
    render(PromptComposer, { global: { plugins: [pinia] } });
    const textarea = screen.getByPlaceholderText("Ask opencode to inspect, edit, test, or explain this workspace...") as HTMLTextAreaElement;

    await fireEvent.update(textarea, "draft");
    textarea.setSelectionRange(0, 0);
    await fireEvent.keyDown(textarea, { key: "ArrowUp" });
    expect(textarea).toHaveValue("Second prompt");

    textarea.setSelectionRange(0, 0);
    await fireEvent.keyDown(textarea, { key: "ArrowUp" });
    expect(textarea).toHaveValue("First prompt");

    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
    await fireEvent.keyDown(textarea, { key: "ArrowDown" });
    expect(textarea).toHaveValue("Second prompt");

    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
    await fireEvent.keyDown(textarea, { key: "ArrowDown" });
    expect(textarea).toHaveValue("draft");
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

  it("selects model variants and clears stale variants when the model changes", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const prompt = usePromptStore();
    const workspace = useWorkspaceStore();
    workspace.models = [
      { id: "claude-sonnet-4", providerId: "anthropic", name: "Claude Sonnet 4", variants: ["low", "medium", "high"] },
      { id: "gpt-5.1", providerId: "openai", name: "GPT-5.1" }
    ];

    render(PromptComposer, { global: { plugins: [pinia] } });

    expect(screen.queryByLabelText("Model variant")).not.toBeInTheDocument();
    await fireEvent.update(screen.getByLabelText("Model"), "anthropic/claude-sonnet-4");
    await fireEvent.update(screen.getByLabelText("Model variant"), "high");

    expect(prompt.snapshot()).toMatchObject({
      model: "anthropic/claude-sonnet-4",
      variant: "high"
    });

    await fireEvent.update(screen.getByLabelText("Model"), "openai/gpt-5.1");

    expect(prompt.snapshot()).toMatchObject({
      model: "openai/gpt-5.1"
    });
    expect(prompt.snapshot().variant).toBeUndefined();
    expect(screen.queryByLabelText("Model variant")).not.toBeInTheDocument();
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

  it("adds image attachments from picker, paste, and drop", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const prompt = usePromptStore();
    const picked = new File(["picked"], "picked.png", { type: "image/png" });
    const pasted = new File(["pasted"], "pasted.png", { type: "image/png" });
    const dropped = new File(["dropped"], "dropped.png", { type: "image/png" });

    render(PromptComposer, { global: { plugins: [pinia] } });

    await fireEvent.change(screen.getByLabelText("Image attachment input"), { target: { files: [picked] } });
    await fireEvent.paste(screen.getByPlaceholderText("Ask opencode to inspect, edit, test, or explain this workspace..."), {
      clipboardData: {
        items: [{ kind: "file", getAsFile: () => pasted }],
        getData: () => ""
      }
    });
    await fireEvent.drop(screen.getByRole("form", { name: "Prompt composer" }), {
      dataTransfer: {
        files: [dropped],
        types: ["Files"]
      }
    });

    await waitFor(() => expect(prompt.images).toHaveLength(3));
    expect(screen.getByRole("img", { name: "picked.png" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "pasted.png" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "dropped.png" })).toBeInTheDocument();
    expect(prompt.snapshot().images?.map((image) => image.name)).toEqual(["picked.png", "pasted.png", "dropped.png"]);
    expect(prompt.snapshot().images?.[0]).toMatchObject({ mimeType: "image/png" });
    expect(prompt.snapshot().images?.[0]?.url).toMatch(/^data:image\/png;base64,/);
    expect(screen.getByText("3 parts")).toBeInTheDocument();

    await fireEvent.click(screen.getByRole("button", { name: "Remove picked.png image attachment" }));

    expect(prompt.snapshot().images?.map((image) => image.name)).toEqual(["pasted.png", "dropped.png"]);
    expect(screen.queryByRole("img", { name: "picked.png" })).not.toBeInTheDocument();
  });
});
