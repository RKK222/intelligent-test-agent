<script setup lang="ts">
import type {
  WorkflowBranch,
  WorkflowRepositoryGroup,
  WorkflowStructuredInput,
} from "@test-agent/workflow-api-client";
import { computed, reactive, ref } from "vue";
import { GitBranch, Plus, Trash2 } from "lucide-vue-next";

const props = defineProps<{
  repositoryGroups: WorkflowRepositoryGroup[];
  loadBranches: (repositoryId: string) => Promise<WorkflowBranch[]>;
  disabled?: boolean;
}>();
const emit = defineEmits<{ submit: [value: WorkflowStructuredInput] }>();

type Row = {
  key: number;
  repositoryId: string;
  targetBranch: string;
  baselineBranch: string;
  branches: WorkflowBranch[];
  loading: boolean;
  error: string;
};

let nextKey = 1;
const rows = reactive<Row[]>([newRow()]);
const mode = ref<"SINGLE" | "REVIEW">("SINGLE");
const analyzers = ref<string[]>(["codex"]);
const availableAnalyzers = [
  { id: "codex", name: "Codex", note: "默认 · 代码证据优先" },
  { id: "opencode", name: "OpenCode", note: "独立复核" },
];

const valid = computed(() => {
  const repositoriesReady = rows.length > 0 && rows.every((row) => row.repositoryId && row.targetBranch);
  const analyzersReady = mode.value === "SINGLE" ? analyzers.value.length === 1 : analyzers.value.length >= 2;
  return repositoriesReady && analyzersReady && !props.disabled;
});

function newRow(): Row {
  return {
    key: nextKey++,
    repositoryId: "",
    targetBranch: "",
    baselineBranch: "",
    branches: [],
    loading: false,
    error: "",
  };
}

function addRow() {
  rows.push(newRow());
}

function removeRow(index: number) {
  if (rows.length > 1) rows.splice(index, 1);
}

async function repositoryChanged(row: Row) {
  row.targetBranch = "";
  row.baselineBranch = "";
  row.branches = [];
  row.error = "";
  if (!row.repositoryId) return;
  row.loading = true;
  try {
    row.branches = await props.loadBranches(row.repositoryId);
  } catch (error) {
    row.error = error instanceof Error ? error.message : "分支读取失败";
  } finally {
    row.loading = false;
  }
}

function changeMode(value: "SINGLE" | "REVIEW") {
  mode.value = value;
  if (value === "SINGLE") analyzers.value = [analyzers.value[0] ?? "codex"];
  else if (analyzers.value.length < 2) analyzers.value = [analyzers.value[0] ?? "codex", "opencode"];
}

function toggleAnalyzer(id: string) {
  if (analyzers.value.includes(id)) {
    if (analyzers.value.length > 1) analyzers.value = analyzers.value.filter((value) => value !== id);
  } else if (analyzers.value.length < 3) {
    analyzers.value = [...analyzers.value, id];
  }
  if (mode.value === "SINGLE" && analyzers.value.length > 1) analyzers.value = [id];
}

function submit() {
  if (!valid.value) return;
  emit("submit", {
    repositories: rows.map((row) => ({
      repositoryId: row.repositoryId,
      targetBranch: row.targetBranch,
      ...(row.baselineBranch ? { baselineBranch: row.baselineBranch } : {}),
    })),
    mode: mode.value,
    analyzerIds: [...analyzers.value],
  });
}
</script>

<template>
  <section class="workflow-input-card" aria-labelledby="workflow-input-title">
    <header>
      <div class="workflow-input-icon"><GitBranch :size="18" /></div>
      <div>
        <p class="workflow-kicker">分析坐标</p>
        <h3 id="workflow-input-title">选择要冻结比较的代码分支</h3>
      </div>
    </header>

    <div class="workflow-repository-rows">
      <div v-for="(row, index) in rows" :key="row.key" class="workflow-repository-row">
        <span class="workflow-row-index">{{ String(index + 1).padStart(2, "0") }}</span>
        <label>
          <span>应用 / 代码库</span>
          <select v-model="row.repositoryId" :disabled="disabled" @change="repositoryChanged(row)">
            <option value="">请选择代码库</option>
            <optgroup
              v-for="group in repositoryGroups"
              :key="group.applicationId"
              :label="group.applicationName"
            >
              <option v-for="repository in group.repositories" :key="repository.id" :value="repository.id">
                {{ repository.name }}{{ repository.englishName ? ` · ${repository.englishName}` : "" }}
              </option>
            </optgroup>
          </select>
        </label>
        <label>
          <span>目标分支</span>
          <select v-model="row.targetBranch" :disabled="disabled || row.loading || !row.repositoryId">
            <option value="">{{ row.loading ? "正在读取…" : "请选择目标分支" }}</option>
            <option v-for="branch in row.branches" :key="branch.name" :value="branch.name">
              {{ branch.name }}{{ branch.default ? "（默认）" : "" }}
            </option>
          </select>
        </label>
        <button
          type="button"
          class="workflow-icon-button"
          :disabled="rows.length === 1 || disabled"
          aria-label="移除代码库"
          @click="removeRow(index)"
        >
          <Trash2 :size="16" />
        </button>
        <p v-if="row.error" class="workflow-field-error">{{ row.error }}</p>
      </div>
    </div>

    <button type="button" class="workflow-add-repository" :disabled="disabled" @click="addRow">
      <Plus :size="15" /> 添加代码库
    </button>

    <div class="workflow-input-options">
      <fieldset>
        <legend>分析方式</legend>
        <label :class="{ active: mode === 'SINGLE' }">
          <input type="radio" :checked="mode === 'SINGLE'" @change="changeMode('SINGLE')" />
          <span><strong>单智能体</strong><small>速度优先，默认 Codex</small></span>
        </label>
        <label :class="{ active: mode === 'REVIEW' }">
          <input type="radio" :checked="mode === 'REVIEW'" @change="changeMode('REVIEW')" />
          <span><strong>交叉复核</strong><small>保留共识、分歧与无法裁决项</small></span>
        </label>
      </fieldset>
      <fieldset>
        <legend>代码智能体 <small>最多 3 个</small></legend>
        <button
          v-for="analyzer in availableAnalyzers"
          :key="analyzer.id"
          type="button"
          class="workflow-analyzer-choice"
          :class="{ active: analyzers.includes(analyzer.id) }"
          :aria-pressed="analyzers.includes(analyzer.id)"
          @click="toggleAnalyzer(analyzer.id)"
        >
          <span class="workflow-analyzer-mark">{{ analyzer.name.slice(0, 1) }}</span>
          <span><strong>{{ analyzer.name }}</strong><small>{{ analyzer.note }}</small></span>
        </button>
      </fieldset>
    </div>

    <footer>
      <p>系统将冻结远端 SHA，并使用 <code>merge-base..targetHead</code> 作为唯一比较范围。</p>
      <button type="button" class="workflow-primary-button" :disabled="!valid" @click="submit">开始影响分析</button>
    </footer>
  </section>
</template>
