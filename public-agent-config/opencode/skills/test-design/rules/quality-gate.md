# 测试设计 Review 质量门禁

本文件是详细审核项的单一来源。测试设计生成只做结构自检；独立 Review 依据本文件判断可交付性，不在 Agent prompt 中复制同一份长清单。

## 1. 规约加载

- 生成结果存在 `policyManifest`，版本与当前 `test-design` skill 一致；
- manifest 包含工作区、输出、对象目录、对象规约索引、实际对象规约、方法选择、A/B、案例组装、追溯和质量门禁；
- 每个实际对象类型都以 `bindingType=PRIMARY` 绑定 `spec-index.md` 指向的主对象规约；有领域加载信号的对象还以 `bindingType=DOMAIN_EXTENSION` 绑定命中的领域附加规约，准确记录 `domainScopes`、逐领域 `loadEvidence` 和去重后的 `expectedRuleIds`，没有信号时未误加载；
- 异步任务、UI 界面、批量任务、接口、业务改造-其它，以及命中大数据领域附加规约的对象，按“对象 + 规约文件”逐条记录启用规则的编号、材料证据、最低覆盖、Phase A 中间物项、案例引用和排除理由；
- 暂缓的混沌、性能、安全和生产安全旧规则卡未被生成或 Review 加载；`specialtySkillDecisions` 同时包含 `chaos-case-generate` 和 `secure-case-recommend` 的选择/排除结果；用户明确请求时强制选中，有具体内容的专项章节或已冻结对象事实中具有独立覆盖价值的安全/容错风险自动选中，空标题、占位文本和孤立泛化词不选中；自动选中项有 `sourceEvidence`、`coverageTargets` 和 Phase A `artifactItemRefs`，`FULL` / `CASES` 在 Phase B 还将实际加载的技术 ID 记入 `methodSkillsRead` 并写回 `caseRefs`；
- 每个选中方法都登记实际方法 skill，未选方法未加载；
- Review 的 `reviewPolicyManifest` 包含质量门禁、方法选择、对象规约索引、实际对象规约、Review 模板和按需 Mermaid 规约；
- 规约缺失、不可读或 manifest 与实际对象/方法不匹配时不得完成。

## 2. 公共规则卡专项审核

- 对每个异步任务、UI 界面、批量任务、接口、业务改造-其它对象及命中的领域附加规约，从 `spec-index.md` 取得规则文件完整性基线和分域规则集合，再核对文件标题中的实际编号及每卡 `适用域`；缺号、重号、未知领域、规则所属域与索引不一致时 `stageStatus=INCOMPLETE`，不得继续给出“通过”；
- 生成阶段每个“对象 + 规约文件”的 `evaluatedRules` 展开 `ruleId` / `ruleIds` 后，编号集合必须与该绑定按 `domainScopes` 计算出的 `expectedRuleIds` 完全一致；不得要求 BDP 对象评估 BDSP 专属规则或反向混用，也不能只登记 `MATCHED`。非命中项只有理由完全相同时才能合并登记，不能用一句“大类不适用”或“领域已覆盖”掩盖逐卡判断；
- Review 必须根据材料独立判断领域附加规约是否应加载、`domainScopes` 是否正确，并在 `loadEvidenceVerdict` 和 `domainScopeVerdict` 中给出结论；应加载而未登记、无加载信号却登记、领域错分、缺少逐领域 `loadEvidence`、`expectedRuleIds` 与索引不符，或用附加规约替代主对象规约，至少记为 `major`；
- Review 根据 `recognizedObjects`、`sourceEvidenceIndex` 和必要的授权材料片段独立复核每张卡的触发条件，不直接采信生成阶段的 `decision`；发现应命中但被标为不适用，或无触发证据却被命中，至少记为 `major`；
- `MATCHED` 必须同时具备可定位的 `sourceEvidence`、规则原文中的 `minimumCoverage`、存在于已冻结 Phase A 的 `artifactItemRefs` 和存在于 Phase B 正式文件中的 `caseRefs`；合并卡内全部适用子检查均须落到中间物和案例，不能只覆盖标题；`caseRefs` 必须能回溯到这些中间物项，最低条数按去重后的实际案例名称计数；
- `MUTUALLY_EXCLUSIVE` 必须指向已命中的对立分支及其材料证据；同一对象同时命中互斥分支至少记为 `major`；
- `NOT_APPLICABLE` 必须说明对象事实为何不满足触发条件；`MISSING_EVIDENCE` 必须对应 `gaps/questions`，不得被当作已覆盖。关键业务预期因缺证据无法判断时 Review 不得给出“通过”；
- Review 在 `reviewPolicyManifest.reviewedObjectRuleSets` 中按“对象 + 规约文件”记录 `ruleFile`、`bindingType`、`domainScopes`、`loadEvidenceVerdict`、`domainScopeVerdict`、`expectedRuleIds`、`evaluatedRuleIds`、复核出的决策差异、`artifactBindingGaps` 和最低覆盖缺口；这些内容只用于内部审核，不写入正式审核文件。

