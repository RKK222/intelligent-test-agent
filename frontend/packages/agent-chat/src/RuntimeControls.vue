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
import { Sparkles, TerminalSquare, User } from "lucide-vue-next";
import { Button } from "@test-agent/ui-kit";
import ChicPopover from "./ChicPopover.vue";
import { modelOptionValue } from "./chat-utils";

const props = defineProps<RuntimeControlsProps>();
const emit = defineEmits<{
  agentChange: [agentId: string];
  providerChange: [providerId: string];
  modelChange: [modelId: string];
  modeChange: [mode: string];
  requestNotifications: [];
}>();

const modeOptions = [
  { value: "build", label: "Build" },
  { value: "plan", label: "Plan" },
  { value: "shell", label: "Shell" },
  ...props.commands.slice(0, 8).map((c) => ({ value: `command:${c.name}`, label: `/${c.name}` }))
];
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 border-t border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] p-2">
    <ChicPopover
      label="Agent"
      placeholder="Agent"
      :value="selectedAgent ?? ''"
      :options="agents.map((a) => ({ value: a.agentId, label: a.name }))"
      searchable
      @change="(v) => emit('agentChange', v)"
    >
      <template #icon><User class="h-3.5 w-3.5" /></template>
    </ChicPopover>
    <ChicPopover
      label="Provider"
      placeholder="Provider"
      :value="selectedProvider ?? ''"
      :options="providers.map((p) => ({ value: p.providerId, label: p.name }))"
      searchable
      @change="(v) => emit('providerChange', v)"
    >
      <template #icon><TerminalSquare class="h-3.5 w-3.5" /></template>
    </ChicPopover>
    <ChicPopover
      label="Model"
      placeholder="Model"
      :value="selectedModel ?? ''"
      :options="models.map((m) => ({ value: modelOptionValue(m), label: m.name }))"
      searchable
      @change="(v) => emit('modelChange', v)"
    >
      <template #icon><Sparkles class="h-3.5 w-3.5" /></template>
    </ChicPopover>
    <ChicPopover
      label="Mode"
      placeholder="Build"
      :value="mode"
      :options="modeOptions"
      @change="(v) => emit('modeChange', v)"
    >
      <template #icon><TerminalSquare class="h-3.5 w-3.5" /></template>
    </ChicPopover>
    <Button type="button" size="sm" variant="secondary" @click="emit('requestNotifications')">通知</Button>
  </div>
</template>
