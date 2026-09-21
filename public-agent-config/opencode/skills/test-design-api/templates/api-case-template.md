# 接口名称：{{apiName}}

## 测试案例 1：{{caseTitle}}

[案例信息]

案例名称：{{caseName}}

测试步骤：
{{steps}}

[内部数据依赖]

| 数据对象 | 表名 | 依赖数据逻辑 | 依赖数据示例 |
| --- | --- | --- | --- |
| {{dataObject}} | {{tableName}} | {{dependencyLogic}} | {{dependencyExample}} |

[CMC参数依赖]

| 参数名称 | 参数逻辑 | 参数键 | 示例值 |
| --- | --- | --- | --- |
| {{paramName}} | {{paramLogic}} | {{paramKey}} | {{sampleValue}} |

[下游应用依赖]

| 下游应用 | 依赖数据逻辑 |
| --- | --- |
| {{downstreamApp}} | {{downstreamLogic}} |

[测试数据]

| 数据名称 | 接口字段 | 数据逻辑 | 示例值 |
| --- | --- | --- | --- |
| {{dataName}} | {{apiField}} | {{dataLogic}} | {{sampleValue}} |

[接口返回验证]

| 字段中文名 | 字段英文名 | 预期值 | 预期结果 |
| --- | --- | --- | --- |
| {{fieldNameZh}} | {{fieldNameEn}} | {{expectedValue}} | {{expectedResult}} |

[数据库验证]

| 表中文名 | 表英文名 | 字段英文名 | 预期值 | 预期结果 |
| --- | --- | --- | --- | --- |
| {{tableNameZh}} | {{tableNameEn}} | {{fieldNameEn}} | {{expectedValue}} | {{expectedResult}} |

---
