<script setup lang="ts">
import { ElConfigProvider } from "element-plus";
import { zhCnWithArabicMonths } from "./utils/locale";
import { useAuthStore } from "./stores/authStore";
import { watch } from "vue";
import { RouterView, useRouter } from "vue-router";

const authStore = useAuthStore();
const router = useRouter();

// 监听 Token 变化，Token 被清除时自动跳转到登录页
watch(
  () => authStore.token,
  (newToken) => {
    if (!newToken && router.currentRoute.value.name !== "login") {
      router.push({ name: "login", query: { redirect: router.currentRoute.value.fullPath } });
    }
  }
);

/**
 * 监听全局未认证事件（在 backend-api 的 catch 中触发）。
 * 任何组件遇到 401 错误时可调用此方法。
 */
function handleUnauthorized() {
  authStore.clearAuth();
  router.push({ name: "login", query: { redirect: router.currentRoute.value.fullPath } });
}

// 暴露到 window 供非 Vue 上下文使用
(window as unknown as Record<string, unknown>).__handleUnauthorized = handleUnauthorized;
</script>

<template>
  <el-config-provider :locale="zhCnWithArabicMonths">
    <RouterView />
  </el-config-provider>
</template>
