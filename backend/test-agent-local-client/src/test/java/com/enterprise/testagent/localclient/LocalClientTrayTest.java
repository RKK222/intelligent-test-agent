package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
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
}
