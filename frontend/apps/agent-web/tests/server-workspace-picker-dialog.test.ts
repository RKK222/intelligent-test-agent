import { defineComponent, h } from "vue";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ElMessageBox } from "element-plus";
import type { WorkspaceBackendServer } from "@test-agent/shared-types";
import ServerWorkspacePickerDialog from "../src/components/ServerWorkspacePickerDialog.vue";

vi.mock("element-plus", async (importOriginal) => {
  const actual = await importOriginal<typeof import("element-plus")>();
  return {
    ...actual,
    ElMessageBox: { confirm: vi.fn() }
  };
});

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
        onClick: async () => {
          try {
            await props.createTicket();
          } catch {
            // 用户取消二次确认属于正常分支，stub 不把拒绝冒泡为未处理异常。
          }
        }
      }, "测试签票")
    ]);
  }
});

describe("server workspace picker dialog", () => {
  beforeEach(() => {
    vi.mocked(ElMessageBox.confirm).mockReset();
    vi.mocked(ElMessageBox.confirm).mockResolvedValue(
      { action: "confirm" } as Awaited<ReturnType<typeof ElMessageBox.confirm>>
    );
  });

  it("在服务器选择弹窗内二次确认目标后自动签发服务器终端 ticket", async () => {
    const createServerTerminalTicket = vi.fn().mockResolvedValue({
      ticket: "pty_server",
      expiresAt: "2026-07-18T14:00:00Z",
      webSocketUrl: "wss://console.example/terminal/ws?ticket=pty_server"
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
    expect(terminal.getAttribute("data-title")).toBe("server@server-a");
    await fireEvent.click(view.getByRole("button", { name: "测试签票" }));
    await waitFor(() => expect(createServerTerminalTicket).toHaveBeenCalledWith("server-a", "SERVER@server-a"));
    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      expect.stringContaining("测试服务器 A（server-a）"),
      "确认连接服务器终端",
      expect.objectContaining({ confirmButtonText: "确认连接", cancelButtonText: "取消" })
    );

    await view.rerender({ selectedServerId: "server-b" });
    expect(view.getByTestId("terminal-panel").getAttribute("data-title")).toBe("server@server-b");
    await fireEvent.click(view.getByRole("button", { name: "测试签票" }));
    await waitFor(() => expect(createServerTerminalTicket).toHaveBeenCalledWith("server-b", "SERVER@server-b"));
  });

  it("取消二次确认时不创建服务器终端 ticket", async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce("cancel");
    const createServerTerminalTicket = vi.fn();
    const view = render(ServerWorkspacePickerDialog, {
      props: {
        open: true,
        servers,
        selectedServerId: "server-a",
        directory: { path: "/data/testagent", parentPath: "/data", entries: [] },
        loading: false,
        serverTerminalEnabled: true,
        terminalBaseUrl: "https://console.example",
        createServerTerminalTicket
      },
      global: { stubs: { TerminalPanel: TerminalPanelStub } }
    });

    await fireEvent.click(view.getByRole("button", { name: "服务器终端" }));
    await fireEvent.click(view.getByRole("button", { name: "测试签票" }));
    await waitFor(() => expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1));
    expect(createServerTerminalTicket).not.toHaveBeenCalled();
  });
});
