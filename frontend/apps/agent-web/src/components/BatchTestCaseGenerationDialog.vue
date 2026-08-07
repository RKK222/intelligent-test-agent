<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { CheckCircle2, Clock3, LoaderCircle, Play, RotateCcw, Search, X } from "lucide-vue-next";
import type { NightExecutionScheduleMode, NightExecutionSlots } from "@test-agent/shared-types";
import {
  adminCustomScheduleBounds,
  customScheduleAtOffset,
  formatBeijingDateTimeInput,
  validateAdminCustomSchedule
} from "../utils/night-execution-schedule";
import type { WorkspaceRequirementReference } from "./workbench-utils";
import ExecutionTimePicker from "./ExecutionTimePicker.vue";
import {
  allocateBatchSchedule,
  type BatchGenerationRequest,
  type BatchItemExecutionState
} from "./batch-test-case-generation";

const MAX_SELECTION = 50;
const DEFAULT_REQUIREMENT = "请生成子条目测试案例。";

const props = withDefaults(defineProps<{
  open: boolean;
  references: WorkspaceRequirementReference[];
  loading?: boolean;
  running?: boolean;
  itemStates?: Record<string, BatchItemExecutionState>;
  nightSlots?: NightExecutionSlots | null;
  nightSlotsLoading?: boolean;
  canScheduleCustomTime?: boolean;
}>(), {
  loading: false,
  running: false,
  itemStates: () => ({}),
  nightSlots: null,
  nightSlotsLoading: false,
  canScheduleCustomTime: false
});

const emit = defineEmits<{
  (event: "close"): void;
  (event: "reload-candidates"): void;
  (event: "request-night-slots"): void;
  (event: "execute", request: BatchGenerationRequest): void;
}>();

const searchInput = ref<HTMLInputElement | null>(null);
const search = ref("");
const selectedIds = ref<string[]>([]);
const requirement = ref(DEFAULT_REQUIREMENT);
const scheduleOpen = ref(false);
const scheduleMode = ref<NightExecutionScheduleMode>("NIGHT_WINDOW");
const selectedNightTimes = ref<string[]>([]);
const customScheduleInput = ref("");
const customScheduleError = ref("");
const customTimes = ref<string[]>([]);
const lastRequest = ref<BatchGenerationRequest | null>(null);

const filteredReferences = computed(() => {
  const query = search.value.trim().toLowerCase();
  if (!query) return props.references;
  return props.references.filter((reference) =>
    `${reference.requirementName} ${reference.subitemName}`.toLowerCase().includes(query)
  );
});
const selectedSet = computed(() => new Set(selectedIds.value));
const selectedReferences = computed(() => props.references.filter((item) => selectedSet.value.has(item.id)));
const selectionAtLimit = computed(() => selectedIds.value.length >= MAX_SELECTION);
const allFilteredSelected = computed(() => filteredReferences.value.length > 0
  && filteredReferences.value.every((item) => selectedSet.value.has(item.id)));
const selectedScheduleTimes = computed(() => scheduleMode.value === "NIGHT_WINDOW"
  ? selectedNightTimes.value
  : customTimes.value);
const scheduleAllocation = computed(() => allocateBatchSchedule({
  itemCount: selectedIds.value.length,
  scheduleMode: scheduleMode.value,
  selectedTimes: selectedScheduleTimes.value,
  slots: props.nightSlots?.slots
}));
const failedIds = computed(() => props.references
  .filter((reference) => props.itemStates[reference.id]?.status === "failed")
  .map((reference) => reference.id));

watch(() => props.open, async (open) => {
  if (!open) return;
  emit("reload-candidates");
  await nextTick();
  searchInput.value?.focus();
}, { immediate: true });

watch(() => props.references, (references) => {
  const valid = new Set(references.map((item) => item.id));
  selectedIds.value = selectedIds.value.filter((id) => valid.has(id));
});

function requestClose() {
  if (!props.running) emit("close");
}

function toggleSelection(reference: WorkspaceRequirementReference, checked: boolean) {
  if (checked) {
    if (selectedSet.value.has(reference.id) || selectionAtLimit.value) return;
    selectedIds.value = [...selectedIds.value, reference.id];
    return;
  }
  selectedIds.value = selectedIds.value.filter((id) => id !== reference.id);
}

