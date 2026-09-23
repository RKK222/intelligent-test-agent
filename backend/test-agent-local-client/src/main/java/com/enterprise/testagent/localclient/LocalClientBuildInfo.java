package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/** 从打包清单读取客户端版本，集中声明稳定启动器协议与自更新能力。 */
public record LocalClientBuildInfo(
        String clientVersion,
        String launcherVersion,
        List<String> capabilities,
        boolean managedRelease) {

    public static final String LAUNCHER_VERSION = "1";
    public static final String SELF_UPDATE_CAPABILITY = "SELF_UPDATE_V1";
    public static final String OBSERVABILITY_CAPABILITY = "OPENCODE_OBSERVABILITY_V1";
    public static final String PUBLIC_CAPABILITY_SYNC = "PUBLIC_CAPABILITY_SYNC_V1";
    /** 本地公共能力个人副本编辑协议；旧客户端不声明时服务端必须保持只读。 */
    public static final String PUBLIC_CAPABILITY_PERSONAL_EDIT = "PUBLIC_CAPABILITY_PERSONAL_EDIT_V1";
    public static final String MANAGED_MODEL_CONFIG = "MANAGED_MODEL_CONFIG_V1";
    public static final String MANAGED_RTK_CONFIG = "MANAGED_RTK_CONFIG_V1";
    public static final String WORKSPACE_GIT_ACCESS = "WORKSPACE_GIT_ACCESS_V1";
    public static final String LOCAL_BROWSER = "LOCAL_BROWSER_V1";
    private static final String DEVELOPMENT_VERSION = "0.1.0-dev";
    private static final String MANIFEST_VERSION_ATTRIBUTE = "Local-Client-Version";

    public LocalClientBuildInfo {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public static LocalClientBuildInfo current() {
        String overridden = System.getProperty("test.agent.local.client.version");
        LocalClientBuildInfo resolved = resolve(
                overridden == null || overridden.isBlank() ? readManifestVersion() : overridden);
        if (LocalBrowserSettings.supportedPlatform(LocalClientPlatform.current())) {
            return resolved;
        }
        return new LocalClientBuildInfo(
                resolved.clientVersion(),
                resolved.launcherVersion(),
                resolved.capabilities().stream().filter(capability -> !LOCAL_BROWSER.equals(capability)).toList(),
                resolved.managedRelease());
    }

    static LocalClientBuildInfo resolve(String version) {
        if (version == null || version.isBlank() || DEVELOPMENT_VERSION.equals(version)) {
            // 开发包没有可信发布版本，不能自更新；Trace 上传能力属于运行时协议，不能随版本能力一起清空。
            return new LocalClientBuildInfo(
                    DEVELOPMENT_VERSION,
                    LAUNCHER_VERSION,
                    List.of(
                            OBSERVABILITY_CAPABILITY,
                            PUBLIC_CAPABILITY_SYNC,
                            PUBLIC_CAPABILITY_PERSONAL_EDIT,
                            MANAGED_MODEL_CONFIG,
                            MANAGED_RTK_CONFIG,
                            WORKSPACE_GIT_ACCESS,
                            LOCAL_BROWSER),
                    false);
        }
        String managedVersion = LocalClientReleaseVersion.parse(version).value();
        return new LocalClientBuildInfo(
                managedVersion,
                LAUNCHER_VERSION,
                List.of(
                        SELF_UPDATE_CAPABILITY,
                        OBSERVABILITY_CAPABILITY,
                        PUBLIC_CAPABILITY_SYNC,
                        PUBLIC_CAPABILITY_PERSONAL_EDIT,
                        MANAGED_MODEL_CONFIG,
                        MANAGED_RTK_CONFIG,
                        WORKSPACE_GIT_ACCESS,
                        LOCAL_BROWSER),
                true);
    }

    private static String readManifestVersion() {
        try (InputStream input = LocalClientBuildInfo.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (input == null) {
                return null;
            }
            Attributes attributes = new Manifest(input).getMainAttributes();
            return attributes.getValue(MANIFEST_VERSION_ATTRIBUTE);
        } catch (IOException exception) {
            return null;
        }
    }
}
