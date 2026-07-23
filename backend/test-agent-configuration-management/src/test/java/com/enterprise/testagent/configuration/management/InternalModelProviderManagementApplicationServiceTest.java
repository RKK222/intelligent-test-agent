package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.configuration.management.InternalModelProviderManagementApplicationService.InternalModelProviderItem;
import com.enterprise.testagent.configuration.management.InternalModelProviderManagementApplicationService.UpdateInternalModelProvidersCommand;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProvidersUpdatedEvent;
import com.enterprise.testagent.domain.configuration.InternalModelToken;
import com.enterprise.testagent.domain.configuration.InternalModelTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class InternalModelProviderManagementApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Test
    void savePreservesExistingTokenAssociationWhenLegacyClientOmitsTokenId() {
        InternalModelProviderRepository providers = mock(InternalModelProviderRepository.class);
        InternalModelTokenRepository tokens = mock(InternalModelTokenRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(providers.findAll()).thenReturn(List.of(provider("provider-a", true, 11L, "共享 Token", true)));
        when(tokens.findById(11L)).thenReturn(Optional.of(token(11L, "共享 Token", 1)));
        InternalModelProviderManagementApplicationService service = new InternalModelProviderManagementApplicationService(
                providers, tokens, publisher);

        service.save(new UpdateInternalModelProvidersCommand(
                        List.of(new InternalModelProviderItem(
                                " provider-a ", " Provider A ", " http://a.example/v1 ", true, 1, null, false)),
                        null),
                "trace_preserve");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InternalModelProvider>> captor = ArgumentCaptor.forClass(List.class);
        verify(providers).replaceProviders(captor.capture(), any(Instant.class));
        assertThat(captor.getValue()).singleElement().extracting(InternalModelProvider::tokenId).isEqualTo(11L);
        verify(publisher).publishEvent(new InternalModelProvidersUpdatedEvent("trace_preserve"));
    }

    @Test
    void saveAppliesExternallyProvidedLegacyTokenToNewProvider() {
        InternalModelProviderRepository providers = mock(InternalModelProviderRepository.class);
        InternalModelTokenRepository tokens = mock(InternalModelTokenRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        InternalModelToken defaultToken = token(21L, "默认 Token", 0);
        when(providers.findAll()).thenReturn(List.of());
        when(tokens.upsertLegacyDefault(eq(" external-token "), any(Instant.class))).thenReturn(defaultToken);
        when(tokens.findById(21L)).thenReturn(Optional.of(defaultToken));
        InternalModelProviderManagementApplicationService service = new InternalModelProviderManagementApplicationService(
                providers, tokens, publisher);

        service.save(new UpdateInternalModelProvidersCommand(
                        List.of(new InternalModelProviderItem(
                                "provider-new", "Provider New", "http://new.example/v1", true, 1, null, false)),
                        " external-token "),
                "trace_legacy");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InternalModelProvider>> captor = ArgumentCaptor.forClass(List.class);
        verify(providers).replaceProviders(captor.capture(), any(Instant.class));
        assertThat(captor.getValue()).singleElement().extracting(InternalModelProvider::tokenId).isEqualTo(21L);
        verify(providers).saveAuthToken(eq(" external-token "), any(Instant.class));
    }

    @Test
    void saveAppliesExternallyProvidedLegacyTokenToExistingUnlinkedProvider() {
        InternalModelProviderRepository providers = mock(InternalModelProviderRepository.class);
        InternalModelTokenRepository tokens = mock(InternalModelTokenRepository.class);
        InternalModelToken defaultToken = token(21L, "默认 Token", 0);
        when(providers.findAll()).thenReturn(List.of(provider("provider-a", false, null, null, false)));
        when(tokens.upsertLegacyDefault(eq("external-token"), any(Instant.class))).thenReturn(defaultToken);
        when(tokens.findById(21L)).thenReturn(Optional.of(defaultToken));
        InternalModelProviderManagementApplicationService service = new InternalModelProviderManagementApplicationService(
                providers, tokens, mock(ApplicationEventPublisher.class));

        service.save(new UpdateInternalModelProvidersCommand(
                        List.of(new InternalModelProviderItem(
                                "provider-a", "Provider A", "http://a.example/v1", true, 1, null, false)),
                        "external-token"),
                "trace_legacy_existing");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InternalModelProvider>> captor = ArgumentCaptor.forClass(List.class);
        verify(providers).replaceProviders(captor.capture(), any(Instant.class));
        assertThat(captor.getValue()).singleElement().extracting(InternalModelProvider::tokenId).isEqualTo(21L);
    }

    @Test
    void saveRejectsEnabledProviderWithoutToken() {
        InternalModelProviderRepository providers = mock(InternalModelProviderRepository.class);
        InternalModelTokenRepository tokens = mock(InternalModelTokenRepository.class);
        when(providers.findAll()).thenReturn(List.of());
        when(tokens.findLegacyDefault()).thenReturn(Optional.empty());
        InternalModelProviderManagementApplicationService service = new InternalModelProviderManagementApplicationService(
                providers, tokens, mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.save(new UpdateInternalModelProvidersCommand(
                        List.of(new InternalModelProviderItem(
                                "provider-new", "Provider New", "http://new.example/v1", true, 1, null, false)),
                        null),
                "trace_missing"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("provider-new", "Token");
                });
        verify(providers, never()).replaceProviders(any(), any());
    }

    @Test
    void saveAllowsExplicitlyClearingTokenOnlyFromDisabledProvider() {
        InternalModelProviderRepository providers = mock(InternalModelProviderRepository.class);
        InternalModelTokenRepository tokens = mock(InternalModelTokenRepository.class);
        when(providers.findAll()).thenReturn(List.of(provider("provider-a", true, 11L, "共享 Token", true)));
        InternalModelProviderManagementApplicationService service = new InternalModelProviderManagementApplicationService(
                providers, tokens, mock(ApplicationEventPublisher.class));

        service.save(new UpdateInternalModelProvidersCommand(
                        List.of(new InternalModelProviderItem(
                                "provider-a", "Provider A", "http://a.example/v1", false, 1, null, true)),
                        null),
                "trace_clear");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InternalModelProvider>> captor = ArgumentCaptor.forClass(List.class);
        verify(providers).replaceProviders(captor.capture(), any(Instant.class));
        assertThat(captor.getValue()).singleElement().extracting(InternalModelProvider::tokenId).isNull();
    }

    @Test
    void saveRejectsMissingTokenReference() {
        InternalModelProviderRepository providers = mock(InternalModelProviderRepository.class);
        InternalModelTokenRepository tokens = mock(InternalModelTokenRepository.class);
        when(providers.findAll()).thenReturn(List.of());
        when(tokens.findLegacyDefault()).thenReturn(Optional.empty());
        when(tokens.findById(99L)).thenReturn(Optional.empty());
        InternalModelProviderManagementApplicationService service = new InternalModelProviderManagementApplicationService(
                providers, tokens, mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.save(new UpdateInternalModelProvidersCommand(
                        List.of(new InternalModelProviderItem(
                                "provider-new", "Provider New", "http://new.example/v1", true, 1, 99L, false)),
                        null),
                "trace_missing_reference"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(providers, never()).replaceProviders(any(), any());
    }

    @Test
    void saveRejectsProviderIdsThatDuplicateAfterTrimming() {
        InternalModelProviderRepository providers = mock(InternalModelProviderRepository.class);
        InternalModelTokenRepository tokens = mock(InternalModelTokenRepository.class);
        when(providers.findAll()).thenReturn(List.of());
        when(tokens.findLegacyDefault()).thenReturn(Optional.of(token(11L, "默认 Token", 0)));
        when(tokens.findById(11L)).thenReturn(Optional.of(token(11L, "默认 Token", 0)));
        InternalModelProviderManagementApplicationService service = new InternalModelProviderManagementApplicationService(
                providers, tokens, mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.save(new UpdateInternalModelProvidersCommand(
                        List.of(
                                new InternalModelProviderItem(
                                        " provider-a", "Provider A", "http://a.example/v1", true, 1, 11L, false),
                                new InternalModelProviderItem(
                                        "provider-a ", "Provider A2", "http://a2.example/v1", true, 2, 11L, false)),
                        null),
                "trace_duplicate"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("providerId", "重复");
                });
        verify(providers, never()).replaceProviders(any(), any());
    }

    @Test
    void invalidProviderRequestDoesNotRotateLegacyTokenBeforeValidation() {
        InternalModelProviderRepository providers = mock(InternalModelProviderRepository.class);
        InternalModelTokenRepository tokens = mock(InternalModelTokenRepository.class);
        when(providers.findAll()).thenReturn(List.of());
        when(tokens.upsertLegacyDefault(eq("external-token"), any(Instant.class)))
                .thenReturn(token(21L, "默认 Token", 0));
        when(tokens.findById(21L)).thenReturn(Optional.of(token(21L, "默认 Token", 0)));
        InternalModelProviderManagementApplicationService service = new InternalModelProviderManagementApplicationService(
                providers, tokens, mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.save(new UpdateInternalModelProvidersCommand(
                        List.of(
                                new InternalModelProviderItem(
                                        "provider-a", "Provider A", "http://a.example/v1", true, 1, null, false),
                                new InternalModelProviderItem(
                                        " provider-a ", "Provider A2", "http://a2.example/v1", true, 2, null, false)),
                        "external-token"),
                "trace_invalid_legacy"))
                .isInstanceOf(PlatformException.class);
        verify(tokens, never()).upsertLegacyDefault(any(), any());
        verify(providers, never()).saveAuthToken(any(), any());
        verify(providers, never()).replaceProviders(any(), any());
    }

    private static InternalModelProvider provider(
            String providerId, boolean enabled, Long tokenId, String tokenName, boolean tokenConfigured) {
        return new InternalModelProvider(
                providerId,
                providerId,
                "http://example.test/v1",
                enabled,
                1,
                tokenId,
                tokenName,
                tokenConfigured,
                NOW,
                NOW);
    }

    private static InternalModelToken token(long tokenId, String name, long references) {
        return new InternalModelToken(tokenId, name, references, NOW, NOW);
    }
}
