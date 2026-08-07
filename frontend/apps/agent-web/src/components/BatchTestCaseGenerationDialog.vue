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
  batchReferenceTestId,
  type BatchExecutionControls,
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
  (event: "close", result: { incompleteCount: number }): void;
  (event: "reload-candidates"): void;
  (event: "request-night-slots"): void;
  (event: "execute", request: BatchGenerationRequest, controls: BatchExecutionControls): void;
}>();

type BatchDialogStage = "selection" | "progress";

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
const dialogStage = ref<BatchDialogStage>("selection");
const activeRequest = ref<BatchGenerationRequest | null>(null);
const activeReferenceIds = ref<string[]>([]);
const localSubmitting = ref(false);
const executionObserved = ref(false);
let submissionSequence = 0;

const filteredReferences = computed(() => {
  const query = search.value.trim().toLowerCase();
  if (!query) return props.references;
  return props.references.filter((reference) =>
    `${reference.requirementName} ${reference.subitemName}`.toLowerCase().includes(query)
  );
});
const selectedSet = computed(() => new Set(selectedIds.value));
const selectedReferences = computed(() => props.references.filter((item) => selectedSet.value.has(item.id)));
const activeReferenceSet = computed(() => new Set(activeReferenceIds.value));
const activeReferences = computed(() => props.references.filter((item) => activeReferenceSet.value.has(item.id)));
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
const failedIds = computed(() => activeReferences.value
  .filter((reference) => props.itemStates[reference.id]?.status === "failed")
  .map((reference) => reference.id));
const capacityConflictIds = computed(() => failedIds.value.filter(
  (id) => props.itemStates[id]?.errorCode === "SLOT_CAPACITY_CONFLICT"
));
const retryScheduleAllocation = computed(() => allocateBatchSchedule({
  itemCount: capacityConflictIds.value.length,
  scheduleMode: activeRequest.value?.scheduleMode ?? scheduleMode.value,
  selectedTimes: selectedScheduleTimes.value,
  slots: props.nightSlots?.slots
}));
const executionLocked = computed(() => props.running || localSubmitting.value);
const createdSessionCount = computed(() => activeReferences.value.filter(
  (reference) => Boolean(props.itemStates[reference.id]?.sessionId)
).length);
const incompleteCount = computed(() => activeReferences.value.length - createdSessionCount.value);
const succeededCount = computed(() => activeReferences.value.filter(
  (reference) => props.itemStates[reference.id]?.status === "succeeded"
).length);

watch(() => props.open, async (open) => {
  if (!open) {
    resetDialogState();
    return;
  }
  emit("reload-candidates");
  await nextTick();
  searchInput.value?.focus();
}, { immediate: true });

watch(() => props.running, (running) => {
  if (dialogStage.value !== "progress") return;
  if (running) {
    executionObserved.value = true;
    localSubmitting.value = false;
    return;
  }
  if (executionObserved.value) {
    executionObserved.value = false;
    localSubmitting.value = false;
  }
});

watch(capacityConflictIds, (current, previous) => {
  if (current.length === 0 || previous.length > 0) return;
  selectedNightTimes.value = [];
  customTimes.value = [];
  emit("request-night-slots");
});

watch(() => props.references, (references) => {
  const valid = new Set(references.map((item) => item.id));
  selectedIds.value = selectedIds.value.filter((id) => valid.has(id));
});

