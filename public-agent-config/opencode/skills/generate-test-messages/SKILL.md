---
name: generate-test-messages
description: API Automation Message Generation（生成接口自动化报文）。根据已评审案例生成 JSON、XML 或文本格式的接口请求报文，并输出 executionMessageFiles 交接块。
compatibility: opencode
metadata:
  display-name: API Automation Message Generation
  display-name-zh: 生成接口自动化报文
  agent-id: test-execution-api
  version: 1.4.0
  emoji: 📨
---

# 生成接口自动化报文

## 输入

已评审 CASEPKG、接口 Markdown 案例或用户指定的报文格式要求。

案例路径可来自用户消息、右键文件、附件、manifest、casePath、sourceFiles 或测试设计阶段 writtenFiles。

## 步骤

1. 加载 `test-execution` Skill 的输出路径规则，并读取案例内容；
2. 按案例逐条生成 JSON、XML 或 text 接口自动化报文；
3. 不新增材料中没有的字段；
4. 加载 `test-execution/rules/output-paths.md`，输出 executionMessageFiles JSON 交接块；targetPath 必须是 .md，用户明确位置优先，没有指定时沿用需求项/子条目上下文写入 `04-测试/042-测试执行/`，不得写入案例同级 `测试执行/`。
5. 默认文件名为 `<案例名称>-接口自动化报文.md`，不得加入 objectId、UUID 或阶段编号。

## 禁止

不要直接写入案例设计文件；除非用户明确指定，接口自动化报文不要写入设计产物目录。

## 通用约束

- 只使用已评审材料；缺字段、码值、表名、状态时写 需确认；
- 只转换已评审的测试设计产物；设计规则以 test-design 的输出为准；
- 不使用空泛名称和空泛预期；
- 输出中写明 resolvedOutputTarget 和 executionMessageFiles。
