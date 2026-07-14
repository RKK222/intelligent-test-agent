<script setup lang="ts">
import { ref } from "vue";

type ViewKey = "structure" | "testing" | "ownership";
type TreeLine = { depth: number; name: string; note?: string; tone?: "new" | "local" };

const activeView = ref<ViewKey>("structure");

const views: Array<{ key: ViewKey; index: string; label: string }> = [
  { key: "structure", index: "01", label: "整体结构" },
  { key: "testing", index: "02", label: "测试目录" },
  { key: "ownership", index: "03", label: "内容与责任" }
];

// 目录行直接来自用户提供的标准工程目录，只压缩重复的子目录用于页面展示。
const developmentTree: TreeLine[] = [
  { depth: 0, name: "应用(服务群组)GIT库/", note: "工程根目录" },
  { depth: 1, name: "ai-agent/", note: "智能研发根目录" },
  { depth: 2, name: "agents/" },
  { depth: 3, name: "01_需求智能体/" },
  { depth: 3, name: "02_设计智能体/" },
  { depth: 4, name: "01_概要设计/ · 02_详细设计/ · 03_程序设计/" },
  { depth: 5, name: "rules/ · template/ · agent.md · eval.md" },
  { depth: 3, name: "03_编码智能体/" },
  { depth: 3, name: "04_测试智能体/", note: "开发原版仅保留入口" },
  { depth: 2, name: "mcp/ · rules/" },
  { depth: 2, name: "skills/coding/code-review-skill/" },
  { depth: 2, name: "archive/2601/I000001/" },
  { depth: 2, name: "spec/I000001/", note: "需求、概要设计、子条目设计" },
  { depth: 1, name: "docs/", note: "开发知识文档" },
  { depth: 2, name: "应用架构.md · 技术架构/ · 功能模块/" },
  { depth: 2, name: "功能文档/ · 数据架构/ · 业务知识/" },
  { depth: 1, name: "git-repo-A/ · git-repo-B/", note: "业务代码工程" }
];

const testingTree: TreeLine[] = [
  { depth: 0, name: "应用(服务群组)GIT库/", note: "测试目标工程视图" },
  { depth: 1, name: "ai-agent/" },
  { depth: 2, name: "agents/04_测试智能体/", tone: "new" },
  { depth: 3, name: "01_测试设计/", note: "对象识别、案例生成、审核", tone: "new" },
  { depth: 3, name: "02_测试执行/", note: "脚本、数据、断言、审核", tone: "new" },
  { depth: 3, name: "03_测试分析/", tone: "new" },
  { depth: 2, name: "skills/test-design/*/", note: "等价类等测试方法", tone: "new" },
  { depth: 1, name: "archive/<年月>/<需求项>/", note: "已确认规格快照" },
  { depth: 1, name: "spec/<需求项>/", note: "个人本地 worktree", tone: "local" },
  { depth: 2, name: "01-需求/ · 02-设计/ · 03-编码/", tone: "local" },
  { depth: 2, name: "04-测试/测试设计/", note: "流程测试 + 子条目", tone: "local" },
  { depth: 2, name: "04-测试/测试执行/", note: "数据、脚本、断言与结果", tone: "local" },
  { depth: 1, name: "docs/", note: "应用级稳定测试资产", tone: "new" },
  { depth: 2, name: "应用架构.md · 技术架构/", tone: "new" },
  { depth: 2, name: "功能模块/ · 数据架构/ · 部署架构/", tone: "new" },
  { depth: 1, name: "git-repo-A/ · git-repo-B/", note: "继续复用业务 Git" }
];

