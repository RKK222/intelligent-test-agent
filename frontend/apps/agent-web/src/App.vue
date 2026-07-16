<script setup lang="ts">
import { ElConfigProvider } from "element-plus";
import { zhCnWithArabicMonths } from "./utils/locale";
import { useAuthStore } from "./stores/authStore";
import { watch, onMounted } from "vue";
import { RouterView, useRouter } from "vue-router";
import { jumpAam } from "./utils/aamLogin";

const AAM_BASE_URL = import.meta.env.VITE_AAM_BASE_URL ?? "http://zfw.sdc.cs.enterprise/aam/login2//";
const APP_ENV = import.meta.env.VITE_ENV ?? "";
const IS_LOCAL_ENV = APP_ENV === "localhost";

const authStore = useAuthStore();
const router = useRouter();

onMounted(() => {
  const TOKEN_KEY = "test-agent.auth.token";
  const storedToken = sessionStorage.getItem(TOKEN_KEY);
  if (storedToken && !authStore.token) {
    authStore.saveToken(storedToken);
  }
});

watch(
  () => authStore.token,
  (newToken) => {
    if (!newToken && router.currentRoute.value.name !== "login") {
      if (IS_LOCAL_ENV) {
        router.replace({ name: "login" });
      } else {
        jumpAam(window.location.href, AAM_BASE_URL);
      }
    }
  }
);

/**
 * 监听全局未认证事件（在 backend-api 的 catch 中触发）。
 * 任何组件遇到 401 错误时可调用此方法。
 */
function handleUnauthorized() {
  console.log('>>>>>>>>>>登录校验>>>>>>>>>>',AAM_BASE_URL)
  authStore.clearAuth();
  if (IS_LOCAL_ENV) {
    router.replace({ name: "login" });
  } else {
    jumpAam(window.location.href, AAM_BASE_URL);
  }
}

// 暴露到 window 供非 Vue 上下文使用
(window as unknown as Record<string, unknown>).__handleUnauthorized = handleUnauthorized;
</script>

<template>
  <el-config-provider :locale="zhCnWithArabicMonths">
    <RouterView />
  </el-config-provider>
</template>
