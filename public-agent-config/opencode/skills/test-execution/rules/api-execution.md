# 接口执行规则

## 请求动作

测试执行入口根据用户意图生成 `requestedActions`，只包含实际要求的动作：

- `GENERATE_SCRIPT`：加载 `generate-api-automation-markdown` 生成接口自动化脚本；
- `GENERATE_MESSAGE`：加载 `generate-test-messages` 生成接口自动化报文；
- `EXECUTE_API`：由 `test-execution-api` 调用平台接口工具真实执行；
- `VERIFY_DB`：由 `test-execution-api` 使用平台数据库工具铺底、校验或清理。

用户只要求脚本或报文时，不得自动追加真实执行。用户要求执行案例时至少加入 `EXECUTE_API`；只有案例含数据库前置/断言或用户明确要求数据库校验时才加入 `VERIFY_DB`。生成脚本和报文也只在用户要求时加入。

## 接口 ID/英文名参考分支

当 `GENERATE_SCRIPT` 请求可从用户原话明确提取 `interfaceIdentity.seasId` 或 `interfaceIdentity.seasName` 时，`test-execution-api` 必须在其它生成步骤之前加载 `resolve-api-automation-references` 并首先调用 TCDS。两个字段均不存在时必须直接沿用存量逻辑：不加载该 Skill、不调用 TCDS/一体化平台、不询问用户，并将参考状态视为 `NOT_APPLICABLE`。

以下约束不可降级：

1. 用户上传 TC Java/Excel 只提高参考数据优先级，不能跳过 TCDS；
2. 仅在已识别接口身份且确实发起 TCDS 后，TCDS 失败且用户未上传 TC 存量文件时，才必须返回 `TCDS_FAILED_CONFIRMATION_REQUIRED`，由入口 Agent 询问用户是否允许无参考案例继续；未识别接口身份时绝不能返回该状态；
3. 未得到用户明确同意前不得生成文件；确认后仍需在本次继续生成前先尝试调用一次 TCDS，失败时才按已确认的无参考路径继续；
4. TCDS 成功但没有可用 NIT、TC 文件不完整或一体化平台部分失败，不触发上述确认，按参考 Skill 的优先级和降级规则继续；
5. 参考解析结果只用于请求、数据准备、Mock 和断言生成，不改变已评审案例范围和预期意图；
6. 该分支只允许写入当前需求项/子条目的 `04-测试/042-测试执行/`。目录外显式路径不得使用。
7. 解析 TC 文件对时必须优先完整提取 Excel 中的报文、数据准备/恢复和断言，再用 Java 确认映射、覆盖顺序和执行路径；不得把 Excel 仅作为字段值附件。
8. NIT 同时存在 TC 和一体化平台脚本时必须执行并完成两类参考解析；不能以优先级、相关性或已有单类成功结果为由跳过另一类。任一类未完成时整体参考阶段为 `INCOMPLETE`，不得生成正式脚本。
9. 数据准备/恢复不得随意简化。全部适用 SQL/table 必须按执行顺序完整生成并二次比对；SQL 长度、行数和复杂度不能成为跳过、截断、概括或改写理由。
10. TC 与一体化平台同时存在时，请求报文、数据准备/恢复和断言必须分别参考两边。非冲突项合并，重复项记录双来源，冲突项按优先级显式处理；不得让任一来源在三个维度被完全放弃，并必须通过来源贡献校验。
11. `test-execution-api` 必须直接完成工作流，禁止再次派发 Task/General/Explore。本工作流所需的读取、写入规定产物目录、外部目录、命令、网络、Skill 和平台 Tool 权限均直接使用，不得申请或等待批准；失败按工具、接口或路径错误返回，不转为权限询问。TCDS 失败后的无参考继续确认属于业务决策，不属于权限申请。
12. TC 文件不在只读引用根目录或直接映射路径时，必须通过平台逻辑引用递归搜索全部后代目录且不限深度。进入递归兜底后，即使中途已找到完整 Java/XLS 对，也必须盘点完所有自动化引用后再配对和选择；不得在首个子目录、首个部分匹配或只找到一种文件时停止。搜索必须记录逻辑引用别名和匹配逻辑路径，禁止扫描或展示服务器物理根路径。
13. `reqParamStruct` 是最终请求报文唯一字段和嵌套结构白名单；其数组中唯一第一层节点默认是结构根节点，不进入最终请求报文，无论名称是什么，报文第一层都必须直接对应其 `children`。普通案例生成后必须检查结构完全一致；案例明确测试“缺少/不传某字段”时，只允许且要求省略从案例原文预先提取的精确 `expectedMissingPaths`，其它字段仍须完整。不存在新增字段例外：案例描述、TC Excel/Java、平台存量案例或其它参考中的“新增/多传/未定义字段”以及偶然多出的字段均不得输出。渲染前校验结构化报文，渲染后必须重新读取正式 Markdown 中的实际请求报文并复验；根节点、任何额外字段、未声明缺失、未生效的缺字段、`null` 或错误嵌套均导致校验失败。
14. 全部中间状态默认只保存在 Agent 上下文，或通过脚本 stdin/stdout 传递；不得为 TCDS/平台响应、参考计划、Excel 解析事实、校验输入/结果、values、manifest、日志或清理登记创建文件。明确禁止 `clean-up.json`、`cleanup.json`，并禁止在 042 内创建 `.reference-work`、`.tmp` 或其它临时目录。Python 使用 `-B`，不得在 Skill 或 042 下生成缓存。
15. 入口 Agent 必须为每次 Task 生成并传入稳定唯一 `runId`，子 Agent 不得改写。只有第三方程序确实强制落盘时，才按该值延迟创建当前 `042-测试执行` 同级 `.tmp/api-automation-<runId>/`；可能隐式使用临时目录的子进程仅在该 run 已创建时以进程级 `TMP/TEMP/TMPDIR=<temporaryRunDirectory>/system-temp` 运行，不修改全局环境。`test-execution-api` 必须用覆盖完整流程的 `try/finally` 在所有出口关闭句柄并只删除本次 run；本次创建的 `.tmp` 根清空后也删除，但不得删除其它并发 run。清理后按执行前基线与正式 `generatedFiles` 运行 `validate_temp_cleanup.py`，入口 Agent 使用相同 `runId` 推导路径并根据内部复验上下文再次校验；任何一层都不得生成清理登记文件、回传已删除临时路径或要求用户手工删除。
16. 正式接口自动化脚本必须通过 `generate-api-automation-markdown` 的确定性 renderer 从当前主模板及全部 partial 整份生成，不能只对齐 5.1/5.2。每个案例的 values、manifest 和 verifier 结果保存在上下文并通过 stdin/stdout 传递；1-5 章标题、固定说明、表头、分隔线、代码块、重复区块和顺序任一不一致即禁止生成。修正只能更新上下文 values 后重渲染，禁止直接修改正式 Markdown。

