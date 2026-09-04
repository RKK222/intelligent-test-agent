<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { ElDatePicker, ElDialog, ElOption, ElSelect } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { ApplicationWorkspaceTemplate } from "@test-agent/shared-types";
import { isValidStandardWorkspaceBranch } from "./standard-workspace-branch";

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
  (e: "submit", payload: { template: ApplicationWorkspaceTemplate; version?: string; branch: string }): void;
}>();

const api = inject<BackendApiClient | null>("api", null);
const versionValue = ref("");
const branch = ref("");
const branches = ref<string[]>([]);
const loadingBranches = ref(false);
const branchLoadFailed = ref(false);
let branchRequestGeneration = 0;

const standardTemplate = computed(() => props.template?.standard !== false);
const canSubmit = computed(() => Boolean(
  branch.value
  && !loadingBranches.value
  && !branchLoadFailed.value
  && (standardTemplate.value || versionValue.value)
));

const resetForm = () => {
  versionValue.value = "";
  branch.value = "";
  branches.value = [];
  loadingBranches.value = false;
  branchLoadFailed.value = false;
};

/**
 * 顶部与左下角共用同一分支加载程序；代次校验阻止关闭或切换工作空间后的迟到响应污染下一次弹窗。
 */
async function loadBranches(template: ApplicationWorkspaceTemplate, generation: number) {
  if (!template.repositoryId || !api) {
    branchLoadFailed.value = true;
    return;
  }
  loadingBranches.value = true;
  try {
    const nextBranches = await api.listRepositoryBranches(template.repositoryId);
    if (generation !== branchRequestGeneration || !props.modelValue || props.template?.workspaceId !== template.workspaceId) return;
    branches.value = template.standard !== false
      ? nextBranches.filter(isValidStandardWorkspaceBranch).sort((left, right) => right.localeCompare(left))
      : nextBranches;
    branch.value = branches.value[0] ?? "";
  } catch {
    if (generation !== branchRequestGeneration) return;
    branches.value = [];
    branch.value = "";
    branchLoadFailed.value = true;
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
  if (!template || props.disabled || !canSubmit.value) return;
  const payload: { template: ApplicationWorkspaceTemplate; version?: string; branch: string } = {
    template,
    branch: branch.value
  };
  if (template.standard === false) payload.version = versionValue.value.replaceAll("-", "");
  emit("submit", payload);
  emit("update:modelValue", false);
}
</script>

<template>
  <!-- 顶部入口位于 transform 定位的上下文舱内，必须挂到 body 才能让 fixed 遮罩覆盖完整视口。 -->
  <ElDialog
    :model-value="modelValue"
    :title="`按分支为「${template?.workspaceName ?? ''}」新建版本`"
    width="420px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="ta-workbench-create-version">
      <label class="ta-workbench-create-version-label">选择远端分支</label>
      <ElSelect
        v-model="branch"
        :loading="loadingBranches"
        placeholder="请选择用于新建版本的分支"
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
      <p v-if="branchLoadFailed" class="ta-workbench-create-version-hint is-error">分支加载失败，请关闭弹窗后重试。</p>
      <p v-else-if="!loadingBranches && branches.length === 0" class="ta-workbench-create-version-hint is-error">
        {{ standardTemplate ? "未找到符合 feature_testagent_yyyyMMdd 规则的分支。" : "未找到可用分支。" }}
      </p>
      <p v-else-if="standardTemplate" class="ta-workbench-create-version-hint">
        版本号会从分支名自动识别，无需再选择月和日。
      </p>
      <label v-if="!standardTemplate" class="ta-workbench-create-version-label">兼容版本日期（格式 yyyyMMdd）</label>
      <ElDatePicker
        v-if="!standardTemplate"
        v-model="versionValue"
        type="date"
        format="YYYY-MM-DD"
        value-format="YYYYMMDD"
        placeholder="请选择日期"
        style="width: 100%"
      />
      <p class="ta-workbench-create-version-hint">提交后会基于所选远端分支创建工作空间版本。</p>
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
        :disabled="!canSubmit || creating"
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

.ta-workbench-create-version-hint.is-error {
  color: #b42318;
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
