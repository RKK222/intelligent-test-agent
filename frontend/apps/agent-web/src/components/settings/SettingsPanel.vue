<script setup lang="ts">
import { computed, type Component } from "vue";
import type { CurrentUser } from "@test-agent/shared-types";
import SettingsAppWorkspacePanel from "./SettingsAppWorkspacePanel.vue";
import SettingsPersonalPanel from "./SettingsPersonalPanel.vue";

type PanelDef = { title: string; component: Component };

const props = defineProps<{
  activeKey: string;
  currentUser: CurrentUser | null;
}>();

const panels: Record<string, PanelDef> = {
  appWorkspace: { title: "应用与工作空间管理", component: SettingsAppWorkspacePanel },
  personal: { title: "个人设置", component: SettingsPersonalPanel }
};

const current = computed<PanelDef>(() => panels[props.activeKey] ?? panels.appWorkspace);
</script>

<template>
  <div class="ta-settings-panel">
    <header class="ta-settings-panel-header">
      <h3 class="ta-settings-panel-title">{{ current.title }}</h3>
    </header>
    <div class="ta-settings-panel-body">
      <component :is="current.component" :current-user="currentUser" />
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
