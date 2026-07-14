<script setup lang="ts">
import { computed, ref } from "vue";
import { useData } from "vitepress";

type ViewKey = "structure" | "ownership";
type TreeScope = "structure" | "development" | "testing" | "shared" | "local";
type AgentRole = "agent" | "workagent";
type ImplementationStatus = "implemented" | "planned";
type TreeNode = {
  id: string;
  name: string;
  scope: TreeScope;
  note?: string;
  physical?: string;
  role?: AgentRole;
  implementation?: ImplementationStatus;
  children?: TreeNode[];
};
type VisibleTreeNode = { node: TreeNode; depth: number; physical?: string; implementation?: ImplementationStatus };
type AgentDesignRow = { scope: string; role: string; description: string };
type OwnershipRow = { directory: string; content: string; builders: string; publisher: string };
type ResponsibilityRule = { index: string; title: string; description: string };
type DirectoryMappingData = {
  defaultExpanded: string[];
  repositorySummaries: Array<{ name: string; content: string }>;
  agentPhysicalSummary: string;
  skillStatusSummary: string;
  directoryTree: TreeNode;
  agentDesignSummary: string;
  agentDesignRows: AgentDesignRow[];
  ownershipRows: OwnershipRow[];
  responsibilityRules: ResponsibilityRule[];
};

const { frontmatter } = useData();
const mappingData = computed(() => {
  const data = frontmatter.value.directoryMapping as DirectoryMappingData | undefined;
  if (!data) throw new Error("directory-mapping.md 缺少 directoryMapping 数据");
  return data;
});

const activeView = ref<ViewKey>("structure");
const expandedNodeIds = ref<Set<string>>(new Set(mappingData.value.defaultExpanded));

const views: Array<{ key: ViewKey; index: string; label: string }> = [
  { key: "structure", index: "01", label: "整体目录" },
  { key: "ownership", index: "02", label: "内容与责任" }
];

const scopeLabels: Record<TreeScope, string> = {
  structure: "目录",
  development: "开发",
  testing: "测试",
  shared: "开发 + 测试",
  local: "个人本地"
};

const visibleDirectoryNodes = computed<VisibleTreeNode[]>(() => {
  const rows: VisibleTreeNode[] = [];
  const visit = (node: TreeNode, depth: number, inheritedPhysical?: string, inheritedImplementation?: ImplementationStatus) => {
    const physical = node.physical ?? inheritedPhysical;
    const implementation = node.implementation ?? inheritedImplementation;
    rows.push({ node, depth, physical, implementation });
    if (!node.children?.length || !expandedNodeIds.value.has(node.id)) return;
    node.children.forEach((child) => visit(child, depth + 1, physical, implementation));
  };
  visit(mappingData.value.directoryTree, 0);
  return rows;
});

const toggleDirectoryNode = (node: TreeNode) => {
  if (!node.children?.length) return;
  const next = new Set(expandedNodeIds.value);
  next.has(node.id) ? next.delete(node.id) : next.add(node.id);
  expandedNodeIds.value = next;
};

const collectFolderIds = (node: TreeNode, result: Set<string>) => {
  if (!node.children?.length) return;
  result.add(node.id);
  node.children.forEach((child) => collectFolderIds(child, result));
};

const expandAllDirectories = () => {
  const folderIds = new Set<string>();
  collectFolderIds(mappingData.value.directoryTree, folderIds);
  expandedNodeIds.value = folderIds;
};

const collapseToRoot = () => {
  expandedNodeIds.value = new Set(["root"]);
};
</script>

