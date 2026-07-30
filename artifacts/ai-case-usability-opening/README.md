# AI 案例可用性评估看板开题 PPT

本目录包含面向大学应届新员工实战课题的开题汇报材料。

## 文件

- `AI案例可用性评估看板-开题汇报.pptx`：10 页一周实战版演示文稿。
- `generate-deck.js`：使用 PptxGenJS 生成演示文稿的源码。

## 内容定位

PPT 以当前 MIMO 测试智能体底座为事实基础，重点说明：

1. 当前已具备的应用/工作区、上下文装配、测试分析/案例生成/案例审核、Session/Run/SSE、文件编辑/Diff/Git、Agent & Skill Hub 等能力。
2. 当前缺少的案例可用性评价、问题标签、影响功能、版本关联与质量统计闭环。
3. 案例与开发版本库的“双资产、只读关联”关系：开发侧源码快照是被测事实，测试侧案例版本是质量资产，评价层通过版本身份、代码路径、接口和功能证据连接二者。
4. 最终交付形态不是新 Agent，而是嵌入现有 `agent-web` 的“案例评价页 + 质量看板”，以及配套的评价/统计 API、数据模型、测试和真实演示链路；现有案例生成/审核 Agent 直接复用。
5. 面向新员工的一周必做工作包、周一到周五排期、团队分工和验收方式。

看板中的比例和趋势均明确标注为演示数据，不代表当前生产质量结论。

## 事实依据

- `docs/architecture/module-map.md`
- `docs/api/http-api.md`
- `docs/api/event-stream.md`
- `frontend/apps/user-manual/docs/guide/getting-started.md`
- `frontend/apps/user-manual/docs/guide/directory-mapping.md`
- Codex 本地任务 `019fae77-0b9a-7870-9316-806121c9ca08` 中关于源码快照、开发/测试角色边界和个人测试工作区的分析结论。

当前应用源码快照运行目录按设计删除 `.git`。一周实战只绑定当前冻结快照的 `generation/targetCommit` 和代码证据，不开发版本间 Diff 引擎，也不在只读快照中恢复 Git 元数据。

## 重新生成

在仓库根目录执行：

```bash
NODE_PATH=/Users/kaka/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
  /Users/kaka/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node \
  artifacts/ai-case-usability-opening/generate-deck.js
```
