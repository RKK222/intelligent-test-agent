# test-agent-xxl-job-admin-upstream

## 工程定位

本模块原样保存 XXL-JOB Admin 3.4.2 的 `src/main/java` 与 `src/main/resources`，构建为普通依赖 JAR，不作为独立可部署应用。平台适配、认证和启动装配全部放在 `test-agent-xxl-job-integration`，禁止直接修改本模块中的上游源码。

## 上游信息

- 项目：`xuxueli/xxl-job`
- 标签：`3.4.2`
- commit：`c2bbb46c9a3af8e2a69246728a452c606240b80e`
- 许可证：GPL-3.0，完整文本见 `LICENSE`
- 导入范围：`xxl-job-admin/src/main/java`、`xxl-job-admin/src/main/resources`

## 升级方法

1. 拉取目标正式标签并核对 commit 与许可证。
2. 整体替换本模块的两个 `src/main` 目录，不在复制过程中做平台补丁。
3. 运行目录级 `diff -qr`，确认内容与目标标签一致。
4. 在 `test-agent-xxl-job-integration` 适配上游 API 变化并执行后端、MySQL Flyway 和 iframe SSO 回归测试。
5. 更新本 README、离线版本清单和第三方许可证材料。

## 依赖边界

- 上游调用方：仅 `test-agent-xxl-job-integration` 和最终装配它的 `test-agent-app`。
- 下游依赖：Spring MVC、Freemarker、MyBatis、MySQL Driver、`xxl-job-core`、`xxl-sso-core`。
- 允许修改：本模块 POM、README、LICENSE 和上游版本元数据。
- 禁止修改：`com.xxl.job.admin` 源码与上游静态资源；平台改造必须放到 integration 模块。
