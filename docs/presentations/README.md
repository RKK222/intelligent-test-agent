# 智能测试技术专题汇报

`智能测试专题汇报（杭州产品部）技术版A7.pptx` 基于既有 A6 原稿保留式改稿：保留封面、目录、测试智能体演进页和结束页，删除推广数据、专班组织、非功能测试与资源规划内容，新增以下技术主题：

- Dify 到灵犀 Code（基于 OpenCode）的底座演进。
- 当前项目的前端、平台后端、Agent Runtime、灵犀 Code 进程、事件与持久化架构。
- 测试设计和测试执行基于统一案例契约的闭环，以及 Agent、Skill 的职责和目录结构。
- 后续 `docs/` 资产目录、治理规则、上下文选择和测试闭环规划。

当前技术版共 9 页；运行链路、工作区目录和执行示例已合并到相邻技术页，不单独占页。

稳定事实依据为 `docs/architecture/module-map.md`、`docs/architecture/dependency-rules.md`、`docs/architecture/domain-models.md`、`backend/README.md`、`frontend/README.md` 与 `frontend/apps/user-manual/docs/guide/agent-config.md`、`workspace.md`。规划内容在页面中显式标注为“规划态”。

重新生成：

```bash
python3 tools/pptx/build-intelligent-testing-tech-deck.py \
  --source "/path/to/智能测试专题汇报（杭州产品部）A6.pptx" \
  --output "docs/presentations/智能测试专题汇报（杭州产品部）技术版A7.pptx"
```
