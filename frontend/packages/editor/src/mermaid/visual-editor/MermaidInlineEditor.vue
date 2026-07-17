<script setup lang="ts">
import { nextTick, onMounted, ref, type CSSProperties } from "vue";
import { normalizeMermaidHexColor } from "../style-directives";
import MermaidColorField from "./MermaidColorField.vue";

const props = defineProps<{
  kind: "node" | "edge";
  text: string;
  textColor?: string;
  position: CSSProperties;
}>();
const emit = defineEmits<{
  commit: [value: { text: string; textColor?: string }];
  cancel: [];
}>();
const draftText = ref(props.text);
const draftColor = ref(props.textColor);
const rawDraftColor = ref(props.textColor ?? "");
const textInput = ref<HTMLInputElement>();

async function commit() {
  // 等待同一事件中的 v-model 更新，再用原始输入统一校验三位/六位 HEX 后提交。
  await nextTick();
  const textColor = rawDraftColor.value.trim()
    ? normalizeMermaidHexColor(rawDraftColor.value)
    : undefined;
  if (rawDraftColor.value.trim() && !textColor) return;
  emit("commit", { text: draftText.value, textColor });
}

onMounted(() => nextTick(() => {
  textInput.value?.focus();
  textInput.value?.select();
}));
</script>

<template>
  <Teleport to="body">
    <form
      class="ta-mermaid-inline-editor"
      :style="position"
      role="dialog"
      :aria-label="kind === 'node' ? '编辑节点' : '编辑连线'"
      @submit.prevent="commit"
      @keydown.enter.stop.prevent="commit"
      @keydown.esc.stop.prevent="emit('cancel')"
      @pointerdown.stop
      @dblclick.stop
    >
      <label>
        <span>{{ kind === "node" ? "节点文字" : "连线文字" }}</span>
        <input
          ref="textInput"
          v-model="draftText"
          type="text"
          :aria-label="kind === 'node' ? '节点文字' : '连线文字'"
          :placeholder="kind === 'edge' ? '为空则删除标签' : undefined"
        />
      </label>
      <MermaidColorField
        :label="kind === 'node' ? '文字颜色' : '连线文字颜色'"
        v-model="draftColor"
        @draft="rawDraftColor = $event"
      />
      <div class="ta-mermaid-inline-editor__actions">
        <button type="button" aria-label="取消编辑" @click="emit('cancel')">取消</button>
        <button type="submit" class="is-primary" aria-label="完成编辑">完成</button>
      </div>
    </form>
  </Teleport>
</template>

<style scoped>
.ta-mermaid-inline-editor { position: fixed; z-index: 3100; box-sizing: border-box; display: grid; width: min(286px, calc(100vw - 16px)); gap: 9px; padding: 10px; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 8px; background: var(--ta-surface, #fff); box-shadow: 0 14px 30px rgba(15, 23, 42, 0.18); color: var(--ta-ink, #172033); }
.ta-mermaid-inline-editor label { display: grid; gap: 4px; }
.ta-mermaid-inline-editor label span { color: var(--ta-muted, #64748b); font-size: 11px; }
.ta-mermaid-inline-editor label input { box-sizing: border-box; width: 100%; min-height: 30px; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; padding: 0 8px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); font: inherit; font-size: 12px; }
.ta-mermaid-inline-editor__actions { display: flex; justify-content: flex-end; gap: 6px; }
.ta-mermaid-inline-editor__actions button { min-height: 28px; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; padding: 0 10px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); cursor: pointer; }
.ta-mermaid-inline-editor__actions .is-primary { border-color: var(--primary, #4f46e5); background: var(--primary, #4f46e5); color: #fff; }
</style>
