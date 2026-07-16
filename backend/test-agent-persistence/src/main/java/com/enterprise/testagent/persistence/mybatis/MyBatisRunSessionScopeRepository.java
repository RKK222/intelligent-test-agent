package com.enterprise.testagent.persistence.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.event.RunSessionScope;
import com.enterprise.testagent.domain.event.RunSessionScopeRepository;
import com.enterprise.testagent.domain.event.RunSessionScopeSession;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Run session scope 的 MyBatis Repository 实现，SQL 固定放在 XML 中以满足新增 SQL 规范。
 */
@Repository
public class MyBatisRunSessionScopeRepository implements RunSessionScopeRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RunSessionScopeMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 注入 MyBatis mapper 和 JSON 序列化器，metadata_json 只保存低风险扩展信息。
     */
    public MyBatisRunSessionScopeRepository(RunSessionScopeMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsertScope(RunSessionScope scope) {
        mapper.upsertScope(new RunSessionScopeRow(
                scope.runId().value(),
                scope.rootSessionId(),
                scope.scopeVersion(),
                scope.traceId(),
                toJson(scope.metadata()),
                scope.createdAt(),
                scope.updatedAt()));
    }

    @Override
    public void upsertSession(RunSessionScopeSession session) {
        mapper.upsertSession(new RunSessionScopeSessionRow(
                session.runId().value(),
                session.sessionId(),
                session.rootSessionId(),
                session.parentSessionId(),
                session.childSession(),
                session.discoverySource(),
                session.taskMessageId(),
                session.taskPartId(),
                session.taskCallId(),
                session.traceId(),
                toJson(session.metadata()),
                session.discoveredAt(),
                session.updatedAt()));
    }

    @Override
    public List<RunSessionScopeSession> findSessionsByRunId(RunId runId) {
        return mapper.findSessionsByRunId(runId.value()).stream()
                .map(this::toSessionDomain)
                .toList();
    }

    @Override
    public List<RunSessionScopeSession> findSessionsByRootSessionId(String rootSessionId) {
        String normalizedRootSessionId = DomainValidation.requireText(rootSessionId, "rootSessionId");
        return mapper.findSessionsByRootSessionId(normalizedRootSessionId).stream()
                .map(this::toSessionDomain)
                .toList();
    }

    @Override
    public Optional<RunSessionScopeSession> findSession(RunId runId, String sessionId) {
        String normalizedSessionId = DomainValidation.requireText(sessionId, "sessionId");
        return Optional.ofNullable(mapper.findSession(runId.value(), normalizedSessionId))
                .map(this::toSessionDomain);
    }

    private RunSessionScopeSession toSessionDomain(RunSessionScopeSessionRow row) {
        return new RunSessionScopeSession(
                new RunId(row.runId()),
                row.sessionId(),
                row.rootSessionId(),
                row.parentSessionId(),
                row.childSession(),
                row.discoverySource(),
                row.taskMessageId(),
                row.taskPartId(),
                row.taskCallId(),
                row.traceId(),
                row.discoveredAt(),
                row.updatedAt(),
                fromJson(row.metadataJson()));
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("metadata must be valid JSON", exception);
        }
    }

    private Map<String, Object> fromJson(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored metadata_json is not valid JSON", exception);
        }
    }
}
