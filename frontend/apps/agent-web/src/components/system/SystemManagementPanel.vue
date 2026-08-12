<script setup lang="ts">
import { computed, ref, watch, type Component } from "vue";
import { Activity, BarChart3, CalendarClock, Fingerprint, KeyRound, Network, Radar, Settings2, SlidersHorizontal } from "lucide-vue-next";
import type { CurrentUser } from "@test-agent/shared-types";
import RuntimeManagementPanel from "../settings/RuntimeManagementPanel.vue";
import ScheduledTaskManagementPanel from "./ScheduledTaskManagementPanel.vue";
import ConfigurationManagementPanel from "./ConfigurationManagementPanel.vue";
import GeneralParamManagementPanel from "./GeneralParamManagementPanel.vue";
import AnalyticsManagementPanel from "./AnalyticsManagementPanel.vue";
import InternalModelProviderPanel from "./InternalModelProviderPanel.vue";
import InternalModelObservabilityPanel from "./InternalModelObservabilityPanel.vue";
import SupportAccessPanel from "./SupportAccessPanel.vue";
import ApiKeyManagementPanel from "./ApiKeyManagementPanel.vue";

const props = defineProps<{
  currentUser: CurrentUser | null;
  supportAccessRequested?: boolean;
}>();

const emit = defineEmits<{
  supportAccessOpened: [];
}>();

type SystemMenuKey = "scheduler" | "runtime" | "params" | "apiKeys" | "internalModels" | "internalModelObservability" | "config" | "analytics" | "support";
type SystemMenuItem = { key: SystemMenuKey; label: string; icon: Component };

const activeKey = ref<SystemMenuKey>(props.currentUser?.roles?.includes("SUPER_ADMIN") === true ? "scheduler" : "config");
const supportRevealed = ref(false);
const supportActivationSequence = ref(0);
const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const hasApplicationAdmin = computed(() => props.currentUser?.roles?.includes("APP_ADMIN") === true);
const hasSystemAccess = computed(() => hasSuperAdmin.value || hasApplicationAdmin.value);

const items: SystemMenuItem[] = [
  { key: "scheduler", label: "定时任务管理", icon: CalendarClock },
  { key: "runtime", label: "运行管理", icon: Activity },
  { key: "params", label: "通用参数管理", icon: SlidersHorizontal },
  { key: "apiKeys", label: "API Key 管理", icon: Fingerprint },
  { key: "internalModels", label: "内部模型供应商", icon: Network },
  { key: "internalModelObservability", label: "内部模型可观测", icon: Radar },
  { key: "config", label: "配置管理", icon: Settings2 },
  { key: "analytics", label: "运营分析", icon: BarChart3 }
];
const visibleItems = computed<SystemMenuItem[]>(() => {
  // 应用管理员只获得应用 Git 控制台入口，其余系统能力继续由超级管理员独占。
  if (!hasSuperAdmin.value) return items.filter((item) => item.key === "config");
  return supportRevealed.value
    ? [...items, { key: "support", label: "问题排查只读访问", icon: KeyRound }]
    : items;
});

function selectMenu(key: SystemMenuKey) {
  activeKey.value = key;
}

/** 全局手势只请求展示入口；组件仍按实时角色收口，不参与身份切换。 */
function revealSupportAccess() {
  if (!hasSuperAdmin.value) return;
  supportActivationSequence.value += 1;
  supportRevealed.value = true;
  activeKey.value = "support";
  emit("supportAccessOpened");
}

watch(() => props.supportAccessRequested, (requested) => {
  if (requested) revealSupportAccess();
}, { immediate: true });
watch(hasSuperAdmin, (allowed) => {
  if (allowed) {
    // 登录态异步恢复为超级管理员时，保持原有控制台默认进入定时任务管理的行为。
    activeKey.value = "scheduler";
    return;
  }
  supportRevealed.value = false;
  activeKey.value = hasApplicationAdmin.value ? "config" : "scheduler";
});
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
            :class="['ta-system-menu-item', { 'is-active': activeKey === item.key }]"
            @click="selectMenu(item.key)"
          >
            <component :is="item.icon" class="ta-system-menu-icon" :stroke-width="1.6" />
            <span class="ta-system-menu-text">{{ item.label }}</span>
          </button>
        </el-tooltip>
      </nav>
      <div class="ta-system-content">
        <ScheduledTaskManagementPanel v-if="activeKey === 'scheduler'" :current-user="currentUser" />
        <RuntimeManagementPanel v-else-if="activeKey === 'runtime'" :current-user="currentUser" />
        <GeneralParamManagementPanel v-else-if="activeKey === 'params'" :current-user="currentUser" />
        <ApiKeyManagementPanel v-else-if="activeKey === 'apiKeys'" :current-user="currentUser" />
        <InternalModelProviderPanel v-else-if="activeKey === 'internalModels'" :current-user="currentUser" />
        <InternalModelObservabilityPanel v-else-if="activeKey === 'internalModelObservability'" :current-user="currentUser" />
        <ConfigurationManagementPanel v-else-if="activeKey === 'config'" :current-user="currentUser" />
        <AnalyticsManagementPanel v-else-if="activeKey === 'analytics'" />
        <SupportAccessPanel
          v-else-if="activeKey === 'support'"
          :current-user="currentUser"
          :activation-sequence="supportActivationSequence"
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
