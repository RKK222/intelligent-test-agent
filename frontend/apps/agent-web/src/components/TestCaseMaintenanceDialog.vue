<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElCheckbox, ElDialog, ElMessage, ElOption, ElSelect } from "element-plus";
import { Button } from "@test-agent/ui-kit";
import type { TcdsTaskTypeOption } from "@test-agent/shared-types";
import {
  hasMissingTaskTypes,
  type TestCaseMaintenanceDraft
} from "./test-case-maintenance";

const props = defineProps<{
  open: boolean;
  fileName: string;
  itemNo: string;
  cases: TestCaseMaintenanceDraft[];
  taskTypeOptions: TcdsTaskTypeOption[];
  taskTypesLoading?: boolean;
  taskTypesError?: string;
  submitting?: boolean;
}>();

const emit = defineEmits<{
  close: [];
  confirm: [cases: TestCaseMaintenanceDraft[]];
  retryTaskTypes: [];
}>();

const rows = ref<TestCaseMaintenanceDraft[]>([]);
const selectedIndexes = ref<Set<number>>(new Set());
const batchTaskTypes = ref<string[]>([]);

const allSelected = computed(() => rows.value.length > 0 && selectedIndexes.value.size === rows.value.length);
const partlySelected = computed(() => selectedIndexes.value.size > 0 && !allSelected.value);

function resetRows() {
  rows.value = props.cases.map((testCase) => ({ ...testCase, taskTypes: [...testCase.taskTypes] }));
  selectedIndexes.value = new Set(rows.value.map((_, index) => index));
  batchTaskTypes.value = [];
}

watch(() => props.open, (open) => { if (open) resetRows(); }, { immediate: true });
watch(() => props.cases, () => { if (props.open) resetRows(); }, { deep: true });

function setAllSelected(selected: boolean) {
  selectedIndexes.value = selected ? new Set(rows.value.map((_, index) => index)) : new Set();
}

function setRowSelected(index: number, selected: boolean) {
  const next = new Set(selectedIndexes.value);
  if (selected) next.add(index);
  else next.delete(index);
  selectedIndexes.value = next;
}

function applyBatchTaskTypes() {
  if (props.taskTypesLoading || props.taskTypesError) return;
  if (selectedIndexes.value.size === 0) {
    ElMessage.warning("请选择需要批量修改的案例");
    return;
  }
  if (batchTaskTypes.value.length === 0) {
    ElMessage.warning("请选择任务类型");
    return;
  }
  rows.value = rows.value.map((row, index) => selectedIndexes.value.has(index)
    ? { ...row, taskTypes: [...batchTaskTypes.value] }
    : row);
}

function confirm() {
  if (props.taskTypesLoading || props.taskTypesError || props.taskTypeOptions.length === 0) return;
  const selectedRows = rows.value.filter((_, index) => selectedIndexes.value.has(index));
  if (selectedRows.length === 0) {
    ElMessage.warning("请选择案例");
    return;
  }
  if (hasMissingTaskTypes(selectedRows)) {
    ElMessage.warning("请选择案例类型");
    return;
  }
  emit("confirm", selectedRows.map((row) => ({ ...row, taskTypes: [...row.taskTypes] })));
}

function requestClose() {
  if (!props.submitting) emit("close");
}
</script>

