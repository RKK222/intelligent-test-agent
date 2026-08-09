<script setup lang="ts">
import { useId } from 'vue'

const props = withDefaults(
  defineProps<{
    size?: number | string
    title?: string
  }>(),
  {
    size: 15,
    title: '夜间执行',
  }
)

const uid = useId()
const ringGradId = `clock-ring-grad-${uid}`
const faceGradId = `clock-face-grad-${uid}`
const bellGradId = `clock-bell-grad-${uid}`
const shadowFilterId = `clock-shadow-${uid}`
</script>

<template>
  <svg
    class="fullcolor-alarm-clock-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :title="title"
    :aria-label="title"
    role="img"
  >
    <defs>
      <!-- Outer Ring Gradient (Vibrant Indigo/Blue) -->
      <linearGradient :id="ringGradId" x1="2" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#4F46E5" />
        <stop offset="100%" stop-color="#2563EB" />
      </linearGradient>

      <!-- Clock Face Gradient -->
      <linearGradient :id="faceGradId" x1="6" y1="6" x2="18" y2="18" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#FFFFFF" />
        <stop offset="100%" stop-color="#EFF6FF" />
      </linearGradient>

      <!-- Bell / Legs Gradient (Golden Coral) -->
      <linearGradient :id="bellGradId" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="#F59E0B" />
        <stop offset="100%" stop-color="#D97706" />
      </linearGradient>

      <!-- Drop Shadow Filter -->
      <filter :id="shadowFilterId" x="-10%" y="-10%" width="120%" height="120%">
        <feDropShadow dx="0" dy="0.8" stdDeviation="0.8" flood-color="#1E40AF" flood-opacity="0.3" />
      </filter>
    </defs>

    <!-- Top Left Bell -->
    <path
      d="M5 6L3.5 3.5C3.2 3 3.8 2.4 4.3 2.7L6.8 4.2"
      :stroke="`url(#${bellGradId})`"
      stroke-width="2"
      stroke-linecap="round"
    />

    <!-- Top Right Bell -->
    <path
      d="M19 6L20.5 3.5C20.8 3 20.2 2.4 19.7 2.7L17.2 4.2"
      :stroke="`url(#${bellGradId})`"
      stroke-width="2"
      stroke-linecap="round"
    />

    <!-- Bottom Left Foot -->
    <path
      d="M6 19L4.5 21"
      stroke="#3B82F6"
      stroke-width="2"
      stroke-linecap="round"
    />

    <!-- Bottom Right Foot -->
    <path
      d="M18 19L19.5 21"
      stroke="#3B82F6"
      stroke-width="2"
      stroke-linecap="round"
    />

    <!-- Outer Clock Body -->
    <circle
      cx="12"
      cy="13"
      r="8"
      :fill="`url(#${ringGradId})`"
      :filter="`url(#${shadowFilterId})`"
    />

    <!-- Inner Clock Face -->
    <circle
      cx="12"
      cy="13"
      r="6.2"
      :fill="`url(#${faceGradId})`"
    />

    <!-- Hour Hand -->
    <path
      d="M12 13L12 9.5"
      stroke="#1E293B"
      stroke-width="1.6"
      stroke-linecap="round"
    />

    <!-- Minute Hand -->
    <path
      d="M12 13L14.5 13"
      stroke="#1E293B"
      stroke-width="1.4"
      stroke-linecap="round"
    />

    <!-- Center Dot (Red Accent) -->
    <circle
      cx="12"
      cy="13"
      r="1"
      fill="#EF4444"
    />
  </svg>
</template>

<style scoped>
.fullcolor-alarm-clock-icon {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
}
</style>
