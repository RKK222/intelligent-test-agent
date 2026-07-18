package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import org.junit.jupiter.api.Test;

class CurrentBackendWebSocketUrlFactoryTest {

    @Test
    void buildsAbsoluteUrlForTicketIssuer() {
        assertThat(factory("http://122.233.30.114:8080").absoluteUrl("/api/tickets/ws?ticket=one"))
                .isEqualTo("ws://122.233.30.114:8080/api/tickets/ws?ticket=one");
        assertThat(factory("https://backend.example/internal/").absoluteUrl("/api/tickets/ws?ticket=two"))
                .isEqualTo("wss://backend.example/internal/api/tickets/ws?ticket=two");
    }

    @Test
    void rejectsRelativeOrUnsupportedListenUrl() {
        assertThatThrownBy(() -> factory("122.233.30.114:8080").absoluteUrl("/api/tickets/ws"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> factory("ftp://122.233.30.114:8080").absoluteUrl("/api/tickets/ws"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildsServerTerminalUrlFromConfiguredWssGateway() {
        CurrentBackendWebSocketUrlFactory factory = new CurrentBackendWebSocketUrlFactory(
                mock(BackendInstanceIdentity.class), "wss://console.example/internal", false);

        assertThat(factory.serverTerminalUrl("/api/server/ws?ticket=pty_1"))
                .isEqualTo("wss://console.example/internal/api/server/ws?ticket=pty_1");
    }

    @Test
    void rejectsPlaintextServerTerminalGateway() {
        CurrentBackendWebSocketUrlFactory factory = new CurrentBackendWebSocketUrlFactory(
                mock(BackendInstanceIdentity.class), "ws://console.example", false);

        assertThatThrownBy(() -> factory.serverTerminalUrl("/api/server/ws?ticket=pty_1"))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("wss");
    }

    @Test
    void allowsDirectServerTerminalWebSocketOnlyWhenExplicitlyEnabled() {
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.listenUrl()).thenReturn("http://127.0.0.1:8080");

        CurrentBackendWebSocketUrlFactory localFactory = new CurrentBackendWebSocketUrlFactory(identity, "", true);
        assertThat(localFactory.serverTerminalUrl("/api/server/ws?ticket=pty_1"))
                .isEqualTo("ws://127.0.0.1:8080/api/server/ws?ticket=pty_1");

        CurrentBackendWebSocketUrlFactory productionFactory = new CurrentBackendWebSocketUrlFactory(identity, "", false);
        assertThatThrownBy(() -> productionFactory.serverTerminalUrl("/api/server/ws?ticket=pty_1"))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("WSS");
    }

    private CurrentBackendWebSocketUrlFactory factory(String listenUrl) {
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.listenUrl()).thenReturn(listenUrl);
        return new CurrentBackendWebSocketUrlFactory(identity);
    }
}
