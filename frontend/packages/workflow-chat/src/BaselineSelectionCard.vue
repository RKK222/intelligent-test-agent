<script setup lang="ts">
import type {
  WorkflowRepositoryGroup,
  WorkflowStructuredInput,
} from "@test-agent/workflow-api-client";
import { GitMerge } from "lucide-vue-next";
import { computed, reactive } from "vue";

const props = defineProps<{
  baselines: Array<{ repositoryId: string; availableBranches: string[] }>;
  currentInput?: WorkflowStructuredInput;
  repositoryGroups: WorkflowRepositoryGroup[];
  disabled?: boolean;
}>();
const emit = defineEmits<{ submit: [value: WorkflowStructuredInput] }>();

const selected = reactive<Record<string, string>>({});
const valid = computed(() =>
  props.baselines.length > 0
  && props.baselines.every((value) => Boolean(selected[value.repositoryId]))
  && !props.disabled,
);

function repositoryName(repositoryId: string): string {
  for (const group of props.repositoryGroups) {
    const repository = group.repositories.find((value) => value.id === repositoryId);
    if (repository) return `${group.applicationName} / ${repository.name}`;
  }
  return repositoryId;
}

function submit() {
  if (!valid.value || !props.currentInput?.repositories) return;
  emit("submit", {
    ...props.currentInput,
    repositories: props.currentInput.repositories.map((repository) => ({
      ...repository,
      baselineBranch: selected[repository.repositoryId] ?? repository.baselineBranch,
    })),
  });
}
</script>

<template>
  <section class="workflow-input-card workflow-baseline-card" aria-labelledby="baseline-title">
    <header>
      <div class="workflow-input-icon"><GitMerge :size="18" /></div>
      <div>
        <p class="workflow-kicker">基线待确认</p>
        <h3 id="baseline-title">远端没有可识别的默认分支</h3>
      </div>
    </header>
    <div class="workflow-baseline-list">
      <label v-for="baseline in baselines" :key="baseline.repositoryId">
        <span>{{ repositoryName(baseline.repositoryId) }}</span>
        <select v-model="selected[baseline.repositoryId]" :disabled="disabled">
          <option value="">请选择比较基线</option>
          <option v-for="branch in baseline.availableBranches" :key="branch" :value="branch">
            {{ branch }}
          </option>
        </select>
      </label>
    </div>
    <footer>
      <p>选择后仍会冻结基线和目标分支的远端 SHA；不会使用本地浮动分支。</p>
      <button type="button" class="workflow-primary-button" :disabled="!valid" @click="submit">
        按所选基线继续
      </button>
    </footer>
  </section>
</template>
