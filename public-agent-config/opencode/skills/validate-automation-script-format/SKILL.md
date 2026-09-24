---
name: validate-automation-script-format
description: Automation Format Check（自动化格式检查）。用于检查接口自动化脚本整份内容是否由当前模板组确定性渲染，并检查文件命名、语义完整性；参考分支额外检查占位词、请求结构和字段长度。
compatibility: opencode
metadata:
  display-name: Automation Format Check
  display-name-zh: 自动化格式检查
  agent-id: test-execution-api
  version: 2.3.1
  source: test-agent
  emoji: ✅
---

# 自动化脚本格式检查

## 基础检查

1. 文件名是否符合 `<案例名称>-接口自动化脚本.md`，不得重新拼接序号、测试类型或交易类型。
2. 是否完整执行 `generate-api-automation-markdown/scripts/verify_rendered_script.py`，且当前主模板、全部 partial、values 和正式输出的哈希与 manifest 一致。
3. 以 `generate-api-automation-markdown/templates/api-script-template.md` 及 `templates/partials/*.md` 为唯一格式基准；整份产物从文档开头到末尾必须由渲染器生成，1-5 章标题、固定说明、表格列、分隔线、代码块和重复区块都与当前模板组逐字节一致，不能只检查 5.1/5.2。
4. 案例信息是否包含测试类型、交易类型、测试要点、设计方法、覆盖维度。
5. 断言是否包含返回报文断言；需要时包含数据库断言。
6. 请求报文是否完整，不得使用“同上”“类似案例”“其余字段”或省略号代替字段。

## 接口参考分支检查

调用方提供 `referenceContext` 时，额外检查：

1. 文件中不包含“待确认”“需确认”“未确认”或常见中英文占位标记；
2. `referenceContext.canonicalReqParamStruct` 非空时，已执行 `resolve-api-automation-references/scripts/validate_generated_payload.py` 且结果通过；
3. `reqParamStruct` 是最终请求报文唯一字段和嵌套结构白名单；其唯一第一层结构根节点名称不得出现在最终报文中，报文第一层必须直接对应其 `children`。普通案例请求结构没有任何缺失、多余或 `null` 字段，空字符串字段保留为 `""`；明确缺字段案例只允许且要求省略 `expectedMissingPaths` 中由案例原文预先映射的精确字段，其它结构仍须完整。不存在新增字段例外；无论额外字段来自案例描述、TC Excel/Java、平台存量案例还是其它参考，均必须拒绝；
4. 结构校验必须针对重新读取的正式 Markdown 中实际请求报文执行，不能只校验渲染前草稿或 values；该报文必须与 renderer 校验过的 `requestPayload` 一致，并通过同一 `canonicalReqParamStruct`/`expectedMissingPaths` 校验；
5. 长度类案例包含显式长度复核结果，实际值长度符合案例要求；
6. 输出案例集合与输入已评审案例一一对应。
7. 已执行 `resolve-api-automation-references/scripts/validate_data_preparations.py` 且结果通过；不存在数据准备时两个列表均为空。所有 SQL/table 准备和恢复均完整、顺序一致，不含截断、省略或摘要。
8. TC 与一体化平台同时存在时，已执行 `validate_reference_contributions.py` 且结果通过；请求、数据准备和断言中凡来源有内容均有具体贡献或冲突处理证据，不能完全放弃任一来源。
9. 所有校验输入和结果均保存在调用方上下文并通过 stdin/stdout 传递，不生成校验 JSON 或清理登记文件。调用方结束前已使用执行前基线、正式 `generatedFiles` 和可选的 sibling `.tmp/api-automation-<runId>` 运行 `validate_temp_cleanup.py`，确认 042 内不存在 `.reference-work`、`.tmp`、`clean-up.json`、`cleanup.json`，本次 run 已删除且 `residualPaths/unexpectedOutputEntries` 均为空。

## 输出

返回通过/失败表；格式或内容失败时更新上下文中的 values，重新运行 renderer 和 verifier 后再校验，禁止直接编辑正式 Markdown。若调用方提供 `targetPath/outputPath`，重渲染结果写回该路径。参考分支任一二次校验未通过时不得返回成功。
