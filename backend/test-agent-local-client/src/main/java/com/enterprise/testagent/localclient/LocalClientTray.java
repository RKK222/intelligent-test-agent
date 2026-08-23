package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** macOS 与 ARM Linux 共用的系统托盘；不支持托盘的桌面环境会保持后台连接运行。 */
final class LocalClientTray implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientTray.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ICON_RESOURCE =
            "/com/enterprise/testagent/localclient/tray/radar-bunny.png";
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
            .withLocale(Locale.SIMPLIFIED_CHINESE)
            .withZone(ZoneId.systemDefault());

    private final LocalClientConfiguration configuration;
    private final LocalClientConnection connection;
    private final SystemTray systemTray;
    private final TrayIcon trayIcon;
    private final MenuItem statusItem;
    private final MenuItem workspaceItem;
    private final MenuItem progressItem;
    private final MenuItem publicCapabilityItem;
    private final BufferedImage petImage;
    private final ScheduledExecutorService updater;
    private final ExecutorService actions;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<String> lastUiKey = new AtomicReference<>();
    private final AtomicReference<LocalClientPayloads.WorkspaceRegistered> latestWorkspace = new AtomicReference<>();

    private LocalClientTray(
            LocalClientConfiguration configuration,
            LocalClientConnection connection,
            SystemTray systemTray,
            TrayIcon trayIcon,
            MenuItem statusItem,
            MenuItem workspaceItem,
            MenuItem progressItem,
            MenuItem publicCapabilityItem,
            BufferedImage petImage,
            ScheduledExecutorService updater,
            ExecutorService actions) {
        this.configuration = configuration;
        this.connection = connection;
        this.systemTray = systemTray;
        this.trayIcon = trayIcon;
        this.statusItem = statusItem;
        this.workspaceItem = workspaceItem;
        this.progressItem = progressItem;
        this.publicCapabilityItem = publicCapabilityItem;
        this.petImage = petImage;
        this.updater = updater;
        this.actions = actions;
    }

    static LocalClientTray install(
            LocalClientConfiguration configuration,
            LocalClientConnection connection) {
        if (!SystemTray.isSupported()) {
            LOGGER.warn("local_client_tray_unavailable reason=system_tray_not_supported");
            return inactive(configuration, connection);
        }
        try {
            BufferedImage pet = loadPetImage();
            SystemTray tray = SystemTray.getSystemTray();
            Dimension size = tray.getTrayIconSize();
            MenuItem status = new MenuItem("正在连接");
            status.setEnabled(false);
            MenuItem openWeb = new MenuItem("打开网页");
            MenuItem registerWorkspace = new MenuItem("选择并注册工作区…");
            registerWorkspace.setEnabled(false);
            MenuItem reconnect = new MenuItem("重连");
            MenuItem publicCapabilities = new MenuItem("公共能力 · 暂无更新");
            publicCapabilities.setEnabled(false);
            MenuItem viewLogs = new MenuItem("查看日志");
            MenuItem downloadLogs = new MenuItem("下载日志");
            MenuItem progress = new MenuItem("会话进度 · 0 项进行中");
            MenuItem exit = new MenuItem("退出客户端");
            PopupMenu menu = new PopupMenu();
            menu.add(status);
            menu.addSeparator();
            menu.add(openWeb);
            menu.add(registerWorkspace);
            menu.add(reconnect);
            menu.addSeparator();
            menu.add(publicCapabilities);
            menu.add(viewLogs);
            menu.add(downloadLogs);
            menu.add(progress);
            menu.addSeparator();
            menu.add(exit);
            TrayIcon icon = new TrayIcon(
                    renderIcon(
                            pet,
                            size.width,
                            size.height,
                            statusColor(LocalClientRuntimeSnapshot.ConnectionState.CONNECTING)),
                    "TestAgent 客户端 · 正在连接",
                    menu);
            icon.setImageAutoSize(true);
            ScheduledExecutorService updater = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("local-client-tray-status-", 0).factory());
            ExecutorService actions = Executors.newCachedThreadPool(
                    Thread.ofPlatform().daemon().name("local-client-tray-action-", 0).factory());
            LocalClientTray result = new LocalClientTray(
                    configuration, connection, tray, icon, status, registerWorkspace, progress,
                    publicCapabilities, pet, updater, actions);
            result.bindActions(
                    openWeb, registerWorkspace, reconnect, publicCapabilities, viewLogs, downloadLogs, progress, exit);
            tray.add(icon);
            updater.scheduleAtFixedRate(result::refreshSafely, 0, 2, TimeUnit.SECONDS);
            LOGGER.info("local_client_tray_started platform={} icon=radar-bunny", platformName());
            return result;
        } catch (AWTException | IOException | RuntimeException exception) {
            LOGGER.warn("local_client_tray_unavailable reason={}", exception.getClass().getSimpleName());
            return inactive(configuration, connection);
        }
    }

    private static LocalClientTray inactive(
            LocalClientConfiguration configuration,
            LocalClientConnection connection) {
        return new LocalClientTray(
                configuration, connection, null, null, null, null, null, null, null, null, null);
    }

    private void bindActions(
            MenuItem openWeb,
            MenuItem registerWorkspace,
            MenuItem reconnect,
            MenuItem publicCapabilities,
            MenuItem viewLogs,
            MenuItem downloadLogs,
            MenuItem progress,
            MenuItem exit) {
        openWeb.addActionListener(event -> runAction("open_web", () -> {
            LocalClientPayloads.WorkspaceRegistered workspace = latestWorkspace.get();
            LocalClientDesktopActions.openWeb(workspace == null
                    ? configuration.webBaseUri()
                    : LocalClientDesktopActions.workspaceWebUri(configuration.webBaseUri(), workspace.workspaceId()));
        }, null));
        registerWorkspace.addActionListener(event -> runAction(
                "register_workspace", this::chooseAndRegisterWorkspace, null));
        reconnect.addActionListener(event -> {
            connection.reconnect();
            displayMessage("TestAgent 客户端", "正在重新连接", TrayIcon.MessageType.INFO);
        });
        publicCapabilities.addActionListener(event -> runAction(
                "public_capability_update", this::confirmPublicCapabilityUpdate, null));
        viewLogs.addActionListener(event -> runAction("view_logs", () ->
                LocalClientDesktopActions.openDirectory(LocalClientPaths.logsDirectory()), null));
        downloadLogs.addActionListener(event -> runAction("download_logs", () -> {
            Path archive = LocalClientLogExporter.export(
                    LocalClientPaths.logsDirectory(), LocalClientPaths.downloadsDirectory());
            LocalClientDesktopActions.revealFile(archive);
            displayMessage("日志已下载", archive.getFileName().toString(), TrayIcon.MessageType.INFO);
        }, null));
        progress.addActionListener(event -> showProgress());
        exit.addActionListener(event -> actions.execute(() -> {
            LOGGER.info("local_client_exit_requested source=tray");
            connection.close();
        }));
    }

    private void refreshSafely() {
        if (closed.get()) {
            return;
        }
        try {
            LocalClientRuntimeSnapshot snapshot = connection.runtimeSnapshot();
            LocalClientPublicCapabilityStore.State capability = connection.publicCapabilitySnapshot();
            String uiKey = snapshot.connectionState() + ":" + snapshot.activeOperations().size() + ":"
                    + processLabel(snapshot.processStatus()) + ":" + capability.status() + ":"
                    + (capability.pendingAvailable() == null ? "" : capability.pendingAvailable().bundleDigest());
            if (uiKey.equals(lastUiKey.getAndSet(uiKey))) {
                return;
            }
            EventQueue.invokeLater(() -> applySnapshot(snapshot));
        } catch (RuntimeException exception) {
            LOGGER.debug("local_client_tray_refresh_failed reason={}", exception.getClass().getSimpleName());
        }
    }

    private void applySnapshot(LocalClientRuntimeSnapshot snapshot) {
        if (closed.get() || trayIcon == null) {
            return;
        }
        String status = statusText(snapshot, snapshot.processStatus());
        statusItem.setLabel(status);
        workspaceItem.setEnabled(snapshot.connectionState() == LocalClientRuntimeSnapshot.ConnectionState.ONLINE);
        int operationCount = snapshot.activeOperations().size();
        progressItem.setLabel("会话进度 · " + operationCount + " 项进行中");
        LocalClientPublicCapabilityStore.State capability = connection.publicCapabilitySnapshot();
        if (capability.pendingAvailable() == null) {
            publicCapabilityItem.setLabel("公共能力 · " + shortCommit(capability.activeCommit()) + " · 当前");
            publicCapabilityItem.setEnabled(false);
        } else {
            var available = capability.pendingAvailable();
            publicCapabilityItem.setLabel(
                    "更新公共能力 " + shortCommit(available.sourceCommit()) + " · A" + available.agentCount()
                            + " S" + available.skillCount() + " T" + available.toolCount());
            publicCapabilityItem.setEnabled(
                    snapshot.connectionState() == LocalClientRuntimeSnapshot.ConnectionState.ONLINE);
        }
        Dimension size = systemTray.getTrayIconSize();
        trayIcon.setImage(renderIcon(
                petImage, size.width, size.height, statusColor(snapshot.connectionState())));
        trayIcon.setToolTip("TestAgent 客户端 · " + status);
    }

    /** 目录名称作为默认工作区名称，选择、注册和结果反馈全部在客户端桌面闭环。 */
    private void chooseAndRegisterWorkspace() throws Exception {
        Path selected = LocalClientDesktopActions.chooseDirectory(null);
        if (selected == null) {
            return;
        }
        try {
            LocalClientPayloads.WorkspaceRegistered workspace = connection.registerWorkspace(
                            workspaceName(selected), selected.toString())
                    .get(50, TimeUnit.SECONDS);
            latestWorkspace.set(workspace);
            displayMessage(
                    "工作区注册成功",
                    workspace.name(),
                    TrayIcon.MessageType.INFO);
            try {
                LocalClientDesktopActions.openWeb(LocalClientDesktopActions.workspaceWebUri(
                        configuration.webBaseUri(), workspace.workspaceId()));
            } catch (IOException exception) {
                // 注册事实已经提交成功；浏览器打开失败只提示用户手动点击菜单，不把成功操作误报为失败。
                LOGGER.warn("local_client_workspace_web_open_failed reason={}",
                        exception.getClass().getSimpleName());
                displayMessage(
                        "工作区已注册",
                        "网页未能自动打开，请点击“打开网页”重试",
                        TrayIcon.MessageType.WARNING);
            }
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception resolved) {
                throw resolved;
            }
            throw exception;
        }
    }

    static String workspaceName(Path selected) {
        Path fileName = selected == null ? null : selected.getFileName();
        return fileName == null || fileName.toString().isBlank() ? "本地工作区" : fileName.toString();
    }

    private void showProgress() {
        LocalClientRuntimeSnapshot snapshot = connection.runtimeSnapshot();
        LocalClientPayloads.LifecycleResult process = snapshot.processStatus();
        StringBuilder message = new StringBuilder();
        message.append("连接状态：").append(connectionLabel(snapshot.connectionState())).append('\n');
        if (snapshot.connectedAt() != null) {
            message.append("在线时间：").append(DISPLAY_TIME.format(snapshot.connectedAt())).append('\n');
        }
        message.append("本地服务：").append(processLabel(process)).append('\n');
        message.append("进行中：").append(snapshot.activeOperations().size()).append(" 项");
        Instant now = Instant.now();
        for (LocalClientRuntimeSnapshot.ActiveOperation operation : snapshot.activeOperations()) {
            long seconds = Math.max(0, Duration.between(operation.startedAt(), now).toSeconds());
            message.append('\n').append("• ").append(operationLabel(operation.type()))
                    .append(" · ").append(seconds).append(" 秒");
        }
        JOptionPane.showMessageDialog(
                null, message.toString(), "TestAgent 会话进度", JOptionPane.INFORMATION_MESSAGE);
    }

    private void confirmPublicCapabilityUpdate() {
        LocalClientPayloads.PublicCapabilityAvailable available =
                connection.publicCapabilitySnapshot().pendingAvailable();
        if (available == null) {
            return;
        }
        String message = "Agent " + available.agentCount()
                + " / Skill " + available.skillCount()
                + " / Tool " + available.toolCount() + "\n"
                + changeSummary(available.changeSummaryJson()) + "\n"
                + (available.requiresRestart()
                        ? "包含 Tool 或依赖变化，将重启本地 OpenCode。"
                        : "仅 Agent/Skill 变化，将热加载配置。")
                + "\n公共 Tool 使用当前 macOS 登录账号权限运行，不会提权。";
        int result = JOptionPane.showConfirmDialog(
                null,
                message,
                "更新本地公共能力",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            connection.requestPublicCapabilityUpdate(available.bundleDigest());
            displayMessage("公共能力更新", "已确认，正在下载完整能力包", TrayIcon.MessageType.INFO);
        }
    }

    private static String shortCommit(String commit) {
        return commit == null || commit.isBlank() ? "未初始化" : commit.substring(0, Math.min(12, commit.length()));
    }

    private static String changeSummary(String summaryJson) {
        try {
            var summary = JSON.readTree(summaryJson);
            java.util.List<String> changed = new java.util.ArrayList<>();
            if (summary.path("agentsChanged").asBoolean()) { changed.add("Agent"); }
            if (summary.path("skillsChanged").asBoolean()) { changed.add("Skill"); }
            if (summary.path("toolsChanged").asBoolean()) { changed.add("Tool"); }
            if (summary.path("dependenciesChanged").asBoolean()) { changed.add("依赖"); }
            return changed.isEmpty() ? "内容摘要未变化" : "变更：" + String.join("、", changed);
        } catch (Exception ignored) {
            return "变更摘要暂不可读";
        }
    }

    private void runAction(String action, ThrowingRunnable runnable, String successMessage) {
        actions.execute(() -> {
            try {
                runnable.run();
                if (successMessage != null) {
                    displayMessage("TestAgent 客户端", successMessage, TrayIcon.MessageType.INFO);
                }
            } catch (Exception exception) {
                LOGGER.warn("local_client_tray_action_failed action={} reason={}",
                        action, exception.getClass().getSimpleName());
                displayMessage("操作失败", exception.getMessage(), TrayIcon.MessageType.ERROR);
            }
        });
    }

    private void displayMessage(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null && !closed.get()) {
            EventQueue.invokeLater(() -> trayIcon.displayMessage(
                    title, message == null ? "请查看客户端日志" : message, type));
        }
    }

    static BufferedImage loadPetImage() throws IOException {
        try (InputStream input = LocalClientTray.class.getResourceAsStream(ICON_RESOURCE)) {
            if (input == null) {
                throw new IOException("tray icon resource is missing");
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("tray icon resource is invalid");
            }
            return image;
        }
    }

    static BufferedImage renderIcon(BufferedImage source, int width, int height, Color statusColor) {
        int safeWidth = Math.max(width, 16);
        int safeHeight = Math.max(height, 16);
        BufferedImage result = new BufferedImage(safeWidth, safeHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int badgeSize = Math.max(5, Math.min(safeWidth, safeHeight) / 3);
            int petWidth = safeWidth - 1;
            int petHeight = safeHeight - 1;
            graphics.drawImage(source, 0, 0, petWidth, petHeight, null);
            int x = safeWidth - badgeSize - 1;
            int y = safeHeight - badgeSize - 1;
            graphics.setColor(new Color(255, 255, 255, 230));
            graphics.fillOval(x - 1, y - 1, badgeSize + 2, badgeSize + 2);
            graphics.setColor(statusColor);
            graphics.fillOval(x, y, badgeSize, badgeSize);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static Color statusColor(LocalClientRuntimeSnapshot.ConnectionState state) {
        return switch (state) {
            case ONLINE -> new Color(36, 174, 92);
            case CONNECTING -> new Color(230, 154, 36);
            case OFFLINE -> new Color(214, 69, 65);
            case STOPPING -> new Color(126, 132, 139);
        };
    }

    private static String statusText(
            LocalClientRuntimeSnapshot snapshot,
            LocalClientPayloads.LifecycleResult process) {
        if (snapshot.connectionState() != LocalClientRuntimeSnapshot.ConnectionState.ONLINE) {
            return connectionLabel(snapshot.connectionState());
        }
        return process != null && process.opencodeHealthy() ? "在线 · 本地服务已就绪" : "在线 · 等待会话";
    }

    private static String connectionLabel(LocalClientRuntimeSnapshot.ConnectionState state) {
        return switch (state) {
            case CONNECTING -> "正在连接";
            case ONLINE -> "在线";
            case OFFLINE -> "离线";
            case STOPPING -> "正在退出";
        };
    }

    private static String processLabel(LocalClientPayloads.LifecycleResult process) {
        if (process == null) {
            return "状态未知";
        }
        if (process.opencodeHealthy()) {
            return "已就绪";
        }
        String status = process.processStatus();
        if (status == null) {
            return "状态未知";
        }
        return switch (status) {
            case "RUNNING", "UNHEALTHY" -> "启动中";
            case "FAILED" -> "异常";
            default -> "未启动";
        };
    }

    private static String operationLabel(LocalClientFrameType type) {
        return switch (type) {
            case LIFECYCLE_COMMAND -> "准备本地服务";
            case FILE_REQUEST -> "文件操作";
            case HTTP_REQUEST -> "会话请求";
            default -> "客户端请求";
        };
    }

    private static String platformName() {
        return LocalClientPaths.isMac() ? "macos" : "linux-arm64";
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (updater != null) {
            updater.shutdownNow();
        }
        if (actions != null) {
            actions.shutdownNow();
        }
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
