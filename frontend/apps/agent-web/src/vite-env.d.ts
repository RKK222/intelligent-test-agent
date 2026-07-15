/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_TEST_AGENT_BUILD_VERSION?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module "*.svg" {
  const src: string;
  export default src;
}
