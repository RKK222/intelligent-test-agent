<script setup lang="ts">
import { computed } from "vue";
import type { AppSourceRepositorySummary } from "@test-agent/shared-types";
import { CloudDownload, CodeXml, LoaderCircle, X } from "lucide-vue-next";

const props = defineProps<{
  open: boolean;
  repositories: AppSourceRepositorySummary[];
  loading?: boolean;
  error?: string | null;
}>();

// 紧凑入口只承担“打开已有快照”；首次下载仓库统一在管理弹窗第 1 步展示。
const visibleRepositories = computed(() => props.repositories.filter(
  (repository) => repository.downloadState !== "NOT_DOWNLOADED"
));

const emit = defineEmits<{
  close: [];
  retry: [];
  download: [];
  "open-source": [repository: AppSourceRepositorySummary];
}>();

function stateLabel(repository: AppSourceRepositorySummary) {
  const labels: Record<AppSourceRepositorySummary["downloadState"], string> = {
    NOT_DOWNLOADED: "未下载",
    DOWNLOADED_ACTIVE: repository.purpose === "TEAM" ? "团队可用" : "个人可用",
    DOWNLOADED_EXPIRED: "已过期",
    PERSONAL_OCCUPIED: "个人占用"
  };
  return labels[repository.downloadState];
}

function stateClass(repository: AppSourceRepositorySummary) {
  if (repository.downloadState === "DOWNLOADED_ACTIVE") {
    return repository.openable ? "is-active" : "is-unavailable";
  }
  if (repository.downloadState === "DOWNLOADED_EXPIRED") return "is-expired";
  if (repository.downloadState === "PERSONAL_OCCUPIED") return "is-personal";
  return "is-empty";
}

function ownerLabel(repository: AppSourceRepositorySummary) {
  return [repository.ownerName, repository.ownerUnifiedAuthId].filter(Boolean).join(" · ");
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="app-source-picker-layer" @click.self="emit('close')">
      <section class="app-source-picker" role="dialog" aria-label="应用源码">
        <header class="app-source-picker-header">
          <div>
            <h2>应用源码</h2>
            <p>打开本机已就绪的精确源码快照</p>
          </div>
          <button type="button" aria-label="关闭源码列表" @click="emit('close')"><X /></button>
        </header>

        <div class="app-source-picker-list">
          <div v-if="loading" class="app-source-picker-state" role="status">
            <LoaderCircle class="app-source-spin" /> 正在读取源码状态…
          </div>
          <div v-else-if="error" class="app-source-picker-state is-error">
            <span>{{ error }}</span>
            <button type="button" aria-label="重试加载应用源码" @click="emit('retry')">重试</button>
          </div>
          <div v-else-if="visibleRepositories.length === 0" class="app-source-picker-state">
            当前应用还没有可用源码。
          </div>
          <article
            v-for="repository in visibleRepositories"
            v-else
            :key="repository.repositoryId"
            :data-source-state="repository.downloadState"
            :class="['app-source-repository', stateClass(repository)]"
          >
            <CodeXml class="app-source-repository-icon" />
            <div class="app-source-repository-copy">
              <div class="app-source-repository-title">
                <strong>{{ repository.name }}</strong>
                <span>{{ repository.englishName }}</span>
              </div>
              <div class="app-source-repository-meta">
                <span class="app-source-state-badge">{{ stateLabel(repository) }}</span>
                <span v-if="repository.branch">{{ repository.branch }}</span>
                <span v-if="ownerLabel(repository)">{{ ownerLabel(repository) }}</span>
              </div>
              <p v-if="!repository.openable" class="app-source-unavailable">
                {{ repository.unavailableReason || "当前服务器没有可打开的 READY 副本" }}
              </p>
            </div>
            <button
              type="button"
              class="app-source-open"
              :aria-label="`打开${repository.name}源码`"
              :disabled="!repository.openable"
              @click="emit('open-source', repository)"
            >
              打开
            </button>
          </article>
        </div>

        <footer class="app-source-picker-footer">
          <button type="button" aria-label="下载版本库" @click="emit('download')">
            <CloudDownload />
            下载版本库
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.app-source-picker-layer {
  position: fixed;
  inset: 0;
  z-index: 3600;
  background: rgb(15 23 42 / 0.12);
}

.app-source-picker {
  position: fixed;
  top: 46px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  width: min(420px, calc(100vw - 24px));
  max-height: min(560px, calc(100vh - 60px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 8px;
  background: var(--ta-panel-bg, #fff);
  box-shadow: 0 14px 36px rgb(15 23 42 / 0.18);
  color: var(--ta-text, #27272a);
  font-family: inherit;
}

.app-source-picker-header,
.app-source-picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
}

.app-source-picker-header h2 { margin: 0; font-size: 14px; font-weight: 600; }
.app-source-picker-header p { margin: 2px 0 0; color: #71717a; font-size: 11px; }
.app-source-picker-header button { display: inline-flex; width: 24px; height: 24px; align-items: center; justify-content: center; border: 0; background: transparent; cursor: pointer; }
.app-source-picker-header svg { width: 14px; height: 14px; }
.app-source-picker-list { min-height: 90px; overflow-y: auto; padding: 6px; }
.app-source-picker-state { display: flex; min-height: 74px; align-items: center; justify-content: center; gap: 8px; color: #71717a; font-size: 12px; }
.app-source-picker-state.is-error { color: #b91c1c; }
.app-source-spin { width: 14px; animation: source-spin 1s linear infinite; }

.app-source-repository {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
  padding: 9px;
  border: 1px solid transparent;
  border-radius: 6px;
}
.app-source-repository + .app-source-repository { margin-top: 3px; }
.app-source-repository:hover { border-color: var(--ta-border, #e4e4e7); background: #fafafa; }
.app-source-repository-icon { width: 18px; color: #71717a; }
.app-source-repository-title { display: flex; min-width: 0; align-items: baseline; gap: 6px; font-size: 12px; }
.app-source-repository-title span { overflow: hidden; color: #a1a1aa; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.app-source-repository-meta { display: flex; flex-wrap: wrap; gap: 5px 9px; margin-top: 4px; color: #71717a; font-size: 11px; }
.app-source-state-badge { font-weight: 600; }
.is-active .app-source-state-badge { color: #047857; }
.is-unavailable .app-source-state-badge, .is-unavailable .app-source-repository-icon { color: #a16207; }
.is-expired .app-source-state-badge, .is-expired .app-source-repository-icon { color: #b45309; }
.is-personal .app-source-state-badge, .is-personal .app-source-repository-icon { color: #7c3aed; }
.app-source-unavailable { margin: 4px 0 0; color: #a16207; font-size: 11px; line-height: 1.4; }
.app-source-open { align-self: center; border: 1px solid #d4d4d8; border-radius: 5px; background: #fff; padding: 4px 9px; color: #3f3f46; font-size: 11px; cursor: pointer; }
.app-source-open:disabled { cursor: not-allowed; opacity: 0.45; }
.app-source-picker-footer { position: sticky; bottom: 0; justify-content: stretch; border-top: 1px solid var(--ta-border, #e4e4e7); border-bottom: 0; background: #fff; }
.app-source-picker-footer button { display: flex; width: 100%; align-items: center; justify-content: center; gap: 6px; border: 0; border-radius: 5px; background: #f4f4f5; padding: 7px; color: #3f3f46; font-size: 12px; font-weight: 500; cursor: pointer; }
.app-source-picker-footer svg { width: 14px; height: 14px; }
@keyframes source-spin { to { transform: rotate(360deg); } }
</style>
