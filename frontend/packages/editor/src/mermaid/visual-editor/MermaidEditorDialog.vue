<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { cloneMermaidDiagram, type MermaidEditableDiagram } from "../diagram";
import { autoLayoutMermaidGraph, syncAutoLayoutMermaidGraph } from "../layout";
import type { MermaidGraph } from "../model";
import { autoLayoutMermaidSequence } from "../sequence/layout";
import type { MermaidSequenceDiagram } from "../sequence/model";
import SequenceVisualEditor from "../sequence/visual-editor/SequenceVisualEditor.vue";
import { autoLayoutMermaidState } from "../state/layout";
import { flattenMermaidStateNodes, type MermaidStateDiagram } from "../state/model";
import StateVisualEditor from "../state/visual-editor/StateVisualEditor.vue";
import MermaidVisualEditor from "./MermaidVisualEditor.vue";

const props = defineProps<{
  model?: MermaidEditableDiagram;
  error?: string;
}>();
const emit = defineEmits<{
  apply: [diagram: MermaidEditableDiagram];
  cancel: [];
}>();

const draft = ref<MermaidEditableDiagram>();

let lastWatchModel: any = null;

watch(
  () => props.model,
  async (model) => {
    lastWatchModel = model;
    if (!model) {
      draft.value = undefined;
      return;
    }
    const cloned = cloneMermaidDiagram(model);
    if (cloned.kind === "sequenceDiagram") {
      const hasStoredLayout = cloned.participants.some((participant) => participant.position.x !== 0 || participant.position.y !== 0);
      if (lastWatchModel === model) {
        draft.value = hasStoredLayout ? cloned : autoLayoutMermaidSequence(cloned);
      }
    } else if (cloned.kind === "stateDiagram") {
      const hasStoredLayout = flattenMermaidStateNodes(cloned)
        .some((node) => node.position.x !== 0 || node.position.y !== 0);
      if (lastWatchModel === model) draft.value = cloned;
      if (!hasStoredLayout) {
        // State 先呈现可交互草稿，再异步完成各层级 Region 的 ELK 布局，避免打开弹窗时出现空白。
        const laidOut = await autoLayoutMermaidState(cloned);
        if (lastWatchModel === model) draft.value = laidOut;
      }
    } else {
      const hasStoredLayout = cloned.nodes.some((node) => node.position.x !== 0 || node.position.y !== 0);
      if (hasStoredLayout) {
        if (lastWatchModel === model) {
          draft.value = cloned;
        }
      } else {
        // 先同步做一次旧的重心布局，给 draft.value 赋初值，保证同步挂载和单元测试能立刻正常交互
        const tempLaidOut = syncAutoLayoutMermaidGraph(cloned);
        if (lastWatchModel === model) {
          draft.value = tempLaidOut;
        }
        // 然后异步去算更精细的 ELK 布局，并在计算好后更新坐标
        const laidOut = await autoLayoutMermaidGraph(cloned);
        if (lastWatchModel === model) {
          draft.value = laidOut;
        }
      }
    }
  },
  { immediate: true }
);

function apply() {
  if (draft.value) emit("apply", cloneMermaidDiagram(draft.value));
}

function updateFlowchart(graph: MermaidGraph) { draft.value = graph; }
function updateSequence(diagram: MermaidSequenceDiagram) { draft.value = diagram; }
function updateState(diagram: MermaidStateDiagram) { draft.value = diagram; }

function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") emit("cancel");
}

onMounted(() => window.addEventListener("keydown", onKeydown));
onBeforeUnmount(() => window.removeEventListener("keydown", onKeydown));
</script>

