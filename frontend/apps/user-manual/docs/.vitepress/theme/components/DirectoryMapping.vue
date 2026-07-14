<script setup lang="ts">
import { ref } from "vue";

type ViewKey = "mapping" | "implementation" | "ownership";

const activeView = ref<ViewKey>("mapping");

const views: Array<{ key: ViewKey; label: string; eyebrow: string }> = [
  { key: "mapping", label: "开发与测试", eyebrow: "01" },
  { key: "implementation", label: "页面与磁盘", eyebrow: "02" },
  { key: "ownership", label: "内容与责任", eyebrow: "03" }
];

const mappings = [
  {
    source: "ai-agent/agents/",
    sourceNote: "需求、设计、编码、测试阶段智能体",
    target: ".opencode/agents/ + skills/",
    targetNote: "OpenCode 原生发现的应用 Agent 与可复用 Skill"
  },
  {
    source: "rules/ · template/ · eval.md",
    sourceNote: "规约、模板、输出质量门禁",
    target: "rules/ + skills/<name>/",
    targetNote: "应用规则与 Skill 内 rules、templates、evals 资源"
  },
  {
    source: "业务资料 · 功能设计 · 架构设计",
    sourceNote: "研发侧稳定事实和设计依据",
    target: "docs/{业务,功能,架构}/",
    targetNote: "经应用团队治理、可跨需求复用的测试资产"
  },
  {
    source: "当前需求 · 设计 · 代码 · 测试",
    sourceNote: "只服务于本次需求或版本的工作内容",
    target: "spec/<需求项>/{01-需求…04-测试}/",
    targetNote: "个人本地 worktree 提交，任何角色都不推送远端"
  },
  {
    source: "mcp/ · 模型 · Provider",
    sourceNote: "所有用户进程共享的运行配置",
    target: "公共 opencode.jsonc",
    targetNote: "由平台统一管理，不复制到应用 AI Git"
  }
];

const ownershipRows = [
  {
    path: "公共 agents/、skills/、opencode.jsonc",
    content: "通用测试 Agent、通用方法、模型、Provider、MCP 与平台安全边界",
    builder: "AI 平台/测试效能团队",
    publisher: "平台超级管理员"
  },
  {
    path: "AGENTS.md、.opencode/、rules/",
    content: "应用专属 Agent、Skill、执行约束、产物规范和质量门禁",
    builder: "应用测试架构师、测试负责人",
    publisher: "应用管理员及以上"
  },
  {
    path: "docs/业务/",
    content: "领域术语、业务流程、业务规则、产品口径、角色和权限语义",
    builder: "产品经理、业务分析师、领域专家",
    publisher: "应用管理员审核发布"
  },
  {
    path: "docs/功能/",
    content: "需求基线、功能清单、接口契约、页面流程、数据字典与异常口径",
    builder: "产品/需求人员、开发负责人、测试设计人员",
    publisher: "应用管理员审核发布"
  },
  {
    path: "docs/架构/",
    content: "系统与模块架构、依赖关系、部署拓扑、数据流、技术约束和非功能基线",
    builder: "架构师、开发负责人、运维/安全专家",
    publisher: "应用管理员审核发布"
  },
  {
    path: "spec/<需求项>/",
    content: "本次需求、设计、代码引用、案例、脚本、执行证据和结论",
    builder: "当前工作用户与其 Agent",
    publisher: "只做本地提交，禁止远程推送"
  }
];
</script>

