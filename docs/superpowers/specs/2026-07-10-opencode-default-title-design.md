# OpenCode 默认会话标题设计

## 目标

创建 OpenCode 根会话时不再传入平台根据首条消息生成的临时标题，使 OpenCode 1.17.7 内置 `title` agent 能在首条真实用户消息后生成 AI 会话标题；既有平台标题同步链路继续负责将该标题显示到 Web 页面。

## 根因

OpenCode 仅在远端会话标题匹配其默认标题格式时运行内置 `title` agent。平台当前经 `AgentRuntimeTargetResolver` 将平台 Session 的临时标题传给远端创建命令，导致 OpenCode 将其视为用户已指定标题并跳过自动命名。

## 方案

平台 Session 仍保留首条消息的临时标题，避免创建到首个远端事件之间的页面标题为空。仅在创建默认 OpenCode 根会话时，传递 `null` 作为远端标题。通用 runtime/client 命令将标题调整为可选值，SDK 网关在标题缺失时发送空 JSON 对象，而不是含有 `title` 的请求体。

远端 OpenCode 随后产生的 root `session.updated` 仍沿用既有标题同步逻辑：只在标题成功写入平台 Session 后通过 SSE 发出确认字段；child、未知或不匹配事件不影响平台标题。

## 边界与兼容性

- 不新增 HTTP API、SSE wire name、DTO、数据库 migration 或 generated SDK 改动。
- 只影响新建或重建的远端 OpenCode 根会话；既有远端会话保留其已有标题。
- 显式提供的非空标题在通用 client 链路中仍被透传，避免破坏将来的非默认调用方。

## 验证

新增定向单元测试，分别断言默认根会话创建不传标题、SDK 网关请求体为空以及显式标题保持透传。使用 JDK 25 运行受影响 Maven reactor 测试，重新启动三服务后以新建会话的真实事件确认标题改变。
