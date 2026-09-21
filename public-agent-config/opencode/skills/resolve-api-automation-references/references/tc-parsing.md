# TC Java/Excel 解析

## 依赖能力

加载公共 Skill `legacy-interface-function-asset-to-md`，并按它的解析方式读取：

- Java/TestNG/JUnit 测试类、方法、DataProvider、fixture 和公共方法；
- Excel 原始值、显示值、公式、隐藏行列和合并区域；
- 请求构造、字段映射、覆盖顺序和动态表达式；
- SQL 数据准备、恢复、查询和适用案例；
- Mock 安装、匹配、返回和卸载；
- 返回值断言和数据库断言；
- setup、teardown、rollback 和 finally 顺序。

发现 XLS 后必须使用 legacy Skill 的 `scripts/extract_excel.py` 提取，不得只凭目视抄表。优先直接消费提取器 stdout/内存结果并保存在 Agent 上下文；若该提取器版本强制生成 `workbook.json`、`summary.md` 和 `tables/*.tsv`，才允许写入调用方按需创建的 `<042-测试执行父目录>/.tmp/api-automation-<runId>/excel/`。不得使用 042 内部、legacy Skill 默认输出目录、系统临时目录、当前目录或 Skill 目录。解析完成后关闭全部读取器，由 `test-execution-api` 在统一 `finally` 中删除本次 run；若 `.tmp` 根由本次创建且已为空也删除该根，不能触碰其它并发 run。

## Excel 重点解析

TC 文件对完整时，必须先完整提取全部 `xxxTC_*.xls`，再结合 Java 还原执行模型。不能只扫描 Java 中出现的字段、SQL 或断言，也不能只读取 Excel 中名称看似与案例数据有关的工作表。

逐个检查提取结果中的 `workbook.json`、`summary.md` 和全部 `tables/*.tsv`，覆盖可见及隐藏工作表、隐藏行列、合并区域、公式、原始值和显示值。不得假定表头在首行或依赖固定 Sheet 名。

对每条 Excel 案例至少建立三类内部事实：

1. **请求报文**：完整报文或模板、字段路径、初始值、案例覆盖值、动态表达式和序列化内容；
2. **数据准备与恢复**：SQL/table 内容、准备或恢复阶段、执行时机、适用案例和排除案例；
3. **断言**：返回字段路径、比较方式、预期值、数据库查询 SQL、数据库字段断言及适用案例。

数据准备/恢复必须保留 Excel 单元格或拼接结果中的完整内容。SQL 无论长度、行数、子查询层级或语句数量都不得截断、摘要、重排、格式化改写或用省略号替代；table 类型必须保留全部列、全部行、原始顺序和空单元格。长内容读取困难时应分段读取提取结果后在内部无损合并，不能跳过。

Java 用于确认 Excel 的 DataProvider/fixture 消费方式、字段映射、类型转换、公共覆盖、案例分支覆盖和生命周期顺序。某段 Excel 内容没有直接出现在测试方法中时，必须继续检查框架约定、公共读取器和配置消费路径，不能据此直接忽略。

解析完成前执行 Excel 覆盖核对：每个有效案例数据行，以及识别出的报文、数据准备、恢复和断言配置，都必须进入对应案例的内部参考结果，或记录明确的内部排除原因；不得静默遗漏。

## 禁止复用的部分

本流程明确禁止使用 legacy Skill 的以下输出行为：

- `docs/功能模块/待确认` 固定目录；
- 接口自动化资产模板和资产转换报告模板；
- `GENERATED/MANUAL` 标记；
- 转换报告、缺口报告或任何第三个正式文件；
- 正式文件中的“未获取”“待确认”“需确认”措辞。

legacy Skill 只提供内部解析方法和确定性 Excel 提取器。本功能的正式输出唯一来自 `generate-api-automation-markdown` 模板，并写入 `042-测试执行`。

## 配对与合并

- Java 显式引用的数据文件优先配对；其次使用 TC 主名一致性；
- 用户上传文件优先。上传 Java + 上传 XLS 完整时，不再用只读库文件覆盖；
- 上传 Java + 缺失 XLS 时，只能用同一 TC 主名的只读 XLS 补齐，反向同理；
- 多个 `xxxTC_*.xls` 全部解析，不能默认只读第一份；
- Java 与 Excel 冲突时按信息维度处理：执行路径、字段映射、覆盖顺序和类型转换以可达 Java 为准；案例原值、报文基准值、SQL/table 配置、断言比较方式和预期值以实际被框架消费的 Excel 显示值为重点依据；Java 中可达的内联 SQL、Mock、断言和运行时覆盖作为补充或最终覆盖。冲突只进入内部警告，不在正式脚本输出来源信息。

## 中间结果

每个参考案例内部至少还原：

```text
caseId
caseName
completeRequest
fieldOverrides
dataPreparations
mocks
responseAssertions
databaseAssertions
runtimeExpressions
cleanupActions
excelRequestCoverage
excelDataPreparationCoverage
excelAssertionCoverage
completeDataPreparationActions
```

这些内容只作为生成依据，不单独落正式文件。
