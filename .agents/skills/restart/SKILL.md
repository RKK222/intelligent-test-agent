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
