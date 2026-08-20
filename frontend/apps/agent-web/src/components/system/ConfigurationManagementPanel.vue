<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { FolderGit2, GitPullRequest } from "lucide-vue-next";
import type { CurrentUser } from "@test-agent/shared-types";
import OpencodePublicConfigManagementPanel from "./OpencodePublicConfigManagementPanel.vue";
import ApplicationGitRefreshManagementPanel from "./ApplicationGitRefreshManagementPanel.vue";

const props = defineProps<{
  currentUser: CurrentUser | null;
  pageActive: boolean;
}>();

type ConfigMenuKey = "opencode-public" | "application-git";

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const activeKey = ref<ConfigMenuKey>(hasSuperAdmin.value ? "opencode-public" : "application-git");

watch(hasSuperAdmin, (allowed) => {
  // 角色刷新后立即收回公共配置页，避免应用管理员停留在超管子页。
  if (!allowed) activeKey.value = "application-git";
});
</script>

<template>
  <section class="ta-config-management">
    <nav class="ta-config-submenu" aria-label="配置管理导航">
      <button
        v-if="hasSuperAdmin"
        type="button"
        :class="['ta-config-submenu-item', { 'is-active': activeKey === 'opencode-public' }]"
        @click="activeKey = 'opencode-public'"
      >
        <FolderGit2 class="ta-config-submenu-icon" :stroke-width="1.6" />
        <span>TestAgent公共配置管理</span>
      </button>
      <button
        type="button"
        :class="['ta-config-submenu-item', { 'is-active': activeKey === 'application-git' }]"
        @click="activeKey = 'application-git'"
      >
        <GitPullRequest class="ta-config-submenu-icon" :stroke-width="1.6" />
        <span>应用 Git 刷新</span>
      </button>
    </nav>
    <div class="ta-config-content">
      <OpencodePublicConfigManagementPanel
        v-if="hasSuperAdmin && activeKey === 'opencode-public'"
        :current-user="currentUser"
        :page-active="props.pageActive"
      />
      <ApplicationGitRefreshManagementPanel v-else :current-user="currentUser" />
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
