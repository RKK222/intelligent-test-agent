export const CUSTOM_MENU_LIMIT = 10;
export const CUSTOM_MENU_NAME_MAX_LENGTH = 12;
export const CUSTOM_MENU_URL_MAX_LENGTH = 2048;

export const CUSTOM_MENU_ICON_OPTIONS = [
  { key: "globe", label: "网站" },
  { key: "link", label: "链接" },
  { key: "book", label: "文档" },
  { key: "chart", label: "看板" },
  { key: "database", label: "数据" },
  { key: "checklist", label: "清单" },
  { key: "gauge", label: "监控" },
  { key: "network", label: "服务" }
] as const;

export type CustomMenuIconKey = typeof CUSTOM_MENU_ICON_OPTIONS[number]["key"];

export type CustomMenuItem = {
  id: string;
  name: string;
  icon: CustomMenuIconKey;
  url: string;
};

export type CustomMenuStorage = Pick<Storage, "getItem" | "setItem">;

const CUSTOM_MENU_ID_PATTERN = /^[a-z0-9][a-z0-9-]{5,63}$/;
const CUSTOM_MENU_ICON_KEYS = new Set<string>(CUSTOM_MENU_ICON_OPTIONS.map((option) => option.key));

export function customMenuStorageKey(userId: string): string {
  return `test-agent.custom-menus.v1:${userId.trim()}`;
}

/** 自定义菜单 ID 只用于本地稳定路由，不包含用户名、URL 或其它业务信息。 */
export function createCustomMenuId(now = Date.now(), random = Math.random()): string {
  const randomPart = Math.floor(random * 0x100000000).toString(36).padStart(6, "0");
  return `menu-${now.toString(36)}-${randomPart}`;
}

export function isCustomMenuId(value: unknown): value is string {
  return typeof value === "string" && CUSTOM_MENU_ID_PATTERN.test(value);
}

export function isCustomMenuIconKey(value: unknown): value is CustomMenuIconKey {
  return typeof value === "string" && CUSTOM_MENU_ICON_KEYS.has(value);
}

export function normalizeCustomMenuName(value: string): string {
  const name = value.trim();
  if (!name) throw new Error("请输入菜单名");
  if (name.length > CUSTOM_MENU_NAME_MAX_LENGTH) {
    throw new Error(`菜单名最多 ${CUSTOM_MENU_NAME_MAX_LENGTH} 个字符`);
  }
  return name;
}

/**
 * 仅接受 http(s) 绝对地址或同源根路径，拒绝脚本协议、协议相对地址和 URL 内凭据。
 * 同源路径保留相对形式，避免不同部署域名之间复制本地偏好时固化主机名。
 */
export function normalizeCustomMenuUrl(value: string, origin: string): string {
  const raw = value.trim();
  if (!raw) throw new Error("请输入 URL");
  if (raw.length > CUSTOM_MENU_URL_MAX_LENGTH) {
    throw new Error(`URL 最多 ${CUSTOM_MENU_URL_MAX_LENGTH} 个字符`);
  }
  if (raw.startsWith("//")) {
    throw new Error("URL 不能使用省略协议的写法");
  }

  const isRootRelative = raw.startsWith("/");
  if (!isRootRelative && !/^https?:\/\//i.test(raw)) {
    throw new Error("URL 需以 http://、https:// 或 / 开头");
  }

  let parsed: URL;
  try {
    parsed = new URL(raw, origin);
  } catch {
    throw new Error("URL 格式不正确");
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error("URL 只支持 HTTP 或 HTTPS");
  }
  if (parsed.username || parsed.password) {
    throw new Error("URL 不能包含账号或密码");
  }
  if (isRootRelative) {
    const expectedOrigin = new URL(origin).origin;
    if (parsed.origin !== expectedOrigin) throw new Error("同源路径不能切换到其它站点");
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  }
  return parsed.toString();
}

function normalizeStoredMenu(value: unknown, origin: string): CustomMenuItem | null {
  if (!value || typeof value !== "object") return null;
  const item = value as Partial<CustomMenuItem>;
  if (!isCustomMenuId(item.id) || !isCustomMenuIconKey(item.icon)) return null;
  try {
    return {
      id: item.id,
      name: normalizeCustomMenuName(typeof item.name === "string" ? item.name : ""),
      icon: item.icon,
      url: normalizeCustomMenuUrl(typeof item.url === "string" ? item.url : "", origin)
    };
  } catch {
    return null;
  }
}

export function loadCustomMenus(
  storage: CustomMenuStorage | undefined,
  userId: string,
  origin: string
): CustomMenuItem[] {
  if (!storage || !userId.trim()) return [];
  try {
    const stored = JSON.parse(storage.getItem(customMenuStorageKey(userId)) ?? "null") as {
      version?: unknown;
      items?: unknown;
    } | null;
    if (stored?.version !== 1 || !Array.isArray(stored.items)) return [];
    const ids = new Set<string>();
    const names = new Set<string>();
    const items: CustomMenuItem[] = [];
    for (const rawItem of stored.items) {
      const item = normalizeStoredMenu(rawItem, origin);
      const normalizedName = item?.name.toLocaleLowerCase();
      if (!item || ids.has(item.id) || names.has(normalizedName!)) continue;
      ids.add(item.id);
      names.add(normalizedName!);
      items.push(item);
      if (items.length >= CUSTOM_MENU_LIMIT) break;
    }
    return items;
  } catch {
    return [];
  }
}

export function saveCustomMenus(
  storage: CustomMenuStorage | undefined,
  userId: string,
  items: readonly CustomMenuItem[]
): boolean {
  if (!storage || !userId.trim()) return false;
  try {
    storage.setItem(customMenuStorageKey(userId), JSON.stringify({ version: 1, items }));
    return true;
  } catch {
    return false;
  }
}
