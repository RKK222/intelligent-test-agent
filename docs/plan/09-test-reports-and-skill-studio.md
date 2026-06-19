# Phase 09 测试报告和 Skill Studio

## 阶段目标

完善测试智能体工作台的专业能力：测试报告详情、失败分析、Trace、截图、日志，以及 Python 技能编辑和调试。

## 可验收功能清单

1. 报告列表和报告详情可用。
2. 失败用例可查看错误、日志、截图和 Trace。
3. 报告可从 Agent 对话和测试运行面板跳转。
4. Skill Studio 可编辑 Python 技能。
5. Skill Studio 可配置参数、调试运行并查看结果。

## 修改项目

- `frontend/packages/report-viewer`
- `frontend/packages/skill-studio`
- `frontend/packages/test-runner`
- `frontend/packages/agent-chat`
- `frontend/packages/editor`
- `backend/test-agent-app`
- `backend/test-agent-persistence`
- `docs/api/backend-api.md`
- `docs/frontend/*`

## 实现功能

- 报告详情支持失败聚合、耗时、截图、日志和 Trace。
- 报告中的失败项可定位到文件和 Diff。
- Skill Studio 使用 Monaco 编辑 Python 脚本。
- 技能调试请求走平台后端，结果通过 API 或 RunEvent SSE 返回。
- 技能运行失败使用统一错误格式展示。

## 验收方式

- 前端测试覆盖报告详情、失败分析、截图缺失、日志加载和 Trace 展示。
- Skill Studio 测试覆盖编辑、参数校验、调试运行和错误展示。
- 后端 API 测试覆盖报告读取和技能调试入口。
- 文档同步报告 API、技能 API 和相关 package README。