function requestClose() {
  if (executionLocked.value) return;
  emit("close", { incompleteCount: incompleteCount.value });
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

function executeImmediate() {
  if (executionLocked.value || selectedIds.value.length === 0 || !requirement.value.trim()) return;
  const request: BatchGenerationRequest = {
    referenceIds: [...selectedIds.value],
    requirement: requirement.value,
    executionMode: "immediate"
  };
  beginExecution(request);
}

function openSchedule() {
  scheduleOpen.value = true;
  emit("request-night-slots");
}

function toggleNightTime(slotStart: string, available: boolean) {
  if (!available || executionLocked.value) return;
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

function executeScheduled() {
  if (executionLocked.value || selectedIds.value.length === 0 || !requirement.value.trim()) return;
  const allocation = allocateBatchSchedule({
    itemCount: selectedIds.value.length,
    scheduleMode: scheduleMode.value,
    selectedTimes: selectedScheduleTimes.value,
    slots: props.nightSlots?.slots
  });
  if (!allocation.ok) return;
  const request: BatchGenerationRequest = {
    referenceIds: [...selectedIds.value],
    requirement: requirement.value,
    executionMode: "scheduled",
    scheduleMode: scheduleMode.value,
    slotStarts: [...selectedScheduleTimes.value].sort()
  };
  beginExecution(request);
}

/** 先切换到进度页再通知父层，关闭同一渲染帧内的重复点击窗口。 */
function beginExecution(request: BatchGenerationRequest) {
  if (dialogStage.value !== "selection" || executionLocked.value) return;
  activeRequest.value = request;
  activeReferenceIds.value = [...request.referenceIds];
  lastRequest.value = request;
  dialogStage.value = "progress";
  emitExecution(request, true);
}

function emitExecution(request: BatchGenerationRequest, firstSubmission: boolean) {
  localSubmitting.value = true;
  executionObserved.value = false;
  const sequence = ++submissionSequence;
  emit("execute", request, {
    reject: () => {
      if (sequence !== submissionSequence || props.running) return;
      localSubmitting.value = false;
      if (!firstSubmission) return;
      dialogStage.value = "selection";
      activeRequest.value = null;
      activeReferenceIds.value = [];
      lastRequest.value = null;
    }
  });
}

function retryReferences(referenceIds: string[]) {
  const request = activeRequest.value;
  if (!request || executionLocked.value || referenceIds.length === 0) return;
  const includesCapacityConflict = referenceIds.some((id) => capacityConflictIds.value.includes(id));
  if (includesCapacityConflict && !retryScheduleAllocation.value.ok) return;
  emitExecution({
    ...request,
    referenceIds: [...referenceIds],
    retry: true,
    ...(request.executionMode === "scheduled" && includesCapacityConflict
      ? { slotStarts: [...selectedScheduleTimes.value].sort() }
      : {})
  }, false);
}

function resetDialogState() {
  search.value = "";
  selectedIds.value = [];
  requirement.value = DEFAULT_REQUIREMENT;
  scheduleOpen.value = false;
  scheduleMode.value = "NIGHT_WINDOW";
  selectedNightTimes.value = [];
  customScheduleInput.value = "";
  customScheduleError.value = "";
  customTimes.value = [];
  lastRequest.value = null;
  activeRequest.value = null;
  activeReferenceIds.value = [];
  dialogStage.value = "selection";
  localSubmitting.value = false;
  executionObserved.value = false;
  submissionSequence += 1;
}

function retryDisabled(referenceId?: string): boolean {
  if (executionLocked.value) return true;
  if (!referenceId || !capacityConflictIds.value.includes(referenceId)) return false;
  return !retryScheduleAllocation.value.ok;
}

function failureText(state?: BatchItemExecutionState): string {
  if (state?.status === "failed" && state.sessionId) {
    return "会话已创建，执行启动失败";
  }
  return statusText(state);
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
      :class="{
        'is-progress': dialogStage === 'progress',
        'has-retry-schedule': dialogStage === 'progress' && capacityConflictIds.length > 0
      }"
      style="width: 70vw; height: 70vh"
      role="dialog"
      aria-modal="true"
      aria-labelledby="batch-dialog-title"
      data-testid="batch-test-case-dialog"
    >
      <header class="batch-dialog-head">
        <div>
          <span class="batch-kicker">BATCH CASES</span>
          <h2 id="batch-dialog-title">{{ dialogStage === "selection" ? "批量生成子条目测试案例" : "会话创建情况" }}</h2>
          <p v-if="dialogStage === 'selection'">候选目录与输入框 <strong>#</strong> 完全一致，单批最多选择 {{ MAX_SELECTION }} 项。</p>
          <p v-else>本页不会自动关闭；失败项可重试，主动关闭后本次批量创建即结束。</p>
        </div>
        <button
          type="button"
          class="batch-icon-button"
          aria-label="关闭批量生成"
          data-testid="batch-dialog-close"
          :disabled="executionLocked"
          @click="requestClose"
        ><X :size="18" /></button>
      </header>

      <section v-if="dialogStage === 'selection'" class="batch-toolbar">
        <label class="batch-search">
          <Search :size="16" />
          <input ref="searchInput" v-model="search" type="search" placeholder="搜索需求项或子条目" />
        </label>
        <button type="button" class="batch-secondary" data-testid="batch-select-all" :disabled="running" @click="toggleSelectAll">
          {{ allFilteredSelected ? "取消全选" : "全选当前结果" }}
        </button>
        <span class="batch-count" :class="{ 'is-limit': selectionAtLimit }">已选 {{ selectedIds.length }}/{{ MAX_SELECTION }}</span>
      </section>

      <main v-if="dialogStage === 'selection'" class="batch-dialog-body">
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

      <main v-else class="batch-dialog-body batch-progress" data-testid="batch-creation-progress">
        <div class="batch-progress-summary">
          <span>已选 <strong>{{ activeReferences.length }}</strong></span>
          <span>已创建会话 <strong>{{ createdSessionCount }}</strong></span>
          <span>成功 <strong>{{ succeededCount }}</strong></span>
          <span :class="{ 'is-error': failedIds.length > 0 }">失败 <strong>{{ failedIds.length }}</strong></span>
          <span v-if="executionLocked" class="batch-progress-running"><LoaderCircle class="is-spinning" :size="14" /> 正在创建，请稍候</span>
        </div>
        <div class="batch-table-head is-progress">
          <span>需求项 / 子条目</span><span>关联文件</span><span>会话</span><span>创建状态</span><span>操作</span>
        </div>
        <div v-for="reference in activeReferences" :key="reference.id" class="batch-row is-progress">
          <span class="batch-reference">
            <strong>{{ reference.subitemName }}</strong>
            <small>{{ reference.requirementName }}</small>
          </span>
          <span class="batch-file-count">{{ reference.filePaths.length }} 个</span>
          <span class="batch-session-id" :title="itemStates[reference.id]?.sessionId">
            {{ itemStates[reference.id]?.sessionId || "尚未创建" }}
          </span>
          <span class="batch-status" :class="`is-${itemStates[reference.id]?.status ?? 'idle'}`">
            <CheckCircle2 v-if="itemStates[reference.id]?.status === 'succeeded'" :size="14" />
            <LoaderCircle v-else-if="itemStates[reference.id] && !['idle', 'failed'].includes(itemStates[reference.id]!.status)" class="is-spinning" :size="14" />
            {{ failureText(itemStates[reference.id]) }}
          </span>
          <span>
            <button
              v-if="itemStates[reference.id]?.status === 'failed'"
              type="button"
              class="batch-secondary batch-row-retry"
              :data-testid="`batch-retry-item-${batchReferenceTestId(reference.id)}`"
              :disabled="retryDisabled(reference.id)"
              @click="retryReferences([reference.id])"
            ><RotateCcw :size="13" /> 重试</button>
          </span>
        </div>
      </main>

      <section v-if="dialogStage === 'selection' && scheduleOpen" class="batch-schedule-panel">
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

      <section
        v-if="dialogStage === 'progress' && activeRequest?.executionMode === 'scheduled' && capacityConflictIds.length > 0"
        class="batch-schedule-panel"
        data-testid="batch-retry-schedule"
      >
        <div class="batch-schedule-head">
          <strong>为 {{ capacityConflictIds.length }} 个容量冲突项重新选择时间</strong>
        </div>
        <ExecutionTimePicker
          multiple
          :schedule-mode="activeRequest.scheduleMode ?? scheduleMode"
          :allow-mode-switch="false"
          :slots="nightSlots"
          :loading="nightSlotsLoading"
          :disabled="executionLocked"
          :selected-times="selectedNightTimes"
          :custom-input="customScheduleInput"
          :custom-error="customScheduleError"
          :custom-min="customBounds.min"
          :custom-max="customBounds.max"
          :custom-times="customTimes"
          @toggle-time="toggleNightTime"
          @quick-offset="setCustomOffset"
          @update:custom-input="updateCustomScheduleInput"
          @add-custom-time="addCustomTime"
          @remove-custom-time="(time) => customTimes = customTimes.filter((item) => item !== time)"
        />
        <span v-if="!retryScheduleAllocation.ok" class="batch-error">
          所选时段总余量 {{ retryScheduleAllocation.remainingCapacity }}，不足以重试 {{ capacityConflictIds.length }} 个子条目。
        </span>
      </section>

      <footer v-if="dialogStage === 'selection'" class="batch-dialog-foot">
        <label class="batch-requirement">
          <span>批量案例生成要求</span>
          <textarea v-model="requirement" data-testid="batch-requirement-input" :disabled="running" maxlength="20000" />
        </label>
        <div class="batch-foot-actions">
          <span v-if="selectionAtLimit" class="batch-limit-hint">最多选择 50 个子条目</span>
          <button type="button" class="batch-secondary" data-testid="batch-open-schedule" :disabled="running || selectedIds.length === 0" @click="openSchedule">
            <Clock3 :size="15" /> 选择定时
          </button>
          <button type="button" class="batch-primary" data-testid="batch-execute-now" :disabled="running || selectedIds.length === 0 || !requirement.trim()" @click="executeImmediate()">
            <Play :size="15" /> 立刻执行
          </button>
        </div>
      </footer>
      <footer v-else class="batch-dialog-foot batch-progress-foot">
        <span>关闭后本次批量创建结束；已创建的会话、Run 和定时任务不会取消。</span>
        <div class="batch-foot-actions">
          <button
            v-if="failedIds.length"
            type="button"
            class="batch-secondary"
            data-testid="batch-retry-all"
            :disabled="executionLocked || (capacityConflictIds.length > 0 && !retryScheduleAllocation.ok)"
            @click="retryReferences(failedIds)"
          ><RotateCcw :size="15" /> 重试全部失败项（{{ failedIds.length }}）</button>
          <button type="button" class="batch-primary" :disabled="executionLocked" @click="requestClose">关闭</button>
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
.batch-dialog.is-progress { grid-template-rows: auto minmax(0, 1fr) auto; }
.batch-dialog.is-progress.has-retry-schedule { grid-template-rows: auto minmax(0, 1fr) auto auto; }
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
.batch-table-head.is-progress, .batch-row.is-progress { grid-template-columns: minmax(190px, 1fr) 90px minmax(150px, .8fr) minmax(180px, 1fr) 80px; gap: 10px; }
.batch-table-head { position: sticky; z-index: 2; top: 0; min-height: 36px; border-bottom: 1px solid #dfe6ee; background: #f7f9fc; color: #7b8999; font-size: 11px; font-weight: 700; }
.batch-row { min-height: 52px; border-bottom: 1px solid #e5eaf0; background: #fff; cursor: pointer; }
.batch-row:hover { background: #f3f7fb; }
.batch-row.is-progress { padding: 0 10px; cursor: default; }
.batch-row.is-progress:hover { background: #fff; }
.batch-row > span:first-child { display: grid; place-items: center; }
.batch-row input { width: 16px; height: 16px; accent-color: #9f2e38; }
.batch-reference { display: grid; gap: 2px; }
.batch-reference strong { overflow: hidden; color: #27384b; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.batch-reference small, .batch-file-count { color: #758496; font-size: 11px; }
.batch-session-id { overflow: hidden; color: #607187; font-family: var(--font-mono); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.batch-status { display: inline-flex; align-items: center; gap: 5px; color: #68778a; font-size: 11px; }
.batch-status.is-succeeded { color: #28724f; }
.batch-status.is-failed { color: #a0343d; }
.batch-empty { display: flex; min-height: 140px; align-items: center; justify-content: center; gap: 8px; color: #758496; font-size: 13px; }
.batch-progress { padding-top: 0; }
.batch-progress-summary { position: sticky; z-index: 3; top: 0; display: flex; min-height: 42px; align-items: center; gap: 18px; border-bottom: 1px solid #dfe6ee; background: #eef4f9; color: #607187; font-size: 11px; }
.batch-progress-summary strong { color: #27384b; font-size: 13px; }
.batch-progress-summary .is-error strong { color: #a0343d; }
.batch-progress-running { display: inline-flex; align-items: center; gap: 5px; margin-left: auto; color: #8f2731; font-weight: 700; }
.batch-row-retry { min-height: 28px; padding: 0 8px; }
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
.batch-progress-foot { align-items: center; color: #657589; font-size: 11px; }
.is-spinning { animation: batch-spin .8s linear infinite; }
@keyframes batch-spin { to { transform: rotate(360deg); } }
@media (max-width: 980px) {
  .batch-dialog { width: calc(100vw - 24px) !important; min-width: 0; }
  .batch-dialog-foot { grid-template-columns: 1fr; }
  .batch-night-slots { grid-template-columns: repeat(3, 1fr); }
}
</style>
