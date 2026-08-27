package com.enterprise.testagent.localclient;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JDialog;
import javax.swing.JFileChooser;

/** 受控桌面动作；只打开平台 URL、客户端目录、导出日志及用户主动触发的本地目录选择器。 */
final class LocalClientDesktopActions {

    private static final String MAC_DIRECTORY_DIALOG_PROPERTY = "apple.awt.fileDialogForDirectories";
    private static final AtomicBoolean DIRECTORY_PICKER_OPEN = new AtomicBoolean();
    private static final AtomicReference<Window> ACTIVE_DIRECTORY_PICKER = new AtomicReference<>();

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

    /** 客户端工作区只通过不可猜测的 Workspace ID 深链，绝对路径不得进入浏览器地址栏。 */
    static URI workspaceWebUri(URI webBaseUri, String workspaceId) throws IOException {
        if (webBaseUri == null
                || webBaseUri.getRawAuthority() == null
                || !("https".equalsIgnoreCase(webBaseUri.getScheme())
                || "http".equalsIgnoreCase(webBaseUri.getScheme()))) {
            throw new IOException("网页地址无效");
        }
        if (workspaceId == null
                || workspaceId.isBlank()
                || workspaceId.length() > 255
                || !workspaceId.matches("wrk_[A-Za-z0-9_-]+")) {
            throw new IOException("工作区标识无效");
        }
        String encodedWorkspaceId = URLEncoder.encode(workspaceId.trim(), StandardCharsets.UTF_8);
        return URI.create(webBaseUri.getScheme() + "://" + webBaseUri.getRawAuthority()
                + "/workbench?localWorkspaceId=" + encodedWorkspaceId);
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
        LocalClientPlatform platform = LocalClientPlatform.current();
        if (platform.isMac()) {
            start("/usr/bin/open", "-R", file.toString());
            return;
        }
        if (platform.isWindows()) {
            start("explorer.exe", "/select,", file.toString());
            return;
        }
        openDirectory(file.getParent());
    }

    /**
     * 在客户端桌面会话中同步打开目录选择器；返回 null 表示用户取消。
     * 同一客户端只允许一个选择器，连接取消时关闭仍在等待的窗口，避免后台残留弹框。
     */
    static Path chooseDirectory(String initialPath) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("当前客户端没有可用的图形桌面，无法打开目录选择器");
        }
        if (!DIRECTORY_PICKER_OPEN.compareAndSet(false, true)) {
            throw new IllegalStateException("本地目录选择器已经打开");
        }
        AtomicReference<Path> selection = new AtomicReference<>();
        try {
            Runnable showPicker = () -> selection.set(LocalClientPlatform.current().isMac()
                    ? chooseMacDirectory(initialDirectory(initialPath))
                    : chooseSwingDirectory(initialDirectory(initialPath)));
            if (EventQueue.isDispatchThread()) {
                showPicker.run();
            } else {
                EventQueue.invokeAndWait(showPicker);
            }
            return selection.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            closeActiveDirectoryPicker();
            throw new IllegalStateException("本地目录选择已取消", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("本地目录选择器打开失败", cause);
        } finally {
            DIRECTORY_PICKER_OPEN.set(false);
        }
    }

    /** 浏览器设置只接受用户选择的单个可执行文件，不允许目录或多选。 */
    static Path chooseExecutableFile(Path initialFile) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("当前客户端没有可用的图形桌面，无法选择浏览器程序");
        }
        AtomicReference<Path> selection = new AtomicReference<>();
        Runnable show = () -> {
            JFileChooser chooser = new JFileChooser(initialFile == null
                    ? Path.of(System.getProperty("user.home", ".")).toFile()
                    : initialFile.toFile());
            chooser.setDialogTitle("选择 360 浏览器程序");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
                    && chooser.getSelectedFile() != null) {
                selection.set(chooser.getSelectedFile().toPath().toAbsolutePath().normalize());
            }
        };
        try {
            if (EventQueue.isDispatchThread()) {
                show.run();
            } else {
                EventQueue.invokeAndWait(show);
            }
            return selection.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("浏览器程序选择已取消", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("浏览器程序选择器打开失败", exception.getCause());
        }
    }

    private static Path chooseMacDirectory(Path initialDirectory) {
        String previous = System.getProperty(MAC_DIRECTORY_DIALOG_PROPERTY);
        System.setProperty(MAC_DIRECTORY_DIALOG_PROPERTY, "true");
        FileDialog dialog = new FileDialog((Frame) null, "选择本地工作区目录", FileDialog.LOAD);
        dialog.setDirectory(initialDirectory.toString());
        dialog.setMultipleMode(false);
        ACTIVE_DIRECTORY_PICKER.set(dialog);
        try {
            dialog.setVisible(true);
            String directory = dialog.getDirectory();
            String file = dialog.getFile();
            if (directory == null || file == null) {
                return null;
            }
            return Path.of(directory, file).toAbsolutePath().normalize();
        } finally {
            ACTIVE_DIRECTORY_PICKER.compareAndSet(dialog, null);
            dialog.dispose();
            if (previous == null) {
                System.clearProperty(MAC_DIRECTORY_DIALOG_PROPERTY);
            } else {
                System.setProperty(MAC_DIRECTORY_DIALOG_PROPERTY, previous);
            }
        }
    }

    private static Path chooseSwingDirectory(Path initialDirectory) {
        AtomicReference<Window> dialogReference = new AtomicReference<>();
        JFileChooser chooser = new JFileChooser(initialDirectory.toFile()) {
            @Override
            protected JDialog createDialog(java.awt.Component parent) {
                JDialog dialog = super.createDialog(parent);
                dialogReference.set(dialog);
                ACTIVE_DIRECTORY_PICKER.set(dialog);
                return dialog;
            }
        };
        chooser.setDialogTitle("选择本地工作区目录");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.setAcceptAllFileFilterUsed(false);
        try {
            int result = chooser.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return null;
            }
            return chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        } finally {
            Window dialog = dialogReference.get();
            ACTIVE_DIRECTORY_PICKER.compareAndSet(dialog, null);
            if (dialog != null) {
                dialog.dispose();
            }
        }
    }

    private static Path initialDirectory(String initialPath) {
        String home = System.getProperty("user.home", ".");
        Path fallback = Path.of(home).toAbsolutePath().normalize();
        if (initialPath == null || initialPath.isBlank()) {
            return fallback;
        }
        try {
            Path requested = Path.of(initialPath.trim()).toAbsolutePath().normalize();
            return Files.isDirectory(requested) ? requested : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static void closeActiveDirectoryPicker() {
        Window active = ACTIVE_DIRECTORY_PICKER.getAndSet(null);
        if (active != null) {
            EventQueue.invokeLater(active::dispose);
        }
    }

    private static void openWithPlatformCommand(String target) throws IOException {
        LocalClientPlatform platform = LocalClientPlatform.current();
        if (platform.isMac()) {
            start("/usr/bin/open", target);
        } else if (platform.isWindows()) {
            start("explorer.exe", target);
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
