package com.enterprise.testagent.localclient;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * 与工作区选择窗口共用 FlatLaf 的托盘弹层。
 * 原生 AWT PopupMenu 不消费 Swing 主题，因此只保留 SystemTray 图标，菜单由此处统一渲染。
 */
final class LocalClientTrayPopup implements AutoCloseable {

    private static final int MENU_WIDTH = 296;
    private static final int MENU_ARC = 14;
    private static final int SCREEN_EDGE_GAP = 8;
    private static final int TRAY_ANCHOR_GAP = 10;

    enum Action {
        OPEN_WEB,
        REGISTER_WORKSPACE,
        RECONNECT,
        UPDATE_PUBLIC_CAPABILITIES,
        VIEW_LOGS,
        DOWNLOAD_LOGS,
        SHOW_PROGRESS,
        EXIT
    }

    private final JPanel content = new JPanel();
    private final JLabel statusLabel = new JLabel("正在连接");
    private final Map<Action, JButton> items = new EnumMap<>(Action.class);
    private JWindow window;
    private boolean focusObserved;

    LocalClientTrayPopup(BufferedImage petImage) {
        content.setName("localClientTrayPopup");
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(color("PopupMenu.background", Color.WHITE));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color("PopupMenu.borderColor", new Color(218, 222, 229)), 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        content.add(createHeader(petImage));
        addSeparator();
        add(Action.OPEN_WEB, "打开网页");
        add(Action.REGISTER_WORKSPACE, "选择并注册工作区…").setEnabled(false);
        add(Action.RECONNECT, "重连");
        addSeparator();
        add(Action.UPDATE_PUBLIC_CAPABILITIES, "公共能力 · 暂无更新").setEnabled(false);
        add(Action.VIEW_LOGS, "查看日志");
        add(Action.DOWNLOAD_LOGS, "下载日志");
        add(Action.SHOW_PROGRESS, "会话进度 · 0 项进行中");
        addSeparator();
        add(Action.EXIT, "退出客户端");
    }

    private JPanel createHeader(BufferedImage petImage) {
        JPanel header = new JPanel();
        header.setName("localClientTrayHeader");
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        header.setPreferredSize(new Dimension(MENU_WIDTH, 56));
        header.setMaximumSize(new Dimension(MENU_WIDTH, 56));
        header.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JLabel icon = new JLabel(new ImageIcon(petImage.getScaledInstance(36, 36, Image.SCALE_SMOOTH)));
        icon.setAlignmentY(0.5f);
        header.add(icon);
        header.add(Box.createHorizontalStrut(10));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("TestAgent 客户端");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        title.setForeground(color("Label.foreground", new Color(31, 41, 55)));
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        statusLabel.setForeground(color("Label.disabledForeground", new Color(107, 114, 128)));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        text.add(title);
        text.add(Box.createVerticalStrut(3));
        text.add(statusLabel);
        header.add(text);
        header.add(Box.createHorizontalGlue());
        return header;
    }

    private JButton add(Action action, String label) {
        JButton item = new JButton(label);
        item.setName("localClientTray." + action.name().toLowerCase(java.util.Locale.ROOT));
        item.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        item.setHorizontalAlignment(SwingConstants.LEFT);
        item.setMargin(new Insets(7, 12, 7, 12));
        item.setPreferredSize(new Dimension(MENU_WIDTH, 38));
        item.setMaximumSize(new Dimension(MENU_WIDTH, 38));
        item.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        item.setFocusPainted(false);
        items.put(action, item);
        content.add(item);
        return item;
    }

    private void addSeparator() {
        content.add(Box.createVerticalStrut(4));
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        separator.setMaximumSize(new Dimension(MENU_WIDTH, 1));
        content.add(separator);
        content.add(Box.createVerticalStrut(4));
    }

    void bind(Action action, Runnable callback) {
        item(action).addActionListener(event -> {
            hide();
            callback.run();
        });
    }

    void setStatus(String status) {
        statusLabel.setText(status);
    }

    void setWorkspaceEnabled(boolean enabled) {
        item(Action.REGISTER_WORKSPACE).setEnabled(enabled);
    }

    void setProgressLabel(String label) {
        item(Action.SHOW_PROGRESS).setText(label);
    }

