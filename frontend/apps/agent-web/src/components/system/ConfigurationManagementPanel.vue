<script setup lang="ts">
import { ref } from "vue";
import { FolderGit2 } from "lucide-vue-next";
import type { CurrentUser } from "@test-agent/shared-types";
import OpencodePublicConfigManagementPanel from "./OpencodePublicConfigManagementPanel.vue";

defineProps<{
  currentUser: CurrentUser | null;
}>();

type ConfigMenuKey = "opencode-public";

const activeKey = ref<ConfigMenuKey>("opencode-public");
</script>

<template>
  <section class="ta-config-management">
    <nav class="ta-config-submenu" aria-label="配置管理导航">
      <button
        type="button"
        :class="['ta-config-submenu-item', { 'is-active': activeKey === 'opencode-public' }]"
        @click="activeKey = 'opencode-public'"
      >
        <FolderGit2 class="ta-config-submenu-icon" :stroke-width="1.6" />
        <span>opencode公共配置管理</span>
      </button>
    </nav>
    <div class="ta-config-content">
      <OpencodePublicConfigManagementPanel v-if="activeKey === 'opencode-public'" :current-user="currentUser" />
    </div>
  </section>
</template>

<style scoped>
.ta-config-management {
  display: flex;
  height: 100%;
  min-height: 0;
  background: #f7f8fa;
  color: #1f2937;
}
.ta-config-submenu {
  width: 196px;
  flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  background: #fff;
  padding: 12px 8px;
  box-sizing: border-box;
}
.ta-config-submenu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 32px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
}
.ta-config-submenu-item:hover,
.ta-config-submenu-item:focus-visible {
  background: #f3f4f6;
  color: #111827;
  outline: none;
}
.ta-config-submenu-item.is-active {
  background: #e8f0ff;
  color: #2563eb;
}
.ta-config-submenu-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.ta-config-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
