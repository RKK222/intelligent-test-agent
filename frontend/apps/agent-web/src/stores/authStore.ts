import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, LoginResponse } from "@test-agent/shared-types";
import { defineStore } from "pinia";
import { ref } from "vue";

/**
 * Token 在 localStorage 中的存储 key。
 */
const TOKEN_KEY = "test-agent.auth.token";

/**
 * 认证状态管理 Store。
 * 管理用户 Token 和认证信息的 Pinia Store。
 */
export const useAuthStore = defineStore("auth", () => {
  // 当前登录用户信息
  const currentUser = ref<CurrentUser | null>(null);
  // 认证 Token
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY));
  // 是否正在加载
  const loading = ref(false);

  /**
   * 是否已登录（有有效的 Token）。
   */
  const isAuthenticated = (): boolean => {
    return token.value !== null && token.value.length > 0;
  };

  /**
   * 保存 Token 到内存和 localStorage。
   */
  const saveToken = (newToken: string) => {
    token.value = newToken;
    localStorage.setItem(TOKEN_KEY, newToken);
  };

  /**
   * 清除 Token 和用户信息。
   */
  const clearAuth = () => {
    token.value = null;
    currentUser.value = null;
    localStorage.removeItem(TOKEN_KEY);
  };

  /**
   * 登录：调用后端 API 认证，保存 Token。
   */
  const login = async (api: BackendApiClient, username: string, password: string): Promise<LoginResponse> => {
    loading.value = true;
    try {
      const response = await api.login({ username, password });
      saveToken(response.token);
      return response;
    } finally {
      loading.value = false;
    }
  };

  /**
   * 登出：清除本地认证状态。
   */
  const logout = (api?: BackendApiClient) => {
    if (api) {
      // 尝试通知后端清除 Token（忽略错误）
      api.logout().catch(() => {});
    }
    clearAuth();
  };

  /**
   * 加载当前用户信息。
   */
  const fetchCurrentUser = async (api: BackendApiClient): Promise<CurrentUser | null> => {
    if (!token.value) {
      return null;
    }
    try {
      const user = await api.getCurrentUser();
      currentUser.value = user;
      return user;
    } catch {
      // Token 无效或过期，清除认证状态
      clearAuth();
      return null;
    }
  };

  return {
    currentUser,
    token,
    loading,
    isAuthenticated,
    login,
    logout,
    fetchCurrentUser,
    clearAuth
  };
});