<template>
  <ElDialog
    :model-value="open"
    class="test-case-maintenance-dialog"
    width="min(1500px, calc(100vw - 32px))"
    :show-close="!submitting"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :destroy-on-close="true"
    append-to-body
    align-center
    @update:model-value="(visible: boolean) => { if (!visible) requestClose(); }"
  >
    <template #header>
      <div class="case-dialog-header">
        <h3>请选择案例</h3>
        <p :title="fileName">{{ fileName }}</p>
      </div>
    </template>

    <div class="case-dialog-toolbar">
      <label class="case-item-no">
        <span>需求子条目</span>
        <input :value="itemNo" readonly aria-label="需求子条目编号" />
      </label>
      <div class="case-batch-controls">
        <ElSelect
          v-model="batchTaskTypes"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          placeholder="选择批量任务类型"
          aria-label="批量任务类型"
          :disabled="submitting || taskTypesLoading || Boolean(taskTypesError)"
        >
          <ElOption v-for="option in taskTypeOptions" :key="option.value" :label="option.name" :value="option.value" />
        </ElSelect>
        <Button
          type="button"
          variant="primary"
          :disabled="submitting || taskTypesLoading || Boolean(taskTypesError)"
          @click="applyBatchTaskTypes"
        >
          批量修改任务类型
        </Button>
      </div>
    </div>

    <div v-if="taskTypesLoading" class="case-task-types-status" role="status">
      正在加载任务类型…
    </div>
    <div v-else-if="taskTypesError" class="case-task-types-status case-task-types-error" role="alert">
      <span>{{ taskTypesError }}</span>
      <Button type="button" variant="ghost" :disabled="submitting" @click="emit('retryTaskTypes')">重试</Button>
    </div>

    <div class="case-table-scroll">
      <table class="case-table">
        <colgroup>
          <col class="case-select-column" />
          <col class="case-name-column" />
          <col class="case-content-column" />
          <col class="case-content-column" />
          <col class="case-content-column" />
          <col class="case-task-column" />
        </colgroup>
        <thead>
          <tr>
            <th>
              <ElCheckbox
                :model-value="allSelected"
                :indeterminate="partlySelected"
                aria-label="选择全部案例"
                :disabled="submitting"
                @change="(value: boolean | string | number) => setAllSelected(Boolean(value))"
              />
            </th>
            <th>案例名称</th>
            <th>测试步骤</th>
            <th>测试数据</th>
            <th>预期结果</th>
            <th>任务类型</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in rows" :key="`${index}:${row.name}`">
            <td>
              <ElCheckbox
                :model-value="selectedIndexes.has(index)"
                :aria-label="`选择案例 ${row.name}`"
                :disabled="submitting"
                @change="(value: boolean | string | number) => setRowSelected(index, Boolean(value))"
              />
            </td>
            <td><div class="case-cell-text" :title="row.name">{{ row.name }}</div></td>
            <td><div class="case-cell-text" :title="row.step">{{ row.step }}</div></td>
            <td><div class="case-cell-text" :title="row.data">{{ row.data }}</div></td>
            <td><div class="case-cell-text" :title="row.expect">{{ row.expect }}</div></td>
            <td>
              <ElSelect
                v-model="row.taskTypes"
                multiple
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="请选择任务类型"
                :aria-label="`案例 ${row.name} 的任务类型`"
                :disabled="submitting || taskTypesLoading || Boolean(taskTypesError)"
              >
                <ElOption v-for="option in taskTypeOptions" :key="option.value" :label="option.name" :value="option.value" />
              </ElSelect>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <template #footer>
      <div class="case-dialog-actions">
        <Button type="button" variant="ghost" :disabled="submitting" @click="requestClose">取消</Button>
        <Button
          type="button"
          variant="primary"
          :disabled="submitting || taskTypesLoading || Boolean(taskTypesError) || taskTypeOptions.length === 0"
          @click="confirm"
        >
          {{ submitting ? "正在维护案例…" : "确定" }}
        </Button>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.case-dialog-header h3,
.case-dialog-header p {
  margin: 0;
}

.case-dialog-header h3 {
  color: var(--ta-text);
  font-size: 16px;
  font-weight: 650;
}

.case-dialog-header p {
  max-width: min(900px, 70vw);
  margin-top: 3px;
  overflow: hidden;
  color: var(--ta-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-dialog-toolbar {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
}

.case-item-no,
.case-batch-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.case-item-no span {
  color: var(--ta-muted);
  font-size: 13px;
}

.case-item-no input {
  width: 220px;
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--ta-border);
  border-radius: 5px;
  background: var(--ta-surface);
  color: var(--ta-text);
  font-size: 13px;
  outline: none;
}

.case-batch-controls :deep(.el-select) {
  width: min(360px, 34vw);
}

.case-table-scroll {
  max-height: min(66vh, 720px);
  overflow: auto;
  border: 1px solid var(--ta-border);
  border-radius: 6px;
}

.case-task-types-status {
  display: flex;
  min-height: 38px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 12px;
  border: 1px solid var(--ta-border);
  border-radius: 5px;
  background: #f7f8fa;
  color: var(--ta-muted);
  font-size: 13px;
}

.case-task-types-error {
  border-color: #f3c6c2;
  background: #fff6f5;
  color: #b42318;
}

.case-table {
  width: 100%;
  min-width: 1180px;
  border-collapse: collapse;
  table-layout: fixed;
}

.case-select-column { width: 48px; }
.case-name-column { width: 20%; }
.case-content-column { width: 19%; }
.case-task-column { width: 23%; }

.case-table thead {
  position: sticky;
  z-index: 2;
  top: 0;
  isolation: isolate;
}

.case-table th {
  height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid var(--ta-border);
  background: #f7f8fa;
  color: var(--ta-text);
  font-size: 13px;
  font-weight: 600;
  text-align: left;
}

.case-table th:first-child,
.case-table td:first-child {
  text-align: center;
}

.case-table td {
  height: 72px;
  padding: 9px 12px;
  border-bottom: 1px solid var(--ta-border);
  color: var(--ta-text);
  vertical-align: middle;
}

.case-table tbody tr:last-child td {
  border-bottom: 0;
}

.case-table tbody tr:hover td {
  background: var(--ta-hover);
}

.case-cell-text {
  display: -webkit-box;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: pre-line;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.case-table :deep(.el-select) {
  width: 100%;
}

.case-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 760px) {
  .case-dialog-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .case-item-no input,
  .case-batch-controls :deep(.el-select) {
    width: 100%;
  }

  .case-batch-controls {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
