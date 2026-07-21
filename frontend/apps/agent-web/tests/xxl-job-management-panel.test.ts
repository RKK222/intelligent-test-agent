import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser } from "@test-agent/shared-types";
import ScheduledTaskManagementPanel from "../src/components/system/ScheduledTaskManagementPanel.vue";
import {
  XXL_JOB_EMBEDDED_STYLESHEET_PATH,
  applyXxlJobEmbeddedShell
} from "../src/components/system/xxl-job-embedded-shell";

const superAdmin: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

const regularUser: CurrentUser = {
  ...superAdmin,
  userId: "usr_user",
  username: "user",
  roles: ["USER"]
};

function ticket(value = "ticket-one") {
  return {
    ticket: value,
    expiresAt: "2099-07-20T08:00:00Z",
    formAction: "/xxl-job-admin/platform-sso/login"
  };
}

function api(issue = vi.fn().mockResolvedValue(ticket())) {
  return { createXxlJobSsoTicket: issue } as unknown as BackendApiClient;
}

function renderPanel(backendApi: BackendApiClient, currentUser: CurrentUser = superAdmin) {
  return render(ScheduledTaskManagementPanel, {
    props: { currentUser },
    global: { provide: { api: backendApi } }
  });
}

function xxlShellDocument() {
  const frameDocument = document.implementation.createHTMLDocument("XXL-JOB");
  frameDocument.body.innerHTML = `
    <div class="wrapper">
      <header class="main-header">
        <a class="logo">XXL 任务调度中心</a>
        <nav class="navbar">
          <a class="sidebar-toggle">Toggle navigation</a>
          <div class="navbar-custom-menu">
            <ul class="nav navbar-nav">
              <li class="dropdown">
                <a href="javascript:" class="dropdown-toggle" data-toggle="dropdown" aria-expanded="false">
                  欢迎：admin <span class="caret"></span>
                </a>
                <ul class="dropdown-menu"><li>修改密码</li></ul>
              </li>
            </ul>
          </div>
        </nav>
      </header>
      <aside class="main-sidebar">
        <section class="sidebar">
          <ul class="sidebar-menu">
            <li class="header">导航</li>
            <li class="nav-click"><a class="J_menuItem" href="/xxl-job-admin/"><span>运行报表</span></a></li>
          </ul>
        </section>
      </aside>
      <div class="content-wrapper"></div>
    </div>`;
  return frameDocument;
}

function frameWithDocument(frameDocument: Document) {
  return { contentDocument: frameDocument } as HTMLIFrameElement;
}

