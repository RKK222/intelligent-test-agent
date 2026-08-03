import type { BackendApiClient } from "@test-agent/backend-api";
import type { LobehubSsoTicket } from "@test-agent/shared-types";

type TicketApi = Pick<BackendApiClient, "createLobehubSsoTicket">;

type LobehubPopup = {
  close: () => void;
  document: Document;
  opener: unknown;
};

type LaunchEnvironment = {
  document: Document;
  openWindow: (url: string, target: string) => LobehubPopup | null;
  targetName: () => string;
};

/**
 * 用户点击时同步创建空白标签页，随后才发起异步换票，避免被浏览器弹窗策略拦截。
 */
export async function launchLobehubInNewTab(
  api: TicketApi,
  overrides: Partial<LaunchEnvironment> = {}
): Promise<void> {
  const environment = launchEnvironment(overrides);
  const target = environment.targetName();
  const popup = environment.openWindow("about:blank", target);
  if (!popup) {
    throw new Error("浏览器阻止了通用问答标签页，请允许本站打开新窗口");
  }
  // 先保留同源 about:blank 的 document 引用，再切断 opener；后续让弹窗自行 POST，
  // 避免依赖命名窗口查找导致票据已签发却没有任何页面接收。
  const popupDocument = popup.document;
  // 提交到跨域页面前切断 opener，降低反向标签页劫持风险。
  popup.opener = null;

  try {
    const issue = await api.createLobehubSsoTicket();
    submitLobehubTicket(popupDocument, issue, "_self");
  } catch (error) {
    popup.close();
    throw error;
  }
}

/** 直接访问聊天域名回到固定 launch 路由时，在当前标签页完成交接。 */
export async function launchLobehubInCurrentTab(
  api: TicketApi,
  targetDocument: Document = document
): Promise<void> {
  const issue = await api.createLobehubSsoTicket();
  submitLobehubTicket(targetDocument, issue, "_self");
}

/** 票据只存在于短生命周期 DOM 表单中，禁止写入 URL、浏览器存储或日志。 */
function submitLobehubTicket(targetDocument: Document, issue: LobehubSsoTicket, target: string): void {
  const action = validateConsumeUrl(issue.consumeUrl);
  if (typeof issue.ticket !== "string" || issue.ticket.length === 0) {
    throw new Error("LobeHub 登录票据无效");
  }

  const form = targetDocument.createElement("form");
  form.method = "post";
  form.action = action;
  form.target = target;
  form.autocomplete = "off";
  form.style.display = "none";

  const ticket = targetDocument.createElement("input");
  ticket.type = "hidden";
  ticket.name = "ticket";
  ticket.value = issue.ticket;
  form.appendChild(ticket);
  targetDocument.body.appendChild(form);
  try {
    form.submit();
  } finally {
    ticket.value = "";
    form.remove();
  }
}

function validateConsumeUrl(rawUrl: string): string {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    throw new Error("LobeHub 登录地址无效");
  }
  if ((url.protocol !== "http:" && url.protocol !== "https:")
    || url.username.length > 0
    || url.password.length > 0
    || url.pathname !== "/api/auth/platform/consume"
    || url.search.length > 0
    || url.hash.length > 0) {
    throw new Error("LobeHub 登录地址无效");
  }
  return url.toString();
}

function launchEnvironment(overrides: Partial<LaunchEnvironment>): LaunchEnvironment {
  return {
    document: overrides.document ?? document,
    openWindow: overrides.openWindow ?? ((url, target) => window.open(url, target) as LobehubPopup | null),
    targetName: overrides.targetName ?? (() => {
      const suffix = typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID().replaceAll("-", "")
        : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
      return `test-agent-lobehub-${suffix}`;
    })
  };
}
