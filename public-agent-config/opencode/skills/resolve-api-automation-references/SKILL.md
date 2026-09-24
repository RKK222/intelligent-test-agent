---
name: resolve-api-automation-references
description: API Automation Reference Resolution（接口自动化参考解析）。当生成接口自动化脚本且用户给出接口编号或英文名时，先查询 TCDS，筛选和排序 NIT TC/一体化平台脚本，读取只读自动化库或用户上传文件，并形成请求、数据准备和断言参考上下文。没有接口身份时不使用。
compatibility: opencode
metadata:
  display-name: API Automation Reference Resolution
  display-name-zh: 接口自动化参考解析
  agent-id: test-execution-api
  version: 2.2.0
  source: test-agent
  emoji: 🔎
---

# 接口自动化参考解析

本 Skill 只形成供 `generate-api-automation-markdown` 消费的内部 `referenceContext`，不生成正式 Markdown、不写转换报告，也不改变案例范围。正式文件仍由生成 Skill 写入 `042-测试执行`。

## 使用条件

仅当 Task prompt 的 `interfaceIdentity.seasId` 或 `interfaceIdentity.seasName` 非空且 `requestedActions` 包含 `GENERATE_SCRIPT` 时使用。两者均为空、缺失或未识别时必须立即返回 `referenceResolutionStatus=NOT_APPLICABLE`，不得调用任何 TCDS/一体化平台接口、不得返回失败确认状态，让调用方走原有存量生成逻辑；调用方不得因该状态询问用户。

执行前读取：

- `references/api-contracts.md`：两个接口的精确请求和响应契约；
- `references/selection-and-priority.md`：NIT 过滤、TC/平台分类、排序、文件查找和来源优先级；
- 发现 TC Java/Excel 时读取 `references/tc-parsing.md`；
- 生成前读取 `references/payload-and-case-rules.md`。

## 强制流程

1. **第一项外部动作调用 TCDS。** 使用公共 `http_call` Tool，禁止把 shell/curl 当成平台 API/DB 真实执行证据。用户上传 TC 文件也不能跳过本调用。
2. TCDS 原始 JSON 响应保存在 Agent 上下文，通过 stdin 交给排序脚本；排序结果只从 stdout 读取并保存为上下文中的 `referenceContext`，不得生成 `tcds-response.json`、`reference-plan.json` 或清理登记文件。运行：

   ```text
   <上下文中的TCDS响应JSON> | python -B <本Skill目录>/scripts/rank_reference_scripts.py \
     --input - \
     --identifier <seasId或seasName> \
     --design-file <已评审案例Markdown> \
     --workspace-value <工作空间元数据版本或路径>
   ```

   有多个案例文件时重复传 `--design-file`。`--workspace-value` 按应用工作空间元数据、工作空间路径、已评审材料的优先级从高到低重复传入。脚本只做确定性过滤、分类、聚合、排序和路径候选计算，不访问网络或磁盘库。只有后续第三方 Excel 工具确实强制落盘时，才由调用方延迟创建 `042-测试执行` 同级 `.tmp/api-automation-<runId>/`；本 Skill 不得自行在 042、当前目录、系统临时目录或 Skill 目录创建临时文件。
