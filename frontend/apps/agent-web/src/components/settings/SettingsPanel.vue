<script setup lang="ts">
import { computed, type Component } from "vue";
import type { CurrentUser } from "@test-agent/shared-types";
import SettingsAppWorkspacePanel from "./SettingsAppWorkspacePanel.vue";
import SettingsRepositoryPanel from "./SettingsRepositoryPanel.vue";
import SettingsPersonalPanel from "./SettingsPersonalPanel.vue";
import SettingsUserManagementPanel from "./SettingsUserManagementPanel.vue";

type PanelDef = { title: string; component: Component };

const props = defineProps<{
  activeKey: string;
  currentUser: CurrentUser | null;
  autoOpenCreate?: boolean;
}>();

const emit = defineEmits<{
  (e: "switch-menu", key: string): void;
}>();

const panels: Record<string, PanelDef> = {
  appWorkspace: { title: "应用管理", component: SettingsAppWorkspacePanel },
  repository: { title: "版本库管理", component: SettingsRepositoryPanel },
  personal: { title: "个人设置", component: SettingsPersonalPanel },
  userManagement: { title: "用户管理", component: SettingsUserManagementPanel }
};

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const hasAppAdmin = computed(() => hasSuperAdmin.value || props.currentUser?.roles?.includes("APP_ADMIN") === true);

const effectiveKey = computed(() => {
  if (hasAppAdmin.value) {
    if (props.activeKey === "userManagement" && !hasSuperAdmin.value) return "appWorkspace";
    return panels[props.activeKey] ? props.activeKey : "appWorkspace";
  }
  return "personal";
});

const current = computed<PanelDef>(() => panels[effectiveKey.value] ?? panels.personal);
</script>

<template>
  <div class="ta-settings-panel">
    <header class="ta-settings-panel-header">
      <h3 class="ta-settings-panel-title">{{ current.title }}</h3>
    </header>
    <div class="ta-settings-panel-body">
      <component :is="current.component" :current-user="currentUser" :auto-open-create="autoOpenCreate" @switch-menu="(key: string) => emit('switch-menu', key)" />
    </div>
  </div>
</template>

<style scoped>
.ta-settings-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.ta-settings-panel-header {
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 20px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}
.ta-settings-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
  margin: 0;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
.ta-settings-panel-body {
  flex: 1;
  min-height: 0;
  padding: 20px 24px;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
