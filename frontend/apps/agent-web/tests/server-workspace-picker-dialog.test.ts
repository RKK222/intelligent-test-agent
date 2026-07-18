import { defineComponent, h } from "vue";
import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type { WorkspaceBackendServer } from "@test-agent/shared-types";
import ServerWorkspacePickerDialog from "../src/components/ServerWorkspacePickerDialog.vue";

const servers: WorkspaceBackendServer[] = [
  {
    linuxServerId: "server-a",
    name: "测试服务器 A",
    baseUrl: "http://10.8.0.12:8080",
    webSocketPath: "/workspace/ws",
    defaultDirectory: "/data/testagent",
    sameAsAgent: true
  },
  {
    linuxServerId: "server-b",
    name: "测试服务器 B",
    baseUrl: "http://10.8.0.13:8080",
    webSocketPath: "/workspace/ws",
    defaultDirectory: "/data/testagent",
    sameAsAgent: false
  }
];

const TerminalPanelStub = defineComponent({
  props: ["disabled", "title", "createTicket"],
  setup(props) {
    return () => h("div", {
      "data-testid": "terminal-panel",
      "data-disabled": String(props.disabled),
      "data-title": props.title
    }, [
      h("button", {
        type: "button",
        disabled: props.disabled,
        onClick: () => props.createTicket()
      }, "测试签票")
    ]);
  }
});

describe("server workspace picker dialog", () => {
  it("在服务器选择弹窗内打开 root 终端并要求逐台确认", async () => {
    const createServerTerminalTicket = vi.fn().mockResolvedValue({
      ticket: "pty_root",
      expiresAt: "2026-07-18T14:00:00Z",
      webSocketUrl: "wss://console.example/terminal/ws?ticket=pty_root"
    });
    const view = render(ServerWorkspacePickerDialog, {
      props: {
        open: true,
        servers,
        selectedServerId: "server-a",
        directory: { path: "/data/testagent", parentPath: "/data", entries: [] },
        loading: false,
        currentAgentLinuxServerId: "server-a",
        serverTerminalEnabled: true,
        terminalBaseUrl: "https://console.example",
        createServerTerminalTicket
      },
      global: {
        stubs: { TerminalPanel: TerminalPanelStub }
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "服务器终端" }));
    expect(view.getByRole("alert").textContent).toContain("测试服务器 A");
    const terminal = view.getByTestId("terminal-panel");
    expect(terminal.getAttribute("data-title")).toBe("root@server-a");
    expect(terminal.getAttribute("data-disabled")).toBe("true");

    await fireEvent.update(view.getByPlaceholderText("ROOT@server-a"), "ROOT@server-a");
    expect(terminal.getAttribute("data-disabled")).toBe("false");
    await fireEvent.click(view.getByRole("button", { name: "测试签票" }));
    expect(createServerTerminalTicket).toHaveBeenCalledWith("server-a", "ROOT@server-a");

    await view.rerender({ selectedServerId: "server-b" });
    expect((view.getByPlaceholderText("ROOT@server-b") as HTMLInputElement).value).toBe("");
    expect(view.getByTestId("terminal-panel").getAttribute("data-title")).toBe("root@server-b");
    expect(view.getByTestId("terminal-panel").getAttribute("data-disabled")).toBe("true");
  });
});
