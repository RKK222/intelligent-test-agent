package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class LocalClientTrayTest {

    @Test
    void packagesPetAssetAndRendersStableTrayDimensions() throws Exception {
        BufferedImage pet = LocalClientTray.loadPetImage();

        BufferedImage icon = LocalClientTray.renderIcon(pet, 22, 22, new Color(36, 174, 92));

        assertThat(pet.getWidth()).isGreaterThan(100);
        assertThat(icon.getWidth()).isEqualTo(22);
        assertThat(icon.getHeight()).isEqualTo(22);
        assertThat(icon.getRGB(19, 19)).isNotZero();
    }

    @Test
    void derivesWorkspaceNameFromTheDirectorySelectedInTheTray() {
        assertThat(LocalClientTray.workspaceName(Path.of("/Users/test/native-project")))
                .isEqualTo("native-project");
        assertThat(LocalClientTray.workspaceName(Path.of("/"))).isEqualTo("本地工作区");
    }

    @Test
    void buildsWorkspaceWebDeepLinkWithoutExposingTheLocalPath() throws Exception {
        URI result = LocalClientDesktopActions.workspaceWebUri(
                URI.create("http://127.0.0.1:3000"), "wrk_local1");

        assertThat(result).hasToString(
                "http://127.0.0.1:3000/workbench?localWorkspaceId=wrk_local1");
        assertThat(result.toString()).doesNotContain("/Users/");
        assertThatThrownBy(() -> LocalClientDesktopActions.workspaceWebUri(
                URI.create("http://127.0.0.1:3000"), "/Users/test/project"))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void rendersConcisePublicCapabilityUpdateStatesWithoutPackageCounts() {
        LocalClientPublicCapabilityStore.State available = capabilityState("SUCCEEDED", true);
        LocalClientPublicCapabilityStore.State updating = capabilityState("DOWNLOADING", true);
        LocalClientPublicCapabilityStore.State failed = capabilityState("FAILED", true);
        LocalClientPublicCapabilityStore.State current = capabilityState("SUCCEEDED", false);

        assertThat(LocalClientTray.publicCapabilityMenuLabel(available)).isEqualTo("公共能力有更新…");
        assertThat(LocalClientTray.publicCapabilityMenuLabel(updating)).isEqualTo("公共能力更新中…");
        assertThat(LocalClientTray.publicCapabilityMenuLabel(failed)).isEqualTo("公共能力更新失败，点击重试…");
        assertThat(LocalClientTray.publicCapabilityMenuLabel(current)).isEqualTo("公共能力已是最新");
        assertThat(LocalClientTray.publicCapabilityMenuEnabled(available, true)).isTrue();
        assertThat(LocalClientTray.publicCapabilityMenuEnabled(updating, true)).isFalse();
        assertThat(LocalClientTray.publicCapabilityMenuEnabled(available, false)).isFalse();
        assertThat(LocalClientTray.publicCapabilityMenuLabel(available))
                .doesNotContain("A17", "S18", "T10");
    }

    @Test
    void buildsThemedSwingPopupWithTheExistingTrayActions() {
        LocalClientDesktopTheme.install();
        LocalClientTrayPopup popup = new LocalClientTrayPopup(
                new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));

        assertThat(popup.component()).isInstanceOf(JPanel.class);
        assertThat(popup.actionItem(LocalClientTrayPopup.Action.OPEN_WEB).getText()).isEqualTo("打开网页");
        assertThat(popup.actionItem(LocalClientTrayPopup.Action.REGISTER_WORKSPACE).getText())
                .isEqualTo("选择并注册工作区…");
        assertThat(popup.actionItem(LocalClientTrayPopup.Action.REGISTER_WORKSPACE).isEnabled()).isFalse();
        assertThat(popup.actionItem(LocalClientTrayPopup.Action.OPEN_WEB)
                .getClientProperty(FlatClientProperties.BUTTON_TYPE))
                .isEqualTo(FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);

        popup.setStatus("在线 · 等待会话");
        popup.setWorkspaceEnabled(true);
        popup.setProgressLabel("会话进度 · 2 项进行中");
        AtomicInteger openWebCalls = new AtomicInteger();
        popup.bind(LocalClientTrayPopup.Action.OPEN_WEB, openWebCalls::incrementAndGet);
        popup.actionItem(LocalClientTrayPopup.Action.OPEN_WEB).doClick();

        assertThat(popup.statusText()).isEqualTo("在线 · 等待会话");
        assertThat(popup.actionItem(LocalClientTrayPopup.Action.REGISTER_WORKSPACE).isEnabled()).isTrue();
        assertThat(popup.actionItem(LocalClientTrayPopup.Action.SHOW_PROGRESS).getText())
                .isEqualTo("会话进度 · 2 项进行中");
        assertThat(openWebCalls).hasValue(1);
        popup.close();
    }

    @Test
    void positionsThePopupInsideTheCurrentScreenForTopAndBottomTrays() {
        Rectangle usable = new Rectangle(0, 24, 1440, 876);
        Dimension menu = new Dimension(296, 420);

        assertThat(LocalClientTrayPopup.popupLocation(new Point(720, 24), menu, usable))
                .isEqualTo(new Point(572, 34));
        assertThat(LocalClientTrayPopup.popupLocation(new Point(720, 2), menu, usable))
                .isEqualTo(new Point(572, 32));
        assertThat(LocalClientTrayPopup.popupLocation(new Point(720, 899), menu, usable))
                .isEqualTo(new Point(572, 469));
        assertThat(LocalClientTrayPopup.popupLocation(new Point(2, 24), menu, usable).x).isEqualTo(8);
        assertThat(LocalClientTrayPopup.popupLocation(new Point(1439, 24), menu, usable).x)
                .isEqualTo(1136);
    }

    private static LocalClientPublicCapabilityStore.State capabilityState(String status, boolean hasUpdate) {
        var pending = hasUpdate
                ? new LocalClientPayloads.PublicCapabilityAvailable(
                        "lci_tray_test", 7L, "c".repeat(40), "d".repeat(64),
                        17, 18, 10, true, "{\"toolsChanged\":true}")
                : null;
        return new LocalClientPublicCapabilityStore.State(
                "a".repeat(40), "b".repeat(64), null, status, null,
                pending, null,
                pending == null ? null : pending.sourceCommit(),
                pending == null ? null : pending.bundleDigest(),
                Instant.parse("2026-08-23T12:00:00Z"));
    }
}
