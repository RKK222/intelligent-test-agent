---
name: test-design-direct
description: Text Understanding Generation（文本理解生成）。从需求或设计文本中的明确单点规则和技术差异生成规则验证表，再组装四列测试案例；不作为材料不足的兜底。
compatibility: opencode
metadata:
  display-name: Text Understanding Generation
  display-name-zh: 文本理解生成
  agent-id: test-design-generation
  version: '3.3.1'
  emoji: ✍️
---

# 文本理解生成

仅在阶段 2 使用。先加载公共 `test-design` skill。

仅用于简单单点规则或技术差异，且不存在更合适的输入域、组合、路径或场景方法。

## phase=artifact

使用 `templates/rule-table.md` 输出规则、输入/触发、可验证结果、风险和证据。Phase A 不输出案例。

## phase=case

输入必须包含已确认/冻结规则表。每个可验证规则点至少组装一条案例，使用公共案例模板（命中规约要求附加字段时扩展），并建立 `规则表行 → 案例名称` 映射。

本方法不是材料不足时的兜底。无法确认的规则写 `需确认`，不得把猜测写成规则。
