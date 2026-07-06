<script setup lang="ts">
import { ElConfigProvider } from "element-plus";
import { zhCnWithArabicMonths } from "./utils/locale";
import { useAuthStore } from "./stores/authStore";
import { watch } from "vue";
import { RouterView, useRouter } from "vue-router";
import { jumpAam } from "./utils/aamLogin";

const AAM_BASE_URL = import.meta.env.VITE_AAM_BASE_URL ?? "http://zfw.sdc.cs.icbc/aam/login//";

const authStore = useAuthStore();
const router = useRouter();

// 监听 URL 变化，切换页面时检查登录状态，未登录则跳转 AAM 认证
watch(
  () => router.currentRoute.value.fullPath,
  () => {
    const token = authStore.token;
    if (!token && router.currentRoute.value.name !== "login") {
      jumpAam(window.location.href, AAM_BASE_URL);
    }
  }
);

/**
 * 监听全局未认证事件（在 backend-api 的 catch 中触发）。
 * 任何组件遇到 401 错误时可调用此方法。
 */
function handleUnauthorized() {
  authStore.clearAuth();
  jumpAam(window.location.href, AAM_BASE_URL);
}

// 暴露到 window 供非 Vue 上下文使用
(window as unknown as Record<string, unknown>).__handleUnauthorized = handleUnauthorized;
</script>

<template>
  <el-config-provider :locale="zhCnWithArabicMonths">
    <RouterView />
  </el-config-provider>
</template>
