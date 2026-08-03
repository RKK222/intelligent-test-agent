<script setup lang="ts">
import { WorkflowApiClient } from "@test-agent/workflow-api-client";
import { WorkflowChat } from "@test-agent/workflow-chat";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/authStore";

// 该路由组件本身按需加载，因此 TDesign Chat 与工作流主题样式不会进入现有工作台首屏。
import "tdesign-vue-next/es/style/index.css";
import "@test-agent/workflow-chat/tdesign-chat.css";
import "@test-agent/workflow-chat/workflow-chat.css";

const authStore = useAuthStore();
const router = useRouter();

const api = new WorkflowApiClient({
  token: () => authStore.token,
  onUnauthorized: () => authStore.clearAuth(),
});
</script>

<template>
  <WorkflowChat :api="api" @back="router.push({ name: 'workbench' })" />
</template>
