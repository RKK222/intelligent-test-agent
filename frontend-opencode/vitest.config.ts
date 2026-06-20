import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
      "@test-agent/backend-api": fileURLToPath(new URL("../frontend/packages/backend-api/src/index.ts", import.meta.url)),
      "@test-agent/event-stream-client": fileURLToPath(
        new URL("../frontend/packages/event-stream-client/src/index.ts", import.meta.url)
      ),
      "@test-agent/shared-types": fileURLToPath(new URL("../frontend/packages/shared-types/src/index.ts", import.meta.url))
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.ts"],
    include: ["tests/**/*.test.ts"]
  }
});
