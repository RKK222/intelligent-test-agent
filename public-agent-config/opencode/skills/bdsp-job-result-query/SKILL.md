---
name: bdsp-job-result-query
description: 查询 BDSP 大数据作业组或单作业执行结果并导出 Excel。根据 JC2/JC4/JC6 自动选择 GaussDB 配置，支持直接参数或 Excel 批量输入，使用随包 JDBC 驱动查询并按环境生成 .xls 结果。用户提出“作业组执行结果查询、作业调度查询、作业执行明细、批量查询大数据作业结果”等需求时使用。
compatibility: opencode 1.18.4+
metadata:
  domain: bdsp-testing
  language: zh-CN
  runtime: python3-java
---

# BDSP 作业执行结果查询

## 能力

- 查询作业组执行结果：调度环境 + 调度应用 + 作业组名 + 可选调度日期。
- 查询单作业执行结果：增加作业名。
- 支持 `.xls/.xlsx` 批量输入。
- 自动读取 `config/database.ini`，使用 `config/.encryption.key` 解密原工程中的数据库密码。
- 使用 `lib/` 中的 GaussDB JDBC 驱动；查询结果按环境写入 `.xls`。

## 使用原则

1. 这是只读查询技能，不修改数据库。
2. 缺少调度环境、应用、作业组等必要信息时先补齐，不要猜测。
3. 不要把数据库主机、用户名、密码、加密密钥或完整连接串输出给用户。
4. 用户给了 Excel 时优先直接读取该文件，不要要求其改造成其他格式。
5. 仅根据原工程既有逻辑查询 `bdsp.etl_job`；不要擅自执行其他 SQL。

## 首次准备

需要 Python 3 和 Java（`java` 命令可用）。在本技能目录执行：

```bash
python3 -m pip install -r requirements.txt
```

## 作业组查询

直接参数：

```bash
python3 scripts/query.py group \
  --env "JC2" \
  --app "<调度应用>" \
  --group "<作业组名>" \
  --date "YYYYMMDD"
```

Excel 批量：

```bash
python3 scripts/query.py group --input "/absolute/path/input.xls"
```

Excel 必须包含：`调度环境`、`调度应用`、`作业组名`；`调度日期`可选。

## 单作业查询

```bash
python3 scripts/query.py job \
  --env "JC2" \
  --app "<调度应用>" \
  --group "<作业组名>" \
  --job "<作业名>" \
  --date "YYYYMMDD"
```

Excel 批量时增加 `作业名` 列。

## 仅校验，不连接数据库

```bash
python3 scripts/query.py group \
  --env "JC2" --app "<应用>" --group "<作业组>" \
  --validate-only
```

## 结果处理

- 默认输出到当前目录的 `执行结果_YYYYMMDD/`，可用 `--output-dir` 指定。
- 最后一行 JSON 包含 `total/success/failed/output_dir`，据此汇总。
- 单条失败时继续处理后续记录，并在最终结果中列出失败原因。
- 数据库连接失败时提示用户检查当前网络/环境和本地配置，不要修改数据库配置来“碰运气”。

## 资源

- 输入模板：`templates/`
- JDBC 驱动：`lib/`
- 数据库配置：`config/`
- CLI：`scripts/query.py`
