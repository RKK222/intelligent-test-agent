import { marked } from "marked";

const URI_SCHEME = /^[A-Za-z][A-Za-z0-9+.-]*:/;

export type WorkspaceUploadItem = {
  file: File;
  targetPath: string;
};

export type MissingMarkdownImage = {
  markdownPath: string;
  source: string;
  targetPath: string;
};

export type WorkspaceUploadPlan = {
  items: WorkspaceUploadItem[];
  missingMarkdownImages: MissingMarkdownImage[];
};

/**
 * 只收集 Markdown 语义中的图片 token，代码块和普通链接不会被误判为待上传图片。
 */
export function markdownImageSources(markdown: string): string[] {
  const result: string[] = [];
  const visited = new WeakSet<object>();
  const visit = (value: unknown): void => {
    if (Array.isArray(value)) {
      value.forEach(visit);
      return;
    }
    if (!value || typeof value !== "object" || visited.has(value)) return;
    visited.add(value);
    const token = value as Record<string, unknown>;
    if (token.type === "image" && typeof token.href === "string") {
      result.push(token.href);
    }
    for (const [key, nested] of Object.entries(token)) {
      if (key === "raw" || key === "text" || key === "href") continue;
      visit(nested);
    }
  };
  visit(marked.lexer(markdown));
  return [...new Set(result)];
}

/** 外链、data URL、锚点和协议相对地址仍交给浏览器，只有工作区路径走文件 WebSocket。 */
export function isWorkspaceMarkdownImageSource(source: string): boolean {
  const value = source.trim();
  return Boolean(value)
    && !value.startsWith("#")
    && !value.startsWith("//")
    && !URI_SCHEME.test(value);
}

/**
 * 按 Markdown 文件所在目录解析相对图片；先消解 `..`，再确认结果仍位于工作区内。
 */
export function resolveMarkdownWorkspaceImagePath(markdownPath: string, source: string): string | undefined {
  if (!isWorkspaceMarkdownImageSource(source)) return undefined;
  const withoutSuffix = source.trim().split(/[?#]/, 1)[0] ?? "";
  let decoded: string;
  try {
    decoded = decodeURIComponent(withoutSuffix);
  } catch {
    return undefined;
  }
  const normalizedSource = decoded.replace(/\\/g, "/");
  const segments = normalizedSource.startsWith("/")
    ? []
    : normalizeSegments(markdownPath).slice(0, -1);
  for (const segment of normalizedSource.split("/")) {
    if (!segment || segment === ".") continue;
    if (segment === "..") {
      if (segments.length === 0) return undefined;
      segments.pop();
      continue;
    }
    if (segment.includes("\0")) return undefined;
    segments.push(segment);
  }
  return segments.length > 0 ? segments.join("/") : undefined;
}

/** 文件夹选择器会带顶层目录名；上传到目标目录时去掉该目录名并保留内部层级。 */
export function browserUploadRelativePath(file: File): string {
  const relative = file.webkitRelativePath?.replace(/\\/g, "/").replace(/^\/+|\/+$/g, "") ?? "";
  if (!relative) return fileName(file.name);
  const segments = normalizeSegments(relative);
  return segments.length > 1 ? segments.slice(1).join("/") : fileName(file.name);
}

/**
 * 生成一次上传批次的目标路径。缺失图片只进入提示，不阻断 Markdown 和已选择图片上传。
 */
export async function planWorkspaceUpload(directory: string, files: File[]): Promise<WorkspaceUploadPlan> {
  const normalizedDirectory = normalizeSegments(directory).join("/");
  const items = files.map((file) => ({
    file,
    targetPath: joinWorkspacePath(normalizedDirectory, browserUploadRelativePath(file))
  }));
  const markdownReferences: MissingMarkdownImage[] = [];
  for (const item of items) {
    if (!/\.md$/i.test(item.targetPath)) continue;
    const markdown = await item.file.text();
    for (const source of markdownImageSources(markdown)) {
      const targetPath = resolveMarkdownWorkspaceImagePath(item.targetPath, source);
      if (targetPath) markdownReferences.push({ markdownPath: item.targetPath, source, targetPath });
    }
  }

  // 普通多选只暴露 basename。若 Markdown 指向子目录且名称唯一，将所选图片放到引用目标而非根目录。
  const defaultTargets = new Set(items.map((item) => item.targetPath));
  const assignedItems = new Set<WorkspaceUploadItem>();
  for (const reference of markdownReferences) {
    if (defaultTargets.has(reference.targetPath)) continue;
    const basename = fileName(reference.targetPath);
    const candidates = items.filter((item) => (
      !assignedItems.has(item)
      && !item.file.webkitRelativePath
      && !/\.md$/i.test(item.targetPath)
      && fileName(item.targetPath) === basename
    ));
    if (candidates.length !== 1) continue;
    const candidate = candidates[0]!;
    defaultTargets.delete(candidate.targetPath);
    candidate.targetPath = reference.targetPath;
    defaultTargets.add(candidate.targetPath);
    assignedItems.add(candidate);
  }

  const availableTargets = new Set(items.map((item) => item.targetPath));
  const missingMarkdownImages = markdownReferences.filter((reference) => !availableTargets.has(reference.targetPath));
  return { items, missingMarkdownImages };
}

export function markdownImageMimeType(path: string): string | undefined {
  const extension = fileName(path).toLowerCase().split(".").at(-1);
  return ({
    png: "image/png",
    jpg: "image/jpeg",
    jpeg: "image/jpeg",
    gif: "image/gif",
    webp: "image/webp",
    bmp: "image/bmp",
    svg: "image/svg+xml",
    avif: "image/avif"
  } as Record<string, string>)[extension ?? ""];
}

function joinWorkspacePath(directory: string, relativePath: string): string {
  const segments = [...normalizeSegments(directory), ...normalizeSegments(relativePath)];
  return segments.join("/");
}

function normalizeSegments(path: string): string[] {
  return path.replace(/\\/g, "/").split("/").filter((segment) => segment && segment !== ".");
}

function fileName(path: string): string {
  return path.split(/[\\/]+/).filter(Boolean).at(-1) ?? path;
}