describe("XXL-JOB management panel", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("uses a one-time ticket in a hidden POST form and never puts it in the iframe URL", async () => {
    const submit = vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(() => undefined);
    const backendApi = api();
    const view = renderPanel(backendApi);

    await waitFor(() => expect(backendApi.createXxlJobSsoTicket).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));

    const form = view.container.querySelector("form");
    const iframe = view.getByTitle("XXL-JOB 定时任务管理") as HTMLIFrameElement;
    const input = view.container.querySelector('input[name="ticket"]') as HTMLInputElement;
    expect(form?.getAttribute("method")).toBe("post");
    expect(form?.getAttribute("action")).toBe("/xxl-job-admin/platform-sso/login");
    expect(form?.getAttribute("target")).toBe(iframe.getAttribute("name"));
    expect(input.value).toBe("ticket-one");
    expect(iframe.getAttribute("src")).not.toContain("ticket-one");

    await fireEvent.load(iframe);
    expect(view.queryByText("XXL-JOB 控制台已连接")).toBeNull();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      data: { type: "test-agent-xxl-job-sso", status: "ready" }
    }));
    expect(await view.findByText("XXL-JOB 控制台已连接")).toBeTruthy();
  });

  it("decorates the embedded XXL shell idempotently and makes the mapped account read-only", () => {
    const frameDocument = xxlShellDocument();
    const frame = frameWithDocument(frameDocument);

    expect(applyXxlJobEmbeddedShell(frame)).toBe(true);
    expect(applyXxlJobEmbeddedShell(frame)).toBe(true);

    expect(frameDocument.documentElement.classList.contains("test-agent-xxl-embedded")).toBe(true);
    const links = frameDocument.querySelectorAll<HTMLLinkElement>("#test-agent-xxl-embedded-shell-styles");
    expect(links).toHaveLength(1);
    expect(links[0]?.getAttribute("href")).toBe(XXL_JOB_EMBEDDED_STYLESHEET_PATH);

    const account = frameDocument.querySelector<HTMLAnchorElement>(".navbar-custom-menu .dropdown-toggle");
    expect(account?.hasAttribute("href")).toBe(false);
    expect(account?.hasAttribute("data-toggle")).toBe(false);
    expect(account?.hasAttribute("aria-expanded")).toBe(false);
    expect(account?.getAttribute("role")).toBe("status");
    expect(frameDocument.querySelector(".navbar-custom-menu .dropdown-menu")).toBeNull();
    expect(frameDocument.querySelector(".navbar-custom-menu .caret")).toBeNull();
  });

  it("skips SSO and inaccessible documents without changing their markup", () => {
    const ssoDocument = document.implementation.createHTMLDocument("XXL-JOB 登录完成");
    ssoDocument.body.textContent = "登录完成";
    expect(applyXxlJobEmbeddedShell(frameWithDocument(ssoDocument))).toBe(false);
    expect(ssoDocument.documentElement.classList.contains("test-agent-xxl-embedded")).toBe(false);

    const inaccessibleFrame = {} as HTMLIFrameElement;
    Object.defineProperty(inaccessibleFrame, "contentDocument", {
      get() {
        throw new DOMException("Blocked", "SecurityError");
      }
    });
    expect(applyXxlJobEmbeddedShell(inaccessibleFrame)).toBe(false);
  });

  it("keeps the clicked horizontal menu item inside the visible scroll area", () => {
    const frameDocument = xxlShellDocument();
    const item = frameDocument.querySelector<HTMLElement>(".sidebar-menu .nav-click")!;
    const scrollIntoView = vi.fn();
    Object.defineProperty(item, "scrollIntoView", { value: scrollIntoView });
    applyXxlJobEmbeddedShell(frameWithDocument(frameDocument));

    frameDocument.querySelector<HTMLElement>(".J_menuItem")!.click();

    expect(scrollIntoView).toHaveBeenCalledWith({ block: "nearest", inline: "nearest" });
  });

  it("decorates the shell on iframe load without treating load as SSO readiness", async () => {
    vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(() => undefined);
    const view = renderPanel(api());
    const iframe = await view.findByTitle("XXL-JOB 定时任务管理") as HTMLIFrameElement;
    const frameDocument = iframe.contentDocument!;
    frameDocument.documentElement.innerHTML = xxlShellDocument().documentElement.innerHTML;

    await fireEvent.load(iframe);

    expect(frameDocument.documentElement.classList.contains("test-agent-xxl-embedded")).toBe(true);
    expect(view.queryByText("XXL-JOB 控制台已连接")).toBeNull();
  });

  it("requests a fresh ticket when the embedded console is reloaded", async () => {
    const submit = vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(() => undefined);
    const issue = vi.fn()
      .mockResolvedValueOnce(ticket("ticket-one"))
      .mockResolvedValueOnce(ticket("ticket-two"));
    const view = renderPanel(api(issue));

    await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    await fireEvent.click(view.getByRole("button", { name: "重新加载" }));

    await waitFor(() => expect(issue).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
    expect((view.container.querySelector('input[name="ticket"]') as HTMLInputElement).value).toBe("ticket-two");
  });

  it("shows permission and Admin availability states", async () => {
    const forbidden = Object.assign(new Error("forbidden"), { status: 403 });
    const unavailable = Object.assign(new Error("unavailable"), { status: 503 });
    const issue = vi.fn().mockRejectedValueOnce(forbidden).mockRejectedValueOnce(unavailable);
    const view = renderPanel(api(issue));

    expect(await view.findByText("当前账号无定时任务管理权限")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "重试" }));
    expect(await view.findByText("XXL-JOB 管理服务暂不可用")).toBeTruthy();
  });

  it("maps iframe session expiry messages and removes the console after platform logout", async () => {
    vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(() => undefined);
    const view = renderPanel(api());
    await waitFor(() => expect(view.getByTitle("XXL-JOB 定时任务管理")).toBeTruthy());

    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      data: { type: "test-agent-xxl-job-sso", status: "expired" }
    }));
    expect((await view.findAllByText("平台会话已失效")).length).toBeGreaterThanOrEqual(1);

    await view.rerender({ currentUser: regularUser });
    expect(view.queryByTitle("XXL-JOB 定时任务管理")).toBeNull();
    expect(view.getByText("当前账号无定时任务管理权限")).toBeTruthy();
  });
});
