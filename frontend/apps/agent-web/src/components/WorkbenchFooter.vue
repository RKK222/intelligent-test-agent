<script setup lang="ts">
import { computed } from "vue";
import { GitBranch, Save } from "lucide-vue-next";

type VcsBranch = { name: string; isCurrent?: boolean };

const props = defineProps<{
  /** 当前 VCS 分支名（来自 /vcs/status） */
  branch?: string;
  /** 可选分支列表；若提供则会渲染下拉切换 */
  branches?: VcsBranch[];
  /** 是否展示写入路径（仅编辑器场景） */
  writePath?: string;
  /** 最近一次更新时间（秒或 ISO 字符串均可） */
  updatedAt?: string | number;
  /** 是否存在未保存改动 */
  dirty?: boolean;
  /** 是否只读 */
  readonly?: boolean;
  /** 是否正在保存 */
  saving?: boolean;
  /** 是否展示保存按钮（仅编辑器场景） */
  showSave?: boolean;
}>();

const emit = defineEmits<{
  (e: "change-branch", branch: string): void;
  (e: "save"): void;
}>();

const updatedLabel = computed(() => {
  if (props.updatedAt === undefined || props.updatedAt === null || props.updatedAt === "") return "—";
  const value = typeof props.updatedAt === "number" ? props.updatedAt * 1000 : Date.parse(props.updatedAt);
  if (Number.isNaN(value)) return String(props.updatedAt);
  const diff = Date.now() - value;
  if (diff < 0) return new Date(value).toLocaleString("zh-CN", { hour12: false });
  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(value).toLocaleDateString("zh-CN");
});

const branchOptions = computed<VcsBranch[]>(() => {
  if (props.branches && props.branches.length > 0) return props.branches;
  return props.branch ? [{ name: props.branch, isCurrent: true }] : [];
});
</script>

<template>
  <footer class="ta-workbench-footer">
    <div class="ta-workbench-footer-left">
      <el-dropdown
        v-if="branchOptions.length > 0"
        trigger="click"
        @command="(name: string) => emit('change-branch', name)"
      >
        <button type="button" class="ta-workbench-footer-branch" :title="`当前分支：${branch ?? '—'}`">
          <GitBranch class="ta-workbench-footer-icon" />
          <span class="ta-workbench-footer-branch-label">{{ branch ?? "选择分支" }}</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="item in branchOptions"
              :key="item.name"
              :command="item.name"
              :disabled="item.name === branch"
            >
              {{ item.name }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <span v-else class="ta-workbench-footer-branch is-disabled">
        <GitBranch class="ta-workbench-footer-icon" />
        <span>分支选择</span>
      </span>
    </div>

    <div v-if="showSave" class="ta-workbench-footer-middle">
      <span class="ta-workbench-footer-path">
        写入路径：<span class="ta-workbench-footer-path-value">{{ writePath ?? "—" }}</span>
      </span>
      <span class="ta-workbench-footer-updated">更新时间：{{ updatedLabel }}</span>
    </div>

    <div v-if="showSave" class="ta-workbench-footer-right">
      <button
        type="button"
        class="ta-workbench-footer-save"
        :disabled="!dirty || readonly || saving"
        :title="readonly ? '只读文件不可保存' : saving ? '保存中…' : '保存 (Ctrl+S)'"
        @click="emit('save')"
      >
        <Save class="ta-workbench-footer-save-icon" />
        <span>保存</span>
      </button>
    </div>
  </footer>
</template>

<style scoped>
.ta-workbench-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 36px;
  padding: 0 12px;
  background: #fff;
  border-top: 1px solid #ddd;
  flex-shrink: 0;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  font-size: 12px;
  color: #4b4b4b;
}

.ta-workbench-footer-left,
.ta-workbench-footer-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.ta-workbench-footer-middle {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  white-space: nowrap;
}

.ta-workbench-footer-right {
  margin-left: auto;
}

.ta-workbench-footer-branch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 10px;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #333;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
  font: inherit;
}

.ta-workbench-footer-branch:hover {
  background: #f5f5f5;
  border-color: #b5b5b5;
}

.ta-workbench-footer-branch.is-disabled {
  cursor: default;
  color: #888;
  background: #fafafa;
}

.ta-workbench-footer-branch-label {
  font-weight: 500;
}

.ta-workbench-footer-icon {
  width: 14px;
  height: 14px;
  color: #555;
}

.ta-workbench-footer-path,
.ta-workbench-footer-updated {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  font-family: "JetBrains Mono", "PingFang SC", monospace;
  font-size: 12px;
  color: #555;
}

.ta-workbench-footer-path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ta-workbench-footer-path-value {
  color: #18181b;
  font-weight: 500;
}

.ta-workbench-footer-updated {
  flex-shrink: 0;
  color: #888;
}

.ta-workbench-footer-save {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: #18181b;
  color: #fff;
  font: inherit;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.12s ease, opacity 0.12s ease;
}

.ta-workbench-footer-save:hover:not(:disabled) {
  background: #000;
}

.ta-workbench-footer-save:disabled {
  background: #c0c4cc;
  cursor: not-allowed;
  opacity: 0.7;
}

.ta-workbench-footer-save-icon {
  width: 14px;
  height: 14px;
}
</style>
