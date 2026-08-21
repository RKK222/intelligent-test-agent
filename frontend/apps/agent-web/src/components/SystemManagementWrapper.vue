<script setup lang="ts">
import { provide } from "vue";
import { createBackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser } from "@test-agent/shared-types";
import SystemManagementPanel from "./system/SystemManagementPanel.vue";
import type { SystemMenuKey } from "./workspace-page-tabs";

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl });
provide("api", api);

defineProps<{
  currentUser: CurrentUser | null;
  activeKey: SystemMenuKey;
  pageActive: boolean;
  supportRevealed: boolean;
  supportActivationSequence: number;
}>();

const emit = defineEmits<{
  selectMenu: [key: SystemMenuKey];
}>();
</script>

<template>
  <SystemManagementPanel
    :current-user="currentUser"
    :active-key="activeKey"
    :page-active="pageActive"
    :support-revealed="supportRevealed"
    :support-activation-sequence="supportActivationSequence"
    @select-menu="emit('selectMenu', $event)"
  />
</template>