3. 先盘点用户上传的 TC Java/Excel。完整上传对优先级最高；部分上传只与同一 TC 身份的只读库文件补齐，不得混入其它 TC。
4. 对排序后的 TC 组逐个查找，默认只选一个完整组。每个候选先尝试第一段映射出的直接逻辑路径；直接路径没有同时找到 `xxxTC.java` 和至少一个 `xxxTC_*.xls` 时，必须使用计划中的 `**/xxxTC.java`、`**/xxxTC_*.xls` 从每个逻辑引用根递归盘点全部后代目录和匹配文件，深度不限。进入递归兜底后，即使中途已经发现完整文件对，也要先搜索完全部自动化逻辑引用，再从完整盘点结果中配对和选择；不得在首个子目录、首个匹配或只找到一种文件时停止。完整盘点后当前候选仍不完整，才继续下一候选或下一 TC 组。
5. 只读自动化库必须使用当前工作区 `.opencode/opencode.jsonc` 中平台写入且 `testagent-reference-kind=automation` 的每个逻辑引用。直接使用 Read/Glob/Grep 读取，无需询问读取权限。Java/XLS 按 TC 主名配对并优先同一逻辑父目录；Java 明确引用 XLS 时优先遵循该关系。必须记录 `recursiveSearchCompleted`、`searchedReferenceAliases` 和 `matchedLogicalPaths`，只有全部引用完成递归搜索后才能报告未找到。禁止扫描服务器物理根、读取或拼接 `OPENCODE_REFERENCES_DIR` 实际值、写入引用目录、执行 Git、修改 JSONC 或新增后端文件代理。
6. 发现 TC 文件后按 `references/tc-parsing.md` 解析。必须先完整提取全部 Excel，并重点还原其中的请求报文、数据准备/恢复和断言，再用 Java 确认消费方式、字段映射、覆盖顺序和生命周期；不得因为 Java 未内联某项配置而忽略 Excel。只复用 `legacy-interface-function-asset-to-md` 的 Java/Excel/SQL/Mock/断言识别与 Excel 提取能力，禁止使用它的输出目录、模板、资产 Markdown、转换报告和增量标记。
7. 按 `scripts/rank_reference_scripts.py` 输出的 `integratedScriptBatches` 顺序处理一体化平台脚本，每批最多 5 个，按 `references/api-contracts.md` 调用一体化平台接口。当前批返回至少一条可解析业务信息时立即停止；只有响应明确包含“未查询到接口”且当前批没有可用数据时，才继续查询下一批，直到有可解析数据或批次耗尽。版本和应用名按该文档从上下文解析，不从包名猜测。
8. **双来源完整性门禁。** NIT 列表同时存在 TC 和一体化平台脚本时，两条链路都必须执行并成功形成参考：完成一个完整 TC Java/Excel 组的解析，同时按排序批次查询一体化平台，直到某批返回至少一条可解析案例脚本。平台返回案例数量可以少于请求批次的 5 条，1 条可解析案例即可作为生成参考；只有所有批次都返回“未查询到接口”或没有可用数据时才判定平台参考未完成。找到 TC 后不得跳过平台调用，平台已有案例也不得跳过 TC 文件查找和解析。
9. 分别盘点 TC 与一体化平台在请求报文、数据准备/恢复和断言三个维度中的实际内容，形成 `sourceContent`。每个维度须保存项目数量和可追踪的内部内容标识；只有完整解析后确认该来源在该维度确实没有内容，才能标记为空，不能为了通过后续门禁删除或隐藏来源内容。
10. 形成保存在 Agent 上下文中的 `referenceContext`：TCDS 状态、可用及已解析的参考类型、选中的 TC 解析事实、平台案例、完整数据准备/恢复动作、Mock、断言、规范请求结构 `reqParamStruct`、来源优先级、`sourceContent` 和警告。保留原始结构用于追踪，但明确标记唯一第一层节点为不进入实际报文的结构根节点，生成字段从其 `children` 开始。`reqParamStruct` 是最终报文唯一字段和嵌套结构白名单，默认全部结构字段必须存在且不得增加字段；已评审案例明确测试“缺少/不传某字段”时，为该案例记录从案例原文预先提取的精确 `expectedMissingPaths`。不存在新增字段允许列表；案例描述、TC Excel/Java、平台存量案例或其它参考中不属于 `reqParamStruct` 的字段一律不得进入最终报文，也不能根据生成结果、参考差异、空值或 `null` 反向放宽结构。SQL/table 无论大小都不得截断或简化。
11. 返回前关闭本 Skill 打开的全部文件、Excel 读取器和子进程，并只向调用方报告 `temporaryFilesCreated=true/false`，不得生成或返回临时文件清单。若因第三方工具创建了 sibling `.tmp/api-automation-<runId>` 内容，清理责任固定属于 `test-execution-api`：它必须在覆盖整个工作流的 `finally` 中只删除本次 run，并在本次创建的 `.tmp` 根清空时删除该根，再运行 `scripts/validate_temp_cleanup.py`。入口 Agent还会独立复验。本 Skill 不得把临时目录当交付物，也不得触碰其它并发 run。

