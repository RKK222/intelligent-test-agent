# 智能测试技术专题汇报

`智能测试专题汇报（杭州产品部）技术逻辑版A8.pptx` 基于既有 A6 原稿保留式改稿：保留封面、测试智能体演进页和结束页，删除推广数据、专班组织、非功能测试与资源规划内容，并把目录改为四个连续问题。全篇沿用一条因果主线：

- 为什么 Dify 的固定流程难以承接长程、变化多的测试任务。
- 灵犀 Code（基于 OpenCode）如何让 Agent 在真实工作区规划并执行，当前四层底座分别承担什么职责。
- 一项测试任务如何从详细设计经过统一案例契约，进入 API/UI 执行并输出证据；Agent、Skill、Tool、Docs 在其中分别扮演什么角色。
- 执行结果如何经复核进入 `docs/`，稳定方法如何进入 `.opencode/`，并被下一次任务复用。

当前逻辑版共 9 页；A7 技术版保留用于对照，A8 重点优化汇报顺序和每页结论，不增加页数。

稳定事实依据为 `docs/architecture/module-map.md`、`docs/architecture/dependency-rules.md`、`docs/architecture/domain-models.md`、`backend/README.md`、`frontend/README.md` 与 `frontend/apps/user-manual/docs/guide/agent-config.md`、`workspace.md`。规划内容在页面中显式标注为“规划态”。

重新生成：

```bash
python3 tools/pptx/build-intelligent-testing-tech-deck.py \
  --source "/path/to/智能测试专题汇报（杭州产品部）A6.pptx" \
  --output "docs/presentations/智能测试专题汇报（杭州产品部）技术逻辑版A8.pptx"
```
