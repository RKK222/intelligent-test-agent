---
name: test-design
description: Test Design（测试设计）。用于 I/S 工作区中的测试设计文档、测试案例和独立审核；按用户请求运行所需的最短链路。
compatibility: opencode
metadata:
  display-name: Test Design
  display-name-zh: 测试设计
  agent-id: test-design-orchestrator
  version: '4.8.0'
  emoji: 🧪
---

# 测试设计公共能力包

默认链路只使用两个子 Agent：

```text
test-design-orchestrator
  -> test-design-generation
       -> 事实分析基线
       -> Phase A：方法中间物
       -> 确认/冻结
       -> Phase B：案例组装
  -> test-design-review
```

合并的是对象分析与案例生成的 Agent 调用边界；事实分析、方法选择、Phase A 和 Phase B 仍有独立输入输出契约。

入口只使用一个 `requestedDeliverable` 路由：`FULL` 跑完整链路，`DESIGN` 停在 Phase A，`CASES` 从已有且已确认/冻结的 Phase A 进入 Phase B，`REVIEW` 直接审核已有文件。不得为了判断路由增加 Agent 或 Task。

## 按角色渐进加载

### Test Design 入口

只读取：

- `rules/workspace-layout.md`
- `rules/output-paths.md`

入口解析 I/S、输出目标、`requestedDeliverable`、用户指定方法和确认模式，只传材料 manifest，不预读并内联工作区材料正文。

### Test Design Generation

一次读取并复用：

- `rules/workspace-layout.md`
- `rules/output-paths.md`
- `rules/object-catalog.md`
- `rules/spec-index.md`
- 实际对象类型对应的对象规约
- `rules/spec-index.md` 按材料证据命中的领域附加规约
- `rules/method-selection.md`
- `rules/method-phase-flow.md`
- `rules/case-generation-rules.md`
- `rules/traceability.md`
- `rules/quality-gate.md`
- `templates/output-template.md`
- `templates/artifact-generation-template.md`
- `templates/case-assembly-template.md`
- `templates/case-template.md`
- 实际选中的方法 skill 和模板
- 选中路径法或场景法时读取 `rules/mermaid.md`

不要加载未选方法 skill，也不要在事实分析、Phase A 和 Phase B 之间重复读取同一规约。

`rules/non-functional-chaos.md`、`rules/non-functional-performance.md`、`rules/non-functional-security.md` 和 `rules/production-safety.md` 是暂缓的旧规则卡，当前测试案例设计和 Review 均不得直接加载或匹配。用户明确要求混沌案例或安全案例时，生成 Agent 分别加载公共 `chaos-case-generate`、`secure-case-recommend` Skill；这两个专项能力不读取上述暂缓规则卡，也不在普通测试设计中自动触发。

异步任务、UI 界面、批量任务、接口和业务改造-其它主对象规约，以及按领域信号加载的大数据公共案例附加规约，均位于本 Skill 的 `rules/` 目录。大数据附加规约必须先区分 `COMMON`、`BDP`、`BDSP`、`OUTBOUND`，只评估命中领域的规则集合；不得把 BDP 与 BDSP 专属规则混用。生成 Agent 必须按“适用域 → 触发条件 → 材料证据 → 必须分析 → 最低覆盖 → 排除条件”匹配实际适用的规则卡；不得只按标题套用，也不得为当前大类或领域机械生成全量案例。合并卡命中后必须逐项落实卡内检查点和互斥分支，不能把一张卡误当成一个宽泛案例。

其中 `BIGDATA-01` 对运行型对象规定的四组数据维度必须按 `rules/big-data-common-cases.md` 逐项生成案例，不能因需求文档未提及而省略；字符示例和每个维度的最低案例要求均以该卡为准。仍须先绑定 Phase A，再组装 Phase B，未确认的字段、码值和业务预期不自行补造。

### Test Case Review

只读取独立审核需要的最小集合：

- `rules/quality-gate.md`
- `rules/method-selection.md`
- `rules/spec-index.md`
- 实际对象类型对应的对象规约
- 生成 `policyManifest` 已登记且经材料证据复核应加载的领域附加规约
- `templates/review-output-template.md`
- 存在 Mermaid 中间物时读取 `rules/mermaid.md`

Review 通过生成结果的 `policyManifest` 检查生成阶段是否加载完整，不为证明已读而再次读取全部工作区、输出、A/B、案例和追溯规约。

对异步任务、UI 界面、批量任务、接口、业务改造-其它，以及命中大数据领域附加规约的对象，Review 必须按 `spec-index.md` 的启用规则卡完整性基线独立检查：生成阶段是否按“对象 + 规约文件”评估了主规约完整编号或附加规约 `domainScopes` 对应的 `expectedRuleIds`；每个 `MATCHED` 是否有材料证据、卡内全部适用子检查、最低覆盖、Phase A 中间物项和案例引用；附加规约的加载证据、BDP/BDSP/出湖分域以及互斥、缺证据和不适用决策是否成立。Review 不重新生成案例，也不能只相信生成阶段给出的命中结论。

