<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { Bomb, Pause, Play, RotateCcw } from "lucide-vue-next";

type MinerStatus = "aiming" | "extending" | "retracting" | "paused" | "success" | "failed";
type MinerItemKind = "large-gold" | "gold" | "diamond" | "rock" | "relic";
type MinerItem = {
  id: string;
  kind: MinerItemKind;
  label: string;
  value: number;
  weight: number;
  size: number;
  x: number;
  y: number;
  rotation: number;
};

const props = withDefaults(defineProps<{ active?: boolean }>(), { active: true });

const MINER_WIDTH = 316;
const MINER_HEIGHT = 410;
const MINER_ORIGIN_X = MINER_WIDTH / 2;
const MINER_ORIGIN_Y = 51;
const MINER_REST_LENGTH = 36;
const MINER_FRAME_MS = 16;
const MINER_LEVEL_MS = 45_000;
const MINER_SWING_LIMIT = 68;
const MINER_SWING_SPEED = 54;
const MINER_EXTEND_SPEED = 285;
const MINER_EMPTY_RETRACT_SPEED = 380;
const MINER_BASE_TARGET = 1_600;

const MINER_LAYOUTS: Array<Array<Omit<MinerItem, "id">>> = [
  [
    { kind: "large-gold", label: "大金块", value: 520, weight: 3.2, size: 34, x: 158, y: 158, rotation: -8 },
    { kind: "diamond", label: "钻石", value: 760, weight: 0.65, size: 21, x: 76, y: 181, rotation: 8 },
    { kind: "gold", label: "金块", value: 280, weight: 1.35, size: 25, x: 236, y: 205, rotation: 16 },
    { kind: "rock", label: "重岩", value: 80, weight: 4.4, size: 38, x: 108, y: 260, rotation: -13 },
    { kind: "relic", label: "旧钱袋", value: 360, weight: 1.8, size: 28, x: 204, y: 286, rotation: 6 },
    { kind: "gold", label: "小金块", value: 220, weight: 1.1, size: 22, x: 59, y: 337, rotation: 11 },
    { kind: "diamond", label: "深层钻石", value: 900, weight: 0.72, size: 19, x: 265, y: 340, rotation: -5 },
    { kind: "rock", label: "岩石", value: 60, weight: 3.7, size: 32, x: 153, y: 354, rotation: 4 },
  ],
  [
    { kind: "rock", label: "岩石", value: 70, weight: 3.8, size: 34, x: 158, y: 151, rotation: 4 },
    { kind: "gold", label: "金块", value: 300, weight: 1.3, size: 25, x: 86, y: 190, rotation: -11 },
    { kind: "diamond", label: "钻石", value: 820, weight: 0.62, size: 20, x: 241, y: 177, rotation: 7 },
    { kind: "large-gold", label: "大金块", value: 580, weight: 3.4, size: 36, x: 224, y: 270, rotation: 12 },
    { kind: "relic", label: "旧钱袋", value: 420, weight: 1.7, size: 28, x: 101, y: 282, rotation: -4 },
    { kind: "diamond", label: "深层钻石", value: 960, weight: 0.7, size: 19, x: 49, y: 346, rotation: 4 },
    { kind: "gold", label: "小金块", value: 240, weight: 1.05, size: 21, x: 270, y: 348, rotation: -9 },
    { kind: "rock", label: "重岩", value: 90, weight: 4.6, size: 40, x: 159, y: 362, rotation: 9 },
  ],
];

const minerLevel = ref(1);
const minerScore = ref(0);
const minerLevelStartScore = ref(0);
const minerTarget = ref(MINER_BASE_TARGET);
const minerTimeLeftMs = ref(MINER_LEVEL_MS);
const minerDynamite = ref(2);
const minerItems = ref<MinerItem[]>([]);
const minerStatus = ref<MinerStatus>("aiming");
const minerResumeStatus = ref<Exclude<MinerStatus, "paused" | "success" | "failed">>("aiming");
const minerHookAngle = ref(-56);
const minerSwingDirection = ref(1);
const minerHookLength = ref(MINER_REST_LENGTH);
const minerGrabbedItemId = ref<string | null>(null);
const minerCallout = ref("摆钩中 · 看准金块再下钩");
let minerFrameTimer: ReturnType<typeof setInterval> | null = null;