const pageMappings = [
  {
    label: "智能体能力",
    visible: "测试设计 · 测试执行 · 测试分析",
    paths: ["ai-agent/agents/04_测试智能体/", "ai-agent/skills/test-design/"]
  },
  {
    label: "当前规格",
    visible: "需求 · 设计 · 编码 · 测试",
    paths: ["spec/<需求项>/01-需求…04-测试/"],
    local: true
  },
  {
    label: "业务测试资产",
    visible: "场景、规则、核心要素、测试关注点",
    paths: ["docs/技术架构/场景测试说明书_*.md"]
  },
  {
    label: "功能测试资产",
    visible: "测试设计文档与测试案例",
    paths: ["docs/功能模块/测试设计文档_*.md", "docs/功能模块/测试案例_*.md"]
  },
  {
    label: "架构测试资产",
    visible: "应用、技术、数据、部署与非功能基线",
    paths: ["docs/应用架构.md", "docs/数据架构/", "docs/部署架构/"]
  },
  {
    label: "业务代码",
    visible: "工程 A · 工程 B · 公共方法",
    paths: ["git-repo-A/", "git-repo-B/"]
  }
];

const workspaceRows = [
  {
    area: "公共能力",
    disk: "<workspace>/public-ai/",
    git: "公共 AI Git",
    branch: "平台发布分支",
    policy: "平台更新"
  },
  {
    area: "ai-agent · docs · archive",
    disk: "<workspace>/application-ai/shared/",
    git: "应用 AI Git",
    branch: "应用发布分支",
    policy: "管理员发布，用户默认更新"
  },
  {
    area: "spec",
    disk: "<workspace>/application-ai/spec/<user>/<worktree>/",
    git: "应用 AI Git 本地 worktree",
    branch: "local/spec/<user>/<worktree>",
    policy: "只提交本地，禁止 push",
    local: true
  },
  {
    area: "git-repo-A · git-repo-B",
    disk: "<workspace>/business/<repo>/<worktree>/",
    git: "各业务开发 Git",
    branch: "当前研发个人分支",
    policy: "沿用研发流程"
  }
];

const ownershipRows = [
  ["公共 Agent / Skill / MCP / 公共规约", "跨应用通用能力和治理基线", "AI 平台、测试效能团队", "平台超级管理员"],
  ["ai-agent/agents/04_测试智能体", "测试设计、执行、分析 SOP 及工作 Agent", "应用测试架构师、测试负责人", "应用管理员及以上"],
  ["ai-agent/skills/test-design", "等价类、流程、接口、UI 等方法", "测试方法专家", "应用管理员及以上"],
  ["docs/技术架构", "测试概述、场景说明、规则和关注点", "业务专家、架构师、测试架构师", "应用管理员审核发布"],
  ["docs/功能模块", "功能测试设计文档与测试案例", "需求、开发和测试设计人员", "应用管理员审核发布"],
  ["docs/应用架构 · 数据架构 · 部署架构", "应用关系、实体、部署和非功能基线", "架构、开发、运维、安全人员", "应用管理员审核发布"],
  ["spec/<需求项>", "本次需求的四阶段工作事实", "当前用户与 Agent", "仅本地提交，禁止推送"],
  ["archive/<年月>/<需求项>", "完成评审后的规格快照", "测试负责人整理", "应用管理员受控发布"],
  ["git-repo-A · git-repo-B", "业务代码、单测和工程文档", "开发团队", "各业务 Git 负责人"]
];
</script>

