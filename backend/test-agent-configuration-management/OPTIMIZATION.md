# Git 远端目录树查询说明

## 目标

配置管理需要在不创建业务工作区的前提下读取远程分支目录。实现必须同时满足：

- 使用当前登录用户的 Git 身份和 SSH key，让 SCM 权限变更立即参与鉴权；
- 设置页创建工作空间时看到远程分支最新提交，不能继续展示一小时内的旧目录；
- 其它只读引用场景允许复用临时 Git 元数据，避免无意义的重复传输；
- 不 checkout 业务文件，不写应用工作空间配置，不启动 Session 或 Run。

## 当前实现

`GitCloneCacheService` 使用以下命令读取目录树：

```bash
git init <cache-dir>
git -C <cache-dir> remote add origin <git-url>
git -C <cache-dir> fetch origin <branch> --depth=1
git -C <cache-dir> ls-tree -r -t FETCH_HEAD
```

`--depth=1` 只保留目标分支最新提交历史，`ls-tree` 直接读取 Git 对象，不创建工作目录 checkout。实际对象传输量由远端 Git 能力和目标提交内容决定，不能把浅层 fetch 描述为必然“不下载 blob”。

临时目录默认位于：

```text
/tmp/git-clone-cache/{effectiveGitUrlSha256前16位}_{branch}
```

内部版本库的有效 URL 包含当前用户统一认证号，因此不同内部 SCM 用户不会共用同一个缓存键。缓存中额外记录完整有效 URL，用于防止摘要碰撞误读其它仓库。

## 新鲜度规则

| 调用场景 | 方法 | 规则 |
|---|---|---|
| 设置页“工作空间管理”目录树 | `refreshTree()` | 每次请求都重新 fetch 所选远程分支，再读取新的 `FETCH_HEAD`。 |
| 自动化引用等普通只读树查询 | `listTree()` | 缓存未过期且 URL 一致时读取现有 `FETCH_HEAD`，否则重新 fetch。 |
| 兼容目录列表 | `listDirectories()` | 与普通缓存查询一致。 |

默认缓存有效期由 `TEST_AGENT_GIT_CACHE_EXPIRY` 控制，当前默认 `1h`；该有效期不再影响设置页工作空间目录树的新鲜度。SCM 权限变化不会主动删除磁盘缓存，但设置页下一次请求会以当前用户 SSH key 重新 fetch，因此无需等待缓存过期或手工清理目录。

同一有效 URL 和分支的 fetch 使用进程内锁串行化，避免同一 Java 进程并发重建相同缓存目录。多 Java 节点各自维护临时缓存，设置页请求无论落到哪台节点都会重新 fetch。

## 错误与安全边界

- Git 命令关闭交互式凭据提示，并受 `command-timeout` 限制。
- SSH 私钥只写入短生命周期、仅 owner 可读写的临时文件，命令结束后删除。
- fetch 失败时删除本次不完整缓存并返回统一 Git 错误，不回退旧目录掩盖权限或远端异常。
- 日志和错误不得输出 SSH 私钥、token、Cookie 或 Authorization。
- 不要通过重启服务或手工删除 `/tmp/git-clone-cache` 解决设置页旧目录；设置页查询本身应完成最新分支刷新。

## 验证

后端回归测试会先建立一个本地 Git 仓库并生成有效缓存，再提交新目录：普通 `listTree()` 仍返回缓存内容，`refreshTree()` 必须返回新提交目录。

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl test-agent-configuration-management -am \
  -Dtest=GitCloneCacheServiceTest,ConfigurationManagementApplicationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

前端回归测试验证设置页保存自定义工作空间别名后，输入框不会重新变为 `ai-test`：

```bash
cd frontend
corepack pnpm vitest run apps/agent-web/tests/settings-app-workspace-panel.test.ts
```
