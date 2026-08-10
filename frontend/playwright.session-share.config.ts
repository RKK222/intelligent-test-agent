import { defineConfig, devices } from "@playwright/test";

// 分享专项使用独立端口，禁止误复用其它 worktree 常驻在 3000 的开发服务。
const sessionShareE2eUrl = "http://127.0.0.1:3100";

/** 会话协作分享单独执行三种桌面浏览器，避免把既有长工作台套件无差别放大。 */
export default defineConfig({
  testDir: "./apps/agent-web/tests",
  testMatch: "workbench.spec.ts",
  grep: /session share/,
  workers: 1,
  retries: 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  webServer: {
    command: "corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3100",
    url: sessionShareE2eUrl,
    reuseExistingServer: true,
    timeout: 120_000
  },
  use: {
    baseURL: sessionShareE2eUrl,
    trace: "retain-on-failure"
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } }
  ]
});
