# AI 编码工作流

所有修改任务（Codex、Claude、人工开发）按本流程执行。本文件合并并取代原 `ai-coding-rules.md`、`task-workflow.md`。入口规范见 `AGENTS.md`。

## 1. 读文档与定位

1. 先读 `AGENTS.md` 和 `docs/README.md`，确认任务类型对应文档。
2. 后端任务读 `backend/README.md`、目标模块 `README.md`，再读 `docs/standards/backend.md` 和 `docs/architecture/dependency-rules.md`。
3. 前端任务读 `frontend/README.md`、目标 app/package `README.md`，再读 `docs/standards/frontend.md`。
4. API、事件、数据库、安全、性能任务必须读 `docs/api/`、`docs/deployment/database.md`、`docs/standards/security.md`。
5. 用 `rg`、`find` 定位相关代码，确认入口、调用链、依赖方向和测试位置；确认是否涉及 generated SDK、数据库 migration、API 或事件文档。
6. 先分析影响范围（兼容性、安全、性能、错误处理、可观测性），再开始修改；不允许在未理解边界时直接搜索替换或大范围重构。

## 2. 修改范围

1. 只改与任务直接相关的最小范围。
2. 不顺手重命名、格式化无关文件、调整无关依赖。
3. 遇到无关问题记录风险，不在当前任务扩大范围；必须扩大时在结果中说明原因和影响。
4. 保持既有包结构和模块边界，新增后端文件前先按 `docs/architecture/dependency-rules.md` 列出现有合适工程，无合适工程时按业务边界新建 Maven module。

## 3. 注释要求

1. 人工维护代码新增类、接口、方法、复杂逻辑、状态流转、边界和异常分支必须写中文注释或中文 Javadoc，说明意图、关键入参、返回语义和边界条件。
2. 注释解释业务意图和原因，不重复描述显而易见的赋值。
3. generated SDK 不手工补注释。

## 4. generated SDK

1. `backend/test-agent-opencode-sdk-generated/` 是从 opencode OpenAPI spec 生成的 Java 源码，禁止手改。
2. SDK 变更必须先运行 `tools/generate-opencode-java-sdk.sh` 重新生成，再按规范同步到后端模块。
3. 生成代码编译失败只能修 generator 配置、spec 元数据处理或依赖配置，不能直接改 generated Java。
4. 除 `test-agent-opencode-client` 外，业务模块不得直接 import `com.example.opencode.sdk.*`。

## 5. 文档同步

任何修改都必须检查是否需要同步：

- 根目录或工程 `README.md`、模块/包 `README.md`。
- `docs/api/`（HTTP 接口、SSE 事件）。
- `docs/deployment/database.md`（migration）。
- `docs/standards/`、`docs/architecture/` 相关规范。

代码、接口、配置、数据结构和行为变更不能只改实现不改文档。`requirements/` 下的历史文档不是编码依据，不在此同步范围。

## 6. 测试与构建

1. 后端默认 `mvn clean package -DskipTests`，需要 JDK 21；涉及行为逻辑时补并运行对应单元/集成测试。详细分层与命令见 `docs/standards/backend.md`。
2. 前端命令统一通过 Corepack 调用 pnpm（`corepack pnpm lint|typecheck|test|build|e2e`），详见 `docs/standards/frontend.md`。
3. API、事件、数据库、前端交互改动按对应专题测试规范执行。
4. 测试失败或未覆盖的风险必须在回复中说明。

## 7. 本地服务重启

需要重新编译并重启本地前后端联调服务时，从仓库根目录执行：

```bash
./restart-dev-services.sh
```

脚本默认使用 `local` profile、读取 `.env.local`、先编译后端和自研前端，再重启 `test-agent-app` 与 `frontend/apps/agent-web`。需要连接 `guo` 环境时显式传入 `--profile guo` 和对应 dotenv 文件。服务日志写入 `.tmp/dev-services/`，不得打印 dotenv 中的敏感值。

开发脚本变更后，必须运行轻量校验，确认根目录重启脚本在 Bash 入口和误用 `sh` 入口下都不会解析失败：

```bash
tools/verify-dev-scripts.sh
```

后端单独启动可用 `tools/dev-backend-run.sh [--profile test|guo] [--env-file <path>]`；脚本只解析 `KEY=VALUE` 行，不执行 dotenv 内容。

## 8. 自检与提交

1. 完成前按 `docs/guides/self-checklist.md` 自检，并在回复中说明验证结果。
2. 如果本次会话产生了持久价值，先更新 `.agents/session-log.md`，用 `Why / What / How / Result` 记录给后续开发者和智能体的交接信息。
3. 自检通过后提交 git，commit message 使用中文并准确概括修改。
4. `.agents/session-log.md` 属于仓库内容，应随本次提交一起保留；如需要同步到远程，随代码一并 push。
5. 只暂存本次任务相关文件，工作区无关改动保持原状。
