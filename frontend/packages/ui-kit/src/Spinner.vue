<script setup lang="ts">
import { useAttrs } from "vue";
import { cn } from "./lib";

// 禁用属性默认透传，以便我们手动将属性透传给根 <svg>
defineOptions({
  inheritAttrs: false
});

const attrs = useAttrs();

// 定义 16 点阵中外圈和边角的索引，与原 Solid.js 版本保持一致
const outerIndices = new Set([1, 2, 4, 7, 8, 11, 13, 14]);
const cornerIndices = new Set([0, 3, 12, 15]);

// 生成 16 个小方块的点阵坐标和随机动画延时/时长
const squares = Array.from({ length: 16 }, (_, i) => ({
  id: i,
  x: (i % 4) * 4,
  y: Math.floor(i / 4) * 4,
  delay: Math.random() * 1.5,
  duration: 1 + Math.random() * 1,
  outer: outerIndices.has(i),
  corner: cornerIndices.has(i),
}));
</script>

<template>
  <!-- 手动绑定透传属性和默认样式类 -->
  <svg
    v-bind="attrs"
    viewBox="0 0 15 15"
    data-component="spinner"
    :class="cn('spinner-svg', attrs.class)"
    fill="currentColor"
  >
    <rect
      v-for="square in squares"
      :key="square.id"
      :x="square.x"
      :y="square.y"
      width="3"
      height="3"
      rx="1"
      :style="{
        opacity: square.corner ? 0 : undefined,
        animation: square.corner
          ? undefined
          : `${square.outer ? 'pulse-opacity-dim' : 'pulse-opacity'} ${square.duration}s ease-in-out infinite`,
        'animation-fill-mode': square.corner ? undefined : 'both',
        'animation-delay': square.corner ? undefined : `${square.delay}s`,
      }"
    />
  </svg>
</template>

<style>
/* 默认 Spinner 样式，尺寸为 18px */
[data-component="spinner"] {
  color: inherit;
  flex-shrink: 0;
  width: 18px;
  aspect-ratio: 1;
}

/* 呼吸闪烁动画定义 */
@keyframes pulse-opacity {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}

@keyframes pulse-opacity-dim {
  0%,
  100% {
    opacity: 0.15;
  }
  50% {
    opacity: 0.35;
  }
}
</style>
