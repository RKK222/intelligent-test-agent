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