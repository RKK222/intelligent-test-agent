import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

// 统一通过 import.meta.url 解析 workspace 包源码，避免硬编码绝对路径
const pkgSrc = (name: string): string =>
  fileURLToPath(new URL(`../../packages/${name}/src`, import.meta.url));
// 本地一键启动脚本会按 TEST_AGENT_FRONTEND_URL 注入 HOST，未注入时保持仅本机访问。
const devServerHost = process.env.HOST ?? "127.0.0.1";

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
      "@test-agent/shared-types": pkgSrc("shared-types"),
      "@test-agent/backend-api": pkgSrc("backend-api"),
      "@test-agent/event-stream-client": pkgSrc("event-stream-client"),
      "@test-agent/ui-kit": pkgSrc("ui-kit"),
      "@test-agent/file-explorer": pkgSrc("file-explorer"),
      "@test-agent/editor": pkgSrc("editor"),
      "@test-agent/diff-viewer": pkgSrc("diff-viewer"),
      "@test-agent/agent-chat": pkgSrc("agent-chat"),
      "@test-agent/terminal": pkgSrc("terminal"),
      "@test-agent/test-runner": pkgSrc("test-runner"),
      "@test-agent/workbench-shell": pkgSrc("workbench-shell")
    }
  },
  server: {
    host: devServerHost,
    port: 3000
  }
});
