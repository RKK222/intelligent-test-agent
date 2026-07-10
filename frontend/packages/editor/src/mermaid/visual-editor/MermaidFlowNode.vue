<script setup lang="ts">
import { computed } from "vue";
import { Handle, Position } from "@vue-flow/core";
import type { MermaidFlowNodeData } from "./vue-flow-adapter";

const props = defineProps<{
  id: string;
  data: MermaidFlowNodeData;
  selected?: boolean;
}>();

const vertical = computed(() => props.data.direction === "TD" || props.data.direction === "TB" || props.data.direction === "BT");
const targetPosition = computed(() => vertical.value ? Position.Top : Position.Left);
const sourcePosition = computed(() => vertical.value ? Position.Bottom : Position.Right);
</script>

<template>
  <div :class="['ta-mermaid-flow-node', `is-${data.nodeType}`, { 'is-selected': selected }]">
    <Handle type="target" :position="targetPosition" />
    <div class="ta-mermaid-flow-node__id">{{ id }}</div>
    <div class="ta-mermaid-flow-node__label">{{ data.text }}</div>
    <Handle type="source" :position="sourcePosition" />
  </div>
</template>

<style scoped>
.ta-mermaid-flow-node {
  min-width: 118px;
  max-width: 190px;
  padding: 10px 14px;
  border: 1px solid var(--ta-border-strong, #94a3b8);
  border-radius: 5px;
  background: var(--ta-surface, #fff);
  color: var(--ta-ink, #172033);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  text-align: center;
}

.ta-mermaid-flow-node.is-selected {
  border-color: var(--primary, #4f46e5);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--primary, #4f46e5) 20%, transparent);
}

.ta-mermaid-flow-node.is-rounded { border-radius: 999px; }
.ta-mermaid-flow-node.is-stadium { border-radius: 999px; padding-inline: 24px; }
.ta-mermaid-flow-node.is-circle { min-width: 92px; min-height: 92px; border-radius: 999px; display: grid; place-content: center; }
.ta-mermaid-flow-node.is-diamond { border-radius: 2px; transform: rotate(45deg); min-width: 86px; min-height: 86px; padding: 8px; }
.ta-mermaid-flow-node.is-diamond > :not(.vue-flow__handle) { transform: rotate(-45deg); }

.ta-mermaid-flow-node__id {
  margin-bottom: 3px;
  color: var(--ta-muted, #64748b);
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 10px;
  letter-spacing: 0.04em;
}

.ta-mermaid-flow-node__label {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
