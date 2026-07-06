export function getAamUrl(url, baseURL = "http://zfw.sdc.cs.icbc/aam/login/") {
  const baseStr = encodeToUrl(url);
  return baseURL + baseStr;
}
export function jumpAam(url, baseURL = "http://zfw.sdc.cs.icbc/aam/login/") {
  const aamUrl = baseURL;
  window.location.href = aamUrl + encodeToUrl(url);
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