## 读取与委派边界

- 当前执行 Agent 直接加载本 Skill 并完成 TCDS、文件读取、Excel 提取、Java 解析和平台参考解析；禁止为了“任务复杂”再次调用 Task、General、Explore 或其它 Agent；
- 本工作流需要的文件、外部目录、命令、网络、Skill 和平台 Tool 权限均已由用户直接授权；直接使用 Read/Glob/Grep、脚本和 `http_call`，不调用 Question，不触发任何权限申请或批准流程；
- 公共 `legacy-interface-function-asset-to-md` 只作为 Skill 指令和脚本依赖直接加载，不得把解析工作委派给另一个 Agent；
- 工具或路径失败时直接记录失败证据并按规则处理，不能转换为用户权限确认，也不能通过再派发 Agent 规避；
- 只读授权不允许修改用户上传文件或自动化库，正式写入范围仍限 `042-测试执行`。

只有已识别接口身份并实际发起 TCDS 后，TCDS 失败且无上传参考时才要求用户决定是否无参考继续，这是需求规定的业务确认，不属于权限申请；未识别接口身份时不得提出该问题。除此之外，不得因执行本 Skill 的任何工具动作暂停等待授权。

## 双来源完成条件

- 过滤后的 NIT 只存在一种脚本类型时，只要求完成该类型的解析；
- 同时存在两种类型时，`requiredReferenceKinds=["TC","INTEGRATED"]`，必须同时满足 `tcReferenceParsed=true` 和 `integratedReferenceParsed=true`；
- `integratedReferenceParsed=true` 仅在按 `integratedScriptBatches` 顺序查询后，某一批返回至少一条可关联且可解析的案例脚本，并且该返回项中的案例、报文、数据准备、Mock、断言和 `reqParamStruct` 已按实际存在内容完成解析后成立；不要求当前批 5 个脚本全部返回；
- TC 候选全部找不到完整 Java + XLS、Excel 提取失败、平台调用失败、平台响应无法解析、所有平台批次均返回“未查询到接口”或没有可用案例脚本时，返回 `referenceResolutionStatus=PARTIAL`、`stageStatus=INCOMPLETE`，禁止进入正式脚本生成；
- 不得通过把失败类型从 `requiredReferenceKinds` 删除、只保留已成功类型或改写为 `NO_MATCH` 来绕过门禁；
- 只有两种必需参考都已进入 `referenceContext` 后，才能返回 `RESOLVED` 并调用生成 Skill。

## TCDS 失败处理

- TCDS HTTP 失败、非 JSON、`code != 0` 或响应结构无法解析均算失败。
- 存在用户上传的 TC Java/Excel 时，记录 `PARTIAL` 并继续使用上传参考。
- 只有已识别接口身份且本 Skill 的第一项外部动作已经实际发起 TCDS 调用时，没有用户上传 TC 文件且 `allowWithoutReference != true` 才能返回 `TCDS_FAILED_CONFIRMATION_REQUIRED`，不得生成正式文件；未识别接口身份的请求不适用此规则。
- 用户已明确同意且 `allowWithoutReference=true` 时，记录 `WITHOUT_REFERENCE_CONFIRMED` 并继续；仍不得输出“待确认”“需确认”占位语。

## 返回

内部结果至少包含：

```text
referenceResolutionStatus
tcdsCalled
tcdsStatus
nitScriptIds
selectedTcGroup
selectedTcFiles
recursiveSearchCompleted
searchedReferenceAliases
matchedLogicalPaths
integratedScriptIds
integratedInterfaceInfos
requiredReferenceKinds
resolvedReferenceKinds
tcReferenceParsed
integratedReferenceParsed
integratedRequestedCount
integratedParsedCount
sourceContent
canonicalReqParamStruct
caseExpectedMissingPaths
referenceCases
referenceDataPreparations
referenceMocks
referenceAssertions
excelCoverage
temporaryFilesCreated
warnings
```

`selectedTcFiles` 只向调用方传可读逻辑路径；最终用户回复不得展示自动化库物理路径。
