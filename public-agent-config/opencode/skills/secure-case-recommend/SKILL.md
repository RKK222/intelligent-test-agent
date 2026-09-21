---
name: secure-case-recommend
description: Secure Case Recommend（安全案例推荐）。由测试设计生成 Agent 在用户明确要求安全案例、安全测试设计或安全案例推荐时加载；根据需求提取关键词、匹配案例库、打分并输出实例化步骤。不处理格式转换，不做安全渗透测试执行。
compatibility: opencode
metadata:
  display-name: Secure Case Recommend
  display-name-zh: 安全案例推荐
  source: test-agent
  version: '1.0.2'
  agent-id: test-design-generation
---

# 安全案例推荐

输入需求子条目及描述，自动提取关键词、匹配案例、打分排序，输出 Top N 安全测试案例，并为每个案例生成针对性的测试实例（场景、步骤、数据、预期）。

## Agent 集成

- owner 为 `test-design-generation`；只有用户明确要求安全案例、安全测试设计或安全案例推荐时加载。
- 不因普通需求材料中出现“权限、认证、敏感”等通用词自动加载，也不替代测试设计主方法。
- 本 Skill 只做案例设计和推荐，不执行漏洞扫描、渗透测试或真实攻击。

## 输入检查

1. 必须提供 `require_sub_item`（需求子条目名称）和 `require_sub_item_describe`（需求子条目描述）。
2. 可选字段：`task_describe`（任务描述）、`content`（功能设计原始数据）、`baseName`（基地名称，默认 `hzbase`）。
3. 不询问用户确认每个关键词；缺失时自动使用兜底策略。

## 执行流程

### Step 1: 理解需求核心

读取输入字段，理解需求的核心功能和技术要点。

### Step 2: L1 正则直匹配

加载 `rules/keyword-matching.md`，读取 L1 正则匹配表。遍历规则，命中则直接添加关键词到 `kws` 集合，标记 `no_further_ask` 的无需后续追问。

### Step 3: L2 主问题判断

加载 `rules/keyword-matching.md`，读取 L2 主问题表。逐一判断需求文本是否命中 `must_match` 特征，命中则添加必选关键词到 `kws`。

### Step 4: L3 子问题追问

加载 `rules/keyword-matching.md`，读取 L3 子问题表。对 Step 3 命中的父场景进一步判断子问题，命中则添加关键词到 `kws`。

### Step 5: 互斥关键词处理

加载 `rules/keyword-matching.md`，读取互斥关键词表。同时出现时保留优先级高的，移除互斥的。

### Step 6: 案例匹配打分

加载 `rules/scoring.md` 获取加减分规则、相似词抵消规则和优先级重排规则。
加载 `references/case-library.md` 获取按基地分类的安全案例库。

1. 用 `kws` 集合匹配案例库中 `关联关键词` 列包含任一关键词的案例。
2. 按加减分规则计算每个案例得分。
3. 应用相似词抵消规则调整得分。
4. 按优先级重排规则排序，同分时高优先级关键词覆盖的案例优先。

### Step 7: 兜底策略

当 `kws` 为空时，推荐兜底通用案例（日志中不应存放敏感信息、本地存储访问控制、报错不应返回版本信息）。

### Step 8: 生成测试实例

加载 `templates/output.md`，为每个推荐案例结合需求上下文生成测试实例，包含 test_scenario、test_steps、test_data、expected_result；结构化 JSON 只保存在 Agent 内部上下文，随后按模板映射为正式四列表案例。

## 输出格式

内部按 `templates/output.md` 形成结构化数组；正式可见文件统一为 Markdown 四列表：`案例名称 / 测试步骤 / 测试数据 / 预期结果`。不得把内部 JSON、关键词集合或评分过程写入正式文件。

## 完成门禁

- 输入必填字段已检查
- 关键词提取已执行（包括 L1/L2/L3 全链路或兜底）
- 案例库按 `baseName` 正确筛选
- 每个推荐案例已生成实例化测试步骤
- 内部结构符合模板，正式文件符合四列表案例格式

## 输出目录

- 一个 workUnit 只绑定一个 I 和一个 S，禁止跨 S 混用材料；
- 正式 Markdown 案例写入 `<I>/04-测试/<S>/041-测试设计/`；内部 JSON 不落盘；
- 测试设计产物不得写入 `042-测试执行/`；
- 测试设计完成后不自动启动测试执行。

## 可见交付边界

完整流程最终只允许出现：

1. `安全测试案例`：将模板中的测试实例映射为四列表 Markdown，保留安全场景、步骤、测试数据和预期安全行为。

对象识别、方法理由、阶段状态、文件路径、manifest、冻结证据、追溯映射、JSON/YAML 和下一步建议只用于内部编排。用户只要求其中一种产物时，只创建并输出对应产物。
