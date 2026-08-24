package com.enterprise.testagent.localclient;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端桌面窗口的统一视觉入口。
 * macOS 继续使用原生 Aqua；Linux 使用 JDK 自带 Nimbus，避免回退到老旧 Metal 风格。
 */
final class LocalClientDesktopTheme {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientDesktopTheme.class);
    private static final ColorUIResource SURFACE = new ColorUIResource(248, 249, 251);
    private static final ColorUIResource FIELD = new ColorUIResource(255, 255, 255);
    private static final ColorUIResource TEXT = new ColorUIResource(31, 41, 55);
    private static final ColorUIResource MUTED = new ColorUIResource(107, 114, 128);
    private static final ColorUIResource ACCENT = new ColorUIResource(190, 30, 45);
    private static final FontUIResource DEFAULT_FONT = new FontUIResource(Font.DIALOG, Font.PLAIN, 14);

    private LocalClientDesktopTheme() {
    }

    static void install() {
        try {
            String lookAndFeel = preferredLookAndFeelClassName(
                    LocalClientPaths.isMac(),
                    UIManager.getInstalledLookAndFeels(),
                    UIManager.getSystemLookAndFeelClassName());
            UIManager.setLookAndFeel(lookAndFeel);
            applyDefaults(UIManager.getDefaults());
            LOGGER.info("local_client_desktop_theme_installed lookAndFeel={}", lookAndFeel);
        } catch (Exception exception) {
            // 主题失败不能阻断客户端连接；继续使用当前 LookAndFeel，并尽量应用字体和间距。
            applyDefaults(UIManager.getDefaults());
            LOGGER.warn("local_client_desktop_theme_fallback reason={}",
                    exception.getClass().getSimpleName());
        }
    }

    static String preferredLookAndFeelClassName(
            boolean mac,
            UIManager.LookAndFeelInfo[] installed,
            String systemLookAndFeel) {
        if (mac) {
            return systemLookAndFeel;
        }
        for (UIManager.LookAndFeelInfo candidate : installed) {
            if ("Nimbus".equals(candidate.getName())) {
                return candidate.getClassName();
            }
        }
        return systemLookAndFeel;
    }

    /** 统一字体、表面层级、主色和触控间距，保证高分屏下仍然清晰紧凑。 */
    static void applyDefaults(UIDefaults defaults) {
        defaults.keySet().stream()
                .filter(key -> key instanceof String value && value.endsWith(".font"))
                .toList()
                .forEach(key -> defaults.put(key, DEFAULT_FONT));

        defaults.put("control", SURFACE);
        defaults.put("info", FIELD);
        defaults.put("text", TEXT);
        defaults.put("nimbusBase", ACCENT);
        defaults.put("nimbusFocus", ACCENT);
        defaults.put("nimbusSelectionBackground", ACCENT);
        defaults.put("nimbusLightBackground", FIELD);
        defaults.put("OptionPane.background", SURFACE);
        defaults.put("Panel.background", SURFACE);
        defaults.put("Label.foreground", TEXT);
        defaults.put("TextField.background", FIELD);
        defaults.put("PasswordField.background", FIELD);
        defaults.put("TextField.foreground", TEXT);
        defaults.put("PasswordField.foreground", TEXT);
        defaults.put("TextField.inactiveForeground", MUTED);
        defaults.put("PasswordField.inactiveForeground", MUTED);

        defaults.put("Button.margin", new InsetsUIResource(8, 18, 8, 18));
        defaults.put("TextField.margin", new InsetsUIResource(8, 10, 8, 10));
        defaults.put("PasswordField.margin", new InsetsUIResource(8, 10, 8, 10));
        defaults.put("OptionPane.minimumSize", new java.awt.Dimension(420, 180));
        defaults.put("OptionPane.border", emptyBorder(18, 22, 14, 22));
        defaults.put("OptionPane.messageAreaBorder", emptyBorder(4, 4, 12, 4));
        defaults.put("OptionPane.buttonAreaBorder", emptyBorder(8, 4, 2, 4));
    }

    private static Border emptyBorder(int top, int left, int bottom, int right) {
        Insets insets = new Insets(top, left, bottom, right);
        return BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right);
    }
}
