import { sentryVitePlugin } from "@sentry/vite-plugin"
import { defineConfig } from "vite"
import desktopPlugin from "./vite"

const sentry =
  process.env.SENTRY_AUTH_TOKEN && process.env.SENTRY_ORG && process.env.SENTRY_PROJECT
    ? sentryVitePlugin({
        authToken: process.env.SENTRY_AUTH_TOKEN,
        org: process.env.SENTRY_ORG,
        project: process.env.SENTRY_PROJECT,
        telemetry: false,
        release: {
          name: process.env.SENTRY_RELEASE ?? process.env.VITE_SENTRY_RELEASE,
        },
        sourcemaps: {
          assets: "./dist/**",
          filesToDeleteAfterUpload: "./dist/**/*.map",
        },
      })
    : false

export default defineConfig({
  plugins: [desktopPlugin, sentry] as any,
  server: {
    host: "0.0.0.0",
    allowedHosts: true,
    // 与 frontend/ 官方自研前端（Next.js，默认 :3000）区分，固定使用 :3001
    port: 3001,
    // 适配层：把 /api 代理到平台后端 test-agent-app（:8080），
    // 前端同源调 /api/...，规避浏览器 CORS；同时把 Origin 改写为后端放行的 :3000，
    // 避免后端 CORS 拒绝（后端默认仅允许 localhost:3000 / 127.0.0.1:3000）。
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on("proxyReq", (proxyReq) => {
            proxyReq.setHeader("Origin", "http://localhost:3000")
          })
        },
      },
    },
  },
  build: {
    target: "esnext",
    sourcemap: true,
  },
})