<template>
  <Teleport to="body">
    <div class="ta-mermaid-dialog-backdrop" @click.self="emit('cancel')">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="ta-mermaid-dialog-title"
        class="ta-mermaid-dialog"
      >
        <header class="ta-mermaid-dialog__header">
          <div class="ta-mermaid-dialog__header-left">
            <h2 id="ta-mermaid-dialog-title">Mermaid 可视化编辑</h2>
            <p>拖动图结构并应用后，修改会回写到当前 Markdown 代码块。</p>
          </div>
          <button type="button" aria-label="关闭可视化编辑" @click="emit('cancel')">×</button>
        </header>

        <div v-if="error" class="ta-mermaid-dialog__error" role="alert">
          <strong>无法进行可视化编辑</strong>
          <span>{{ error }}</span>
          <span v-if="!draft">请关闭后使用源码编辑。</span>
        </div>

        <MermaidVisualEditor
          v-if="draft && (draft.kind === 'flowchart' || draft.kind === 'graph')"
          :model-value="draft"
          @update:model-value="updateFlowchart"
        />
        <SequenceVisualEditor
          v-else-if="draft?.kind === 'sequenceDiagram'"
          :model-value="draft"
          @update:model-value="updateSequence"
        />
        <StateVisualEditor
          v-else-if="draft?.kind === 'stateDiagram'"
          :model-value="draft"
          @update:model-value="updateState"
        />

        <footer class="ta-mermaid-dialog__footer">
          <button type="button" @click="emit('cancel')">取消</button>
          <button type="button" class="is-primary" :disabled="!draft || !!error" @click="apply">
            应用到 Markdown
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ta-mermaid-dialog-backdrop {
  position: fixed;
  z-index: 140;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
}

.ta-mermaid-dialog {
  display: flex;
  width: min(1180px, calc(100vw - 48px));
  height: min(760px, calc(100vh - 48px));
  min-height: 520px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ta-border, #dbe2ea);
  border-radius: 9px;
  background: var(--ta-surface, #fff);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.24);
}

.ta-mermaid-dialog__header,
.ta-mermaid-dialog__footer {
  display: flex;
  flex: none;
  align-items: center;
  border-color: var(--ta-border, #e2e8f0);
  background: var(--ta-surface, #fff);
}

.ta-mermaid-dialog__header {
  min-height: 58px;
  justify-content: space-between;
  border-bottom: 1px solid var(--ta-border, #e2e8f0);
  padding: 9px 14px 9px 17px;
}

.ta-mermaid-dialog__header-left { display: flex; align-items: baseline; gap: 12px; }
.ta-mermaid-dialog__header h2 { margin: 0; color: var(--ta-ink, #172033); font-size: 14px; }
.ta-mermaid-dialog__header p { margin: 0; color: var(--ta-muted, #64748b); font-size: 11px; }
.ta-mermaid-dialog__header button { width: 30px; height: 30px; border: 0; border-radius: 5px; background: transparent; color: var(--ta-muted, #64748b); font-size: 22px; cursor: pointer; }
.ta-mermaid-dialog__header button:hover { background: var(--ta-hover, #f1f5f9); color: var(--ta-ink, #172033); }

.ta-mermaid-dialog__error {
  display: grid;
  flex: none;
  gap: 3px;
  border-bottom: 1px solid rgba(239, 68, 68, 0.25);
  padding: 10px 16px;
  background: rgba(239, 68, 68, 0.06);
  color: #b42318;
  font-size: 11px;
  line-height: 1.45;
}

.ta-mermaid-dialog__footer {
  min-height: 50px;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid var(--ta-border, #e2e8f0);
  padding: 9px 14px;
}

.ta-mermaid-dialog__footer button {
  min-width: 76px;
  min-height: 30px;
  border: 1px solid var(--ta-border, #dbe2ea);
  border-radius: 5px;
  background: var(--ta-surface, #fff);
  color: var(--ta-ink, #172033);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.ta-mermaid-dialog__footer button.is-primary { border-color: var(--primary, #4f46e5); background: var(--primary, #4f46e5); color: #fff; }
.ta-mermaid-dialog__footer button:disabled { cursor: not-allowed; opacity: 0.5; }
.ta-mermaid-dialog__footer button:focus-visible, .ta-mermaid-dialog__header button:focus-visible { outline: 2px solid color-mix(in srgb, var(--primary, #4f46e5) 55%, transparent); outline-offset: 1px; }

@media (max-width: 820px) {
  .ta-mermaid-dialog-backdrop { padding: 8px; }
  .ta-mermaid-dialog { width: calc(100vw - 16px); height: calc(100vh - 16px); min-height: 0; }
}
</style>
