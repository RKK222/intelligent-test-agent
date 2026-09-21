# 接口自动化脚本模板

每个接口案例生成一个独立 `.md` 文件。

文件名：`<案例名称>-接口自动化脚本.md`

## 1. 案例信息

| 字段 | 内容 |
| ---- | ---- |
| 案例名称 | {{caseName}} |
| 测试类型 | {{testType}} |
| 交易类型 | {{transactionType}} |
| 测试要点 | {{testPoint}} |
| 设计方法 | {{designMethod}} |
| 覆盖维度 | {{coverageDimension}} |
| 案例说明 | {{caseDescription}} |

---

## 2. 接口信息

| 字段 | 内容 |
| ---- | ---- |
| 接口编号 | {{apiId}} |
| 接口名称 | {{apiName}} |
| 接口类型 | RPC / HTTP |
| 接口地址 | {{apiUrl}} |
| 请求报文格式 | JSON / XML / TXT / CSV |

---

## 3. 请求报文

```{{payloadCodeBlockLanguage}}
{{requestPayload}}
```

---

## 4. 应用内依赖数据

按实际执行顺序为每个准备和恢复动作重复下列对应小节。所有 SQL/table 必须完整写入；不得只保留代表性动作，不得因内容过长而截断、摘要或省略。没有某种数据类型时删除对应示例小节。

{{dependencySections}}

---

## 5. 断言

### 5.1 返回报文断言

| JSON Path / XPath / 字段路径 | 预期值 | 断言方式 | 说明 |
| ---------------------------- | ------ | -------- | ---- |
{{responseAssertionRows}}

{{databaseAssertionSection}}
