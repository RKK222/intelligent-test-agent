<script setup lang="ts">
import { inject, ref, watch } from "vue";
import { ElDatePicker, ElDialog, ElOption, ElSelect } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { ApplicationWorkspaceTemplate } from "@test-agent/shared-types";

const props = withDefaults(defineProps<{
  modelValue: boolean;
  template?: ApplicationWorkspaceTemplate | null;
  creating?: boolean;
  disabled?: boolean;
}>(), {
  template: null,
  creating: false,
  disabled: false
});

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "submit", payload: { template: ApplicationWorkspaceTemplate; version: string; branch?: string }): void;
}>();

const api = inject<BackendApiClient | null>("api", null);
const versionValue = ref("");
const branch = ref("");
const branches = ref<string[]>([]);
const loadingBranches = ref(false);
let branchRequestGeneration = 0;

const resetForm = () => {
  versionValue.value = "";
  branch.value = "";
  branches.value = [];
  loadingBranches.value = false;
};

/**
 * 顶部与左下角共用同一分支加载程序；代次校验阻止关闭或切换工作空间后的迟到响应污染下一次弹窗。
 */
async function loadBranches(template: ApplicationWorkspaceTemplate, generation: number) {
  if (template.standard !== false || !template.repositoryId || !api) return;
  loadingBranches.value = true;
  try {
    const nextBranches = await api.listRepositoryBranches(template.repositoryId);
    if (generation !== branchRequestGeneration || !props.modelValue || props.template?.workspaceId !== template.workspaceId) return;
    branches.value = nextBranches;
    branch.value = nextBranches[0] ?? "";
  } catch {
    if (generation !== branchRequestGeneration) return;
    branches.value = [];
    branch.value = "";
  } finally {
    if (generation === branchRequestGeneration) loadingBranches.value = false;
  }
}

watch(
  [() => props.modelValue, () => props.template, () => props.disabled],
  ([open, template, disabled]) => {
    branchRequestGeneration += 1;
    resetForm();
    if (disabled && open) {
      emit("update:modelValue", false);
      return;
    }
    if (!open || !template) return;
    void loadBranches(template, branchRequestGeneration);
  },
  { immediate: true }
);

function confirmCreateVersion() {
  const template = props.template;
  if (!template || props.disabled || !versionValue.value) return;
  const nonStandard = template.standard === false;
  if (nonStandard && !branch.value) return;
  emit("submit", {
    template,
    version: versionValue.value.replaceAll("-", ""),
    branch: nonStandard ? branch.value : undefined
  });
  emit("update:modelValue", false);
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="`为「${template?.workspaceName ?? ''}」新增版本`"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="ta-workbench-create-version">
      <template v-if="template?.standard === false">
        <label class="ta-workbench-create-version-label">选择分支</label>
        <ElSelect
          v-model="branch"
          :loading="loadingBranches"
          placeholder="请先选择分支"
          filterable
          style="width: 100%"
        >
          <ElOption
            v-for="item in branches"
            :key="item"
            :label="item"
            :value="item"
          />
        </ElSelect>
      </template>
      <label class="ta-workbench-create-version-label">选择日期（格式 yyyyMMdd）</label>
      <ElDatePicker
        v-model="versionValue"
        type="date"
        format="YYYY-MM-DD"
        value-format="YYYYMMDD"
        placeholder="请选择日期"
        style="width: 100%"
      />
      <p class="ta-workbench-create-version-hint">提交后会在远端创建对应的工作空间版本。</p>
    </div>
    <template #footer>
      <button
        type="button"
        class="ta-workbench-create-version-cancel"
        :disabled="creating"
        @click="emit('update:modelValue', false)"
      >
        取消
      </button>
      <button
        type="button"
        class="ta-workbench-create-version-confirm"
        :disabled="!versionValue || creating || (template?.standard === false && !branch)"
        @click="confirmCreateVersion"
      >
        {{ creating ? "创建中…" : "确定" }}
      </button>
    </template>
  </ElDialog>
</template>

<style scoped>
.ta-workbench-create-version {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ta-workbench-create-version-label {
  color: #555;
  font-size: 12px;
}

.ta-workbench-create-version-hint {
  margin: 0;
  color: #999;
  font-size: 11px;
}

.ta-workbench-create-version-cancel,
.ta-workbench-create-version-confirm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 14px;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #333;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.ta-workbench-create-version-confirm {
  margin-left: 8px;
  border-color: #18181b;
  background: #18181b;
  color: #fff;
}

.ta-workbench-create-version-cancel:hover:not(:disabled) {
  border-color: #b5b5b5;
  background: #f5f5f5;
}

.ta-workbench-create-version-confirm:hover:not(:disabled) {
  background: #000;
}

.ta-workbench-create-version-cancel:disabled,
.ta-workbench-create-version-confirm:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
