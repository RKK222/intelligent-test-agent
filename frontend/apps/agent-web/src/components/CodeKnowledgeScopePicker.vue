<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { AppSourceRepositorySummary } from "@test-agent/shared-types";
import { BookOpen, Check, ChevronDown, LoaderCircle } from "lucide-vue-next";

const props = withDefaults(defineProps<{
  available?: boolean;
  repositories?: AppSourceRepositorySummary[];
  selectedRepositoryIds?: string[];
  loading?: boolean;
  error?: string | null;
  disabled?: boolean;
}>(), {
  available: false,
  repositories: () => [],
  selectedRepositoryIds: () => [],
  loading: false,
  error: null,
  disabled: false
});

const emit = defineEmits<{
  "update:selected-repository-ids": [repositoryIds: string[]];
  "prepare-source": [repositoryId: string];
}>();

const root = ref<HTMLElement | null>(null);
const open = ref(false);
const selectedSet = computed(() => new Set(props.selectedRepositoryIds));
const allSelected = computed(() =>
  props.repositories.length > 0
  && props.repositories.every((repository) => selectedSet.value.has(repository.repositoryId))
);
const visible = computed(() => props.available || props.loading || Boolean(props.error));
const triggerDisabled = computed(() =>
  props.disabled || props.loading || !props.available || props.repositories.length === 0
);
const triggerLabel = computed(() => {
  if (props.loading) return "代码知识";
  if (props.error) return "代码知识不可用";
  return `代码知识 ${props.selectedRepositoryIds.length}/${props.repositories.length}`;
});
const triggerTitle = computed(() => {
  if (props.error) return props.error;
  if (!props.available) return "代码知识当前未启用";
  if (props.repositories.length === 0) return "当前应用没有已配置且授权的代码库";
  return `已选择 ${props.selectedRepositoryIds.length} 个代码库；选择范围不会切换当前工作区`;
});

watch(
  () => [props.available, props.repositories.length] as const,
  ([available, count]) => {
    if (!available || count === 0) open.value = false;
  }
);

function toggleOpen() {
  if (triggerDisabled.value) return;
  open.value = !open.value;
}

function toggleRepository(repositoryId: string) {
  const current = selectedSet.value;
  if (current.has(repositoryId) && current.size <= 1) return;
  const next = props.repositories
    .map((repository) => repository.repositoryId)
    .filter((id) => id === repositoryId ? !current.has(id) : current.has(id));
  emit("update:selected-repository-ids", next);
}

function selectAll() {
  emit("update:selected-repository-ids", props.repositories.map((repository) => repository.repositoryId));
}

function sourceState(repository: AppSourceRepositorySummary) {
  if (repository.downloadState === "DOWNLOADED_ACTIVE") {
    const version = [repository.branch, repository.targetCommit?.slice(0, 8)].filter(Boolean).join("@");
    return version ? `源码已准备 · ${version}` : "源码已准备";
  }
  if (repository.downloadState === "DOWNLOADED_EXPIRED") return "源码已过期";
  if (repository.downloadState === "PERSONAL_OCCUPIED") return "源码由其他用户占用";
  return "源码未准备";
}

function canPrepare(repository: AppSourceRepositorySummary) {
  return repository.manageable && repository.downloadState !== "DOWNLOADED_ACTIVE";
}

function prepareSource(repositoryId: string) {
  open.value = false;
  emit("prepare-source", repositoryId);
}

function closeFromOutside(event: MouseEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false;
}

onMounted(() => window.addEventListener("click", closeFromOutside));
onBeforeUnmount(() => window.removeEventListener("click", closeFromOutside));
</script>

