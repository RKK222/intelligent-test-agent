// 屏蔽 Monaco Editor 内部由取消操作（如销毁、快速切文件）导致的未捕获 Promise Rejection
if (typeof window !== "undefined") {
  window.addEventListener("unhandledrejection", (event) => {
    if (
      event.reason &&
      (event.reason === "Canceled" ||
        event.reason.name === "Canceled" ||
        event.reason.message === "Canceled")
    ) {
      event.preventDefault();
    }
  });
}

import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import App from "@/App.vue";
import { routes } from "@/router";
import "@/styles/theme.css";

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(createPinia()).use(router).mount("#app");
