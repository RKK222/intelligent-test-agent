import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { createPinia } from "pinia";
import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./router";
import "./styles/globals.css";

// 全局 QueryClient 单例，供 useQueryClient 读取
const queryClient = new QueryClient();

createApp(App).use(createPinia()).use(VueQueryPlugin, { queryClient }).use(router).mount("#app");
