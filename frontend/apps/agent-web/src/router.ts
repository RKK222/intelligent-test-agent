import { createRouter, createWebHistory } from "vue-router";

// SPA 客户端路由：/ 工作台、/s/:sessionId 只读 transcript
export const router = createRouter({
  history: createWebHistory(),
  routes: [
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
