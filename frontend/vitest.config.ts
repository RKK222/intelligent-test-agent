import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["packages/*/tests/**/*.test.ts", "packages/*/tests/**/*.test.tsx", "apps/*/tests/**/*.test.tsx"]
  },
  resolve: {
    alias: {
      "@test-agent/shared-types": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/shared-types/src",
      "@test-agent/backend-api": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/backend-api/src",
      "@test-agent/event-stream-client": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/event-stream-client/src",
      "@test-agent/ui-kit": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/ui-kit/src",
      "@test-agent/file-explorer": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/file-explorer/src",
      "@test-agent/editor": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/editor/src",
      "@test-agent/diff-viewer": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/diff-viewer/src",
      "@test-agent/agent-chat": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/agent-chat/src",
      "@test-agent/test-runner": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/test-runner/src",
      "@test-agent/workbench-shell": "/Users/huang/workspace/intelligent-test-agent/frontend/packages/workbench-shell/src"
    }
  }
});
