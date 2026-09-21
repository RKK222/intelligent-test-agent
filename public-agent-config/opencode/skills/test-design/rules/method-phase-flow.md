# 阶段 2 的 Phase A / Phase B 流程

阶段 2 不是“边分析边写案例”。每个已选中的结构化测试方法都必须先产出中间物，再从已冻结的中间物组装案例；未选方法不生成中间物或案例。

```text
方法选择
  -> Phase A：生成方法中间物
  -> 中间物校验与冻结/确认
  -> Phase B：只基于中间物组装案例
```

## Phase A：中间物生成

Phase A 的输入是阶段 1 对象事实、`designSignals` 和材料证据。输出只包含当前方法的中间物本体，不输出测试案例。

| 方法 skill | Phase A 产物 |
| --- | --- |
| `test-design-api` | 接口覆盖矩阵 |
| `test-design-equivalence` | 等价类表 |
| `test-design-orthogonal` | 因子水平表 |
| `test-design-path` | Mermaid 路径图 |
| `test-design-scenario` | Mermaid 场景图 |
| `test-design-direct` | 规则与验证点表 |
| `test-design-augment` | UI/API 联动映射或存量增补映射 |

Phase A 必须直接保留图、表或矩阵本体；不得先生成案例，再反推中间物。

命中启用公共规则卡时，Phase A 必须把规则覆盖点落到一个或多个实际中间物项，并在内部对应单条 `ruleId` 的 `objectRuleBindings.evaluatedRules[].artifactItemRefs` 中记录引用。`MATCHED` 不得使用批量 `ruleIds`，缺少引用不得冻结。

路径图和场景图必须额外读取 `rules/mermaid.md`，以 Mermaid `11.16.0` 为最低兼容基线。静态检查失败或官方 parser 校验失败的 Mermaid 中间物不得进入确认/冻结；parser 不可用时必须如实记录 `UNAVAILABLE`。

## 中间物确认模式

`artifactApprovalMode` 有两种：

- `auto`：默认模式。Phase A 完成后执行结构、证据和完整性校验，生成不可变快照并标记 `FROZEN_BY_PIPELINE`，随后进入 Phase B；
- `manual`：当用户明确要求“先看中间物”“确认后再出案例”时使用。写完 Phase A 后返回 `WAITING_FOR_ARTIFACT_CONFIRMATION`，不生成案例、不进入 Review。用户确认后，后续调用从已确认中间物继续 Phase B。

自动模式不是“跳过 Phase A”。Phase B 必须接收 Phase A 的完整快照、文件路径和冻结证据。

## Phase B：案例组装

Phase B 的主要输入必须是现有的 Phase A 中间物。可读取阶段 1 对象事实和证据来补充步骤、具体数据和可验证预期，但不得：

- 新增 Phase A 中没有的等价类、因子、路径、场景、接口覆盖项或规则点；
- 修改、重画或替换已冻结中间物；
- 直接根据原始需求重新设计案例；
- 在没有中间物时自行进入 Phase B。

发现中间物缺失或错误时，返回 `artifactMismatch`，重新执行 Phase A；不要在 Phase B 静默修补。

## 方法调用

每个方法 skill 都支持：

- `phase=artifact`：只执行 Phase A；
- `phase=case`：只执行 Phase B，输入必须包含已有且已确认/冻结的中间物；
测试设计完整流水线必须由 `test-design-generation` 显式先调用 `phase=artifact`，完成冻结/确认后再调用 `phase=case`；不得用一个隐式 full 模式绕过两阶段交接。用户直接提供已确认中间物时可使用 `phase=case`。
