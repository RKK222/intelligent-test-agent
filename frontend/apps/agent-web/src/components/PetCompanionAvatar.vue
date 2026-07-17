<script setup lang="ts">
import { computed } from "vue";
import snifferHoundUrl from "../assets/pets/sniffer-hound.png";
import radarBunnyUrl from "../assets/pets/radar-bunny.png";
import starFoxUrl from "../assets/pets/star-fox.png";
import inspectorBirdUrl from "../assets/pets/inspector-bird.png";
import dataHedgehogUrl from "../assets/pets/data-hedgehog.png";
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
  sniffer: snifferHoundUrl,
  bunny: radarBunnyUrl,
  fox: starFoxUrl,
  bird: inspectorBirdUrl,
  hedgehog: dataHedgehogUrl,
};

// 名册场景按角色使用不同底色；运行态场景用头像下方的细光圈表达进程状态。
const petDiscColors: Record<PetCompanionId, string> = {
  sniffer: "#f1d8b2",
  bunny: "#bfe9e2",
  fox: "#f3c39c",
  bird: "#f7d77d",
  hedgehog: "#c9baf2",
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
