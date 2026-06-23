<script setup lang="ts">
import { ref, watch } from "vue";

const form = ref({
  theme: "light",
  accent: "#3366ff",
  fontSize: 13,
  density: "comfortable",
  showLineNumbers: true,
  showMinimap: false
});

watch(
  form,
  (value) => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem("ta.settings.appearance", JSON.stringify(value));
    }
  },
  { deep: true }
);
</script>

<template>
  <el-form label-position="top" class="ta-settings-form">
    <el-form-item label="主题">
      <el-radio-group v-model="form.theme">
        <el-radio-button value="light">浅色</el-radio-button>
        <el-radio-button value="dark">深色</el-radio-button>
        <el-radio-button value="system">跟随系统</el-radio-button>
      </el-radio-group>
    </el-form-item>

    <el-form-item label="主题色">
      <el-color-picker v-model="form.accent" />
    </el-form-item>

    <el-form-item label="字体大小">
      <el-slider v-model="form.fontSize" :min="11" :max="18" :step="1" show-input style="max-width: 320px" />
    </el-form-item>

    <el-form-item label="界面密度">
      <el-select v-model="form.density" placeholder="选择密度" style="width: 200px">
        <el-option label="舒适" value="comfortable" />
        <el-option label="紧凑" value="compact" />
      </el-select>
    </el-form-item>

    <el-form-item label="编辑器">
      <el-checkbox v-model="form.showLineNumbers">显示行号</el-checkbox>
      <el-checkbox v-model="form.showMinimap" style="margin-left: 16px">显示小地图</el-checkbox>
    </el-form-item>
  </el-form>
</template>

<style scoped>
.ta-settings-form {
  max-width: 520px;
}
</style>
