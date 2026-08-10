package com.enterprise.testagent.system.management.externalapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialsUpdatedEvent;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证外部凭据刷新广播不携带密钥，并区分本地提交与远端通知。 */
class ExternalApiCredentialUpdateBroadcasterTest {

    @Test
    void productionSpringBeanUsesBroadcastIdentityAndRegistryConstructor() {
        new ApplicationContextRunner()
                .withBean(ServerBroadcastPublisher.class, () -> mock(ServerBroadcastPublisher.class))
                .withBean(BackendInstanceIdentity.class, () -> mock(BackendInstanceIdentity.class))
                .withBean(ExternalApiCredentialRegistry.class, () -> mock(ExternalApiCredentialRegistry.class))
                .withBean(ExternalApiCredentialUpdateBroadcaster.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExternalApiCredentialUpdateBroadcaster.class);
                });
    }

    @Test
    void localCommitRefreshesRegistryAndBroadcastsEmptyPayload() {
        List<ServerBroadcastEvent> published = new ArrayList<>();
        ServerBroadcastPublisher publisher = new ServerBroadcastPublisher() {
            @Override
            public String instanceId() {
                return "instance-one";
            }

            @Override
            public void publish(ServerBroadcastEvent event) {
                published.add(event);
            }
        };
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        org.mockito.Mockito.when(identity.instanceId()).thenReturn("instance-one");
        org.mockito.Mockito.when(identity.linuxServerId()).thenReturn("linux-one");
        ExternalApiCredentialRegistry registry = mock(ExternalApiCredentialRegistry.class);
        ExternalApiCredentialUpdateBroadcaster broadcaster = new ExternalApiCredentialUpdateBroadcaster(
                publisher, identity, registry,
                Clock.fixed(Instant.parse("2026-08-09T04:00:00Z"), ZoneOffset.UTC));

        broadcaster.onCredentialsUpdated(new ExternalApiCredentialsUpdatedEvent("trace_update"));

        verify(registry).refresh("trace_update");
        assertThat(published).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("external-api-credential.refresh-requested");
            assertThat(event.payload()).isEmpty();
        });
    }

    @Test
    void remoteBroadcastOnlyRefreshesLocalRegistry() {
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        org.mockito.Mockito.when(publisher.instanceId()).thenReturn("instance-one");
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        ExternalApiCredentialRegistry registry = mock(ExternalApiCredentialRegistry.class);
        ExternalApiCredentialUpdateBroadcaster broadcaster = new ExternalApiCredentialUpdateBroadcaster(
                publisher, identity, registry, Clock.systemUTC());
        ServerBroadcastEvent remote = new ServerBroadcastEvent(
                "sbe_remote", "external-api-credential.refresh-requested", "instance-two", "linux-two",
                "trace_remote", Instant.now(), java.util.Map.of());

        broadcaster.handle(remote);

        verify(registry).refresh("trace_remote");
    }
}
