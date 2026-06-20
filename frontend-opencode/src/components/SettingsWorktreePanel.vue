<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { GitBranch, Plus, RefreshCw, RotateCcw, Trash2 } from "lucide-vue-next";
import { usePlatformStore } from "@/stores/platform";

type WorktreeItem = {
  name: string;
  branch?: string;
  directory: string;
  status?: string;
};

const props = defineProps<{
  workspaceId?: string;
}>();

const platform = usePlatformStore();
const worktrees = ref<WorktreeItem[]>([]);
const loading = ref(false);
const action = ref<string>();
const error = ref<string>();
const name = ref("");
const startCommand = ref("");

const isEmpty = computed(() => !loading.value && worktrees.value.length === 0);

onMounted(() => void loadWorktrees());
watch(() => props.workspaceId, () => void loadWorktrees());

async function loadWorktrees() {
  loading.value = true;
  error.value = undefined;
  try {
    worktrees.value = normalizeWorktrees(await platform.api.listWorktrees(props.workspaceId));
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "Worktree 加载失败";
    worktrees.value = [];
  } finally {
    loading.value = false;
  }
}

async function createWorktree() {
  action.value = "create";
  error.value = undefined;
  try {
    await platform.api.createWorktree({
      ...workspacePayload(),
      ...(name.value.trim() ? { name: name.value.trim() } : {}),
      ...(startCommand.value.trim() ? { startCommand: startCommand.value.trim() } : {})
    });
    name.value = "";
    startCommand.value = "";
    await loadWorktrees();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "Worktree 创建失败";
  } finally {
    action.value = undefined;
  }
}

async function resetWorktree(item: WorktreeItem) {
  action.value = `reset:${item.directory}`;
  error.value = undefined;
  try {
    await platform.api.resetWorktree({ ...workspacePayload(), directory: item.directory });
    await loadWorktrees();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "Worktree 重置失败";
  } finally {
    action.value = undefined;
  }
}

async function removeWorktree(item: WorktreeItem) {
  action.value = `remove:${item.directory}`;
  error.value = undefined;
  try {
    await platform.api.removeWorktree({ ...workspacePayload(), directory: item.directory });
    await loadWorktrees();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "Worktree 删除失败";
  } finally {
    action.value = undefined;
  }
}

function workspacePayload() {
  return props.workspaceId ? { workspaceId: props.workspaceId } : {};
}

// opencode runtime 的 experimental worktree 可能被平台包装成 items/worktrees，这里只提取稳定展示字段。
function normalizeWorktrees(response: unknown) {
  const items = Array.isArray(response)
    ? response
    : isRecord(response) && Array.isArray(response.items)
      ? response.items
      : isRecord(response) && Array.isArray(response.worktrees)
        ? response.worktrees
        : [];
  return items.flatMap((item) => {
    if (!isRecord(item)) {
      return [];
    }
    const directory = readString(item.directory) ?? readString(item.path) ?? readString(item.worktree);
    if (!directory) {
      return [];
    }
    return [
      {
        name: readString(item.name) ?? lastPathSegment(directory),
        branch: readString(item.branch),
        directory,
        status: readString(item.status) ?? readString(item.state)
      }
    ];
  });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function readString(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function lastPathSegment(path: string) {
  const parts = path.split(/[\\/]/).filter(Boolean);
  return parts.at(-1) ?? path;
}
</script>

<template>
  <section class="settings-list worktree-panel" aria-label="Worktrees">
    <div class="section-label">Worktrees</div>
    <form class="worktree-create-form" @submit.prevent="createWorktree">
      <label>
        <span>Worktree name</span>
        <input v-model="name" aria-label="Worktree name" autocomplete="off" placeholder="feature-ui" />
      </label>
      <label>
        <span>Startup command</span>
        <input v-model="startCommand" aria-label="Startup command" autocomplete="off" placeholder="pnpm install" />
      </label>
      <div class="worktree-form-actions">
        <button class="primary-action" type="submit" aria-label="Create worktree" :disabled="action === 'create'">
          <Plus :size="14" />Create worktree
        </button>
        <button class="icon-text" type="button" :disabled="loading" @click="loadWorktrees">
          <RefreshCw :size="14" />Refresh
        </button>
      </div>
    </form>
    <div v-if="loading && !worktrees.length" class="empty-note">Loading worktrees...</div>
    <div v-else-if="isEmpty" class="empty-note">No sandbox worktrees yet.</div>
    <article v-for="item in worktrees" :key="item.directory" class="worktree-row">
      <div class="worktree-head">
        <span class="worktree-title">
          <GitBranch :size="15" />
          <strong>{{ item.name }}</strong>
          <small v-if="item.branch">{{ item.branch }}</small>
        </span>
        <small v-if="item.status" class="auth-pill">{{ item.status }}</small>
      </div>
      <code>{{ item.directory }}</code>
      <div class="worktree-actions">
        <button
          class="icon-text"
          type="button"
          :aria-label="`Reset ${item.directory}`"
          :disabled="action === `reset:${item.directory}`"
          @click="resetWorktree(item)"
        >
          <RotateCcw :size="14" />Reset
        </button>
        <button
          class="icon-text danger-action"
          type="button"
          :aria-label="`Remove ${item.directory}`"
          :disabled="action === `remove:${item.directory}`"
          @click="removeWorktree(item)"
        >
          <Trash2 :size="14" />Remove
        </button>
      </div>
    </article>
    <div v-if="error" class="inline-alert">{{ error }}</div>
  </section>
</template>
