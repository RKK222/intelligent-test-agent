export const DEFAULT_AAM_LOGIN_BASE_URL: string;

export function resolveAamLoginBaseUrl(baseURL?: string): string;

export function getAamUrl(url: string, baseURL?: string): string;

export function jumpAam(url: string, baseURL?: string): void;
