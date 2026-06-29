import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vitest/config";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

// 统一通过 import.meta.url 解析 workspace 包源码，避免硬编码绝对路径在不同机器失效
const pkgSrc = (name: string): URL => new URL(`./packages/${name}/src`, import.meta.url);

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver({ importStyle: false })]
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: false })]
    })
  ],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: [
      "packages/*/tests/**/*.test.ts",
      "apps/*/tests/**/*.test.ts"
    ]
  },
  resolve: {
    alias: {
      "@test-agent/shared-types": fileURLToPath(pkgSrc("shared-types")),
      "@test-agent/backend-api": fileURLToPath(pkgSrc("backend-api")),
      "@test-agent/event-stream-client": fileURLToPath(pkgSrc("event-stream-client")),
      "@test-agent/ui-kit": fileURLToPath(pkgSrc("ui-kit")),
      "@test-agent/file-explorer": fileURLToPath(pkgSrc("file-explorer")),
      "@test-agent/editor": fileURLToPath(pkgSrc("editor")),
      "@test-agent/diff-viewer": fileURLToPath(pkgSrc("diff-viewer")),
      "@test-agent/agent-chat": fileURLToPath(pkgSrc("agent-chat")),
      "@test-agent/terminal": fileURLToPath(pkgSrc("terminal")),
      "@test-agent/test-runner": fileURLToPath(pkgSrc("test-runner")),
      "@test-agent/workbench-shell": fileURLToPath(pkgSrc("workbench-shell"))
    }
  }
});
