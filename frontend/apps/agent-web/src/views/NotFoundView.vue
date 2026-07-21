<script setup lang="ts">
import { Button } from "@test-agent/ui-kit";
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<template>
  <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 24px;">
    <div style="text-align: center;">
      <div style="font-size: 96px; font-weight: bold; color: #909399; margin-bottom: 16px;">404</div>
      <div style="font-size: 18px; color: #606266; margin-bottom: 32px;">没有这个页面</div>
      <Button type="primary" @click="router.push('/')">返回首页</Button>
    </div>
  </div>
</template>
