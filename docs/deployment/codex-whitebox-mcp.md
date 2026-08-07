# 官方 Codex MCP 企业接入

## 用途与当前边界

OpenCode worker 固定携带官方开源 Codex CLI `0.145.0`，公共配置通过本地 stdio 直接启动
官方 `codex mcp-server`，用于应用源码白盒分析。平台不再在 MCP 协议前增加工具门面。

官方服务原生公开两个工具：

- `codex`：开始一次分析，输入除 `prompt` 外还原生支持 `cwd`、`model`、`config`、
  `sandbox`、`approval-policy`、`base-instructions`、`developer-instructions` 和
  `compact-prompt`，输出正文和 `threadId`。
- `codex-reply`：使用 `threadId` 和 `prompt` 继续同一会话。

下列行为不是官方 Codex MCP 的原生要求，当前接入不再实现：工具改名或参数过滤、固定 cwd、
仅允许当前 workspace、进程内 thread ID 白名单、每进程临时 `CODEX_HOME`、stderr/结果重写、
根文件系统默认拒绝、workspace 外拒读、临时目录拒绝和命令断网。官方 MCP 进程的生命周期、
会话管理、取消与错误输出均使用 Codex 自身实现。

平台仍保留两项企业部署约束：启动器把现有内部模型代理写入官方 `config.toml`；管理员级
`/etc/codex/requirements.toml` 只允许 `approval_policy=never`，确保夜间任务不会停在授权询问。
代码分析 Agent 默认调用官方 `sandbox=read-only`，但这是官方参数，不是平台自定义文件边界。

## 应用源码与使用方式

应用代码库引用、源码物化和应用源码 Runtime Workspace 已由平台现有功能负责。Codex 不自行
clone、同步或组合仓库；运行夜间分析前，应让任务绑定到已经打开且可访问目标源码的 workspace，
并把实际源码目录作为 `cwd`。如果源码尚未物化、挂载过期或路径不可读，Agent 必须报告证据
缺口，不能假装完成分析。

管理员发布公共配置后，用户在有权访问的应用对话或夜间任务中选择
`whitebox-code-analyst` Agent，直接描述分析目标即可。例如：

```text
分析登录接口从 Controller 到数据库的调用链，列出鉴权绕过风险和证据代码位置。
```

Agent 首次调用 `code_analysis_codex`，显式传入 `approval-policy: never`，默认使用
`sandbox: read-only` 和当前任务实际源码目录；继续追问时调用
`code_analysis_codex-reply`。审批或命令失败只记录证据缺口，不发起 ask permission。

最终输出包含结论边界、证据文件和关键位置、调用链、风险与待确认项。当前 Agent 不允许调用
Codex MCP 以外的工具。

## 权限说明

公共配置让加载它的 OpenCode 进程发现该 MCP 和 Agent。用户能否打开应用、应用源码或运行
workspace，仍由平台现有应用成员、workspace/session 和源码 Runtime Workspace 鉴权决定：

- 普通应用成员、应用管理员、已加入该应用的超级管理员均可使用；
- 超级管理员不绕过应用成员校验；
- 未加入应用的超级管理员不能通过平台取得该应用 workspace；
- 本次不新增角色、前端接口、数据库、Flyway 或 RunEvent 类型。

直接使用官方 MCP 后，MCP 本身不理解平台成员关系，也不会把 `cwd` 限制在当前 workspace。
它能读取的范围取决于 worker 容器内操作系统权限与调用时的官方 sandbox 配置。因此不能把
“应用成员鉴权”描述成 Codex 文件系统隔离；共享容器中若存在不应互读的路径，应通过容器挂载、
进程隔离或调度边界解决。

## 公共 JSONC 与 Agent

按“Java 后端 → programs/worker → 公共配置”的顺序升级。当前完整
`deploy/internal/opencode.jsonc.example` 已包含 `mcp.code_analysis`，新部署直接把完整样例写入
公共配置仓库 `opencode/opencode.jsonc`；`deploy/internal/codex-whitebox-public.opencode.jsonc.example`
仅用于给已有公共配置增量合并 MCP 片段。默认企业模型为：

- `TEST_AGENT_CODEX_PROVIDER_ID=deepseek-prod`；
- `TEST_AGENT_CODEX_MODEL=DeepSeek-V4-Flash-W8A8`；
- `TEST_AGENT_CODEX_CONTEXT_WINDOW=262144`；
- MCP `timeout=600000` 毫秒。

