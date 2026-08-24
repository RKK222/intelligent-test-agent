package com.enterprise.testagent.localclient;

import com.formdev.flatlaf.FlatLightLaf;
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
 * 所有桌面平台统一使用随客户端打包的 FlatLaf，不依赖 JDK 或操作系统原生主题。
 */
final class LocalClientDesktopTheme {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientDesktopTheme.class);
    private static final ColorUIResource SURFACE = new ColorUIResource(248, 249, 251);
    private static final ColorUIResource FIELD = new ColorUIResource(255, 255, 255);
    private static final ColorUIResource TEXT = new ColorUIResource(31, 41, 55);
    private static final ColorUIResource MUTED = new ColorUIResource(107, 114, 128);
    private static final ColorUIResource ACCENT = new ColorUIResource(49, 91, 125);
    private static final ColorUIResource ACCENT_HOVER = new ColorUIResource(39, 76, 105);
    private static final ColorUIResource ACCENT_PRESSED = new ColorUIResource(31, 62, 86);
    private static final ColorUIResource BORDER = new ColorUIResource(218, 222, 229);
    private static final ColorUIResource DISABLED_BORDER = new ColorUIResource(232, 234, 239);
    private static final FontUIResource DEFAULT_FONT = new FontUIResource(Font.DIALOG, Font.PLAIN, 14);

    private LocalClientDesktopTheme() {
    }

    static boolean install() {
        if (!FlatLightLaf.setup()) {
            // 外观失败不能阻断反向连接；记录固定原因，不回退选择另一套平台原生主题。
            LOGGER.warn("local_client_desktop_theme_unavailable lookAndFeel={}",
                    FlatLightLaf.class.getName());
            return false;
        }
        applyDefaults(UIManager.getDefaults());
        LOGGER.info("local_client_desktop_theme_installed lookAndFeel={}",
                FlatLightLaf.class.getName());
        return true;
    }

    /** 统一字体、圆角、表面层级和主操作色，保证高分屏下仍然清晰紧凑。 */
    static void applyDefaults(UIDefaults defaults) {
        defaults.keySet().stream()
                .filter(key -> key instanceof String value && value.endsWith(".font"))
                .toList()
                .forEach(key -> defaults.put(key, DEFAULT_FONT));

        defaults.put("OptionPane.background", SURFACE);
        defaults.put("Panel.background", SURFACE);
        defaults.put("Label.foreground", TEXT);
        defaults.put("TextField.background", FIELD);
        defaults.put("PasswordField.background", FIELD);
        defaults.put("TextField.foreground", TEXT);
        defaults.put("PasswordField.foreground", TEXT);
        defaults.put("TextField.inactiveForeground", MUTED);
        defaults.put("PasswordField.inactiveForeground", MUTED);

        // FlatLaf 客户端级 token：白色卡片、轻边框、12px 圆角，默认操作使用克制的深蓝灰。
        defaults.put("Component.arc", 12);
        defaults.put("Component.focusWidth", 1);
        defaults.put("Component.innerFocusWidth", 0);
        defaults.put("Component.focusColor", ACCENT);
        defaults.put("Component.borderColor", BORDER);
        defaults.put("Component.focusedBorderColor", ACCENT);
        defaults.put("Component.disabledBorderColor", DISABLED_BORDER);
        defaults.put("Button.arc", 12);
        defaults.put("Button.background", FIELD);
        defaults.put("Button.foreground", TEXT);
        defaults.put("Button.default.background", ACCENT);
        defaults.put("Button.default.foreground", new ColorUIResource(Color.WHITE));
        defaults.put("Button.default.hoverBackground", ACCENT_HOVER);
        defaults.put("Button.default.pressedBackground", ACCENT_PRESSED);
        defaults.put("Button.default.focusColor", ACCENT);
        defaults.put("TextComponent.arc", 12);
        defaults.put("ScrollBar.width", 10);
        defaults.put("ScrollBar.thumbArc", 999);
        defaults.put("ProgressBar.arc", 999);

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
