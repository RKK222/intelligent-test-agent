import type { FileTreeEntry } from "@test-agent/shared-types";

const EXACT_FILE_NAMES: Record<string, string> = {
  "readme.md": "Readme",
  readme: "Readme",
  "changelog.md": "Changelog",
  license: "Certificate",
  "license.txt": "Certificate",
  "license.md": "Certificate",
  "package.json": "Nodejs",
  "package-lock.json": "Nodejs",
  "yarn.lock": "Yarn",
  "pnpm-lock.yaml": "Pnpm",
  "pnpm-workspace.yaml": "Pnpm",
  "bun.lock": "Bun",
  "bun.lockb": "Bun",
  "bunfig.toml": "Bun",
  ".nvmrc": "Nodejs",
  ".node-version": "Nodejs",
  dockerfile: "Docker",
  "docker-compose.yml": "Docker",
  "docker-compose.yaml": "Docker",
  ".dockerignore": "Docker",
  "jest.config.js": "Jest",
  "jest.config.ts": "Jest",
  "vitest.config.js": "Vitest",
  "vitest.config.ts": "Vitest",
  "tailwind.config.js": "Tailwindcss",
  "tailwind.config.ts": "Tailwindcss",
  "tsconfig.json": "Tsconfig",
  "tsconfig.base.json": "Tsconfig",
  "tsconfig.node.json": "Tsconfig",
  "tsconfig.app.json": "Tsconfig",
  "jsconfig.json": "Jsconfig",
  ".eslintrc": "Eslint",
  ".eslintrc.js": "Eslint",
  ".eslintrc.json": "Eslint",
  ".eslintrc.cjs": "Eslint",
  ".prettierrc": "Prettier",
  ".prettierrc.js": "Prettier",
  ".prettierrc.json": "Prettier",
  "vite.config.js": "Vite",
  "vite.config.ts": "Vite",
  "vite.config.mjs": "Vite",
  "webpack.config.js": "Webpack",
  ".gitignore": "Git",
  ".gitattributes": "Git",
  ".gitmodules": "Git",
  makefile: "Makefile",
  "cargo.toml": "Rust",
  "go.mod": "GoMod",
  "go.sum": "GoMod",
  "requirements.txt": "Python",
  "pyproject.toml": "Python",
  "pom.xml": "Maven",
  "build.gradle": "Gradle",
  ".env": "Tune",
  ".env.local": "Tune",
  ".env.test": "Tune",
  ".env.development": "Tune",
  ".env.production": "Tune",
  ".env.example": "Tune",
  ".editorconfig": "Editorconfig",
  "favicon.ico": "Favicon",
  "playwright.config.js": "Playwright",
  "playwright.config.ts": "Playwright"
};

const FILE_EXTENSIONS: Record<string, string> = {
  "spec.ts": "TestTs",
  "test.ts": "TestTs",
  "spec.tsx": "TestJsx",
  "test.tsx": "TestJsx",
  "spec.js": "TestJs",
  "test.js": "TestJs",
  "spec.jsx": "TestJsx",
  "test.jsx": "TestJsx",
  "d.ts": "TypescriptDef",
  ts: "Typescript",
  tsx: "React_ts",
  js: "Javascript",
  jsx: "React",
  mjs: "Javascript",
  cjs: "Javascript",
  vue: "Vue",
  html: "Html",
  htm: "Html",
  css: "Css",
  scss: "Sass",
  sass: "Sass",
  less: "Less",
  json: "Json",
  jsonc: "Json",
  xml: "Xml",
  yml: "Yaml",
  yaml: "Yaml",
  toml: "Toml",
  md: "Markdown",
  markdown: "Markdown",
  mdx: "Mdx",
  py: "Python",
  pyx: "Python",
  pyw: "Python",
  rs: "Rust",
  go: "Go",
  java: "Java",
  kt: "Kotlin",
  kts: "Kotlin",
  php: "Php",
  rb: "Ruby",
  cs: "Csharp",
  cpp: "Cpp",
  cc: "Cpp",
  cxx: "Cpp",
  c: "Cpp",
  h: "Hpp",
  hpp: "Hpp",
  swift: "Swift",
  sh: "Console",
  bash: "Console",
  zsh: "Console",
  fish: "Console",
  ps1: "Powershell",
  svg: "Svg",
  png: "Image",
  jpg: "Image",
  jpeg: "Image",
  gif: "Image",
  webp: "Image",
  bmp: "Image",
  ico: "Favicon",
  avif: "Image",
  mp4: "Video",
  mov: "Video",
  avi: "Video",
  webm: "Video",
  mp3: "Audio",
  wav: "Audio",
  ogg: "Audio",
  flac: "Audio",
  zip: "Zip",
  tar: "Zip",
  gz: "Zip",
  rar: "Zip",
  "7z": "Zip",
  pdf: "Pdf",
  sql: "Database",
  db: "Database",
  sqlite: "Database",
  log: "Log",
  lock: "Lock",
  key: "Certificate",
  pem: "Certificate",
  crt: "Certificate"
};

/**
 * Material Icon Theme 图标映射：
 * - 文件夹（type === "directory"）：无图标，返回空字符串 ""
 * - 文件（type === "file"）：返回 sprite.svg 中对应的 Material Icon symbol ID
 */
export function getMaterialFileIconName(entry: Pick<FileTreeEntry, "name" | "path" | "type">): string {
  if (entry.type === "directory") {
    return ""; // 文件夹不要图标
  }

  const name = (entry.name || entry.path.split("/").pop() || "").toLowerCase();
  if (EXACT_FILE_NAMES[name]) {
    return EXACT_FILE_NAMES[name];
  }

  // 尝试匹配多段后缀 (例如 .spec.ts, .d.ts)
  const dotIndexes: number[] = [];
  for (let i = 0; i < name.length; i++) {
    if (name[i] === ".") dotIndexes.push(i);
  }
  for (const idx of dotIndexes) {
    const ext = name.slice(idx + 1);
    if (FILE_EXTENSIONS[ext]) {
      return FILE_EXTENSIONS[ext];
    }
  }

  return "Document"; // 默认文件图标
}

/**
 * 兼容旧代码与测试的转换函数
 */
export function getVsCodeFileIconClass(entry: Pick<FileTreeEntry, "name" | "path" | "type">): string {
  const icon = getMaterialFileIconName(entry);
  return icon ? `material-icon material-icon-${icon}` : "";
}
