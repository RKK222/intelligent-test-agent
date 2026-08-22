<script setup lang="ts">
import { computed, type Component } from "vue";
import { Activity, BarChart3, BrainCircuit, CalendarClock, Fingerprint, KeyRound, Network, Radar, RefreshCw, Settings2, SlidersHorizontal, UsersRound } from "lucide-vue-next";
import type { CurrentUser } from "@test-agent/shared-types";
import RuntimeManagementPanel from "../settings/RuntimeManagementPanel.vue";
import ScheduledTaskManagementPanel from "./ScheduledTaskManagementPanel.vue";
import ConfigurationManagementPanel from "./ConfigurationManagementPanel.vue";
import GeneralParamManagementPanel from "./GeneralParamManagementPanel.vue";
import AnalyticsManagementPanel from "./AnalyticsManagementPanel.vue";
import InternalModelProviderPanel from "./InternalModelProviderPanel.vue";
import InternalModelObservabilityPanel from "./InternalModelObservabilityPanel.vue";
import SupportAccessPanel from "./SupportAccessPanel.vue";
import MemoryAdminPanel from "./MemoryAdminPanel.vue";
import ApiKeyManagementPanel from "./ApiKeyManagementPanel.vue";
import SettingsUserManagementPanel from "../settings/SettingsUserManagementPanel.vue";
import LocalClientVersionManagementPanel from "./LocalClientVersionManagementPanel.vue";
import TraceView from "../../views/TraceView.vue";
import type { SystemMenuKey } from "../workspace-page-tabs";

const props = defineProps<{
  currentUser: CurrentUser | null;
  activeKey: SystemMenuKey;
  pageActive: boolean;
  supportRevealed: boolean;
  supportActivationSequence: number;
}>();

const emit = defineEmits<{
  selectMenu: [key: SystemMenuKey];
}>();

type SystemMenuItem = { key: SystemMenuKey; label: string; icon: Component };

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const hasApplicationAdmin = computed(() => props.currentUser?.roles?.includes("APP_ADMIN") === true);
const hasSystemAccess = computed(() => hasSuperAdmin.value || hasApplicationAdmin.value);

const items: SystemMenuItem[] = [
  { key: "scheduler", label: "定时任务管理", icon: CalendarClock },
  { key: "runtime", label: "运行管理", icon: Activity },
  { key: "users", label: "用户管理", icon: UsersRound },
  { key: "localClientVersions", label: "本地客户端版本", icon: RefreshCw },
  { key: "params", label: "通用参数管理", icon: SlidersHorizontal },
  { key: "apiKeys", label: "API Key 管理", icon: Fingerprint },
  { key: "internalModels", label: "内部模型供应商", icon: Network },
  { key: "internalModelObservability", label: "内部模型可观测", icon: Radar },
  { key: "memory", label: "记忆能力", icon: BrainCircuit },
  { key: "config", label: "配置管理", icon: Settings2 },
  { key: "analytics", label: "运营分析", icon: BarChart3 },
  { key: "traces", label: "Trace 可观测", icon: Activity }
];
const visibleItems = computed<SystemMenuItem[]>(() => {
  // 应用管理员只获得应用 Git 控制台入口，其余系统能力继续由超级管理员独占。
  if (!hasSuperAdmin.value) return items.filter((item) => item.key === "config");
  return props.supportRevealed
    ? [...items, { key: "support", label: "问题排查只读访问", icon: KeyRound }]
    : items;
});

function selectMenu(key: SystemMenuKey) {
  if (!visibleItems.value.some((item) => item.key === key)) return;
  emit("selectMenu", key);
}
</script>

<template>
  <section class="ta-system-management">
    <div v-if="!hasSystemAccess" class="ta-system-placeholder">当前账号无系统管理权限</div>
    <template v-else>
      <nav class="ta-system-menu" aria-label="系统管理导航">
        <el-tooltip
          v-for="item in visibleItems"
          :key="item.key"
          :content="item.label"
          placement="right"
        >
          <button
            type="button"
            :class="['ta-system-menu-item', { 'is-active': props.activeKey === item.key }]"
            @click="selectMenu(item.key)"
          >
            <component :is="item.icon" class="ta-system-menu-icon" :stroke-width="1.6" />
            <span class="ta-system-menu-text">{{ item.label }}</span>
          </button>
        </el-tooltip>
      </nav>
      <div class="ta-system-content">
        <ScheduledTaskManagementPanel
          v-if="props.activeKey === 'scheduler'"
          :current-user="currentUser"
          :page-active="props.pageActive"
        />
        <RuntimeManagementPanel
          v-else-if="props.activeKey === 'runtime'"
          :current-user="currentUser"
          :page-active="props.pageActive"
        />
        <SettingsUserManagementPanel
          v-else-if="props.activeKey === 'users'"
          :current-user="currentUser"
        />
        <LocalClientVersionManagementPanel
          v-else-if="props.activeKey === 'localClientVersions'"
          :current-user="currentUser"
          :page-active="props.pageActive"
        />
        <GeneralParamManagementPanel v-else-if="props.activeKey === 'params'" :current-user="currentUser" />
        <ApiKeyManagementPanel
          v-else-if="props.activeKey === 'apiKeys'"
          :current-user="currentUser"
          :page-active="props.pageActive"
        />
        <InternalModelProviderPanel
          v-else-if="props.activeKey === 'internalModels'"
          :current-user="currentUser"
          :page-active="props.pageActive"
        />
        <InternalModelObservabilityPanel
          v-else-if="props.activeKey === 'internalModelObservability'"
          :current-user="currentUser"
          :page-active="props.pageActive"
        />
        <MemoryAdminPanel
          v-else-if="props.activeKey === 'memory'"
          @configure-models="selectMenu('internalModels')"
        />
        <ConfigurationManagementPanel
          v-else-if="props.activeKey === 'config'"
          :current-user="currentUser"
          :page-active="props.pageActive"
        />
        <AnalyticsManagementPanel v-else-if="props.activeKey === 'analytics'" />
        <TraceView v-else-if="props.activeKey === 'traces'" />
        <SupportAccessPanel
          v-else-if="props.activeKey === 'support' && props.supportRevealed && props.pageActive"
          :current-user="currentUser"
          :activation-sequence="props.supportActivationSequence"
        />
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
  width: 52px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0;
  border-right: 1px solid #e5e7eb;
  background: #fff;
  box-sizing: border-box;
}
.ta-system-menu-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin: 0 0 8px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  transition: all 0.2s ease;
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
}
.ta-system-menu-icon {
  width: 18px;
  height: 18px;
}
.ta-system-menu-text {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.ta-system-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
