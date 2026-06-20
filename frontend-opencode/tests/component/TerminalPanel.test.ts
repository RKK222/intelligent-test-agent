import { fireEvent, render, screen } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import TerminalPanel from "@/components/TerminalPanel.vue";
import { useTerminalStore } from "@/stores/terminal";

describe("TerminalPanel", () => {
  it("renders terminal output and submits input through the store", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const terminal = useTerminalStore();
    terminal.status = "ready";
    terminal.output = ["$ pwd\n", "/repo\n"];
    terminal.sendInput = vi.fn();
    terminal.resize = vi.fn();
    terminal.clear = vi.fn();
    terminal.close = vi.fn();

    render(TerminalPanel, { global: { plugins: [pinia] } });

    expect(screen.getByLabelText("Terminal output")).toHaveTextContent("/repo");

    await fireEvent.update(screen.getByLabelText("Terminal input"), "pnpm test");
    await fireEvent.click(screen.getByRole("button", { name: "Send" }));

    expect(terminal.input).toBe("pnpm test");
    expect(terminal.sendInput).toHaveBeenCalledOnce();

    await fireEvent.update(screen.getByLabelText("Terminal columns"), "120");
    await fireEvent.update(screen.getByLabelText("Terminal rows"), "32");
    await fireEvent.click(screen.getByRole("button", { name: "Resize terminal" }));
    expect(terminal.resize).toHaveBeenCalledWith(120, 32);

    await fireEvent.click(screen.getByRole("button", { name: "Clear terminal" }));
    expect(terminal.clear).toHaveBeenCalledOnce();

    await fireEvent.click(screen.getByRole("button", { name: "Close terminal" }));
    expect(terminal.close).toHaveBeenCalledOnce();
  });
});