`deepseek-prod` 是 Java 内部模型代理的供应商路由 ID，不是 OpenCode provider key
`enterprise-deepseek`。代理地址、密钥和用户 `ucid` 不写入 JSONC；继续复用 Java/manager
注入的 `TEST_AGENT_INTERNAL_PROXY_BASE_URL`、`TEST_AGENT_INTERNAL_PROXY_API_KEY` 和
`ENTERPRISE_UCID`。任一项缺失或 URL/上下文长度非法时，启动器在启动官方 MCP 前失败。
由于官方 MCP 的每个 `codex` 调用都会重新加载配置，启动器使用当前 OpenCode 用户 HOME 下
持久化的 `.testagent-codex-mcp/config.toml`，而不是仅传 `mcp-server -c`；该文件不保存 API key，
只保存模型 ID、代理 URL 和环境变量名，并由 `--strict-config` 校验。它不隔离或清理官方会话状态。

把 `deploy/internal/whitebox-code-analyst.md` 发布为公共
`opencode/agents/whitebox-code-analyst.md`。Agent 名称不包含 Codex；官方 MCP 工具经 OpenCode
组合后的名称是 `code_analysis_codex` 和 `code_analysis_codex-reply`。

公共配置变更沿用现有公共 Agent Git、发布、跨服务器同步和 OpenCode dispose 流程。完整替换包
固定为 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及同名 `.sha256`；导入时应整体
替换，避免丢失已有公共 Agent、Skill、Tool 或 JSONC 配置。

## 内部模型 Responses 适配

官方 Codex 通过内部端点调用企业模型：

```text
POST /api/internal/platform/opencode-runtime/internal-model-proxy/v1/responses
```

端点继续使用 Bearer 代理密钥、`X-Enterprise-Model-Provider` 和 `ucid`，把 Codex `0.145.0`
需要的流式 Responses 子集转换到供应商 `/chat/completions`。支持文本 message、function tools、
`function_call`、`function_call_output`、文本增量、工具参数、usage、完成和错误事件；不透传
reasoning 或加密思维链。图片、文件、内置 Web 工具、非纯文本输出和非流式请求返回校验错误。
原 `/chat/completions` 行为不变。

## 企业 Linux / Docker 兼容基线

当前现场基线为 Linux `4.19`、Docker Server `18.09.7`、`x86_64`。worker 使用 Debian 11
bullseye / glibc `2.31`，Codex 与 bubblewrap 固定为 `x86_64-unknown-linux-musl` 资产并校验
SHA-256。目标机脚本兼容 Docker 18.09，不使用 `docker run --platform`。

每台 worker 节点启用前执行：

```bash
cd /data/0709/test-agent-internal-release/deploy/internal
./check-codex-whitebox-host.sh test-agent-opencode-worker:internal
```

脚本检查 Linux/x86_64、Docker 不低于 18.09、镜像 linux/amd64、glibc 2.31、Codex 0.145.0、
bubblewrap 摘要，并用本地伪模型验证官方工具发现、指定 cwd、源码读取、官方 read-only sandbox
拒写、Git 状态不变和会话续写。它不再把 workspace 外拒读或命令断网作为验收项，因为那两项
不是官方 MCP 的固定保证。

worker 仍以 `--privileged` 和 Docker `--network none` 运行；后者是现有 worker 容器部署边界，
不等同于官方 MCP 的工具级网络策略。Apple Silicon 上的 amd64 仿真不能创建所需嵌套 namespace，
构建检查会跳过 native sandbox E2E，必须在每台企业 Linux 节点补跑宿主检查。

## 构建与验收

外网 Mac 构建完整离线包：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

`test-agent-programs.tar.gz` 与 worker 镜像同时携带 Codex、固定 bubblewrap、Apache-2.0
LICENSE/NOTICE、bubblewrap COPYING、启动器、requirements 和版本/摘要元数据；企业服务器不
下载 npm、Codex 或沙箱依赖。专项验证：

```bash
tools/verify-codex-whitebox-worker-image.sh test-agent-opencode-worker:internal
```

验证必须看到官方 `codex` / `codex-reply` 完整输入契约，并通过本地伪 Responses 服务完成读取、
官方只读拒写、Git 不变与续写。发布后再用已物化的真实应用源码执行一次夜间任务验收；任务
全程不应产生 question/permission 等待。

## 回滚

先从公共 `opencode/opencode.jsonc` 移除或禁用 `code_analysis`，并撤下
`opencode/agents/whitebox-code-analyst.md`；等待公共配置发布、跨服务器同步和用户 OpenCode
dispose 完成后，再按同一批次回退 Java、programs 与 worker 镜像。不涉及数据库或 Flyway 回滚。
