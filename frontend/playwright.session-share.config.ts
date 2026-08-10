import { defineConfig, devices } from "@playwright/test";

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
    command: "corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3000",
    url: "http://127.0.0.1:3000",
    reuseExistingServer: true,
    timeout: 120_000
  },
  use: {
    baseURL: "http://127.0.0.1:3000",
    trace: "retain-on-failure"
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } }
  ]
});
