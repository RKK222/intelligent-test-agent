# 接口覆盖矩阵

## 接口契约摘要

| 接口编号 | 接口名称 | 方法/协议 | 路径/地址 | 业务前置 | 幂等 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| {{apiId}} | {{apiName}} | {{methodOrProtocol}} | {{apiPath}} | {{auth}} | {{idempotency}} | {{sourceEvidence}} |

## 覆盖矩阵

| 覆盖项 | 交易类型/业务动作 | 请求条件 | 返回/错误码 | 状态变化 | 数据/下游断言 | 风险维度 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| API-R001 | {{transactionType}} / {{businessAction}} | {{requestCondition}} | {{responseOrError}} | {{stateChange}} | {{dataOrDownstreamAssertion}} | {{riskDimension}} | {{sourceEvidence}} |
