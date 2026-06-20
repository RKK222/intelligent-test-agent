import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./apps/agent-web/tests",
  testMatch: "**/*.spec.ts",
  timeout: 30_000,
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
    { name: "mobile", use: { ...devices["Pixel 7"] } }
  ]
});
