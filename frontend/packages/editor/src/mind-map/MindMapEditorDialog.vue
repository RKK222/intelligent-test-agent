<script setup lang="ts">
import { X } from "lucide-vue-next";
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import MindMapEditor from "./MindMapEditor.vue";
import type { MindMapApplyResult, MindMapDocument } from "./model";

const props = defineProps<{
  document: MindMapDocument;
  blockIndex: number;
  sourceLine: number;
  error?: string;
}>();

const emit = defineEmits<{
  apply: [result: MindMapApplyResult];
  cancel: [];
}>();

const dialogRef = ref<HTMLElement>();
const title = computed(() => `编辑第 ${props.blockIndex + 1} 个思维导图`);
let previouslyFocused: HTMLElement | null = null;

onMounted(async () => {
  previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  await nextTick();
  dialogRef.value?.focus();
});

onBeforeUnmount(() => previouslyFocused?.focus());
</script>

<template>
  <Teleport to="body">
    <div class="ta-mind-map-dialog-backdrop">
      <div
        ref="dialogRef"
        class="ta-mind-map-dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        tabindex="-1"
        @keydown.esc="emit('cancel')"
      >
        <header class="ta-mind-map-dialog__header">
          <div>
            <h2>{{ title }}</h2>
            <p>源码起始于第 {{ sourceLine }} 行；应用后仍需保存文件才会写入磁盘。</p>
          </div>
          <button type="button" aria-label="关闭思维导图编辑" title="关闭" @click="emit('cancel')">
            <X :size="18" />
          </button>
        </header>
        <div v-if="error" class="ta-mind-map-dialog__error" role="alert">{{ error }}</div>
        <div class="ta-mind-map-dialog__content">
          <MindMapEditor
            :document="document"
            :generation-key="`markdown-${blockIndex}-${sourceLine}`"
            @apply="emit('apply', $event)"
            @cancel="emit('cancel')"
          />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ta-mind-map-dialog-backdrop {
  position: fixed;
  z-index: 4600;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(15 23 42 / 46%);
}

.ta-mind-map-dialog {
  display: flex;
  width: min(1180px, calc(100vw - 48px));
  height: min(760px, calc(100vh - 48px));
  min-width: min(720px, calc(100vw - 48px));
  min-height: min(520px, calc(100vh - 48px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #d8dde5;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 24px 70px rgb(15 23 42 / 24%);
}

.ta-mind-map-dialog__header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 14px 8px 18px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.ta-mind-map-dialog__header h2 { margin: 0; color: #111827; font-size: 15px; font-weight: 600; }
.ta-mind-map-dialog__header p { margin: 3px 0 0; color: #6b7280; font-size: 11px; }
.ta-mind-map-dialog__header button { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 5px; color: #4b5563; background: transparent; cursor: pointer; }
.ta-mind-map-dialog__header button:hover { background: #f3f4f6; }
.ta-mind-map-dialog__header button:focus-visible { outline: 2px solid #7f1e2b; outline-offset: 1px; }
.ta-mind-map-dialog__error { padding: 8px 14px; border-bottom: 1px solid #fecdd3; color: #9f1239; background: #fff1f2; font-size: 12px; }
.ta-mind-map-dialog__content { min-height: 0; flex: 1; }

@media (max-width: 760px) {
  .ta-mind-map-dialog-backdrop { padding: 8px; }
  .ta-mind-map-dialog { width: calc(100vw - 16px); height: calc(100vh - 16px); min-width: 0; min-height: 0; }
}
</style>
