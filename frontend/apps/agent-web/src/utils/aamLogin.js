export const DEFAULT_AAM_LOGIN_BASE_URL = "http://tcds-prod.sdc.icbc/aam/onlyLogin/";

export function resolveAamLoginBaseUrl(baseURL) {
  const configured = typeof baseURL === "string" ? baseURL.trim() : "";
  return `${(configured || DEFAULT_AAM_LOGIN_BASE_URL).replace(/\/+$/, "")}/`;
}

export function getAamUrl(url, baseURL = DEFAULT_AAM_LOGIN_BASE_URL) {
  const baseStr = encodeToUrl(url);
  return resolveAamLoginBaseUrl(baseURL) + baseStr;
}
export function jumpAam(url, baseURL = DEFAULT_AAM_LOGIN_BASE_URL) {
  window.location.replace(getAamUrl(url, baseURL));
}
function encodeToUrl(url) {
  const encoder = new TextEncoder();
  const data = encoder.encode(url);
  let binary = "";
  data.forEach((byte) => (binary += String.fromCharCode(byte)));
  let base64 = btoa(binary);
  base64 = base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return base64;
}