const minerGrabbedItem = computed(() => minerItems.value.find((item) => item.id === minerGrabbedItemId.value) ?? null);
const minerSecondsLeft = computed(() => Math.max(0, Math.ceil(minerTimeLeftMs.value / 1000)));
const minerProgress = computed(() => Math.min(100, Math.round(minerScore.value / minerTarget.value * 100)));
const minerTargetReached = computed(() => minerScore.value >= minerTarget.value);
const minerCanDynamite = computed(() => minerStatus.value === "retracting" && Boolean(minerGrabbedItem.value) && minerDynamite.value > 0);
const minerHookRadians = computed(() => minerHookAngle.value * Math.PI / 180);
const minerHookX = computed(() => MINER_ORIGIN_X + Math.sin(minerHookRadians.value) * minerHookLength.value);
const minerHookY = computed(() => MINER_ORIGIN_Y + Math.cos(minerHookRadians.value) * minerHookLength.value);
const minerStatusText = computed(() => {
  if (minerStatus.value === "paused") return "勘探暂停";
  if (minerStatus.value === "success") return "本层目标达成";
  if (minerStatus.value === "failed") return "本层资金不足";
  if (minerStatus.value === "extending") return "钩索下探中";
  if (minerStatus.value === "retracting") return minerGrabbedItem.value ? `回收 ${minerGrabbedItem.value.label}` : "空钩回收中";
  return "摆钩瞄准中";
});

function createMinerItems(level: number): MinerItem[] {
  const layout = MINER_LAYOUTS[(level - 1) % MINER_LAYOUTS.length]!;
  const depthOffset = Math.min(10, Math.floor((level - 1) / MINER_LAYOUTS.length) * 3);
  return layout.map((item, index) => ({
    ...item,
    id: `level-${level}-item-${index}`,
    y: Math.min(MINER_HEIGHT - item.size / 2 - 8, item.y + depthOffset),
    value: item.value + Math.max(0, level - 1) * (item.kind === "diamond" ? 70 : 25),
  }));
}

function clearMinerTimer() {
  if (minerFrameTimer) clearInterval(minerFrameTimer);
  minerFrameTimer = null;
}

function startMinerTimer() {
  clearMinerTimer();
  if (props.active && !["paused", "success", "failed"].includes(minerStatus.value)) {
    minerFrameTimer = setInterval(stepMiner, MINER_FRAME_MS);
  }
}

function resetMinerHook() {
  minerHookAngle.value = -56;
  minerSwingDirection.value = 1;
  minerHookLength.value = MINER_REST_LENGTH;
  minerGrabbedItemId.value = null;
}

function startMinerLevel(level: number, keepScore: boolean) {
  minerLevel.value = level;
  if (!keepScore) minerScore.value = 0;
  minerLevelStartScore.value = minerScore.value;
  minerTarget.value = MINER_BASE_TARGET + (level - 1) * 1_050;
  minerTimeLeftMs.value = MINER_LEVEL_MS;
  minerDynamite.value = Math.min(4, 2 + Math.floor((level - 1) / 2));
  minerItems.value = createMinerItems(level);
  minerStatus.value = "aiming";
  minerResumeStatus.value = "aiming";
  minerCallout.value = `第 ${level} 层 · 目标 ¥${minerTarget.value}`;
  resetMinerHook();
  startMinerTimer();
}

function resetMinerGame() {
  startMinerLevel(1, false);
}

function retryMinerLevel() {
  minerScore.value = minerLevelStartScore.value;
  startMinerLevel(minerLevel.value, true);
}

function nextMinerLevel() {
  startMinerLevel(minerLevel.value + 1, true);
}

function minerMaximumLength(angleRadians: number): number {
  const horizontalSpeed = Math.sin(angleRadians);
  const verticalSpeed = Math.max(0.01, Math.cos(angleRadians));
  const bottomLength = (MINER_HEIGHT - MINER_ORIGIN_Y - 10) / verticalSpeed;
  const sideLength = horizontalSpeed > 0.01
    ? (MINER_WIDTH - 10 - MINER_ORIGIN_X) / horizontalSpeed
    : horizontalSpeed < -0.01
      ? (10 - MINER_ORIGIN_X) / horizontalSpeed
      : Number.POSITIVE_INFINITY;
  return Math.min(bottomLength, sideLength);
}

