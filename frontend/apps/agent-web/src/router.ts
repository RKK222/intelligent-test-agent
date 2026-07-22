import { createRouter, createWebHistory } from "vue-router";
import { jumpAam } from "./utils/aamLogin";
import { useAuthStore } from "./stores/authStore";

const TOKEN_KEY = "test-agent.auth.token";
const UNIFIED_AUTH_ID_KEY = "test-agent.auth.unifiedAuthId";

const AAM_BASE_URL = import.meta.env.VITE_AAM_BASE_URL ?? "http://zfw.sdc.cs.icbc/aam/login/";

/**
 * 当前环境标识：localhost 表示本地开发模式，其他值走 AAM 统一认证。
 * 通过环境变量 VITE_ENV 控制。
 */
const APP_ENV = import.meta.env.VITE_ENV ?? "";
const IS_LOCAL_ENV = APP_ENV === "localhost";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/985211",
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
      component: () => import("./views/NotFoundView.vue"),
    },
  ],
});

const LOGIN_REDIRECT_BASE_URL = "http://test-agent.local";

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

  if (target.origin !== LOGIN_REDIRECT_BASE_URL || target.pathname === "/985211") {
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

router.beforeEach(async (to, _from) => {
  const authStore = useAuthStore();

  // 统一认证登录：URL 携带 userId + token 时，先完成登录再继续路由
  const unifiedAuthId = to.query.userId;
  const urlToken = to.query.token;
  if (unifiedAuthId && typeof unifiedAuthId === "string" && urlToken && typeof urlToken === "string") {
    const { token: _, userId: __, SSIAuth: ___, SSISign: ____, ...restQuery } = to.query;
    try {
      const baseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
      const response = await fetch(`${baseUrl}/api/auth/login-by-unified-auth`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ unifiedAuthId, token: urlToken }),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      if (data.data && data.data.token) {
        authStore.saveToken(data.data.token);
        sessionStorage.setItem(UNIFIED_AUTH_ID_KEY, unifiedAuthId);
      }
    } catch (error) {
      console.error("统一认证登录失败:", error);
    }
    return { path: to.path, query: restQuery, replace: true };
  }

  if (to.name === "login") {
    // 登录页仅在本地开发环境可用；非 localhost 环境下一律走 AAM 统一认证，
    // 避免任何不走 AAM 的进入方式（包括主动输入 URL）进入登录页。
    if (!IS_LOCAL_ENV) {
      jumpAam(window.location.href, AAM_BASE_URL);
      return false;
    }
    return true;
  }

  const token = sessionStorage.getItem(TOKEN_KEY);
  if (!token) {
    if (IS_LOCAL_ENV) {
      // localhost 下 404 页面可以直接看，其他页面跳登录
      if (to.name === "not-found") {
        return true;
      }
      return { path: "/985211", replace: true };
    }
    // 非 localhost：任何页面（包括 404）都先走 AAM 登录
    jumpAam(window.location.href, AAM_BASE_URL);
    return false;
  }

  if (!authStore.token) {
    authStore.saveToken(token);
  }

  return true;
});