<template>
  <section class="directory-blueprint" aria-label="开发与测试标准工程目录">
    <header class="blueprint-header">
      <div>
        <p class="eyebrow">APPLICATION ENGINEERING DIRECTORY</p>
        <h2>同一应用工程，从开发基线扩展到测试闭环</h2>
        <p>保持需求、设计、编码和业务 Git 不变，补齐测试智能体、四阶段规格、测试资产和受控归档。</p>
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
      <div class="tree-compare">
        <section>
          <div class="section-heading"><span>开发原版</span><strong>研发标准工程树</strong></div>
          <div class="tree-box">
            <p
              v-for="(line, index) in developmentTree"
              :key="`${line.name}-${index}`"
              :style="{ '--depth': line.depth }"
            >
              <b>{{ line.depth ? '├─' : '' }}</b><code>{{ line.name }}</code><em v-if="line.note">{{ line.note }}</em>
            </p>
          </div>
        </section>

        <div class="compare-arrow" aria-hidden="true"><span>+</span><b>测试闭环</b></div>

        <section>
          <div class="section-heading"><span>测试目标</span><strong>扩展后的完整工程树</strong></div>
          <div class="tree-box target">
            <p
              v-for="(line, index) in testingTree"
              :key="`${line.name}-${index}`"
              :class="line.tone"
              :style="{ '--depth': line.depth }"
            >
              <b>{{ line.depth ? '├─' : '' }}</b><code>{{ line.name }}</code><em v-if="line.note">{{ line.note }}</em>
            </p>
          </div>
        </section>
      </div>

      <div class="change-strip">
        <p><strong>补智能体</strong><span>测试设计、执行、分析形成完整编排</span></p>
        <p><strong>改规格树</strong><span>spec 固定为需求、设计、编码、测试四阶段</span></p>
        <p><strong>沉淀资产</strong><span>docs 保存跨需求复用的测试知识</span></p>
        <p><strong>保留代码库</strong><span>业务 Git 继续由开发团队独立维护</span></p>
      </div>
    </div>

    <div v-else-if="activeView === 'testing'" class="blueprint-panel">
      <div class="section-heading wide"><span>页面映射</span><strong>页面按工作对象展示，磁盘保留标准目录</strong></div>
      <div class="mapping-list">
        <article v-for="item in pageMappings" :key="item.label">
          <div class="mapping-title">
            <strong>{{ item.label }}</strong>
            <span :class="{ local: item.local }">{{ item.local ? '仅本地' : '组合展示' }}</span>
          </div>
          <p>{{ item.visible }}</p>
          <div><code v-for="path in item.paths" :key="path">{{ path }}</code></div>
        </article>
      </div>

      <div class="section-heading wide workspace-heading"><span>Git / worktree</span><strong>每块内容只有一个来源</strong></div>
      <div class="workspace-table-wrap">
        <table class="workspace-table">
          <thead><tr><th>页面区域</th><th>组合工作区路径</th><th>Git</th><th>分支</th><th>更新规则</th></tr></thead>
          <tbody>
            <tr v-for="row in workspaceRows" :key="row.area" :class="{ local: row.local }">
              <td><strong>{{ row.area }}</strong></td><td><code>{{ row.disk }}</code></td><td>{{ row.git }}</td><td><code>{{ row.branch }}</code></td><td>{{ row.policy }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="blueprint-note">组合工作区只是页面和 Agent 的统一入口，不复制目录，也不改变各 Git 的提交与发布边界。</p>
    </div>

    <div v-else class="blueprint-panel">
      <div class="section-heading wide"><span>建设责任</span><strong>事实生产、测试资产化和发布治理分开</strong></div>
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
        <p><b>01</b><span><strong>开发负责事实源</strong>业务代码、设计和工程文档不在测试目录重复建设。</span></p>
        <p><b>02</b><span><strong>测试负责可测试化</strong>把事实整理成场景、方法、案例、脚本和质量门禁。</span></p>
        <p><b>03</b><span><strong>管理员负责共享发布</strong>共享内容受控发布，spec 始终只保留本地提交。</span></p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.directory-blueprint {
  --ink: #183344;
  --muted: #657986;
  --line: #d8e2e5;
  --paper: #f5f8f7;
  --teal: #247d79;
  --blue: #315f79;
  --amber: #ad6b18;
  color: var(--ink);
  margin: 28px 0 38px;
}

.blueprint-header {
  align-items: end;
  background: linear-gradient(125deg, #edf4f2, #f8f9f6 58%, #eef3f5);
  border: 1px solid var(--line);
  border-radius: 16px 16px 0 0;
  display: grid;
  gap: 28px;
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 28px 30px 24px;
}

.eyebrow,
.section-heading span {
  color: var(--teal);
  font-family: var(--vp-font-family-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .11em;
  margin: 0 0 7px;
  text-transform: uppercase;
}

.blueprint-header h2 {
  border: 0;
  color: var(--ink);
  font-size: clamp(22px, 3vw, 30px);
  letter-spacing: -.035em;
  margin: 0;
  padding: 0;
}

.blueprint-header p:not(.eyebrow) { color: var(--muted); line-height: 1.65; margin: 9px 0 0; max-width: 700px; }
.legend { display: grid; gap: 7px; min-width: 110px; }
.legend span { align-items: center; color: var(--muted); display: flex; font-size: 11px; gap: 8px; }
.legend i { background: var(--blue); border-radius: 50%; height: 8px; width: 8px; }
.legend i.new { background: var(--teal); }
.legend i.local { background: var(--amber); }

.blueprint-tabs { border: 1px solid var(--line); border-top: 0; display: grid; grid-template-columns: repeat(3, 1fr); }
.blueprint-tabs button { background: #fff; border: 0; border-right: 1px solid var(--line); color: var(--muted); cursor: pointer; font-size: 14px; font-weight: 650; padding: 15px 10px; }
.blueprint-tabs button:last-child { border-right: 0; }
.blueprint-tabs button:hover { background: #f7faf9; }
.blueprint-tabs button.active { box-shadow: inset 0 -3px 0 var(--teal); color: var(--ink); }
.blueprint-tabs small { color: var(--teal); font-family: var(--vp-font-family-mono); margin-right: 10px; }

.blueprint-panel { background: #fff; border: 1px solid var(--line); border-radius: 0 0 16px 16px; border-top: 0; padding: 28px 30px 30px; }
.tree-compare { align-items: stretch; display: grid; gap: 18px; grid-template-columns: minmax(0, 1fr) 72px minmax(0, 1fr); }
.tree-compare > section { display: flex; flex-direction: column; min-width: 0; }
.section-heading { margin-bottom: 12px; }
.section-heading strong { color: var(--ink); display: block; font-size: 18px; letter-spacing: -.02em; }
.section-heading.wide { align-items: end; display: flex; justify-content: space-between; }

.tree-box { background: #1b303d; border-radius: 10px; color: #d9e4e7; flex: 1; padding: 14px 12px; }
.tree-box.target { background: #173a3c; }
.tree-box p { align-items: baseline; display: grid; gap: 6px; grid-template-columns: 17px minmax(0, auto) 1fr; line-height: 1.55; margin: 0; padding: 3px 4px 3px calc(4px + var(--depth) * 12px); }
.tree-box b { color: #6f8995; font-family: var(--vp-font-family-mono); font-size: 10px; font-weight: 400; }
.tree-box code { background: transparent; color: inherit; font-size: 10px; font-weight: 650; overflow-wrap: anywhere; padding: 0; }
.tree-box em { color: #8fa6af; font-size: 9px; font-style: normal; }
.tree-box p.new code { color: #91dfd7; }
.tree-box p.local code { color: #f2c781; }
.compare-arrow { align-items: center; align-self: center; display: flex; flex-direction: column; gap: 7px; }
.compare-arrow span { align-items: center; background: #eaf4f1; border: 1px solid #b9d9d4; border-radius: 50%; color: var(--teal); display: flex; font-size: 22px; height: 40px; justify-content: center; width: 40px; }
.compare-arrow b { color: var(--muted); font-size: 10px; font-weight: 600; text-align: center; }

.change-strip { border-top: 1px solid var(--line); display: grid; gap: 16px; grid-template-columns: repeat(4, 1fr); margin-top: 24px; padding-top: 20px; }
.change-strip p { margin: 0; }
.change-strip strong { color: var(--ink); display: block; font-size: 12px; }
.change-strip span { color: var(--muted); display: block; font-size: 10px; line-height: 1.55; margin-top: 3px; }

.mapping-list { border-top: 1px solid var(--line); display: grid; grid-template-columns: repeat(2, 1fr); }
.mapping-list article { border-bottom: 1px solid var(--line); min-width: 0; padding: 16px 18px 16px 0; }
.mapping-list article:nth-child(odd) { border-right: 1px solid var(--line); }
.mapping-list article:nth-child(even) { padding-left: 18px; }
.mapping-title { align-items: center; display: flex; gap: 10px; justify-content: space-between; }
.mapping-title strong { font-size: 14px; }
.mapping-title span { border: 1px solid #b6d8d4; border-radius: 99px; color: var(--teal); font-size: 9px; padding: 2px 6px; }
.mapping-title span.local { border-color: #dfbf8e; color: var(--amber); }
.mapping-list article > p { color: var(--muted); font-size: 11px; line-height: 1.5; margin: 5px 0 9px; }
.mapping-list article > div:last-child { display: flex; flex-wrap: wrap; gap: 5px; }
.mapping-list code { background: var(--paper); color: #345466; font-size: 10px; overflow-wrap: anywhere; padding: 4px 6px; }
.workspace-heading { margin-top: 26px; }
.workspace-table-wrap,
.ownership-wrap { border: 1px solid var(--line); border-radius: 9px; overflow-x: auto; }
.workspace-table,
.ownership-table { border-collapse: collapse; display: table; font-size: 11px; margin: 0; min-width: 850px; width: 100%; }
.workspace-table th,
.ownership-table th { background: #eef4f2; color: #4e6671; font-size: 10px; letter-spacing: .04em; text-align: left; }
.workspace-table th,
.workspace-table td,
.ownership-table th,
.ownership-table td { border: 0; border-bottom: 1px solid var(--line); line-height: 1.5; padding: 10px 11px; vertical-align: top; }
.workspace-table tbody tr:last-child td,
.ownership-table tbody tr:last-child td { border-bottom: 0; }
.workspace-table code,
.ownership-table code { background: transparent; color: var(--blue); font-size: 10px; font-weight: 650; overflow-wrap: anywhere; padding: 0; }
.workspace-table tr.local td { background: #fffaf2; }
.blueprint-note { background: #eef6f3; border-left: 3px solid var(--teal); color: #48636d; font-size: 11px; line-height: 1.6; margin: 16px 0 0; padding: 11px 14px; }

.ownership-table { min-width: 820px; }
.ownership-table td:first-child { min-width: 185px; }
.responsibility-rules { display: grid; gap: 18px; grid-template-columns: repeat(3, 1fr); margin-top: 20px; }
.responsibility-rules p { align-items: start; display: grid; gap: 10px; grid-template-columns: 24px 1fr; margin: 0; }
.responsibility-rules b { align-items: center; background: var(--ink); border-radius: 50%; color: #fff; display: flex; font-family: var(--vp-font-family-mono); font-size: 9px; height: 24px; justify-content: center; }
.responsibility-rules strong { color: var(--ink); display: block; font-size: 11px; }
.responsibility-rules span { color: var(--muted); font-size: 10px; line-height: 1.55; }

@media (max-width: 760px) {
  .blueprint-header { align-items: start; grid-template-columns: 1fr; padding: 22px 18px; }
  .legend { display: flex; flex-wrap: wrap; }
  .blueprint-tabs small { display: none; }
  .blueprint-tabs button { font-size: 12px; padding: 13px 5px; }
  .blueprint-panel { padding: 22px 16px; }
  .tree-compare { grid-template-columns: 1fr; }
  .compare-arrow { flex-direction: row; justify-self: center; }
  .compare-arrow span { height: 32px; width: 32px; }
  .change-strip { grid-template-columns: repeat(2, 1fr); }
  .mapping-list { grid-template-columns: 1fr; }
  .mapping-list article:nth-child(odd) { border-right: 0; }
  .mapping-list article:nth-child(even) { padding-left: 0; }
  .responsibility-rules { grid-template-columns: 1fr; }
  .section-heading.wide { align-items: start; flex-direction: column; }
}
</style>
