---
name: test-design-augment
description: Case Augmentation（增补法）。phase=artifact 先生成 UI/API 联动映射或基线增补映射，phase=case 再从已确认/冻结映射组装或增补案例。
compatibility: opencode
metadata:
  display-name: Case Augmentation
  display-name-zh: 增补法
  agent-id: test-design-generation
  version: '3.3.1'
  emoji: 🧩
---

# 增补法

仅在阶段 2 使用。先加载公共 `test-design` skill。

## mode=ui-api

### phase=artifact

使用 `templates/ui-api-mapping.md`，输出页面动作 → 接口 → 状态 → 数据映射，不输出案例。

### phase=case

从已确认/冻结映射逐项组装联动案例。没有映射时停止。使用公共案例模板（命中规约要求附加字段时扩展），并建立映射行到案例的关系。

## mode=augment-flow

仅当用户明确要求增补既有案例并保持原编号、顺序和结构时使用。

### phase=artifact

使用 `templates/augment-mapping.md`，输出基线案例到新增字段、规则、状态或断言的嵌入位置。

### phase=case

从已确认/冻结增补映射更新既有案例；不得自行增加、删除或重排案例。输出修改后的完整案例，并保留基线。

本 skill 只覆盖联动或增补关系，不自动触发等价类、正交、路径或场景方法；是否追加其他方法由公共 `method-selection.md` 按独立高风险覆盖或用户明确指定来决定。
