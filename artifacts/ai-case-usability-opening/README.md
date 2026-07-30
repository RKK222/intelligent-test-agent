# 面向开发变更的测试资产质量评估与优化开题 PPT

本目录包含面向大学应届新员工实战课题的开题汇报材料。

## 文件

- `面向开发变更的测试资产质量评估与优化-开题汇报.pptx`：9 页一周实战版演示文稿。
- `generate-deck.js`：使用 PptxGenJS 生成演示文稿的源码。

## 内容定位

PPT 以当前 MIMO 测试智能体底座为事实基础，重点说明：

1. 当前已具备的应用/工作区、上下文装配、测试分析/案例生成/案例审核、Session/Run/SSE、文件编辑/Diff/Git、Agent & Skill Hub 等能力。
2. 当前缺少的测试资产可用性评估、开发变更响应判断与资产优化能力。
3. 开发变更与测试资产的“变更—资产—处置”关系：代码变更且关联资产未新增、修改或人工确认时，生成需要关注的风险信号，不直接判定为资产缺陷。
4. 最终交付为 1 个 `Test Asset Quality Optimizer` 主 Agent 和 4 个可复用 Skill：变更影响分析、资产变更关联、测试资产质量评估、测试资产优化。
5. 产物为质量评估报告、优化后测试案例和处理记录；全部写入现有测试工作区，不新建看板、网页、专用 API 或数据模型。
6. 面向新员工的一周必做工作包、周一到周五排期、团队分工和真实运行验收方式。

“可用”的验收标准是：Agent/Skills 可被平台发现和调用，能在真实模型与工作区中运行，结论可追溯到代码证据，优化资产可落盘和人工复核，并通过 30 组样例验证。

## 事实依据

- `docs/architecture/module-map.md`
- `docs/api/http-api.md`
- `docs/api/event-stream.md`
- `frontend/apps/user-manual/docs/guide/getting-started.md`
- `frontend/apps/user-manual/docs/guide/directory-mapping.md`
- Codex 本地任务 `019fae77-0b9a-7870-9316-806121c9ca08` 中关于源码快照、开发/测试角色边界和个人测试工作区的分析结论。

当前应用源码快照运行目录按设计删除 `.git`。一周实战使用前后快照的 `generation/targetCommit`、代码证据和已有或导入的变更文件清单，不开发完整版本 Diff 引擎，也不在只读快照中恢复 Git 元数据。

## 重新生成

在仓库根目录执行：

```bash
NODE_PATH=/Users/kaka/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
  /Users/kaka/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node \
  artifacts/ai-case-usability-opening/generate-deck.js
```
