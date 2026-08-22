import { createRouter, createWebHistory } from "vue-router";
import { createBackendApiClient } from "@test-agent/backend-api";
import {
  isReleaseFeaturePathEnabled,
  releaseFeatures,
  type ReleaseFeatureFlags
} from "./release-features";
import { jumpAam } from "./utils/aamLogin";
import { useAuthStore } from "./stores/authStore";
import { useMemoryAccessStore } from "./stores/memoryAccessStore";

const TOKEN_KEY = "test-agent.auth.token";
const UNIFIED_AUTH_ID_KEY = "test-agent.auth.unifiedAuthId";
const DEFAULT_WORKBENCH_PATH = "/workbench";

const AAM_BASE_URL = import.meta.env.VITE_AAM_BASE_URL ?? "http://zfw.sdc.cs.icbc/aam/login/";
const API_BASE_URL = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const memoryAccessApi = createBackendApiClient({ baseUrl: API_BASE_URL });

/**
 * 当前环境标识：localhost 表示本地开发模式，其他值走 AAM 统一认证。
 * 通过环境变量 VITE_ENV 控制。
 */
const APP_ENV = import.meta.env.VITE_ENV ?? "";
const IS_LOCAL_ENV = APP_ENV === "localhost";

/**
 * 旧的无尾斜杠地址仅负责跳转到独立 iframe 入口。
 * 不再从主工作台路由懒加载导入页，避免 Rollup 将工作台 UI 依赖合并进轻量入口。
 */
const RequirementImportRedirectView = {
  name: "RequirementImportRedirectView",
  created() {
    window.location.replace("/workspace-requirement-import/");
  },
  render: () => null,
};

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
      redirect: { name: "workbench" },
    },
    {
      path: DEFAULT_WORKBENCH_PATH,
      name: "workbench",
      component: () => import("./views/WorkbenchView.vue"),
    },
    {
      path: "/toolbox",
      name: "toolbox",
      component: () => import("./views/WorkbenchView.vue"),
    },
    {
      path: "/memories",
      name: "memories",
      component: () => import("./views/WorkbenchView.vue"),
    },
    {
      path: "/system",
      name: "system",
      component: () => import("./views/WorkbenchView.vue"),
    },
    {
      path: "/hub",
      name: "hub",
      component: () => import("./views/WorkbenchView.vue"),
    },
    {
      path: "/settings",
      name: "settings",
      component: () => import("./views/WorkbenchView.vue"),
    },
    {
      path: "/traces",
      name: "traces",
      component: () => import("./views/TraceView.vue"),
    },
    {
      path: "/lobehub/launch",
      name: "lobehub-launch",
      component: () => import("./views/LobehubLaunchView.vue"),
    },
    {
      path: "/workspace-requirement-import",
      name: "workspace-requirement-import",
      strict: true,
      component: RequirementImportRedirectView,
    },
    {
      path: "/s/:shareId",
      name: "transcript",
      component: () => import("./views/SharedWorkbenchView.vue"),
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

export function resolveLoginRedirect(
  rawRedirect: unknown,
  features: ReleaseFeatureFlags = releaseFeatures
): string {
  if (typeof rawRedirect !== "string") {
    return DEFAULT_WORKBENCH_PATH;
  }

  const redirect = rawRedirect.trim();
  if (redirect.length === 0 || redirect.startsWith("//")) {
    return DEFAULT_WORKBENCH_PATH;
  }

  let target: URL;
  try {
    target = new URL(redirect, LOGIN_REDIRECT_BASE_URL);
  } catch {
    return DEFAULT_WORKBENCH_PATH;
  }

  if (target.origin !== LOGIN_REDIRECT_BASE_URL || target.pathname === "/985211") {
    return DEFAULT_WORKBENCH_PATH;
  }

  if (!isKnownLoginRedirectPath(target.pathname, features)) {
    return DEFAULT_WORKBENCH_PATH;
  }

  // LobeHub 只能恢复到固定 launch 路由，任何查询或片段都不参与回跳。
  if (target.pathname === "/lobehub/launch") {
    return target.pathname;
  }

  // 根路径只作为旧入口兼容，登录后统一落到可识别的工作台 URI。
  if (target.pathname === "/") {
    return `${DEFAULT_WORKBENCH_PATH}${target.search}${target.hash}`;
  }

  return `${target.pathname}${target.search}${target.hash}`;
}

function isKnownLoginRedirectPath(pathname: string, features: ReleaseFeatureFlags): boolean {
  return pathname === "/"
    || /^\/workbench\/?$/.test(pathname)
    || /^\/toolbox\/?$/.test(pathname)
    || /^\/memories\/?$/.test(pathname)
    || /^\/system\/?$/.test(pathname)
    || /^\/hub\/?$/.test(pathname)
    || /^\/settings\/?$/.test(pathname)
    || /^\/traces\/?$/.test(pathname)
    || pathname === "/workspace-requirement-import"
    || (pathname === "/lobehub/launch" && features.lobehub)
    || /^\/s\/[^/]+$/.test(pathname);
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

  if (!isReleaseFeaturePathEnabled(to.path)) {
    return { name: "workbench", replace: true };
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
      return { path: "/985211", query: { redirect: to.fullPath }, replace: true };
    }
    // 非 localhost：任何页面（包括 404）都先走 AAM 登录
    jumpAam(window.location.href, AAM_BASE_URL);
    return false;
  }

  if (!authStore.token) {
    authStore.saveToken(token);
  }

  if (to.name === "memories") {
    // 直达记忆路由必须在工作台挂载前重新确认总开关与灰度授权；异常时同样失败关闭。
    const memoryAllowed = await useMemoryAccessStore().refresh(memoryAccessApi, authStore.token);
    if (!memoryAllowed) {
      return { name: "workbench", replace: true };
    }
  }

  if (to.name === "traces") {
    // 菜单隐藏不是权限边界；直达路由先刷新当前角色，后台接口还会再次执行 SUPER_ADMIN 强校验。
    const currentUser = authStore.currentUser ?? await authStore.fetchCurrentUser(memoryAccessApi);
    if (!currentUser?.roles?.includes("SUPER_ADMIN")) {
      return { name: "workbench", replace: true };
    }
  }

  return true;
});
