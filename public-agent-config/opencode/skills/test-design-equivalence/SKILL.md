---
name: test-design-equivalence
description: Equivalence Partitioning（等价类法）。支持 phase=artifact 先输出等价类表，或 phase=case 从已有且已确认/冻结的等价类表组装四列测试案例。
compatibility: opencode
metadata:
  display-name: Equivalence Partitioning
  display-name-zh: 等价类法
  source: test-agent
  agent-id: test-design-generation
  version: '3.2.2'
  emoji: 📊
---

# 等价类法

仅由 `test-design-generation` 在事实基线冻结并选中本方法后使用。先加载公共 `test-design` skill。

## phase=artifact：生成等价类表

输入：已冻结事实基线中的对象事实、输入约束和证据。

使用 `templates/equivalence-table.md`，只输出等价类表本体：

```text
| 输入条件 | 等价类 | 类别 |
```

规则：

1. 输入条件只写字段、参数或数据项，不写按钮和操作动作；
2. 每个有效等价类、无效等价类单独一行；
3. 长度、数值、日期等边界作为具体等价类行体现；
4. 枚举值按证据逐值列出；
5. 类别只写“有效”或“无效”；
6. 只写证据支持的约束，未知内容写 `需确认`；
7. Phase A 不输出案例名称、步骤、数据或预期结果。

## phase=case：从等价类表组装案例

输入必须包含现有等价类表及其 `artifactStatus=CONFIRMED/FROZEN_BY_PIPELINE`。没有表时停止并返回 `artifactMismatch`，不得从需求重建表。

规则：

1. 每个有效等价类至少组装一条正向案例；
2. 每个无效等价类至少组装一条反向案例；
3. 关键边界行分别组装案例；
4. 一条案例聚焦一个核心等价类，其余字段使用有效类代表值；
5. 有关联的输入条件补组合案例，但不得新增等价类行；
6. 案例使用公共案例模板，命中规约要求附加字段时按模板扩展；
7. 用 `等价类表文件#R行号 → 案例名称` 建立映射。

完整流水线由 `test-design-generation` 显式执行 `artifact → 冻结/确认 → case`，不得在一个模板中同时生成等价类表和案例。
