import ElementPlus from "element-plus";
import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { createPinia } from "pinia";
import { createApp } from "vue";
import dayjs from "dayjs";
import "dayjs/locale/zh-cn";
import App from "./App.vue";
import { router } from "./router";
import "./styles/globals.css";
import "element-plus/dist/index.css";
import "./styles/element-overrides.css";

// 把 dayjs 全局 locale 切到中文，让 Element Plus 的 el-date-picker（包括 type="month"）
// 在面板里显示"1月、2月、…、12月"而不是默认英文 "Jan、Feb、…、Dec"。
// 项目里没有其他直接 dayjs 用法，所以全局切换是安全的。
dayjs.locale("zh-cn");

// 全局 QueryClient 单例，供 useQueryClient 读取
const queryClient = new QueryClient();

const app = createApp(App);
app.use(createPinia());
app.use(VueQueryPlugin, { queryClient });
app.use(router);
app.use(ElementPlus);
app.mount("#app");