function detectMinerCatch() {
  const caught = minerItems.value.find((item) => {
    if (item.id === minerGrabbedItemId.value) return false;
    return Math.hypot(minerHookX.value - item.x, minerHookY.value - item.y) <= item.size / 2 + 9;
  });
  if (!caught) return false;
  minerGrabbedItemId.value = caught.id;
  minerStatus.value = "retracting";
  minerCallout.value = `抓住 ${caught.label} · 重量 ${caught.weight.toFixed(1)}`;
  return true;
}

function finishMinerRetract() {
  const item = minerGrabbedItem.value;
  if (item) {
    minerScore.value += item.value;
    minerItems.value = minerItems.value.filter((candidate) => candidate.id !== item.id);
    minerCallout.value = `${item.label} 入账 +¥${item.value}`;
  } else {
    minerCallout.value = "空钩返回 · 继续瞄准";
  }
  minerGrabbedItemId.value = null;
  minerHookLength.value = MINER_REST_LENGTH;
  minerStatus.value = "aiming";
}

function settleMinerLevel() {
  clearMinerTimer();
  minerStatus.value = minerTargetReached.value ? "success" : "failed";
  minerCallout.value = minerTargetReached.value
    ? `超过目标 ¥${Math.max(0, minerScore.value - minerTarget.value)}`
    : `还差 ¥${minerTarget.value - minerScore.value}`;
}

function stepMiner() {
  if (!props.active || ["paused", "success", "failed"].includes(minerStatus.value)) return;
  minerTimeLeftMs.value = Math.max(0, minerTimeLeftMs.value - MINER_FRAME_MS);
  if (minerTimeLeftMs.value <= 0) {
    settleMinerLevel();
    return;
  }

  const deltaSeconds = MINER_FRAME_MS / 1000;
  if (minerStatus.value === "aiming") {
    minerHookAngle.value += minerSwingDirection.value * MINER_SWING_SPEED * deltaSeconds;
    if (Math.abs(minerHookAngle.value) >= MINER_SWING_LIMIT) {
      minerHookAngle.value = Math.sign(minerHookAngle.value) * MINER_SWING_LIMIT;
      minerSwingDirection.value *= -1;
    }
    return;
  }

  if (minerStatus.value === "extending") {
    minerHookLength.value += MINER_EXTEND_SPEED * deltaSeconds;
    if (detectMinerCatch()) return;
    if (minerHookLength.value >= minerMaximumLength(minerHookRadians.value)) {
      minerStatus.value = "retracting";
      minerCallout.value = "没有抓到 · 正在收钩";
    }
    return;
  }

  const retractSpeed = minerGrabbedItem.value
    ? 215 / minerGrabbedItem.value.weight
    : MINER_EMPTY_RETRACT_SPEED;
  minerHookLength.value = Math.max(MINER_REST_LENGTH, minerHookLength.value - retractSpeed * deltaSeconds);
  if (minerHookLength.value <= MINER_REST_LENGTH) finishMinerRetract();
}

function dropMinerHook() {
  if (minerStatus.value !== "aiming") return;
  minerStatus.value = "extending";
  minerCallout.value = "下钩 · 命中后会按重量回收";
  startMinerTimer();
}

function useMinerDynamite() {
  const item = minerGrabbedItem.value;
  if (!item || !minerCanDynamite.value) return;
  minerDynamite.value -= 1;
  minerItems.value = minerItems.value.filter((candidate) => candidate.id !== item.id);
  minerGrabbedItemId.value = null;
  minerCallout.value = `炸掉 ${item.label} · 空钩快速返回`;
}

function toggleMinerPause() {
  if (minerStatus.value === "success" || minerStatus.value === "failed") return;
  if (minerStatus.value === "paused") {
    minerStatus.value = minerResumeStatus.value;
    startMinerTimer();
    return;
  }
  minerResumeStatus.value = minerStatus.value;
  minerStatus.value = "paused";
  clearMinerTimer();
}

