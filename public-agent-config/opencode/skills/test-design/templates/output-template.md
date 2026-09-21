# 事实分析内部基线模板

```yaml
analysisBaseline:
  baselineStatus: FROZEN | INCOMPLETE
  workspaceContext:
    workspaceRoot:
    requirementItemDir:
    requirementItemId:
    requirementItemName:
    subItemId:
    canonicalSubItemDirName:
    stageSubItemDirs:
      requirement:
      design:
      code:
      test:
    requirementRoots: []
    designRoot:
    codeRoot:
    testSubItemRoot:
    designOutputRoot:
    designDocumentRoot:
    caseOutputRoot:
    executionOutputRoot:
    resolutionEvidence: []
    resolutionStatus: RESOLVED | AMBIGUOUS | NOT_FOUND
  materialsRead: []
  recognizedObjects: []
  objectRelations: []
  objectTypeSummary: {}
  outOfScope: []
  risks: []
  gaps: []
  questions: []
  sourceEvidenceIndex: []
```

## recognizedObjects 单项结构

```yaml
objectId: OBJ-001
objectName:
objectType:
scope:
businessGoal:
entryOrTrigger:
inputs: []
outputs: []
businessRules: []
applicablePublicRules:
  - ruleFile:
    ruleId:
    triggerEvidence: []
    requiredAnalysis: []
    minimumCoverage: []
    sourceEvidence: []
excludedPublicRules:
  - ruleId:
    decision: NOT_APPLICABLE | MUTUALLY_EXCLUSIVE | MISSING_EVIDENCE
    reason:
    sourceEvidence: []
transactionTypes: []
states: []
dependencies: []
dataObjects: []
permissions: []
exceptions: []
risks: []
designSignals: []
sourceEvidence: []
gaps: []
```

本模板只用于 `test-design-generation` 的内部 `analysisBaseline`，不单独写成正式测试设计文件。基线冻结前不得出现方法选择、中间物或案例。
