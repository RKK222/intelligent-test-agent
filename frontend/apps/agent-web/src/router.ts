import { createRouter, createWebHistory } from "vue-router";

/**
 * Token 在 localStorage 中的存储 key，与 authStore 保持一致。
 */
const TOKEN_KEY = "test-agent.auth.token";

// SPA 客户端路由：/ 工作台、/s/:sessionId 只读 transcript、/login 登录页
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("./views/LoginView.vue")
    },
    {
      path: "/",
      name: "workbench",
      component: () => import("./views/WorkbenchView.vue")
    },
    {
      path: "/s/:sessionId",
      name: "transcript",
      component: () => import("./views/TranscriptView.vue"),
      props: true
    }
  ]
});

/**
 * 全局前置守卫：检查用户是否已登录，未登录时跳转登录页。
 * 登录页和勿需登录的路径直接放行。
 */
router.beforeEach((to, _from) => {
  // 登录页不需要鉴权
  if (to.name === "login") {
    return true;
  }

  // 检查是否有 Token
  const token = localStorage.getItem(TOKEN_KEY);
  if (!token) {
    // 未登录，跳转到登录页并记录原始路径
    return { name: "login", query: { redirect: to.fullPath } };
  }

  return true;
});
