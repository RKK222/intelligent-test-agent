<script setup lang="ts">
import { ref, watch } from "vue";

const form = ref({
  language: "zh-CN",
  defaultAgent: "",
  defaultModel: "",
  autoSave: true,
  telemetry: false
});

watch(
  form,
  (value) => {
    // 通用设置变更可以由后续需求接入持久化（localStorage / 后端配置）
    if (typeof window !== "undefined") {
      window.localStorage.setItem("ta.settings.general", JSON.stringify(value));
    }
  },
  { deep: true }
);
</script>

<template>
  <el-form label-position="top" class="ta-settings-form">
    <el-form-item label="界面语言">
      <el-select v-model="form.language" placeholder="选择语言" style="width: 240px">
        <el-option label="简体中文" value="zh-CN" />
        <el-option label="English" value="en-US" />
      </el-select>
    </el-form-item>

    <el-form-item label="默认 Agent">
      <el-input v-model="form.defaultAgent" placeholder="例如：default-agent" clearable />
    </el-form-item>

    <el-form-item label="默认模型">
      <el-input v-model="form.defaultModel" placeholder="例如：openai/gpt-4o" clearable />
    </el-form-item>

    <el-form-item label="文件保存">
      <el-switch v-model="form.autoSave" />
      <span class="ta-settings-form-hint">启用后会在空闲时自动保存编辑器内容</span>
    </el-form-item>

    <el-form-item label="匿名使用统计">
      <el-switch v-model="form.telemetry" />
      <span class="ta-settings-form-hint">帮助我们改进产品体验</span>
    </el-form-item>
  </el-form>
</template>

<style scoped>
.ta-settings-form {
  max-width: 520px;
}

.ta-settings-form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