function minerItemStyle(item: MinerItem) {
  const grabbed = item.id === minerGrabbedItemId.value;
  return {
    left: `${grabbed ? minerHookX.value : item.x}px`,
    top: `${grabbed ? minerHookY.value + 8 : item.y}px`,
    width: `${item.size}px`,
    height: `${item.size}px`,
    "--miner-item-rotation": `${item.rotation}deg`,
  };
}

watch(() => props.active, (active) => {
  if (active) startMinerTimer();
  else clearMinerTimer();
}, { immediate: true });

onBeforeUnmount(clearMinerTimer);

startMinerLevel(1, false);

defineExpose({ dropHook: dropMinerHook, togglePause: toggleMinerPause, resetGame: resetMinerGame });
</script>

<template>
  <section class="pet-gold-miner" data-testid="pet-gold-miner" aria-label="黄金矿工游戏">
    <header class="pet-miner-instrument-row">
      <div><small>层级</small><strong>{{ minerLevel }}</strong></div>
      <div><small>资金</small><strong data-testid="pet-miner-score">¥{{ minerScore }}</strong></div>
      <div><small>目标</small><strong>¥{{ minerTarget }}</strong></div>
      <div :class="{ 'is-urgent': minerSecondsLeft <= 10 }"><small>时间</small><strong>{{ minerSecondsLeft }}s</strong></div>
    </header>

    <div class="pet-miner-progress" :class="{ 'is-complete': minerTargetReached }" aria-label="关卡目标进度">
      <span :style="{ width: `${minerProgress}%` }" />
      <small>{{ minerTargetReached ? "目标已达成，继续淘金" : `${minerProgress}%` }}</small>
    </div>

    <div
      class="pet-miner-shaft"
      data-testid="pet-miner-board"
      role="application"
      aria-label="矿井剖面，空格或向下键释放钩索，P 键暂停"
      @click="dropMinerHook"
    >
      <div class="pet-miner-skyline" aria-hidden="true">
        <span class="pet-miner-crane"><i /><b /></span>
        <span class="pet-miner-cart">MIMO<br>ORE</span>
      </div>
      <div class="pet-miner-depth-scale" aria-hidden="true"><span>10m</span><span>20m</span><span>30m</span></div>
      <div class="pet-miner-strata" aria-hidden="true"><i /><i /><i /><i /></div>

      <svg class="pet-miner-hook" :viewBox="`0 0 ${MINER_WIDTH} ${MINER_HEIGHT}`" aria-hidden="true">
        <line :x1="MINER_ORIGIN_X" :y1="MINER_ORIGIN_Y" :x2="minerHookX" :y2="minerHookY" />
        <g :transform="`translate(${minerHookX} ${minerHookY}) rotate(${-minerHookAngle})`">
          <path d="M -8 -4 L 0 3 L 8 -4 M 0 3 L 0 -8" />
        </g>
      </svg>

      <span
        v-for="item in minerItems"
        :key="item.id"
        class="pet-miner-item"
        :class="[`is-${item.kind}`, { 'is-grabbed': item.id === minerGrabbedItemId }]"
        :style="minerItemStyle(item)"
        :aria-label="`${item.label}，价值 ${item.value}`"
      >
        <i v-if="item.kind === 'diamond'" />
        <small v-if="item.kind === 'relic'">?</small>
      </span>

      <span v-if="minerStatus === 'extending'" class="pet-miner-probe-pulse" :style="{ left: `${minerHookX}px`, top: `${minerHookY}px` }" aria-hidden="true" />

      <div v-if="minerStatus === 'paused' || minerStatus === 'success' || minerStatus === 'failed'" class="pet-miner-overlay" role="status">
        <strong>{{ minerStatus === "success" ? "达标" : minerStatus === "failed" ? "未达标" : "暂停" }}</strong>
        <span>{{ minerCallout }}</span>
        <button v-if="minerStatus === 'success'" type="button" @click.stop="nextMinerLevel">进入第 {{ minerLevel + 1 }} 层</button>
        <button v-else-if="minerStatus === 'failed'" type="button" @click.stop="retryMinerLevel">重试本层</button>
        <button v-else type="button" @click.stop="toggleMinerPause"><Play :size="13" />继续勘探</button>
      </div>
    </div>

    <div class="pet-miner-callout" aria-live="polite">
      <span><strong>{{ minerStatusText }}</strong>{{ minerCallout }}</span>
      <span>炸药 × {{ minerDynamite }}</span>
    </div>

    <div class="pet-miner-controls" aria-label="黄金矿工操作">
      <button type="button" class="is-primary" :disabled="minerStatus !== 'aiming'" @click="dropMinerHook">
        {{ minerStatus === "aiming" ? "放下钩索" : minerStatus === "extending" ? "下探中" : "回收中" }}
      </button>
      <button type="button" class="is-dynamite" :disabled="!minerCanDynamite" @click="useMinerDynamite">
        <Bomb :size="13" />炸掉重物
      </button>
      <button type="button" :aria-label="minerStatus === 'paused' ? '继续黄金矿工' : '暂停黄金矿工'" @click="toggleMinerPause">
        <Play v-if="minerStatus === 'paused'" :size="13" /><Pause v-else :size="13" />
      </button>
      <button type="button" aria-label="重开黄金矿工" @click="resetMinerGame"><RotateCcw :size="13" /></button>
    </div>
    <p class="pet-miner-help">空格 / ↓ 下钩 · 抓住重物后可用炸药 · P 暂停</p>
  </section>
