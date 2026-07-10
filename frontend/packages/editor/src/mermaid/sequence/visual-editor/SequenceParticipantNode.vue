<script setup lang="ts">
import { Handle, Position } from "@vue-flow/core";
import type { SequenceFlowNodeData } from "./vue-flow-adapter";

defineProps<{ id: string; data: SequenceFlowNodeData; selected?: boolean }>();
</script>

<template>
  <div :class="['ta-sequence-participant', { 'is-actor': data.participantType === 'actor', 'is-selected': selected }]">
    <Handle id="target" type="target" :position="Position.Left" />
    <span class="ta-sequence-participant__kind">{{ data.participantType === "actor" ? "ACTOR" : "PARTICIPANT" }}</span>
    <strong>{{ data.text }}</strong>
    <code>{{ id }}</code>
    <span class="ta-sequence-participant__lifeline" aria-hidden="true" />
    <Handle id="source" type="source" :position="Position.Right" />
  </div>
</template>

<style scoped>
.ta-sequence-participant { position: relative; display: grid; min-width: 132px; gap: 2px; border: 1px solid var(--ta-border-strong, #94a3b8); border-radius: 5px; padding: 9px 13px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08); text-align: center; }
.ta-sequence-participant.is-actor { border-radius: 999px 999px 6px 6px; }
.ta-sequence-participant.is-selected { border-color: var(--primary, #4f46e5); box-shadow: 0 0 0 2px color-mix(in srgb, var(--primary, #4f46e5) 20%, transparent); }
.ta-sequence-participant__kind { color: var(--ta-muted, #64748b); font-size: 8px; letter-spacing: 0.08em; }
.ta-sequence-participant strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.ta-sequence-participant code { color: var(--ta-muted, #64748b); font-size: 9px; }
.ta-sequence-participant__lifeline { position: absolute; z-index: -1; top: 100%; left: 50%; width: 1px; height: 520px; border-left: 1px dashed var(--ta-border-strong, #94a3b8); pointer-events: none; }
</style>