## 输入

已评审接口案例、接口定义、预置数据、断言规则、`requestedActions` 和是否允许真实执行。

案例和接口定义路径可来自用户消息、右键文件、附件、manifest、`casePath`、`sourceFiles` 或测试设计阶段 `writtenFiles`；只有路径时先读文件。

## 处理规则

1. 读取 `rules/output-paths.md`，解析执行产物目录；
2. 校验方法、地址、headers、body、认证和断言；无法确认时列缺口，不编造或静默补齐；
3. 只处理 `requestedActions` 中列出的生成、接口执行和数据库动作；
4. 平台 API/DB 工具必须由 `test-execution-api` 实际调用；Skill 只定义证据和断言要求；
5. 请求真实执行时逐条记录响应、断言、数据库结果和失败原因；只生成产物时记录 `executionStatus=NOT_REQUESTED`；
6. 进入接口 ID/英文名参考分支后，最终文件禁止出现“待确认”“需确认”、TBD、TODO 或同义占位语。信息不足但有参考时保持最高优先级参考的稳定值；关键结构完全无法恢复时返回 `INCOMPLETE`，不得把不确定性写入正式脚本。

## 真实性与状态

- 只有实际调用平台接口工具 `testing_api_action_run` 后，接口案例才能标为 `EXECUTED`；
- 只有实际调用平台数据库工具 `testing_db_action_run`，或 Task prompt 明确提供可信 DB 结果后，数据库校验才能标为 `VERIFIED`；
- 平台工具不可用、接口或路径失败、环境缺失或请求信息不足时，返回 `NOT_EXECUTED` 或 `BLOCKED` 并说明原因；不得转成权限申请或要求用户授权；
- 脚本、报文、shell、curl、伪代码、自然语言推演和模拟结果都不能作为真实执行证据；
- 只有全部已请求断言通过，案例状态才能写为 `PASSED`。

## 输出

- 请求 `EXECUTE_API` 时，每条案例输出执行状态、请求摘要、关键响应、已请求的响应/数据库断言、失败原因和证据位置；
- 未请求真实执行时，`executionStatus=NOT_REQUESTED`，不生成模拟响应、断言结果或执行证据；
- 生成执行产物时，必须写入 `rules/output-paths.md` 解析出的目标，并返回 `resolvedOutputTarget` 和实际文件清单；
- 参考分支的内部响应、物理路径、脚本排序分值和临时解析目录不得写入正式接口自动化脚本。
- `generatedFiles` 只能包含正式产物；返回完成状态前必须提供 `temporaryWorkspaceCleanup.policy=IN_MEMORY_FIRST`、`residualCheckPassed=true`，且 `residualPaths/unexpectedOutputEntries` 均为空。不得返回已删除临时路径。
