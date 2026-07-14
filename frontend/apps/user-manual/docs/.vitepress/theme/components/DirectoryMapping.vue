<script setup lang="ts">
import { computed, ref } from "vue";

type ViewKey = "structure" | "ownership";
type TreeTone = "baseline" | "new" | "local";
type TreeNode = {
  id: string;
  name: string;
  note?: string;
  physical?: string;
  tone?: TreeTone;
  children?: TreeNode[];
};
type VisibleTreeNode = { node: TreeNode; depth: number };

const activeView = ref<ViewKey>("structure");

const views: Array<{ key: ViewKey; index: string; label: string }> = [
  { key: "structure", index: "01", label: "整体目录" },
  { key: "ownership", index: "02", label: "内容与责任" }
];

const baseline = (id: string, name: string, note?: string, children?: TreeNode[]): TreeNode => ({ id, name, note, children });
const testing = (id: string, name: string, note?: string, children?: TreeNode[]): TreeNode => ({ id, name, note, tone: "new", children });
const local = (id: string, name: string, note?: string, children?: TreeNode[]): TreeNode => ({ id, name, note, tone: "local", children });
const placed = (node: TreeNode, physical: string): TreeNode => ({ ...node, physical });

// 目录节点直接按用户提供的标准工程文件建模，页面只负责逐级展开，不再压缩或拆成两棵树。
const directoryTree: TreeNode = placed(baseline("root", "应用(服务群组)工作区/", "多 Git worktree 的组合视图", [
  baseline("ai-agent", "ai-agent/", "智能研发根目录", [
    placed(baseline("agents", "agents/", "智能体逻辑目录", [
      baseline("requirements-agent", "01_需求智能体/", "需求智能体"),
      baseline("design-agent", "02_设计智能体/", "设计智能体", [
        baseline("outline-design", "01_概要设计/", "概要设计智能体", [
          baseline("outline-rules", "rules/", "规约目录", [
            baseline("outline-common-rule", "概要设计公共规约.md", "公共规约"),
            baseline("outline-app-rule", "概要设计应用规约.md", "应用规约")
          ]),
          baseline("outline-template", "template/", "模板目录"),
          baseline("outline-agent-file", "agent.md", "子智能体定义（SOP）"),
          baseline("outline-eval", "eval.md", "子智能体输出物评估")
        ]),
        baseline("detail-design", "02_详细设计/", "详细设计子智能体", [
          baseline("detail-rules", "rules/", "规约目录", [
            baseline("detail-common-rule", "详细设计公共规约.md", "公共规约"),
            baseline("detail-app-rule", "详细设计应用规约.md", "应用规约")
          ]),
          baseline("detail-template", "template/", "模板目录"),
          baseline("detail-agent-file", "agent.md", "子智能体定义（SOP）"),
          baseline("detail-eval", "eval.md", "子智能体输出物评估")
        ]),
        baseline("program-design", "03_程序设计/", "程序设计子智能体", [
          baseline("program-rules", "rules/", "规约目录", [
            baseline("program-common-rule", "程序设计公共规约.md", "公共规约"),
            baseline("program-app-rule", "程序设计应用规约.md", "应用规约")
          ]),
          baseline("program-template", "template/", "模板目录"),
          baseline("program-agent-file", "agent.md", "子智能体定义（SOP）"),
          baseline("program-eval", "evaluation.md", "子智能体输出物评估")
        ])
      ]),
      baseline("coding-agent", "03_编码智能体/", "编码智能体"),
      baseline("testing-agent", "04_测试智能体/", "开发基线已有入口，以下为测试扩展", [
        testing("test-design-agent", "01_测试设计/", "测试设计 subagent", [
          testing("test-design-agent-file", "agent.md", "子智能体定义（SOP）"),
          testing("test-design-rules", "rules/", "规约目录", [
            testing("test-design-common-rules", "测试设计公共规约/", "公共规约", [
              testing("test-design-api-rule", "接口测试设计规约.md"),
              testing("test-design-ui-rule", "UI测试设计规约.md"),
              testing("test-design-async-rule", "异步任务测试设计规约.md"),
              testing("test-design-batch-rule", "批量任务测试设计规约.md"),
              testing("test-design-other-rule", "其他测试设计规约.md")
            ]),
            testing("test-design-app-rule", "测试设计应用规约.md", "应用规约")
          ]),
          testing("test-object-agent", "001 测试对象识别/", "Skill / 编排步骤", [testing("test-object-agent-file", "SKILL.md")]),
          testing("test-case-agent", "002 测试设计及案例生成/", "Skill / 编排步骤", [testing("test-case-agent-file", "SKILL.md")]),
          testing("test-review-agent", "003 测试设计审核/", "独立审核 subagent", [testing("test-review-agent-file", "test-design-review.md")])
        ]),
        testing("test-execution-agent", "02_测试执行/", "测试执行 subagent", [
          testing("test-execution-agent-file", "agent.md", "子智能体定义（SOP）"),
          testing("test-execution-rules", "rules/", "规约目录", [
            testing("test-execution-common-rules", "测试执行公共规约/", "公共规约", [
              testing("test-execution-api-rule", "接口测试执行规约.md"),
              testing("test-execution-ui-rule", "UI测试执行规约.md"),
              testing("test-execution-more-rule", "...", "其他执行规约")
            ]),
            testing("test-execution-app-rule", "测试执行应用规约.md", "应用规约")
          ]),
          testing("script-agent", "001 测试执行脚本构造/", "Skill / 编排步骤", [testing("script-agent-file", "SKILL.md")]),
          testing("data-agent", "002 测试执行数据构造/", "Skill / 编排步骤", [testing("data-agent-file", "SKILL.md")]),
          testing("assertion-agent", "003 测试执行断言构造/", "Skill / 编排步骤", [testing("assertion-agent-file", "SKILL.md")]),
          testing("execution-review-agent", "004 测试执行审核/", "独立审核 subagent", [testing("execution-review-agent-file", "test-execution-review.md")])
        ]),
        testing("test-analysis-agent", "03_测试分析/", "测试分析 subagent", [testing("test-analysis-more", "...", "待按分析能力继续扩展")])
      ])
    ]), "应用 AI Git worktree → .opencode/agents/*.md"),
    placed(baseline("mcp", "mcp/", "MCP 逻辑定义"), "应用 AI Git → 由 opencode.jsonc 注册"),
    placed(baseline("engineering-rules", "rules/", "工程规约逻辑目录", [baseline("engineering-rule", "工程规约.md")]), "应用 AI Git → 由 AGENTS.md / instructions 引用"),
    placed(baseline("skills", "skills/", "Skill 技能目录", [
      baseline("coding-skills", "coding/", "开发已有 Skill", [
        baseline("code-review-skill", "code-review-skill/", undefined, [baseline("code-review-skill-file", "SKILL.md")])
      ]),
      testing("test-design-skills", "test-design/", "测试方法 Skill", [
        testing("test-design-skill", "test-design/", undefined, [testing("test-design-skill-file", "SKILL.md")]),
        testing("equivalence-skill", "test-design-equivalence/", undefined, [testing("equivalence-skill-file", "SKILL.md")]),
        testing("more-test-skills", "...", "继续扩展其他测试方法")
      ])
    ]), "应用 AI Git worktree → .opencode/skills/<name>/SKILL.md")
  ]),
  placed(testing("archive", "archive/", "规格文档归档目录", [
    testing("archive-period", "2601/", "归档年月", [testing("archive-item", "I000001/", "需求项编号")])
  ]), "应用 AI Git · 共享发布 worktree · 发布分支"),
  placed(local("spec", "spec/", "个人本地规格工作目录，只提交不推送", [
    local("spec-item", "I000001/", "需求项编号", [
      local("requirements-spec", "01-需求/", undefined, [local("requirements-use-case", "1_需求用例.md")]),
      local("design-spec", "02-设计/", undefined, [
        local("outline-spec", "01.概要设计.md"),
        local("subitem-spec", "S000001/", "需求子条目编号", [
          local("detail-spec", "xxx_详细设计.md"),
          local("program-spec", "xxx_程序设计.md")
        ])
      ]),
      local("coding-spec", "03-编码/", undefined, [local("business-code", "业务代码/"), local("unit-test", "单元测试/")]),
      local("testing-spec", "04-测试/", undefined, [
        local("test-design-spec", "测试设计/", undefined, [
          local("flow-test-design", "流程测试/", undefined, [
            local("flow-design-docs", "测试设计文档/", undefined, [local("flow-design-doc", "流程测试设计.md")]),
            local("flow-test-cases", "测试案例/", undefined, [local("flow-test-case", "流程测试案例.md")])
          ]),
          local("subitem-test-design", "S000001/", undefined, [
            local("subitem-design-docs", "测试设计文档/", undefined, [local("subitem-design-doc", "S000001_测试设计.md")]),
            local("subitem-test-cases", "测试案例/", undefined, [local("subitem-test-case", "S000001_测试案例.md")])
          ])
        ]),
        local("test-execution-spec", "测试执行/", undefined, [
          local("flow-test-execution", "流程测试/", undefined, [local("flow-test-data", "测试数据.md"), local("flow-test-script", "测试脚本.md")]),
          local("subitem-test-execution", "S000001/", undefined, [local("subitem-test-data", "测试数据.md"), local("subitem-test-script", "测试脚本.md")])
        ])
      ])
    ])
  ]), "应用 AI Git · 独立本地 worktree · local/spec/<用户>（无 upstream）"),
  placed(baseline("docs", "docs/", "开发知识与测试资产共用目录", [
    baseline("application-architecture", "应用架构.md", "应用关系、服务节点和功能模块清单"),
    baseline("technical-architecture", "技术架构/", "开发目录内补充测试资产", [
      baseline("engineering-overview-a", "工程概览_A.md", "技术栈、接口清单和应用关系"),
      baseline("engineering-overview-b", "工程概览_B.md", "技术栈、接口清单和应用关系"),
      baseline("engineering-overview-more", "...", "其他工程概览"),
      testing("test-overview", "测试概述.md", "目录索引、非功能要求、公共案例和环境"),
      testing("scenario-x", "场景测试说明书_XXX.md", "场景图、业务规则、核心要素和测试关注点"),
      testing("scenario-y", "场景测试说明书_YYY.md"),
      testing("scenario-more", "...", "其他场景测试说明书")
    ]),
    baseline("functional-module", "功能模块/", "开发目录内补充测试设计与案例", [
      baseline("functional-module-x", "功能模块_XXX.md", "业务说明书"),
      baseline("functional-module-y", "功能模块_YYY.md", "业务说明书"),
      testing("functional-design-x1", "测试设计文档_X1.md", "等价类表、路径图等测试设计"),
      testing("functional-design-x2", "测试设计文档_X2.md"),
      testing("functional-case-x1", "测试案例_X1.md"),
      testing("functional-case-x2", "测试案例_X2.md"),
      testing("functional-more", "...", "其他功能测试资产")
    ]),
    baseline("functional-docs", "功能文档/", "开发功能文档", [
      baseline("functional-doc-x1", "功能文档_X1.md"),
      baseline("functional-doc-x2", "功能文档_X2.md"),
      baseline("functional-doc-more", "...", "其他功能文档")
    ]),
    baseline("data-architecture", "数据架构/", "开发目录内补充测试数据实体", [
      baseline("gauss-schema", "F-ABC_Gauss_1.yaml", "开发数据架构资产"),
      baseline("mysql-schema", "F-ABC_MySQL_1.yaml", "开发数据架构资产"),
      testing("data-entity-x1", "数据实体_X1.md"),
      testing("data-entity-x2", "数据实体_X2.md")
    ]),
    baseline("business-knowledge", "业务知识/", "开发业务知识资产", [baseline("business-knowledge-more", "...", "领域术语、规则和业务说明")]),
    testing("deployment-architecture", "部署架构/", "测试新增架构资产", [testing("physical-deployment", "物理部署架构")])
  ]), "应用 AI Git · 共享发布 worktree · 发布分支"),
  placed(baseline("business-repo-a", "git-repo-A/", "业务 Git 工程 A", [
    baseline("business-repo-a-docs", "docs/", undefined, [baseline("shared-methods", "公共方法/")])
  ]), "业务 Git A · 当前用户 worktree · 当前业务分支"),
  placed(baseline("business-repo-b", "git-repo-B/", "业务 Git 工程 B"), "业务 Git B · 当前用户 worktree · 当前业务分支")
]), "组合挂载根目录（不是 Git 仓库）");

