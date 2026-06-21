<script setup lang="ts">
import { BackendApiClient, createBackendApiClient } from "@test-agent/backend-api";
import { Button } from "@test-agent/ui-kit";
import { Input } from "@test-agent/ui-kit";
import { useQueryClient } from "@tanstack/vue-query";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { resolveLoginRedirect } from "../router";
import { useAuthStore } from "../stores/authStore";

const router = useRouter();
const authStore = useAuthStore();
const queryClient = useQueryClient();

// 创建 API client（不需要 token，登录接口可匿名访问）
const api: BackendApiClient = createBackendApiClient();

const username = ref("888888888");
const password = ref("123456");
const error = ref("");
const loading = ref(false);

/**
 * 登录成功后跳转的目标路径（从 query.redirect 读取）。
 */
const redirectPath = ref("/");

onMounted(() => {
  redirectPath.value = resolveLoginRedirect(router.currentRoute.value.query.redirect);
});

/**
 * 执行登录。
 */
async function handleLogin() {
  error.value = "";

  if (!username.value.trim() || !password.value.trim()) {
    error.value = "请输入用户名和密码";
    return;
  }

  loading.value = true;
  try {
    await authStore.login(api, username.value.trim(), password.value);
    // 清除旧缓存的 query 数据
    queryClient.clear();
    // 跳转到原页面或首页
    await router.push(redirectPath.value);
  } catch (err: unknown) {
    if (err && typeof err === "object" && "message" in err) {
      error.value = (err as { message: string }).message;
    } else {
      error.value = "登录失败，请重试";
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <!-- 背景 -->
  <div class="flex h-screen w-full items-center justify-center bg-[var(--ta-bg)]">
    <div class="flex w-80 flex-col gap-6">
      <!-- 标题 -->
      <div class="flex flex-col gap-1 text-center">
        <h1 class="text-base font-semibold text-[var(--ta-text)]">智能测试代理平台</h1>
        <p class="text-xs text-[var(--ta-muted)]">请登录以继续</p>
      </div>

      <!-- 登录表单 -->
      <form class="flex flex-col gap-3" @submit.prevent="handleLogin">
        <Input
          v-model="username"
          placeholder="用户名"
          type="text"
          autocomplete="username"
        />
        <Input
          v-model="password"
          placeholder="密码"
          type="password"
          autocomplete="current-password"
        />

        <!-- 错误提示 -->
        <p
          v-if="error"
          class="rounded border border-[#9e3b34] bg-[#f4e3e1] px-3 py-2 text-xs text-[#c2413f]"
        >
          {{ error }}
        </p>

        <!-- 登录按钮 -->
        <Button
          variant="primary"
          size="md"
          :disabled="loading"
          class="w-full"
          type="submit"
        >
          {{ loading ? "登录中..." : "登录" }}
        </Button>
      </form>
    </div>
  </div>
</template>
