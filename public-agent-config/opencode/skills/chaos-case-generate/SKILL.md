---
name: chaos-case-generate
description: Chaos Case Generate（混沌案例生成）。由测试设计生成 Agent 在用户明确要求混沌案例、故障注入案例或混沌测试设计时加载；根据需求提取关键词、匹配案例库并输出实例化注入步骤。不处理格式转换，不做真实混沌工程执行。
compatibility: opencode
metadata:
  display-name: Chaos Case Generate
  display-name-zh: 混沌案例生成
  source: test-agent
  version: '1.0.1'
  agent-id: test-design-generation
---

# 混沌案例生成

输入需求子条目及描述，自动提取故障类型关键词、匹配混沌案例库，输出混沌工程测试案例，并为每个案例生成针对性的注入实例（故障场景、注入步骤、预期系统行为）。

## Agent 集成

- owner 为 `test-design-generation`；只有用户明确要求混沌测试设计、混沌案例或故障注入案例时加载。
- 不因普通需求材料中出现“异常、失败、重试”等通用词自动加载，也不替代测试设计主方法。
- 本 Skill 只生成设计案例，不调用真实故障注入或测试执行能力。

## 输入检查

1. 必须提供 `require_sub_item`（需求子条目名称）和 `require_sub_item_describe`（需求子条目描述）。
2. 可选字段：`task_describe`（任务描述）、`content`（功能设计原始数据）、`systemType`（系统类型，默认 `distributed`）。
3. 不询问用户确认每个关键词；缺失时自动使用兜底策略。

## 执行流程

### Step 1: 理解需求核心

读取输入字段，理解需求的核心功能、技术架构和关键依赖。

### Step 2: L1 正则值匹配

加载 `rules/keyword-matching.md`，读取 L1 正则匹配表。遍历规则，命中则直接添加故障类型关键词到 `kws` 集合，标记 `no_further_ask` 的无需后续追问。

### Step 3: L2 主问题判断

加载 `rules/keyword-matching.md`，读取 L2 主问题表。逐一判断需求文本是否命中 `must_match` 特征，命中则添加必选关键词到 `kws`。

### Step 4: L3 子问题追问

加载 `rules/keyword-matching.md`，读取 L3 子问题表。对 Step 3 命中的父场景进一步判断子问题，命中则添加关键词到 `kws`。

### Step 5: 互斥关键词处理

加载 `rules/keyword-matching.md`，读取互斥关键词表。同时出现时保留优先级高的，移除互斥的。

### Step 6: 案例匹配

加载 `references/chaos-case-library.md` 获取按系统类型分类的混沌案例库。

1. 用 `kws` 集合匹配案例库中 `关联故障类型` 列包含任一关键词的案例。

### Step 7: 兜底策略

当 `kws` 为空时，推荐兜底通用混沌案例（CPU 满负载注入、网络延迟注入、磁盘空间耗尽、进程 Kill 等基础故障场景）。

### Step 8: 生成注入实例

加载 `templates/output.md`，为每个推荐案例结合需求上下文生成注入实例，包含 fault_scenario、injection_steps、monitoring_checkpoints、expected_system_behavior；结构化 JSON 只保存在 Agent 内部上下文，随后按模板映射为正式四列表案例。

## 输出格式

内部按 `templates/output.md` 形成结构化数组；正式可见文件统一为 Markdown 四列表：`案例名称 / 测试步骤 / 测试数据 / 预期结果`。不得把内部 JSON、关键词集合或评分过程写入正式文件。

## 完成门禁

- 输入必填字段已检查
- 关键词提取已执行（包括 L1/L2/L3 全链路或兜底）
- 案例库按 `systemType` 正确筛选
- 每个推荐案例已生成实例化注入步骤
- 内部结构符合模板，正式文件符合四列表案例格式

## 输出目录

- 一个 workUnit 只绑定一个 I 和一个 S，禁止跨 S 混用材料；
- 正式 Markdown 案例写入 `<I>/04-测试/<S>/041-测试设计/`；内部 JSON 不落盘；
- 测试设计产物不得写入 `042-测试执行/`；
- 测试设计完成后不自动启动测试执行。

## 可见交付边界

完整流程最终只允许出现：

1. `混沌测试案例`：将模板中的注入实例映射为四列表 Markdown，保留故障场景、注入步骤、监控检查点和预期系统行为。

对象识别、方法理由、阶段状态、文件路径、manifest、冻结证据、追溯映射、JSON/YAML 和下一步建议只用于内部编排。用户只要求其中一种产物时，只创建并输出对应产物。