<template>
  <section class="directory-blueprint" aria-label="开发与测试标准工程目录">
    <header class="blueprint-header">
      <div>
        <p class="eyebrow">目录说明</p>
        <h2>逐级查看完整工程目录</h2>
        <p>目录名统一使用中性色；小标签分别说明内容范围、Agent 形态和实际所属 Git。</p>
      </div>
    </header>

    <nav class="blueprint-tabs" aria-label="目录设计视图">
      <button
        v-for="view in views"
        :key="view.key"
        type="button"
        :class="{ active: activeView === view.key }"
        :aria-pressed="activeView === view.key"
        @click="activeView = view.key"
      >
        <small>{{ view.index }}</small>{{ view.label }}
      </button>
    </nav>

    <div v-if="activeView === 'structure'" class="blueprint-panel">
      <div class="section-heading tree-heading">
        <div><span>合并工程树</span><strong>开发测试整体目录</strong></div>
        <div class="tree-actions">
          <button type="button" @click="expandAllDirectories">全部展开</button>
          <button type="button" @click="collapseToRoot">收起到一级</button>
        </div>
      </div>
      <p class="tree-instruction">点击文件夹逐级展开。标签按“开发/测试范围 → Agent 形态 → 实现状态 → 物理 Git”阅读，不再用整行颜色表达多个含义。</p>
      <p class="git-scope-note">
        <span v-for="repository in mappingData.repositorySummaries" :key="repository.name"><strong>{{ repository.name }}</strong>{{ repository.content }}</span>
      </p>
      <p class="agent-physical-note">{{ mappingData.agentPhysicalSummary }}</p>
      <p class="skill-status-note">{{ mappingData.skillStatusSummary }}</p>
      <div class="tree-box merged" role="tree" aria-label="开发与测试合并工程目录">
        <button
          v-for="row in visibleDirectoryNodes"
          :key="row.node.id"
          type="button"
          class="tree-row"
          :class="[row.node.scope, row.implementation]"
          :style="{ '--depth': row.depth }"
          :aria-expanded="row.node.children?.length ? expandedNodeIds.has(row.node.id) : undefined"
          :aria-level="row.depth + 1"
          :aria-label="`${row.node.name}${row.node.children?.length ? '，点击展开或收起' : ''}`"
          role="treeitem"
          @click="toggleDirectoryNode(row.node)"
        >
          <span class="tree-guide" aria-hidden="true"></span>
          <span class="tree-chevron" :class="{ open: expandedNodeIds.has(row.node.id), leaf: !row.node.children?.length }" aria-hidden="true"></span>
          <span class="tree-kind" :class="{ file: !row.node.name.endsWith('/') }" aria-hidden="true"></span>
          <code>{{ row.node.name }}</code>
          <span class="tree-tags">
            <small class="scope-badge" :class="row.node.scope">{{ scopeLabels[row.node.scope] }}</small>
            <small v-if="row.node.role" class="role-badge" :class="row.node.role">{{ row.node.role === "agent" ? "Agent" : "workagent" }}</small>
            <small v-if="row.node.implementation" class="implementation-badge" :class="row.node.implementation">{{ row.node.implementation === "implemented" ? "已实现" : "未实现" }}</small>
          </span>
          <em v-if="row.node.note">{{ row.node.note }}</em>
          <small v-if="row.physical" class="physical-badge">{{ row.physical }}</small>
        </button>
      </div>
      <section class="agent-design" aria-labelledby="agent-design-title">
        <div class="section-heading">
          <span>OpenCode 原生映射</span>
          <strong id="agent-design-title">工作 Agent 统一称为 workagent</strong>
        </div>
        <p class="agent-design-summary">{{ mappingData.agentDesignSummary }}</p>
        <div class="agent-design-grid">
          <article v-for="row in mappingData.agentDesignRows" :key="row.scope">
            <strong>{{ row.scope }}</strong>
            <b>{{ row.role }}</b>
            <p>{{ row.description }}</p>
          </article>
        </div>
      </section>
    </div>

    <div v-else class="blueprint-panel">
      <div class="section-heading wide"><span>建设责任</span><strong>公共治理与应用测试建设分开</strong></div>
      <div class="ownership-wrap">
        <table class="ownership-table">
          <thead><tr><th>目录</th><th>放什么</th><th>主要建设者</th><th>发布责任</th></tr></thead>
          <tbody>
            <tr v-for="row in mappingData.ownershipRows" :key="row.directory">
              <td><code>{{ row.directory }}</code></td><td>{{ row.content }}</td><td>{{ row.builders }}</td><td>{{ row.publisher }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="responsibility-rules">
        <p v-for="rule in mappingData.responsibilityRules" :key="rule.index"><b>{{ rule.index }}</b><span><strong>{{ rule.title }}</strong>{{ rule.description }}</span></p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.directory-blueprint {
  --ink: var(--vp-c-text-1);
  --muted: var(--vp-c-text-2);
  --line: var(--vp-c-divider);
  --teal: #00845a;
  --blue: #2557a7;
  --structure: #586273;
  --shared: #6556b3;
  --amber: #b05a00;
  color: var(--ink);
  margin: 28px 0 38px;
}

.blueprint-header {
  align-items: end;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--line);
  border-radius: 10px 10px 0 0;
  padding: 18px 20px;
}

.eyebrow,
.section-heading span {
  color: var(--teal);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  margin: 0 0 5px;
}

.blueprint-header h2 {
  border: 0;
  color: var(--ink);
  font-size: 20px;
  letter-spacing: -.015em;
  margin: 0;
  padding: 0;
}

.blueprint-header p:not(.eyebrow) { color: var(--muted); font-size: 14px; line-height: 1.65; margin: 6px 0 0; max-width: 700px; }