    void setPublicCapability(String label, boolean enabled) {
        JButton item = item(Action.UPDATE_PUBLIC_CAPABILITIES);
        item.setText(label);
        item.setEnabled(enabled);
    }

    /** 在托盘图标点击点附近切换弹层；顶部托盘向下展开，底部托盘自动向上展开。 */
    void toggleAt(Point trayPoint) {
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(() -> toggleAt(trayPoint));
            return;
        }
        ensureWindow();
        if (window.isVisible()) {
            hide();
            return;
        }
        focusObserved = false;
        window.pack();
        Point location = popupLocation(trayPoint, window.getSize(), usableScreenBounds(trayPoint));
        window.setLocation(location);
        applyRoundedShape();
        window.setVisible(true);
        window.toFront();
        window.requestFocus();
    }

    static Point popupLocation(Point trayPoint, Dimension popupSize, Rectangle usableBounds) {
        int minimumX = usableBounds.x + SCREEN_EDGE_GAP;
        int maximumX = usableBounds.x + usableBounds.width - popupSize.width - SCREEN_EDGE_GAP;
        int centeredX = trayPoint.x - popupSize.width / 2;
        int x = Math.max(minimumX, Math.min(centeredX, Math.max(minimumX, maximumX)));

        int minimumY = usableBounds.y + SCREEN_EDGE_GAP;
        int maximumY = Math.max(
                minimumY,
                usableBounds.y + usableBounds.height - popupSize.height - SCREEN_EDGE_GAP);
        // macOS 状态栏坐标位于可用工作区上方，向下展开时也必须夹在菜单栏之后。
        int below = Math.max(minimumY, trayPoint.y + TRAY_ANCHOR_GAP);
        int above = Math.max(
                minimumY,
                Math.min(trayPoint.y - popupSize.height - TRAY_ANCHOR_GAP, maximumY));
        int y = below <= maximumY ? below : Math.max(minimumY, above);
        return new Point(x, y);
    }

    private void ensureWindow() {
        if (window != null) {
            return;
        }
        window = new JWindow();
        window.setName("localClientTrayWindow");
        window.setType(Window.Type.POPUP);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(true);
        window.setAutoRequestFocus(true);
        try {
            window.setBackground(new Color(0, 0, 0, 0));
        } catch (UnsupportedOperationException ignored) {
            // 无桌面合成器时保留不透明白色背景，不能因此让整个托盘能力失效。
            window.setBackground(content.getBackground());
        }
        window.setContentPane(content);
        window.getRootPane().registerKeyboardAction(
                event -> hide(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        window.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
                focusObserved = true;
            }

            @Override
            public void windowLostFocus(WindowEvent event) {
                // 首次显示前的系统焦点切换不能关窗；真正取得焦点后，点击外部才收起。
                if (focusObserved) {
                    EventQueue.invokeLater(() -> {
                        if (window != null && !window.isFocused()) {
                            hide();
                        }
                    });
                }
            }
        });
    }

    private void applyRoundedShape() {
        try {
            window.setShape(new RoundRectangle2D.Double(
                    0, 0, window.getWidth(), window.getHeight(), MENU_ARC, MENU_ARC));
        } catch (UnsupportedOperationException ignored) {
            // 不支持窗口裁剪的 Linux 桌面只降级为直角卡片，不影响托盘动作。
        }
    }

    private static Rectangle usableScreenBounds(Point point) {
        GraphicsConfiguration selected = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration configuration = device.getDefaultConfiguration();
            if (configuration.getBounds().contains(point)) {
                selected = configuration;
                break;
            }
        }
        Rectangle bounds = new Rectangle(selected.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(selected);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private JButton item(Action action) {
        JButton item = items.get(action);
        if (item == null) {
            throw new IllegalArgumentException("未知托盘动作: " + action);
        }
        return item;
    }

    JPanel component() {
        return content;
    }

    JButton actionItem(Action action) {
        return item(action);
    }

    String statusText() {
        return statusLabel.getText();
    }

    private void hide() {
        if (window != null) {
            window.setVisible(false);
        }
    }

    @Override
    public void close() {
        Runnable dispose = () -> {
            if (window != null) {
                window.dispose();
                window = null;
            }
        };
        if (EventQueue.isDispatchThread()) {
            dispose.run();
        } else {
            EventQueue.invokeLater(dispose);
        }
    }

    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }
}
