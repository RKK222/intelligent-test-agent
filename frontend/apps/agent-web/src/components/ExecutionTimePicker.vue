<script setup lang="ts">
import { computed } from "vue";
import { X } from "lucide-vue-next";
import type { NightExecutionScheduleMode, NightExecutionSlots } from "@test-agent/shared-types";
import { formatBeijingDateTimeInput } from "../utils/night-execution-schedule";

const props = withDefaults(defineProps<{
  scheduleMode: NightExecutionScheduleMode;
  allowModeSwitch?: boolean;
  multiple?: boolean;
  slots?: NightExecutionSlots | null;
  loading?: boolean;
  disabled?: boolean;
  selectedTimes?: string[];
  customInput?: string;
  customError?: string;
  customMin?: string;
  customMax?: string;
  customTimes?: string[];
}>(), {
  allowModeSwitch: false,
  multiple: false,
  slots: null,
  loading: false,
  disabled: false,
  selectedTimes: () => [],
  customInput: "",
  customError: "",
  customMin: "",
  customMax: "",
  customTimes: () => []
});

const emit = defineEmits<{
  (event: "select-mode", mode: NightExecutionScheduleMode): void;
  (event: "toggle-time", slotStart: string, available: boolean): void;
  (event: "quick-offset", minutes: number): void;
  (event: "update:custom-input", value: string): void;
  (event: "add-custom-time"): void;
  (event: "remove-custom-time", value: string): void;
}>();

const selected = computed(() => new Set(props.selectedTimes));
const slotTestId = computed(() => props.multiple ? "batch-night-slot" : "night-slot-option");

function nightTime(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(new Date(value));
}

function customTime(value: string): string {
  return formatBeijingDateTimeInput(new Date(value)).replace("T", " ");
}
</script>

<template>
  <div class="execution-time-picker" :class="{ 'is-multiple': multiple }">
    <div v-if="allowModeSwitch" class="execution-time-mode" aria-label="定时模式">
      <button
        type="button"
        data-testid="schedule-mode-night"
        :class="{ 'is-active': scheduleMode === 'NIGHT_WINDOW' }"
        @click="emit('select-mode', 'NIGHT_WINDOW')"
      >夜间时段</button>
      <button
        type="button"
        data-testid="schedule-mode-custom"
        :class="{ 'is-active': scheduleMode === 'ADMIN_CUSTOM' }"
        @click="emit('select-mode', 'ADMIN_CUSTOM')"
      >测试时间</button>
    </div>

    <template v-if="scheduleMode === 'NIGHT_WINDOW'">
      <div v-if="loading" class="execution-time-empty">正在刷新剩余容量…</div>
      <div v-else-if="!slots?.slots.length" class="execution-time-empty">暂无可用夜间时间段，请稍后重试。</div>
      <div v-else class="execution-time-slots">
        <button
          v-for="slot in slots.slots"
          :key="slot.slotStart"
          type="button"
          :data-testid="slotTestId"
          :class="{
            'is-selected': selected.has(slot.slotStart),
            'is-recommended': slot.recommended
          }"
          :disabled="disabled || !slot.available"
          :title="slot.available ? `${slot.reservedCount}/${slot.capacity} 已预约` : '该时间段已满'"
          @click="emit('toggle-time', slot.slotStart, slot.available)"
        >
          <strong>{{ nightTime(slot.slotStart) }}</strong>
          <small v-if="slot.recommended">系统推荐</small>
          <small v-else-if="!slot.available">已满</small>
          <small v-else-if="multiple">{{ slot.capacity - slot.reservedCount }} 个余量</small>
          <small v-else>{{ slot.reservedCount }}/{{ slot.capacity }}</small>
        </button>
      </div>
    </template>

    <div v-else class="execution-custom-time">
      <div class="execution-custom-entry">
        <button
          v-for="minutes in [1, 3, 5]"
          :key="minutes"
          type="button"
          :data-testid="`custom-schedule-plus-${minutes}`"
          :disabled="disabled"
          @click="emit('quick-offset', minutes)"
        >{{ multiple ? `+${minutes} 分钟` : `${minutes} 分钟后` }}</button>
        <label>
          <span v-if="!multiple">计划启动时间（北京时间）</span>
          <input
            type="datetime-local"
            step="60"
            :value="customInput"
            :min="customMin"
            :max="customMax"
            :disabled="disabled"
            data-testid="custom-schedule-input"
            @input="emit('update:custom-input', ($event.target as HTMLInputElement).value)"
          />
        </label>
        <button v-if="multiple" type="button" class="execution-add-time" :disabled="disabled" @click="emit('add-custom-time')">添加时间</button>
      </div>
      <span v-if="customError" class="execution-time-error" data-testid="custom-schedule-error">{{ customError }}</span>
      <span v-else-if="!multiple" class="execution-time-hint">到达计划时间后，通常会在 1 分钟内发起执行。</span>
      <div v-if="multiple" class="execution-time-chips">
        <button v-for="time in customTimes" :key="time" type="button" :disabled="disabled" @click="emit('remove-custom-time', time)">
          {{ customTime(time) }} <X :size="12" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.execution-time-picker { display: grid; gap: 8px; }
