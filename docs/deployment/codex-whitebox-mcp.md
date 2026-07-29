# Codex 白盒分析 MCP

## 用途与边界

平台在 OpenCode worker 内固定携带官方开源 Codex CLI `0.145.0`，通过本地 stdio MCP
为应用成员提供严格只读的白盒代码分析。Codex 不直接暴露给对话模型；平台门面只提供：

- `whitebox_analyze({prompt})`：开始一次分析，返回正文和本 MCP 进程生成的 `threadId`。
- `whitebox_reply({threadId,prompt})`：在同一 MCP 生命周期内继续分析。

工作目录永远是当前 OpenCode workspace。调用方不能指定 cwd、模型、配置、沙箱、审批策略、
基础指令或开发者指令。MCP 生命周期结束后旧 `threadId` 失效。

本功能不实现应用代码库引用、挂载、同步、目录组合、ManagedWorkspace/worktree 创建或 Git
刷新，也不以这些未来能力作为启用前置条件。Codex 只能读取当前 workspace 已存在的文件；
业务源码尚未进入 workspace 时，分析结果必须明确报告源码缺失。

## 用户怎么使用

管理员发布公共配置后，用户在自己有权访问的应用对话中选择 `whitebox-code-analyst` Agent，直接描述
要分析的问题即可，例如：

```text
分析登录接口从 Controller 到数据库的调用链，列出鉴权绕过风险和证据代码位置。
```

Agent 会自动调用本地 MCP，用户不需要手写工具名、cwd 或模型参数。需要沿着同一证据继续
追问时仍在当前对话里发送问题；Agent 会使用本次 MCP 返回的 `threadId` 调用
`whitebox_reply`。worker、OpenCode 或 MCP 重启后会重新开始分析。

最终输出固定包含结论边界、证据文件和关键位置、调用链、风险与待确认项。首期不会生成、
修改或提交测试代码。

## 权限

使用条件同时包括：

1. 公共 `opencode/opencode.jsonc` 已启用 `code_analysis` MCP，并发布
   `opencode/agents/whitebox-code-analyst.md`；
2. 当前用户仍是该应用的有效成员；
3. 当前托管 workspace 属于该应用且通过既有会话/工作区鉴权。

普通应用成员、应用管理员、已加入应用的超级管理员都可使用。超级管理员不绕过应用成员
校验；未加入应用时不能取得该应用 workspace 上下文，也不能借本 MCP 分析代码。当前公共
配置会让使用该配置的所有应用都出现白盒分析 Agent，不再提供逐应用启用开关。本期不新增
`TESTER` 角色，也不修改成员、Workspace、前端、数据库或 RunEvent 鉴权模型。

## 公共配置启用

先按“Java 后端 → programs/worker → 公共配置”的顺序升级。把
`deploy/internal/codex-whitebox-public.opencode.jsonc.example` 中的 `mcp.code_analysis`
合并进公共配置仓库 `opencode/opencode.jsonc`，并按企业实际模型填写：

- `TEST_AGENT_CODEX_PROVIDER_ID`：内部模型供应商路由 ID，例如 `qwen-prod`；
- `TEST_AGENT_CODEX_MODEL`：该供应商下可靠支持 function calling 的模型；
- `TEST_AGENT_CODEX_CONTEXT_WINDOW`：模型真实上下文窗口，整数且不小于 4096；
- `timeout`：默认 `600000` 毫秒。

代理地址、代理密钥和当前用户 `ucid` 不写入应用配置。Java/manager 已为每个用户 OpenCode
进程注入 `TEST_AGENT_INTERNAL_PROXY_BASE_URL`、`TEST_AGENT_INTERNAL_PROXY_API_KEY` 和
`ENTERPRISE_UCID`，门面缺少任一项时失败关闭。

再把 `deploy/internal/whitebox-code-analyst.md` 发布为公共
`opencode/agents/whitebox-code-analyst.md`。该 Agent
默认拒绝全部工具，只放行 `code_analysis_whitebox_analyze` 和
`code_analysis_whitebox_reply`。公共配置变更沿用现有公共 Agent Git、发布、跨服务器同步和
OpenCode dispose 流程，不新增业务接口。独立完整替换包固定为
`deploy/internal/dist/test-agent-public-agents-skills.zip` 及同名 `.sha256`，包内同时携带当前
公共 `opencode.jsonc`、全部公共 Agent、Skill、Tool 和说明；导入时不得只复制白盒文件而
丢失既有公共配置。

## 只读与日志安全

worker 将管理员级策略安装为只读 `/etc/codex/requirements.toml`：审批固定 `never`，根文件
系统默认拒绝，仅允许 Codex 最小运行文件和当前 workspace 读取，临时目录、文件写入、命令
联网和 Web Search 均拒绝。Linux 精细文件系统策略由官方固定摘要的静态 bubblewrap 执行。

每个门面进程创建隔离且权限为 `0700` 的临时 `CODEX_HOME`，配置文件权限为 `0600`；
取消、超时、stdin 断开或进程终止时关闭 Codex 子进程并删除临时目录。续写只接受本进程
生成的 thread ID。

