## 2026-09-16 - 后端 AAM 基址由 zfw.sdc.cs.icbc 改为 tcds-prod.sdc.icbc

### Why

- 用户要求把 `TEST_AGENT_AAM_BASE_URL` 的主机从 `http://zfw.sdc.cs.icbc` 改为 `http://tcds-prod.sdc.icbc`（不带端口）。
- 前端生产/开发环境的 AAM 登录入口早在本 `release` 已包含的远端提交 `c2775c280` 中改为
  `http://tcds-prod.sdc.icbc/aam/onlyLogin/`；当时 `aamLogin.js` 里的旧地址被有意保留为兜底常量（详见 2026-09-02 条目），
  于是出现了「前端登录在 tcds-prod、后端验真在 zfw」的跨主机不一致。本轮按用户明确要求把后端基址与前端入口统一到同一 origin。
- 该值不是构建期 `--env-file` 可覆盖的普通变量：它由 `package-two-backend-complete.sh` 的
  `normalize_backend_node_archive()` 硬编码写入节点归档的 `config/backend.env`，且打包侧与部署侧各有一处精确断言，
  必须成对修改，否则现场部署会被门禁拦下。

### What

- 打包写入与断言：`deploy/internal/package-two-backend-complete.sh`（写入值、`grep -Fxq` 断言各 1 处）。
- 部署断言：`deploy/internal/deploy-multi-backend-node.sh` 的 `require_exact_value`。
- 后端默认值与模板：`backend/test-agent-app/src/main/resources/application.yml`、
  `deploy/internal/backend.env.example`。
- 前端兜底常量与其单测：`frontend/apps/agent-web/src/utils/aamLogin.js`、`tests/aam-auth-callback.test.ts`。
  前端 `.env.production` / `.env.development` 原本已是新值，未改动；`.env.localhost` 保持 `127.0.0.1` 不变。
- 文档：`backend/README.md`、`docs/deployment/backend.md`、`docs/deployment/frontend.md`、
  `deploy/internal/README.md`、`deploy/internal/MULTI-BACKEND.md`（节点配置示例 2 处）。
- `TEST_AGENT_TCDS_BASE_URL=http://tcds-prod.sdc.icbc:9080` 及其它主机（`mimo.sdc.cs.icbc`、`ai-code.sdc.icbc`）未改动；
  AAM 与 TCDS 现在同主机、不同端口（AAM 走 80）。

### How

- 全仓扫描确认 `zfw.sdc.cs.icbc` 仅剩 3 处 gitignore 的构建产物（`frontend/apps/agent-web/dist/`、
  `deploy/internal/dist/frontend/`、`backend/test-agent-app/target/classes/`），均由构建或打包过程重新生成，未手工改写。
- 前端 `vitest run apps/agent-web/tests/aam-auth-callback.test.ts` 通过（5 tests）。
- `corepack pnpm --filter @test-agent/agent-web build` 成功；新产物中 `tcds-prod.sdc.icbc/aam/onlyLogin/` 出现 7 处，
  已无任何 `zfw.sdc.cs.icbc` 残留，证明 dist 中的旧值确实来自 `aamLogin.js` 兜底常量而非环境文件。
- 两个 shell 脚本 `bash -n` 通过；`git diff` 核对 11 个文件 13 处改动全部为本次地址替换。
- 复核 `replace_or_append_env_value` 用 `awk 'index($0, key "=") == 1'` 按行首精确匹配整行替换，
  不会误伤同为 `tcds-prod.sdc.icbc` 主机的 TCDS 行。

### Result

- 代码、脚本、节点配置模板与文档中的 AAM 基址已统一为 `http://tcds-prod.sdc.icbc`，打包与部署两侧断言值一致。
- 现场部署时会由 `backup_and_install` 用包内新 `config/backend.env` 覆盖 `/data/testagent/config/backend.env`
  （旧文件按既有流程备份），因此现场 Java 下次升级后即改为访问新 origin。
- **未验证项**：没有真实连通性验证 `http://tcds-prod.sdc.icbc` 的 80 端口确实提供 AAM 的 `/aam/checkLogin`。
  若该验真接口只存在于 `zfw` 而 `tcds-prod` 80 端口只提供登录页，登录会返回 503（`EXTERNAL_API_UNAVAILABLE`），
  现场升级前需与 AAM 侧确认。
- 本次不新增部署节点，不涉及 HTTP API、DTO、RunEvent/SSE、数据库、SQL、Flyway、generated SDK 或 OpenCode 只读源码；
  未修改 `.env.local`。
