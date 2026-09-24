---
name: generate-api-automation-markdown
description: API Automation Script Generation（生成接口自动化脚本）。把已评审的接口 Markdown 案例转换为包含请求、依赖数据和断言的接口自动化脚本；接口编号/英文名场景可消费 TCDS、TC 和一体化平台参考，不处理测试设计。
compatibility: opencode
metadata:
  display-name: API Automation Script Generation
  display-name-zh: 生成接口自动化脚本
  agent-id: test-execution-api
  version: 2.3.1
  source: test-agent
  emoji: 🤖
---

# 生成接口自动化脚本

## 输入

test-design-api 产出的已评审接口 Markdown 案例。

案例路径可来自用户消息、右键文件、附件、manifest、casePath、sourceFiles 或测试设计阶段 writtenFiles。调用方进入接口 ID/英文名参考分支时，还必须传入 `resolve-api-automation-references` 形成的内部 `referenceContext`。

## 步骤

1. 读取执行范围内全部已评审案例，每个测试案例生成一个独立 `.md` 脚本；不能按参考脚本数量截断案例。
2. 文件名沿用案例名称：`<案例名称>-接口自动化脚本.md`，无需重新拼接序号、测试类型或交易类型。
3. 按“整份模板确定性渲染”在 Agent 上下文形成结构化 values 对象，通过 stdin 交给本 Skill 的 `scripts/render_api_script.py`，从主模板及 partial 一次性输出完整脚本；禁止为 values 创建文件，也禁止自行拼接任何章节。
4. 断言必须包含返回报文断言；涉及落库或状态流转时补数据库断言。
5. 应用内依赖数据禁止格式互相转换：输入为 SQL 时只输出 SQL 代码块，不额外生成表结构表格；输入为 table 时只输出表格，不额外生成 SQL。模板展示两种可选形式，不要求同时输出。
6. 每次渲染后先执行 `scripts/verify_rendered_script.py`，再调用 `validate-automation-script-format`；进入参考分支时还必须执行本文件的“参考分支二次校验”。

## 整份模板确定性渲染

`templates/api-script-template.md` 及其 `templates/partials/*.md` 是整份正式 Markdown 的唯一格式来源。主模板控制文档开头、1-5 章标题、固定说明、分隔线、固定表头和代码块位置；partial 控制重复的数据准备、返回断言行和数据库断言区块。所有固定文字都必须来自当前模板组，Python 和 Agent 均不得硬编码、仿写或凭记忆补写标题、表头、分隔线及章节顺序。

所有案例均执行以下流程，不限于 5.1/5.2：

1. 在 Agent 上下文中创建 values 对象。根节点必须提供主模板使用的字符串字段 `caseName/testType/transactionType/testPoint/designMethod/coverageDimension/caseDescription/apiId/apiName/apiUrl/payloadCodeBlockLanguage/requestPayload`，并提供以下结构化数组：
   - `dependencies`：按执行顺序排列；`kind=sql` 时包含 `databaseName/sql`，`kind=table` 时包含 `tableName/columns/rows`，每行最后一项是说明列；SQL 原文直接作为字符串写入，禁止预先格式化、摘要或截断；
   - `responseAssertions`：每项包含 `fieldPath/expectedValue/assertionMethod/assertDescription`，至少一项；
   - `databaseAssertions`：没有数据库断言时为空数组，有值时每项包含 `tableName/sql/assertions`，其中断言行包含 `fieldName/expectedValue/assertionMethod/assertDescription`。
2. 执行：

   ```text
   <上下文中的values JSON> | python -B <本Skill目录>/scripts/render_api_script.py \
     --input - \
     --output <resolvedOutputTarget/案例名称-接口自动化脚本.md> \
     --output-target <resolvedOutputTarget>
   ```

