<script setup lang="ts">
import { computed, type Component } from "vue";
import { Setting, User, Folder } from "@element-plus/icons-vue";
import type { CurrentUser } from "@test-agent/shared-types";
import { hasAppAdminCapability } from "../../auth/roleCapabilities";

type MenuKey = "appWorkspace" | "repository" | "personal";

const props = defineProps<{
  activeKey: MenuKey;
  currentUser: CurrentUser | null;
}>();

const emit = defineEmits<{
  (e: "select", key: MenuKey): void;
}>();

type MenuItem = { key: MenuKey; label: string; icon: Component };
const frontendBuildVersion = import.meta.env.VITE_TEST_AGENT_BUILD_VERSION || "-";

function onboardingTarget(key: MenuKey) {
  if (key === "appWorkspace") return "settings-app-workspace";
  if (key === "repository") return "settings-repository";
  if (key === "personal") return "settings-personal";
  return "settings-personal";
}

const items = computed<MenuItem[]>(() => {
  // 普通成员也能查看应用内当前自动化版本；只有版本库管理继续要求管理员权限。
  const menuItems: MenuItem[] = [
    { key: "appWorkspace", label: "应用管理", icon: Setting }
  ];
  if (hasAppAdminCapability(props.currentUser?.roles)) {
    menuItems.push({ key: "repository", label: "版本库管理", icon: Folder });
  }
  menuItems.push({ key: "personal", label: "个人设置", icon: User });
  return menuItems;
});
</script>

<template>
  <nav class="ta-settings-menu" aria-label="设置导航" data-onboarding="settings-panel">
    <ul class="ta-settings-menu-list">
      <li
        v-for="item in items"
        :key="item.key"
        :class="['ta-settings-menu-item', { 'is-active': activeKey === item.key }]"
        role="button"
        tabindex="0"
        :data-onboarding="onboardingTarget(item.key)"
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
    <div class="ta-settings-version" aria-label="前端版本">
      <span>前端版本</span>
      <strong>{{ frontendBuildVersion }}</strong>
    </div>
  </nav>
</template>

<style scoped>
.ta-settings-menu {
  display: flex;
  flex-direction: column;
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
  flex: 1;
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ta-settings-version {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 12px 8px 0;
  padding-top: 10px;
  border-top: 1px solid #e5e7eb;
  color: #909399;
  font-size: 11px;
  line-height: 1.4;
}
.ta-settings-version strong {
  color: #606266;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 500;
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