.blueprint-tabs { border: 1px solid var(--line); border-top: 0; display: grid; grid-template-columns: repeat(2, 1fr); }
.blueprint-tabs button { background: var(--vp-c-bg); border: 0; border-right: 1px solid var(--line); color: var(--muted); cursor: pointer; font-size: 13px; font-weight: 600; padding: 12px 10px; }
.blueprint-tabs button:last-child { border-right: 0; }
.blueprint-tabs button:hover { background: var(--vp-c-bg-soft); }
.blueprint-tabs button.active { box-shadow: inset 0 -3px 0 var(--teal); color: var(--ink); }
.blueprint-tabs small { color: var(--teal); font-family: var(--vp-font-family-mono); margin-right: 10px; }

.blueprint-panel { background: var(--vp-c-bg); border: 1px solid var(--line); border-radius: 0 0 10px 10px; border-top: 0; padding: 22px 20px 24px; }
.section-heading { margin-bottom: 12px; }
.section-heading strong { color: var(--ink); display: block; font-size: 18px; letter-spacing: -.02em; }
.section-heading.wide { align-items: end; display: flex; justify-content: space-between; }

.tree-heading { align-items: end; display: flex; gap: 18px; justify-content: space-between; }
.tree-actions { display: flex; gap: 7px; }
.tree-actions button { background: var(--vp-c-bg); border: 1px solid var(--line); border-radius: 6px; color: var(--vp-c-brand-1); cursor: pointer; font-size: 11px; padding: 5px 9px; }
.tree-actions button:hover { background: var(--vp-c-brand-soft); border-color: var(--vp-c-brand-2); color: var(--ink); }
.tree-actions button:focus-visible { outline: 2px solid var(--teal); outline-offset: 2px; }
.tree-instruction { color: var(--muted); font-size: 11px; line-height: 1.6; margin: -2px 0 12px; }
.git-scope-note { border-left: 3px solid var(--vp-c-brand-1); color: var(--muted); display: flex; flex-wrap: wrap; font-size: 9px; gap: 6px 18px; line-height: 1.5; margin: 0 0 7px; padding: 4px 0 4px 10px; }
.git-scope-note span { display: inline-flex; gap: 5px; }
.git-scope-note strong { color: var(--ink); }
.agent-physical-note { background: var(--vp-c-bg-soft); border-radius: 5px; color: var(--muted); font-size: 9px; line-height: 1.55; margin: 0 0 12px; padding: 7px 10px; }
.agent-physical-note code { color: var(--ink); font-size: 9px; font-weight: 700; }
.agent-physical-note strong { color: var(--ink); }
.skill-status-note { border-left: 2px solid var(--teal); color: var(--muted); font-size: 9px; line-height: 1.55; margin: -5px 0 12px; padding-left: 8px; }

.tree-box { background: var(--vp-c-bg-soft); border: 1px solid var(--line); border-radius: 8px; color: var(--ink); overflow: hidden; padding: 9px 8px; }
.tree-row { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--ink); cursor: default; display: grid; font: inherit; gap: 6px; grid-template-columns: 1px 10px 13px minmax(130px, auto) auto minmax(0, 1fr) auto; min-height: 30px; padding: 3px 8px 3px calc(8px + var(--depth) * 18px); text-align: left; width: 100%; }
.tree-row[aria-expanded] { cursor: pointer; }
.tree-row:hover { background: var(--vp-c-default-soft); }
.tree-row:focus-visible { background: var(--vp-c-brand-soft); outline: 1px solid currentColor; outline-offset: -1px; }
.tree-row code { background: transparent; color: inherit; font-size: 11px; font-weight: 650; overflow-wrap: anywhere; padding: 0; }
.tree-row em { color: var(--muted); font-size: 10px; font-style: normal; line-height: 1.45; }
.tree-tags { display: flex; gap: 4px; white-space: nowrap; }
.scope-badge,
.role-badge,
.implementation-badge { border-radius: 999px; font-size: 8px; font-weight: 700; line-height: 1.35; padding: 2px 6px; }
.scope-badge.structure { background: color-mix(in srgb, var(--structure) 9%, transparent); color: var(--structure); }
.scope-badge.development { background: color-mix(in srgb, var(--blue) 9%, transparent); color: var(--blue); }
.scope-badge.testing { background: color-mix(in srgb, var(--teal) 9%, transparent); color: var(--teal); }
.scope-badge.shared { background: color-mix(in srgb, var(--shared) 10%, transparent); color: var(--shared); }
.scope-badge.local { background: color-mix(in srgb, var(--amber) 10%, transparent); color: var(--amber); }
.role-badge { border: 1px solid var(--line); color: var(--ink); }
.role-badge.workagent { border-color: color-mix(in srgb, var(--teal) 55%, var(--line)); border-style: dashed; color: var(--teal); }
.implementation-badge.implemented { background: color-mix(in srgb, var(--teal) 11%, transparent); color: var(--teal); }
.implementation-badge.planned { background: var(--vp-c-default-soft); color: var(--vp-c-text-3); }
.tree-row.planned { color: var(--vp-c-text-3); }
.tree-row.planned em,
.tree-row.planned .physical-badge { color: var(--vp-c-text-3); opacity: .72; }
.tree-row.planned .scope-badge { background: var(--vp-c-default-soft); color: var(--vp-c-text-3); }
.tree-guide { background: var(--line); height: 100%; }
.tree-row:first-child .tree-guide { opacity: 0; }
.tree-chevron { border-bottom: 1.5px solid currentColor; border-right: 1.5px solid currentColor; height: 5px; margin-left: 1px; transform: rotate(-45deg); transition: transform 140ms ease; width: 5px; }
.tree-chevron.open { transform: rotate(45deg) translate(-1px, -1px); }
.tree-chevron.leaf { border: 0; }
.tree-kind { background: currentColor; border-radius: 2px; height: 9px; opacity: .85; position: relative; width: 12px; }
.tree-kind::before { background: currentColor; border-radius: 2px 2px 0 0; content: ""; height: 3px; left: 1px; position: absolute; top: -2px; width: 6px; }
.tree-kind.file { background: transparent; border: 1px solid currentColor; border-radius: 1px; height: 12px; width: 9px; }
.tree-kind.file::before { display: none; }
.physical-badge { border: 1px solid var(--line); border-radius: 999px; color: var(--muted); font-size: 8px; font-weight: 650; line-height: 1.4; max-width: 330px; overflow-wrap: anywhere; padding: 2px 7px; text-align: right; }

