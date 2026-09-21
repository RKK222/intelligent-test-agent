# 存量案例合流规则

本规则只在输入包含有效 `stockCaseContext` 时加载。存量案例是 Phase B 的案例来源，不是测试对象、需求事实或测试设计方法。

## 1. 证据优先级

当前工作单元的事实口径按以下顺序确定：

1. 当前 I/S 的需求与详细设计；
2. 明确适用于当前 I/S 的工作区 `docs` 资产和代码等补充实现证据；
3. TCDS 存量案例。

低层证据不得覆盖高层证据。存量案例与当前需求、详细设计冲突时，以当前需求和详细设计为准；缺少当前设计证据时，不得因为历史案例存在就补造规则、状态、字段、码值或预期。

## 2. 材料有效性

Generation 只读取 `stockCaseContext.materialFile` 指向的一个确切文件，并同时校验：

- 文件位于当前 `workspaceContext.workspaceRoot/docs/` 内；
- `sourceManifest` 中存在 `materialType=STOCK_CASE_MATERIAL` 的同路径条目，且其来源系统、参考目录、案例数和摘要与上下文一致；
- 条目的 I/S 与当前 workUnit 一致；
- 文件元数据中的来源为 TCDS，子条目、参考目录和案例数与 `stockCaseContext` 一致；
- 文件元数据中的 `materialSnapshot` 与上下文一致，并能对应本次请求和规范化案例列表；
- 七列字段完整，案例 ID 唯一。

任一项不一致时返回 `INCOMPLETE`，不得改读同目录其他存量案例文件，也不得重新查询 TCDS。

`workspaceRoot/docs/` 下其他资产只有被 `sourceManifest` 授权，且其元数据或可定位证据明确适用于当前 S 或属于当前 I/S 可共享资产时才可读取。不得因目录同名或语义相似混入其他 S 的资产。

## 3. 与 Phase A 解耦

- 事实分析、公共规则匹配、方法选择和 Phase A 只由当前需求、详细设计及获准的实现证据驱动；
- 不读取存量案例来识别对象、选择方法、增加或删除 Phase A 覆盖项；
- 先完成并确认/冻结全部 Phase A，再读取存量案例材料；
- 存量材料在 `materialsRead` 中标记 `readPhase=PHASE_B`，不得进入冻结的 `analysisBaseline.materialsRead` 或事实 `sourceEvidenceIndex`；
- 存量案例无论多少，都不能降低对象规约最低覆盖，也不能让缺少 Phase A 的案例直接进入正式文件。

这保证“测什么”由当前设计决定，“已有哪条案例可以承载”只在案例组装阶段决定。

## 4. Phase B 匹配与决策

对每条冻结的 `artifactItemRef`，按当前对象、业务动作、前置状态、输入数据、步骤和可观察预期与存量候选进行确定性匹配，并记录证据。每个候选只能得到以下一种决策：

- `REUSE_AS_IS`：覆盖目标一致，且名称、步骤、数据和预期均被当前设计支持，可直接组装；
- `ADAPT`：覆盖目标一致，但至少一个字段需要按当前设计调整；必须逐项记录调整前后差异和当前设计证据；
- `REJECT`：不属于当前对象或覆盖目标、与当前设计冲突、关键信息无法确认，或与更合适候选重复；
- 未被 `REUSE_AS_IS` 或 `ADAPT` 覆盖的 Phase A 项使用 `NEW` 组装新案例。

禁止只按案例名称或词项重合判断复用。禁止为了提高复用率降低断言、删减边界/异常覆盖，或把多个预期不同的存量案例合并成模糊案例。

TCDS 的 `isUpdate` 和 `aiAiCase` 仅作为来源元数据保留；除非另有明确字段契约，不得据此推断案例新旧、质量、是否可直接复用或优先级。

## 5. 组装和去重

- 最终案例仍写入其 Phase A 所属方法的标准案例文件；不得创建“存量案例推荐”“TCDS 案例”或其他额外方法文件；
- `REUSE_AS_IS` 只允许 Markdown 转义和输出模板格式转换，不得改写语义；
- `ADAPT` 的最终值必须来自当前设计或明确标记 `需确认`，不得回写 TCDS；
- 一个最终案例可以覆盖多个 Phase A 项，但必须有一个唯一的案例名称和所属方法文件；
- 同一个存量案例不得仅因多个方法存在而复制成语义相同的多个最终案例；选择覆盖关系最直接的所属方法，其余中间物项映射到该案例；
- 先落盘 `REUSE_AS_IS` / `ADAPT`，再为未覆盖项生成 `NEW`，最后对名称、步骤、数据和预期进行语义去重；
- 空存量列表或全部 `REJECT` 是有效情况，继续按冻结 Phase A 生成 `NEW` 案例。

## 6. 内部追溯

`stockCaseReuseManifest` 至少记录：

```yaml
materialFile:
materialSnapshot:
referencePath:
candidateCount:
decisions:
  - sourceCaseId:
    decision: REUSE_AS_IS | ADAPT | REJECT
    artifactItemRefs: []
    currentDesignEvidence: []
    fieldChanges: []
    reason:
uncoveredArtifactItemRefs: []
```

`caseAssemblyManifest` 和 `artifactToCaseMapping` 中的每条最终案例还要记录：

```yaml
caseSource: STOCK_REUSED | STOCK_ADAPTED | NEW
sourceCaseIds: []
adaptations: []
```

这些字段只用于内部交接和 Review，不写入正式案例文件或最终回复。存量来源不能替代既有的 `ruleId -> artifactItemRef -> caseName` 追溯链。