function toggleSelectAll() {
  if (allFilteredSelected.value) {
    const filtered = new Set(filteredReferences.value.map((item) => item.id));
    selectedIds.value = selectedIds.value.filter((id) => !filtered.has(id));
    return;
  }
  const next = [...selectedIds.value];
  const included = new Set(next);
  for (const reference of filteredReferences.value) {
    if (next.length >= MAX_SELECTION) break;
    if (!included.has(reference.id)) {
      next.push(reference.id);
      included.add(reference.id);
    }
  }
  selectedIds.value = next;
}

function executeImmediate(referenceIds = selectedIds.value, retry = false) {
  if (props.running || referenceIds.length === 0 || !requirement.value.trim()) return;
  const request: BatchGenerationRequest = {
    referenceIds: [...referenceIds],
    requirement: requirement.value,
    executionMode: "immediate",
    ...(retry ? { retry: true } : {})
  };
  lastRequest.value = request;
  emit("execute", request);
}

function openSchedule() {
  scheduleOpen.value = true;
  emit("request-night-slots");
}

function toggleNightTime(slotStart: string, available: boolean) {
  if (!available || props.running) return;
  selectedNightTimes.value = selectedNightTimes.value.includes(slotStart)
    ? selectedNightTimes.value.filter((item) => item !== slotStart)
    : [...selectedNightTimes.value, slotStart].sort();
}

function setScheduleMode(mode: NightExecutionScheduleMode) {
  scheduleMode.value = mode;
  customScheduleError.value = "";
  if (mode === "ADMIN_CUSTOM" && !customScheduleInput.value) {
    customScheduleInput.value = formatBeijingDateTimeInput(customScheduleAtOffset(new Date(), 1));
  }
}

function setCustomOffset(minutes: number) {
  customScheduleInput.value = formatBeijingDateTimeInput(customScheduleAtOffset(new Date(), minutes));
  customScheduleError.value = "";
}

function addCustomTime() {
  const validation = validateAdminCustomSchedule(customScheduleInput.value, new Date());
  if (!validation.valid) {
    customScheduleError.value = validation.reason;
    return;
  }
  customScheduleError.value = "";
  customTimes.value = Array.from(new Set([...customTimes.value, validation.slotStart])).sort();
}

function updateCustomScheduleInput(value: string) {
  customScheduleInput.value = value;
  customScheduleError.value = "";
}

function executeScheduled(referenceIds = selectedIds.value, retry = false) {
  if (props.running || referenceIds.length === 0 || !requirement.value.trim()) return;
  const allocation = allocateBatchSchedule({
    itemCount: referenceIds.length,
    scheduleMode: scheduleMode.value,
    selectedTimes: selectedScheduleTimes.value,
    slots: props.nightSlots?.slots
  });
  if (!allocation.ok) return;
  const request: BatchGenerationRequest = {
    referenceIds: [...referenceIds],
    requirement: requirement.value,
    executionMode: "scheduled",
    scheduleMode: scheduleMode.value,
    slotStarts: [...selectedScheduleTimes.value].sort(),
    ...(retry ? { retry: true } : {})
  };
  lastRequest.value = request;
  emit("execute", request);
}

function retryFailed() {
  if (!lastRequest.value || failedIds.value.length === 0 || props.running) return;
  if (lastRequest.value.executionMode === "immediate") {
    executeImmediate(failedIds.value, true);
  } else {
    executeScheduled(failedIds.value, true);
  }
}

function statusText(state?: BatchItemExecutionState): string {
  switch (state?.status) {
    case "loading-context": return "加载资料";
    case "creating-session": return "创建会话";
    case "starting-run": return "启动执行";
    case "creating-task": return "创建任务";
    case "succeeded": return "成功";
    case "failed": return state.message || "失败";
    default: return "待执行";
  }
}

const customBounds = computed(() => adminCustomScheduleBounds(new Date()));
</script>

