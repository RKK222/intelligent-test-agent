<script setup lang="ts">
import type { ScopeSelector, WorkflowRepositoryGroup } from "@test-agent/workflow-api-client";
import { computed, ref } from "vue";
import { ScanSearch } from "lucide-vue-next";

const props = defineProps<{
  repositoryGroups: WorkflowRepositoryGroup[];
  disabled?: boolean;
  initialSelector?: ScopeSelector;
}>();
const emit = defineEmits<{ submit: [selector: ScopeSelector] }>();
const repositoryId = ref(props.initialSelector?.repositoryId ?? "");
const kind = ref<ScopeSelector["kind"]>(props.initialSelector?.kind ?? "SYMBOL");
const value = ref(props.initialSelector?.value ?? "");
const valid = computed(() => repositoryId.value && value.value.trim() && !props.disabled);

function submit() {
  if (!valid.value) return;
  emit("submit", { repositoryId: repositoryId.value, kind: kind.value, value: value.value.trim() });
}
</script>

<template>
  <section class="workflow-reanalysis-card">
    <header><ScanSearch :size="17" /><strong>局部重分析</strong><span>保持原分支与提交 SHA 不变</span></header>
    <div>
      <select v-model="repositoryId" aria-label="局部重分析代码库">
        <option value="">选择代码库</option>
        <optgroup v-for="group in repositoryGroups" :key="group.applicationId" :label="group.applicationName">
          <option v-for="repository in group.repositories" :key="repository.id" :value="repository.id">{{ repository.name }}</option>
        </optgroup>
      </select>
      <select v-model="kind" aria-label="范围类型">
        <option value="PROGRAM">程序</option><option value="MODULE">模块</option>
        <option value="DIRECTORY">目录</option><option value="FILE">文件</option><option value="SYMBOL">符号</option>
      </select>
      <input v-model="value" type="text" placeholder="输入程序、目录、文件或符号" @keydown.enter.prevent="submit" />
      <button type="button" class="workflow-secondary-button" :disabled="!valid" @click="submit">重新分析</button>
    </div>
  </section>
</template>
