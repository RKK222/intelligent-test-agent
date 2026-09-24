# TCDS 与一体化平台接口契约

## TCDS 关联脚本查询

- URL：`http://tcds-prod.sdc.icbc/caseInterface/getyScriptIdByInterface`
- Method：`POST`
- Content-Type：`application/json`
- Header：`toolId: 66f36bfa5c1c6105572b0118880261d6`

请求体固定包含两个字段；未识别的字段传空字符串，不能用日期、路径或案例编号补造：

```json
{
  "seasId": "103849873",
  "seasName": "Moegglobalpaysubmit"
}
```

使用公共 `http_call` Tool 时参数为：

```text
uri=http://tcds-prod.sdc.icbc/caseInterface/getyScriptIdByInterface
method=POST
headers={"toolId":"66f36bfa5c1c6105572b0118880261d6","Content-Type":"application/json"}
body=<上面请求体的 JSON 字符串>
```

成功响应要求 `code == 0` 且 `data` 为数组。只保留 `scriptType` 去空白后不区分大小写等于 `NIT` 的元素。`scriptId` 若以 `Nit,`、`NIT,` 等大小写变体开头，先移除该前缀和逗号，再参与分类和后续接口入参。

## 一体化平台接口脚本查询

- URL：`http://interface.sdc.cs.icbc/contract-api/opencode/interface/getInterfaceInfosByScriptIds`
- Method：`POST`
- Content-Type：`application/json`

```json
{
  "appName": "F-BASE",
  "version": "2026年3月",
  "scriptIds": ["脚本名1", "脚本名2"]
}
```

`scriptIds` 必须是排序后的非 TC 一体化平台脚本的当前批次，去重后每批最多 5 个，一次请求传入，不能逐个调用。调用方必须按 `integratedScriptBatches` 顺序逐批请求：当前批返回可解析的 `interfaceInfos`/案例脚本数据后立即停止，不要求响应覆盖该批全部 5 个脚本；若响应明确返回“未查询到接口”且没有可用数据，才继续下一批。所有批次耗尽仍无可用数据时，平台参考链路返回 `PARTIAL/INCOMPLETE`。

### appName 解析

按以下顺序选择第一个非空精确值：

1. 用户明确给出的应用 ID；
2. Task prompt 的 `workspaceContext.appId`；
3. 当前应用工作空间元数据中的应用 ID；
4. 已评审接口案例中明确标注的应用 ID。

不得从 Java 包名、脚本第一段、中文模块名或常识猜测。存在一体化平台脚本但没有 appName 时返回 `INCOMPLETE`，由主 Agent 要求用户补充应用 ID 或恢复工作空间应用上下文；不得跳过必需调用。

### version 解析

按以下顺序选择：

1. 工作空间元数据中的明确版本；
2. 当前工作空间路径中有效的 8 位日期段 `yyyyMMdd`；
3. 已评审材料中明确的版本日期。

`yyyyMMdd` 必须先按真实日期校验，再转换为 `yyyy年M月`，例如 `20260101 -> 2026年1月`。如果上下文已经是 `yyyy年M月`，保持该值。多个日期候选时优先使用应用工作空间元数据；仅有路径时使用最接近当前应用工作空间根的有效日期段。不得使用当前系统日期代替。存在一体化平台脚本但无法得到版本时返回 `INCOMPLETE`，由主 Agent 要求用户补充版本或恢复工作空间版本上下文。

调用 `rank_reference_scripts.py` 时，`--workspace-value` 按上述优先级从高到低重复传入；脚本选取第一个含有效版本的上下文值。

### 返回使用

读取 `interfaceInfos` 中的：

- `caseList`：案例名和 `caseData`；
- `dataPrepareList`：SQL 或表格准备/恢复；
- `assertGroupList`：返回值和数据库断言；
- `dataMockList`：Mock；
- `reqParamStruct`：最终请求报文唯一字段和嵌套结构白名单。数组中唯一的第一层节点作为结构根节点保留在内部上下文，但生成实际报文时不输出该节点名称，直接使用其 `children` 作为报文第一层字段。普通案例必须完整保留全部结构字段且不能增加字段；只有已评审案例明确测试缺少字段时，才允许省略该案例指定的精确路径。案例描述或任何参考中的“新增/多传/未定义字段”均不能突破该白名单。

若多个返回项的 `reqParamStruct` 不一致，使用输入排序中第一个非空结构作为当前生成批次的规范结构；后续参考值只能映射到该结构已有字段，不能因案例描述、后续参考或存量资产本身多出字段而扩大白名单。响应若携带脚本标识则按标识关联；未携带时按请求 `scriptIds` 与返回顺序关联。

当前批不要求每个 `scriptId` 都关联到返回 case；只要响应中至少有一个可关联且可解析的案例脚本，就可以停止后续批次并形成平台参考。返回案例少于请求项（包括只返回 1 条）不视为失败；无法解析的返回项可丢弃，但若当前批没有任何可用案例且响应不是明确的“未查询到接口”，则按平台响应解析失败处理。所有批次均无可用案例时，平台参考链路不完整；同时存在 TC 参考时必须返回 `PARTIAL/INCOMPLETE`，不能只使用已成功解析的 TC 生成脚本。
