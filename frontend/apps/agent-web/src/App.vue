<script setup lang="ts">
import { ElConfigProvider } from "element-plus";
import { zhCnWithArabicMonths } from "./utils/locale";
import { useAuthStore } from "./stores/authStore";
import { watch, onMounted, ref } from "vue";
import { RouterView, useRouter } from "vue-router";
import { jumpAam, resolveAamLoginBaseUrl } from "./utils/aamLogin";
import logoUrl from "./assets/figma/logo.png";

const AAM_BASE_URL = resolveAamLoginBaseUrl(import.meta.env.VITE_AAM_BASE_URL);
const APP_ENV = import.meta.env.VITE_ENV ?? "";
const IS_LOCAL_ENV = APP_ENV === "localhost";

const authStore = useAuthStore();
const router = useRouter();
const initialNavigationReady = ref(false);

/**
 * Vue Router 会在首次导航中等待异步路由组件下载完成；在此之前显式展示品牌加载态，
 * 避免重新部署后的冷缓存请求让根节点保持纯白。导航失败时也结束加载，交给路由错误处理。
 */
void router.isReady().then(
  () => {
    initialNavigationReady.value = true;
  },
  () => {
    initialNavigationReady.value = true;
  }
);

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
    if (!newToken
      && !authStore.suppressAutoLoginRedirect
      && router.currentRoute.value.name !== "login"
      && router.currentRoute.value.name !== "aam-error") {
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
    <main
      v-if="!initialNavigationReady"
      class="app-entry-loading"
      data-testid="app-entry-loading"
      role="status"
      aria-live="polite"
    >
      <div class="app-entry-loading__brand">
        <div class="app-entry-loading__lockup">
          <img :src="logoUrl" alt="" class="app-entry-loading__logo" aria-hidden="true" />
          <span class="app-entry-loading__divider" aria-hidden="true" />
          <span class="app-entry-loading__title">
            <strong>MIMO测试智能体</strong>
            <small>MIMO Intelligent Test Agent</small>
          </span>
        </div>
        <span class="app-entry-loading__signal" aria-hidden="true">
          <span />
        </span>
        <p>正在进入工作台</p>
      </div>
    </main>
    <RouterView v-else />
  </el-config-provider>
</template>

<style scoped>
.app-entry-loading {
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 24px;
  overflow: hidden;
  background: var(--ta-shell-canvas, #f7f9fc);
  color: var(--ta-shell-text, #333a48);
}

.app-entry-loading__brand {
  display: flex;
  width: min(320px, calc(100vw - 48px));
  flex-direction: column;
  align-items: center;
}

.app-entry-loading__lockup {
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-entry-loading__logo {
  width: 46px;
  height: 32px;
  object-fit: contain;
}

.app-entry-loading__divider {
  width: 1px;
  height: 28px;
  margin: 0 13px;
  background: rgba(127, 30, 43, 0.34);
}

.app-entry-loading__title {
  display: flex;
  flex-direction: column;
  gap: 3px;
  line-height: 1;
}

.app-entry-loading__title strong {
  color: var(--ta-shell-brand, #111827);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.app-entry-loading__title small {
  color: var(--ta-shell-brand-strong, #7f1e2b);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.075em;
}

.app-entry-loading__signal {
  position: relative;
  width: 176px;
  height: 2px;
  margin-top: 22px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(127, 30, 43, 0.14);
}

.app-entry-loading__signal > span {
  position: absolute;
  inset: 0 auto 0 0;
  width: 46%;
  border-radius: inherit;
  background: var(--ta-shell-brand-strong, #7f1e2b);
  animation: app-entry-signal 1.15s cubic-bezier(0.55, 0.08, 0.45, 0.92) infinite alternate;
}

.app-entry-loading p {
  margin: 10px 0 0;
  color: var(--ta-shell-muted, #6b7280);
  font-size: 12px;
  letter-spacing: 0.08em;
}

@keyframes app-entry-signal {
  from {
    transform: translateX(-4%);
  }
  to {
    transform: translateX(121%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-entry-loading__signal > span {
    animation: none;
    transform: translateX(58%);
  }
}
</style>
