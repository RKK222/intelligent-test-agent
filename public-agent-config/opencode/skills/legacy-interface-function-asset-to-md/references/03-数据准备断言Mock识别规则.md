# 数据准备、断言、Mock 与恢复识别规则

## 目录

1. 通用动作模型
2. SQL 数据准备与恢复
3. 断言
4. Mock 与非 SQL 依赖
5. Excel/配置执行性
6. 场景适用性
7. 未发现时的措辞

## 1. 通用动作模型

每个候选动作记录：

| 字段 | 说明 |
| --- | --- |
| `kind` | prepare_sql / lookup_sql / cleanup_sql / assertion / mock / dependency / unknown |
| `source_type` | code / excel / sql / config / log / document |
| `source_location` | 文件、Sheet、方法或行号 |
| `phase` | 主技能定义的最小执行阶段 |
| `execution_status` | confirmed_executed / configured_unverified / referenced_not_reached / unknown |
| `applicable_scenarios` | 全部或具体场景 ID |
| `inputs` | 使用变量 |
| `outputs` | 产生变量或副作用 |

保留最小阶段和动作顺序，用于区分准备、调用后断言和恢复；不重复抄写完整生命周期代码。

## 2. SQL 数据准备与恢复

### 2.1 唯一范围

数据准备只支持 SQL。API、SSH、Shell、文件、配置修改和普通方法调用不得标为数据准备。

| 代码形态 | 默认分类 | 判断要求 |
| --- | --- | --- |
| 调用前 INSERT/UPDATE/REPLACE/MERGE | `prepare_sql` | 结合可达路径和适用场景 |
| 调用前 DELETE | `cleanup_sql` | 属于前置清理，不得写成恢复 |
| SELECT 后提取字段进入请求 | `lookup_sql` | 记录输出变量及映射 |
| SELECT 后与预期比较 | `assertion` | 归数据库断言 |
| 调用后 DML | 恢复候选 | 结合触发条件确定 cleanup 阶段 |
| finally/@After 中 DML | 恢复候选 | 只有可达时才确认执行 |

输出完整 SQL、数据库、阶段、适用场景、执行状态和输入输出；来源仅供内部判断，不写入正式资产或转换报告。不评价 SQL 安全性、数据积累风险或脱敏需求。

### 2.2 来源

SQL 可能位于：

- 测试方法；
- `@Before*`、`@After*`、fixture 或 hook；
- 被调用的公共方法；
- 任意 Excel Sheet；
- 外部 SQL、XML、JSON、YAML 或框架配置。

Sheet 名、列名和 SQL 关键字只能帮助发现候选。必须继续检查代码或框架是否消费该配置。

## 3. 断言

识别：

- `assertEquals`、`assertTrue`、`assertThat` 和自研 Assert；
- JSONPath/XPath 比较；
- 数据库查询结果比较；
- 异常、状态码和响应结构断言；
- 已确认由框架执行的 Excel 断言。

每条断言在内部记录 actual、expected、operator、expected 的取值依据、阶段、适用场景和执行状态。输出时不写取值来源；预期值引用测试数据时仍必须在内部绑定到具体场景行。

## 4. Mock 与非 SQL 依赖

Mock 候选包括：

- WireMock/MockServer stub；
- Mockito 对外部 client 的行为配置；
- 自研 Mock 平台调用；
- 固定响应文件；
- 环境开关切换到 Mock。

只有启用或安装动作在当前路径可达时，才标为 `confirmed_executed`。

API、SSH、Shell、文件和普通方法调用如果为主接口提供输入或环境条件，归入 `dependency`，不得写入 SQL 数据准备。

## 5. Excel/配置执行性

### 5.1 Sheet 角色

同时使用以下证据：

1. Sheet 名；
2. 表头和单元格内容；
3. Java/DataProvider 的读取路径；
4. 框架基类或配置加载器；
5. 执行日志。

不得规定某类 SQL 只能出现在固定 Sheet，也不得因 Sheet 名不符合预期而排除其中的 SQL。

### 5.2 执行状态

- 当前脚本、公共方法或基类明确读取并执行：`confirmed_executed`；
- 执行日志确认命中：`confirmed_executed`；
- 只有配置存在：`configured_unverified`；
- 有引用但当前分支不可达：`referenced_not_reached`；
- 证据不足：`unknown`。

### 5.3 多来源并存

Java 与 Excel 同时存在 SQL、断言或 Mock 时不得二选一。检查：

- 是否共同执行；
- 内容是否重复；
- 数据库、参数或场景范围是否不同；
- 是否存在不可达或失效配置；
- 是否产生执行结果冲突。

不确定时保留双方并生成冲突，但冲突输出只描述差异及影响，不写来源名称或位置。

## 6. 场景适用性

从以下证据判断：

- `if/switch` 中的案例 ID；
- Excel 的适用案例、排除案例和执行开关；
- 测试方法或参数化标记；
- Mock 匹配条件；
- 生命周期方法的 include/exclude 配置。

无法确认时写“适用范围未知”，不得默认全部场景。

## 7. 未发现时的措辞

- SQL 数据准备：`扫描可用代码、全部 Excel Sheet 和外部配置后，未发现明确的 SQL 数据准备。`
- Mock：`未发现 Mock 配置或启用动作；这不等于不存在真实上下游依赖。`
- 数据库断言：`未发现实际执行或已确认配置的数据库断言。`
- 数据恢复：`未发现主接口调用后的显式 SQL 恢复；调用前清理不计为恢复。`
