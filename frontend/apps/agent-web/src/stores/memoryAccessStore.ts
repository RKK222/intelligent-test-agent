import type { BackendApiClient } from "@test-agent/backend-api";
import { defineStore } from "pinia";
import { ref } from "vue";

/**
 * 记忆页面访问状态以服务端 availability（总开关 + 灰度名单）为唯一事实源。
 * 路由守卫和工作台入口共用本 Store，避免分别缓存后出现入口与直达权限不一致。
 */
export const useMemoryAccessStore = defineStore("memoryAccess", () => {
  const allowed = ref(false);
  const resolved = ref(false);
  const checking = ref(false);
  let tokenKey: string | null = null;
  let requestVersion = 0;
  let activeRequest: { token: string; promise: Promise<boolean> } | null = null;

  function switchToken(nextToken: string | null) {
    if (tokenKey === nextToken) return;
    requestVersion += 1;
    tokenKey = nextToken;
    activeRequest = null;
    allowed.value = false;
    resolved.value = false;
    checking.value = false;
  }

  async function resolve(
    api: BackendApiClient,
    rawToken: string | null | undefined,
    force: boolean
  ): Promise<boolean> {
    const token = rawToken?.trim() || null;
    switchToken(token);
    if (!token) {
      resolved.value = true;
      return false;
    }
    if (!force && resolved.value) return allowed.value;
    if (activeRequest?.token === token) return activeRequest.promise;

    const version = ++requestVersion;
    checking.value = true;
    let request!: Promise<boolean>;
    request = (async () => {
      let enabled = false;
      try {
        enabled = (await api.getQaMemoryAvailability()).enabled === true;
      } catch {
        // 灰度状态不可确认时失败关闭，避免临时网络错误把隐藏页面意外开放。
      }
      if (version === requestVersion && tokenKey === token) {
        allowed.value = enabled;
        resolved.value = true;
        checking.value = false;
      }
      if (activeRequest?.promise === request) activeRequest = null;
      return version === requestVersion && tokenKey === token ? enabled : false;
    })();
    activeRequest = { token, promise: request };
    return request;
  }

  function ensure(api: BackendApiClient, token: string | null | undefined) {
    return resolve(api, token, false);
  }

  function refresh(api: BackendApiClient, token: string | null | undefined) {
    return resolve(api, token, true);
  }

  function reset() {
    switchToken(null);
  }

  return { allowed, resolved, checking, ensure, refresh, reset };
});
