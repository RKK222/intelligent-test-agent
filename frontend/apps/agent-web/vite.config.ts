import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, type Plugin } from "vite";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

// 统一通过 import.meta.url 解析 workspace 包源码，避免硬编码绝对路径
const pkgSrc = (name: string): string =>
  fileURLToPath(new URL(`../../packages/${name}/src`, import.meta.url));
// 本地一键启动脚本会按 TEST_AGENT_FRONTEND_URL 注入 HOST，未注入时保持仅本机访问。
const devServerHost = process.env.HOST ?? "127.0.0.1";

/**
 * Vite 的 SPA fallback 会优先接管目录 URL；显式改写手册首页，保证开发与预览环境点击手册 Logo 后仍留在手册内。
 */
const manualIndexRoute = (): Plugin => ({
  name: "test-agent-manual-index-route",
  configureServer(server) {
    server.middlewares.use((request, _response, next) => {
      if (request.url?.split("?", 1)[0] === "/help/") {
        request.url = request.url.replace("/help/", "/help/index.html");
      }
      next();
    });
  },
  configurePreviewServer(server) {
    server.middlewares.use((request, _response, next) => {
      if (request.url?.split("?", 1)[0] === "/help/") {
        request.url = request.url.replace("/help/", "/help/index.html");
      }
      next();
    });
  }
});

export default defineConfig({
  plugins: [
    manualIndexRoute(),
    vue(),
    tailwindcss(),
    AutoImport({
      resolvers: [ElementPlusResolver({ importStyle: false })]
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: false })]
    })
  ],
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
  },
  build: {
    target: "chrome108",
    cssTarget: "chrome108",
    rollupOptions: {
      output: {
        // 代码分割策略：将大型第三方库独立分 chunk，优化缓存和首屏加载
        // 注意：Monaco Editor 不放入 manualChunks，让 Vite 的 ?worker 语法自然拆分 Workers
        manualChunks(id) {
          // Vue 生态核心
          if (id.includes("vue/dist") || id.includes("vue-router") || id.includes("pinia")) {
            return "vue-vendor";
          }
          // Element Plus UI 库
          if (id.includes("element-plus") || id.includes("@element-plus/icons-vue")) {
            return "element-plus";
          }
          // Markdown 相关
          if (id.includes("markdown-it") || id.includes("highlight.js") || id.includes("marked")) {
            return "markdown";
          }
          // 布局/面板管理
          if (id.includes("dockview-vue")) {
            return "dockview";
          }
          // 数据请求管理
          if (id.includes("@tanstack/vue-query")) {
            return "query";
          }
          // Monaco Editor 不配置，让 ?worker 语法自然拆分 Workers
        }
      }
    },
    // Monaco Editor 作为懒加载包体积较大，提高包警告阈值至 1.5MB 避免误报
    chunkSizeWarningLimit: 1500
  }
});
