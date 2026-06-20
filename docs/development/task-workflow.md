# 任务执行流程

所有修改任务按以下流程执行。

## 1. 读文档

1. 读 `AGENTS.md`。
2. 读 `docs/README.md`。
3. 读任务相关工程 README、PACKAGE.md 和专题规范。
4. API、事件、数据库、安全、性能任务必须读对应专题文档。

## 2. 定位代码

1. 使用 `rg`、`find`、`sed` 等工具定位相关代码。
2. 确认入口、调用链、依赖方向和测试位置。
3. 确认是否涉及 generated SDK、数据库 migration、API 文档、事件文档。

## 3. 分析影响

1. 明确最小改动范围。
2. 判断是否影响兼容性、安全、性能、错误处理、可观测性。
3. 判断需要补哪些测试。
4. 判断需要同步哪些文档。

## 4. 修改

1. 按最小范围修改。
2. 保持既有包结构和模块边界。
3. 人工代码补中文注释。
4. 不手改 generated SDK。

## 5. 测试

1. 后端默认运行 `mvn clean package -DskipTests`，需要 JDK 21。
2. 涉及行为逻辑时补并运行相应单元测试或集成测试。
3. 涉及 API、事件、数据库、前端交互时按专题测试规范执行。

## 6. 更新文档

1. 同步 README、PACKAGE.md。
2. 同步 API、事件、数据库、安全、性能或测试文档。
3. 文档必须描述行为、边界、依赖关系和验证方式。

## 7. 自检

完成前必须执行 `docs/development/ai-self-checklist.md` 的清单，并在回复中说明验证结果。

## 8. 本地服务重启

需要重新编译并重启本地前后端联调服务时，从仓库根目录执行：

```bash
./restart-dev-services.sh
```

脚本默认使用 `local` profile、读取 `.env.local`、先编译后端和自研前端，再重启 `test-agent-app` 与 `frontend/apps/agent-web`。服务日志写入 `.tmp/dev-services/`，不得打印 dotenv 中的敏感值。

开发脚本变更后，必须运行轻量校验，确认根目录重启脚本在 Bash 入口和误用 `sh` 入口下都不会解析失败：

```bash
tools/verify-dev-scripts.sh
```

## 9. Git 提交

1. 自检通过后提交 git。
2. commit message 使用中文，描述本次任务的实际修改。
3. 只提交本次任务相关变更，工作区已有无关改动必须保持原状。
