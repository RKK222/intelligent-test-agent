<script setup lang="ts">
import { computed, useId } from "vue";
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

const uid = useId().replace(/:/g, "");
const chameleonGradientId = computed(() => `pet-chameleon-${uid}`);
const owlMetalGradientId = computed(() => `pet-owl-${uid}`);
const statusBeacon = computed(() => ({
  sniffer: { cx: 32, cy: 41.5 },
  chameleon: { cx: 38, cy: 39 },
  platypus: { cx: 46, cy: 37 },
  owl: { cx: 32, cy: 15 },
  glitch: { cx: 54, cy: 45 },
}[props.petId]));
</script>

<template>
  <svg
    viewBox="0 0 64 64"
    class="pet-companion-svg"
    :class="`is-${petId}`"
    role="img"
    :aria-label="petId"
  >
    <defs>
      <linearGradient :id="chameleonGradientId" x1="8" y1="12" x2="56" y2="54" gradientUnits="userSpaceOnUse">
        <stop stop-color="#b7d9ee" />
        <stop offset="0.52" stop-color="#b8a9e5" />
        <stop offset="1" stop-color="#65c9cb" />
      </linearGradient>
      <linearGradient :id="owlMetalGradientId" x1="18" y1="8" x2="47" y2="57" gradientUnits="userSpaceOnUse">
        <stop stop-color="#f5f7f9" />
        <stop offset="0.55" stop-color="#a9b3bf" />
        <stop offset="1" stop-color="#5f6d7c" />
      </linearGradient>
    </defs>

    <g v-if="petId === 'sniffer'" class="pet-character pet-sniffer">
      <ellipse cx="33" cy="52" rx="23" ry="4" fill="#1b2734" opacity=".12" />
      <ellipse class="pet-body" cx="34" cy="39" rx="21" ry="16" fill="#cfae7d" />
      <path class="pet-tail pet-wave-part" d="M52 38c7-6 8-12 3-15" fill="none" stroke="#ad8257" stroke-width="5" stroke-linecap="round" />
      <circle class="pet-head" cx="25" cy="27" r="17" fill="#d9bb8c" />
      <ellipse cx="11" cy="31" rx="7" ry="17" fill="#9a704d" transform="rotate(8 11 31)" />
      <ellipse cx="38" cy="31" rx="7" ry="17" fill="#9a704d" transform="rotate(-8 38 31)" />
      <ellipse cx="25" cy="34" rx="12" ry="9" fill="#f3e7d4" />
      <circle class="pet-eye" cx="19" cy="24" r="3.2" fill="#182331" />
      <circle class="pet-eye" cx="31" cy="24" r="3.2" fill="#182331" />
      <circle cx="18" cy="23" r="1" fill="#fff" />
      <circle cx="30" cy="23" r="1" fill="#fff" />
      <ellipse cx="25" cy="31" rx="5" ry="3.8" fill="#16202a" />
      <path d="M12 41h25" stroke="#253b49" stroke-width="4" stroke-linecap="round" />
      <path d="M14 40.5h21" stroke="#51d19c" stroke-width="1.4" stroke-linecap="round" />
      <rect class="pet-leg-l" x="18" y="48" width="8" height="8" rx="4" fill="#ead7b8" />
      <rect class="pet-leg-r" x="40" y="47" width="8" height="8" rx="4" fill="#ead7b8" />
    </g>

    <g v-else-if="petId === 'chameleon'" class="pet-character pet-chameleon">
      <ellipse cx="31" cy="53" rx="24" ry="4" fill="#1b2734" opacity=".12" />
      <path class="pet-tail pet-wave-part" d="M42 40c14 1 18-5 15-12-3-7-13-4-10 2 2 4 7 1 5-2" fill="none" :stroke="`url(#${chameleonGradientId})`" stroke-width="7" stroke-linecap="round" />
      <path class="pet-body" d="M12 42c0-14 8-24 22-24 12 0 18 9 17 22-1 10-10 14-23 14-10 0-16-4-16-12Z" :fill="`url(#${chameleonGradientId})`" opacity=".94" />
      <path d="M24 18l3-7 4 7 4-7 3 8" fill="#a99ee0" opacity=".8" />
      <circle class="pet-head" cx="20" cy="29" r="14" fill="#aebde5" />
      <circle cx="14" cy="25" r="8" fill="#7884b9" />
      <circle cx="27" cy="24" r="8" fill="#7884b9" />
      <circle class="pet-eye" cx="14" cy="25" r="5.2" fill="#f3bb48" />
      <circle class="pet-eye" cx="27" cy="24" r="5.2" fill="#f3bb48" />
      <circle cx="14" cy="25" r="2.6" fill="#17202d" />
      <circle cx="27" cy="24" r="2.6" fill="#17202d" />
      <path d="M15 35c4 3 8 3 12 0" fill="none" stroke="#516084" stroke-width="1.8" stroke-linecap="round" />
      <circle cx="37" cy="39" r="2.5" fill="#55e0db" opacity=".8" />
      <path class="pet-leg-l" d="M17 47l-6 7h12" fill="none" stroke="#8192ca" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
      <path class="pet-leg-r" d="M36 48l-3 7h11" fill="none" stroke="#8192ca" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
    </g>

    <g v-else-if="petId === 'platypus'" class="pet-character pet-platypus">
      <ellipse cx="32" cy="53" rx="24" ry="4" fill="#1b2734" opacity=".12" />
      <path class="pet-tail pet-wave-part" d="M49 34c9-4 12 2 8 11-3 6-8 5-11 1" fill="#66778a" />
      <ellipse class="pet-body" cx="34" cy="36" rx="23" ry="18" fill="#526476" />
      <ellipse class="pet-head" cx="22" cy="33" rx="16" ry="15" fill="#5e7082" />
      <rect x="10" y="20" width="26" height="16" rx="8" fill="#172631" />
      <rect x="13" y="23" width="20" height="10" rx="5" fill="#0b151c" />
      <rect class="pet-eye" x="16" y="26" width="4" height="4" rx="1" fill="#65e3db" />
      <rect class="pet-eye" x="26" y="26" width="4" height="4" rx="1" fill="#65e3db" />
      <path d="M6 35c0-5 7-8 16-8s17 3 17 8-8 9-17 9S6 40 6 35Z" fill="#eda733" />
      <path d="M7 36h31" stroke="#b86c14" stroke-width="1.2" opacity=".65" />
      <ellipse class="pet-leg-l" cx="19" cy="50" rx="8" ry="4" fill="#eaa132" />
      <ellipse class="pet-leg-r" cx="45" cy="49" rx="8" ry="4" fill="#eaa132" />
      <path d="M43 37h5" stroke="#54d8d1" stroke-width="2" stroke-linecap="round" />
    </g>

    <g v-else-if="petId === 'owl'" class="pet-character pet-owl">
      <ellipse cx="32" cy="55" rx="22" ry="4" fill="#1b2734" opacity=".14" />
      <path d="M15 18 19 8l8 10M37 18l8-10 5 13" :fill="`url(#${owlMetalGradientId})`" stroke="#687482" stroke-width="1" />
      <circle class="pet-body pet-head" cx="32" cy="34" r="23" :fill="`url(#${owlMetalGradientId})`" stroke="#576574" stroke-width="1.2" />
      <path d="M12 30c6-11 14-13 20-4 6-9 15-7 20 4v15c-6 8-14 9-20 1-6 8-14 7-20-1Z" fill="#263341" />
      <circle cx="23" cy="33" r="11" fill="#141d28" stroke="#9ca9b7" stroke-width="2" />
      <circle cx="41" cy="33" r="11" fill="#141d28" stroke="#9ca9b7" stroke-width="2" />
      <circle class="pet-eye" cx="23" cy="33" r="7" fill="#3f79cc" />
      <circle class="pet-eye" cx="41" cy="33" r="7" fill="#3f79cc" />
      <circle cx="23" cy="33" r="3.4" fill="#0b1118" />
      <circle cx="41" cy="33" r="3.4" fill="#0b1118" />
      <circle cx="21" cy="30" r="1.5" fill="#dff7ff" />
      <circle cx="39" cy="30" r="1.5" fill="#dff7ff" />
      <path d="m32 36-4 5h8Z" fill="#65cfd0" />
      <path class="pet-leg-l" d="M21 53h8" stroke="#3d4855" stroke-width="5" stroke-linecap="round" />
      <path class="pet-leg-r" d="M36 53h8" stroke="#3d4855" stroke-width="5" stroke-linecap="round" />
    </g>

    <g v-else class="pet-character pet-glitch">
      <ellipse cx="32" cy="55" rx="23" ry="4" fill="#1b2734" opacity=".12" />
      <path d="m16 18-2-11 8 8M45 17l4-10 5 11" fill="#258e91" stroke="#1c6e73" stroke-width="1" />
      <circle class="pet-body pet-head" cx="32" cy="35" r="24" fill="#e1cba7" stroke="#c7ad83" stroke-width="2.8" stroke-dasharray="2 1.7" />
      <circle class="pet-eye" cx="23" cy="27" r="3.8" fill="#18232c" />
      <circle class="pet-eye" cx="41" cy="27" r="3.8" fill="#18232c" />
      <circle cx="22" cy="26" r="1.2" fill="#fff" />
      <circle cx="40" cy="26" r="1.2" fill="#fff" />
      <path d="M10 37c3-8 12-10 22-10s19 2 22 10v10c-7 8-15 10-22 10S16 55 10 47Z" fill="#27232a" stroke="#69747c" stroke-width="2" />
      <path d="M11 38h42" stroke="#e4e7e9" stroke-width="2" stroke-dasharray="2 2" />
      <circle cx="23" cy="43" r="5" fill="#e87b76" />
      <circle cx="32" cy="45" r="5" fill="#e7bd4c" />
      <circle cx="42" cy="42" r="5" fill="#34afb2" />
      <circle cx="21.5" cy="42" r=".7" fill="#39252c" />
      <circle cx="24.5" cy="42" r=".7" fill="#39252c" />
      <circle cx="30.5" cy="44" r=".7" fill="#39252c" />
      <circle cx="33.5" cy="44" r=".7" fill="#39252c" />
      <circle cx="40.5" cy="41" r=".7" fill="#17373a" />
      <circle cx="43.5" cy="41" r=".7" fill="#17373a" />
    </g>

    <circle
      v-if="showStatus"
      class="pet-status-beacon"
      :class="`is-${statusTone}`"
      data-testid="robot-process-status-beacon"
      :cx="statusBeacon.cx"
      :cy="statusBeacon.cy"
      r="2.7"
    />
  </svg>
</template>

<style scoped>
.pet-companion-svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
  filter: drop-shadow(0 2px 2px rgba(31, 43, 55, 0.16));
}

.pet-eye {
  transform-origin: center;
  animation: pet-sensor-breathe 2.6s ease-in-out infinite;
}

.pet-status-beacon {
  fill: #9ca7b2;
  stroke: rgba(255, 255, 255, 0.88);
  stroke-width: 1.4;
  filter: drop-shadow(0 0 2px currentColor);
}

.pet-status-beacon.is-ready { color: #42d7a4; fill: #42d7a4; }
.pet-status-beacon.is-needs-initialization,
.pet-status-beacon.is-error { color: #f06b63; fill: #f06b63; }
.pet-status-beacon.is-checking { color: #a8b0ba; fill: #a8b0ba; }

.pet-status-beacon.is-needs-initialization {
  animation: pet-status-breathe 1.7s ease-in-out infinite;
}

@keyframes pet-sensor-breathe {
  0%, 100% { opacity: .78; }
  50% { opacity: 1; }
}

@keyframes pet-status-breathe {
  0%, 100% { opacity: .55; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .pet-eye,
  .pet-status-beacon { animation: none; }
}
</style>
