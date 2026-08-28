---
name: jenkins-test-environment-deploy
description: Use whenever this repository must be deployed or accepted on the shared 192.168.8.100 test environment. Deploy the committed release branch through Jenkins, never by directly replacing remote artifacts, and report the test URL after success.
---

# Jenkins 测试环境部署

## 适用范围

用户要求部署、更新或验收本项目的共享测试环境时使用本技能，包括“部署测试环境”“发布到 100”“远端端到端验收”和“部署后给地址”。本技能不适用于开发者 Mac 本地启动，也不替代企业离线发布。

测试环境固定入口：

- Jenkins：`http://192.168.8.100:18081`，任务 `intelligent-test-agent-release`
- Web 测试环境：`http://192.168.8.100:3000`
- 后端健康检查：`http://192.168.8.100:18082/actuator/health/readiness`

`18081` 是 Jenkins 管理页，`18082` 才是 Jenkins 发布的 Java 后端。以仓库 `deploy/local/README.md` 和根 `Jenkinsfile` 为运行事实源；端口或任务配置发生变化时先读取两者，不凭历史记录猜测。

## 强制发布路径

1. 目标分支固定为 `release`。先完成与任务相关的测试、自检和提交，确认没有夹带工作区中的无关修改。
2. Jenkins 从远端 `release` 检出源码；触发前必须确认目标提交已经进入远端 `release`。只有当前用户请求授权了发布及其必要的推送时才执行外部写操作，否则说明阻塞并请求授权。
3. 在 Jenkins 任务中使用 `ACTION=DEPLOY`。只有用户明确要求回滚时才使用 `ROLLBACK` 和既有不可变 release 标签。
4. 等待该次构建结束并核对最终状态、目标 commit 和不可变 `release-{BUILD_NUMBER}-{commit前8位}` 标签。构建未达到 `SUCCESS` 时不得称为已部署。
5. Jenkins 成功后至少验证后端 readiness、Web 首页 HTTP 200 和 Jenkins 管理的前后端容器状态，再执行本次功能对应的真实浏览器或 API 验收。

禁止把以下操作作为 Jenkins 的替代方案：

- 通过 SSH/SCP 直接覆盖测试机 JAR、前端源码、静态资源或运行配置。
- 在测试机工作树直接构建、执行 `restart-dev-services.sh` 或手工重启 Java/Vite。
- Jenkins 失败后绕过数据库门禁，手工执行 Compose、Flyway `repair/outOfOrder` 或修改历史表。
- 用本机 `8080/3000` 的成功冒充共享测试环境已经部署。

只读 SSH 检查可以用于定位 Jenkins 失败，但修复后仍必须重新走 Jenkins，不能把手工恢复的进程当作本次发布结果。

## 验收与交付

部署成功后的回复必须以可访问地址开头，并至少给出：

- Web 测试环境：`http://192.168.8.100:3000`
- Jenkins 本次构建地址
- 已部署的 commit 与不可变 release 标签
- 后端 readiness、Web HTTP 和本次业务验收结果

如果 Jenkins 或业务验收失败，明确写“测试环境未部署完成”或“部署成功但业务验收失败”，给出失败阶段、构建地址和下一步；不能只提供测试地址造成已经可验收的误解。
