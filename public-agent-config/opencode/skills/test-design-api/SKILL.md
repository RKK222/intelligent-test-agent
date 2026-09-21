---
name: test-design-api
description: API Test Case Generation（生成接口测试案例）。根据接口契约先生成覆盖矩阵，再从已确认或冻结的矩阵组装七区块接口测试案例。
compatibility: opencode
metadata:
  display-name: API Test Case Generation
  display-name-zh: 生成接口测试案例
  source: test-agent
  agent-id: test-design-generation
  version: '3.4.2'
  emoji: 🔌
---

# 生成接口测试案例

仅由 `test-design-generation` 在事实基线冻结并选中本方法后使用。先加载公共 `test-design` skill。

## phase=artifact：接口覆盖矩阵

使用 `templates/api-coverage-matrix.md`，根据已冻结事实基线中的接口契约生成覆盖矩阵。只输出接口契约摘要和矩阵，不输出测试案例。

矩阵关注：

- 方法/协议、路径/地址；
- Header、Query、Path、Body；
- 正常返回、业务码、错误码；
- 幂等及材料明确的业务前置；
- 状态变化、内部数据、配置、下游和数据库落点；
- 超时、重试、降级和兼容性。

未证实内容写 `需确认`。

当前不得从暂缓的混沌、性能、安全和生产安全规约补充矩阵覆盖项；材料中的普通业务角色或流程前置仍按功能事实处理。

## phase=case：从矩阵组装接口案例

输入必须包含已确认/冻结的接口覆盖矩阵。没有矩阵时停止，不得直接从接口文档生成案例。

1. 每个矩阵覆盖项至少映射一条案例或说明未转换原因；
2. 使用 `templates/api-case-template.md`；
3. 保持接口专用区块，不转换为四列案例表；
4. 缺少表名、字段、参数键、状态或下游依据时写 `需确认`；
5. 建立 `接口矩阵行 → 案例名称` 映射。

第 5 项映射只放入内部 `<task_result>.artifactToCaseMapping`，不写入正式接口案例文件。正式案例文件不得增加案例设计说明、来源覆盖项、证据列或映射表。

接口字段有输入域、条件组合、状态流或端到端链路时，由公共 `method-selection.md` 判断接口覆盖矩阵是否已经覆盖主要风险。只有其他方法能补充独立高风险覆盖，或用户明确指定时，才追加等价类、正交、路径或场景 skill；不得默认全部追加。
