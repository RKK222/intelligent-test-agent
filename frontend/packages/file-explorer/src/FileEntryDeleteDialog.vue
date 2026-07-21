<script setup lang="ts">
import { ref } from "vue";
import { AlertTriangle, X } from "lucide-vue-next";

type DeleteEntry = {
  path: string;
  type: "file" | "directory";
};

const emit = defineEmits<{
  confirm: [path: string, type: "file" | "directory"];
}>();

const visible = ref(false);
const entry = ref<DeleteEntry | null>(null);

/** 工作空间与 Agents 文件树共用同一递归删除确认面板。 */
function open(target: DeleteEntry) {
  entry.value = target;
  visible.value = true;
}

function close() {
  visible.value = false;
  entry.value = null;
}

function submit() {
  if (!entry.value) return;
  emit("confirm", entry.value.path, entry.value.type);
  close();
}

defineExpose({ open });
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="ta-file-dialog-overlay"
      @keydown.esc="close"
      @click.self="close"
    >
      <section
        role="dialog"
        aria-modal="true"
        :aria-label="entry?.type === 'directory' ? '删除文件夹' : '删除文件'"
        class="ta-file-dialog ta-file-dialog--danger"
      >
        <header class="ta-file-dialog-header">
          <div class="ta-file-dialog-heading">
            <span class="ta-file-dialog-icon"><AlertTriangle :size="16" :stroke-width="1.8" /></span>
            <div>
              <h2>确认删除</h2>
              <p>此操作会立即写入当前个人 worktree</p>
            </div>
          </div>
          <button type="button" class="ta-file-dialog-close" aria-label="关闭" @click="close">
            <X :size="15" :stroke-width="1.7" />
          </button>
        </header>
        <div class="ta-file-dialog-body">
          <div class="ta-file-dialog-danger-card">
            <span>{{ entry?.type === 'directory' ? '文件夹' : '文件' }}</span>
            <strong>{{ entry?.path }}</strong>
          </div>
          <p class="ta-file-dialog-warning">
            {{ entry?.type === 'directory' ? '文件夹及其中的全部内容都会被删除。' : '文件删除后无法恢复。' }}
          </p>
        </div>
        <footer class="ta-file-dialog-footer">
          <button type="button" class="ta-file-dialog-button" @click="close">取消</button>
          <button type="button" class="ta-file-dialog-button is-danger" @click="submit">确认删除</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ta-file-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 2700;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(8px);
}

.ta-file-dialog {
  display: flex;
  width: min(440px, calc(100vw - 28px));
  overflow: hidden;
  flex-direction: column;
  border: 1px solid var(--ta-border-strong, #cfcfcf);
  border-radius: 12px;
  background: var(--ta-panel-2, #fff);
  box-shadow: 0 24px 48px -12px rgba(0, 0, 0, 0.18);
  color: var(--ta-text, #333);
}

.ta-file-dialog-header,
.ta-file-dialog-footer {
  display: flex;
  align-items: center;
  background: var(--ta-surface, #fff);
  padding: 14px 20px;
}

.ta-file-dialog-header {
  justify-content: space-between;
  border-bottom: 1px solid var(--ta-border, #eaeaea);
}

.ta-file-dialog-heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 12px;
}

.ta-file-dialog-heading h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.ta-file-dialog-heading p {
  margin: 4px 0 0;
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
}

.ta-file-dialog-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  color: var(--ta-error, #9e3b34);
}

.ta-file-dialog-close {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--ta-muted, #7a7a7a);
  cursor: pointer;
}

.ta-file-dialog-close:hover {
  background: var(--ta-hover, #eef1f5);
  color: var(--ta-text, #333);
}

.ta-file-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

.ta-file-dialog-danger-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid #fee2e2;
  border-radius: 10px;
  background: #fef2f2;
  padding: 14px 16px;
}

.ta-file-dialog-danger-card span {
  color: #ef4444;
  font-size: 11px;
  font-weight: 600;
}

.ta-file-dialog-danger-card strong {
  overflow-wrap: anywhere;
  color: #991b1b;
  font-family: var(--font-mono, "Geist Mono", monospace);
  font-size: 13px;
  line-height: 1.4;
}

.ta-file-dialog-warning {
  margin: 0;
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
  line-height: 1.5;
}

.ta-file-dialog-footer {
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid var(--ta-border, #eaeaea);
}

.ta-file-dialog-button {
  min-width: 80px;
  height: 34px;
  border: 1px solid var(--ta-border-strong, #cfcfcf);
  border-radius: 8px;
  background: var(--ta-surface, #fff);
  color: var(--ta-text, #333);
  padding: 0 16px;
  font-size: 13px;
  cursor: pointer;
}

.ta-file-dialog-button.is-danger {
  border-color: #dc2626;
  background: #dc2626;
  color: #fff;
}

.ta-file-dialog-button.is-danger:hover {
  border-color: #b91c1c;
  background: #b91c1c;
}
</style>
