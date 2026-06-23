<script setup lang="ts">
import { Setting, User } from "@element-plus/icons-vue";

type MenuKey = "appWorkspace" | "personal";

defineProps<{
  activeKey: MenuKey;
}>();

const emit = defineEmits<{
  (e: "select", key: MenuKey): void;
}>();

const items: Array<{ key: MenuKey; label: string; icon: typeof Setting }> = [
  { key: "appWorkspace", label: "应用与工作区", icon: Setting },
  { key: "personal", label: "个人设置", icon: User }
];
</script>

<template>
  <nav class="ta-settings-menu" aria-label="设置导航">
    <ul class="ta-settings-menu-list">
      <li
        v-for="item in items"
        :key="item.key"
        :class="['ta-settings-menu-item', { 'is-active': activeKey === item.key }]"
        role="button"
        tabindex="0"
        @click="emit('select', item.key)"
        @keydown.enter="emit('select', item.key)"
        @keydown.space.prevent="emit('select', item.key)"
      >
        <el-icon class="ta-settings-menu-icon">
          <component :is="item.icon" />
        </el-icon>
        <span class="ta-settings-menu-label">{{ item.label }}</span>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.ta-settings-menu {
  width: 200px;
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  padding: 12px 8px;
  border-right: 1px solid #ebeef5;
  background: #fafafa;
  border-top-left-radius: 4px;
  border-bottom-left-radius: 4px;
  box-sizing: border-box;
  overflow-y: auto;
}
.ta-settings-menu-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ta-settings-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding: 0 12px;
  border-radius: 6px;
  cursor: pointer;
  color: #4c4d4f;
  font-size: 13px;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  transition: background-color 0.12s ease, color 0.12s ease;
  outline: none;
}
.ta-settings-menu-item:hover,
.ta-settings-menu-item:focus-visible {
  background: #f0f0f0;
  color: #18181b;
}
.ta-settings-menu-item.is-active {
  background: #e8f0ff;
  color: #3366ff;
  font-weight: 500;
}
.ta-settings-menu-icon { font-size: 16px; }
.ta-settings-menu-label { flex: 1; min-width: 0; }
</style>