## 规约清单

测试设计生成结果使用一次 `policyManifest`：

```yaml
policyVersion: test-design/4.8.0
rulesRead: []
objectRuleBindings:
  - objectId:
    ruleFile:
    bindingType: PRIMARY | DOMAIN_EXTENSION
    domainScopes: []
    loadEvidence: []
    expectedRuleIds: []
    evaluatedRules:
      - ruleId:
        decision: MATCHED | NOT_APPLICABLE | MUTUALLY_EXCLUSIVE | MISSING_EVIDENCE
        sourceEvidence: []
        minimumCoverage: []
        artifactItemRefs: []
        caseRefs: []
        reason:
      - ruleIds: []
        decision: NOT_APPLICABLE | MUTUALLY_EXCLUSIVE | MISSING_EVIDENCE
        reason:
methodSkillsRead: []
```

Review 使用独立 manifest：

```yaml
reviewPolicyManifest:
  policyVersion: test-design/4.8.0
  rulesRead: []
  reviewedObjectRuleSets:
    - objectId:
      ruleFile:
      bindingType: PRIMARY | DOMAIN_EXTENSION
      domainScopes: []
      loadEvidenceVerdict:
      domainScopeVerdict:
      expectedRuleIds: []
      evaluatedRuleIds: []
      decisionDifferences: []
      artifactBindingGaps: []
      minimumCoverageGaps: []
```

`objectRuleBindings` 按“对象 + 规约文件”记录规则编号的评估结果；同一对象命中主对象规约和领域附加规约时可出现多项，但每项 `ruleFile` 唯一。主对象规约登记 `bindingType=PRIMARY`，`domainScopes` 为空，`expectedRuleIds` 是主规约完整编号；领域附加规约登记 `bindingType=DOMAIN_EXTENSION`，在 `domainScopes` 中区分 `COMMON`、`BDP`、`BDSP`、`OUTBOUND`，在 `loadEvidence` 中保存逐领域材料锚点，并以 `spec-index.md` 的分域集合计算去重后的 `expectedRuleIds`。每个预期编号的 `decision` 只能是 `MATCHED`、`NOT_APPLICABLE`、`MUTUALLY_EXCLUSIVE` 或 `MISSING_EVIDENCE`；`MATCHED` 必须用单个 `ruleId` 记录材料证据、最低覆盖、Phase A `artifactItemRefs` 和 Phase B `caseRefs`。多个非命中规则仅在判断理由完全相同时可用 `ruleIds` 合并一项，Review 展开后仍须与 `expectedRuleIds` 完全一致。缺少必要规约、附加规约加载或分域证据不足、编号未评估完整、跨域误评估或命中规则未绑定 Phase A 时对应 Agent 返回 `INCOMPLETE`。两种 manifest 都只用于内部编排，不进入正式文件或最终回复。

## 核心不变量

- 一个 workUnit 只绑定一个 I 和一个 S，禁止跨 S 混用材料；
- Phase A 中间物写入 `<I>/04-测试/<S>/041-测试设计/测试设计文档/`；
- Phase B 案例和 Review 写入 `<I>/04-测试/<S>/041-测试设计/`；
- 测试设计产物不得写入 `042-测试执行/`；
- 事实分析基线只记录对象、事实、关系、风险、证据和缺口，不选择方法；
- 用户未指定方法时，每个对象先选一个主方法，仅在有独立覆盖价值时追加辅助方法；
- 用户指定的方法必须纳入，材料不足时明确返回缺口，不静默替换；
- Phase A 不生成案例，Phase B 不新增或修改中间物；
- `DESIGN`、`CASES`、`REVIEW` 只创建用户请求的产物；`REVIEW` 不先补跑生成；
- 等价类、正交、路径、场景和接口方法必须先生成对应表、图或矩阵；
- 路径图和场景图以 Mermaid `11.16.0` 为最低兼容基线；
- 每个选中方法分别生成案例文件，未选方法不生成产物；
- 无证据字段、码值、表名、状态、接口、依赖和预期写 `需确认`；
- 测试设计完成后不自动启动测试执行。

详细目录、方法映射、A/B 转换、案例格式、追溯和审核标准分别以 `rules/` 中对应文件为单一来源。Agent prompt 不重复展开这些规则。

## 可见交付边界

完整流程最终只允许出现：

1. `测试设计文档`：Phase A 图、表或矩阵本体；
2. `测试案例`：普通对象默认四列表、接口对象默认七区块 Markdown；命中规约有额外字段要求时按规则扩展；
3. `案例审核结果`：简洁审核表和最多一行总评。

对象识别、方法理由、阶段状态、文件路径、manifest、冻结证据、追溯映射、JSON/YAML 和下一步建议只用于内部编排。用户只要求其中一种产物时，只创建并输出对应产物。
