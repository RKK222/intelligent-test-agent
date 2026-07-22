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
import com.enterprise.testagent.domain.configuration.InternalModelProvidersUpdatedEvent;
import com.enterprise.testagent.domain.configuration.InternalModelToken;
import com.enterprise.testagent.domain.configuration.InternalModelTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class InternalModelTokenManagementApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Test
    void createRecordsExternalTokenWithoutReturningItsValueAndPublishesRefresh() {
        InternalModelTokenRepository repository = mock(InternalModelTokenRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(repository.findByName("生产 Token")).thenReturn(Optional.empty());
        when(repository.create(eq("生产 Token"), eq(" external-token "), any(Instant.class)))
                .thenReturn(token(31L, "生产 Token", 0));
        InternalModelTokenManagementApplicationService service =
                new InternalModelTokenManagementApplicationService(repository, publisher);

        InternalModelToken created = service.create(" 生产 Token ", " external-token ", "trace_create");

        assertThat(created).extracting(InternalModelToken::tokenId, InternalModelToken::name)
                .containsExactly(31L, "生产 Token");
        assertThat(created.toString()).doesNotContain("external-token");
        verify(publisher).publishEvent(new InternalModelProvidersUpdatedEvent("trace_create"));
    }

    @Test
    void updateRejectsExplicitlyBlankName() {
        InternalModelTokenRepository repository = mock(InternalModelTokenRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(repository.findById(31L)).thenReturn(Optional.of(token(31L, "旧名称", 0)));
        InternalModelTokenManagementApplicationService service =
                new InternalModelTokenManagementApplicationService(repository, publisher);

        assertThatThrownBy(() -> service.update(31L, "   ", null, "trace_blank_name"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(repository, never()).update(any(long.class), any(), any(), any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void updateNameWithBlankTokenPreservesExistingValue() {
        InternalModelTokenRepository repository = mock(InternalModelTokenRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        InternalModelToken existing = token(31L, "旧名称", 2);
        when(repository.findById(31L)).thenReturn(Optional.of(existing));
        when(repository.findByName("新名称")).thenReturn(Optional.empty());
        when(repository.update(eq(31L), eq("新名称"), eq(null), any(Instant.class)))
                .thenReturn(Optional.of(token(31L, "新名称", 2)));
        InternalModelTokenManagementApplicationService service =
                new InternalModelTokenManagementApplicationService(repository, publisher);

        InternalModelToken updated = service.update(31L, " 新名称 ", "   ", "trace_update");

        assertThat(updated.tokenId()).isEqualTo(31L);
        verify(repository).update(eq(31L), eq("新名称"), eq(null), any(Instant.class));
        verify(publisher).publishEvent(new InternalModelProvidersUpdatedEvent("trace_update"));
    }

    @Test
    void deleteRejectsReferencedTokenAndDoesNotPublishRefresh() {
        InternalModelTokenRepository repository = mock(InternalModelTokenRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(repository.findById(31L)).thenReturn(Optional.of(token(31L, "共享 Token", 2)));
        InternalModelTokenManagementApplicationService service =
                new InternalModelTokenManagementApplicationService(repository, publisher);

        assertThatThrownBy(() -> service.delete(31L, "trace_delete"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(repository, never()).deleteIfUnreferenced(31L);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void deleteRemovesUnreferencedTokenAndPublishesRefresh() {
        InternalModelTokenRepository repository = mock(InternalModelTokenRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(repository.findById(31L)).thenReturn(Optional.of(token(31L, "闲置 Token", 0)));
        when(repository.deleteIfUnreferenced(31L)).thenReturn(true);
        InternalModelTokenManagementApplicationService service =
                new InternalModelTokenManagementApplicationService(repository, publisher);

        service.delete(31L, "trace_delete");

        verify(repository).deleteIfUnreferenced(31L);
        verify(publisher).publishEvent(new InternalModelProvidersUpdatedEvent("trace_delete"));
    }

    private static InternalModelToken token(long tokenId, String name, long references) {
        return new InternalModelToken(tokenId, name, references, NOW, NOW);
    }
}
