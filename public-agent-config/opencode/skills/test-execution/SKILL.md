---
name: test-execution
description: Test Execution（测试执行）。作为测试执行 Agent 共用的能力包，集中提供请求动作、真实执行证据、断言和输出路径规则；具体脚本、报文由对应 Skill 生成。
compatibility: opencode
metadata:
  display-name: Test Execution
  display-name-zh: 测试执行
  agent-id: test-execution-agent
  version: 1.0.0
  emoji: ▶️
---

# 测试执行公共能力包

本 Skill 是测试执行链路的规则所有者，不是用户入口，也不独立派发 Task 或调用平台 API/DB 工具。

执行 Agent 必须实际读取：

- `rules/api-execution.md`：请求动作、真实执行、证据和状态语义；
- `rules/output-paths.md`：执行产物目录与文件命名。

接口自动化脚本、接口自动化报文和格式校验的具体生成规则仍由各自 Skill 负责，避免把不同产物逻辑集中到公共规则中。