<template>
  <div
    v-if="visible"
    ref="root"
    class="code-knowledge-scope"
    data-testid="code-knowledge-scope"
    @click.stop
  >
    <button
      type="button"
      class="code-knowledge-scope-trigger"
      data-testid="code-knowledge-scope-trigger"
      :disabled="triggerDisabled"
      :title="triggerTitle"
      :aria-expanded="open"
      aria-haspopup="dialog"
      aria-label="选择代码知识范围"
      @click="toggleOpen"
    >
      <LoaderCircle v-if="loading" class="code-knowledge-scope-spin" :size="13" />
      <BookOpen v-else :size="13" />
      <span>{{ triggerLabel }}</span>
      <ChevronDown :size="12" />
    </button>

    <section
      v-if="open"
      class="code-knowledge-scope-dropdown"
      role="dialog"
      aria-label="代码知识范围"
      data-testid="code-knowledge-scope-dropdown"
    >
      <header>
        <div>
          <strong>代码知识范围</strong>
          <span>可多选；不会切换当前工作区</span>
        </div>
        <button v-if="!allSelected" type="button" @click="selectAll">全选</button>
      </header>

      <div class="code-knowledge-scope-list">
        <article
          v-for="repository in repositories"
          :key="repository.repositoryId"
          class="code-knowledge-scope-row"
          :class="{ 'is-selected': selectedSet.has(repository.repositoryId) }"
        >
          <label>
            <input
              type="checkbox"
              :checked="selectedSet.has(repository.repositoryId)"
              :disabled="selectedSet.has(repository.repositoryId) && selectedSet.size <= 1"
              :aria-label="`选择${repository.name}`"
              @change="toggleRepository(repository.repositoryId)"
            />
            <span class="code-knowledge-scope-check" aria-hidden="true">
              <Check v-if="selectedSet.has(repository.repositoryId)" :size="12" />
            </span>
            <span class="code-knowledge-scope-copy">
              <strong>{{ repository.name || repository.englishName }}</strong>
              <code>{{ repository.repositoryId }}</code>
              <small :class="{ 'is-ready': repository.downloadState === 'DOWNLOADED_ACTIVE' }">
                {{ sourceState(repository) }}
              </small>
            </span>
          </label>
          <button
            v-if="canPrepare(repository)"
            type="button"
            class="code-knowledge-source-action"
            :aria-label="`准备${repository.name}源码`"
            @click="prepareSource(repository.repositoryId)"
          >
            准备源码
          </button>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.code-knowledge-scope {
  position: relative;
  display: inline-flex;
}

.code-knowledge-scope-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  max-width: 136px;
  padding: 0 9px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: #f4f4f5;
  color: #555;
  font-family: inherit;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}

.code-knowledge-scope-trigger:hover:not(:disabled) {
  border-color: #bae6fd;
  background: #e0f2fe;
  color: #075985;
}

.code-knowledge-scope-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.code-knowledge-scope-trigger > svg:first-child {
  flex: 0 0 auto;
  color: #0284c7;
  stroke-width: 2.2;
}

.code-knowledge-scope-trigger span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-knowledge-scope-spin {
  animation: code-knowledge-spin 0.8s linear infinite;
}

.code-knowledge-scope-dropdown {
  position: absolute;
  bottom: calc(100% + 12px);
  left: 0;
  z-index: 1000;
  display: flex;
  width: min(360px, calc(100vw - 32px));
  max-height: min(420px, calc(100vh - 160px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 12px;
  background: var(--ta-panel, #fff);
  box-shadow: 0 12px 32px rgb(15 23 42 / 0.16);
  color: var(--ta-text, #18181b);
}

.code-knowledge-scope-dropdown::after {
  position: absolute;
  bottom: -6px;
  left: 32px;
  width: 12px;
  height: 12px;
  transform: rotate(45deg);
  border-right: 1px solid var(--ta-border, #e4e4e7);
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  background: var(--ta-panel, #fff);
  content: "";
}

.code-knowledge-scope-dropdown header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px 10px;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
}

.code-knowledge-scope-dropdown header div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.code-knowledge-scope-dropdown header strong {
  font-size: 13px;
}

.code-knowledge-scope-dropdown header span,
.code-knowledge-scope-row small {
  color: var(--ta-muted, #71717a);
  font-size: 11px;
}

.code-knowledge-scope-dropdown header button,
.code-knowledge-source-action {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: #0369a1;
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
}

.code-knowledge-scope-list {
  min-height: 0;
  overflow-y: auto;
  padding: 7px;
}

.code-knowledge-scope-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 52px;
  padding: 6px 8px;
  border-radius: 8px;
}

.code-knowledge-scope-row:hover,
.code-knowledge-scope-row.is-selected {
  background: var(--ta-panel-2, #f4f4f5);
}

.code-knowledge-scope-row label {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.code-knowledge-scope-row input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.code-knowledge-scope-check {
  display: inline-flex;
  width: 17px;
  height: 17px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid #cbd5e1;
  border-radius: 5px;
  background: #fff;
  color: #fff;
}

.code-knowledge-scope-row.is-selected .code-knowledge-scope-check {
  border-color: #0284c7;
  background: #0284c7;
}

.code-knowledge-scope-copy {
  display: grid;
  min-width: 0;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 2px 8px;
}

.code-knowledge-scope-copy strong,
.code-knowledge-scope-copy code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-knowledge-scope-copy strong {
  font-size: 12px;
}

.code-knowledge-scope-copy code {
  color: var(--ta-muted, #71717a);
  font-size: 10px;
}

.code-knowledge-scope-copy small {
  grid-column: 1 / -1;
}

.code-knowledge-scope-copy small.is-ready {
  color: #047857;
}

.code-knowledge-source-action:hover,
.code-knowledge-scope-dropdown header button:hover {
  text-decoration: underline;
}

@keyframes code-knowledge-spin {
  to { transform: rotate(360deg); }
}
</style>
