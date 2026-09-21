# Agent 与 skill 映射

## 当前主链路

| 阶段 | Agent | 核心产物 |
| --- | --- | --- |
| 1. 事实分析、方法设计与案例组装 | `test-design-generation` | 先冻结对象事实基线，再生成 Phase A 中间物和 Phase B 案例 |
| 2. 案例 Review | `test-design-review` | 评审结论、问题和评审报告 |

`test-design-analysis` 仅作为历史兼容的隐藏内部 Agent 保留，当前 `test-design-orchestrator` 不单独调用。合并的是 Agent 调用边界；事实基线冻结、方法选择、Phase A 和 Phase B 的语义仍严格分开。

## 生成 Agent 内部流程

```text
事实分析基线冻结
  -> Phase A 方法选择与中间物生成
  -> Phase A 中间物确认/冻结
  -> Phase B 基于中间物组装案例
```

生成 Agent 内的 Phase A 和 Phase B 必须使用不同输入/输出契约。不能合成一个“边做表边写案例”的模板。

## 方法 skill 的双模式

| 方法 | skill | `phase=artifact` | `phase=case` |
| --- | --- | --- | --- |
| 生成接口测试案例 | `test-design-api` | 接口覆盖矩阵 | 接口七区块案例 |
| 等价类 | `test-design-equivalence` | 等价类表 | 从表逐类组装案例 |
| 正交 | `test-design-orthogonal` | 因子水平表 | 正交/判定组合 + 案例 |
| 路径 | `test-design-path` | Mermaid 路径图 | 路径枚举 + 案例 |
| 场景 | `test-design-scenario` | Mermaid 场景图 | 场景枚举 + 案例 |
| 文本理解生成 | `test-design-direct` | 规则与验证点表 | 从规则点组装案例 |
| UI/API 联动 | `test-design-augment` `mode=ui-api` | 联动映射 | 联动案例 |
| 存量增补 | `test-design-augment` `mode=augment-flow` | 增补映射 | 增补既有案例 |

每个方法 skill 可独立调用 Phase A 或 Phase B。完整流水线中，generation 只调用自主选中或用户明确指定的方法，并必须显式执行 A → 冻结/确认 → B；未选中的方法不调用。

## 专项案例 Skill

| 专项能力 | owner Agent | Skill | 执行边界 |
| --- | --- | --- | --- |
| 混沌案例、故障注入案例、混沌测试设计 | `test-design-generation` | `chaos-case-generate` | 只生成专项案例与注入步骤，不执行故障注入 |
| 安全案例、安全测试设计、安全案例推荐 | `test-design-generation` | `secure-case-recommend` | 只生成专项案例，不执行扫描或渗透测试 |

专项 Skill 在用户明确提出对应意图时强制选中；在需求/详细设计含有具体内容的专项章节，或生成 Agent 根据已冻结对象事实判断存在具有独立覆盖价值的安全或容错风险时自动选中。决策记入 `policyManifest.specialtySkillDecisions`，Phase B 实际加载后把技术 ID 记入 `methodSkillsRead`。它们不替代主方法，也不读取暂缓的 `non-functional-chaos.md`、`non-functional-security.md`。