</template>

<style scoped>
.pet-gold-miner {
  width: 316px;
  margin: 0 auto;
  color: #183f49;
  font-family: var(--ta-font-sans, "Noto Sans SC", "PingFang SC", sans-serif);
}

.pet-miner-instrument-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  overflow: hidden;
  border: 1px solid #b6c7c8;
  border-radius: 9px 9px 0 0;
  background: #e8efec;
}

.pet-miner-instrument-row > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  padding: 5px 2px 4px;
  border-right: 1px solid #cbd7d5;
}

.pet-miner-instrument-row > div:last-child { border-right: 0; }
.pet-miner-instrument-row small { color: #627d7f; font-size: 7px; line-height: 9px; }
.pet-miner-instrument-row strong { font: 750 11px/13px var(--ta-font-mono, "Geist Mono", monospace); }
.pet-miner-instrument-row .is-urgent { background: #a94032; color: #fff2dc; }
.pet-miner-instrument-row .is-urgent small { color: #ffd6ba; }

.pet-miner-progress {
  position: relative;
  height: 12px;
  overflow: hidden;
  border-right: 1px solid #b6c7c8;
  border-left: 1px solid #b6c7c8;
  background: #d6e1dc;
}

.pet-miner-progress > span { display: block; width: 0; height: 100%; background: #d59028; transition: width 180ms ease; }
.pet-miner-progress.is-complete > span { background: #278474; }
.pet-miner-progress small { position: absolute; inset: 1px 5px auto auto; color: #264e53; font: 650 7px/10px var(--ta-font-mono, "Geist Mono", monospace); }

.pet-miner-shaft {
  position: relative;
  width: 316px;
  height: 410px;
  box-sizing: border-box;
  overflow: hidden;
  border: 6px solid #214f58;
  border-radius: 0 0 14px 14px;
  background:
    linear-gradient(166deg, transparent 0 56%, rgba(59, 44, 33, .12) 57% 59%, transparent 60%),
    radial-gradient(circle at 18% 66%, rgba(255, 225, 162, .16) 0 2px, transparent 3px),
    linear-gradient(#c78d4f 0 22%, #b87541 22% 46%, #8e5b3e 46% 71%, #64453d 71% 100%);
  box-shadow: inset 0 0 28px rgba(45, 31, 25, .35), 0 8px 16px rgba(32, 66, 70, .18);
  cursor: crosshair;
  touch-action: none;
}

.pet-miner-skyline { position: absolute; z-index: 4; inset: 0 0 auto; height: 55px; border-bottom: 3px solid #d7b66b; background: #153e49; box-shadow: 0 3px 0 rgba(58, 38, 27, .28); }
.pet-miner-skyline::after { position: absolute; left: 0; right: 0; bottom: -7px; height: 7px; background: repeating-linear-gradient(90deg, #72503c 0 18px, #9e7046 18px 32px); content: ""; }
.pet-miner-crane { position: absolute; left: 116px; bottom: 4px; width: 83px; height: 36px; border-top: 4px solid #d8a53a; transform: rotate(-2deg); }
.pet-miner-crane::before { position: absolute; left: 38px; top: -22px; width: 6px; height: 30px; background: #d8a53a; content: ""; transform: rotate(18deg); }
.pet-miner-crane i { position: absolute; left: 35px; top: -8px; width: 16px; height: 16px; border: 3px solid #e8c56a; border-radius: 50%; background: #214f58; }
.pet-miner-crane b { position: absolute; right: 0; top: -4px; width: 5px; height: 13px; background: #d8a53a; }
.pet-miner-cart { position: absolute; left: 11px; bottom: 5px; padding: 3px 6px; border: 1px solid #719099; color: #d5e6e2; font: 700 6px/7px var(--ta-font-mono, "Geist Mono", monospace); letter-spacing: .08em; }

.pet-miner-depth-scale { position: absolute; z-index: 1; right: 6px; top: 77px; bottom: 13px; display: flex; flex-direction: column; justify-content: space-around; border-right: 1px solid rgba(248, 215, 164, .35); color: rgba(255, 227, 181, .64); font: 6px var(--ta-font-mono, "Geist Mono", monospace); }
.pet-miner-depth-scale span { position: relative; right: 3px; }
.pet-miner-depth-scale span::after { position: absolute; left: calc(100% + 3px); top: 3px; width: 7px; height: 1px; background: currentColor; content: ""; }
.pet-miner-strata { position: absolute; z-index: 0; inset: 58px 0 0; pointer-events: none; }
.pet-miner-strata i { position: absolute; left: -8%; width: 116%; height: 1px; background: rgba(255, 222, 169, .18); transform: rotate(-4deg); }
.pet-miner-strata i:nth-child(1) { top: 22%; }.pet-miner-strata i:nth-child(2) { top: 43%; transform: rotate(3deg); }.pet-miner-strata i:nth-child(3) { top: 66%; transform: rotate(-2deg); }.pet-miner-strata i:nth-child(4) { top: 85%; transform: rotate(5deg); }

.pet-miner-hook { position: absolute; z-index: 5; inset: 0; width: 100%; height: 100%; overflow: visible; pointer-events: none; }
.pet-miner-hook line { stroke: #e4d5b2; stroke-width: 1.6; stroke-dasharray: 2 1; }
.pet-miner-hook path { fill: none; stroke: #f2d07a; stroke-width: 3; stroke-linecap: round; stroke-linejoin: round; filter: drop-shadow(0 1px 1px rgba(30, 24, 19, .5)); }

.pet-miner-item { position: absolute; z-index: 3; display: flex; align-items: center; justify-content: center; box-sizing: border-box; transform: translate(-50%, -50%) rotate(var(--miner-item-rotation)); transition: filter 120ms ease; pointer-events: none; }
.pet-miner-item.is-grabbed { z-index: 6; filter: drop-shadow(0 4px 3px rgba(28, 23, 18, .45)); }
.pet-miner-item.is-gold,
.pet-miner-item.is-large-gold { border: 2px solid #7e4c17; border-radius: 46% 55% 42% 60%; background: radial-gradient(circle at 34% 28%, #fff0a6 0 8%, #f2bf39 23%, #c87d16 72%); box-shadow: inset -3px -4px 0 rgba(118, 66, 14, .2), 0 2px 2px rgba(52, 34, 24, .35); }
.pet-miner-item.is-large-gold::after { color: #714015; content: "Au"; font: 750 7px var(--ta-font-mono, "Geist Mono", monospace); }
.pet-miner-item.is-diamond { clip-path: polygon(50% 0, 94% 34%, 72% 88%, 50% 100%, 26% 88%, 6% 34%); background: linear-gradient(135deg, #edffff 0 18%, #62d3d4 19% 48%, #167c8c 49% 72%, #baf9ef 73%); filter: drop-shadow(0 2px 2px rgba(28, 48, 49, .48)); }
.pet-miner-item.is-diamond i { width: 32%; height: 65%; border-left: 1px solid rgba(255,255,255,.75); border-right: 1px solid rgba(255,255,255,.55); transform: skewX(-14deg); }
.pet-miner-item.is-rock { border: 2px solid #423d3a; border-radius: 58% 42% 52% 44%; background: radial-gradient(circle at 34% 31%, #8b8178, #5e5752 65%, #403c3a 100%); box-shadow: inset -4px -4px 0 rgba(35, 31, 29, .18), 0 3px 3px rgba(43, 31, 25, .36); }
.pet-miner-item.is-rock::before { width: 26%; height: 3px; border-radius: 99px; background: rgba(38, 34, 32, .36); content: ""; transform: rotate(-20deg); }
.pet-miner-item.is-relic { border: 2px solid #5e3921; border-radius: 45% 45% 54% 54%; background: linear-gradient(145deg, #cf7441, #8e4029); box-shadow: inset 0 4px 0 #e19b61, 0 3px 3px rgba(43, 31, 25, .35); }
.pet-miner-item.is-relic small { color: #ffe0a2; font: 800 11px var(--ta-font-mono, "Geist Mono", monospace); }

.pet-miner-probe-pulse { position: absolute; z-index: 2; width: 24px; height: 24px; border: 1px solid rgba(255, 225, 164, .5); border-radius: 50%; transform: translate(-50%, -50%); animation: miner-probe 600ms ease-out infinite; pointer-events: none; }
@keyframes miner-probe { from { opacity: .8; transform: translate(-50%, -50%) scale(.35); } to { opacity: 0; transform: translate(-50%, -50%) scale(1); } }

.pet-miner-overlay { position: absolute; z-index: 10; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 20px; background: rgba(17, 48, 55, .86); color: #f7e7bf; text-align: center; backdrop-filter: blur(2px); }
.pet-miner-overlay strong { font: 800 25px/29px var(--ta-font-mono, "Geist Mono", monospace); letter-spacing: .16em; }
.pet-miner-overlay span { font-size: 10px; }
.pet-miner-overlay button { display: inline-flex; height: 30px; align-items: center; gap: 5px; padding: 0 13px; border: 1px solid #e1bd66; border-radius: 7px; background: #e0a939; color: #183d45; cursor: pointer; font-size: 10px; font-weight: 750; }

.pet-miner-callout { display: flex; min-height: 32px; align-items: center; justify-content: space-between; gap: 8px; padding: 5px 7px; border-right: 1px solid #b6c7c8; border-bottom: 1px solid #b6c7c8; border-left: 1px solid #b6c7c8; border-radius: 0 0 8px 8px; background: #eef3ef; color: #587176; font-size: 7px; }
.pet-miner-callout > span:first-child { display: flex; min-width: 0; flex-direction: column; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pet-miner-callout strong { color: #204c55; font-size: 8px; }
.pet-miner-callout > span:last-child { flex: 0 0 auto; color: #9f4c32; font-weight: 700; }

.pet-miner-controls { display: grid; grid-template-columns: 1.5fr 1.2fr 32px 32px; gap: 5px; margin-top: 6px; }
.pet-miner-controls button { display: inline-flex; min-width: 0; height: 31px; align-items: center; justify-content: center; gap: 4px; padding: 0 6px; border: 1px solid #b8c8c7; border-radius: 7px; background: #f6f8f5; color: #315960; cursor: pointer; font-size: 8px; font-weight: 700; }
.pet-miner-controls button.is-primary { border-color: #c18b28; background: #e0a939; color: #173c44; }
.pet-miner-controls button.is-dynamite { border-color: #c77b62; color: #9f402d; }
.pet-miner-controls button:disabled { cursor: default; filter: grayscale(.45); opacity: .45; }
.pet-miner-controls button:not(:disabled):focus-visible { outline: 2px solid #2c7a78; outline-offset: 1px; }
.pet-miner-help { margin: 5px 0 0; color: #78898a; font-size: 7px; line-height: 10px; text-align: center; }

@media (prefers-reduced-motion: reduce) {
  .pet-miner-progress > span,
  .pet-miner-item { transition: none; }
  .pet-miner-probe-pulse { animation: none; }
}
</style>
