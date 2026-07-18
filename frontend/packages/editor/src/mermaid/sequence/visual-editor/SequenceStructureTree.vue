<script setup lang="ts">
import { computed } from "vue";
import type { MermaidSequenceStatement } from "../model";

const props = withDefaults(defineProps<{
  statements: MermaidSequenceStatement[];
  containerId?: string;
  selectedId?: string;
  depth?: number;
}>(), {
  containerId: "root",
  selectedId: undefined,
  depth: 0
});

const emit = defineEmits<{
  select: [statementId: string];
  selectContainer: [containerId: string, label: string];
  move: [statementId: string, containerId: string, targetIndex: number];
}>();

const visibleEntries = computed(() => props.statements
  .map((statement, originalIndex) => ({ statement, originalIndex }))
  .filter(({ statement }) => statement.kind !== "locked" || !statement.hidden));

function label(statement: MermaidSequenceStatement): string {
  if (statement.kind === "message") return statement.text || `${statement.source} → ${statement.target}`;
  if (statement.kind === "note") return `Note · ${statement.text || "说明"}`;
  if (statement.kind === "activation") return `${statement.active ? "activate" : "deactivate"} · ${statement.participantId}`;
  if (statement.kind === "comment") return `注释 · ${statement.text || "空注释"}`;
  if (statement.kind === "locked") return `锁定 · ${statement.reason}`;
  return `${statement.blockType}${statement.branches[0]?.label ? ` · ${statement.branches[0].label}` : ""}`;
}

function accessibleLabel(statement: MermaidSequenceStatement): string {
  if (statement.kind === "message") return statement.text || "消息";
  if (statement.kind === "locked") return "locked";
  return label(statement);
}

function onKeydown(event: KeyboardEvent, statement: MermaidSequenceStatement, visibleIndex: number) {
  if (!event.altKey || statement.kind === "locked") return;
  if (event.key === "ArrowUp" && visibleIndex > 0) {
    event.preventDefault();
    emit("move", statement.id, props.containerId, visibleEntries.value[visibleIndex - 1]!.originalIndex);
  }
  if (event.key === "ArrowDown" && visibleIndex < visibleEntries.value.length - 1) {
    event.preventDefault();
    emit("move", statement.id, props.containerId, visibleEntries.value[visibleIndex + 1]!.originalIndex + 1);
  }
}

function onDragStart(event: DragEvent, statement: MermaidSequenceStatement) {
  if (statement.kind === "locked") {
    event.preventDefault();
    return;
  }
  event.dataTransfer?.setData("application/x-sequence-statement", statement.id);
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "move";
}

function onDrop(event: DragEvent, index: number) {
  const statementId = event.dataTransfer?.getData("application/x-sequence-statement");
  if (!statementId) return;
  event.preventDefault();
  event.stopPropagation();
  emit("move", statementId, props.containerId, index);
}
</script>

