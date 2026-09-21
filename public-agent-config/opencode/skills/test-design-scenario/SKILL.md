---
name: test-design-scenario
description: Scenario Testing（场景法）。支持 phase=artifact 先输出 Mermaid 场景图，或 phase=case 从已有且已确认/冻结的场景图枚举场景并组装四列测试案例。
compatibility: opencode
metadata:
  display-name: Scenario Testing
  display-name-zh: 场景法
  agent-id: test-design-generation
  version: '3.2.1'
  emoji: 🎬
---

# 场景法

仅在阶段 2 使用。先加载公共 `test-design` skill。

## phase=artifact：生成场景图

先读取公共 `test-design/rules/mermaid.md`，再使用 `templates/scenario-graph.md`，只输出场景链路图。正式图以 Mermaid `11.16.0` 为最低兼容基线。

图中体现：触发、前置、主流程、下游、异常、超时、重试、补偿、数据一致性和终态。未知内容标记 `需确认`。Phase A 不输出场景枚举或案例。

动态 label 必须先按公共 Mermaid 规约转义，禁止直接嵌入未转义 ASCII 双引号。写入后重新读取图源码并执行 Mermaid 静态检查；运行环境有 `11.16.0` 或更高版本官方 parser 时逐块执行 `mermaid.parse`。

## phase=case：从图枚举场景并组装案例

输入必须包含已确认/冻结的 Mermaid 场景图。没有图时停止，不得从需求重新构造场景。

1. 使用 `templates/scenario-enumeration-table.md` 枚举主场景、异常场景、重试/补偿场景和最终失败场景；
2. 每个场景至少一条案例；
3. 数据和预期覆盖跨组件交互、状态、消息/批量结果和一致性；
4. 使用公共案例模板，命中规约要求附加字段时按模板扩展；
5. 建立 `SCN-编号 → 案例名称` 映射。

Phase B 不修改场景图。
