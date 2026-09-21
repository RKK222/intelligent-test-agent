# Phase B 内部案例组装交接模板

```yaml
phase: B
phaseStatus: COMPLETED | INCOMPLETE
objectId:
methodSkill:
inputArtifacts: []
artifactFreezeEvidence: []
caseFile:
caseCount:
artifactToCaseMapping: []
specialtyCaseMapping:
  - skillId: chaos-case-generate | secure-case-recommend
    coverageTarget:
    artifactItemRefs: []
    caseRefs: []
artifactMismatch: []
gaps: []
questions: []
```

以上 YAML 和 `artifactToCaseMapping` 只写入内部 `<task_result>`，不写入正式案例文件。

同一对象选择多个方法时，每个方法各生成一条 Phase B 交接记录和一个 `caseFile`。

## 组装前检查

- 所有输入中间物存在；
- 状态为 `CONFIRMED` 或 `FROZEN_BY_PIPELINE`；
- Phase B 不重建或修改中间物；
- 案例覆盖由中间物项驱动。
- 命中公共规则的案例只能来自该规则已绑定的 Phase A 中间物项。
- 安全/混沌专项案例只能来自已冻结决策中的 `coverageTargets` 及其 Phase A `artifactItemRefs`；不得在 Phase B 新增专项覆盖点。

## 非接口案例

默认使用 `case-template.md` 的四列案例表；对象规约的使用方式或最低覆盖指定额外字段时，使用同一模板中的八列表。案例设计说明和追溯映射只保留在内部交接数据中。

## 接口案例

使用 `test-design-api/templates/api-case-template.md`，不转换为四列案例表；对象规约要求的附加案例字段写入“案例信息”区块。
