package com.enterprise.testagent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextIssueLease;
import com.enterprise.testagent.domain.run.ConversationContextSessionRevocation;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.run.ConversationContextUserMutation;
import com.enterprise.testagent.domain.run.ConversationContextWorkspaceMutation;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 会话运行上下文存储。
 *
 * <p>所有 key 使用统一 hash tag，以 Lua 原子维护 token、五类反向索引、资源/全局代次与 Session revoke gate；
 * Redis 中不保存原始 token。
 */
public class RedisConversationContextStore implements ConversationContextStore {

    private static final String KEY_PREFIX = "test-agent:{conversation-context}:";
    private static final long TTL_MILLIS = CONTEXT_TTL.toMillis();

    private static final DefaultRedisScript<String> BEGIN_ISSUE_SCRIPT = new DefaultRedisScript<>("""
            local function currentGeneration(key)
              local value = redis.call('GET', key)
              if not value then return 0 end
              return tonumber(value)
            end
            if redis.call('SCARD', KEYS[6]) > 0 or redis.call('SCARD', KEYS[7]) > 0 then
              return '__REVOKED__'
            end
            return cjson.encode({
              issueGeneration = currentGeneration(KEYS[1]),
              userSessionGeneration = currentGeneration(KEYS[2]),
              userGeneration = currentGeneration(KEYS[3]),
              sessionGeneration = currentGeneration(KEYS[4]),
              contextGeneration = currentGeneration(KEYS[5])
            })
            """, String.class);

    private static final DefaultRedisScript<String> SAVE_SCRIPT = new DefaultRedisScript<>("""
            local function currentGeneration(key)
              local value = redis.call('GET', key)
              if not value then return 0 end
              return tonumber(value)
            end
            local function issueGenerationMatches()
              return currentGeneration(KEYS[12]) == tonumber(ARGV[5])
                and currentGeneration(KEYS[7]) == tonumber(ARGV[6])
                and currentGeneration(KEYS[8]) == tonumber(ARGV[7])
                and currentGeneration(KEYS[9]) == tonumber(ARGV[8])
                and currentGeneration(KEYS[13]) == tonumber(ARGV[9])
            end
            if redis.call('SCARD', KEYS[14]) > 0
                or redis.call('SCARD', KEYS[15]) > 0
                or redis.call('SCARD', KEYS[16]) > 0
                or not issueGenerationMatches() then
              return nil
            end
            local stored = {
              context = cjson.decode(ARGV[1]),
              userSessionIndexKey = KEYS[2],
              userIndexKey = KEYS[3],
              sessionIndexKey = KEYS[4],
              workspaceIndexKey = KEYS[5],
              processIndexKey = KEYS[6],
              userSessionGeneration = currentGeneration(KEYS[7]),
              userGeneration = currentGeneration(KEYS[8]),
              sessionGeneration = currentGeneration(KEYS[9]),
              workspaceGeneration = currentGeneration(KEYS[10]),
              processGeneration = currentGeneration(KEYS[11]),
              contextGeneration = currentGeneration(KEYS[13])
            }
            local encoded = cjson.encode(stored)
            redis.call('SET', KEYS[1], encoded, 'PX', ARGV[2])
            for index = 2, 6 do
              redis.call('ZREMRANGEBYSCORE', KEYS[index], '-inf', ARGV[4])
              redis.call('ZADD', KEYS[index], ARGV[3], KEYS[1])
            end
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            redis.call('PEXPIRE', KEYS[3], ARGV[2])
            redis.call('PEXPIRE', KEYS[4], ARGV[2])
            redis.call('PEXPIRE', KEYS[5], ARGV[2])
            redis.call('PEXPIRE', KEYS[6], ARGV[2])
            return encoded
            """, String.class);

