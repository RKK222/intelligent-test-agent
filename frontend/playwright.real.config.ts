import { defineConfig, devices } from "@playwright/test";

const backendBaseUrl = process.env.TEST_AGENT_BASE_URL ?? "http://127.0.0.1:8080";
const frontendUrl = process.env.TEST_AGENT_FRONTEND_URL ?? "http://127.0.0.1:3000";
const frontend = new URL(frontendUrl);

export default defineConfig({
  testDir: "./apps/agent-web/tests",
  testMatch: "**/*.real-spec.ts",
  timeout: 120_000,
  webServer: {
    command: `corepack pnpm --filter @test-agent/agent-web dev --hostname ${frontend.hostname} --port ${frontend.port || "3000"}`,
    url: frontendUrl,
    reuseExistingServer: true,
    timeout: 120_000,
    env: {
      NEXT_PUBLIC_TEST_AGENT_API_BASE_URL: backendBaseUrl
    }
  },
  use: {
    baseURL: frontendUrl,
    trace: "retain-on-failure"
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }]
});
