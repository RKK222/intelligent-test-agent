import { createRouter, createWebHistory } from "vue-router";
import { jumpAam } from "./utils/aamLogin";

/**
 * Token 在 localStorage 中的存储 key，与 authStore 保持一致。
 */
const TOKEN_KEY = "test-agent.auth.token";

const AAM_BASE_URL = import.meta.env.VITE_AAM_BASE_URL ?? "http://zfw.sdc.cs.icbc/aam/login/";

// SPA 客户端路由：/ 工作台、/s/:sessionId 只读 transcript、/login 登录页
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("./views/LoginView.vue"),
    },
    {
      path: "/",
      name: "workbench",
      component: () => import("./views/WorkbenchView.vue"),
    },
    {
      path: "/s/:sessionId",
      name: "transcript",
      component: () => import("./views/TranscriptView.vue"),
      props: true,
    },
    {
      path: "/:pathMatch(.*)*",
      name: "not-found",
      redirect: "/",
    },
  ],
});

const LOGIN_REDIRECT_BASE_URL = "http://test-agent.local";

/**
 * 解析登录成功后的跳转目标。
 * 只允许跳回当前 SPA 内已知页面，避免旧的 /error 或外部地址让登录成功后停在空白页。
 */
export function resolveLoginRedirect(rawRedirect: unknown): string {
  if (typeof rawRedirect !== "string") {
    return "/";
  }

  const redirect = rawRedirect.trim();
  if (redirect.length === 0 || redirect.startsWith("//")) {
    return "/";
  }

  let target: URL;
  try {
    target = new URL(redirect, LOGIN_REDIRECT_BASE_URL);
  } catch {
    return "/";
  }

  if (target.origin !== LOGIN_REDIRECT_BASE_URL || target.pathname === "/login") {
    return "/";
  }

  if (!isKnownLoginRedirectPath(target.pathname)) {
    return "/";
  }

  return `${target.pathname}${target.search}${target.hash}`;
}

function isKnownLoginRedirectPath(pathname: string): boolean {
  return pathname === "/" || /^\/s\/[^/]+$/.test(pathname);
}

/**
 * 全局前置守卫：检查用户是否已登录，未登录时跳转登录页。
 * 登录页和勿需登录的路径直接放行。
 */
router.beforeEach((to, _from) => {
  // 登录页不需要鉴权
  if (to.name === "login") {
    return true;
  }

  const token = localStorage.getItem(TOKEN_KEY);
  if (!token) {
    jumpAam(window.location.href, AAM_BASE_URL);
    return false;
  }

  return true;
});
