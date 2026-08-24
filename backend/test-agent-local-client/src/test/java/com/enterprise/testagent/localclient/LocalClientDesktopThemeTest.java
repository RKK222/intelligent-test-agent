package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Font;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import org.junit.jupiter.api.Test;

class LocalClientDesktopThemeTest {

    @Test
    void installsBundledFlatLightThemeInsteadOfPlatformNativeTheme() {
        assertThat(LocalClientDesktopTheme.install()).isTrue();
        assertThat(UIManager.getLookAndFeel()).isInstanceOf(FlatLightLaf.class);
    }

    @Test
    void appliesSharedTypographySpacingAndTestAgentAccent() {
        UIDefaults defaults = new UIDefaults();
        defaults.put("Label.font", new FontUIResource(Font.DIALOG, Font.PLAIN, 11));
        defaults.put("Button.font", new FontUIResource(Font.DIALOG, Font.PLAIN, 11));

        LocalClientDesktopTheme.applyDefaults(defaults);

        assertThat((Font) defaults.get("Label.font")).extracting(Font::getSize).isEqualTo(14);
        assertThat((Font) defaults.get("Button.font")).extracting(Font::getSize).isEqualTo(14);
        assertThat((Color) defaults.get("Component.focusColor")).isEqualTo(new Color(49, 91, 125));
        assertThat((Color) defaults.get("Button.default.background")).isEqualTo(new Color(49, 91, 125));
        assertThat(defaults.getInt("Component.arc")).isEqualTo(12);
        assertThat(defaults.getInt("TextComponent.arc")).isEqualTo(12);
        assertThat(defaults.getInsets("Button.margin")).isEqualTo(new java.awt.Insets(8, 18, 8, 18));
        Border border = defaults.getBorder("OptionPane.border");
        assertThat(border.getBorderInsets(null)).isEqualTo(new java.awt.Insets(18, 22, 14, 22));
    }
}
