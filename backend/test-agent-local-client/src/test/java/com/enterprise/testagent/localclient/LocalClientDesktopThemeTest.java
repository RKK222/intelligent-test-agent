package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import org.junit.jupiter.api.Test;

class LocalClientDesktopThemeTest {

    @Test
    void keepsMacSystemThemeAndPrefersBundledNimbusOnLinux() {
        UIManager.LookAndFeelInfo[] installed = {
                new UIManager.LookAndFeelInfo("Metal", "javax.swing.plaf.metal.MetalLookAndFeel"),
                new UIManager.LookAndFeelInfo("Nimbus", "javax.swing.plaf.nimbus.NimbusLookAndFeel")
        };

        assertThat(LocalClientDesktopTheme.preferredLookAndFeelClassName(
                true, installed, "com.apple.laf.AquaLookAndFeel"))
                .isEqualTo("com.apple.laf.AquaLookAndFeel");
        assertThat(LocalClientDesktopTheme.preferredLookAndFeelClassName(
                false, installed, "javax.swing.plaf.metal.MetalLookAndFeel"))
                .isEqualTo("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    }

    @Test
    void appliesSharedTypographySpacingAndTestAgentAccent() {
        UIDefaults defaults = new UIDefaults();
        defaults.put("Label.font", new FontUIResource(Font.DIALOG, Font.PLAIN, 11));
        defaults.put("Button.font", new FontUIResource(Font.DIALOG, Font.PLAIN, 11));

        LocalClientDesktopTheme.applyDefaults(defaults);

        assertThat((Font) defaults.get("Label.font")).extracting(Font::getSize).isEqualTo(14);
        assertThat((Font) defaults.get("Button.font")).extracting(Font::getSize).isEqualTo(14);
        assertThat((Color) defaults.get("nimbusFocus")).isEqualTo(new Color(190, 30, 45));
        assertThat(defaults.getInsets("Button.margin")).isEqualTo(new java.awt.Insets(8, 18, 8, 18));
        Border border = defaults.getBorder("OptionPane.border");
        assertThat(border.getBorderInsets(null)).isEqualTo(new java.awt.Insets(18, 22, 14, 22));
    }
}
