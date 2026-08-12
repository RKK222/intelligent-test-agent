package com.enterprise.testagent.localclient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** 从用户私有文件读取 client key；禁止命令行参数、环境变量和宽权限文件。 */
final class LocalClientCredentialFile {

    private LocalClientCredentialFile() {
    }

    static String read() throws IOException {
        Path path = LocalClientPaths.configDirectory().resolve("client.key");
        requirePrivatePermissions(path);
        String value = Files.readString(path).trim();
        if (!value.startsWith("tack_v1_") || value.length() > 128) {
            throw new IllegalArgumentException("client.key is invalid");
        }
        return value;
    }

    private static void requirePrivatePermissions(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                throw new IllegalStateException("client.key permissions must be 0600");
            }
        } catch (UnsupportedOperationException ignored) {
            // 首版平台均支持 POSIX 权限；保留异常兼容由未来平台适配层处理。
        }
    }
}
