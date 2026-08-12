export type ReleaseFeatureFlags = Readonly<{
  lobehub: boolean;
}>;

/**
 * 发布功能只接受显式 true，缺失、空值和其它拼写都按关闭处理，避免企业包误开放未交付服务。
 */
function enabled(raw: string | undefined): boolean {
  return raw?.trim().toLowerCase() === "true";
}

export function resolveReleaseFeatureFlags(env: ImportMetaEnv): ReleaseFeatureFlags {
  return Object.freeze({
    lobehub: enabled(env.VITE_TEST_AGENT_LOBEHUB_ENABLED)
  });
}

export const releaseFeatures = resolveReleaseFeatureFlags(import.meta.env);

/** 禁用的发布功能路由不进入登录回跳或懒加载页面。 */
export function isReleaseFeaturePathEnabled(
  pathname: string,
  features: ReleaseFeatureFlags = releaseFeatures
): boolean {
  if (pathname === "/lobehub/launch") {
    return features.lobehub;
  }
  return true;
}
