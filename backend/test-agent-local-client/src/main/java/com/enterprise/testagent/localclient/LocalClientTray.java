package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
    private final LocalClientTrayPopup popup;
    private final BufferedImage petImage;
    private final ScheduledExecutorService updater;
    private final ExecutorService actions;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong lastPopupTriggerNanos = new AtomicLong();
    private final AtomicReference<String> lastUiKey = new AtomicReference<>();
    private final AtomicReference<String> lastPublicCapabilityStatus = new AtomicReference<>();
    private final AtomicReference<String> lastPublicCapabilityDigest = new AtomicReference<>();
    private final AtomicReference<LocalClientPayloads.WorkspaceRegistered> latestWorkspace = new AtomicReference<>();

    private LocalClientTray(
            LocalClientConfiguration configuration,
            LocalClientConnection connection,
            SystemTray systemTray,
            TrayIcon trayIcon,
            LocalClientTrayPopup popup,
            BufferedImage petImage,
            ScheduledExecutorService updater,
            ExecutorService actions) {
        this.configuration = configuration;
        this.connection = connection;
        this.systemTray = systemTray;
        this.trayIcon = trayIcon;
        this.popup = popup;
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
            LocalClientTrayPopup popup = new LocalClientTrayPopup(pet);
            TrayIcon icon = new TrayIcon(
                    renderIcon(
                            pet,
                            size.width,
                            size.height,
                            statusColor(LocalClientRuntimeSnapshot.ConnectionState.CONNECTING)),
                    "TestAgent 客户端 · 正在连接");
            icon.setImageAutoSize(true);
            ScheduledExecutorService updater = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("local-client-tray-status-", 0).factory());
            ExecutorService actions = Executors.newCachedThreadPool(
                    Thread.ofPlatform().daemon().name("local-client-tray-action-", 0).factory());
            LocalClientTray result = new LocalClientTray(
                    configuration, connection, tray, icon, popup, pet, updater, actions);
            result.bindActions();
            result.bindTrayClick();
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
                configuration, connection, null, null, null, null, null, null);
    }

    private void bindActions() {
        popup.bind(LocalClientTrayPopup.Action.OPEN_WEB, () -> runAction("open_web", () -> {
            LocalClientPayloads.WorkspaceRegistered workspace = latestWorkspace.get();
            LocalClientDesktopActions.openWeb(workspace == null
                    ? configuration.webBaseUri()
                    : LocalClientDesktopActions.workspaceWebUri(configuration.webBaseUri(), workspace.workspaceId()));
        }, null));
        popup.bind(LocalClientTrayPopup.Action.REGISTER_WORKSPACE, () -> runAction(
                "register_workspace", this::chooseAndRegisterWorkspace, null));
        popup.bind(LocalClientTrayPopup.Action.RECONNECT, () -> {
            connection.reconnect();
            displayMessage("TestAgent 客户端", "正在重新连接", TrayIcon.MessageType.INFO);
        });
        popup.bind(LocalClientTrayPopup.Action.UPDATE_PUBLIC_CAPABILITIES, () -> runAction(
                "public_capability_update", this::confirmPublicCapabilityUpdate, null));
        popup.bind(LocalClientTrayPopup.Action.VIEW_LOGS, () -> runAction("view_logs", () ->
                LocalClientDesktopActions.openDirectory(LocalClientPaths.logsDirectory()), null));
        popup.bind(LocalClientTrayPopup.Action.DOWNLOAD_LOGS, () -> runAction("download_logs", () -> {
            Path archive = LocalClientLogExporter.export(
                    LocalClientPaths.logsDirectory(), LocalClientPaths.downloadsDirectory());
            LocalClientDesktopActions.revealFile(archive);
            displayMessage("日志已下载", archive.getFileName().toString(), TrayIcon.MessageType.INFO);
        }, null));
        popup.bind(LocalClientTrayPopup.Action.SHOW_PROGRESS, this::showProgress);
        popup.bind(LocalClientTrayPopup.Action.EXIT, () -> actions.execute(() -> {
            LOGGER.info("local_client_exit_requested source=tray");
            connection.close();
        }));
    }

    private void bindTrayClick() {
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    togglePopupAt(new Point(event.getXOnScreen(), event.getYOnScreen()));
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger() || event.getButton() == MouseEvent.BUTTON1) {
                    togglePopupAt(new Point(event.getXOnScreen(), event.getYOnScreen()));
                }
            }
        });
        // macOS 菜单栏和部分 Linux 桌面只派发 TrayIcon 的标准动作事件，不一定下发鼠标事件。
        trayIcon.addActionListener(event -> {
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if (pointerInfo != null) {
                togglePopupAt(pointerInfo.getLocation());
            }
        });
    }

    private void togglePopupAt(Point point) {
        long now = System.nanoTime();
        long previous = lastPopupTriggerNanos.getAndSet(now);
        if (now - previous < TimeUnit.MILLISECONDS.toNanos(180)) {
            return;
        }
        popup.toggleAt(point);
    }

    private void refreshSafely() {
        if (closed.get()) {
            return;
        }
        try {
            LocalClientRuntimeSnapshot snapshot = connection.runtimeSnapshot();
            LocalClientPublicCapabilityStore.State capability = connection.publicCapabilitySnapshot();
            String previousCapabilityStatus = lastPublicCapabilityStatus.getAndSet(capability.status());
            String previousCapabilityDigest = lastPublicCapabilityDigest.getAndSet(capability.activeDigest());
            String uiKey = snapshot.connectionState() + ":" + snapshot.activeOperations().size() + ":"
                    + processLabel(snapshot.processStatus()) + ":" + capability.status() + ":"
                    + (capability.activeDigest() == null ? "" : capability.activeDigest()) + ":"
                    + (capability.pendingAvailable() == null ? "" : capability.pendingAvailable().bundleDigest());
            if (uiKey.equals(lastUiKey.getAndSet(uiKey))) {
                return;
            }
            EventQueue.invokeLater(() -> {
                applySnapshot(snapshot);
                notifyPublicCapabilityTransition(
                        previousCapabilityStatus, previousCapabilityDigest, capability);
            });
        } catch (RuntimeException exception) {
            LOGGER.debug("local_client_tray_refresh_failed reason={}", exception.getClass().getSimpleName());
        }
    }

    private void applySnapshot(LocalClientRuntimeSnapshot snapshot) {
        if (closed.get() || trayIcon == null) {
            return;
        }
        String status = statusText(snapshot, snapshot.processStatus());
        popup.setStatus(status);
        popup.setWorkspaceEnabled(snapshot.connectionState() == LocalClientRuntimeSnapshot.ConnectionState.ONLINE);
        int operationCount = snapshot.activeOperations().size();
        popup.setProgressLabel("会话进度 · " + operationCount + " 项进行中");
        LocalClientPublicCapabilityStore.State capability = connection.publicCapabilitySnapshot();
        popup.setPublicCapability(
                publicCapabilityMenuLabel(capability),
                publicCapabilityMenuEnabled(
                        capability, snapshot.connectionState() == LocalClientRuntimeSnapshot.ConnectionState.ONLINE));
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
                null, message.toString(), "TestAgent 会话进度", JOptionPane.PLAIN_MESSAGE);
    }

    private void confirmPublicCapabilityUpdate() {
        LocalClientPayloads.PublicCapabilityAvailable available =
                connection.publicCapabilitySnapshot().pendingAvailable();
        if (available == null) {
            return;
        }
        String message = "检测到新的公共能力版本。\n"
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
                JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            connection.requestPublicCapabilityUpdate(available.bundleDigest());
            displayMessage("公共能力更新", "已确认，正在下载完整能力包", TrayIcon.MessageType.INFO);
        }
    }

    static String publicCapabilityMenuLabel(LocalClientPublicCapabilityStore.State capability) {
        if (capability.pendingAvailable() == null) {
            return "公共能力已是最新";
        }
        if (isPublicCapabilityUpdateInProgress(capability.status())) {
            return "公共能力更新中…";
        }
        if (isPublicCapabilityUpdateFailed(capability.status())) {
            return "公共能力更新失败，点击重试…";
        }
        return "公共能力有更新…";
    }

    static boolean publicCapabilityMenuEnabled(
            LocalClientPublicCapabilityStore.State capability,
            boolean online) {
        return online
                && capability.pendingAvailable() != null
                && !isPublicCapabilityUpdateInProgress(capability.status());
    }

    private void notifyPublicCapabilityTransition(
            String previousStatus,
            String previousDigest,
            LocalClientPublicCapabilityStore.State current) {
        if (previousStatus == null) {
            return;
        }
        if ("SUCCEEDED".equals(current.status())
                && current.pendingAvailable() == null
                && (!"SUCCEEDED".equals(previousStatus)
                        || !Objects.equals(previousDigest, current.activeDigest()))) {
            displayMessage("公共能力更新", "更新完成，可以继续使用", TrayIcon.MessageType.INFO);
            return;
        }
        if (isPublicCapabilityUpdateFailed(current.status())
                && !current.status().equals(previousStatus)) {
            displayMessage("公共能力更新失败", "请点击托盘菜单重试或查看日志", TrayIcon.MessageType.ERROR);
        }
    }

    private static boolean isPublicCapabilityUpdateInProgress(String status) {
        return "PENDING".equals(status) || "DOWNLOADING".equals(status) || "APPLYING".equals(status);
    }

    private static boolean isPublicCapabilityUpdateFailed(String status) {
        return "FAILED".equals(status) || "ROLLED_BACK".equals(status);
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
        return LocalClientPlatform.current().displayName();
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
        if (popup != null) {
            popup.close();
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
