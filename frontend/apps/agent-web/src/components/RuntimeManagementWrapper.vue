<script setup lang="ts">
import { provide } from "vue";
import { createBackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser } from "@test-agent/shared-types";
import RuntimeManagementPanel from "./settings/RuntimeManagementPanel.vue";

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl });
provide("api", api);

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();
</script>

<template>
  <RuntimeManagementPanel :current-user="currentUser" />
</template>