const physicalLayers = [
  {
    label: "公共能力",
    path: "${OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/<worktree>/opencode/{agents,skills}",
    detail: "公共 AI Git · 管理员工作分支；发布后同步到 OPENCODE_PUBLIC_CONFIG_DIR"
  },
  {
    label: "应用配置与资产",
    path: "${OPENCODE_PERSONAL_WORKTREE_ROOT}/<版本>/<用户>/<应用AI库>/<分支>/{.opencode,docs,archive}",
    detail: "应用 AI Git · 应用发布分支"
  },
  {
    label: "本地规格",
    path: "${OPENCODE_PERSONAL_WORKTREE_ROOT}/<版本>/<用户>/<应用AI库>/local-spec/spec",
    detail: "应用 AI Git · 独立 worktree / 本地分支 · 不设置 upstream"
  },
  {
    label: "业务工程",
    path: "<业务库A/B个人worktree>",
    detail: "各自业务 Git · 当前业务分支"
  }
];

const agentDesignRows = [
  ["测试设计 / 测试执行 / 测试分析", "可见 subagent", "放入 .opencode/agents/<name>.md；用户可用 @<name>，主 Agent 也可按 description 自动调用"],
  ["对象识别 / 案例生成 / 脚本、数据、断言构造", "Skill + 编排步骤", "放入 .opencode/skills/<name>/SKILL.md；由上层 subagent 按流程加载，不占用 @ 列表"],
  ["测试设计审核 / 测试执行审核", "独立审核 subagent", "需要独立上下文和只读权限；需人工直达时可见，否则 hidden: true，仅由上层调用"],
  ["rules / template / eval", "Skill 资源或 instructions", "不要放在 .opencode/agents/** 里作为普通 .md，避免被递归注册成 Agent"]
];

