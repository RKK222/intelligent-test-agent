# 后端统一配置与外置依赖设计

## 目标

后端只保留 `application.yml` 和 `application-test.yml`；企业交付将 Spring Boot 可执行包的 `BOOT-INF/lib` 全量展开为外置 `lib/` 目录，运行时从该目录加载依赖。

## 配置

- `application.yml` 承担默认、本地和生产配置，所有环境差异通过 dotenv 或系统环境变量提供。
- `application-test.yml` 保留测试环境的专用数据库与运行期覆盖。
- 删除 `application-prod.yml`、`application-local.yml`、`application-local-h2.yml`、`application-guo.yml`；启动脚本只保留默认和 `test` 两种模式。

## 交付与启动

打包机仍构建 Spring Boot jar，但会把其 `BOOT-INF/lib/*.jar` 提取到 `dist/backend/lib/`，随后生成去除内置依赖的薄 jar。企业后端安装目录固定为：

```text
/data/testagent/dist/backend/test-agent-app.jar
/data/testagent/dist/backend/lib/*.jar
```

systemd 通过 `PropertiesLauncher` 和 `-Dloader.path=/data/testagent/dist/backend/lib` 启动。部署脚本先完整替换并备份 `lib/`，再启动 Java；Java 健康、`.serverid`、`.serverhost` 正常后再重启 worker。

## 边界

- 不修改业务 Java 逻辑、数据库结构、HTTP API 或事件协议。
- JDBC 驱动替换仍必须重启 Java；不同数据库类型还要另行验证 Flyway 和 SQL 方言。
- 外置库目录必须与薄 jar 同版本交付，禁止只升级其中任一方。
