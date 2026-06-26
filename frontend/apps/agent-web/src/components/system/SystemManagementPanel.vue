<script setup lang="ts">
import { computed, ref, type Component } from "vue";
import { Activity, CalendarClock, SlidersHorizontal } from "lucide-vue-next";
import type { CurrentUser } from "@test-agent/shared-types";
import RuntimeManagementPanel from "../settings/RuntimeManagementPanel.vue";
import ScheduledTaskManagementPanel from "./ScheduledTaskManagementPanel.vue";
import GeneralParamManagementPanel from "./GeneralParamManagementPanel.vue";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

type SystemMenuKey = "scheduler" | "runtime" | "params";
type SystemMenuItem = { key: SystemMenuKey; label: string; icon: Component };

const activeKey = ref<SystemMenuKey>("scheduler");
const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);

const items: SystemMenuItem[] = [
  { key: "scheduler", label: "定时任务管理", icon: CalendarClock },
  { key: "runtime", label: "运行管理", icon: Activity },
  { key: "params", label: "通用参数管理", icon: SlidersHorizontal }
];

function selectMenu(key: SystemMenuKey) {
  activeKey.value = key;
}
</script>

<template>
  <section class="ta-system-management">
    <div v-if="!hasSuperAdmin" class="ta-system-placeholder">当前账号无系统管理权限</div>
    <template v-else>
      <nav class="ta-system-menu" aria-label="系统管理导航">
        <button
          v-for="item in items"
          :key="item.key"
          type="button"
          :class="['ta-system-menu-item', { 'is-active': activeKey === item.key }]"
          @click="selectMenu(item.key)"
        >
          <component :is="item.icon" class="ta-system-menu-icon" :stroke-width="1.6" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
      <div class="ta-system-content">
        <ScheduledTaskManagementPanel v-if="activeKey === 'scheduler'" :current-user="currentUser" />
        <RuntimeManagementPanel v-else-if="activeKey === 'runtime'" :current-user="currentUser" />
        <GeneralParamManagementPanel v-else :current-user="currentUser" />
      </div>
    </template>
  </section>
</template>

<style scoped>
.ta-system-management {
  display: flex;
  height: 100%;
  min-height: 0;
  background: #f7f8fa;
  color: #1f2937;
}
.ta-system-placeholder {
  margin: 16px;
  padding: 16px;
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
}
.ta-system-menu {
  width: 180px;
  flex-shrink: 0;
  padding: 12px 8px;
  border-right: 1px solid #e5e7eb;
  background: #fff;
  box-sizing: border-box;
}
.ta-system-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 36px;
  margin: 0 0 4px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #4b5563;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.ta-system-menu-item:hover,
.ta-system-menu-item:focus-visible {
  background: #f3f4f6;
  color: #111827;
  outline: none;
}
.ta-system-menu-item.is-active {
  background: #e8f0ff;
  color: #2563eb;
  font-weight: 600;
}
.ta-system-menu-icon {
  width: 16px;
  height: 16px;
}
.ta-system-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
</style>
