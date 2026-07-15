<script setup lang="ts">
import { computed } from "vue";
import { cn } from "./lib";

defineOptions({
  inheritAttrs: false
});

// 定义组件的属性接口
interface ShimmerDividerProps {
  /**
   * 流光动画的播放速度/持续时间（秒）。
   * 支持预设：
   * - 'fast': 1.0 秒，流光速度较快，适用于紧急、高优先级或轻量提示。
   * - 'normal': 2.0 秒，常规流动速度，适合普通的过渡和提示。
   * - 'slow': 4.0 秒，平缓的流动速度，更显沉稳和静谧。
   * 也可以传入具体数值（单位为秒），数值越小速度越快。
   * @default "normal"
   */
  speed?: number | "fast" | "normal" | "slow";

  /**
   * 分割线的高度。
   * 支持 CSS 长度单位（如 '1px', '2px', '0.5rem'）或纯数字（表示像素）。
   * @default "1px"
   */
  height?: string | number;

  /**
   * 是否在分割线左右两端应用淡出（渐隐）效果。
   * 开启后，分割线两端将呈现柔和淡出，边缘更加柔和高级。
   * @default true
   */
  fade?: boolean;
}

const props = withDefaults(defineProps<ShimmerDividerProps>(), {
  speed: "normal",
  height: "1px",
  fade: true
});

// 计算动画的持续时间
const duration = computed(() => {
  if (typeof props.speed === "number") {
    return `${props.speed}s`;
  }
  switch (props.speed) {
    case "fast":
      return "1s";
    case "slow":
      return "4s";
    case "normal":
    default:
      return "2s";
  }
});

// 计算容器的高度样式
const heightStyle = computed(() => {
  if (typeof props.height === "number") {
    return `${props.height}px`;
  }
  return props.height;
});
</script>

<template>
  <!-- 外层容器，接收外部传入的 class 和 style 属性进行布局，隐藏溢出部分 -->
  <div
    :class="cn('relative w-full overflow-hidden my-4 shrink-0', $attrs.class as string)"
    :style="[
      { height: heightStyle },
      $attrs.style as Record<string, any>
    ]"
  >
    <!-- 内层流光轨迹，应用无限循环流光动画和边缘淡出遮罩 -->
    <div
      class="ta-shimmer-track absolute inset-0 w-full h-full"
      :class="{ 'ta-fade-mask': fade }"
      :style="{ '--ta-shimmer-duration': duration }"
    />
  </div>
</template>

<style scoped>
/* 流光轨迹背景配置 */
.ta-shimmer-track {
  background: linear-gradient(
    90deg,
    #4f7cff 0%,
    #8b5cf6 50%,
    #4f7cff 100%
  );
  background-size: 200% 100%;
  animation: ta-shimmer-anim linear infinite;
  animation-duration: var(--ta-shimmer-duration, 2s);
}

/* 左右两端淡出遮罩，使分割线过渡更加 premium 和高级 */
.ta-fade-mask {
  mask-image: linear-gradient(to right, transparent 0%, black 15%, black 85%, transparent 100%);
  -webkit-mask-image: linear-gradient(to right, transparent 0%, black 15%, black 85%, transparent 100%);
}

/* 无限循环流光位移动画 */
@keyframes ta-shimmer-anim {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}
</style>
