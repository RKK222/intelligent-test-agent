# TestAgent 公共 Agent 能力包

- 当前项目内源文件目录：`public-agent-config/`
- 首次导入来源：原公共配置包 `feature_config@6bb05a0f585ebe12cf06c72fe58224676a5ec02`
- 内容：9 个 Agent、22 个 Skill、12 个 Tool 文件

## 仓库与发布边界

本目录是 TestAgent 项目内公共 Agent、Skill、Tool 的可审计基线。后续修改应先在本目录完成评审、测试并提交，不能只修改临时解压目录或只留一个无法审查来源的 ZIP。

企业运行时仍以公共配置独立 Git 中由平台发布的固定提交为权威。将本目录提交到 TestAgent 仓库不会自动发布到企业服务器，也不会覆盖公共共享运行目录；发布时仍须通过“系统管理 → 配置管理 → opencode 公共配置管理”的个人 worktree 查看 Diff、提交并发布。

从已提交且无本目录脏改动的仓库生成交付包：

```bash
deploy/internal/package-public-agent-config.sh
```

输出为 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及其 `.sha256`。包内 `SOURCE-COMMIT` 记录生成时的 TestAgent 仓库提交，`artifacts.sha256` 锁定 BDSP 所需的预编译程序、驱动、Java class 和 Excel 模板字节。

## 导入边界

此 ZIP 是公共能力候选包，只包含 `opencode/agents/**`、`opencode/skills/**`、`opencode/tools/**`、校验清单和说明文档。没有携带 `opencode.jsonc`、`.git`、缓存、真实数据库配置、加密密钥、Cookie、Token 或固定企业服务器地址。

请在平台“公共 Agent”个人 worktree 中导入，查看 Diff，完成评审、提交和发布；不要直接覆盖企业共享运行目录。正式客户端公共能力仍以企业平台已发布的固定 Git commit 为唯一权威源。

本次仅生成并校验候选包，没有替用户在企业平台提交、发布或安装，也没有验证企业客户端的通知、确认、安装和回滚链路。

## 本次修正

1. 新增 `concise-output`，只约束最终回复的精简表达；原生 `/compact` 仍由运行时负责上下文压缩，完整工具结果和审计证据不得被省略。
2. `skill-creator` 升级到 1.2.1、`skill-optimizer` 升级到 1.1.1，明确 `Working directory` 与 Git/UI Workspace root 的差异，并为校验器增加互斥的 `--workspace-root`、`--public-config-root`。
3. 将中文目录 `安全测试` 改为 `secure-scan`，使目录名与 frontmatter `name` 一致；去除其中的固定扫描地址、Cookie 和真实报文，改为受控环境注入。
4. 为 `legacy-interface-function-asset-to-md` 补上 `metadata.agent-id: test-execution-api`；修复 AGENTS 清单中不存在的 `api-execute-case`，补齐遗漏 Agent/Skill。
5. 将 `chaos-case-generate`、`secure-case-recommend` 绑定到 `test-design-generation`，增加显式意图路由和正反评测；内部匹配结果不落盘，正式案例统一映射为四列表 Markdown。
6. `test-execution-ui` 增加 `hidden: true`，与“只允许内部编排”的约定一致。
7. 数据库、TCDS、HTTP/RPC 代理、BDSP 地址改为环境注入；BDSP 真实数据库配置和加密密钥不进入公共包，只保留示例模板。

## Agent 与 Skill 归属

| Agent | 绑定 Skill |
| --- | --- |
| `test-design-orchestrator` | `test-design` |
| `test-design-generation` | `test-design-api`、`test-design-augment`、`test-design-direct`、`test-design-equivalence`、`test-design-orthogonal`、`test-design-path`、`test-design-scenario`、`chaos-case-generate`、`secure-case-recommend` |
| `test-execution-agent` | `test-execution` |
| `test-execution-api` | `resolve-api-automation-references`、`legacy-interface-function-asset-to-md`、`generate-api-automation-markdown`、`generate-test-messages`、`validate-automation-script-format` |

以下是直接调用的独立 Skill，不需要伪造 `agent-id`：`bdsp-job-result-query`、`bdsp-job-scheduler`、`concise-output`、`secure-scan`、`skill-creator`、`skill-optimizer`。

BDSP 案例设计通过 `test-design` 的 `big-data-common-cases.md` 接入设计链：识别到 BDSP 平台化作业、作业组、单作业调度或全流程调度信号后，由 `test-design-generation` 加载 `COMMON + BDSP` 规则集合。`bdsp-job-scheduler` 和 `bdsp-job-result-query` 只负责真实调度/结果查询，不应绑定案例设计 Agent。

`stock-case-recommendation`、`test-execution-ui` 和 `whitebox-code-analyst` 通过专用 Tool 或自身流程工作，没有遗漏一个必须绑定的 Skill。

## 部署者需要注入的环境变量

- `ASSET_CASE_BASE_URL`
- `TCDS_BASE_URL`
- `TEST_AGENT_DB_SERVICE_BASE_URL`
- `TEST_AGENT_HTTP_PROXY_BASE_URL`
- `TEST_AGENT_RPC_PROXY_BASE_URL`
- `BDSP_BASE_URL`、可选 `BDSP_USERINFO`
- `SECURE_SCAN_ENDPOINT`、可选 `SECURE_SCAN_AUTH`
- `GAUSSDB_JC2_PASSWORD`、`GAUSSDB_JC4_PASSWORD`、`GAUSSDB_JC6_PASSWORD`（采用示例配置的 `env:` 方式时）

上述值必须由企业受控配置或平台连接提供，不要写回公共 Git。

## 客户端兼容状态

`opencode/tools/package.json` 锁定 `@opencode-ai/plugin 1.18.4` 和 `playwright-core 1.61.0`，包内按规则不携带原始 `node_modules`。发布给本地客户端前必须确认受控可移植运行时已经包含这两个精确依赖；未确认或不匹配时，本候选版本应标记为 `SERVER_ONLY`，不能让客户端自动安装或联网补依赖。