审计日志只输出随机 `traceId`、毫秒耗时、`SUCCESS/FAILED` 和稳定错误码，不输出提示词、
代码、工具参数、Token、供应商错误正文或敏感路径。官方 Codex stderr 被消费但不转发。

## 内部模型 Responses 适配

Java 新增内部端点：

```text
POST /api/internal/platform/opencode-runtime/internal-model-proxy/v1/responses
```

它继续使用现有 Bearer 代理密钥、`X-Enterprise-Model-Provider` 和 `ucid`，把 Codex
`0.145.0` 所需的流式 Responses 子集转换到既有供应商 `/chat/completions`：支持纯文本
message、function tools、`function_call`、`function_call_output`、文本增量、工具参数、usage、
完成和安全错误事件；reasoning 与加密思维链不透传。图片、文件、内置 Web 工具、非纯文本
输出和非流式请求统一返回校验错误。原 `/chat/completions` 行为不变。

## 企业 Linux / Docker 兼容基线

当前企业现场基线是 Linux `4.19`、Docker Server `18.09.7`、`x86_64`，worker 由
`opencode-worker-docker.sh` 以 `--privileged` 启动。镜像继续固定 Debian 11 bullseye / glibc
`2.31`，Codex 与 bubblewrap 都使用官方 `x86_64-unknown-linux-musl` 资产并校验归档及可执行
文件 SHA-256。目标机运行期不使用 Docker 19.03 才普及的 `docker run --platform` 参数。

Codex 精细只读策略依赖 bubblewrap 创建 user、mount、PID 和 network namespace。内核版本
只能作为基线，不能证明厂商内核、安全模块和 Docker 配置允许这些能力。因此每台 worker
节点在替换线上容器前，先加载新镜像并执行：

```bash
cd /data/0709/test-agent-internal-release/deploy/internal
./check-codex-whitebox-host.sh test-agent-opencode-worker:internal
```

脚本兼容 Docker 18.09 CLI；`18.09.7` 等带前导零的版本字段按十进制比较，基础 namespace
探针使用 worker 镜像内的 `/bin/true`。脚本失败关闭检查：Linux/x86_64、Docker 不低于 18.09、镜像
linux/amd64、glibc 2.31、Codex 0.145.0、bubblewrap 摘要，以及真实的 `rg`/源码读取成功、
文件拒写、workspace 外拒读、命令断网、Git 状态不变和会话续写。该探针用与正式 worker
相同的 `--privileged` 和 `--network none`，模型服务是容器 loopback 内的本地伪服务，
完全不访问外网。

任何一项失败都不得发布公共 MCP 配置。先保留完整输出并检查：worker 是否确由正式脚本重建为
privileged、宿主安全策略是否禁止 namespace、镜像/programs 是否来自同一批发布包。不得
通过改成危险的宽权限 Codex 配置来绕过探针。Mac Docker Desktop 的 linux/amd64 仿真只能
完成二进制、摘要、MCP 契约和失败关闭检查；Apple Silicon 仿真不能创建 Codex 所需的嵌套
namespace，构建脚本会明确显示 native sandbox E2E 已跳过。这不能替代每台 Linux 4.19 /
Docker 18.09.7 目标节点验收。

## 构建与验收

外网 Mac 使用既有命令构建完整离线包：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

`package-release.sh` 会在 worker 镜像构建后自动运行构建机检查；检查不通过则不导出发布包。
构建机检查还会构造“历史 `function_call_output` + 本轮续写提示”的输入，确认续写提示优先被
识别并保留第一轮上下文；该项不依赖 native namespace，Apple Silicon 构建机也必须通过。

OpenCode Manager、OpenCode runtime、Codex MCP、Node/MCP SDK、bubblewrap、worker 镜像和
`test-agent-programs.tar.gz` 按一个 `worker runtime` 指纹单元发布。任一运行输入变化时全部重建并
进入 ZIP；全部未变化时增量 ZIP 标记 `TEST_AGENT_RELEASE_WORKER_RUNTIME=reuse`，不再重复携带
worker tar 或 programs。目标部署在替换 Java 前必须确认
`/data/testagent/config/release-component-state.env` 中的实际安装指纹与清单一致，并确认现有
Manager、OpenCode、Codex 文件及 worker 容器健康，否则拒绝使用增量包。新装机、灾备或迁移到
本机制后的首包使用 `--include-all-components`。

`test-agent-programs.tar.gz` 与 worker 镜像同时包含 Codex、固定 bubblewrap、Apache-2.0
LICENSE/NOTICE、bubblewrap COPYING、门面和版本/摘要元数据；企业服务器不下载 npm、Codex
或沙箱依赖。构建机验证命令：

```bash
tools/verify-codex-whitebox-worker-image.sh test-agent-opencode-worker:internal
```

真实服务验收只分析当前 workspace 已存在的代码，不临时构造或依赖尚未实现的应用代码库
引用。运行分析前后执行 `git status --short`，输出必须完全一致。

## 回滚

先从公共 `opencode/opencode.jsonc` 移除/禁用 `code_analysis`，并撤下
`opencode/agents/whitebox-code-analyst.md`；等待公共配置发布、跨服务器同步和用户 OpenCode
dispose 完成后，再按同一批次
回退 Java、programs 与 worker 镜像。不涉及数据库、Flyway 或 RunEvent 回滚。
