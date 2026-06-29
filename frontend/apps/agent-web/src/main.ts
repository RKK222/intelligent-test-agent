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