<template>
  <ol class="ta-sequence-tree" :class="{ 'is-nested': depth > 0 }" role="tree">
    <li
      v-for="(entry, index) in visibleEntries"
      :key="entry.statement.id"
      class="ta-sequence-tree-item"
      role="treeitem"
      :aria-expanded="entry.statement.kind === 'block' ? 'true' : undefined"
      @dragover.prevent
      @drop="onDrop($event, entry.originalIndex)"
    >
      <div class="ta-sequence-tree-row" :class="{ 'is-selected': selectedId === entry.statement.id, 'is-locked': entry.statement.kind === 'locked' }">
        <button
          type="button"
          class="ta-sequence-tree-drag"
          :aria-label="`移动 ${accessibleLabel(entry.statement)}`"
          :aria-disabled="entry.statement.kind === 'locked'"
          :disabled="entry.statement.kind === 'locked'"
          :draggable="entry.statement.kind !== 'locked'"
          @dragstart="onDragStart($event, entry.statement)"
        >⋮⋮</button>
        <button
          type="button"
          class="ta-sequence-tree-select"
          :aria-label="`选择 ${accessibleLabel(entry.statement)}`"
          @click="emit('select', entry.statement.id)"
          @keydown="onKeydown($event, entry.statement, index)"
        >
          <span class="ta-sequence-tree-kind">{{ entry.statement.kind === "block" ? entry.statement.blockType : entry.statement.kind }}</span>
          <span>{{ label(entry.statement) }}</span>
        </button>
      </div>
      <template v-if="entry.statement.kind === 'block'">
        <section v-for="branch in entry.statement.branches" :key="branch.id" class="ta-sequence-tree-branch">
          <button
            type="button"
            class="ta-sequence-tree-branch-label"
            :aria-label="`选择 ${branch.keyword} · ${branch.label || '空分支'} 作为插入位置`"
            @click="emit('selectContainer', branch.id, `${branch.keyword} · ${branch.label || '空分支'}`)"
          >{{ branch.keyword }} · {{ branch.label || "空分支" }}</button>
          <SequenceStructureTree
            :statements="branch.statements"
            :container-id="branch.id"
            :selected-id="selectedId"
            :depth="depth + 1"
            @select="emit('select', $event)"
            @select-container="(id, branchLabel) => emit('selectContainer', id, branchLabel)"
            @move="(id, container, target) => emit('move', id, container, target)"
          />
        </section>
      </template>
    </li>
    <li
      v-if="!visibleEntries.length"
      class="ta-sequence-tree-empty"
      role="treeitem"
      @dragover.prevent.stop
      @drop="onDrop($event, 0)"
    >空分支</li>
    <li
      v-else
      class="ta-sequence-tree-drop-end"
      role="none"
      aria-label="拖放到当前分支末尾"
      @dragover.prevent.stop
      @drop="onDrop($event, statements.length)"
    >拖放到末尾</li>
  </ol>
</template>

<style scoped>
.ta-sequence-tree { display: grid; gap: 5px; margin: 0; padding: 0; list-style: none; }
.ta-sequence-tree.is-nested { margin: 5px 0 0 10px; padding-left: 8px; border-left: 1px solid var(--ta-border, #dbe3ed); }
.ta-sequence-tree-item { min-width: 0; }
.ta-sequence-tree-row { display: flex; min-width: 0; align-items: stretch; border: 1px solid var(--ta-border, #dbe3ed); border-radius: 6px; background: var(--ta-surface, #fff); }
.ta-sequence-tree-row.is-selected { border-color: var(--primary, #4f6bed); box-shadow: 0 0 0 1px rgba(79, 107, 237, .26); box-shadow: 0 0 0 1px color-mix(in srgb, var(--primary, #4f6bed) 26%, transparent); }
.ta-sequence-tree-row.is-locked { border-style: dashed; background: #f5f6f8; color: #667085; }
.ta-sequence-tree-drag, .ta-sequence-tree-select { border: 0; background: transparent; color: inherit; font: inherit; }
.ta-sequence-tree-drag { width: 25px; flex: 0 0 25px; border-right: 1px solid var(--ta-border, #e3e8ef); cursor: grab; font-size: 10px; }
.ta-sequence-tree-drag:disabled { cursor: not-allowed; opacity: .45; }
.ta-sequence-tree-select { display: grid; min-width: 0; flex: 1; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 6px; padding: 6px; text-align: left; cursor: pointer; }
.ta-sequence-tree-select span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ta-sequence-tree-kind { border-radius: 4px; padding: 1px 4px; background: #edf2ff; color: #3b5ccc; font-size: 9px; }
.ta-sequence-tree-branch { margin: 5px 0 8px 18px; }
.ta-sequence-tree-branch-label { min-height: 24px; border: 0; padding: 2px 4px; background: transparent; color: var(--ta-muted, #64748b); font: inherit; font-size: 10px; text-align: left; cursor: pointer; }
.ta-sequence-tree-empty { padding: 6px 8px; border: 1px dashed var(--ta-border, #dbe3ed); border-radius: 5px; color: var(--ta-muted, #64748b); font-size: 10px; }
.ta-sequence-tree-drop-end { height: 5px; overflow: hidden; border-radius: 3px; color: transparent; font-size: 0; }
.ta-sequence-tree-drop-end:hover { height: 18px; border: 1px dashed var(--primary, #4f6bed); color: var(--primary, #4f6bed); font-size: 9px; text-align: center; }
</style>
