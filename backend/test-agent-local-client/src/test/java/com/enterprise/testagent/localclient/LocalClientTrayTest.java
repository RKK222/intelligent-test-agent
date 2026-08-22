package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Path;
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
}
