import { defineComponent, h } from "vue";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ElMessage, ElMessageBox } from "element-plus";
import type { WorkspaceBackendServer } from "@test-agent/shared-types";
import ServerWorkspacePickerDialog from "../src/components/ServerWorkspacePickerDialog.vue";

vi.mock("element-plus", async (importOriginal) => {
  const actual = await importOriginal<typeof import("element-plus")>();
  return {
    ...actual,
    ElMessage: { warning: vi.fn() },
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
    vi.mocked(ElMessage.warning).mockReset();
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

  it("支持普通窗口键盘缩放以及页面内全屏和还原", async () => {
    const view = render(ServerWorkspacePickerDialog, {
      props: {
        open: true,
        servers,
        selectedServerId: "server-a",
        directory: { path: "/data/testagent", parentPath: "/data", entries: [] },
        loading: false
      }
    });

    const dialog = view.getByRole("dialog");
    const resizeHandle = view.getByRole("button", { name: "调整窗口大小" });
    const originalWidth = Number.parseInt(dialog.style.width, 10);
    await fireEvent.keyDown(resizeHandle, { key: "ArrowLeft" });
    expect(Number.parseInt(dialog.style.width, 10)).toBe(originalWidth - 16);

    await fireEvent.click(view.getByRole("button", { name: "进入全屏" }));
    expect(dialog.getAttribute("data-layout-mode")).toBe("fullscreen");
    expect(dialog.style.width).toBe("100vw");
    expect(view.queryByRole("button", { name: "调整窗口大小" })).toBeNull();

    await fireEvent.click(view.getByRole("button", { name: "退出全屏" }));
    expect(dialog.getAttribute("data-layout-mode")).toBe("window");
    expect(Number.parseInt(dialog.style.width, 10)).toBe(originalWidth - 16);
    expect(view.getByRole("button", { name: "调整窗口大小" })).toBeTruthy();
  });

  it("使用真实应用 URL 打开普通新标签页", async () => {
    const openedTab = { closed: false } as Window;
    const openWindow = vi.spyOn(window, "open").mockReturnValue(openedTab);
    const view = render(ServerWorkspacePickerDialog, {
      props: {
        open: true,
        servers,
        selectedServerId: "server-a",
        directory: { path: "/data/testagent", parentPath: "/data", entries: [] },
        loading: false,
        newTabUrl: "/?serverWorkspacePicker=1"
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "在新标签页打开" }));
    expect(openWindow).toHaveBeenCalledWith(
      "http://localhost:3000/?serverWorkspacePicker=1",
      "_blank"
    );
    expect(JSON.parse(sessionStorage.getItem("test-agent.server-workspace-picker-tab-state") ?? "{}")).toEqual({
      serverId: "server-a",
      path: "/data/testagent"
    });
    expect(view.getByRole("dialog").getAttribute("data-layout-mode")).toBe("window");
    openWindow.mockRestore();
  });

  it("企业浏览器策略阻止新标签页时保留原页面并给出明确提示", async () => {
    const openWindow = vi.spyOn(window, "open").mockReturnValue(null);
    const view = render(ServerWorkspacePickerDialog, {
      props: {
        open: true,
        servers,
        selectedServerId: "server-a",
        directory: { path: "/data/testagent", parentPath: "/data", entries: [] },
        loading: false
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "在新标签页打开" }));
    expect(ElMessage.warning).toHaveBeenCalledWith("浏览器阻止了新标签页，请允许此站点打开弹窗后重试");
    expect(view.getByRole("dialog").getAttribute("data-layout-mode")).toBe("window");
    expect(view.getAllByText("/data/testagent").length).toBeGreaterThan(0);
    openWindow.mockRestore();
  });
});
