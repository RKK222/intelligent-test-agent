<script setup lang="ts">
import { ref, watch } from "vue";
import SettingsMenu from "./SettingsMenu.vue";
import SettingsPanel from "./SettingsPanel.vue";

type MenuKey = "general" | "appearance" | "account" | "about";

const props = defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
}>();

const activeKey = ref<MenuKey>("general");

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      activeKey.value = "general";
    }
  }
);

function close() {
  emit("update:modelValue", false);
}

function selectMenu(key: MenuKey) {
  activeKey.value = key;
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="系统设置"
    width="840px"
    align-center
    destroy-on-close
    :close-on-click-modal="false"
    class="ta-settings-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="ta-settings-shell">
      <SettingsMenu :active-key="activeKey" @select="selectMenu" />
      <div class="ta-settings-content">
        <SettingsPanel :active-key="activeKey" />
      </div>
    </div>
    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ta-settings-shell {
  display: flex;
  height: 460px;
  border-top: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  overflow: hidden;
}

.ta-settings-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>

<style>
/* 全局：限制 el-dialog 整体高度，使其不超出视口 */
.el-dialog.ta-settings-dialog {
  max-height: calc(100vh - 80px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.el-dialog.ta-settings-dialog .el-dialog__body {
  padding: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
