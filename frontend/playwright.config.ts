import { defineConfig, devices } from "@playwright/test";

// 独立 worktree 可覆盖端口，避免 reuseExistingServer 误连另一份源码启动的 3000 服务。
const e2ePort = Number.parseInt(process.env.TEST_AGENT_E2E_PORT ?? "3000", 10);
const e2eBaseUrl = `http://127.0.0.1:${e2ePort}`;

export default defineConfig({
  testDir: "./apps/agent-web/tests",
  testMatch: "**/*.spec.ts",
  // 当前项目没有移动端产品内容；工作台回归以桌面 Chromium 为唯一交付视口，避免把非产品视口纳入发布门槛。
  workers: 1,
  // Monaco、Mermaid 和全局提示在长套件尾部偶发出现过渡帧，失败只允许一次完整重试。
  retries: 1,
  timeout: 60_000,
  expect: {
    timeout: 10_000
  },
  webServer: {
    command: `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port ${e2ePort}`,
    url: e2eBaseUrl,
    reuseExistingServer: true,
    timeout: 120_000
  },
  use: {
    baseURL: e2eBaseUrl,
    trace: "retain-on-failure"
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } }
  ]
});
