<script setup lang="ts">
import { computed, type CSSProperties } from "vue";
import type { MermaidNodeType } from "../model";

const props = defineProps<{
  type: MermaidNodeType;
  selected?: boolean;
  thumbnail?: boolean;
  fillColor?: string;
  strokeColor?: string;
}>();

const visualStyle = computed<CSSProperties>(() => ({
  ...(props.fillColor ? { "--ta-mermaid-fill": props.fillColor } : {}),
  ...(props.strokeColor ? { "--ta-mermaid-stroke": props.strokeColor } : {})
}) as CSSProperties);
</script>

<template>
  <svg
    :class="['ta-mermaid-node-shape', `is-${type}`, { 'is-selected': selected }]"
    :data-mermaid-shape="type"
    viewBox="0 0 100 100"
    preserveAspectRatio="none"
    :style="visualStyle"
    aria-hidden="true"
  >
    <rect
      v-if="type === 'rectangle'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      x="1"
      y="1"
      width="98"
      height="98"
      rx="3"
    />
    <rect
      v-else-if="type === 'rounded'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      x="1"
      y="1"
      width="98"
      height="98"
      rx="10"
      ry="20"
    />
    <rect
      v-else-if="type === 'stadium'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      x="1"
      y="1"
      width="98"
      height="98"
      rx="22"
      ry="49"
    />
    <template v-else-if="type === 'subroutine'">
      <rect data-mermaid-shape-layer class="ta-mermaid-node-shape__surface" x="1" y="1" width="98" height="98" rx="3" />
      <path data-mermaid-shape-layer class="ta-mermaid-node-shape__detail" d="M14 1V99M86 1V99" />
    </template>
    <template v-else-if="type === 'database'">
      <path
        data-mermaid-shape-layer
        class="ta-mermaid-node-shape__surface"
        d="M1 16C1 6 23 1 50 1S99 6 99 16V84C99 94 77 99 50 99S1 94 1 84Z"
      />
      <path data-mermaid-shape-layer class="ta-mermaid-node-shape__detail" d="M1 16C1 26 23 31 50 31S99 26 99 16" />
    </template>
    <circle
      v-else-if="type === 'circle'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      cx="50"
      cy="50"
      r="49"
    />
    <polygon
      v-else-if="type === 'diamond'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      points="50,1 99,50 50,99 1,50"
    />
    <polygon
      v-else-if="type === 'hexagon'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      points="20,1 80,1 99,50 80,99 20,99 1,50"
    />
    <polygon
      v-else-if="type === 'parallelogram'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      points="20,1 99,1 80,99 1,99"
    />
    <polygon
      v-else-if="type === 'trapezoid'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      points="20,1 80,1 99,99 1,99"
    />
    <template v-else-if="type === 'double-circle'">
      <circle data-mermaid-shape-layer class="ta-mermaid-node-shape__surface" cx="50" cy="50" r="49" />
      <circle data-mermaid-shape-layer class="ta-mermaid-node-shape__detail" cx="50" cy="50" r="41" />
    </template>
    <template v-else-if="type === 'text'">
      <rect class="ta-mermaid-node-shape__hitbox" x="1" y="1" width="98" height="98" rx="3" />
      <path
        v-if="thumbnail"
        data-mermaid-shape-thumbnail
        class="ta-mermaid-node-shape__thumbnail"
        d="M18 30H82M18 50H70M18 70H88"
      />
    </template>
    <path
      v-else-if="type === 'doc'"
      data-mermaid-shape-layer
      class="ta-mermaid-node-shape__surface"
      d="M1 1H99V82Q75 66 50 99Q25 66 1 82Z"
    />
    <template v-else-if="type === 'docs'">
      <path data-mermaid-shape-layer class="ta-mermaid-node-shape__stack is-back" d="M9 1H99V72C82 62 69 82 53 72C37 62 24 82 9 72Z" />
      <path data-mermaid-shape-layer class="ta-mermaid-node-shape__stack is-middle" d="M5 8H95V79C78 69 65 89 49 79C33 69 20 89 5 79Z" />
      <path data-mermaid-shape-layer class="ta-mermaid-node-shape__surface" d="M1 15H99V82Q75 67 50 99Q25 67 1 82Z" />
    </template>
  </svg>
</template>

<style scoped>
.ta-mermaid-node-shape {
  display: block;
  overflow: visible;
  color: var(--ta-border-strong, #94a3b8);
  filter: drop-shadow(0 2px 3px rgba(15, 23, 42, 0.1));
  pointer-events: none;
}

.ta-mermaid-node-shape__surface,
.ta-mermaid-node-shape__stack,
.ta-mermaid-node-shape__detail,
.ta-mermaid-node-shape__thumbnail,
.ta-mermaid-node-shape__hitbox {
  vector-effect: non-scaling-stroke;
}

.ta-mermaid-node-shape__surface {
  fill: var(--ta-mermaid-fill, var(--ta-surface, #fff));
  stroke: var(--ta-mermaid-stroke, currentColor);
  stroke-width: 1.5;
  stroke-linejoin: round;
}

.ta-mermaid-node-shape__detail {
  fill: none;
  stroke: var(--ta-mermaid-stroke, currentColor);
  stroke-width: 1.5;
  stroke-linecap: round;
}

.ta-mermaid-node-shape__thumbnail {
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
}

.ta-mermaid-node-shape__stack {
  fill: color-mix(in srgb, var(--ta-mermaid-fill, var(--ta-surface, #fff)) 28%, white);
  stroke: var(--ta-mermaid-stroke, currentColor);
  stroke-width: 1.35;
  stroke-linejoin: round;
}

.ta-mermaid-node-shape__stack.is-back { opacity: 0.58; }
.ta-mermaid-node-shape__stack.is-middle { opacity: 0.78; }

.ta-mermaid-node-shape__hitbox {
  fill: transparent;
  stroke: transparent;
  stroke-width: 1.5;
}

.ta-mermaid-node-shape.is-selected {
  filter: drop-shadow(0 0 3px color-mix(in srgb, var(--primary, #4f46e5) 30%, transparent));
}

.ta-mermaid-node-shape.is-text { filter: none; }
.ta-mermaid-node-shape.is-text.is-selected .ta-mermaid-node-shape__hitbox {
  stroke: color-mix(in srgb, var(--primary, #4f46e5) 55%, transparent);
  stroke-dasharray: 4 3;
}
</style>
