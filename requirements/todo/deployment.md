1、部署架构
web->F5->2台Nginx部署前端->2台后端linux服务器（后端jar包部署在服务器上运行，一个容器中启动多个opencode进程，一个守护进程，服务器磁盘映射到容器，每个人对应一个opencode进程）->一台数据库,一个minio（管理opencode的公共config）,1个git服务器

linux服务器上的磁盘目录
1、工作空间文件
data/opencode/workspace
├── appworkspace/              
│   ├── 20260707/
│   │   └── repo1/                         # 对应代码库名称: gcms/gcms
│   │       ├── .git/                      # 对应 Git 分支: feature_testagent_20260707
│   │       ├── F-GCMS/                    # 应用名称
│   │       │   └── workspace1/            # 工作区
│   │       └── F-FMBM/
│   │           └── workspace/
│   └── 20260816/
│       └── repo2/
│           ├── .git/
│           └── F-ATP/
│               └── workspace/
└── personalworktree/
    └── 20260707/
        └── 000857009/                      # 用户统一认证号
            └── repo1/                      # 对应代码库名称
              └── personalworkspacename/  # 对应代码库名称
                  └── .git/               # 对应 git worktree 分支: 
                  └── feature_testagent_20260707_000857009_私人空间名称
                  

2、session存储(存储用户级的会话)
/data/opencode/.session/{port}
启动参数：
XDG_DATA_HOME=/data/opencode/session/{port} 

3、公共配置（agent，插件，skill等）
/data/opencode/.config/opencode/
目录下的内容有哪些，参考opencode官方文档。
启动参数：
OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/


4、项目配置（agent，skill等）
opencode.jsonc 管结构化配置，AGENTS.md 管项目规则，.opencode/ 管项目级 commands / agents / plugins / tools / skills / themes。

/project-a/
├─ opencode.jsonc
├─ AGENTS.md
├─ .ignore
└─ .opencode/
   ├─ commands/
   │  ├─ test.md
   │  └─ review.md
   ├─ agents/
   │  ├─ reviewer.md
   │  └─ tester.md
   ├─ plugins/
   │  └─ my-plugin.ts
   ├─ tools/
   │  └─ database.ts
   ├─ skills/
   │  └─ api-debugging/
   │     └─ SKILL.md
   ├─ themes/
   │  └─ team-theme.json
   ├─ modes/
   │  └─ debug.md
   └─ package.json

5、配置通过守护进程重启opencode进程生效，不重启容器。


后端部署架构为后端有多个Linux服务器，每个服务器上启动1个后端java进程和多个docker容器，容器中启动多个opencode server进程，每个opencode server进程服务于一个用户。config、session、workspace都在linux服务器中持久化，通过挂载模式映射到docker容器中。我需要实现如下功能

1、用户进程分配和检测：用户登录时，如果还没有分配进程，自动选取进程数最少的一个容器启动进程，将用户与linux服务器和端口绑定，后续用户的进程都需要在这台服务器上（因为session持久化在linux服务器上）。如果已分配进程，做一次进程health检测，如果health异常，则在原有linux服务器的任一一个进程最少的容器中启动新的进程。
2、每个容器中有一个管理进程，用于管理容器中的opencode server进程，包括进程的启动，重启，停止，健康检测等。后端可以发送指令给管理进程进行opencode进程的操作。各opencode server进程的状态持久化到数据库中。管理进程与后端java进程通过socket交互，由于有负载均衡，每个管理进程需要与所有的后端java进程建立连接。扩展后端java进程时，各管理节点自动与其建立新的连接。
3、前端增加一个管理菜单，只有超级管理员可见，用于展示有后端进程，管理进程，opencode server进程的运行情况。
4、管理进程独立一个文件夹，与backend评级，可以另选合适的语言和技术栈。

# session和配置存储路径
session存储
/data/opencode/.session/{port}
启动参数：
XDG_DATA_HOME=/data/opencode/session/{port} 
官方 CLI 文档列出的环境变量包括 OPENCODE_CONFIG、OPENCODE_TUI_CONFIG、OPENCODE_CONFIG_DIR、OPENCODE_CONFIG_CONTENT 等，没有列出 XDG_DATA_HOME 作为 opencode 专用变量。 官方配置文档里，专门用于自定义配置文件/配置目录的是 OPENCODE_CONFIG 和 OPENCODE_CONFIG_DIR，不是 session 数据目录。

不过从 opencode 源码看，它确实使用 xdg-basedir，并把 Global.Path.data 设置成 path.join(xdgData, "opencode")；session/storage 又建在 Global.Path.data/storage 下面。也就是说，设置 XDG_DATA_HOME=/some/base 后，opencode 数据大概率会落到：

/some/base/opencode/storage

# 公共配置（agent，插件，skill等）
/data/opencode/.config/opencode/
目录下的内容有哪些，参考opencode官方文档：https://opencode.ai/docs/config/
启动参数：
OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/

https://opencode.ai/docs/cli/




