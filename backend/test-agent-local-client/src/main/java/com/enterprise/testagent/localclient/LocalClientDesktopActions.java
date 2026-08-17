package com.enterprise.testagent.localclient;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** 受控桌面动作；只打开平台 URL、日志目录和刚导出的日志文件。 */
final class LocalClientDesktopActions {

    private LocalClientDesktopActions() {
    }

    static void openWeb(URI uri) throws IOException {
        if (uri == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IOException("网页地址无效");
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
            return;
        }
        openWithPlatformCommand(uri.toString());
    }

    static void openDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(directory.toFile());
            return;
        }
        openWithPlatformCommand(directory.toString());
    }

    static void revealFile(Path file) throws IOException {
        if (LocalClientPaths.isMac()) {
            start("/usr/bin/open", "-R", file.toString());
            return;
        }
        openDirectory(file.getParent());
    }

    private static void openWithPlatformCommand(String target) throws IOException {
        if (LocalClientPaths.isMac()) {
            start("/usr/bin/open", target);
        } else {
            start("xdg-open", target);
        }
    }

    private static void start(String... command) throws IOException {
        new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
