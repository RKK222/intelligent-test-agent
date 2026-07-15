package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity;
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

    private CurrentBackendWebSocketUrlFactory factory(String listenUrl) {
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.listenUrl()).thenReturn(listenUrl);
        return new CurrentBackendWebSocketUrlFactory(identity);
    }
}
