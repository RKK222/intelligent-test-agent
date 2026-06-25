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

session和配置存储路径
session存储
/data/opencode/.session/{port}
启动参数：
XDG_DATA_HOME=/data/opencode/session/{port} 

公共配置（agent，插件，skill等）
/data/opencode/.config/opencode/
目录下的内容有哪些，参考opencode官方文档。
启动参数：
OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/

https://opencode.ai/docs/cli/
1、进行中和历史会话从数据库中查标题和列表，不直接根据opencode session从opencode查。
2、一个会话中，每次的用户输入和opencode server的输出需要在这次对话的输出完成后持久化（确认是否是持久化到session_messages中），同时需要增加每次对话的token消耗的持久化。
3、查询以完成的会话时，优先根据opencode session调用opencode接口恢复会话消息，如果用户的opencode 进程不存在，则从数据库中查询并展示。
4、优化前端的发送按钮展示逻辑，如果是运行中的状态，则展示为终止按钮，点击以后可以立刻停止输出，并做持久化。
5、新增run过程中的会话恢复机制，当run还在执行但前端关闭或者刷新是，再次进入会话，如果还是run中，则需要支持查询到历史信息并且仍保证增量输出。由于后端是分布式部署，前端不一定会连接到原来的后端恢复sse输出。建议的方案：前端重新连接后，发送恢复sse的请求，后端负责找到当前sse在哪里，比如sse在B服务器，前端连到了A服务器，则通过A服务器找到B服务器，并在AB之间建立sse。这样来实现后续增量消息的输出，存量消息则从opencode session中查询，然后在前端同步展示增量和存量。

