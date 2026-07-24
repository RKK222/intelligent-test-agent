# 完成前自检清单

每次修改结束前必须按本清单自查，并在回复中说明验证结果。

## 代码

- [ ] 只修改了与任务相关的最小范围，没有顺手重构、重命名或格式化无关文件。
- [ ] 人工维护代码的类、接口、方法、复杂逻辑、边界和异常分支已有中文注释。
- [ ] 没有手工修改 generated SDK 源码。

## 文档

- [ ] 工程 README、模块/包 README 已检查并按需更新。
- [ ] API、事件、数据库、安全、性能、测试文档已检查并按需更新。
- [ ] 文档中的模块名、包名、命令和依赖关系与代码一致。
- [ ] 未修改 `opencode-source/` 中的 OpenCode 源码、测试、配置、构建脚本、资源或临时补丁；平台适配仍位于项目自身边界内。
- [ ] 稳定文档（`docs/`、模块/包 README、`PACKAGE.md`）未使用阶段名（Phase NN）作为来源或时间注脚；阶段计划与待办只放在 `requirements/`。
- [ ] 提交前已回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md` 和各 `.agents/session-log.{id}.md`）中近期条目的变更、坑点和未完成事项，确认本次暂存内容不会覆盖或丢弃他人已提交成果。
- [ ] 若本次会话收尾时确有需要留痕的新增信息，已按会话级别更新本机对应的 `.agents/session-log.{id}.md`（`{id}` 为清洗后的 `git config user.name`），且条目包含 `Why`、`What`、`How`、`Result`；同一会话的零散改动已合并，不按文件级频繁记录；不再向已冻结的 `.agents/session-log.md` 追加。

## API 与兼容性

- [ ] 新增或修改 API 已更新 `docs/api/http-api.md`。
- [ ] 新增或修改事件已更新 `docs/api/event-stream.md`。
- [ ] DTO、事件类型、数据库字段变更已说明兼容策略。
- [ ] 错误响应仍符合统一格式。

## 数据与安全

- [ ] 数据库结构变更包含 Flyway migration，并更新 `docs/deployment/database.md`。
- [ ] Flyway migration 未写入测试、演示、个人开发或环境专属数据；此类数据已放入测试 fixture、`test-agent-test-support`、mock 数据、显式本地开发脚本或人工初始化流程。
- [ ] 新增或修改关系型数据库 SQL 已通过 MyBatis XML mapper 实现；未新增 JDBC SQL 或 MyBatis 注解 SQL。
- [ ] 没有硬编码密钥、token、账号或环境特定地址。
- [ ] 日志不输出密钥、token、个人信息或大段请求体。
- [ ] 鉴权、限流、CORS、安全响应头影响已检查。

## 性能与可观测性

- [ ] 列表查询有分页或明确上限。
- [ ] 外部调用有超时、重试边界和错误转换。
- [ ] SSE 或流式处理考虑背压、断线和重连。
- [ ] 关键链路有 traceId，未使用 `System.out.println` 作为正式日志。

## 测试

- [ ] 已按改动类型补充或确认测试。
- [ ] 已运行必要命令（后端 `mvn`、前端 `corepack pnpm`）。
- [ ] runtime 或 PTY 相关改动已运行 mock E2E；真实三服务 E2E 未运行或失败时已记录外部依赖阻塞点和日志路径。
- [ ] 测试失败或未覆盖的风险已在回复中说明。
