# Agent 与 skill 映射

## 三阶段主链路

| 阶段 | Agent | 核心产物 |
| --- | --- | --- |
| 1. 对象分析和识别 | `test-design-analysis` | 对象事实、关系、风险、证据和缺口；不含方法 |
| 2. 方法设计与案例组装 | `test-design-generation` | Phase A 中间物 + Phase B 案例 |
| 3. 案例 Review | `test-design-review` | 评审结论、问题和评审报告 |

## 阶段 2 内部流程

```text
2A 方法选择与中间物生成
  -> 2A 中间物确认/冻结
  -> 2B 基于中间物组装案例
```

阶段 2A 和 2B 属于同一个顶层阶段，但必须使用不同输入/输出契约。不能合成一个“边做表边写案例”的模板。

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

| 用户明确意图 | owner Agent | Skill | 执行边界 |
| --- | --- | --- | --- |
| 混沌案例、故障注入案例、混沌测试设计 | `test-design-generation` | `chaos-case-generate` | 只生成专项案例与注入步骤，不执行故障注入 |
| 安全案例、安全测试设计、安全案例推荐 | `test-design-generation` | `secure-case-recommend` | 只生成专项案例，不执行扫描或渗透测试 |

专项 Skill 只有在用户明确提出对应意图时才加载，并记录到 `policyManifest.methodSkillsRead`。它们不替代主方法，也不读取暂缓的 `non-functional-chaos.md`、`non-functional-security.md`。
