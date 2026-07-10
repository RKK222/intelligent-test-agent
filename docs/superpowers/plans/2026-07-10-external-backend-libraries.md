# External Backend Libraries Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一后端配置文件，并让企业后端从外置完整依赖目录启动。

**Architecture:** 保持 Spring Boot loader 与业务 classes 在可执行 jar 中，将 `BOOT-INF/lib` 全量移到交付包 `dist/backend/lib`。`PropertiesLauncher` 以外置目录构建 classpath，部署脚本原子替换 jar 和 lib 后再启动 Java。

**Tech Stack:** Spring Boot Maven Plugin、Bash、systemd、JUnit 5。

---

### Task 1: 配置与启动入口

- [ ] 删除非 test profile yml，并把运行配置合并到 `application.yml`。
- [ ] 收敛本地启动脚本、IDEA 配置和文档到默认/test 两种模式。

### Task 2: 外置依赖交付

- [ ] 添加打包回归测试或脚本断言，验证 lib 目录和薄 jar 结构。
- [ ] 更新 `package-release.sh` 提取 `BOOT-INF/lib` 并生成薄 jar。
- [ ] 更新后端部署脚本、systemd 示例和文档，完整替换外置 lib 并使用 `PropertiesLauncher`。

### Task 3: 验证与交付

- [ ] 运行配置绑定测试、脚本语法检查和完整企业打包。
- [ ] 检查 zip 同时包含薄 jar、完整 lib 和部署脚本。
