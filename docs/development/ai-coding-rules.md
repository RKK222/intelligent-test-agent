# AI 编码红线

本文档约束 Codex、Claude 和其他 AI 在本项目中的修改行为。

## 修改前

1. 先读 `AGENTS.md`、`docs/README.md` 和任务相关文档。
2. 先定位代码、测试、README、PACKAGE.md、API 文档和迁移文件。
3. 先分析影响范围，再开始修改。
4. 不允许在没有理解边界的情况下直接搜索替换或大范围重构。

## 修改范围

1. 只改与任务直接相关的最小范围。
2. 不允许顺手重命名、顺手格式化无关文件、顺手调整无关依赖。
3. 遇到无关问题时记录风险，不在当前任务中扩大范围。
4. 如果必须扩大范围，需要在结果中说明原因和影响。

## 文档同步

任何修改都必须检查是否需要同步：

- 根目录或工程 README。
- 模块 README。
- 源码包 PACKAGE.md。
- API 文档。
- 事件流文档。
- 数据库迁移文档。
- 安全、性能、测试规范。

代码、接口、配置、数据结构和行为变更不能只改实现不改文档。

## 注释要求

1. 人工维护代码新增类、接口、方法、复杂逻辑、边界条件和异常分支必须写中文注释或中文 Javadoc。
2. 方法注释必须说明方法意图、关键入参、返回语义和边界条件。
3. 注释解释业务意图、边界和原因，不重复描述显而易见的赋值。
4. generated SDK 不手工补中文注释。

## 生成代码

1. `backend/test-agent-opencode-sdk-generated/src/main/java/com/example/opencode/sdk/**` 是 generated Java 源码。
2. 不允许手改 generated Java 源码。
3. SDK 变更必须先运行 `tools/generate-opencode-java-sdk.sh`，再按规范同步到后端模块。
4. 如果生成代码编译失败，只能修 generator 配置、spec 元数据处理或依赖配置，不能直接改 generated Java。

## Git 提交

1. 修改完成并通过必要校验后，必须将本次任务相关变更提交到 git。
2. commit message 必须使用中文，准确概括本次修改。
3. 提交前只允许暂存本次任务相关文件，不得把工作区中无关或他人已有改动带入 commit。

## 完成前

1. 按任务类型运行测试或构建。
2. 按 `docs/development/ai-self-checklist.md` 自检。
3. 输出中说明测试命令、结果和未覆盖风险。