3. 渲染器完整读取当前主模板和全部 partial，在内存中完成替换，只写指定的正式 Markdown；manifest 只从 stdout 返回并保存到 Agent 上下文，不生成 manifest 文件。正式输出必须位于同一 `042-测试执行`，且不能位于任何临时目录。
4. 用上下文中的 values 和 manifest 形成输入对象后立即执行：

   ```text
   <{"values": {...}, "manifest": {...}}> | python -B <本Skill目录>/scripts/verify_rendered_script.py \
     --input - \
     --output <resolvedOutputTarget/案例名称-接口自动化脚本.md> \
     --output-target <resolvedOutputTarget>
   ```

5. 校验器会重新读取当前完整模板组并重渲染全文，逐字节核对正式输出，同时核对 renderer、所有模板/partial、values 和 output 的 SHA-256。任一章节标题、固定说明、表头、列数、分隔线、代码块、重复区块格式或顺序偏离模板，模板在渲染后被修改，正式文件被手工修改，或仍有未解析占位符时均失败。
6. 任何内容或格式修正只能修改上下文中的 values 或当前权威模板后重新渲染，禁止直接编辑正式 Markdown。模板修改后旧 manifest 必须失效，不能沿用旧校验结果。
7. `generatedFiles` 只记录正式 Markdown；values、manifest 和校验结果只存在于上下文/stdin/stdout，不得创建对应文件。

## 参考分支生成规则

`referenceContext.referenceResolutionStatus` 非 `NOT_APPLICABLE` 时应用以下规则：

生成前先检查参考完整性：若 `requiredReferenceKinds` 同时包含 `TC` 和 `INTEGRATED`，但 `resolvedReferenceKinds` 未同时包含两者，或任一对应的 `*ReferenceParsed` 不为 `true`，立即返回 `INCOMPLETE`，不得写入任何正式脚本。来源优先级不能用于跳过这项检查。

### 案例与参考的职责

- 当前已评审案例决定测试目标、异常字段、边界值、预期结果和最终输出数量；
- 用户上传 TC、只读 TC 和一体化平台脚本仅提供请求基准、数据准备、Mock 和断言参考；
- 有直接相关参考时优先使用；没有直接参考的案例选择最相关成功案例为基准，只修改案例明确指定的目标字段；
- 不得因为参考只覆盖成功场景而跳过异常、空值、枚举、长度或边界案例。

### 来源优先级

生成字段值、SQL、Mock 和断言时：

```text
当前已评审案例的目标差异
> 用户上传 TC Java/Excel
> 只读自动化库选中的 TC Java/Excel
> 一体化平台当前相关案例
> 同接口最相关成功案例
```

一体化平台的非空 `canonicalReqParamStruct` 是结构权威，不受上述值优先级覆盖。

### TC Java/Excel 合并

- TC 参考包含 Excel 时，请求报文基准值、数据准备/恢复、返回断言和数据库断言必须优先从已提取的 Excel 事实中逐案例映射，不能只根据 Java 代码生成；
- Java 用于应用字段映射、类型转换、公共运行时覆盖、案例分支覆盖和实际生命周期顺序；
- Excel 中实际被框架消费的 SQL/table、比较方式和预期值不得被同一来源层级的推断值替换；
- 生成前检查 `referenceContext.excelCoverage`，确认 Excel 中的报文、数据准备和断言均已映射或有内部排除原因。覆盖信息不得写入正式脚本。

### 双来源贡献门禁

`requiredReferenceKinds` 同时包含 `TC` 和 `INTEGRATED` 时，请求报文、数据准备/恢复和断言必须逐维度同时参考两边：

1. 根据 `referenceContext.sourceContent` 为当前案例建立内部 `sourceContributions` 台账；
2. 某来源在某维度有内容时，必须记录 `ADOPTED`、`COMPLEMENTED`、`CROSS_VALIDATED` 或 `CONFLICT_RESOLVED`，并列出实际生成字段路径、数据动作索引或断言路径；
3. 非冲突内容必须合并；完全相同内容可以去重但要同时计入两边贡献；冲突内容按案例目标和来源优先级决定最终值，同时记录具体冲突路径；
4. 某来源经完整解析确认在该维度无内容时才记录 `NO_CONTENT`，不得因为没有采用而反向标记为无内容；
5. 不得出现三个维度全部只使用 TC 或全部只使用一体化平台的情况。

