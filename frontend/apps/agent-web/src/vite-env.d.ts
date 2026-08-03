/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_TEST_AGENT_BUILD_VERSION?: string;
  readonly VITE_TEST_AGENT_LOBEHUB_ENABLED?: string;
  readonly VITE_TEST_AGENT_WORKFLOW_ENABLED?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module "*.svg" {
  const src: string;
  export default src;
}
