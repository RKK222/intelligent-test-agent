<script setup lang="ts">
import { computed } from "vue";

const appVersion = "0.1.0";
const protocol = computed(() => `v1`);
const lastUpdated = computed(() => new Date().toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric" }));
</script>

<template>
  <div class="ta-about">
    <div class="ta-about-hero">
      <div class="ta-about-logo">M</div>
      <div class="ta-about-meta">
        <h3 class="ta-about-name">MIMO 测试智能体</h3>
        <p class="ta-about-version">版本 {{ appVersion }} · 协议 {{ protocol }} · 更新于 {{ lastUpdated }}</p>
      </div>
    </div>

    <el-descriptions class="ta-about-table" :column="1" border>
      <el-descriptions-item label="产品代号">MIMO Agent Web</el-descriptions-item>
      <el-descriptions-item label="前端框架">Vue 3 + Vite</el-descriptions-item>
      <el-descriptions-item label="UI 组件">Element Plus 2.12</el-descriptions-item>
      <el-descriptions-item label="状态管理">Pinia</el-descriptions-item>
      <el-descriptions-item label="数据请求">TanStack Vue Query</el-descriptions-item>
      <el-descriptions-item label="SSE 客户端">@test-agent/event-stream-client</el-descriptions-item>
    </el-descriptions>

    <p class="ta-about-footer">
      本产品仅供内部测试与研发协作使用，所有数据与 AI 推理结果均来自用户配置的本地或远端后端服务。
    </p>
  </div>
</template>

<style scoped>
.ta-about {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 640px;
}

.ta-about-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: linear-gradient(135deg, #f0f4ff 0%, #ffffff 100%);
}

.ta-about-logo {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #3366ff;
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.ta-about-name {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: #18181b;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.ta-about-version {
  margin: 0;
  font-size: 12px;
  color: #606266;
}

.ta-about-footer {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}
</style>