### 请求报文

1. 以最高优先级参考的完整请求为基准；
2. 按当前案例只修改目标字段和有明确证据的运行时表达式；
3. 存在 `canonicalReqParamStruct` 时，其数组中唯一第一层节点默认只是结构根节点；最终报文不得带该节点名称，直接从该节点的 `children` 开始生成第一层字段，不按根节点是否名为 `ROOT` 判断；
4. 默认递归保留根节点 `children` 下的所有字段、对象和数组；空字符串字段写 `""`，不得删除或写 `null`。普通案例的 `expectedMissingPaths` 必须为空，最终报文与结构完全一致；
5. 当前已评审案例明确测试“缺少/不传某字段”时，才省略从案例原文映射出的精确结构路径；目标字段必须真正不存在，数组元素内目标须对每个实际元素生效，除精确目标外其余结构仍须完全一致；
6. `reqParamStruct` 是唯一字段白名单，不存在新增字段例外。即使案例描述明确要求“新增/多传/未定义字段”，或者 TC Excel/Java、平台存量案例及其它参考中包含白名单外字段，也不得写入最终报文；把结构根节点名称包在报文外层同样属于禁止的额外字段；
7. 缺字段目标必须在生成前从案例原文提取，不能根据生成后实际差异、参考碰巧缺少字段、空值或 `null` 反向推断；
8. 参考缺少但结构存在时必须按 `reqParamStruct` 递归补齐全部后代字段，叶子字符串写 `""`；只有结构字段本身确为可空数组且当前案例无元素时才写 `[]`，不得用空对象跳过其子字段；
9. 结构字段顺序遵循根节点 `children` 的顺序；
10. 完整报文必须直接写入每个案例的脚本，禁止“同上”“类似案例”或省略号。

### 数据准备和断言

- 复用参考中适用于当前案例或 `all` 的全部 SQL/table、Mock 和断言；准备及恢复动作均不得遗漏；
- `stepType=0` 写准备，`stepType=1` 写恢复；保持 `executeTiming` 的全局前/每案例前语义；
- SQL 必须按参考内容完整输出到代码块，长 SQL、多语句 SQL、存储过程、嵌套查询均不得截断、概括、重排、合并、拆分、重新格式化或写“同上/略/其余不变/...”代替；
- table 数据必须保留全部行列、顺序和空值，不得为了缩短文件只输出示例行；
- 异常案例只调整与目标异常直接相关的预期返回或数据库断言，其它执行事实保持参考一致；
- 不从案例名猜测表、SQL、返回码或 Mock。关键事实完全无法恢复时返回 `INCOMPLETE`，不要生成占位脚本。

## 参考分支二次校验

每个脚本写入后必须重新读取并执行以下检查：

本节所有校验输入对象、结果、解析事实和日志均保存在 Agent 上下文，通过 stdin/stdout 传递；不得生成校验 JSON、结果 JSON、清理登记或缓存文件。所有 Python 校验命令使用 `python -B`。只有不受控第三方程序确实强制落盘时，才复用调用方按需创建的 sibling `.tmp/api-automation-<runId>`，本 Skill 不得另建或改换临时目录。

1. **全案例检查**：预期案例集合与 `generatedFiles` 一一对应，没有遗漏、合并或重复。
2. **禁止词检查**：脚本不得出现“待确认”“需确认”“未确认”或常见中英文占位标记。
3. **整份模板检查**：确认 `verify_rendered_script.py` 已基于当前主模板和全部 partial 通过；失败时只修改 values 后重渲染，不得直接修补正式 Markdown。随后调用 `validate-automation-script-format`，失败后同样回到 values 重渲染并重跑。
4. **结构检查**：存在 `canonicalReqParamStruct` 时，在上下文中形成最终请求报文和结构对象：

   ```json
   {
      "payload": {},
      "reqParamStruct": [],
      "expectedMissingPaths": []
   }
   ```

   然后执行：

   ```text
   <上下文中的结构校验JSON> | python -B <resolve-api-automation-references目录>/scripts/validate_generated_payload.py \
      --input - \
      [--expected-missing-field <caseDeclaredFieldPath>] \
      [--length-check <fieldPath>=<expectedLength>:chars|utf8-bytes]
   ```

   校验器默认忽略 `reqParamStruct` 唯一第一层结构根节点。普通案例的 `expectedMissingPaths` 为空，任何结构差异都会失败；缺字段案例只把案例预先指定的路径写入该列表。校验器要求缺字段目标确实不存在、数组内目标对每个实际元素生效，且其它字段完全一致；任何额外字段都会失败，不能用案例描述或参考资产放行。根节点名称、未计划缺失、目标未生效、无效允许路径、`null`、容器无效或长度不符时必须修正上下文中的实际报文并重新渲染。