## 3. 工作区与范围

- I 和 S 唯一，未混入其他 S；
- 材料读取受 `sourceManifest` 和 I/S 边界约束；
- `materialsRead` 均可在 `sourceManifest` 中找到授权项，`outOfScope` 未进入事实基线和正式产物；
- Phase A 位于 `<I>/04-测试/<S>/041-测试设计/测试设计文档/`；
- Phase B 和 Review 位于 `<I>/04-测试/<S>/041-测试设计/`；
- 未把 `042-测试执行` 内容当成需求规则，也未向其中写入测试设计产物；
- 正式文件名采用“业务名称-产物类型”，不包含 objectId、UUID、Phase、Stage 或“中间产物”。

## 4. 事实分析基线

- `analysisBaseline` 在方法选择前完成并冻结；
- 对象按可独立验证的业务职责拆分，不把整份文档或整个 S 当成一个对象；
- 每个对象包含范围、入口、输入、输出、规则、状态、依赖、权限、异常、风险、设计信号和证据；
- 每个对象的公共规约匹配有材料证据，未因读取到大类规约而机械套用全部规则；
- 对象关系、材料冲突和未知项有记录；
- 事实基线没有选择方法、生成中间物或案例；
- 关键对象、异常、依赖和风险没有无理由遗漏。

## 5. 方法选择

- 每个对象先有一个匹配主要风险的主方法；
- 辅助方法有独立高风险覆盖目标，或来自用户明确指定；
- 用户指定的方法已纳入，材料不足时有明确缺口；
- 未机械生成全套方法，未选择覆盖目标相同的方法；
- 高相关但未选择的方法有合理说明；
- 没有用文本理解生成或接口格式替代必要结构化方法。

## 6. Phase A 中间物

- 每个选中方法有独立 Phase A 文件；
- 等价类先有等价类表，正交先有因子水平表，路径/场景先有 Mermaid 图，接口先有覆盖矩阵；
- Phase A 只保留图、表或矩阵本体，不包含案例；
- 中间物有材料证据和 `CONFIRMED` / `FROZEN_BY_PIPELINE` 状态；
- manual 模式有用户确认记录，auto 模式有结构校验和冻结记录；
- Mermaid 使用 `11.16.0+` 兼容语法，静态检查和可用时的官方 parser 校验真实有效。

## 7. Phase B 案例组装

- Phase B 输入是已确认/冻结中间物；
- 案例逐项从对应中间物组装；
- 没有在 Phase B 新增、修改或反推中间物；
- 默认非接口案例使用四列、接口案例使用七区块；对象规约的使用方式或最低覆盖明确要求额外案例字段时，正式案例已按规则扩展；
- 每个选中方法有且只有一个对应案例文件，未选方法没有案例文件；
- 正式案例文件不包含内部设计说明、manifest、追溯表或阶段元数据。

## 8. 案例质量

- 案例名称具体且能区分验证目标；
- 步骤可执行，不使用“验证是否正常”；
- 测试数据给出具体值、边界、组合、报文或构造方式；
- 预期结果包含可观察的页面、接口、状态、数据、消息、文件或批量结果；
- 正向、反向、边界、异常、权限、幂等、状态流、重试/补偿、一致性和兼容性与对象风险匹配；
- 每条命中规约的最低案例数量和覆盖点均已满足，不能满足时有明确缺口；
- 互斥规则没有同时生成相互冲突的案例，无法判断互斥分支时保留待确认；
- 无证据的表名、字段、码值或预期标记为 `需确认`。

## 9. 追溯完整性

- 每个方法有 Phase A 文件和 Phase B 案例文件；
- 每个中间物项有案例或未转换原因；
- 每条案例至少映射一个中间物项；
- 每条命中公共规则有 Phase A 中间物项，规则案例没有绕过 Phase A 直接增加；
- Phase B 不存在 Phase A 未定义的覆盖项；
- 关键预期有材料证据或标记 `需确认`。

## 10. 可见交付

- 最终回复只包含测试设计文档、测试案例、案例审核结果；
- 普通案例默认四列、接口案例默认七区块；规则行明确要求的附加字段已完整展示；
- 审核结果不重复完整案例；
- 对象识别、方法理由、内部状态、路径、manifest、冻结证据和追溯映射不作为第四类产物展示。

## 严重程度与结论

- `critical`：跨 I/S、错误目录、明显虚构、关键对象遗漏；
- `major`：方法明显不匹配、跳过 Phase A、A/B 混写或反推、应命中规则漏判、无证据规则误判、互斥分支同时命中、关键最低覆盖缺失、关键方法/案例缺失、追溯断裂、Mermaid 校验失败；
- `minor`：命名、格式、低风险覆盖或非关键待确认问题；
- `通过`：无 critical / major；
- `有条件通过`：只有 minor 或不影响核心覆盖的待确认；
- `不通过`：存在任一 critical 或未解决 major。
