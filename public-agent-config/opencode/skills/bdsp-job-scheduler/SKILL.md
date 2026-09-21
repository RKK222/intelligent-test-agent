---
name: bdsp-job-scheduler
description: 调度 BDSP 大数据作业组或单作业。支持统一认证号、JC2/JC4/JC6 环境、应用、作业组、作业名、调度日期的手工参数或 Excel 批量输入，并通过随技能提供的授权捕获组件调用调度 API。用户提出“作业组调度、调度作业组、触发作业组、作业调度、单作业调度、作业发报”等需求时使用。
compatibility: opencode 1.18.4+
metadata:
  domain: bdsp-testing
  language: zh-CN
  runtime: python3.8+-go-binary
  station-version: "2026-09-18"
---

# BDSP 作业调度

## 能力

- 调度作业组：统一认证号 + 调度环境 + 调度应用 + 作业组名 + 调度日期。
- 调度单作业：在上述参数基础上增加作业名。
- 支持直接参数和 `.xls/.xlsx` 批量文件。
- `JC2/JC4/JC6` 自动转换为 `BDSP_JC2/BDSP_JC4/BDSP_JC6`。
- 默认通过 `station/` 中的授权捕获程序获取 Authorization；也可由 `BDSP_AUTH_TOKEN` 环境变量或 `--auth-file` 提供。
- 授权捕获组件支持 macOS、Linux amd64/arm64、Windows 32/64 位，并包含新版浏览器兼容逻辑。
- 授权目标页必须由部署者在 `station/config.txt` 配置；公共包不内置企业服务器地址。
- 调度 API 基础地址通过 `BDSP_BASE_URL` 注入；如接口要求额外 Userinfo 头，通过 `BDSP_USERINFO` 安全注入。
- 授权捕获会持续更新 `queryUserInfo` 请求中的 Authorization，以 5 秒静默后的最后一次有效值继续调度。
- Linux 奇安信浏览器场景使用 PAC，仅将目标 API 请求送入本地捕获代理，其余页面资源直连；抓取完成后关闭代理浏览器，不再自动重开页面。

## 使用原则

1. 这是会触发真实作业执行的外部动作。只有当用户明确要求“执行/调度/触发”时才加 `--execute`；如果用户只是询问方法、校验参数或希望预览，运行不带 `--execute` 的命令。
2. 缺少必要参数时先向用户补齐；不要猜统一认证号、应用、作业组、作业名或日期。
3. 不要把 Authorization、Userinfo 或其他认证信息打印到回复里；OpenCode CLI 包装层会静默授权捕获程序的终端输出，并在读取后清理 `result.txt` / `capture-proxy.log`。
4. 批量文件优先直接使用用户给出的文件路径，不要复制或改写原文件。
5. `station/config.txt` 控制授权时打开的目标页面。首次部署必须由管理员按授权范围填写；除非用户明确要求切换环境/页面，否则不要修改。
6. 不要把 `.browser-profile`、浏览器缓存、Cookie、历史记录等运行态目录加入技能包。

## 首次准备

运行环境建议 Python 3.8+。在本技能目录执行：

```bash
python3 -m pip install -r requirements.txt
```

`station/` 已包含预编译授权捕获程序，正常使用不要求安装 Go。只有需要重新编译授权捕获组件时才需要 Go；Windows 兼容构建脚本位于 `station/build-windows.sh`。

## 调度作业组

向用户收集参数时，请给出以下示例帮助用户理解：

> 请提供以下参数：
> - **统一认证号**：例如 `1000012345`
> - **调度环境**：`JC2` / `JC4` / `JC6` 三选一
> - **调度应用**：例如 `F-BDSP`
> - **作业组名**：例如 `DWD_ODS_DATA_SYNC`
> - **调度日期**：格式 `YYYYMMDD`，例如 `20260918`

预览（仅校验参数，不发送请求）：

```bash
python3 scripts/schedule.py group \
  --user-id "1000012345" \
  --env "JC2" \
  --app "F-BDSP" \
  --group "DWD_ODS_DATA_SYNC" \
  --date "20260918"
```

用户明确要求执行后：

```bash
python3 scripts/schedule.py group \
  --user-id "1000012345" \
  --env "JC2" \
  --app "F-BDSP" \
  --group "DWD_ODS_DATA_SYNC" \
  --date "20260918" \
  --execute
```

Excel 批量执行：

```bash
python3 scripts/schedule.py group --input "/absolute/path/input.xls" --execute
```

Excel 必须包含：`统一认证号`、`调度环境`、`调度应用`、`作业组名`、`调度日期`。

## 调度单作业

向用户收集参数时，请给出以下示例帮助用户理解：

> 请提供以下参数（在作业组调度基础上增加作业名）：
> - **统一认证号**：例如 `1000012345`
> - **调度环境**：`JC2` / `JC4` / `JC6` 三选一
> - **调度应用**：例如 `F-BDSP`
> - **作业组名**：例如 `DWD_ODS_DATA_SYNC`
> - **作业名**：例如 `DWD_ODS_USER_INFO`
> - **调度日期**：格式 `YYYYMMDD`，例如 `20260918`

```bash
python3 scripts/schedule.py job \
  --user-id "1000012345" \
  --env "JC2" \
  --app "F-BDSP" \
  --group "DWD_ODS_DATA_SYNC" \
  --job "DWD_ODS_USER_INFO" \
  --date "20260918" \
  --execute
```

批量文件必须包含：`统一认证号`、`调度环境`、`调度应用`、`作业组名`、`作业名`、`调度日期`。

## 授权捕获说明

正常情况下不直接运行 `station/`，由 `scripts/schedule.py` 在真正执行调度时自动选择当前系统/CPU 对应的程序并启动浏览器。

新版 `station` 的目标页来自：

```text
station/config.txt
```

公共包只提供示例占位地址；部署者必须先在 `station/config.txt` 写入已授权目标。捕获程序匹配 `queryUserInfo` 请求并获取 Authorization；多次请求时持续覆盖，静默 5 秒后以最后一次捕获值结束。Linux 奇安信浏览器会使用 PAC 仅代理目标 API，避免全局代理影响页面资源加载。抓取完成后关闭代理浏览器；CLI 读取令牌后会清理临时敏感文件。

如授权捕获需要人工排障，可根据系统使用：

```text
station/start-mac.command
station/start-linux.sh
station/start-windows.bat
```

直接运行这些脚本属于排障方式，可能在本地终端/日志中显示 Authorization，不要把其输出粘贴到对话或提交到版本库。

## 结果处理

- 命令输出最后一行是 JSON 汇总，可据此判断 `success/failed`。
- 若授权捕获失败，提示用户检查内网、浏览器、代理或目标页面配置；不要伪造 token。
- 若 API 返回失败，原样概括错误码和错误信息，不要自动修改业务参数重试。
- 批量任务中单条失败不应掩盖其他记录结果，最终汇总成功/失败数量。

## 资源

- Excel 模板：`templates/`
- 请求模板：`config/`
- 授权捕获程序、源码与目标页配置：`station/`
- CLI：`scripts/schedule.py`