    private static final DefaultRedisScript<String> TOUCH_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return nil end
            local stored = cjson.decode(raw)
            local function currentGeneration(key)
              local value = redis.call('GET', key)
              if not value then return 0 end
              return tonumber(value)
            end
            local function generationMatches()
              return tonumber(stored.userSessionGeneration) == currentGeneration(KEYS[7])
                and tonumber(stored.userGeneration) == currentGeneration(KEYS[8])
                and tonumber(stored.sessionGeneration) == currentGeneration(KEYS[9])
                and tonumber(stored.workspaceGeneration) == currentGeneration(KEYS[10])
                and tonumber(stored.processGeneration) == currentGeneration(KEYS[11])
                and tonumber(stored.contextGeneration) == currentGeneration(KEYS[12])
            end
            local indexesMatch = stored.userSessionIndexKey == KEYS[2]
              and stored.userIndexKey == KEYS[3]
              and stored.sessionIndexKey == KEYS[4]
              and stored.workspaceIndexKey == KEYS[5]
              and stored.processIndexKey == KEYS[6]
            if redis.call('SCARD', KEYS[13]) > 0
                or redis.call('SCARD', KEYS[14]) > 0
                or redis.call('SCARD', KEYS[15]) > 0
                or not indexesMatch
                or not generationMatches() then
              redis.call('DEL', KEYS[1])
              redis.call('ZREM', KEYS[2], KEYS[1])
              redis.call('ZREM', KEYS[3], KEYS[1])
              redis.call('ZREM', KEYS[4], KEYS[1])
              redis.call('ZREM', KEYS[5], KEYS[1])
              redis.call('ZREM', KEYS[6], KEYS[1])
              return nil
            end
            stored.context.expiresAt = ARGV[1]
            local encoded = cjson.encode(stored)
            redis.call('SET', KEYS[1], encoded, 'PX', ARGV[2])
            for index = 2, 6 do
              redis.call('ZREMRANGEBYSCORE', KEYS[index], '-inf', ARGV[4])
              redis.call('ZADD', KEYS[index], ARGV[3], KEYS[1])
            end
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            redis.call('PEXPIRE', KEYS[3], ARGV[2])
            redis.call('PEXPIRE', KEYS[4], ARGV[2])
            redis.call('PEXPIRE', KEYS[5], ARGV[2])
            redis.call('PEXPIRE', KEYS[6], ARGV[2])
            return encoded
            """, String.class);

    private static final DefaultRedisScript<String> ROUTE_RESOLVE_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return nil end
            local stored = cjson.decode(raw)
            local function currentGeneration(key)
              local value = redis.call('GET', key)
              if not value then return 0 end
              return tonumber(value)
            end
            local generationsMatch = tonumber(stored.userSessionGeneration) == currentGeneration(KEYS[7])
              and tonumber(stored.userGeneration) == currentGeneration(KEYS[8])
              and tonumber(stored.sessionGeneration) == currentGeneration(KEYS[9])
              and tonumber(stored.workspaceGeneration) == currentGeneration(KEYS[10])
              and tonumber(stored.processGeneration) == currentGeneration(KEYS[11])
              and tonumber(stored.contextGeneration) == currentGeneration(KEYS[12])
            local indexesMatch = stored.userSessionIndexKey == KEYS[2]
              and stored.userIndexKey == KEYS[3]
              and stored.sessionIndexKey == KEYS[4]
              and stored.workspaceIndexKey == KEYS[5]
              and stored.processIndexKey == KEYS[6]
            if redis.call('SCARD', KEYS[13]) > 0
                or redis.call('SCARD', KEYS[14]) > 0
                or redis.call('SCARD', KEYS[15]) > 0
                or not indexesMatch
                or not generationsMatch then
              return nil
            end
            return raw
            """, String.class);

    private static final DefaultRedisScript<Long> INVALIDATE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('INCR', KEYS[3])
            redis.call('INCR', KEYS[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, tokenKey in ipairs(members) do
              local raw = redis.call('GET', tokenKey)
              if raw then
                local ok, stored = pcall(cjson.decode, raw)
                if ok then
                  redis.call('ZREM', stored.userSessionIndexKey, tokenKey)
                  if stored.userIndexKey then redis.call('ZREM', stored.userIndexKey, tokenKey) end
                  redis.call('ZREM', stored.sessionIndexKey, tokenKey)
                  redis.call('ZREM', stored.workspaceIndexKey, tokenKey)
                  redis.call('ZREM', stored.processIndexKey, tokenKey)
                end
              end
              redis.call('DEL', tokenKey)
            end
            redis.call('DEL', KEYS[1])
            return #members
            """, Long.class);

    private static final DefaultRedisScript<Long> REVOKE_SESSION_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SADD', KEYS[4], ARGV[1])
            redis.call('PEXPIRE', KEYS[4], ARGV[3])
            redis.call('INCR', KEYS[3])
            redis.call('INCR', KEYS[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, tokenKey in ipairs(members) do
              local raw = redis.call('GET', tokenKey)
              if raw then
                local ok, stored = pcall(cjson.decode, raw)
                if ok then
                  redis.call('ZREM', stored.userSessionIndexKey, tokenKey)
                  if stored.userIndexKey then redis.call('ZREM', stored.userIndexKey, tokenKey) end
                  redis.call('ZREM', stored.sessionIndexKey, tokenKey)
                  redis.call('ZREM', stored.workspaceIndexKey, tokenKey)
                  redis.call('ZREM', stored.processIndexKey, tokenKey)
                end
              end
              redis.call('DEL', tokenKey)
            end
            redis.call('DEL', KEYS[1])
            return #members
            """, Long.class);

    private static final DefaultRedisScript<Long> RESTORE_REVOCATION_SCRIPT = new DefaultRedisScript<>("""
            return redis.call('SREM', KEYS[1], ARGV[1])
            """, Long.class);

    private static final DefaultRedisScript<Long> BEGIN_MUTATION_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SADD', KEYS[4], ARGV[1])
            redis.call('PEXPIRE', KEYS[4], ARGV[3])
            redis.call('INCR', KEYS[3])
            redis.call('INCR', KEYS[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, tokenKey in ipairs(members) do
              local raw = redis.call('GET', tokenKey)
              if raw then
                local ok, stored = pcall(cjson.decode, raw)
                if ok then
                  redis.call('ZREM', stored.userSessionIndexKey, tokenKey)
                  if stored.userIndexKey then redis.call('ZREM', stored.userIndexKey, tokenKey) end
                  redis.call('ZREM', stored.sessionIndexKey, tokenKey)
                  redis.call('ZREM', stored.workspaceIndexKey, tokenKey)
                  redis.call('ZREM', stored.processIndexKey, tokenKey)
                end
              end
              redis.call('DEL', tokenKey)
            end
            redis.call('DEL', KEYS[1])
            return #members
            """, Long.class);

    private static final DefaultRedisScript<Long> COMPLETE_MUTATION_SCRIPT = new DefaultRedisScript<>("""
            redis.call('INCR', KEYS[3])
            redis.call('INCR', KEYS[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, tokenKey in ipairs(members) do
              local raw = redis.call('GET', tokenKey)
              if raw then
                local ok, stored = pcall(cjson.decode, raw)
                if ok then
                  redis.call('ZREM', stored.userSessionIndexKey, tokenKey)
                  if stored.userIndexKey then redis.call('ZREM', stored.userIndexKey, tokenKey) end
                  redis.call('ZREM', stored.sessionIndexKey, tokenKey)
                  redis.call('ZREM', stored.workspaceIndexKey, tokenKey)
                  redis.call('ZREM', stored.processIndexKey, tokenKey)
                end
              end
              redis.call('DEL', tokenKey)
            end
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[4], ARGV[1])
            return #members
            """, Long.class);

    private static final DefaultRedisScript<Long> INVALIDATE_ALL_SCRIPT = new DefaultRedisScript<>("""
            redis.call('INCR', KEYS[1])
            return redis.call('INCR', KEYS[2])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RedisConversationContextStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, Clock.systemUTC());
    }

    RedisConversationContextStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public ConversationContextIssueLease beginIssue(UserId userId, SessionId sessionId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try {
            String value = redisTemplate.execute(
                    BEGIN_ISSUE_SCRIPT,
                    List.of(
                            issueGenerationKey(),
                            userSessionGenerationKey(userId, sessionId),
                            userGenerationKey(userId),
                            sessionGenerationKey(sessionId),
                            contextGenerationKey(),
                            sessionRevocationKey(sessionId),
                            userMutationKey(userId)));
            if (value == null) {
                throw new IllegalStateException("Redis context begin issue script returned no result");
            }
            if ("__REVOKED__".equals(value)) {
                throw new PlatformException(
                        ErrorCode.CONVERSATION_CONTEXT_EXPIRED,
                        "会话运行上下文正在失效，暂不能签发");
            }
            IssueGenerations generations = objectMapper.readValue(value, IssueGenerations.class);
            return new ConversationContextIssueLease(
                    userId,
                    sessionId,
                    generations.issueGeneration(),
                    generations.userSessionGeneration(),
                    generations.userGeneration(),
                    generations.sessionGeneration(),
                    generations.contextGeneration());
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public boolean saveIfCurrent(
            String contextToken,
            ConversationRunContext context,
            ConversationContextIssueLease issueLease) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(issueLease, "issueLease must not be null");
        if (!issueLease.matches(context)) {
            throw new IllegalArgumentException("issueLease must match context user and session");
        }
        try {
            ContextKeys keys = keys(contextToken, context);
            Instant now = clock.instant();
            String result = redisTemplate.execute(
                    SAVE_SCRIPT,
                    keys.saveKeys(),
                    serializeContext(context),
                    String.valueOf(TTL_MILLIS),
                    String.valueOf(now.plus(CONTEXT_TTL).toEpochMilli()),
                    String.valueOf(now.toEpochMilli()),
                    String.valueOf(issueLease.issueGeneration()),
                    String.valueOf(issueLease.userSessionGeneration()),
                    String.valueOf(issueLease.userGeneration()),
                    String.valueOf(issueLease.sessionGeneration()),
                    String.valueOf(issueLease.contextGeneration()));
            return result != null;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<ConversationRunContext> peek(String contextToken) {
        try {
            String value = redisTemplate.opsForValue().get(tokenKey(contextToken));
            if (value == null) {
                return Optional.empty();
            }
            StoredContext stored = deserializeStored(value);
            validateStoredIndexes(stored);
            return stored.context().expiresAt().isAfter(clock.instant())
                    ? Optional.of(stored.context())
                    : Optional.empty();
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<ConversationRunContext> touch(
            String contextToken,
            ConversationRunContext expectedContext) {
        Objects.requireNonNull(expectedContext, "expectedContext must not be null");
        try {
            if (!expectedContext.expiresAt().isAfter(clock.instant())) {
                return Optional.empty();
            }
            ContextKeys keys = keys(contextToken, expectedContext);
            Instant now = clock.instant();
            String value = redisTemplate.execute(
                    TOUCH_SCRIPT,
                    keys.touchKeys(),
                    now.plus(CONTEXT_TTL).toString(),
                    String.valueOf(TTL_MILLIS),
                    String.valueOf(now.plus(CONTEXT_TTL).toEpochMilli()),
                    String.valueOf(now.toEpochMilli()));
            if (value == null) {
                return Optional.empty();
            }
            StoredContext stored = deserializeStored(value);
            validateStoredIndexes(stored);
            ConversationRunContext refreshed = stored.context();
            if (!refreshed.withExpiresAt(expectedContext.expiresAt()).equals(expectedContext)) {
                throw new IllegalStateException("Redis context changed while being touched");
            }
            return Optional.of(refreshed);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<ConversationRunContext> resolveForRouting(
            String contextToken,
            UserId userId,
            String agentId,
            SessionId sessionId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        String normalizedAgentId = requireText(agentId, "agentId").toLowerCase(Locale.ROOT);
        try {
            String tokenKey = tokenKey(contextToken);
            String raw = redisTemplate.opsForValue().get(tokenKey);
            if (raw == null) {
                return Optional.empty();
            }
            StoredContext candidate = deserializeStored(raw);
            validateStoredIndexes(candidate);
            ConversationRunContext context = candidate.context();
            if (!context.userId().equals(userId)
                    || !context.agentId().equals(normalizedAgentId)
                    || !context.sessionId().equals(sessionId)
                    || !context.expiresAt().isAfter(clock.instant())) {
                return Optional.empty();
            }
            ContextKeys keys = keys(contextToken, context);
            String resolved = redisTemplate.execute(ROUTE_RESOLVE_SCRIPT, keys.currentKeys());
            if (resolved == null) {
                return Optional.empty();
            }
            StoredContext stored = deserializeStored(resolved);
            validateStoredIndexes(stored);
            ConversationRunContext current = stored.context();
            return current.equals(context) ? Optional.of(current) : Optional.empty();
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void invalidate(UserId userId, SessionId sessionId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        invalidateIndex(userSessionIndexKey(userId, sessionId), userSessionGenerationKey(userId, sessionId));
    }

    @Override
    public void invalidateUser(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        invalidateIndex(userIndexKey(userId), userGenerationKey(userId));
    }

    @Override
    public void invalidateSession(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        invalidateIndex(sessionIndexKey(sessionId), sessionGenerationKey(sessionId));
    }

    @Override
    public void invalidateWorkspace(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        invalidateIndex(workspaceIndexKey(workspaceId), workspaceGenerationKey(workspaceId));
    }

    @Override
    public void invalidateProcess(String processId) {
        String normalizedProcessId = requireText(processId, "processId");
        invalidateIndex(processIndexKey(normalizedProcessId), processGenerationKey(normalizedProcessId));
    }

    @Override
    public ConversationContextUserMutation beginUserMutation(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        String mutationToken = UUID.randomUUID().toString();
        beginMutation(
                userIndexKey(userId),
                userGenerationKey(userId),
                userMutationKey(userId),
                mutationToken);
        return new ConversationContextUserMutation(userId, mutationToken);
    }

    @Override
    public void completeUserMutation(ConversationContextUserMutation mutation) {
        Objects.requireNonNull(mutation, "mutation must not be null");
        completeMutation(
                userIndexKey(mutation.userId()),
                userGenerationKey(mutation.userId()),
                userMutationKey(mutation.userId()),
                mutation.mutationToken());
    }

    @Override
    public void abortUserMutation(ConversationContextUserMutation mutation) {
        Objects.requireNonNull(mutation, "mutation must not be null");
        abortMutation(userMutationKey(mutation.userId()), mutation.mutationToken());
    }

    @Override
    public ConversationContextWorkspaceMutation beginWorkspaceMutation(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        String mutationToken = UUID.randomUUID().toString();
        beginMutation(
                workspaceIndexKey(workspaceId),
                workspaceGenerationKey(workspaceId),
                workspaceMutationKey(workspaceId),
                mutationToken);
        return new ConversationContextWorkspaceMutation(workspaceId, mutationToken);
    }

    @Override
    public void completeWorkspaceMutation(ConversationContextWorkspaceMutation mutation) {
        Objects.requireNonNull(mutation, "mutation must not be null");
        completeMutation(
                workspaceIndexKey(mutation.workspaceId()),
                workspaceGenerationKey(mutation.workspaceId()),
                workspaceMutationKey(mutation.workspaceId()),
                mutation.mutationToken());
    }

    @Override
    public void abortWorkspaceMutation(ConversationContextWorkspaceMutation mutation) {
        Objects.requireNonNull(mutation, "mutation must not be null");
        abortMutation(workspaceMutationKey(mutation.workspaceId()), mutation.mutationToken());
    }

    @Override
    public ConversationContextSessionRevocation revokeSession(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        String revokeToken = UUID.randomUUID().toString();
        try {
            Long result = redisTemplate.execute(
                    REVOKE_SESSION_SCRIPT,
                    List.of(
                            sessionIndexKey(sessionId),
                            sessionGenerationKey(sessionId),
                            issueGenerationKey(),
                            sessionRevocationKey(sessionId)),
                    revokeToken,
                    String.valueOf(clock.instant().toEpochMilli()),
                    String.valueOf(TTL_MILLIS));
            if (result == null) {
                throw new IllegalStateException("Redis context revoke session script returned no result");
            }
            return new ConversationContextSessionRevocation(sessionId, revokeToken);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void restoreSessionRevocation(ConversationContextSessionRevocation revocation) {
        Objects.requireNonNull(revocation, "revocation must not be null");
        try {
            Long result = redisTemplate.execute(
                    RESTORE_REVOCATION_SCRIPT,
                    List.of(sessionRevocationKey(revocation.sessionId())),
                    revocation.revokeToken());
            if (result == null) {
                throw new IllegalStateException("Redis context restore revocation script returned no result");
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void invalidateAll() {
        try {
            Long result = redisTemplate.execute(
                    INVALIDATE_ALL_SCRIPT,
                    List.of(contextGenerationKey(), issueGenerationKey()));
            if (result == null) {
                throw new IllegalStateException("Redis context global invalidate script returned no result");
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private void invalidateIndex(String indexKey, String generationKey) {
        try {
            Long result = redisTemplate.execute(
                    INVALIDATE_SCRIPT,
                    List.of(indexKey, generationKey, issueGenerationKey()),
                    String.valueOf(clock.instant().toEpochMilli()));
            if (result == null) {
                throw new IllegalStateException("Redis context invalidate script returned no result");
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private void beginMutation(
            String indexKey,
            String generationKey,
            String mutationKey,
            String mutationToken) {
        try {
            Long result = redisTemplate.execute(
                    BEGIN_MUTATION_SCRIPT,
                    List.of(indexKey, generationKey, issueGenerationKey(), mutationKey),
                    mutationToken,
                    String.valueOf(clock.instant().toEpochMilli()),
                    String.valueOf(TTL_MILLIS));
            if (result == null) {
                throw new IllegalStateException("Redis context begin mutation script returned no result");
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private void completeMutation(
            String indexKey,
            String generationKey,
            String mutationKey,
            String mutationToken) {
        try {
            Long result = redisTemplate.execute(
                    COMPLETE_MUTATION_SCRIPT,
                    List.of(indexKey, generationKey, issueGenerationKey(), mutationKey),
                    mutationToken,
                    String.valueOf(clock.instant().toEpochMilli()));
            if (result == null) {
                throw new IllegalStateException("Redis context complete mutation script returned no result");
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private void abortMutation(String mutationKey, String mutationToken) {
        try {
            Long result = redisTemplate.execute(
                    RESTORE_REVOCATION_SCRIPT,
                    List.of(mutationKey),
                    mutationToken);
            if (result == null) {
                throw new IllegalStateException("Redis context abort mutation script returned no result");
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private String serializeContext(ConversationRunContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private StoredContext deserializeStored(String value) {
        try {
            return objectMapper.readValue(value, StoredContext.class);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private static void validateStoredIndexes(StoredContext stored) {
        ConversationRunContext context = stored.context();
        if (!stored.userSessionIndexKey().equals(userSessionIndexKey(context.userId(), context.sessionId()))
                || !stored.userIndexKey().equals(userIndexKey(context.userId()))
                || !stored.sessionIndexKey().equals(sessionIndexKey(context.sessionId()))
                || !stored.workspaceIndexKey().equals(workspaceIndexKey(context.workspaceId()))
                || !stored.processIndexKey().equals(processIndexKey(context.processId()))) {
            throw new IllegalStateException("Redis context reverse indexes do not match context snapshot");
        }
    }

    private static ContextKeys keys(String contextToken, ConversationRunContext context) {
        return new ContextKeys(
                tokenKey(contextToken),
                userSessionIndexKey(context.userId(), context.sessionId()),
                userIndexKey(context.userId()),
                sessionIndexKey(context.sessionId()),
                workspaceIndexKey(context.workspaceId()),
                processIndexKey(context.processId()),
                userSessionGenerationKey(context.userId(), context.sessionId()),
                userGenerationKey(context.userId()),
                sessionGenerationKey(context.sessionId()),
                workspaceGenerationKey(context.workspaceId()),
                processGenerationKey(context.processId()),
                issueGenerationKey(),
                contextGenerationKey(),
                sessionRevocationKey(context.sessionId()),
                userMutationKey(context.userId()),
                workspaceMutationKey(context.workspaceId()));
    }

    private static String tokenKey(String contextToken) {
        return KEY_PREFIX + "token:" + sha256(requireText(contextToken, "contextToken"));
    }

    private static String userSessionIndexKey(UserId userId, SessionId sessionId) {
        return KEY_PREFIX + "index:user-session:" + userId.value() + ":" + sessionId.value();
    }

    private static String workspaceIndexKey(WorkspaceId workspaceId) {
        return KEY_PREFIX + "index:workspace:" + workspaceId.value();
    }

    private static String userIndexKey(UserId userId) {
        return KEY_PREFIX + "index:user:" + userId.value();
    }

    private static String sessionIndexKey(SessionId sessionId) {
        return KEY_PREFIX + "index:session:" + sessionId.value();
    }

    private static String processIndexKey(String processId) {
        return KEY_PREFIX + "index:process:" + processId;
    }

    private static String userSessionGenerationKey(UserId userId, SessionId sessionId) {
        return KEY_PREFIX + "generation:user-session:" + userId.value() + ":" + sessionId.value();
    }

    private static String workspaceGenerationKey(WorkspaceId workspaceId) {
        return KEY_PREFIX + "generation:workspace:" + workspaceId.value();
    }

    private static String userGenerationKey(UserId userId) {
        return KEY_PREFIX + "generation:user:" + userId.value();
    }

    private static String sessionGenerationKey(SessionId sessionId) {
        return KEY_PREFIX + "generation:session:" + sessionId.value();
    }

    private static String processGenerationKey(String processId) {
        return KEY_PREFIX + "generation:process:" + processId;
    }

    private static String issueGenerationKey() {
        return KEY_PREFIX + "generation:issue";
    }

    private static String contextGenerationKey() {
        return KEY_PREFIX + "generation:context";
    }

    private static String sessionRevocationKey(SessionId sessionId) {
        return KEY_PREFIX + "revoked:session:" + sessionId.value();
    }

    private static String userMutationKey(UserId userId) {
        return KEY_PREFIX + "mutating:user:" + userId.value();
    }

    private static String workspaceMutationKey(WorkspaceId workspaceId) {
        return KEY_PREFIX + "mutating:workspace:" + workspaceId.value();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static PlatformException unavailable(Exception cause) {
        if (cause instanceof PlatformException platformException
                && platformException.errorCode() == ErrorCode.RUNTIME_STATE_UNAVAILABLE) {
            return platformException;
        }
        return new PlatformException(
                ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                "运行态存储不可用",
                Map.of(),
                cause);
    }

    private record ContextKeys(
            String tokenKey,
            String userSessionIndexKey,
            String userIndexKey,
            String sessionIndexKey,
            String workspaceIndexKey,
            String processIndexKey,
            String userSessionGenerationKey,
            String userGenerationKey,
            String sessionGenerationKey,
            String workspaceGenerationKey,
            String processGenerationKey,
            String issueGenerationKey,
            String contextGenerationKey,
            String sessionRevocationKey,
            String userMutationKey,
            String workspaceMutationKey) {

        private List<String> currentKeys() {
            return List.of(
                    tokenKey,
                    userSessionIndexKey,
                    userIndexKey,
                    sessionIndexKey,
                    workspaceIndexKey,
                    processIndexKey,
                    userSessionGenerationKey,
                    userGenerationKey,
                    sessionGenerationKey,
                    workspaceGenerationKey,
                    processGenerationKey,
                    contextGenerationKey,
                    sessionRevocationKey,
                    userMutationKey,
                    workspaceMutationKey);
        }

        private List<String> touchKeys() {
            return currentKeys();
        }

        private List<String> saveKeys() {
            return List.of(
                    tokenKey,
                    userSessionIndexKey,
                    userIndexKey,
                    sessionIndexKey,
                    workspaceIndexKey,
                    processIndexKey,
                    userSessionGenerationKey,
                    userGenerationKey,
                    sessionGenerationKey,
                    workspaceGenerationKey,
                    processGenerationKey,
                    issueGenerationKey,
                    contextGenerationKey,
                    sessionRevocationKey,
                    userMutationKey,
                    workspaceMutationKey);
        }
    }

    private record IssueGenerations(
            long issueGeneration,
            long userSessionGeneration,
            long userGeneration,
            long sessionGeneration,
            long contextGeneration) {
    }

    private record StoredContext(
            ConversationRunContext context,
            String userSessionIndexKey,
            String userIndexKey,
            String sessionIndexKey,
            String workspaceIndexKey,
            String processIndexKey,
            long userSessionGeneration,
            long userGeneration,
            long sessionGeneration,
            long workspaceGeneration,
            long processGeneration,
            long contextGeneration) {

        private StoredContext {
            Objects.requireNonNull(context, "context must not be null");
            userSessionIndexKey = requireText(userSessionIndexKey, "userSessionIndexKey");
            userIndexKey = requireText(userIndexKey, "userIndexKey");
            sessionIndexKey = requireText(sessionIndexKey, "sessionIndexKey");
            workspaceIndexKey = requireText(workspaceIndexKey, "workspaceIndexKey");
            processIndexKey = requireText(processIndexKey, "processIndexKey");
        }
    }
}
