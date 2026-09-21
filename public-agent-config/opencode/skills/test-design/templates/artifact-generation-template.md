# Phase A 内部交接模板

```yaml
phase: A
phaseStatus: COMPLETED | INCOMPLETE | WAITING_FOR_ARTIFACT_CONFIRMATION
objectId:
methodSkill:
artifactType:
artifactFile:
artifactStatus: DRAFT | CONFIRMED | FROZEN_BY_PIPELINE
sourceEvidence: []
artifactSnapshot:
  file:
  section:
  contentSummary:
  freezeEvidence:
mermaidValidation:
  syntaxBaseline: ">=11.16.0"
  staticCheck: PASSED | FAILED | NOT_APPLICABLE
  parserCheck: PASSED | FAILED | UNAVAILABLE | NOT_APPLICABLE
  failureLocation:
gaps: []
questions: []
specialtyCoverageBindings:
  - skillId: chaos-case-generate | secure-case-recommend
    coverageTarget:
    artifactItemRefs: []
```

以上 YAML 只写入内部 `<task_result>.phaseAArtifactManifest`，不写入正式测试设计文档。

## 正式文件

正式 Phase A 文件只输出方法中间物本体，例如等价类表、Mermaid 路径图、场景图、因子水平表或接口覆盖矩阵。

`mermaidValidation` 只用于路径图和场景图；非 Mermaid 中间物使用 `NOT_APPLICABLE`。parser 不可用时必须记录 `UNAVAILABLE`，不得伪造 `PASSED`。

Phase A 不输出测试案例。

命中启用公共规则时，`policyManifest.objectRuleBindings` 必须把每个 `ruleId` 绑定到正式中间物中的实际行、节点、路径或矩阵项。选中安全/混沌专项时，`specialtyCoverageBindings` 必须把每个 `coverageTarget` 绑定到同类可定位中间物项；未选中时不生成虚假绑定。
