import { createApp } from "vue";
import RequirementImportView from "./views/RequirementImportView.vue";
import "./styles/requirement-import-shell.css";

const AUTH_TOKEN_KEY = "test-agent.auth.token";

/** 独立 iframe 入口只在同一登录会话内挂载，避免直接访问时触发外部查询。 */
if (!sessionStorage.getItem(AUTH_TOKEN_KEY)) {
  window.parent.postMessage({ type: "ITA_REQUIREMENT_IMPORT_AUTH_REQUIRED" }, window.location.origin);
  document.querySelector<HTMLDivElement>("#app")!.textContent = "登录状态已失效，请返回工作台重新登录。";
} else {
  // backend-api 在 iframe 内遇到 401 时通知已登录的父工作台执行统一退出流程。
  (window as unknown as Record<string, unknown>).__handleUnauthorized = () => {
    window.parent.postMessage({ type: "ITA_REQUIREMENT_IMPORT_AUTH_REQUIRED" }, window.location.origin);
  };
  createApp(RequirementImportView).mount("#app");
}
