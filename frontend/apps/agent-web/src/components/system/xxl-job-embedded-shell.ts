export const XXL_JOB_EMBEDDED_STYLESHEET_PATH =
  "/xxl-job-admin/static/platform/xxl-job-embedded-shell.css";

const ROOT_CLASS = "test-agent-xxl-embedded";
const STYLESHEET_ID = "test-agent-xxl-embedded-shell-styles";
const MENU_BOUND_ATTRIBUTE = "data-test-agent-embedded-scroll";

/**
 * 只装饰同源 iframe 中的 XXL 外层 shell；SSO 中转页、网关错误页和跨域文档保持原样。
 */
export function applyXxlJobEmbeddedShell(frame: HTMLIFrameElement): boolean {
  try {
    const frameDocument = frame.contentDocument;
    if (!isXxlShell(frameDocument)) {
      return false;
    }

    frameDocument.documentElement.classList.add(ROOT_CLASS);
    ensureStylesheet(frameDocument);
    makeAccountReadOnly(frameDocument);
    bindMenuScroll(frameDocument);
    return true;
  } catch {
    // 同源代理异常或未来部署边界变化时保持 XXL 原页面可用，不影响现有 SSO 状态机。
    return false;
  }
}

function isXxlShell(frameDocument: Document | null): frameDocument is Document {
  return frameDocument !== null
    && frameDocument.querySelector(".wrapper > .main-header") !== null
    && frameDocument.querySelector(".wrapper > .main-sidebar") !== null
    && frameDocument.querySelector(".wrapper > .content-wrapper") !== null;
}

function ensureStylesheet(frameDocument: Document) {
  if (frameDocument.getElementById(STYLESHEET_ID)) {
    return;
  }
  const stylesheet = frameDocument.createElement("link");
  stylesheet.id = STYLESHEET_ID;
  stylesheet.rel = "stylesheet";
  stylesheet.href = XXL_JOB_EMBEDDED_STYLESHEET_PATH;
  frameDocument.head.append(stylesheet);
}

function makeAccountReadOnly(frameDocument: Document) {
  const account = frameDocument.querySelector<HTMLAnchorElement>(
    ".main-header .navbar-custom-menu .dropdown-toggle"
  );
  if (!account) {
    return;
  }

  const accountLabel = account.textContent?.replace(/\s+/g, " ").trim() ?? "";
  account.removeAttribute("href");
  account.removeAttribute("data-toggle");
  account.removeAttribute("aria-expanded");
  account.setAttribute("role", "status");
  account.tabIndex = -1;
  if (accountLabel) {
    account.title = accountLabel;
    account.setAttribute("aria-label", accountLabel);
  }
  account.querySelector(".caret")?.remove();
  account.closest("li")?.querySelector(".dropdown-menu")?.remove();
}

function bindMenuScroll(frameDocument: Document) {
  const menu = frameDocument.querySelector<HTMLElement>(".main-sidebar .sidebar-menu");
  if (!menu || menu.hasAttribute(MENU_BOUND_ATTRIBUTE)) {
    return;
  }
  menu.setAttribute(MENU_BOUND_ATTRIBUTE, "true");
  menu.addEventListener("click", (event) => {
    const eventElement = event.target as { closest?: (selectors: string) => Element | null } | null;
    const menuLink = eventElement?.closest?.(".J_menuItem");
    const menuItem = menuLink?.closest("li") as HTMLElement | null;
    if (typeof menuItem?.scrollIntoView === "function") {
      menuItem.scrollIntoView({ block: "nearest", inline: "nearest" });
    }
  });
}