1、进行中和历史会话从数据库中查标题和列表，不直接根据opencode session从opencode查。
2、一个会话中，每次的用户输入和opencode server的输出需要在这次对话的输出完成后持久化（确认是否是持久化到session_messages中），同时需要增加每次对话的token消耗的持久化。
3、查询以完成的会话时，优先根据opencode session调用opencode接口恢复会话消息，如果用户的opencode 进程不存在，则从数据库中查询并展示。
4、优化前端的发送按钮展示逻辑，如果是运行中的状态，则展示为终止按钮，点击以后可以立刻停止输出，并做持久化。
5、新增run过程中的会话恢复机制，当run还在执行但前端关闭或者刷新是，再次进入会话，如果还是run中，则需要支持查询到历史信息并且仍保证增量输出。最终用的是Redis 广播模式，但是不影响现有的正常前端交互，只有会话恢复才进入redis模式。

# 会话持久化与分布式 SSE 恢复优化计划

## Summary

- 会话列表和标题继续只查平台数据库，不再依赖 opencode session 列表。
- `session_messages` 从“只存用户文本”升级为“每次 Run 完成后保存用户输入、assistant 输出快照、message parts、token/cost”；token 粒度按每次 Run。
- 查询会话消息时优先从 opencode session 拉取最新投影并回写数据库；opencode 不可用时从数据库 fallback 展示。
- Run 执行中刷新/重进页面时，前端先加载存量消息，再订阅 active run 的 SSE；多实例增量输出通过 Redis pub/sub fan-out 到任意后端实例。
- 发送按钮在运行中切换为停止按钮，点击后调用平台 Run cancel，立即停止本地输出订阅，并触发后端最终快照持久化。

## Key Changes

- 数据库与领域模型：
  - 新增 Flyway migration 扩展 `session_messages`：`run_id`、`agent_id`、`remote_message_id`、`parts_json`、`tokens_input/output/reasoning/cache_read/cache_write`、`cost_usd`、`updated_at`，并增加 `session_id + run_id`、`session_id + remote_message_id` 查询索引。
  - 扩展 `runs` 保存同一套 token/cost 字段，便于按 Run 查询每次对话消耗。
  - 扩展 `SessionMessage`、`Run`、Repository 和 JDBC 映射，保持旧 `content` 文本字段作为兼容 fallback。

- 后端业务/API：
  - `RunApplicationService` 在 Run 终态、失败、取消后调用消息快照持久化服务：优先拉取 agent projected messages，失败时使用本进程内最后收到的 message projection fallback。
  - `GET /api/sessions/{sessionId}/messages` 行为改为：有 agent binding 时先查 opencode projected messages 并 upsert 到数据库；opencode 不可用/超时/进程不存在时返回数据库快照。
  - 新增 `GET /api/sessions/{sessionId}/active-run`，返回当前最新非终态 Run 或 `null`，供刷新后恢复订阅。
  - `SessionMessageResponse` 增加可选 `runId`、`remoteMessageId`、`parts`、`tokens`、`costUsd`、`updatedAt`；`RunResponse` 增加可选 `tokens`、`costUsd`。旧前端可忽略新增字段。

- 分布式 SSE：
  - 保留当前 DB replay、`Last-Event-ID`、本机 `RunEventLiveBus` 行为。
  - 新增可选 Redis RunEvent 广播：发布 durable/transient `RunEventSsePayload` 到 `test-agent:run-events` channel，消息带 `originInstanceId`，本机收到自己消息时忽略，其他实例转发给本机 SSE 客户端。
  - Redis 不可用或 `TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED=false` 时自动降级为现有单机 live bus + DB polling，不影响当前正常 SSE。

- 前端：
  - `shared-types` 增加 message/run token 字段；`backend-api` 增加 `getActiveRun(sessionId)`。
  - `frontend-opencode` 的 session store 在 `load()` 时并行加载 session、messages、activeRun；activeRun 非终态时自动订阅 RunEvent SSE。
  - timeline 按 `messageId` 合并数据库消息和 SSE projection，projection 覆盖同 ID 的旧快照，避免刷新恢复后重复显示。
  - `PromptComposer` 运行中把发送按钮切换为停止按钮；停止调用 `cancelRun(activeRun.runId)`，立即关闭本地 EventSource 并把 activeRun 标记为 cancelling/cancelled，响应返回后重新加载消息快照。


## Assumptions

- token 统计口径按每次 Run；消息和 Run 都保存同一份 usage，缺失字段保持 `null`。
- Redis 广播是补充实时通道，不替代数据库持久化和 `Last-Event-ID` replay。
- 不手改 generated SDK；只通过现有 opencode client facade 投影字段。
- 实施时需同步更新 `docs/api/http-api.md`、`docs/api/event-stream.md`、`docs/deployment/database.md`、相关模块 README/PACKAGE，以及 `frontend-opencode/docs/api-mapping.md`。
- 实施完成后按项目要求不新建分支，并用中文 commit message 自动提交本次相关改动。
