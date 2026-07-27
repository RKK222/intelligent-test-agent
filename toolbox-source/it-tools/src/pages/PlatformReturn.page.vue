<script setup lang="ts">
import { onMounted } from 'vue';

onMounted(() => window.location.replace('/toolbox'));
</script>

<template>
  <main class="platform-return">
    <h1>工具不可用</h1>
    <p>该地址不是当前离线目录中的工具，正在返回平台工具盒子。</p>
    <a href="/toolbox">返回工具盒子</a>
  </main>
</template>

<style scoped>
.platform-return {
  display: grid;
  min-height: 100vh;
  place-content: center;
  gap: 10px;
  padding: 24px;
  color: #172033;
  font-family: system-ui, sans-serif;
  text-align: center;
  background: #f3f5f7;
}
.platform-return h1,
.platform-return p { margin: 0; }
.platform-return a { color: #315f9b; }
</style>
