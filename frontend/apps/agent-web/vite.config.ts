import { createReadStream, lstatSync } from "node:fs";
import { extname, isAbsolute, relative, resolve } from "node:path";
import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, type Plugin } from "vite";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import {
  createToolboxDevProxyOptions,
  toolboxSuiteRootGuard
} from "./toolbox-dev-proxy";

// 统一通过 import.meta.url 解析 workspace 包源码，避免硬编码绝对路径
const pkgSrc = (name: string): string =>
  fileURLToPath(new URL(`../../packages/${name}/src`, import.meta.url));
// 本地一键启动脚本会按 TEST_AGENT_FRONTEND_URL 注入 HOST，未注入时保持仅本机访问。
const devServerHost = process.env.HOST ?? "127.0.0.1";

/**
 * 前端版本只在 Vite 启动构建时生成一次，统一使用北京时间，避免部署机器时区造成版本口径不一致。
 */
const buildVersion = (() => {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23"
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `V${values.year}${values.month}${values.day}.${values.hour}${values.minute}${values.second}`;
})();

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

const localClientDistributionRoot = resolve(
  process.env.TEST_AGENT_LOCAL_CLIENT_DIST_DIR?.trim()
    || fileURLToPath(new URL("../../../deploy/internal/dist/local-opencode-client", import.meta.url))
);

const localClientContentType = (filePath: string): string => {
  if (filePath.endsWith(".tar.gz")) return "application/gzip";
  if (filePath.endsWith(".deb")) return "application/vnd.debian.binary-package";
  if (extname(filePath) === ".json") return "application/json; charset=utf-8";
  if (extname(filePath) === ".sh") return "text/x-shellscript; charset=utf-8";
  return "application/octet-stream";
};

/**
 * dev 只读复用正式客户端分发目录，不把 JRE/OpenCode 大制品复制进前端 bundle。
 * 未打包或路径非法时直接 404，避免 Vite SPA fallback 把 index.html 伪装成安装脚本。
 */
const localClientDistributionRoute = (): Plugin => ({
  name: "test-agent-local-client-distribution-route",
  configureServer(server) {
    server.middlewares.use("/downloads/local-opencode-client", (request, response) => {
      if (request.method !== "GET" && request.method !== "HEAD") {
        response.statusCode = 405;
        response.setHeader("Allow", "GET, HEAD");
        response.end("Method Not Allowed");
        return;
      }

      let pathname: string;
      try {
        pathname = decodeURIComponent((request.url ?? "/").split("?", 1)[0] ?? "/");
      } catch {
        response.statusCode = 400;
        response.end("Bad Request");
        return;
      }
      const segments = pathname.split("/").filter(Boolean);
      if (!segments.length || segments.some(segment => segment === "." || segment === ".." || segment.startsWith("."))) {
        response.statusCode = 404;
        response.end("Not Found");
        return;
      }
      if (segments.length === 1 && segments[0] === "installer") {
        response.statusCode = 302;
        response.setHeader(
          "Location",
          "/downloads/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb"
        );
        response.setHeader("Cache-Control", "no-store");
        response.end();
        return;
      }

      const filePath = resolve(localClientDistributionRoot, ...segments);
      const relativePath = relative(localClientDistributionRoot, filePath);
      if (!relativePath || relativePath.startsWith("..") || isAbsolute(relativePath)) {
        response.statusCode = 404;
        response.end("Not Found");
        return;
      }

      try {
        const stat = lstatSync(filePath);
        if (!stat.isFile() || stat.isSymbolicLink()) {
          response.statusCode = 404;
          response.end("Not Found");
          return;
        }
        response.statusCode = 200;
        response.setHeader("Content-Type", localClientContentType(filePath));
        response.setHeader("Content-Length", String(stat.size));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(
          "Cache-Control",
          relativePath.startsWith("releases/")
            ? "public, max-age=31536000, immutable"
            : "no-store"
        );
        if (relativePath === "TestAgent-Local-Client-Kylin-arm64.deb") {
          response.setHeader("Content-Disposition", `attachment; filename="${relativePath}"`);
        }
        if (request.method === "HEAD") {
          response.end();
          return;
        }
        createReadStream(filePath).on("error", () => response.destroy()).pipe(response);
      } catch {
        response.statusCode = 404;
        response.end("Not Found");
      }
    });
  }
});

export default defineConfig({
  define: {
    "import.meta.env.VITE_TEST_AGENT_BUILD_VERSION": JSON.stringify(buildVersion)
  },
  optimizeDeps: {
    exclude: ["mermaid", "@mermaid-js/layout-elk"]
  },
  plugins: [
    toolboxSuiteRootGuard(),
    manualIndexRoute(),
    localClientDistributionRoute(),
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
      "mermaid": "mermaid/dist/mermaid.esm.min.mjs",
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
    port: 3000,
    proxy: {
      ...createToolboxDevProxyOptions({
        itToolsTarget:
          process.env.TEST_AGENT_TOOLBOX_IT_TOOLS_URL ?? "http://127.0.0.1:18120",
        omniToolsTarget:
          process.env.TEST_AGENT_TOOLBOX_OMNI_TOOLS_URL ?? "http://127.0.0.1:18121"
      }),
      "/xxl-job-admin": {
        target: process.env.TEST_AGENT_XXL_JOB_ADMIN_URL ?? "http://127.0.0.1:18080",
        changeOrigin: true
      }
    }
  },
  build: {
    target: "chrome108",
    cssTarget: "chrome108",
    rollupOptions: {
      input: {
        main: fileURLToPath(new URL("./index.html", import.meta.url)),
        requirementImport: fileURLToPath(new URL("./workspace-requirement-import/index.html", import.meta.url))
      },
      output: {
        // 代码分割策略：将大型第三方库独立分 chunk，优化缓存和首屏加载
        // 注意：Monaco Editor 不放入 manualChunks，让 Vite 的 ?worker 语法自然拆分 Workers
        manualChunks(id) {
          // Vue 生态核心
          if (
            id.includes("vue/dist")
            || id.includes("/node_modules/vue/")
            || id.includes("/node_modules/@vue/")
            || id.includes("vue-router")
            || id.includes("pinia")
          ) {
            return "vue-vendor";
          }
          // Element Plus 与 Vue Query 交给 Rollup 按入口依赖图拆分，避免轻量 iframe
          // 被公共手工 chunk 反向带入完整 UI 与查询库。
          // Markdown 相关
          if (id.includes("markdown-it") || id.includes("highlight.js") || id.includes("marked")) {
            return "markdown";
          }
          // 布局/面板管理
          if (id.includes("dockview-vue")) {
            return "dockview";
          }
          // Monaco Editor 不配置，让 ?worker 语法自然拆分 Workers
        }
      }
    },
    // Monaco Editor 作为懒加载包体积较大，提高包警告阈值至 1.5MB 避免误报
    chunkSizeWarningLimit: 1500
  }
});