5. **长度检查**：案例涉及长度时，必须从最终序列化报文取值复核；不能只看生成前变量或文字说明。计量方式由案例决定，默认字符数，明确字节长度时使用 UTF-8 字节数。
6. **数据准备完整性检查**：从最终脚本重新提取当前案例的准备/恢复动作，按输出语义形成有序列表，与 `referenceContext` 中当前案例应输出的有序动作列表组成上下文对象：

   ```json
   {
     "referenceDataPreparations": [],
     "generatedDataPreparations": []
   }
   ```

   两个列表中的每个动作必须使用同一字段集合，至少包含阶段、顺序、数据类型以及完整 `executeSql`，或完整 `actionType/tableName/tableData`。然后执行：

   ```text
   <上下文中的数据准备校验JSON> | python -B <resolve-api-automation-references目录>/scripts/validate_data_preparations.py \
     --input -
   ```

   缺少动作、多出动作、顺序改变、SQL/table 内容变化或长内容被截断时必须修正实际脚本并重新验证。只允许统一 `CRLF/LF` 换行，不能折叠空白或重新格式化 SQL。
7. **双来源贡献检查**：把 `requiredReferenceKinds`、三个维度的来源内容盘点和内部贡献台账组成上下文对象：

   ```json
   {
     "requiredReferenceKinds": ["TC", "INTEGRATED"],
     "sourceContent": {
       "TC": {"request": true, "dataPreparations": true, "assertions": true},
       "INTEGRATED": {"request": true, "dataPreparations": true, "assertions": true}
     },
     "sourceContributions": {}
   }
   ```

   然后执行：

   ```text
   <上下文中的来源贡献校验JSON> | python -B <resolve-api-automation-references目录>/scripts/validate_reference_contributions.py \
     --input -
   ```

   任一有内容来源在任一维度未被采用、补充、交叉核对或显式解决冲突时，必须重新合并并验证；两类来源中任一在三个维度均无实际贡献时不得生成。
只有全部内容检查通过且调用方最终残留校验通过，才返回 `artifactStatus.script=GENERATED`。

## 输出

加载 `test-execution` Skill 及其 `test-execution/rules/output-paths.md`。未进入参考分支时沿用存量路径优先级。进入接口 ID/英文名参考分支时，输出目录固定为当前需求项/子条目的 `04-测试/042-测试执行/`；用户明确文件路径只有位于该目录内才可使用，目录外路径返回 `INCOMPLETE`。案例文件所在目录只用于读取，不得据此创建同级 `测试执行/`。

进入参考分支时，正式输出只能是接口自动化脚本；不得写 legacy 资产、转换报告、参考计划、接口原始响应、values/manifest/校验 JSON、`clean-up.json`、`cleanup.json` 或 `.reference-work` 内容。未进入参考分支时继续沿用原有缺口处理逻辑。

## 通用约束

- 只使用已评审材料和已解析参考；
- 只转换已评审的测试设计产物；设计规则以 test-design 的输出为准；
- 不使用空泛名称和空泛预期；
- 输出中写明 resolvedOutputTarget 和 generatedFiles；
- 参考来源、内部排序分数、自动化库物理路径、TCDS 原始响应和一体化平台原始响应不得写入正式脚本。
