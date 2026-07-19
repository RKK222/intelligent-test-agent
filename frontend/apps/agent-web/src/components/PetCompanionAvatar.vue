<script setup lang="ts">
import { computed } from "vue";
import deerUrl from "../assets/pets/deer.png";
import redPandaUrl from "../assets/pets/red-panda.png";
import dragonUrl from "../assets/pets/dragon.png";
import purpleFoxUrl from "../assets/pets/purple-fox.png";
import pandaUrl from "../assets/pets/panda.png";
import raccoonUrl from "../assets/pets/raccoon.png";
import tuxedoCatUrl from "../assets/pets/tuxedo-cat.png";
import type { PetCompanionId } from "./pet-companions";

type PetStatusTone = "ready" | "needs-initialization" | "checking" | "error";

const props = withDefaults(defineProps<{
  petId: PetCompanionId;
  statusTone?: PetStatusTone;
  showStatus?: boolean;
}>(), {
  statusTone: "ready",
  showStatus: false,
});

const petImageUrls: Record<PetCompanionId, string> = {
  deer: deerUrl,
  "red-panda": redPandaUrl,
  dragon: dragonUrl,
  fox: purpleFoxUrl,
  panda: pandaUrl,
  raccoon: raccoonUrl,
  cat: tuxedoCatUrl,
};

// 名册场景按角色使用不同底色；运行态场景用头像下方的细光圈表达进程状态。
const petDiscColors: Record<PetCompanionId, string> = {
  deer: "#f0d6a9",
  "red-panda": "#f3c0a1",
  dragon: "#c0d1f4",
  fox: "#d9c8f4",
  panda: "#e5ddd2",
  raccoon: "#d9c8bd",
  cat: "#ded7e9",
};

const statusDiscColors: Record<PetStatusTone, string> = {
  ready: "#78c6d2",
  "needs-initialization": "#ef8e84",
  checking: "#b2c0cc",
  error: "#ef8e84",
};

const imageUrl = computed(() => petImageUrls[props.petId]);
const discColor = computed(() => props.showStatus ? statusDiscColors[props.statusTone] : petDiscColors[props.petId]);
</script>

<template>
  <svg
    viewBox="0 0 64 64"
    class="pet-companion-svg"
    :class="[
      `is-${petId}`,
      showStatus ? `has-status status-${statusTone}` : null,
    ]"
    role="img"
    :aria-label="petId"
  >
    <circle v-if="!showStatus" class="pet-status-disc" cx="32" cy="32" r="29" :fill="discColor" />
    <image
      class="pet-companion-image"
      :href="imageUrl"
      x="0"
      y="0"
      width="64"
      height="64"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
    />
    <ellipse
      v-if="showStatus"
      class="pet-status-halo"
      cx="32"
      cy="57"
      rx="14"
      ry="2.2"
      fill="none"
      :stroke="discColor"
      stroke-width="1.6"
    />
  </svg>
</template>

<style scoped>
.pet-companion-svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
  filter: drop-shadow(0 2px 2px rgba(31, 43, 55, 0.14));
}

.pet-companion-image {
  display: block;
  width: 64px;
  height: 64px;
  pointer-events: none;
}

.pet-status-halo {
  opacity: .42;
  filter: drop-shadow(0 0 1.4px var(--pet-status-halo-color, #78c6d2));
  --pet-status-halo-color: v-bind(discColor);
}

@media (prefers-reduced-motion: reduce) {
  .pet-status-disc { transition: none; }
}
</style>