<template>
  <div v-if="open" class="batch-dialog-overlay" @click.self="requestClose">
    <div
      class="batch-dialog"
      style="width: 70vw; height: 70vh"
      role="dialog"
      aria-modal="true"
      aria-labelledby="batch-dialog-title"
      data-testid="batch-test-case-dialog"
    >
      <header class="batch-dialog-head">
        <div>
          <span class="batch-kicker">BATCH CASES</span>
          <h2 id="batch-dialog-title">批量生成子条目测试案例</h2>
          <p>候选目录与输入框 <strong>#</strong> 完全一致，单批最多选择 {{ MAX_SELECTION }} 项。</p>
        </div>
        <button
          type="button"
          class="batch-icon-button"
          aria-label="关闭批量生成"
          data-testid="batch-dialog-close"
          :disabled="running"
          @click="requestClose"
        ><X :size="18" /></button>
      </header>

      <section class="batch-toolbar">
        <label class="batch-search">
          <Search :size="16" />
          <input ref="searchInput" v-model="search" type="search" placeholder="搜索需求项或子条目" />
        </label>
        <button type="button" class="batch-secondary" data-testid="batch-select-all" :disabled="running" @click="toggleSelectAll">
          {{ allFilteredSelected ? "取消全选" : "全选当前结果" }}
        </button>
        <span class="batch-count" :class="{ 'is-limit': selectionAtLimit }">已选 {{ selectedIds.length }}/{{ MAX_SELECTION }}</span>
      </section>

      <main class="batch-dialog-body">
        <div class="batch-table-head">
          <span>选择</span><span>需求项 / 子条目</span><span>关联文件</span><span>执行状态</span>
        </div>
        <div v-if="loading" class="batch-empty"><LoaderCircle class="is-spinning" :size="18" /> 正在读取当前工作区需求结构…</div>
        <div v-else-if="filteredReferences.length === 0" class="batch-empty">当前工作区没有匹配的需求子条目</div>
        <label v-for="reference in filteredReferences" v-else :key="reference.id" class="batch-row">
          <span>
            <input
              type="checkbox"
              data-testid="batch-item-checkbox"
              :checked="selectedSet.has(reference.id)"
              :disabled="running || (!selectedSet.has(reference.id) && selectionAtLimit)"
              @change="toggleSelection(reference, ($event.target as HTMLInputElement).checked)"
            />
          </span>
          <span class="batch-reference">
            <strong>{{ reference.subitemName }}</strong>
            <small>{{ reference.requirementName }}</small>
          </span>
          <span class="batch-file-count">{{ reference.filePaths.length }} 个</span>
          <span class="batch-status" :class="`is-${itemStates[reference.id]?.status ?? 'idle'}`">
            <CheckCircle2 v-if="itemStates[reference.id]?.status === 'succeeded'" :size="14" />
            <LoaderCircle v-else-if="itemStates[reference.id] && !['idle', 'failed'].includes(itemStates[reference.id]!.status)" class="is-spinning" :size="14" />
            {{ statusText(itemStates[reference.id]) }}
          </span>
        </label>
      </main>

      <section v-if="scheduleOpen" class="batch-schedule-panel">
        <div class="batch-schedule-head">
          <strong>选择定时执行时间</strong>
        </div>
        <ExecutionTimePicker
          multiple
          :schedule-mode="scheduleMode"
          :allow-mode-switch="canScheduleCustomTime"
          :slots="nightSlots"
          :loading="nightSlotsLoading"
          :disabled="running"
          :selected-times="selectedNightTimes"
          :custom-input="customScheduleInput"
          :custom-error="customScheduleError"
          :custom-min="customBounds.min"
          :custom-max="customBounds.max"
          :custom-times="customTimes"
          @select-mode="setScheduleMode"
          @toggle-time="toggleNightTime"
          @quick-offset="setCustomOffset"
          @update:custom-input="updateCustomScheduleInput"
          @add-custom-time="addCustomTime"
          @remove-custom-time="(time) => customTimes = customTimes.filter((item) => item !== time)"
        />
        <div class="batch-schedule-foot">
          <span v-if="!scheduleAllocation.ok" class="batch-error">所选时段总余量 {{ scheduleAllocation.remainingCapacity }}，不足以安排 {{ selectedIds.length }} 个子条目。</span>
          <span v-else>将按时间升序轮询分配 {{ selectedScheduleTimes.length }} 个时间段。</span>
          <button
            type="button"
            class="batch-primary"
            data-testid="batch-execute-scheduled"
            :disabled="running || selectedIds.length === 0 || !scheduleAllocation.ok"
            @click="executeScheduled()"
          ><Clock3 :size="15" /> 定时执行</button>
        </div>
      </section>

      <footer class="batch-dialog-foot">
        <label class="batch-requirement">
          <span>批量案例生成要求</span>
          <textarea v-model="requirement" data-testid="batch-requirement-input" :disabled="running" maxlength="20000" />
        </label>
        <div class="batch-foot-actions">
          <span v-if="selectionAtLimit" class="batch-limit-hint">最多选择 50 个子条目</span>
          <button v-if="failedIds.length" type="button" class="batch-secondary" :disabled="running" @click="retryFailed">
            <RotateCcw :size="15" /> 仅重试失败项（{{ failedIds.length }}）
          </button>
          <button type="button" class="batch-secondary" data-testid="batch-open-schedule" :disabled="running || selectedIds.length === 0" @click="openSchedule">
            <Clock3 :size="15" /> 选择定时
          </button>
          <button type="button" class="batch-primary" data-testid="batch-execute-now" :disabled="running || selectedIds.length === 0 || !requirement.trim()" @click="executeImmediate()">
            <Play :size="15" /> 立刻执行
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.batch-dialog-overlay {
  position: fixed;
  z-index: 2200;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(18, 25, 42, 0.42);
  backdrop-filter: blur(5px);
}
.batch-dialog {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto auto;
  max-width: 1180px;
  min-width: 720px;
  overflow: hidden;
  border: 1px solid #cdd6e2;
  border-radius: 18px;
  background: #f7f9fc;
  color: #263548;
  box-shadow: 0 26px 80px rgba(20, 31, 51, 0.28);
}
.batch-dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 22px 16px;
  border-bottom: 1px solid #dde4ec;
  background: linear-gradient(135deg, #fff 0%, #f1f6fb 72%, #f8eef0 100%);
}
.batch-dialog-head h2 { margin: 2px 0 4px; font-size: 20px; line-height: 28px; }
.batch-dialog-head p { margin: 0; color: #68778a; font-size: 12px; }
.batch-kicker { color: #a62b35; font-size: 10px; font-weight: 800; letter-spacing: .16em; }
.batch-icon-button {
  display: grid; width: 34px; height: 34px; place-items: center; border: 0; border-radius: 9px;
  background: #fff; color: #58687b; cursor: pointer; box-shadow: 0 1px 4px rgba(23, 41, 62, .12);
}
.batch-icon-button:disabled { cursor: not-allowed; opacity: .45; }
.batch-toolbar { display: flex; align-items: center; gap: 10px; padding: 10px 16px; border-bottom: 1px solid #e1e7ee; background: #fff; }
.batch-search { display: flex; flex: 1; align-items: center; gap: 8px; min-height: 36px; padding: 0 11px; border: 1px solid #ccd6e1; border-radius: 9px; background: #f9fbfd; }
.batch-search:focus-within { border-color: #8c2831; box-shadow: 0 0 0 2px rgba(140, 40, 49, .09); }
.batch-search input { width: 100%; border: 0; outline: 0; background: transparent; color: inherit; font: inherit; font-size: 13px; }
.batch-secondary, .batch-primary { display: inline-flex; min-height: 34px; align-items: center; justify-content: center; gap: 6px; padding: 0 12px; border-radius: 8px; font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; }
.batch-secondary { border: 1px solid #cbd5e1; background: #fff; color: #405269; }
.batch-primary { border: 1px solid #8f2731; background: #9f2e38; color: #fff; }
.batch-secondary:disabled, .batch-primary:disabled { cursor: not-allowed; opacity: .45; }
.batch-count { color: #6d7b8b; font-size: 12px; font-variant-numeric: tabular-nums; }
.batch-count.is-limit { color: #9f2e38; font-weight: 700; }
.batch-dialog-body { min-height: 0; overflow: auto; padding: 0 16px 10px; background: #f7f9fc; }
.batch-table-head, .batch-row { display: grid; grid-template-columns: 56px minmax(240px, 1fr) 110px 150px; align-items: center; }
.batch-table-head { position: sticky; z-index: 2; top: 0; min-height: 36px; border-bottom: 1px solid #dfe6ee; background: #f7f9fc; color: #7b8999; font-size: 11px; font-weight: 700; }
.batch-row { min-height: 52px; border-bottom: 1px solid #e5eaf0; background: #fff; cursor: pointer; }
.batch-row:hover { background: #f3f7fb; }
.batch-row > span:first-child { display: grid; place-items: center; }
.batch-row input { width: 16px; height: 16px; accent-color: #9f2e38; }
.batch-reference { display: grid; gap: 2px; }
.batch-reference strong { overflow: hidden; color: #27384b; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.batch-reference small, .batch-file-count { color: #758496; font-size: 11px; }
.batch-status { display: inline-flex; align-items: center; gap: 5px; color: #68778a; font-size: 11px; }
.batch-status.is-succeeded { color: #28724f; }
.batch-status.is-failed { color: #a0343d; }
.batch-empty { display: flex; min-height: 140px; align-items: center; justify-content: center; gap: 8px; color: #758496; font-size: 13px; }
.batch-schedule-panel { display: grid; gap: 10px; max-height: 220px; overflow: auto; padding: 12px 16px; border-top: 1px solid #dce4ec; background: #eef4f9; }
.batch-schedule-head, .batch-schedule-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.batch-schedule-head strong { font-size: 13px; }
.batch-mode-switch { display: flex; gap: 3px; padding: 3px; border-radius: 8px; background: #dfe7ef; }
.batch-mode-switch button { min-height: 28px; border: 0; border-radius: 6px; background: transparent; color: #657589; font: inherit; font-size: 11px; cursor: pointer; }
.batch-mode-switch button.active { background: #fff; color: #8f2731; box-shadow: 0 1px 3px rgba(31, 47, 67, .13); }
.batch-night-slots { display: grid; grid-template-columns: repeat(6, minmax(84px, 1fr)); gap: 6px; }
.batch-night-slots button { display: grid; min-height: 43px; place-content: center; gap: 2px; border: 1px solid #ccd7e2; border-radius: 8px; background: #fff; color: #43556b; font: inherit; cursor: pointer; }
.batch-night-slots button.selected { border-color: #9f2e38; background: #9f2e38; color: #fff; }
.batch-night-slots button:disabled { cursor: not-allowed; opacity: .45; }
.batch-night-slots small { font-size: 9px; opacity: .78; }
.batch-custom-times { display: grid; gap: 7px; }
.batch-custom-entry { display: flex; gap: 6px; }
.batch-custom-entry button, .batch-custom-entry input, .batch-time-chips button { min-height: 32px; border: 1px solid #c8d3df; border-radius: 7px; background: #fff; color: #405269; font: inherit; font-size: 11px; }
.batch-custom-entry button { padding: 0 9px; cursor: pointer; }
.batch-custom-entry input { flex: 1; padding: 0 8px; }
.batch-custom-entry .batch-add-time { border-color: #8f2731; color: #8f2731; font-weight: 700; }
.batch-time-chips { display: flex; flex-wrap: wrap; gap: 5px; }
.batch-time-chips button { display: inline-flex; align-items: center; gap: 5px; padding: 0 8px; cursor: pointer; }
.batch-schedule-foot { color: #617186; font-size: 11px; }
.batch-error { color: #a0343d; font-size: 11px; }
.batch-dialog-foot { display: grid; grid-template-columns: minmax(300px, 1fr) auto; align-items: end; gap: 14px; padding: 12px 16px 14px; border-top: 1px solid #dce4ec; background: #fff; }
.batch-requirement { display: grid; gap: 5px; color: #53657a; font-size: 11px; font-weight: 700; }
.batch-requirement textarea { min-height: 58px; max-height: 100px; resize: vertical; padding: 9px 10px; border: 1px solid #cbd6e1; border-radius: 9px; color: #27384b; font: inherit; font-size: 12px; line-height: 18px; }
.batch-requirement textarea:focus { border-color: #8f2731; box-shadow: 0 0 0 2px rgba(143, 39, 49, .08); outline: 0; }
.batch-foot-actions { display: flex; align-items: center; justify-content: flex-end; gap: 7px; }
.batch-limit-hint { color: #9f2e38; font-size: 10px; }
.is-spinning { animation: batch-spin .8s linear infinite; }
@keyframes batch-spin { to { transform: rotate(360deg); } }
@media (max-width: 980px) {
  .batch-dialog { width: calc(100vw - 24px) !important; min-width: 0; }
  .batch-dialog-foot { grid-template-columns: 1fr; }
  .batch-night-slots { grid-template-columns: repeat(3, 1fr); }
}
</style>
