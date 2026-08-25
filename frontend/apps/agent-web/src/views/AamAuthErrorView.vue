<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { normalizeAamRetryPath } from "../auth/aamAuth";
import { jumpAam, resolveAamLoginBaseUrl } from "../utils/aamLogin";

const route = useRoute();
const AAM_BASE_URL = resolveAamLoginBaseUrl(import.meta.env.VITE_AAM_BASE_URL);
const retryPath = computed(() => normalizeAamRetryPath(route.query.retry));
const malformed = computed(() => route.query.reason === "malformed");

function retryLogin() {
  const target = new URL(retryPath.value, window.location.origin).toString();
  jumpAam(target, AAM_BASE_URL);
}
</script>

<template>
  <main class="aam-error-shell">
    <section class="aam-error-card" aria-labelledby="aam-error-title">
      <div class="status-mark" aria-hidden="true">!</div>
      <p class="eyebrow">SECURE SIGN-IN</p>
      <h1 id="aam-error-title">统一认证暂未完成</h1>
      <p class="description">
        {{ malformed
          ? "登录回调参数不完整或重复，系统已清除地址栏中的敏感信息。"
          : "统一认证服务暂时不可用，系统没有保留本次登录凭据。" }}
      </p>
      <p class="hint">请稍后手工重试。页面不会自动跳转，以免形成登录循环。</p>
      <button type="button" @click="retryLogin">重新进入统一认证</button>
    </section>
  </main>
</template>

<style scoped>
.aam-error-shell {
  --ink: #15201d;
  --paper: #f4f0e5;
  --signal: #d55331;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  color: var(--ink);
  background:
    radial-gradient(circle at 18% 18%, rgba(213, 83, 49, 0.16), transparent 34%),
    linear-gradient(135deg, #dce7df 0%, var(--paper) 48%, #e9dccb 100%);
}

.aam-error-card {
  width: min(560px, 100%);
  padding: clamp(32px, 7vw, 64px);
  border: 1px solid rgba(21, 32, 29, 0.18);
  border-radius: 28px 8px 28px 8px;
  background: rgba(255, 253, 247, 0.88);
  box-shadow: 0 28px 80px rgba(45, 54, 48, 0.16);
}

.status-mark {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fffaf1;
  background: var(--signal);
  font: 700 30px/1 Georgia, serif;
}

.eyebrow {
  margin: 28px 0 10px;
  color: var(--signal);
  font: 700 12px/1.2 "Avenir Next", "Trebuchet MS", sans-serif;
  letter-spacing: 0.2em;
}

h1 {
  margin: 0;
  font: 700 clamp(30px, 6vw, 46px)/1.12 Georgia, "Songti SC", serif;
}

.description,
.hint {
  font-family: "Avenir Next", "PingFang SC", sans-serif;
  line-height: 1.75;
}

.description {
  margin: 24px 0 0;
  font-size: 17px;
}

.hint {
  margin: 10px 0 30px;
  color: rgba(21, 32, 29, 0.68);
  font-size: 14px;
}

button {
  width: 100%;
  min-height: 48px;
  border: 0;
  border-radius: 12px 4px 12px 4px;
  color: #fffaf1;
  background: var(--ink);
  font: 700 15px/1 "Avenir Next", "PingFang SC", sans-serif;
  cursor: pointer;
}

button:focus-visible {
  outline: 3px solid rgba(213, 83, 49, 0.45);
  outline-offset: 3px;
}
</style>
