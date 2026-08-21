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
    private static final String DEVELOPMENT_VERSION = "0.1.0-dev";
    private static final String MANIFEST_VERSION_ATTRIBUTE = "Local-Client-Version";

    public LocalClientBuildInfo {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public static LocalClientBuildInfo current() {
        String overridden = System.getProperty("test.agent.local.client.version");
        return resolve(overridden == null || overridden.isBlank() ? readManifestVersion() : overridden);
    }

    static LocalClientBuildInfo resolve(String version) {
        if (version == null || version.isBlank() || DEVELOPMENT_VERSION.equals(version)) {
            return new LocalClientBuildInfo(
                    DEVELOPMENT_VERSION,
                    LAUNCHER_VERSION,
                    List.of(),
                    false);
        }
        String managedVersion = LocalClientReleaseVersion.parse(version).value();
        return new LocalClientBuildInfo(
                managedVersion,
                LAUNCHER_VERSION,
                List.of(SELF_UPDATE_CAPABILITY),
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