<template>
  <section class="directory-map" aria-label="开发与测试目录映射设计">
    <header class="directory-map__header">
      <div>
        <p class="directory-map__kicker">DIRECTORY BLUEPRINT · 目录蓝图</p>
        <h2>一套页面视图，背后是四类受控工作区</h2>
        <p>
          页面使用业务语言组织内容；磁盘继续按 Git、分支和 worktree 隔离。公共能力、应用资产和当前需求不会混成一个仓库。
        </p>
      </div>
      <div class="directory-map__legend" aria-label="状态图例">
        <span><i class="is-current"></i>当前已实现</span>
        <span><i class="is-target"></i>目标设计</span>
        <span><i class="is-local"></i>仅本地</span>
      </div>
    </header>

    <nav class="directory-map__tabs" aria-label="目录设计视图">
      <button
        v-for="view in views"
        :key="view.key"
        type="button"
        :class="{ 'is-active': activeView === view.key }"
        :aria-pressed="activeView === view.key"
        @click="activeView = view.key"
      >
        <small>{{ view.eyebrow }}</small>
        <span>{{ view.label }}</span>
      </button>
    </nav>

    <div v-if="activeView === 'mapping'" class="directory-map__panel">
      <div class="directory-map__column-headings" aria-hidden="true">
        <span>研发 / SDD 表达</span>
        <span>映射原则</span>
        <span>测试工作台目标结构</span>
      </div>
      <div class="directory-map__mapping-list">
        <div v-for="item in mappings" :key="item.source" class="directory-map__mapping-row">
          <div class="directory-map__path-block">
            <code>{{ item.source }}</code>
            <span>{{ item.sourceNote }}</span>
          </div>
          <div class="directory-map__arrow" aria-hidden="true">
            <span></span><b>→</b>
          </div>
          <div class="directory-map__path-block is-target">
            <code>{{ item.target }}</code>
            <span>{{ item.targetNote }}</span>
          </div>
        </div>
      </div>
      <p class="directory-map__decision">
        <strong>判断标准：</strong>跨需求长期有效的内容进入 <code>docs</code>；只属于当前需求的内容进入 <code>spec</code>；改变 Agent 行为的内容进入
        <code>.opencode</code> 或 <code>rules</code>。
      </p>
    </div>

    <div v-else-if="activeView === 'implementation'" class="directory-map__panel">
      <div class="directory-map__implementation-grid">
        <section class="directory-map__tree" aria-label="页面展示目录">
          <div class="directory-map__section-label">页面展示</div>
          <h3>用户只看见四个业务入口</h3>
          <div class="directory-map__tree-lines">
            <p><span>▾</span><strong>公共能力</strong><em>平台统一</em></p>
            <p class="depth-1"><span>├</span>Agents</p>
            <p class="depth-1"><span>└</span>Skills</p>
            <p><span>▾</span><strong>应用能力</strong><em>应用共享</em></p>
            <p class="depth-1"><span>├</span>Agents</p>
            <p class="depth-1"><span>├</span>Skills</p>
            <p class="depth-1"><span>└</span>Rules</p>
            <p><span>▾</span><strong>测试资产</strong><em>应用共享</em></p>
            <p class="depth-1"><span>├</span>业务</p>
            <p class="depth-1"><span>├</span>功能</p>
            <p class="depth-1"><span>└</span>架构</p>
            <p><span>▾</span><strong>当前工作</strong><em class="is-local">仅本地</em></p>
            <p class="depth-1"><span>└</span>&lt;需求项&gt; / 01-需求 … 04-测试</p>
          </div>
        </section>

        <section class="directory-map__paths" aria-label="实际磁盘、Git 与分支">
          <div class="directory-map__section-label">技术实现</div>
          <h3>每个入口映射到明确的 Git worktree</h3>
          <p class="directory-map__variables">
            <code>R=${SYS_DATA_ROOT_DIR}/agent-opencode</code>，路径中的应用、用户和工作区均使用平台稳定 ID。
          </p>

          <article>
            <span class="directory-map__status is-current">当前</span>
            <h4>公共能力</h4>
            <code>R/.config/opencode/{agents,skills}</code>
            <dl><dt>Git</dt><dd>公共 Agent Git</dd><dt>分支</dt><dd>平台发布分支</dd></dl>
          </article>
          <article>
            <span class="directory-map__status is-current">当前</span>
            <h4>应用能力 + 当前研发文件</h4>
            <code>R/workspace/personalworktree/{version}/{userId}/{repo}/{personalBranch}/…</code>
            <dl><dt>Git</dt><dd>业务开发 Git</dd><dt>分支</dt><dd>个人业务分支；应用配置位于 <code>.opencode/</code></dd></dl>
          </article>
          <article>
            <span class="directory-map__status is-target">目标</span>
            <h4>应用能力 + docs 测试资产</h4>
            <code>R/workspace/application-ai/{appId}/runtime/{commit}/</code>
            <dl><dt>Git</dt><dd>应用 AI Git</dd><dt>分支</dt><dd>共享发布提交（其他用户默认更新）</dd></dl>
          </article>
          <article>
            <span class="directory-map__status is-target">目标</span>
            <h4>应用能力编辑</h4>
            <code>R/workspace/application-ai/{appId}/edit/{userId}/{worktreeId}/</code>
            <dl><dt>Git</dt><dd>应用 AI Git</dd><dt>分支</dt><dd><code>edit/app/{appId}/{worktreeId}</code>，管理员发布后合并</dd></dl>
          </article>
          <article>
            <span class="directory-map__status is-local">目标 · 本地</span>
            <h4>当前工作 spec</h4>
            <code>R/workspace/application-ai/{appId}/spec/{userId}/{workspaceId}/spec/</code>
            <dl><dt>Git</dt><dd>应用 AI Git 的本地 worktree</dd><dt>分支</dt><dd><code>local/spec/{appId}/{userId}/{workspaceId}</code>，无远端跟踪</dd></dl>
          </article>
        </section>
      </div>
      <p class="directory-map__projection">
        OpenCode 进程仍按“一人一进程”运行。组装层把公共配置、应用 AI 运行版本、业务个人 worktree 和本地 spec 投影成一个工作视图；投影不产生第三套 Git 数据。
      </p>
    </div>

    <div v-else class="directory-map__panel">
      <div class="directory-map__responsibility-head">
        <div>
          <div class="directory-map__section-label">建设责任</div>
          <h3>内容生产者与发布者分离</h3>
        </div>
        <p>领域专家负责事实，测试团队负责可测试化整理，管理员负责发布治理。</p>
      </div>
      <div class="directory-map__table-wrap">
        <table>
          <thead>
            <tr><th>目录</th><th>放什么</th><th>主要建设者</th><th>发布责任</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in ownershipRows" :key="row.path">
              <td><code>{{ row.path }}</code></td>
              <td>{{ row.content }}</td>
              <td>{{ row.builder }}</td>
              <td>{{ row.publisher }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="directory-map__rules">
        <p><span>1</span><strong>开发团队提供事实源</strong>不把测试目录变成研发文档的无差别镜像。</p>
        <p><span>2</span><strong>测试团队完成资产化</strong>补齐可测试口径、适用范围、来源和更新时间。</p>
        <p><span>3</span><strong>管理员控制共享发布</strong><code>spec</code> 无论用户角色都只能本地提交。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.directory-map {
  --map-ink: #173041;
  --map-muted: #607483;
  --map-line: #d8e1e5;
  --map-paper: #f6f8f7;
  --map-teal: #247a78;
  --map-blue: #315b75;
  --map-amber: #a66618;
  color: var(--map-ink);
  margin: 28px 0 36px;
}

.directory-map__header {
  align-items: end;
  background: linear-gradient(130deg, #edf3f2 0%, #f7f8f5 58%, #eef2f5 100%);
  border: 1px solid var(--map-line);
  border-radius: 16px 16px 0 0;
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 28px 30px 24px;
}

.directory-map__kicker,
.directory-map__section-label {
  color: var(--map-teal);
  font-family: var(--vp-font-family-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.11em;
  margin: 0 0 8px;
}

.directory-map__header h2,
.directory-map__panel h3 {
  border: 0;
  color: var(--map-ink);
  margin: 0;
  padding: 0;
}

.directory-map__header h2 {
  font-size: clamp(22px, 3vw, 30px);
  letter-spacing: -0.035em;
}

.directory-map__header p:not(.directory-map__kicker) {
  color: var(--map-muted);
  line-height: 1.7;
  margin: 10px 0 0;
  max-width: 660px;
}

.directory-map__legend {
  display: grid;
  gap: 8px;
  min-width: 118px;
}

.directory-map__legend span {
  align-items: center;
  color: var(--map-muted);
  display: flex;
  font-size: 12px;
  gap: 8px;
}

.directory-map__legend i {
  border-radius: 50%;
  height: 8px;
  width: 8px;
}

.directory-map__legend .is-current { background: var(--map-blue); }
.directory-map__legend .is-target { background: var(--map-teal); }
.directory-map__legend .is-local { background: var(--map-amber); }

.directory-map__tabs {
  background: #fff;
  border: 1px solid var(--map-line);
  border-top: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
}

.directory-map__tabs button {
  align-items: center;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--map-line);
  color: var(--map-muted);
  cursor: pointer;
  display: flex;
  gap: 12px;
  justify-content: center;
  padding: 15px 12px;
}

.directory-map__tabs button:last-child { border-right: 0; }
.directory-map__tabs button:hover { background: #f5f8f7; }
.directory-map__tabs button.is-active { box-shadow: inset 0 -3px 0 var(--map-teal); color: var(--map-ink); }
.directory-map__tabs small { color: var(--map-teal); font-family: var(--vp-font-family-mono); }
.directory-map__tabs span { font-size: 14px; font-weight: 650; }

.directory-map__panel {
  background: #fff;
  border: 1px solid var(--map-line);
  border-radius: 0 0 16px 16px;
  border-top: 0;
  padding: 28px 30px 30px;
}

.directory-map__column-headings,
.directory-map__mapping-row {
  display: grid;
  gap: 20px;
  grid-template-columns: minmax(0, 1fr) 72px minmax(0, 1fr);
}

.directory-map__column-headings {
  color: var(--map-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  padding: 0 15px 10px;
  text-transform: uppercase;
}

.directory-map__column-headings span:nth-child(2) { text-align: center; }

.directory-map__mapping-row {
  align-items: stretch;
  border-top: 1px solid var(--map-line);
  padding: 13px 0;
}

.directory-map__path-block {
  background: var(--map-paper);
  border-left: 3px solid #8da0aa;
  border-radius: 6px;
  padding: 13px 15px;
}

.directory-map__path-block.is-target { border-left-color: var(--map-teal); }
.directory-map__path-block code { background: transparent; color: var(--map-ink); font-size: 13px; font-weight: 700; padding: 0; }
.directory-map__path-block span { color: var(--map-muted); display: block; font-size: 12px; line-height: 1.55; margin-top: 5px; }

.directory-map__arrow { align-items: center; display: flex; position: relative; }
.directory-map__arrow span { background: var(--map-line); height: 1px; width: 100%; }
.directory-map__arrow b { background: #fff; color: var(--map-teal); font-size: 20px; left: 50%; padding: 0 5px; position: absolute; transform: translateX(-50%); }

.directory-map__decision,
.directory-map__projection {
  background: #eef5f3;
  border-left: 3px solid var(--map-teal);
  color: #3f5d66;
  font-size: 13px;
  line-height: 1.7;
  margin: 18px 0 0;
  padding: 13px 16px;
}

.directory-map__implementation-grid {
  display: grid;
  gap: 32px;
  grid-template-columns: minmax(250px, 0.75fr) minmax(0, 1.5fr);
}

.directory-map__panel h3 { font-size: 20px; letter-spacing: -0.02em; }

.directory-map__tree-lines {
  background: #172b37;
  border-radius: 10px;
  box-shadow: 0 14px 30px rgb(23 48 65 / 12%);
  color: #dbe6e7;
  font-family: var(--vp-font-family-mono);
  margin-top: 18px;
  padding: 16px 18px;
}

.directory-map__tree-lines p { align-items: center; display: flex; font-size: 12px; gap: 8px; line-height: 1.75; margin: 0; }
.directory-map__tree-lines p + p:not(.depth-1) { margin-top: 8px; }
.directory-map__tree-lines p.depth-1 { color: #adbdc3; padding-left: 15px; }
.directory-map__tree-lines strong { color: #fff; }
.directory-map__tree-lines em { background: rgb(94 178 172 / 18%); border-radius: 99px; color: #9dddd7; font-size: 9px; font-style: normal; margin-left: auto; padding: 1px 7px; }
.directory-map__tree-lines em.is-local { background: rgb(226 167 84 / 18%); color: #f0c584; }

.directory-map__variables { color: var(--map-muted); font-size: 12px; line-height: 1.6; margin: 10px 0 16px; }
.directory-map__variables code { word-break: break-all; }

.directory-map__paths article {
  border-top: 1px solid var(--map-line);
  display: grid;
  gap: 6px 12px;
  grid-template-columns: auto 1fr;
  padding: 14px 0;
}

.directory-map__paths article > code,
.directory-map__paths article > dl { grid-column: 2; }
.directory-map__paths article > code { background: #f2f5f5; color: #294759; font-size: 11px; line-height: 1.6; overflow-wrap: anywhere; padding: 5px 8px; }
.directory-map__paths h4 { color: var(--map-ink); font-size: 14px; margin: 0; }

.directory-map__status {
  align-self: start;
  border: 1px solid currentColor;
  border-radius: 99px;
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
  margin-top: 2px;
  padding: 4px 6px;
}

.directory-map__status.is-current { color: var(--map-blue); }
.directory-map__status.is-target { color: var(--map-teal); }
.directory-map__status.is-local { color: var(--map-amber); }

.directory-map__paths dl { display: grid; font-size: 11px; gap: 3px 8px; grid-template-columns: 30px 1fr; margin: 0; }
.directory-map__paths dt { color: var(--map-muted); font-weight: 500; }
.directory-map__paths dd { color: #435e6c; margin: 0; }
.directory-map__paths dd code { font-size: 10px; }

.directory-map__responsibility-head { align-items: end; display: flex; gap: 28px; justify-content: space-between; margin-bottom: 18px; }
.directory-map__responsibility-head p { color: var(--map-muted); font-size: 12px; line-height: 1.6; margin: 0; max-width: 310px; }

.directory-map__table-wrap { border: 1px solid var(--map-line); border-radius: 10px; overflow-x: auto; }
.directory-map table { border-collapse: collapse; display: table; font-size: 12px; margin: 0; min-width: 760px; width: 100%; }
.directory-map th { background: #eef3f2; color: #49616d; font-size: 11px; letter-spacing: 0.04em; text-align: left; }
.directory-map th,
.directory-map td { border: 0; border-bottom: 1px solid var(--map-line); line-height: 1.55; padding: 11px 12px; vertical-align: top; }
.directory-map tbody tr:last-child td { border-bottom: 0; }
.directory-map tbody tr:hover { background: #f8faf9; }
.directory-map td:first-child { min-width: 155px; }
.directory-map td code { background: transparent; color: var(--map-blue); font-size: 11px; font-weight: 700; padding: 0; }

.directory-map__rules { display: grid; gap: 12px; grid-template-columns: repeat(3, 1fr); margin-top: 18px; }
.directory-map__rules p { color: var(--map-muted); font-size: 11px; line-height: 1.6; margin: 0; padding-left: 34px; position: relative; }
.directory-map__rules span { align-items: center; background: var(--map-ink); border-radius: 50%; color: #fff; display: flex; font-family: var(--vp-font-family-mono); font-size: 10px; height: 22px; justify-content: center; left: 0; position: absolute; top: 0; width: 22px; }
.directory-map__rules strong { color: var(--map-ink); display: block; font-size: 12px; }

@media (max-width: 760px) {
  .directory-map__header { align-items: start; grid-template-columns: 1fr; padding: 22px 18px; }
  .directory-map__legend { display: flex; flex-wrap: wrap; }
  .directory-map__tabs button { gap: 6px; padding: 13px 6px; }
  .directory-map__tabs small { display: none; }
  .directory-map__panel { padding: 22px 16px; }
  .directory-map__column-headings { display: none; }
  .directory-map__mapping-row { gap: 8px; grid-template-columns: 1fr; padding: 16px 0; }
  .directory-map__arrow { height: 18px; justify-content: center; transform: rotate(90deg); }
  .directory-map__arrow span { width: 48px; }
  .directory-map__implementation-grid { grid-template-columns: 1fr; }
  .directory-map__responsibility-head { align-items: start; flex-direction: column; }
  .directory-map__rules { grid-template-columns: 1fr; }
}
</style>
