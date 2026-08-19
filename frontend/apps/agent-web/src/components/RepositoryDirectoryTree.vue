<script setup lang="ts">
import { defineComponent, h, ref, watch, type PropType, type VNode } from "vue";
import type { RepositoryTreeNode } from "@test-agent/shared-types";

type DirectoryTreeNode = RepositoryTreeNode & {
  children?: DirectoryTreeNode[];
  directoryNew?: boolean;
};

const props = withDefaults(defineProps<{
  nodes: DirectoryTreeNode[];
  selectedPath?: string;
  selectionMode?: "any-directory" | "test-workspace";
  appName?: string;
}>(), {
  selectedPath: "",
  selectionMode: "any-directory",
  appName: ""
});

const emit = defineEmits<{
  select: [node: DirectoryTreeNode];
}>();

/** 测试工作空间只能选择应用同名目录的一级子目录；只读引用可选择任意已有目录。 */
function selectable(node: DirectoryTreeNode) {
  if (node.type !== "directory") return false;
  if (props.selectionMode === "any-directory") return true;
  const parts = node.path.split("/");
  return parts.length === 2 && parts[0] === props.appName && Boolean(parts[1]);
}

function underCurrentApp(node: DirectoryTreeNode) {
  if (props.selectionMode === "any-directory") return true;
  return node.path === props.appName || node.path.startsWith(`${props.appName}/`);
}

function nodeTitle(node: DirectoryTreeNode) {
  if (selectable(node)) return props.selectionMode === "any-directory" ? "选择为引用目录" : "选择为工作空间";
  if (node.type === "file") return "文件仅可浏览，不能选择";
  if (!underCurrentApp(node)) return "其他应用目录仅可查看，不能选择为工作空间";
  return props.selectionMode === "test-workspace" ? "测试工作库只能选择应用同名目录的一级子目录" : "目录";
}

/** 目录树统一维护展开状态，供测试工作空间和自动化只读引用复用。 */
const TreeRenderer = defineComponent({
  name: "RepositoryDirectoryTreeRenderer",
  props: {
    nodes: { type: Array as PropType<DirectoryTreeNode[]>, required: true },
    selectedPath: { type: String, default: "" }
  },
  emits: ["select"],
  setup(treeProps, { emit: treeEmit }) {
    const expandedPaths = ref(new Set<string>());
    const collectDefaultExpandedPaths = (nodes: DirectoryTreeNode[], depth = 0, acc = new Set<string>()) => {
      for (const node of nodes) {
        if (node.type === "directory" && (node.children?.length ?? 0) > 0 && depth <= 2) {
          acc.add(node.path);
          collectDefaultExpandedPaths(node.children ?? [], depth + 1, acc);
        }
      }
      return acc;
    };
    watch(
      () => treeProps.nodes,
      (nodes) => {
        expandedPaths.value = collectDefaultExpandedPaths(nodes);
      },
      { immediate: true }
    );
    const toggleExpanded = (path: string) => {
      const next = new Set(expandedPaths.value);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      expandedPaths.value = next;
    };
    const renderNodes = (nodes: DirectoryTreeNode[], depth = 0): VNode => h(
      "ul",
      { class: "ta-repository-tree-list", "data-depth": String(depth) },
      nodes.map((node) => {
        const canSelect = selectable(node);
        const expandable = node.type === "directory" && (node.children?.length ?? 0) > 0;
        const expanded = expandedPaths.value.has(node.path);
        return h("li", { key: node.path, class: ["ta-repository-tree-item", `is-${node.type}`] }, [
          h(
            "button",
            {
              type: "button",
              class: ["ta-repository-tree-node", { "is-selected": treeProps.selectedPath === node.path, "is-selectable": canSelect }],
              style: { paddingLeft: `${8 + depth * 16}px` },
              disabled: !canSelect && (!expandable || !underCurrentApp(node)),
              title: nodeTitle(node),
              "aria-expanded": expandable ? String(expanded) : undefined,
              "aria-label": canSelect ? `选择目录 ${node.path}` : undefined,
              onClick: () => {
                if (expandable) toggleExpanded(node.path);
                if (canSelect) treeEmit("select", node);
              }
            },
            [
              h("span", { class: "ta-repository-tree-icon", "aria-hidden": "true" }, expandable ? (expanded ? "▾" : "▸") : "·"),
              // 保留旧 class 供现有真实验收定位测试工作空间目录；新语义 class 供引用配置复用。
              h("span", { class: "ta-repository-tree-path ta-workspace-tree-path" }, node.path),
              node.directoryNew ? h("span", { class: "ta-repository-tree-badge" }, "新增") : null
            ]
          ),
          expanded ? renderNodes(node.children ?? [], depth + 1) : null
        ]);
      })
    );
    return () => renderNodes(treeProps.nodes);
  }
});
</script>

<template>
  <TreeRenderer :nodes="nodes" :selected-path="selectedPath" @select="emit('select', $event)" />
</template>

<style scoped>
:deep(.ta-repository-tree-list) {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}
:deep(.ta-repository-tree-item) {
  margin: 0;
  padding: 0;
}
:deep(.ta-repository-tree-node) {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  height: 24px;
  border: 0;
  background: transparent;
  color: #374151;
  font-family: var(--ta-tree-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif);
  font-size: 13px;
  line-height: 24px;
  text-align: left;
  transition: background-color 0.12s ease, color 0.12s ease;
}
:deep(.ta-repository-tree-node:not(:disabled)) {
  cursor: pointer;
}
:deep(.ta-repository-tree-node:not(:disabled):hover) {
  background: #eef2f7;
  color: #111827;
}
:deep(.ta-repository-tree-node:disabled) {
  cursor: default;
  color: #9ca3af;
}
:deep(.ta-repository-tree-node.is-selected) {
  background: #e8f0ff;
  color: #1d4ed8;
  font-weight: 500;
}
:deep(.ta-repository-tree-icon) {
  flex: 0 0 auto;
  width: 12px;
  color: #9ca3af;
  font-size: 10px;
  line-height: 1;
  text-align: center;
}
:deep(.ta-repository-tree-path) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
:deep(.ta-repository-tree-badge) {
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: 4px;
  background: #ecfdf3;
  color: #168a52;
  font-size: 11px;
}
</style>
