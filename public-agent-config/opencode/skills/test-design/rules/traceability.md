# 测试设计追溯规则

正式追溯链为：

```text
sourceEvidence
  -> ruleId（命中公共规则卡时）
  -> objectId
  -> methodSkill
  -> phaseAArtifactFile
  -> artifactItemRef
  -> caseFile
  -> caseName
```

## 中间物内部标识

内部 `phaseAArtifactManifest` 至少记录：

- `objectId`
- `methodSkill`
- `phase: A`
- `artifactStatus`: `DRAFT` / `CONFIRMED` / `FROZEN_BY_PIPELINE`
- `sourceEvidence`
- `artifactSnapshot`：文件路径、生成时间/轮次和内容摘要或摘要值

## 中间物项引用

不得为追溯而破坏历史方法模板的核心格式：

- 等价类表保持 `输入条件 / 等价类 / 类别` 三列；引用使用 `文件#表格行号`，如 `登录页-用户名输入-等价类表.md#R3`；
- 因子水平表使用 `文件#R行号`；
- 路径/场景图的 Phase B 枚举项使用 `PATH-001` / `SCN-001`；
- 接口矩阵、规则表、联动映射可以使用自身行号或显式条目编号。

## Phase B 内部映射

每个 generation `<task_result>` 必须有独立映射：

| 规则编号 | 中间物文件 | 中间物项引用 | 方法 skill | 案例文件 | 案例名称 | 覆盖状态 | 未转换原因 |
| --- | --- | --- | --- | --- | --- | --- | --- |

映射用于阶段交接和 Review，不写入正式案例文件，也不在最终回复中展示。

## 完整性规则

- 每个选中方法有 Phase A 文件；
- 每个选中方法有一个按方法命名的案例文件；
- 每个 Phase A 中间物项至少映射一条案例，或写明未转换原因；
- 每条案例至少映射一个中间物项；
- 每条 `MATCHED` 公共规则在 Phase A 冻结前至少绑定一个 `artifactItemRef`，Phase B 的 `caseRefs` 只能来自这些中间物项；
- 关键预期有材料证据或标记 `需确认`；
- Phase B 不得出现 Phase A 中不存在的覆盖项；
- Review 发现孤立方法、孤立中间物项、孤立案例或反向补造时，至少判为 `major`。