.execution-time-mode { display: grid; grid-template-columns: 1fr 1fr; gap: 3px; padding: 3px; border: 1px solid #e0e1e7; border-radius: 8px; background: #e8edf3; }
.execution-time-mode button { min-height: 29px; border: 0; border-radius: 6px; background: transparent; color: #68778a; font: inherit; font-size: 11px; font-weight: 700; cursor: pointer; }
.execution-time-mode button.is-active { background: #fff; color: #8f2731; box-shadow: 0 1px 3px rgba(39, 50, 94, .13); }
.execution-time-slots { display: grid; grid-template-columns: repeat(5, minmax(50px, 1fr)); gap: 5px; max-height: 154px; overflow-y: auto; padding: 1px; }
.is-multiple .execution-time-slots { grid-template-columns: repeat(6, minmax(84px, 1fr)); }
.execution-time-slots button { display: grid; min-height: 43px; place-content: center; gap: 1px; border: 1px solid #d4dce5; border-radius: 7px; background: #fff; color: #405269; font: inherit; cursor: pointer; }
.execution-time-slots strong { font-size: 11px; }
.execution-time-slots small { color: #8290a0; font-size: 9px; }
.execution-time-slots button.is-recommended { border-color: #c4a66b; background: #fffdf7; }
.execution-time-slots button.is-selected { border-color: #9f2e38; background: #9f2e38; color: #fff; box-shadow: 0 0 0 1px #9f2e38; }
.execution-time-slots button.is-selected small { color: #f5e5e7; }
.execution-time-slots button:disabled { border-style: dashed; background: #f3f4f6; color: #a0a7b0; cursor: not-allowed; opacity: .62; }
.execution-time-empty { display: flex; min-height: 56px; align-items: center; justify-content: center; color: #718095; font-size: 11px; }
.execution-custom-time { display: grid; gap: 7px; padding: 9px; border: 1px solid #d7dfe8; border-radius: 9px; background: #fff; }
.execution-custom-entry { display: flex; gap: 6px; }
.execution-custom-entry > button, .execution-custom-entry input, .execution-time-chips button { min-height: 32px; border: 1px solid #cad4df; border-radius: 7px; background: #fff; color: #405269; font: inherit; font-size: 11px; }
.execution-custom-entry > button { padding: 0 9px; cursor: pointer; }
.execution-custom-entry label { display: grid; flex: 1; gap: 4px; color: #617186; font-size: 10px; }
.execution-custom-entry input { width: 100%; padding: 0 8px; }
.execution-custom-entry .execution-add-time { border-color: #8f2731; color: #8f2731; font-weight: 700; }
.execution-time-error, .execution-time-hint { font-size: 11px; }
.execution-time-error { color: #a0343d; }
.execution-time-hint { color: #718095; }
.execution-time-chips { display: flex; flex-wrap: wrap; gap: 5px; }
.execution-time-chips button { display: inline-flex; align-items: center; gap: 5px; padding: 0 8px; cursor: pointer; }
@media (max-width: 980px) { .is-multiple .execution-time-slots { grid-template-columns: repeat(3, 1fr); } }
</style>
