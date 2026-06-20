import { fireEvent, render, screen } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import PromptComposer from "@/components/PromptComposer.vue";
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
});
