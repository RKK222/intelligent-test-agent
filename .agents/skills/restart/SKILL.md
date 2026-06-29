---
name: restart-services
description: 重启本地开发环境和服务，配置 JAVA_HOME 和 PATH
---

# 重启本地开发环境和服务

在 Mac 本地重启前后端和 opencode 等开发服务时，请使用以下命令以确保使用正确的 Java 25 / 21 版本和环境变量：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
export JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:/Users/kaka/Desktop/intelligent-test-agent/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"
./restart-dev-services.sh --profile test --env-file .env.test
```
