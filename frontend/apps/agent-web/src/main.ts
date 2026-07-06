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

import "./utils/locale";
import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { createPinia } from "pinia";
import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./router";
import "./styles/globals.css";
import "element-plus/dist/index.css";
import "./styles/element-overrides.css";

// 全局 QueryClient 单例，供 useQueryClient 读取
const queryClient = new QueryClient();

const app = createApp(App);
app.use(createPinia());
app.use(VueQueryPlugin, { queryClient });
app.use(router);
app.mount("#app");
