import { defineConfig, devices } from "@playwright/test";

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
    { name: "chromium", use: { ...devices["Desktop Chrome"] } }
  ]
});
