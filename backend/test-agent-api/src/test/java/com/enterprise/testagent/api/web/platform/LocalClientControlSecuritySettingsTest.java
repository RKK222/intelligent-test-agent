package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.PlatformException;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class LocalClientControlSecuritySettingsTest {

    @Test
    void acceptsDirectSecureTransport() {
        LocalClientControlSecuritySettings settings = settings(false);

        assertThatCode(() -> settings.requireSecure(
                        URI.create("wss://platform.example/ws"), new HttpHeaders(), remote("203.0.113.8")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsForwardedSecureTransportOnlyFromTrustedProxy() {
        LocalClientControlSecuritySettings settings = settings(false);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-Proto", "https");

        assertThatCode(() -> settings.requireSecure(
                        URI.create("http://backend/model"), headers, remote("127.0.0.1")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> settings.requireSecure(
                        URI.create("http://backend/model"), headers, remote("203.0.113.8")))
                .isInstanceOf(PlatformException.class);
    }

    @Test
    void rejectsPlaintextAndInvalidTrustedProxyConfiguration() {
        LocalClientControlSecuritySettings settings = settings(false);

        assertThatThrownBy(() -> settings.requireSecure(
                        URI.create("http://backend/model"), new HttpHeaders(), remote("127.0.0.1")))
                .isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> new LocalClientControlSecuritySettings(false, "proxy.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitDevelopmentSwitchAllowsPlaintext() {
        LocalClientControlSecuritySettings settings = settings(true);

        assertThatCode(() -> settings.requireSecure(
                        URI.create("http://127.0.0.1/ws"), new HttpHeaders(), remote("127.0.0.1")))
                .doesNotThrowAnyException();
    }

    @Test
    void resolvesNearestUntrustedForwardedAddressOnlyBehindTrustedProxy() {
        LocalClientControlSecuritySettings settings =
                new LocalClientControlSecuritySettings(false, "127.0.0.1,10.0.0.2");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "198.51.100.77, 203.0.113.8, 10.0.0.2");

        assertThat(settings.resolveClientAddress(headers, remote("127.0.0.1")))
                .isEqualTo("203.0.113.8");
    }

    @Test
    void ignoresSpoofedForwardedAddressFromDirectClient() {
        LocalClientControlSecuritySettings settings = settings(false);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "198.51.100.77");

        assertThat(settings.resolveClientAddress(headers, remote("203.0.113.8")))
                .isEqualTo("203.0.113.8");
    }

    private static LocalClientControlSecuritySettings settings(boolean allowInsecure) {
        return new LocalClientControlSecuritySettings(allowInsecure, "127.0.0.1,::1");
    }

    private static InetSocketAddress remote(String address) {
        return new InetSocketAddress(address, 12345);
    }
}
