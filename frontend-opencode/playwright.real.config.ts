import { resolve } from "node:path";
import { defineConfig, devices } from "@playwright/test";

const frontendPort = Number(process.env.FRONTEND_OPENCODE_REAL_PORT ?? "4187");
const frontendBaseUrl = `http://127.0.0.1:${frontendPort}`;
const backendTarget =
  process.env.FRONTEND_OPENCODE_REAL_API_BASE_URL ??
  process.env.VITE_TEST_AGENT_API_PROXY_TARGET ??
  process.env.TEST_AGENT_API_BASE_URL ??
  process.env.VITE_TEST_AGENT_API_BASE_URL ??
  "http://127.0.0.1:8080";

export default defineConfig({
  testDir: "./tests/e2e-real",
  testMatch: "**/*.real-spec.ts",
  timeout: Number(process.env.FRONTEND_OPENCODE_REAL_TIMEOUT_MS ?? "180000"),
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: frontendBaseUrl,
    trace: "retain-on-failure",
    video: "retain-on-failure"
  },
  webServer: {
    // 真实验收强制同源 /api 代理到 test-agent-app，避免浏览器跨域或直连 opencode server。
    command: `${env("VITE_TEST_AGENT_API_BASE_URL", "")} ${env("VITE_TEST_AGENT_API_PROXY_TARGET", backendTarget)} corepack pnpm exec vite --host 127.0.0.1 --port ${frontendPort}`,
    cwd: resolve(import.meta.dirname),
    url: frontendBaseUrl,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  },
  projects: [
    {
      name: "desktop-real",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } }
    }
  ]
});

function env(name: string, value: string) {
  return `${name}=${JSON.stringify(value)}`;
}
