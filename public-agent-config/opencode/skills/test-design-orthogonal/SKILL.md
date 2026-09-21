---
name: test-design-orthogonal
description: Orthogonal Array（正交法）。支持 phase=artifact 先输出因子水平表，或 phase=case 从已确认/冻结因子水平表生成组合表并组装四列测试案例。
compatibility: opencode
metadata:
  display-name: Orthogonal Array
  display-name-zh: 正交法
  agent-id: test-design-generation
  version: '3.2.1'
  emoji: 🧮
---

# 正交法

仅在阶段 2 使用。先加载公共 `test-design` skill。

## phase=artifact：生成因子水平表

使用 `templates/factor-level-table.md`，只输出：

```text
| 因子 | 水平 |
```

规则：

- 因子来自阶段 1 的条件、角色、配置、开关或外部因素；
- 每个水平单独一行，同一因子重复填写因子名；
- 互斥、不可达或依赖关系在表后单独列证据，不在 Phase A 生成案例；
- 不补造水平或约束。

## phase=case：生成组合并组装案例

输入必须包含已确认/冻结的因子水平表。没有表时停止，不得从需求重新提取因子。

1. 根据因子与水平选择全量判定表或合适正交表；
2. 使用 `templates/orthogonal-combination-table.md` 生成组合；
3. 每个有效组合至少一条案例；
4. 高风险但未被正交表覆盖的组合可补充，并注明来自哪个因子/约束；
5. 使用公共案例模板，命中规约要求附加字段时按模板扩展；
6. 建立 `因子水平表行/组合编号 → 案例名称` 映射。

完整流水线由 `test-design-generation` 显式执行因子水平表 → 冻结/确认 → 组合表 → 案例。
