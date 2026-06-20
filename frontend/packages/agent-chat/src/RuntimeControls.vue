<script lang="ts">
import type { AgentInfo, CommandInfo, ModelInfo, ProviderInfo } from "@test-agent/shared-types";

export type RuntimeControlsProps = {
  agents: AgentInfo[];
  models: ModelInfo[];
  providers: ProviderInfo[];
  commands: CommandInfo[];
  selectedAgent?: string;
  selectedProvider?: string;
  selectedModel?: string;
  mode: string;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { User } from "lucide-vue-next";
import { Button } from "@test-agent/ui-kit";
import ChicPopover from "./ChicPopover.vue";
import ModelPicker from "./ModelPicker.vue";

const props = defineProps<RuntimeControlsProps>();
const emit = defineEmits<{
  agentChange: [agentId: string];
  providerChange: [providerId: string];
  modelChange: [modelId: string];
  modeChange: [mode: string];
  requestNotifications: [];
}>();

// Agent 下拉只展示 primary/all，过滤掉 subagent 与 hidden，对齐 opencode local.agent.list() 行为。
const runtimeAgents = computed(() => props.agents.filter((agent) => agent.mode !== "subagent" && !agent.hidden));

function onModelSelect(payload: { providerId?: string; modelValue: string }) {
  if (payload.providerId) {
    emit("providerChange", payload.providerId);
  }
  emit("modelChange", payload.modelValue);
}
</script>

<template>
  <div class="ta-runtime-controls">
    <ChicPopover
      label="Agent"
      placeholder="Agent"
      :value="selectedAgent ?? ''"
      :options="runtimeAgents.map((a) => ({ value: a.agentId, label: a.name }))"
      searchable
      @change="(v) => emit('agentChange', v)"
    >
      <template #icon><User class="h-3.5 w-3.5" /></template>
    </ChicPopover>
    <ModelPicker
      :models="models"
      :providers="providers"
      :selected-model="selectedModel"
      @select="onModelSelect"
    />
    <Button type="button" size="sm" variant="secondary" class="ta-runtime-notify" @click="emit('requestNotifications')">通知</Button>
  </div>
</template>
