---
name: restart-services
description: 重启本地开发环境和服务，配置 JAVA_HOME 和 PATH
---

# 重启本地开发环境和服务

在 Mac 本地重启前后端和 opencode 等开发服务时，**必须先覆盖当前 shell 继承的 Java 配置**，再调用项目脚本。不得直接运行 `./restart-dev-services.sh` 后等构建失败再补 `JAVA_HOME`，也不得信任调用前的 `java -version` 或已有 `JAVA_HOME`。

固定使用以下同一个 shell 命令块：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
export JAVA_VERSION=25
export JAVA_HOME=$(/usr/libexec/java_home -v "$JAVA_VERSION")
export PATH="$JAVA_HOME/bin:/Users/kaka/Desktop/intelligent-test-agent/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"
"$JAVA_HOME/bin/java" -version
./restart-dev-services.sh --profile test --env-file .env.test
```

如果本机没有 JDK 25，才允许把 `JAVA_VERSION` 改成 `21`；解析不到对应 JDK 时停止并报告，不要尝试用 Java 17 启动。即使使用 `--skip-backend-build`，也必须执行同一段 Java 初始化，因为后端运行进程同样需要兼容 JDK。

## 固定验收环境与服务器归属预检

- 默认验收固定使用根目录 `.env.test` 中的 `192.168.8.100:15432/testagent_dev` PostgreSQL。除非用户明确要求，不切本机 PostgreSQL、`.env.local` 或临时 dotenv。
- `.env.test` 的 `TEST_AGENT_OPENCODE_BASE_URL` 指向远端时，脚本会按设计跳过本机 opencode-manager；不要把该提示当成重启失败，也不要为消除提示强制启动本机 manager。
- 进入对话前同时检查：所选工作区与当前用户 ACTIVE Agent binding 的 `linuxServerId` 一致；目标服务器实际存在并可读取工作区根目录。同一个共享验收账号不得交替绑定本机和远端 Agent，确需双端验证时使用不同专用账号。
- 出现“工作空间与 agent 不在同一服务器”后，检查个人工作区迁移记录与目标物理路径。连续 `RETRY_WAIT` 说明存在持久冲突或鉴权问题，不能只重启或继续等待；也不能只改 `workspaces.linux_server_id` 制造不存在的远端目录。

## 从独立 worktree 启动并复用主工作区测试数据

从 `/Users/kaka/Desktop/intelligent-test-agent-notification-center` 等独立 worktree 启动、但仍需复用主工作区已经初始化的 OpenCode session、公共 Agent 配置和稳定服务器身份时，必须同时显式设置 `TESTAGENT` 与 `SYS_DATA_ROOT_DIR`：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent-notification-center
export JAVA_VERSION=25
export JAVA_HOME=$(/usr/libexec/java_home -v "$JAVA_VERSION")
export PATH="$JAVA_HOME/bin:$PWD/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"
export TESTAGENT=/Users/kaka/Desktop/intelligent-test-agent
export SYS_DATA_ROOT_DIR="$TESTAGENT/.testagent"
"$JAVA_HOME/bin/java" -version
./restart-dev-services.sh --profile test \
  --env-file /Users/kaka/Desktop/intelligent-test-agent/.env.test \
  --without-workflow
```

`TEST_AGENT_ROOT` 不要改成主工作区，继续由脚本默认设为当前 worktree，保证代码、构建产物和服务日志来自当前分支。必须同时设置上述两个兼容变量：本机历史测试库中的 macOS `SYS_DATA_ROOT_DIR` 参数值是 `$TESTAGENT/.testagent`，Java 通用参数展开读取 `TESTAGENT`；启动脚本和 manager 身份文件使用 `SYS_DATA_ROOT_DIR`。只设置后者会让 Java 仍解析到当前 worktree 的空数据目录，初始化进程时报“公共 Agent 配置源目录不可用”。

只有 `.env.test` 确认未配置 `WORKFLOW_DEV_REDIS_PASSWORD`、且当前任务不需要 Workflow 时才使用 `--without-workflow`；否则移除该参数并按默认流程启动 Workflow。跳过构建时可追加 `--skip-backend-build --skip-frontend-build`，但只能复用已验证的当前 worktree 产物。
