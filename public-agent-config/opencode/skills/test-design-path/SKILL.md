---
name: test-design-path
description: Path Testing（路径法）。支持 phase=artifact 先输出 Mermaid 路径图，或 phase=case 从已有且已确认/冻结的图枚举路径并组装四列测试案例；状态迁移也统一按路径法表达。
compatibility: opencode
metadata:
  display-name: Path Testing
  display-name-zh: 路径法
  agent-id: test-design-generation
  version: '3.3.1'
  emoji: 🛤️
---

# 路径法

仅在阶段 2 使用。先加载公共 `test-design` skill。

## phase=artifact：生成路径图

先读取公共 `test-design/rules/mermaid.md`，再使用 `templates/path-graph.md`，只输出 Mermaid 图和必要的节点证据说明。正式图以 Mermaid `11.16.0` 为最低兼容基线。

规则：

- 必须有开始和结束；
- 节点、判定条件、状态和流转来自证据；
- 每条边终止于明确节点；
- 覆盖主流、异常、回退、重试和终态；
- 无法确认的节点/条件标记 `需确认`；
- 动态 label 必须先按公共 Mermaid 规约转义；禁止在 `[]`、`{}`、`()` 或连线 label 中直接嵌入未转义 ASCII 双引号；
- 写入后重新读取图源码并执行 Mermaid 静态检查；运行环境有 `11.16.0` 或更高版本官方 parser 时逐块执行 `mermaid.parse`；
- Phase A 不输出路径枚举表或案例。

## phase=case：从图枚举路径并组装案例

输入必须包含已确认/冻结的 Mermaid 路径图。只有自然语言流程而没有图时停止，返回“请先执行 phase=artifact”。

1. 使用 `templates/path-enumeration-table.md` 枚举从开始到结束的独立路径；
2. 每个判定节点的每个分支至少被一条路径覆盖；
3. 每条路径至少组装一条案例；
4. 测试数据必须驱动流程走到对应终态；
5. 使用公共案例模板，命中规约要求附加字段时按模板扩展；
6. 建立 `PATH-编号 → 案例名称` 映射。

Phase B 不补画、修改或重建路径图。
