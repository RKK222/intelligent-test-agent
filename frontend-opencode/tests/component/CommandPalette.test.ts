import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import CommandPalette from "@/components/CommandPalette.vue";
import { usePromptStore } from "@/stores/prompt";
import { useWorkspaceStore } from "@/stores/workspace";

describe("CommandPalette", () => {
  it("filters commands, supports keyboard selection, and writes slash text into the composer store", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const workspace = useWorkspaceStore();
    const prompt = usePromptStore();
    workspace.commands = [
      { commandId: "compact", name: "compact", description: "Summarize the session" },
      { commandId: "review", name: "review", description: "Review staged changes", arguments: "--quick" },
      { commandId: "fork", name: "fork", description: "Fork from message" }
    ];
    workspace.loadCommands = vi.fn();

    render({
      components: { CommandPalette },
      template: `
        <button data-command-trigger type="button">Commands</button>
        <CommandPalette />
      `
    }, { global: { plugins: [pinia] } });

    await fireEvent.click(screen.getByRole("button", { name: "Commands" }));
    const search = screen.getByLabelText("Search commands");
    expect(screen.getByRole("dialog", { name: "Command palette" })).toBeInTheDocument();
    expect(workspace.loadCommands).toHaveBeenCalledOnce();

    await fireEvent.update(search, "review");
    expect(screen.getByRole("option", { name: "/review Review staged changes" })).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByRole("option", { name: "/compact Summarize the session" })).not.toBeInTheDocument();

    await fireEvent.keyDown(search, { key: "Enter" });
    expect(prompt.text).toBe("/review --quick");
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Command palette" })).not.toBeInTheDocument());
  });

  it("opens with the opencode palette keybind and closes with Escape", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const workspace = useWorkspaceStore();
    workspace.commands = [
      { commandId: "compact", name: "compact", description: "Summarize the session" },
      { commandId: "review", name: "review", description: "Review staged changes" }
    ];
    workspace.loadCommands = vi.fn();

    render(CommandPalette, { global: { plugins: [pinia] } });

    await fireEvent.keyDown(document, { key: "P", ctrlKey: true, shiftKey: true });
    const search = screen.getByLabelText("Search commands");
    expect(screen.getByRole("dialog", { name: "Command palette" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "/compact Summarize the session" })).toHaveAttribute("aria-selected", "true");

    await fireEvent.keyDown(search, { key: "ArrowDown" });
    expect(screen.getByRole("option", { name: "/review Review staged changes" })).toHaveAttribute("aria-selected", "true");
    await fireEvent.keyDown(search, { key: "Escape" });

    expect(screen.queryByRole("dialog", { name: "Command palette" })).not.toBeInTheDocument();
  });
});
