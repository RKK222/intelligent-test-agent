# 一体化平台接口契约

## 请求

查询脚本固定向以下路径发送 `POST application/json`，不接受对话覆盖地址：

```text
http://interface.sdc.cs.icbc/contract-api/opencode/interface/getInterfaceInfoByName
```

请求体：

```json
{
  "appName": "从工作空间路径解析",
  "version": "从工作空间路径中的 YYYYMMDD 转为 YYYY年M月",
  "interfaceEnName": "用户明确给出的接口英文名"
}
```

## 必需响应

`interfaceInfo.reqParamStruct` 必须是非空数组。Skill 不消费或落盘 `caseList`、`dataPrepareList`、`assertGroupList`、`dataMockList` 等其他字段。

## 失败边界

- 20 秒超时，不自动重试；
- 响应体上限 4 MiB；
- HTTP 非 2xx、JSON 非对象、`interfaceInfo` 缺失或 `reqParamStruct` 为空时失败关闭；
- 错误输出不得包含完整原始响应。