const expandedNodeIds = ref<Set<string>>(new Set(["root"]));

const visibleDirectoryNodes = computed<VisibleTreeNode[]>(() => {
  const rows: VisibleTreeNode[] = [];
  const visit = (node: TreeNode, depth: number) => {
    rows.push({ node, depth });
    if (!node.children?.length || !expandedNodeIds.value.has(node.id)) return;
    node.children.forEach((child) => visit(child, depth + 1));
  };
  visit(directoryTree, 0);
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
  collectFolderIds(directoryTree, folderIds);
  expandedNodeIds.value = folderIds;
};

const collapseToRoot = () => {
  expandedNodeIds.value = new Set(["root"]);
};

const ownershipRows = [
  ["公共 Agent / Skills", "跨应用通用智能体和测试方法", "效能组、测试管理组", "平台超级管理员"],
  ["公共规约", "跨应用统一质量要求和公共测试规约", "测试管理组", "平台超级管理员"],
  ["应用 Agent / Skills", "应用测试设计、执行、分析智能体及方法", "测试组", "应用管理员及以上"],
  ["应用规约", "结合应用特点补充的测试设计与执行规约", "测试组", "应用管理员及以上"],
  ["docs/**", "开发已有资产与新增测试资产的应用级稳定沉淀", "测试组", "应用管理员审核发布"],
  ["spec/<需求项>", "本次需求的四阶段工作事实", "当前用户与 Agent", "仅本地提交，禁止推送"],
  ["archive/<年月>/<需求项>", "完成评审后的规格快照", "测试组", "应用管理员受控发布"],
  ["git-repo-A · git-repo-B", "业务代码、单测和工程文档", "开发团队", "各业务 Git 负责人"]
];
</script>

<template>
  <section class="directory-blueprint" aria-label="开发与测试标准工程目录">
    <header class="blueprint-header">
      <div>
        <p class="eyebrow">目录说明</p>
        <h2>逐级查看完整工程目录</h2>
        <p>开发已有结构与测试扩展合并展示，颜色区分来源，点击文件夹查看下一层真实目录。</p>
      </div>
      <div class="legend" aria-label="目录图例">
        <span><i></i>开发基线</span>
        <span><i class="new"></i>测试新增</span>
        <span><i class="local"></i>个人本地</span>
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
        <div><span>合并工程树</span><strong>测试目标整体目录</strong></div>
        <div class="tree-actions">
          <button type="button" @click="expandAllDirectories">全部展开</button>
          <button type="button" @click="collapseToRoot">收起到一级</button>
        </div>
      </div>
      <p class="tree-instruction">点击带箭头的文件夹逐级展开。开发目录保持原位置，测试内容直接进入它所属的目录层级。</p>
      <div class="physical-layers" aria-label="物理目录、Git 与分支映射">
        <article v-for="layer in physicalLayers" :key="layer.label">
          <strong>{{ layer.label }}</strong>
          <code>{{ layer.path }}</code>
          <span>{{ layer.detail }}</span>
        </article>
      </div>
      <div class="tree-box merged" role="tree" aria-label="开发与测试合并工程目录">
        <button
          v-for="row in visibleDirectoryNodes"
          :key="row.node.id"
          type="button"
          class="tree-row"
          :class="row.node.tone ?? 'baseline'"
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
          <em v-if="row.node.note">{{ row.node.note }}</em>
          <small v-if="row.node.physical" class="physical-badge">{{ row.node.physical }}</small>
        </button>
      </div>
      <div class="tree-reading-note">
        <p><i></i><span><strong>开发已有</strong>保留需求、设计、编码、知识文档和业务 Git 的原有位置。</span></p>
        <p><i class="new"></i><span><strong>测试扩展</strong>在原目录中增加测试智能体、方法、归档和稳定资产。</span></p>
        <p><i class="local"></i><span><strong>个人本地</strong><code>spec</code> 四阶段内容仅在个人 worktree 提交。</span></p>
      </div>
      <section class="agent-design" aria-labelledby="agent-design-title">
        <div class="section-heading">
          <span>OpenCode 原生映射</span>
          <strong id="agent-design-title">不保留 workagent 这一技术类型</strong>
        </div>
        <p class="agent-design-summary">
          业务上仍可称“工作步骤”，技术上只落为可 <code>@</code> 的 subagent、按需加载的 Skill，或普通规则资源。
        </p>
        <div class="agent-design-grid">
          <article v-for="row in agentDesignRows" :key="row[0]">
            <strong>{{ row[0] }}</strong>
            <b>{{ row[1] }}</b>
            <p>{{ row[2] }}</p>
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
            <tr v-for="row in ownershipRows" :key="row[0]">
              <td><code>{{ row[0] }}</code></td><td>{{ row[1] }}</td><td>{{ row[2] }}</td><td>{{ row[3] }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="responsibility-rules">
        <p><b>01</b><span><strong>公共能力共同建设</strong>公共 Agent / Skills 由效能组和测试管理组建设。</span></p>
        <p><b>02</b><span><strong>规约分层负责</strong>测试管理组维护公共规约，测试组维护应用规约。</span></p>
        <p><b>03</b><span><strong>应用资产归测试组</strong><code>docs/**</code> 内容由测试组统一建设并受控发布。</span></p>
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
  --amber: #b05a00;
  color: var(--ink);
  margin: 28px 0 38px;
}

.blueprint-header {
  align-items: end;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--line);
  border-radius: 10px 10px 0 0;
  display: grid;
  gap: 28px;
  grid-template-columns: minmax(0, 1fr) auto;
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
.legend { display: grid; gap: 7px; min-width: 110px; }
.legend span { align-items: center; color: var(--muted); display: flex; font-size: 11px; gap: 8px; }
.legend i { background: var(--blue); border-radius: 50%; height: 8px; width: 8px; }
.legend i.new { background: var(--teal); }
.legend i.local { background: var(--amber); }

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

.physical-layers { display: grid; gap: 8px; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0 0 14px; }
.physical-layers article { background: var(--vp-c-bg-soft); border: 1px solid var(--line); border-left: 3px solid var(--blue); border-radius: 7px; display: grid; gap: 3px; min-width: 0; padding: 9px 10px; }
.physical-layers article:nth-child(3) { border-left-color: var(--amber); }
.physical-layers strong { color: var(--ink); font-size: 10px; }
.physical-layers code { background: transparent; color: var(--blue); font-size: 9px; line-height: 1.45; overflow-wrap: anywhere; padding: 0; }
.physical-layers article:nth-child(3) code { color: var(--amber); }
.physical-layers span { color: var(--muted); font-size: 9px; line-height: 1.45; }

.tree-box { background: var(--vp-c-bg-soft); border: 1px solid var(--line); border-radius: 8px; color: var(--ink); overflow: hidden; padding: 9px 8px; }
.tree-row { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--blue); cursor: default; display: grid; font: inherit; gap: 6px; grid-template-columns: 1px 10px 13px minmax(150px, auto) minmax(0, 1fr) auto; min-height: 30px; padding: 3px 8px 3px calc(8px + var(--depth) * 18px); text-align: left; width: 100%; }
.tree-row[aria-expanded] { cursor: pointer; }
.tree-row:hover { background: var(--vp-c-default-soft); }
.tree-row:focus-visible { background: var(--vp-c-brand-soft); outline: 1px solid currentColor; outline-offset: -1px; }
.tree-row.new { background: color-mix(in srgb, var(--teal) 6%, transparent); color: var(--teal); }
.tree-row.local { color: var(--amber); }
.tree-row code { background: transparent; color: inherit; font-size: 11px; font-weight: 650; overflow-wrap: anywhere; padding: 0; }
.tree-row em { color: var(--muted); font-size: 10px; font-style: normal; line-height: 1.45; }
.tree-row.new em { color: #548f89; }
.tree-row.local em { color: #9c7a49; }
.tree-guide { background: var(--line); height: 100%; }
.tree-row:first-child .tree-guide { opacity: 0; }
.tree-chevron { border-bottom: 1.5px solid currentColor; border-right: 1.5px solid currentColor; height: 5px; margin-left: 1px; transform: rotate(-45deg); transition: transform 140ms ease; width: 5px; }
.tree-chevron.open { transform: rotate(45deg) translate(-1px, -1px); }
.tree-chevron.leaf { border: 0; }
.tree-kind { background: currentColor; border-radius: 2px; height: 9px; opacity: .85; position: relative; width: 12px; }
.tree-kind::before { background: currentColor; border-radius: 2px 2px 0 0; content: ""; height: 3px; left: 1px; position: absolute; top: -2px; width: 6px; }
.tree-kind.file { background: transparent; border: 1px solid currentColor; border-radius: 1px; height: 12px; width: 9px; }
.tree-kind.file::before { display: none; }
.physical-badge { border: 1px solid color-mix(in srgb, currentColor 48%, var(--line)); border-radius: 999px; color: inherit; font-size: 8px; font-weight: 650; line-height: 1.4; max-width: 330px; overflow-wrap: anywhere; padding: 2px 7px; text-align: right; }
.tree-reading-note { border-top: 1px solid var(--line); display: grid; gap: 16px; grid-template-columns: repeat(3, 1fr); margin-top: 20px; padding-top: 18px; }
.tree-reading-note p { align-items: start; display: grid; gap: 8px; grid-template-columns: 8px 1fr; margin: 0; }
.tree-reading-note i { background: var(--blue); border-radius: 50%; height: 8px; margin-top: 4px; width: 8px; }
.tree-reading-note i.new { background: var(--teal); }
.tree-reading-note i.local { background: var(--amber); }
.tree-reading-note strong { color: var(--ink); display: block; font-size: 11px; }
.tree-reading-note span { color: var(--muted); font-size: 10px; line-height: 1.55; }
.tree-reading-note code { font-size: 9px; }

.agent-design { border-top: 1px solid var(--line); margin-top: 20px; padding-top: 18px; }
.agent-design-summary { color: var(--muted); font-size: 11px; line-height: 1.6; margin: -5px 0 12px; }
.agent-design-summary code { color: var(--teal); font-size: 10px; }
.agent-design-grid { display: grid; gap: 8px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.agent-design-grid article { border: 1px solid var(--line); border-radius: 7px; display: grid; gap: 5px; padding: 11px; }
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
  .blueprint-header { align-items: start; grid-template-columns: 1fr; padding: 22px 18px; }
  .legend { display: flex; flex-wrap: wrap; }
  .blueprint-tabs small { display: none; }
  .blueprint-tabs button { font-size: 12px; padding: 13px 5px; }
  .blueprint-panel { padding: 22px 16px; }
  .tree-heading { align-items: start; flex-direction: column; }
  .physical-layers { grid-template-columns: 1fr; }
  .tree-row { gap: 4px; grid-template-columns: 1px 9px 12px minmax(100px, auto); padding-left: calc(5px + var(--depth) * 14px); }
  .tree-row em { display: none; }
  .physical-badge { grid-column: 4; justify-self: start; margin: 2px 0 4px; max-width: min(260px, 70vw); text-align: left; }
  .tree-reading-note { grid-template-columns: 1fr; }
  .agent-design-grid { grid-template-columns: 1fr; }
  .responsibility-rules { grid-template-columns: 1fr; }
  .section-heading.wide { align-items: start; flex-direction: column; }
}
</style>
