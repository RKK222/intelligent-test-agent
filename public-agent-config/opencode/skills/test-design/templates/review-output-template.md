# 案例审核结果

正式评审文件只输出下面审核表和最多一行总评，不重复完整案例，不输出基本信息、文件清单、问题明细、修订计划或阶段交接 YAML。

| 审核项 | 审核结果 | 说明 |
| --- | --- | --- |
| 需求/设计覆盖 | 通过/有条件通过/不通过 | {{coverageEvidenceOrIssue}} |
| 公共规约命中、最低覆盖与排除 | 通过/有条件通过/不通过/不适用 | {{publicRuleCoverageEvidenceOrIssue}} |
| 方法中间物与案例一致性 | 通过/有条件通过/不通过 | {{artifactCaseEvidenceOrIssue}} |
| 正常、异常、边界和关键分支 | 通过/有条件通过/不通过 | {{scenarioEvidenceOrIssue}} |
| 测试数据具体性 | 通过/有条件通过/不通过 | {{testDataEvidenceOrIssue}} |
| 步骤可执行性 | 通过/有条件通过/不通过 | {{stepsEvidenceOrIssue}} |
| 预期结果可观察性 | 通过/有条件通过/不通过 | {{expectedResultEvidenceOrIssue}} |
| 案例模板与规约字段完整性 | 通过/有条件通过/不通过/不适用 | {{caseTemplateEvidenceOrIssue}} |

总评：通过 / 有条件通过 / 不通过。

详细问题定位、修订建议、剩余问题、文件路径和阶段判定只返回在内部 `<task_result>`，不写入正式评审文件。