.agent-design { border-top: 1px solid var(--line); margin-top: 18px; padding-top: 18px; }
.agent-design-summary { color: var(--muted); font-size: 11px; line-height: 1.6; margin: -5px 0 12px; }
.agent-design-summary code { color: var(--teal); font-size: 10px; }
.agent-design-grid { border: 1px solid var(--line); border-radius: 7px; display: grid; overflow: hidden; }
.agent-design-grid article { align-items: start; border-bottom: 1px solid var(--line); display: grid; gap: 10px; grid-template-columns: minmax(150px, .7fr) 90px minmax(0, 2fr); padding: 9px 11px; }
.agent-design-grid article:last-child { border-bottom: 0; }
.agent-design-grid strong { color: var(--ink); font-size: 11px; }
.agent-design-grid b { color: var(--teal); font-size: 10px; }
.agent-design-grid p { color: var(--muted); font-size: 9px; line-height: 1.55; margin: 0; }

.ownership-wrap { border: 1px solid var(--line); border-radius: 9px; overflow-x: auto; }
.ownership-table { border-collapse: collapse; display: table; font-size: 11px; margin: 0; min-width: 820px; width: 100%; }
.ownership-table th { background: var(--vp-c-bg-soft); color: var(--muted); font-size: 10px; letter-spacing: .04em; text-align: left; }
.ownership-table th,
.ownership-table td { border: 0; border-bottom: 1px solid var(--line); line-height: 1.5; padding: 10px 11px; vertical-align: top; }
.ownership-table tbody tr:last-child td { border-bottom: 0; }
.ownership-table code { background: transparent; color: var(--blue); font-size: 10px; font-weight: 650; overflow-wrap: anywhere; padding: 0; }

.ownership-table td:first-child { min-width: 185px; }
.responsibility-rules { display: grid; gap: 18px; grid-template-columns: repeat(3, 1fr); margin-top: 20px; }
.responsibility-rules p { align-items: start; display: grid; gap: 10px; grid-template-columns: 24px 1fr; margin: 0; }
.responsibility-rules b { align-items: center; background: var(--vp-c-brand-1); border-radius: 50%; color: #fff; display: flex; font-family: var(--vp-font-family-mono); font-size: 9px; height: 24px; justify-content: center; }
.responsibility-rules strong { color: var(--ink); display: block; font-size: 11px; }
.responsibility-rules span { color: var(--muted); font-size: 10px; line-height: 1.55; }

@media (max-width: 760px) {
  .blueprint-header { padding: 22px 18px; }
  .blueprint-tabs small { display: none; }
  .blueprint-tabs button { font-size: 12px; padding: 13px 5px; }
  .blueprint-panel { padding: 22px 16px; }
  .tree-heading { align-items: start; flex-direction: column; }
  .git-scope-note { display: grid; gap: 5px; }
  .tree-row { gap: 4px; grid-template-columns: 1px 9px 12px minmax(90px, 1fr) auto; padding-left: calc(5px + var(--depth) * 14px); }
  .tree-row em { display: none; }
  .physical-badge { grid-column: 4 / -1; justify-self: start; margin: 2px 0 4px; max-width: min(260px, 70vw); text-align: left; }
  .agent-design-grid article { grid-template-columns: 1fr; gap: 4px; }
  .responsibility-rules { grid-template-columns: 1fr; }
  .section-heading.wide { align-items: start; flex-direction: column; }
}
</style>
