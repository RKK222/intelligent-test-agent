<script setup lang="ts">
import { computed, provide, ref, watch } from "vue";
import { createBackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser } from "@test-agent/shared-types";
import SettingsMenu from "./SettingsMenu.vue";
import SettingsPanel from "./SettingsPanel.vue";

type MenuKey = "appWorkspace" | "repository" | "personal" | "userManagement";

const props = defineProps<{
  open: boolean;
  currentUser: CurrentUser | null;
  initialAppId?: string;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl });
provide("api", api);

const activeKey = ref<MenuKey>("appWorkspace");
const autoOpenCreate = ref(false);
const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const hasAppAdmin = computed(() => hasSuperAdmin.value || props.currentUser?.roles?.includes("APP_ADMIN") === true);
const defaultMenuKey = computed<MenuKey>(() => hasAppAdmin.value ? "appWorkspace" : "personal");

watch(
  () => props.open,
  (open) => {
    if (open) {
      activeKey.value = defaultMenuKey.value;
      autoOpenCreate.value = false;
    }
  }
);

watch(
  () => props.currentUser?.roles,
  () => {
    if (!hasAppAdmin.value && activeKey.value !== "personal") {
      activeKey.value = "personal";
      autoOpenCreate.value = false;
    } else if (!hasSuperAdmin.value && activeKey.value === "userManagement") {
      activeKey.value = "appWorkspace";
      autoOpenCreate.value = false;
    }
  }
);

function close() {
  emit("close");
}

function handleSwitchMenu(key: string) {
  if ((!hasAppAdmin.value && key !== "personal") || (!hasSuperAdmin.value && key === "userManagement")) {
    selectMenu("personal");
    return;
  }
  if (key === "repository") {
    autoOpenCreate.value = true;
  }
  selectMenu(key as MenuKey);
}

function selectMenu(key: MenuKey) {
  if ((!hasAppAdmin.value && key !== "personal") || (!hasSuperAdmin.value && key === "userManagement")) {
    activeKey.value = "personal";
    autoOpenCreate.value = false;
    return;
  }
  activeKey.value = key;
  if (key !== "repository") {
    autoOpenCreate.value = false;
  }
}
</script>

<template>
  <el-dialog
    :model-value="open"
    title="设置"
    width="960px"
    align-center
    :close-on-click-modal="false"
    class="ta-settings-dialog"
    @update:model-value="(v: boolean) => { if (!v) close() }"
  >
    <div class="ta-settings-shell">
      <SettingsMenu :active-key="activeKey" :current-user="currentUser" @select="selectMenu" />
      <div class="ta-settings-content">
        <SettingsPanel :active-key="activeKey" :current-user="currentUser" :auto-open-create="autoOpenCreate" :initial-app-id="props.initialAppId" @switch-menu="handleSwitchMenu" />
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
  height: 520px;
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
