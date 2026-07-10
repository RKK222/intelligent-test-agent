package com.icbc.testagent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventId;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.event.RunSessionScope;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunDiffCounts;
import com.icbc.testagent.domain.run.RunRuntimeAppendResult;
import com.icbc.testagent.domain.run.RunRuntimeInput;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeReplay;
import com.icbc.testagent.domain.run.RunRuntimeSnapshot;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunRuntimeStreamEvent;
import com.icbc.testagent.domain.run.RunRuntimeTail;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis Run 运行数据面。单 Run 的 manifest/input/Stream/snapshot/scope key 均使用 {@code {runId}} hash tag；
 * Lua 原子分配 seq、追加 Stream、更新容量计数并刷新 TTL。
 */
public class RedisRunRuntimeStore implements RunRuntimeStore {

    private static final String PREFIX = "test-agent:run:";
    private static final int DEFAULT_SNAPSHOT_EVENT_LIMIT = MAX_DURABLE_EVENTS;

    private static final DefaultRedisScript<Long> INITIALIZE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('HSET', KEYS[1],
              'base', ARGV[1], 'status', ARGV[2], 'statusVersion', ARGV[3],
              'lastSeq', '0', 'earliestSeq', '1', 'runtimeVersion', '0',
              'earliestRuntimeVersion', '1', 'resetGeneration', '0',
              'detailsTruncated', '0', 'durableEventCount', '0', 'runtimeEventCount', '0',
              'diffProposedCount', '0', 'diffAcceptedCount', '0', 'diffRejectedCount', '0',
              'inputBytes', ARGV[8], 'scopeBytes', '0', 'streamBytes', '0',
              'snapshotBytes', tostring(string.len(ARGV[9])),
              'detailBytes', tostring(tonumber(ARGV[8]) + string.len(ARGV[9])),
              'attention', '', 'attentionRequestId', '', 'attentionEventId', '', 'attentionAt', '',
              'detailsExpiresAt', ARGV[4], 'updatedAt', ARGV[5])
            redis.call('SET', KEYS[2], ARGV[6], 'PX', ARGV[7])
            redis.call('HSET', KEYS[6], ARGV[10], ARGV[9])
            redis.call('ZADD', KEYS[7], 0, ARGV[10])
            redis.call('PEXPIRE', KEYS[1], ARGV[7])
            redis.call('SADD', KEYS[3], KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5], KEYS[6], KEYS[7])
            redis.call('PEXPIRE', KEYS[3], ARGV[7])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_CLIENT_REQUEST_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> BIND_REMOTE_SESSION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            redis.call('HSET', KEYS[1], 'rootRemoteSessionId', ARGV[1], 'updatedAt', ARGV[2])
            for _, key in ipairs(redis.call('SMEMBERS', KEYS[2])) do redis.call('PEXPIRE', key, ARGV[3]) end
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<String> APPEND_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return '__MISSING__' end
            local currentStatus = redis.call('HGET', KEYS[1], 'status') or ''
            local nextStatus = ARGV[16]
            if nextStatus ~= '' then
              local currentTerminal = currentStatus == 'SUCCEEDED' or currentStatus == 'FAILED' or currentStatus == 'CANCELLED'
              local nextTerminal = nextStatus == 'SUCCEEDED' or nextStatus == 'FAILED' or nextStatus == 'CANCELLED'
              local statusAllowed = currentStatus == nextStatus or (currentTerminal and nextTerminal)
                or (currentStatus == 'PENDING' and (nextStatus == 'RUNNING' or nextStatus == 'FAILED' or nextStatus == 'CANCELLED'))
                or (currentStatus == 'RUNNING' and (nextStatus == 'CANCELLING' or nextTerminal))
                or (currentStatus == 'CANCELLING' and nextTerminal)
              if not statusAllowed then
                -- 终态之后的晚到非终态事件只消耗一个序号，不能进入可回放 Stream 或物化快照。
                local ignoredSeq = redis.call('HINCRBY', KEYS[1], 'lastSeq', 1)
                return cjson.encode({seq=ignoredSeq,
                  runtimeVersion=tonumber(redis.call('HGET', KEYS[1], 'runtimeVersion') or '0'), ignored=1,
                  truncated=0, resetGeneration=tonumber(redis.call('HGET', KEYS[1], 'resetGeneration') or '0'),
                  earliestSeq=tonumber(redis.call('HGET', KEYS[1], 'earliestSeq') or '1'),
                  earliestRuntimeVersion=tonumber(redis.call('HGET', KEYS[1], 'earliestRuntimeVersion') or '1')})
              end
            end
            if ARGV[25] == 'PROPOSED' then
              redis.call('HINCRBY', KEYS[1], 'diffProposedCount', 1)
            elseif ARGV[25] == 'ACCEPTED' then
              redis.call('HINCRBY', KEYS[1], 'diffAcceptedCount', 1)
            elseif ARGV[25] == 'REJECTED' then
              redis.call('HINCRBY', KEYS[1], 'diffRejectedCount', 1)
            end
            local seq = redis.call('HINCRBY', KEYS[1], 'lastSeq', 1)
            local runtimeVersion = redis.call('HINCRBY', KEYS[1], 'runtimeVersion', 1)
            redis.call('XADD', KEYS[2], tostring(seq) .. '-0', 'draft', ARGV[1])
            redis.call('XADD', KEYS[3], tostring(runtimeVersion) .. '-0',
              'draft', ARGV[1], 'durable', '1', 'seq', tostring(seq))
            local count = redis.call('HINCRBY', KEYS[1], 'durableEventCount', 1)
            local runtimeCount = redis.call('HINCRBY', KEYS[1], 'runtimeEventCount', 1)
            local streamBytes = redis.call('HINCRBY', KEYS[1], 'streamBytes', string.len(ARGV[1]) * 2)

            local projectionKey = ARGV[2]
            local cleanupKey = ARGV[4]
            local snapshotBytes = tonumber(redis.call('HGET', KEYS[1], 'snapshotBytes') or '0')
            if cleanupKey ~= '' then
              local cleanupKeys = {}
              if string.sub(cleanupKey, -1) == '*' then
                local cursor = '0'
                repeat
                  local scanned = redis.call('HSCAN', KEYS[4], cursor, 'MATCH', cleanupKey, 'COUNT', 100)
                  cursor = scanned[1]
                  for index = 1, #scanned[2], 2 do table.insert(cleanupKeys, scanned[2][index]) end
                until cursor == '0'
              else
                table.insert(cleanupKeys, cleanupKey)
              end
              for _, key in ipairs(cleanupKeys) do
                local cleanup = redis.call('HGET', KEYS[4], key)
                if cleanup then snapshotBytes = snapshotBytes - string.len(cleanup) end
                redis.call('HDEL', KEYS[4], key)
                redis.call('ZREM', KEYS[5], key)
              end
            end
            local projected = ARGV[24]
            local previous = redis.call('HGET', KEYS[4], projectionKey)
            if ARGV[3] == 'APPEND_TEXT' and previous then
              local current = cjson.decode(previous)
              local incoming = cjson.decode(ARGV[24])
              current['occurredAt'] = incoming['occurredAt']
              current['traceId'] = incoming['traceId']
              local currentPayload = current['payload'] or {}
              local incomingPayload = incoming['payload'] or {}
              local oldText = currentPayload['delta'] or currentPayload['text'] or ''
              local delta = incomingPayload['delta'] or incomingPayload['text'] or ''
              currentPayload['delta'] = tostring(oldText) .. tostring(delta)
              currentPayload['text'] = currentPayload['delta']
              current['payload'] = currentPayload
              projected = cjson.encode(current)
            end
            redis.call('HSET', KEYS[4], projectionKey, projected)
            if ARGV[5] == '1' or not redis.call('ZSCORE', KEYS[5], projectionKey) then
              redis.call('ZADD', KEYS[5], runtimeVersion, projectionKey)
            end
            snapshotBytes = snapshotBytes + string.len(projected) - (previous and string.len(previous) or 0)

            local evicted = 0
            local function evictSnapshotEntry()
              local ordered = redis.call('ZRANGE', KEYS[5], 0, -1)
              local victim = nil
              for _, key in ipairs(ordered) do
                if string.sub(key, 1, 9) == 'p:latest:' or string.sub(key, 1, 7) == 'p:tool:'
                    or string.sub(key, 1, 7) == 'p:diff:' or string.sub(key, 1, 17) == 'p:session-status:'
                    or string.sub(key, 1, 8) == 'p:child:' or string.sub(key, 1, 8) == 'p:scope:' then
                  victim = key; break
                end
              end
              if not victim then
                for _, key in ipairs(ordered) do
                  if string.sub(key, 1, 13) ~= 'p:run-status:' then victim = key; break end
                end
              end
              if not victim then return false end
              local value = redis.call('HGET', KEYS[4], victim)
              if value then snapshotBytes = snapshotBytes - string.len(value) end
              redis.call('HDEL', KEYS[4], victim)
              redis.call('ZREM', KEYS[5], victim)
              evicted = 1
              return true
            end
            while redis.call('ZCARD', KEYS[5]) > tonumber(ARGV[6]) do
              if not evictSnapshotEntry() then break end
            end
            local baseBytes = tonumber(redis.call('HGET', KEYS[1], 'inputBytes') or '0')
              + tonumber(redis.call('HGET', KEYS[1], 'scopeBytes') or '0')
            while snapshotBytes > math.max(0, tonumber(ARGV[9]) - baseBytes)
                and redis.call('ZCARD', KEYS[5]) > 1 do
              if not evictSnapshotEntry() then break end
            end

            if ARGV[13] ~= '' then
              redis.call('HSET', KEYS[1], 'attention', ARGV[13],
                'attentionRequestId', ARGV[14], 'attentionEventId', ARGV[22] .. tostring(seq), 'attentionAt', ARGV[23])
            elseif ARGV[15] == '1' and (redis.call('HGET', KEYS[1], 'attentionRequestId') or '') == ARGV[14] then
              redis.call('HSET', KEYS[1], 'attention', '', 'attentionRequestId', '',
                'attentionEventId', '', 'attentionAt', '')
            end
            if nextStatus ~= '' then
              if currentStatus ~= nextStatus then
                redis.call('HSET', KEYS[1], 'status', nextStatus)
                redis.call('HINCRBY', KEYS[1], 'statusVersion', 1)
              end
            end

            local attention = redis.call('HGET', KEYS[1], 'attention') or ''
            local currentStatus = redis.call('HGET', KEYS[1], 'status') or ''
            local terminal = ARGV[17] == '1' or currentStatus == 'SUCCEEDED'
              or currentStatus == 'FAILED' or currentStatus == 'CANCELLED'
            local ttl = tonumber(ARGV[10])
            local expiresAt = ARGV[11]
            if attention ~= '' and not terminal then ttl = tonumber(ARGV[18]); expiresAt = ARGV[19] end
            if terminal then ttl = tonumber(ARGV[20]); expiresAt = ARGV[21] end
            redis.call('HSET', KEYS[1], 'updatedAt', ARGV[7], 'detailsExpiresAt', expiresAt)
            local truncated = 0
            local resetGeneration = tonumber(redis.call('HGET', KEYS[1], 'resetGeneration') or '0')
            local earliestSeq = tonumber(redis.call('HGET', KEYS[1], 'earliestSeq') or '1')
            local earliestRuntimeVersion = tonumber(redis.call('HGET', KEYS[1], 'earliestRuntimeVersion') or '1')
            local totalBytes = baseBytes + streamBytes + snapshotBytes
            if count > tonumber(ARGV[8]) or runtimeCount > tonumber(ARGV[8])
                or totalBytes > tonumber(ARGV[9]) or evicted == 1 then
              redis.call('DEL', KEYS[2])
              redis.call('DEL', KEYS[3])
              resetGeneration = redis.call('HINCRBY', KEYS[1], 'resetGeneration', 1)
              earliestSeq = seq + 1
              earliestRuntimeVersion = runtimeVersion + 1
              redis.call('HSET', KEYS[1],
                'detailsTruncated', '1', 'earliestSeq', tostring(earliestSeq),
                'earliestRuntimeVersion', tostring(earliestRuntimeVersion),
                'durableEventCount', '0', 'runtimeEventCount', '0',
                'streamBytes', '0', 'detailBytes', tostring(baseBytes + snapshotBytes))
              truncated = 1
            else
              redis.call('HSET', KEYS[1], 'snapshotBytes', tostring(snapshotBytes), 'detailBytes', tostring(totalBytes))
            end
            redis.call('HSET', KEYS[1], 'snapshotBytes', tostring(snapshotBytes))
            redis.call('SADD', KEYS[6], KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5], KEYS[6], KEYS[7])
            for _, key in ipairs(redis.call('SMEMBERS', KEYS[6])) do redis.call('PEXPIRE', key, ttl) end
            redis.call('PEXPIRE', KEYS[6], ttl)
            return cjson.encode({seq=seq,runtimeVersion=runtimeVersion,ignored=0,truncated=truncated,
              resetGeneration=resetGeneration,earliestSeq=earliestSeq,earliestRuntimeVersion=earliestRuntimeVersion})
            """, String.class);

    private static final DefaultRedisScript<String> PROJECT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return '__MISSING__' end
            local currentStatus = redis.call('HGET', KEYS[1], 'status') or ''
            local nextStatus = ARGV[16]
            if nextStatus ~= '' then
              local currentTerminal = currentStatus == 'SUCCEEDED' or currentStatus == 'FAILED' or currentStatus == 'CANCELLED'
              local nextTerminal = nextStatus == 'SUCCEEDED' or nextStatus == 'FAILED' or nextStatus == 'CANCELLED'
              local statusAllowed = currentStatus == nextStatus or (currentTerminal and nextTerminal)
                or (currentStatus == 'PENDING' and (nextStatus == 'RUNNING' or nextStatus == 'FAILED' or nextStatus == 'CANCELLED'))
                or (currentStatus == 'RUNNING' and (nextStatus == 'CANCELLING' or nextTerminal))
                or (currentStatus == 'CANCELLING' and nextTerminal)
              if not statusAllowed then
                return cjson.encode({
                  runtimeVersion=tonumber(redis.call('HGET', KEYS[1], 'runtimeVersion') or '0'), ignored=1,
                  truncated=0, resetGeneration=tonumber(redis.call('HGET', KEYS[1], 'resetGeneration') or '0'),
                  earliestRuntimeVersion=tonumber(redis.call('HGET', KEYS[1], 'earliestRuntimeVersion') or '1')})
              end
            end
            local runtimeVersion = redis.call('HINCRBY', KEYS[1], 'runtimeVersion', 1)
            redis.call('XADD', KEYS[2], tostring(runtimeVersion) .. '-0',
              'draft', ARGV[1], 'durable', '0', 'seq', '0')
            local runtimeCount = redis.call('HINCRBY', KEYS[1], 'runtimeEventCount', 1)
            local streamBytes = redis.call('HINCRBY', KEYS[1], 'streamBytes', string.len(ARGV[1]))

            local projectionKey = ARGV[2]
            local cleanupKey = ARGV[4]
            local snapshotBytes = tonumber(redis.call('HGET', KEYS[1], 'snapshotBytes') or '0')
            if cleanupKey ~= '' then
              local cleanupKeys = {}
              if string.sub(cleanupKey, -1) == '*' then
                local cursor = '0'
                repeat
                  local scanned = redis.call('HSCAN', KEYS[3], cursor, 'MATCH', cleanupKey, 'COUNT', 100)
                  cursor = scanned[1]
                  for index = 1, #scanned[2], 2 do table.insert(cleanupKeys, scanned[2][index]) end
                until cursor == '0'
              else
                table.insert(cleanupKeys, cleanupKey)
              end
              for _, key in ipairs(cleanupKeys) do
                local cleanup = redis.call('HGET', KEYS[3], key)
                if cleanup then snapshotBytes = snapshotBytes - string.len(cleanup) end
                redis.call('HDEL', KEYS[3], key)
                redis.call('ZREM', KEYS[4], key)
              end
            end
            local projected = ARGV[24]
            local previous = redis.call('HGET', KEYS[3], projectionKey)
            if ARGV[3] == 'APPEND_TEXT' and previous then
              local current = cjson.decode(previous)
              local incoming = cjson.decode(ARGV[24])
              current['occurredAt'] = incoming['occurredAt']
              current['traceId'] = incoming['traceId']
              local currentPayload = current['payload'] or {}
              local incomingPayload = incoming['payload'] or {}
              local oldText = currentPayload['delta'] or currentPayload['text'] or ''
              local delta = incomingPayload['delta'] or incomingPayload['text'] or ''
              currentPayload['delta'] = tostring(oldText) .. tostring(delta)
              currentPayload['text'] = currentPayload['delta']
              current['payload'] = currentPayload
              projected = cjson.encode(current)
            end
            redis.call('HSET', KEYS[3], projectionKey, projected)
            if ARGV[5] == '1' or not redis.call('ZSCORE', KEYS[4], projectionKey) then
              redis.call('ZADD', KEYS[4], runtimeVersion, projectionKey)
            end
            snapshotBytes = snapshotBytes + string.len(projected) - (previous and string.len(previous) or 0)
            local evicted = 0
            local function evictSnapshotEntry()
              local ordered = redis.call('ZRANGE', KEYS[4], 0, -1)
              local victim = nil
              for _, key in ipairs(ordered) do
                if string.sub(key, 1, 9) == 'p:latest:' or string.sub(key, 1, 7) == 'p:tool:'
                    or string.sub(key, 1, 7) == 'p:diff:' or string.sub(key, 1, 17) == 'p:session-status:'
                    or string.sub(key, 1, 8) == 'p:child:' or string.sub(key, 1, 8) == 'p:scope:' then
                  victim = key; break
                end
              end
              if not victim then
                for _, key in ipairs(ordered) do
                  if string.sub(key, 1, 13) ~= 'p:run-status:' then victim = key; break end
                end
              end
              if not victim then return false end
              local value = redis.call('HGET', KEYS[3], victim)
              if value then snapshotBytes = snapshotBytes - string.len(value) end
              redis.call('HDEL', KEYS[3], victim)
              redis.call('ZREM', KEYS[4], victim)
              evicted = 1
              return true
            end
            while redis.call('ZCARD', KEYS[4]) > tonumber(ARGV[6]) do
              if not evictSnapshotEntry() then break end
            end
            local baseBytes = tonumber(redis.call('HGET', KEYS[1], 'inputBytes') or '0')
              + tonumber(redis.call('HGET', KEYS[1], 'scopeBytes') or '0')
            while snapshotBytes > math.max(0, tonumber(ARGV[9]) - baseBytes)
                and redis.call('ZCARD', KEYS[4]) > 1 do
              if not evictSnapshotEntry() then break end
            end
            if ARGV[13] ~= '' then
              redis.call('HSET', KEYS[1], 'attention', ARGV[13],
                'attentionRequestId', ARGV[14], 'attentionEventId', ARGV[22] .. tostring(runtimeVersion),
                'attentionAt', ARGV[23])
            elseif ARGV[15] == '1' and (redis.call('HGET', KEYS[1], 'attentionRequestId') or '') == ARGV[14] then
              redis.call('HSET', KEYS[1], 'attention', '', 'attentionRequestId', '',
                'attentionEventId', '', 'attentionAt', '')
            end
            if nextStatus ~= '' then
              if currentStatus ~= nextStatus then
                redis.call('HSET', KEYS[1], 'status', nextStatus)
                redis.call('HINCRBY', KEYS[1], 'statusVersion', 1)
              end
            end
            local attention = redis.call('HGET', KEYS[1], 'attention') or ''
            local currentStatus = redis.call('HGET', KEYS[1], 'status') or ''
            local terminal = ARGV[17] == '1' or currentStatus == 'SUCCEEDED'
              or currentStatus == 'FAILED' or currentStatus == 'CANCELLED'
            local ttl = tonumber(ARGV[10])
            local expiresAt = ARGV[11]
            if attention ~= '' and not terminal then ttl = tonumber(ARGV[18]); expiresAt = ARGV[19] end
            if terminal then ttl = tonumber(ARGV[20]); expiresAt = ARGV[21] end
            redis.call('HSET', KEYS[1], 'updatedAt', ARGV[7], 'detailsExpiresAt', expiresAt)
            local resetGeneration = tonumber(redis.call('HGET', KEYS[1], 'resetGeneration') or '0')
            local earliestRuntimeVersion = tonumber(redis.call('HGET', KEYS[1], 'earliestRuntimeVersion') or '1')
            local totalBytes = baseBytes + streamBytes + snapshotBytes
            local truncated = 0
            if runtimeCount > tonumber(ARGV[8]) or totalBytes > tonumber(ARGV[9]) or evicted == 1 then
              redis.call('DEL', KEYS[2])
              redis.call('DEL', KEYS[7])
              resetGeneration = redis.call('HINCRBY', KEYS[1], 'resetGeneration', 1)
              earliestRuntimeVersion = runtimeVersion + 1
              redis.call('HSET', KEYS[1], 'detailsTruncated', '1',
                'earliestSeq', tostring(tonumber(redis.call('HGET', KEYS[1], 'lastSeq') or '0') + 1),
                'earliestRuntimeVersion', tostring(earliestRuntimeVersion),
                'durableEventCount', '0', 'runtimeEventCount', '0',
                'streamBytes', '0', 'detailBytes', tostring(baseBytes + snapshotBytes))
              truncated = 1
            else
              redis.call('HSET', KEYS[1], 'snapshotBytes', tostring(snapshotBytes), 'detailBytes', tostring(totalBytes))
            end
            redis.call('HSET', KEYS[1], 'snapshotBytes', tostring(snapshotBytes))
            redis.call('SADD', KEYS[5], KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5], KEYS[6], KEYS[7])
            for _, key in ipairs(redis.call('SMEMBERS', KEYS[5])) do redis.call('PEXPIRE', key, ttl) end
            redis.call('PEXPIRE', KEYS[5], ttl)
            return cjson.encode({runtimeVersion=runtimeVersion,ignored=0,truncated=truncated,
              resetGeneration=resetGeneration,earliestRuntimeVersion=earliestRuntimeVersion})
            """, String.class);

    private static final DefaultRedisScript<String> REPLAY_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return '__MISSING__' end
            local cursor = tonumber(ARGV[1])
            local barrier = tonumber(redis.call('HGET', KEYS[1], 'lastSeq') or '0')
            local earliest = tonumber(redis.call('HGET', KEYS[1], 'earliestSeq') or '1')
            local truncated = redis.call('HGET', KEYS[1], 'detailsTruncated') == '1'
            local reset = truncated and cursor < earliest
            local startSeq = cursor
            local snapshot = {}
            local snapshotKeys = redis.call('ZRANGE', KEYS[4], 0, -1)
            for _, key in ipairs(snapshotKeys) do
              local value = redis.call('HGET', KEYS[3], key)
              if value then table.insert(snapshot, value) end
            end
            if reset then startSeq = barrier end
            local rows = redis.call('XRANGE', KEYS[2], '(' .. tostring(startSeq) .. '-0', '+', 'COUNT', tonumber(ARGV[2]))
            local events = {}
            for _, row in ipairs(rows) do
              table.insert(events, {id=row[1], draft=row[2][2]})
            end
            -- Redis Lua 的空 table 默认编码为 JSON object；显式转换为空数组以保持回放协议稳定。
            if #snapshot == 0 then snapshot = cjson.empty_array end
            if #events == 0 then events = cjson.empty_array end
            return cjson.encode({
              barrierSeq=barrier, earliestSeq=earliest,
              runtimeVersion=tonumber(redis.call('HGET', KEYS[1], 'runtimeVersion') or '0'),
              resetGeneration=tonumber(redis.call('HGET', KEYS[1], 'resetGeneration') or '0'),
              reset=reset, snapshot=snapshot, events=events
            })
            """, String.class);

    private static final DefaultRedisScript<String> TAIL_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return '__MISSING__' end
            local cursor = tonumber(ARGV[1])
            local current = tonumber(redis.call('HGET', KEYS[1], 'runtimeVersion') or '0')
            local earliest = tonumber(redis.call('HGET', KEYS[1], 'earliestRuntimeVersion') or '1')
            local reset = cursor < earliest - 1
            local snapshot = {}
            local events = {}
            if reset then
              local snapshotKeys = redis.call('ZRANGE', KEYS[4], 0, -1)
              for _, key in ipairs(snapshotKeys) do
                local value = redis.call('HGET', KEYS[3], key)
                if value then table.insert(snapshot, value) end
              end
            else
              local rows = redis.call('XRANGE', KEYS[2], '(' .. tostring(cursor) .. '-0', '+', 'COUNT', tonumber(ARGV[2]))
              for _, row in ipairs(rows) do
                local fields = row[2]
                local values = {}
                for index = 1, #fields, 2 do values[fields[index]] = fields[index + 1] end
                table.insert(events, {runtimeVersion=tonumber(string.match(row[1], '^(%d+)')),
                  durable=values['durable'] == '1', seq=tonumber(values['seq'] or '0'), draft=values['draft']})
              end
            end
            if #snapshot == 0 then snapshot = cjson.empty_array end
            if #events == 0 then events = cjson.empty_array end
            return cjson.encode({currentRuntimeVersion=current,reset=reset,
              barrierSeq=tonumber(redis.call('HGET', KEYS[1], 'lastSeq') or '0'),
              resetGeneration=tonumber(redis.call('HGET', KEYS[1], 'resetGeneration') or '0'),
              snapshot=snapshot,events=events})
            """, String.class);

    private static final DefaultRedisScript<Long> SAVE_SNAPSHOT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            if tonumber(redis.call('HGET', KEYS[1], 'lastSeq') or '0') ~= tonumber(ARGV[1]) then return 0 end
            if tonumber(redis.call('HGET', KEYS[1], 'runtimeVersion') or '0') ~= tonumber(ARGV[2]) then return 0 end
            local drafts = cjson.decode(ARGV[3])
            redis.call('DEL', KEYS[2], KEYS[3])
            local bytes = 0
            for index, draft in ipairs(drafts) do
              local key = 'external:' .. tostring(index)
              redis.call('HSET', KEYS[2], key, draft)
              redis.call('ZADD', KEYS[3], index, key)
              bytes = bytes + string.len(draft)
            end
            local baseBytes = tonumber(redis.call('HGET', KEYS[1], 'inputBytes') or '0')
              + tonumber(redis.call('HGET', KEYS[1], 'scopeBytes') or '0')
            redis.call('HSET', KEYS[1], 'snapshotBytes', tostring(bytes),
              'detailBytes', tostring(baseBytes + bytes + tonumber(redis.call('HGET', KEYS[1], 'streamBytes') or '0')))
            redis.call('SADD', KEYS[4], KEYS[1], KEYS[2], KEYS[3], KEYS[4])
            for _, key in ipairs(redis.call('SMEMBERS', KEYS[4])) do redis.call('PEXPIRE', key, ARGV[4]) end
            redis.call('PEXPIRE', KEYS[4], ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<String> DRAIN_PENDING_SCRIPT = new DefaultRedisScript<>("""
            local values = redis.call('LRANGE', KEYS[1], 0, -1)
            redis.call('DEL', KEYS[1])
            if #values == 0 then values = cjson.empty_array end
            return cjson.encode(values)
            """, String.class);

    private static final DefaultRedisScript<Long> CLAIM_RAW_EVENT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local claimed = redis.call('HSETNX', KEYS[2], ARGV[1], '1')
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            redis.call('SADD', KEYS[3], KEYS[1], KEYS[2], KEYS[3])
            redis.call('PEXPIRE', KEYS[3], ARGV[2])
            return claimed
            """, Long.class);

    private static final DefaultRedisScript<Long> APPEND_PENDING_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            redis.call('RPUSH', KEYS[2], ARGV[1])
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            redis.call('SADD', KEYS[3], KEYS[1], KEYS[2], KEYS[3])
            redis.call('PEXPIRE', KEYS[3], ARGV[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> SAVE_SCOPE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[6]) == 0 then return -1 end
            local previousScope = redis.call('GET', KEYS[1])
            local previousSession = redis.call('GET', KEYS[2])
            local previousBytes = (previousScope and string.len(previousScope) or 0)
              + (previousSession and string.len(previousSession) or 0)
            local scopeBytes = tonumber(redis.call('HGET', KEYS[6], 'scopeBytes') or '0')
              + string.len(ARGV[1]) + string.len(ARGV[2]) - previousBytes
            local detailBytes = tonumber(redis.call('HGET', KEYS[6], 'inputBytes') or '0') + scopeBytes
              + tonumber(redis.call('HGET', KEYS[6], 'streamBytes') or '0')
              + tonumber(redis.call('HGET', KEYS[6], 'snapshotBytes') or '0')
            if detailBytes > tonumber(ARGV[6]) then return -2 end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            redis.call('HSET', KEYS[6], 'scopeBytes', tostring(scopeBytes))
            redis.call('HSET', KEYS[6], 'detailBytes', tostring(
              detailBytes))
            local added = redis.call('SADD', KEYS[3], ARGV[4])
            redis.call('PEXPIRE', KEYS[3], ARGV[3])
            if added == 1 then
              local current = tonumber(redis.call('GET', KEYS[4]) or '1')
              redis.call('SET', KEYS[4], tostring(math.max(current, tonumber(ARGV[5]))), 'PX', ARGV[3])
            else
              redis.call('PEXPIRE', KEYS[4], ARGV[3])
            end
            redis.call('SADD', KEYS[5], KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5])
            redis.call('PEXPIRE', KEYS[5], ARGV[3])
            return added
            """, Long.class);

    private static final DefaultRedisScript<Long> UPDATE_STATUS_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local current = tonumber(redis.call('HGET', KEYS[1], 'statusVersion') or '0')
            if current ~= tonumber(ARGV[1]) then return 0 end
            local currentStatus = redis.call('HGET', KEYS[1], 'status') or ''
            local nextStatus = ARGV[2]
            local currentTerminal = currentStatus == 'SUCCEEDED' or currentStatus == 'FAILED' or currentStatus == 'CANCELLED'
            local nextTerminal = nextStatus == 'SUCCEEDED' or nextStatus == 'FAILED' or nextStatus == 'CANCELLED'
            local allowed = (currentTerminal and nextTerminal)
              or (currentStatus == 'PENDING' and (nextStatus == 'RUNNING' or nextStatus == 'FAILED' or nextStatus == 'CANCELLED'))
              or (currentStatus == 'RUNNING' and (nextStatus == 'CANCELLING' or nextTerminal))
              or (currentStatus == 'CANCELLING' and nextTerminal)
            if not allowed then return -2 end
            redis.call('HSET', KEYS[1],
              'status', ARGV[2], 'statusVersion', tostring(current + 1),
              'attention', ARGV[3], 'updatedAt', ARGV[4], 'detailsExpiresAt', ARGV[5])
            if ARGV[3] == '' then
              redis.call('HSET', KEYS[1], 'attentionEventId', '', 'attentionAt', '')
            end
            for _, key in ipairs(redis.call('SMEMBERS', KEYS[2])) do redis.call('PEXPIRE', key, ARGV[6]) end
            redis.call('PEXPIRE', KEYS[2], ARGV[6])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration activeTtl;
    private final Duration terminalTtl;
    private final Duration pendingTtl;
    private final int maxDurableEvents;
    private final long maxDetailBytes;
    private final int snapshotEventLimit;

    public RedisRunRuntimeStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, Clock.systemUTC());
    }

    RedisRunRuntimeStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Clock clock) {
        this(
                redisTemplate,
                objectMapper,
                clock,
                ACTIVE_TTL,
                TERMINAL_DETAILS_TTL,
                PENDING_ASK_TTL,
                MAX_DURABLE_EVENTS,
                MAX_DETAIL_BYTES,
                DEFAULT_SNAPSHOT_EVENT_LIMIT);
    }

    RedisRunRuntimeStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            Duration activeTtl,
            Duration terminalTtl,
            Duration pendingTtl,
            int maxDurableEvents,
            long maxDetailBytes,
            int snapshotEventLimit) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.activeTtl = positive(activeTtl, "activeTtl");
        this.terminalTtl = positive(terminalTtl, "terminalTtl");
        this.pendingTtl = positive(pendingTtl, "pendingTtl");
        if (maxDurableEvents <= 0 || maxDetailBytes <= 0 || snapshotEventLimit <= 0) {
            throw new IllegalArgumentException("runtime limits must be positive");
        }
        this.maxDurableEvents = maxDurableEvents;
        this.maxDetailBytes = maxDetailBytes;
        this.snapshotEventLimit = snapshotEventLimit;
    }

    @Override
    public void initialize(RunRuntimeManifest manifest, RunRuntimeInput input) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(input, "input must not be null");
        if (!manifest.runId().equals(input.runId())) {
            throw new IllegalArgumentException("manifest and input runId must match");
        }
        try {
            String serializedInput = write(input);
            long inputBytes = serializedInput.getBytes(StandardCharsets.UTF_8).length;
            RunEventDraft inputSnapshot = inputSnapshot(manifest, input);
            String serializedInputSnapshot = snapshotJson(inputSnapshot);
            long inputSnapshotBytes = serializedInputSnapshot.getBytes(StandardCharsets.UTF_8).length;
            if (inputBytes + inputSnapshotBytes > maxDetailBytes) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Run 输入超过 Redis 详情容量上限");
            }
            Long created = redisTemplate.execute(
                    INITIALIZE_SCRIPT,
                    List.of(
                            manifestKey(manifest.runId()),
                            inputKey(manifest.runId()),
                            registryKey(manifest.runId()),
                            streamKey(manifest.runId()),
                            runtimeStreamKey(manifest.runId()),
                            snapshotKey(manifest.runId()),
                            snapshotOrderKey(manifest.runId())),
                    write(manifest),
                    manifest.status().name(),
                    Long.toString(manifest.statusVersion()),
                    manifest.detailsExpiresAt().toString(),
                    manifest.updatedAt().toString(),
                    serializedInput,
                    Long.toString(activeTtl.toMillis()),
                    Long.toString(inputBytes),
                    serializedInputSnapshot,
                    projection(inputSnapshot).key());
            if (created == null) {
                throw new IllegalStateException("Redis Run initialize returned no result");
            }
            if (manifest.userId() != null) {
                redisTemplate.opsForValue().set(userRuntimeMarkerKey(manifest.userId()), "1", terminalTtl);
            }
            indexActive(manifest);
            indexHistory(manifest);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public boolean claimClientRequest(SessionId sessionId, String clientRequestId, RunId runId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new IllegalArgumentException("clientRequestId must not be blank");
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    clientRequestKey(sessionId, clientRequestId), runId.value(), pendingTtl));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<RunId> findByClientRequest(SessionId sessionId, String clientRequestId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(clientRequestKey(sessionId, clientRequestId));
            return value == null ? Optional.empty() : Optional.of(new RunId(value));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void releaseClientRequest(SessionId sessionId, String clientRequestId, RunId runId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return;
        }
        try {
            redisTemplate.execute(
                    RELEASE_CLIENT_REQUEST_SCRIPT,
                    List.of(clientRequestKey(sessionId, clientRequestId)),
                    runId.value());
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<RunRuntimeManifest> findManifest(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(manifestKey(runId));
            if (fields == null || fields.isEmpty()) {
                return Optional.empty();
            }
            RunRuntimeManifest base = objectMapper.readValue(text(fields, "base"), RunRuntimeManifest.class);
            return Optional.of(overlay(base, fields));
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<RunRuntimeInput> findInput(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        try {
            String json = redisTemplate.opsForValue().get(inputKey(runId));
            return json == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(json, RunRuntimeInput.class));
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public RunDiffCounts diffCounts(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        try {
            List<Object> values = redisTemplate.opsForHash().multiGet(
                    manifestKey(runId),
                    List.of("diffProposedCount", "diffAcceptedCount", "diffRejectedCount"));
            if (values == null || values.size() != 3) {
                throw new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期");
            }
            return new RunDiffCounts(integer(values.get(0)), integer(values.get(1)), integer(values.get(2)));
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public void bindRemoteSession(RunId runId, String remoteSessionId) {
        Objects.requireNonNull(runId, "runId must not be null");
        if (remoteSessionId == null || remoteSessionId.isBlank()) {
            throw new IllegalArgumentException("remoteSessionId must not be blank");
        }
        try {
            Duration ttl = ttlFor(runId);
            Long updated = redisTemplate.execute(
                    BIND_REMOTE_SESSION_SCRIPT,
                    List.of(manifestKey(runId), registryKey(runId)),
                    remoteSessionId,
                    clock.instant().toString(),
                    Long.toString(ttl.toMillis()));
            if (updated == null || updated == 0L) {
                throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run Redis manifest 不存在");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public RunRuntimeAppendResult appendDurable(RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        try {
            Instant now = clock.instant();
            SnapshotProjection projection = projection(draft);
            String result = redisTemplate.execute(
                    APPEND_SCRIPT,
                    operationKeys(draft.runId()),
                    operationArguments(draft, projection, now, true));
            if (result == null || "__MISSING__".equals(result)) {
                throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run Redis manifest 不存在");
            }
            AppendScriptResult appended = objectMapper.readValue(result, AppendScriptResult.class);
            refreshRuntimeIndexes(draft.runId());
            RunEvent event = event(draft, appended.seq());
            return new RunRuntimeAppendResult(
                    event,
                    appended.truncated() == 1,
                    appended.resetGeneration(),
                    appended.earliestSeq(),
                    appended.ignored() == 0);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public boolean projectTransient(RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        try {
            Instant now = clock.instant();
            SnapshotProjection projection = projection(draft);
            String projected = redisTemplate.execute(
                    PROJECT_SCRIPT,
                    List.of(
                            manifestKey(draft.runId()),
                            runtimeStreamKey(draft.runId()),
                            snapshotKey(draft.runId()),
                            snapshotOrderKey(draft.runId()),
                            registryKey(draft.runId()),
                            inputKey(draft.runId()),
                            streamKey(draft.runId())),
                    operationArguments(draft, projection, now, false));
            if (projected == null || "__MISSING__".equals(projected)) {
                throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run Redis manifest 不存在");
            }
            ProjectScriptResult result = objectMapper.readValue(projected, ProjectScriptResult.class);
            refreshRuntimeIndexes(draft.runId());
            return result.ignored() == 0;
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public void saveSnapshot(RunRuntimeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        try {
            Duration ttl = ttlFor(snapshot.runId());
            Long saved = redisTemplate.execute(
                    SAVE_SNAPSHOT_SCRIPT,
                    List.of(
                            manifestKey(snapshot.runId()),
                            snapshotKey(snapshot.runId()),
                            snapshotOrderKey(snapshot.runId()),
                            registryKey(snapshot.runId())),
                    Long.toString(snapshot.barrierSeq()),
                    Long.toString(snapshot.runtimeVersion()),
                    write(snapshot.events().stream().map(this::write).toList()),
                    Long.toString(ttl.toMillis()));
            if (saved == null || saved == -1L) {
                throw new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期");
            }
            if (saved == 0L) {
                throw new PlatformException(ErrorCode.CONFLICT, "Run snapshot barrier 已变化");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public RunRuntimeReplay replayAfter(RunId runId, long lastSeq, int limit) {
        Objects.requireNonNull(runId, "runId must not be null");
        if (lastSeq < 0 || limit <= 0) {
            throw new IllegalArgumentException("lastSeq must not be negative and limit must be positive");
        }
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                String json = redisTemplate.execute(
                        REPLAY_SCRIPT,
                        List.of(manifestKey(runId), streamKey(runId), snapshotKey(runId), snapshotOrderKey(runId)),
                        Long.toString(lastSeq),
                        Integer.toString(limit));
                if (json == null || "__MISSING__".equals(json)) {
                    throw new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期");
                }
                ReplayScriptResult result = objectMapper.readValue(json, ReplayScriptResult.class);
                RunRuntimeManifest manifest = findManifest(runId)
                        .orElseThrow(() -> new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期"));
                if (manifest.resetGeneration() != result.resetGeneration()) {
                    continue;
                }
                List<RunEventDraft> snapshotDrafts = readDrafts(result.snapshot());
                RunRuntimeSnapshot snapshot = new RunRuntimeSnapshot(
                        runId,
                        result.barrierSeq(),
                        result.runtimeVersion(),
                        result.resetGeneration(),
                        snapshotDrafts,
                        clock.instant());
                List<RunEvent> events = result.events().stream()
                        .map(row -> event(readDraft(row.draft()), seq(row.id())))
                        .toList();
                return new RunRuntimeReplay(
                        manifest,
                        snapshot,
                        events,
                        result.reset(),
                        result.reset() ? "CURSOR_BEFORE_EARLIEST_OR_DETAILS_TRUNCATED" : null);
            }
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run snapshot generation 持续变化");
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public RunRuntimeTail tailAfter(RunId runId, long runtimeVersion, int limit) {
        Objects.requireNonNull(runId, "runId must not be null");
        if (runtimeVersion < 0 || limit <= 0) {
            throw new IllegalArgumentException("runtimeVersion must not be negative and limit must be positive");
        }
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                String json = redisTemplate.execute(
                        TAIL_SCRIPT,
                        List.of(
                                manifestKey(runId),
                                runtimeStreamKey(runId),
                                snapshotKey(runId),
                                snapshotOrderKey(runId)),
                        Long.toString(runtimeVersion),
                        Integer.toString(limit));
                if (json == null || "__MISSING__".equals(json)) {
                    throw new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期");
                }
                TailScriptResult result = objectMapper.readValue(json, TailScriptResult.class);
                RunRuntimeManifest manifest = findManifest(runId)
                        .orElseThrow(() -> new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期"));
                if (manifest.resetGeneration() != result.resetGeneration()) {
                    continue;
                }
                RunRuntimeSnapshot snapshot = result.reset()
                        ? new RunRuntimeSnapshot(
                                runId,
                                result.barrierSeq(),
                                result.currentRuntimeVersion(),
                                result.resetGeneration(),
                                readDrafts(result.snapshot()),
                                clock.instant())
                        : RunRuntimeSnapshot.empty(runId);
                List<RunRuntimeStreamEvent> events = result.events().stream()
                        .map(row -> new RunRuntimeStreamEvent(
                                row.runtimeVersion(), row.durable(), row.seq(), readDraft(row.draft())))
                        .toList();
                return new RunRuntimeTail(
                        manifest,
                        snapshot,
                        events,
                        result.reset(),
                        result.reset() ? "RUNTIME_STREAM_TRUNCATED" : null);
            }
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run runtime generation 持续变化");
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public void saveScope(RunSessionScope scope, RunSessionScopeSession session) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(session, "session must not be null");
        if (!scope.runId().equals(session.runId())) {
            throw new IllegalArgumentException("scope and session runId must match");
        }
        try {
            Duration ttl = ttlFor(scope.runId());
            String serializedScope = write(scope);
            String serializedSession = write(session);
            Long saved = redisTemplate.execute(
                    SAVE_SCOPE_SCRIPT,
                    List.of(
                            scopeKey(scope.runId()),
                            scopeSessionKey(scope.runId(), session.sessionId()),
                            scopeSessionsKey(scope.runId()),
                            scopeVersionKey(scope.runId()),
                            registryKey(scope.runId()),
                            manifestKey(scope.runId())),
                    serializedScope,
                    serializedSession,
                    Long.toString(ttl.toMillis()),
                    session.sessionId(),
                    Long.toString(scope.scopeVersion()),
                    Long.toString(maxDetailBytes));
            if (saved == null || saved == -1L) {
                throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run Redis manifest 不存在");
            }
            if (saved == -2L) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Run scope 超过 Redis 详情容量上限");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<RunSessionScopeSession> findScopeSession(RunId runId, String sessionId) {
        Objects.requireNonNull(runId, "runId must not be null");
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(scopeSessionKey(runId, sessionId));
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, RunSessionScopeSession.class));
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public long scopeVersion(RunId runId) {
        try {
            String value = redisTemplate.opsForValue().get(scopeVersionKey(runId));
            return value == null ? 1L : Long.parseLong(value);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public boolean claimRawEvent(RunId runId, String sessionId, String rawEventId) {
        if (rawEventId == null || rawEventId.isBlank()) {
            return true;
        }
        try {
            String key = dedupKey(runId);
            Long claimed = redisTemplate.execute(
                    CLAIM_RAW_EVENT_SCRIPT,
                    List.of(manifestKey(runId), key, registryKey(runId)),
                    digest((sessionId == null ? "" : sessionId) + '\u0000' + rawEventId),
                    Long.toString(activeTtl.toMillis()));
            if (claimed == null || claimed == -1L) {
                throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run Redis manifest 不存在");
            }
            return claimed == 1L;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void appendPending(String sessionId, RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        try {
            String key = pendingKey(draft.runId(), sessionId);
            Long appended = redisTemplate.execute(
                    APPEND_PENDING_SCRIPT,
                    List.of(manifestKey(draft.runId()), key, registryKey(draft.runId())),
                    write(draft),
                    Long.toString(pendingTtl.toMillis()));
            if (appended == null || appended == 0L) {
                throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run Redis manifest 不存在");
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<RunEventDraft> drainPending(RunId runId, String sessionId) {
        try {
            String key = pendingKey(runId, sessionId);
            String json = redisTemplate.execute(DRAIN_PENDING_SCRIPT, List.of(key));
            List<String> values = json == null
                    ? List.of()
                    : objectMapper.readValue(json, new TypeReference<List<String>>() { });
            return readDrafts(values);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<RunRuntimeManifest> findActiveBySession(SessionId sessionId) {
        try {
            String runId = redisTemplate.opsForValue().get(activeSessionKey(sessionId));
            if (runId == null) {
                return Optional.empty();
            }
            Optional<RunRuntimeManifest> manifest = findManifest(new RunId(runId))
                    .filter(RunRuntimeManifest::active)
                    .filter(item -> item.sessionId().equals(sessionId));
            if (manifest.isEmpty()) {
                redisTemplate.delete(activeSessionKey(sessionId));
            }
            return manifest;
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) throw platformException;
            throw unavailable(exception);
        }
    }

    @Override
    public List<RunRuntimeManifest> findRecentBySession(SessionId sessionId, int limit) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        String key = historySessionKey(sessionId);
        try {
            Set<String> members = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1L);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            Instant now = clock.instant();
            List<RunRuntimeManifest> manifests = new ArrayList<>();
            for (String member : members) {
                Optional<RunRuntimeManifest> manifest = findManifest(new RunId(member))
                        .filter(item -> item.sessionId().equals(sessionId))
                        .filter(item -> item.detailsExpiresAt().isAfter(now));
                if (manifest.isPresent()) {
                    manifests.add(manifest.orElseThrow());
                } else {
                    redisTemplate.opsForZSet().remove(key, member);
                }
            }
            return List.copyOf(manifests);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) throw platformException;
            throw unavailable(exception);
        }
    }

    @Override
    public List<RunRuntimeManifest> findActiveByUser(UserId userId) {
        return findActiveFromIndex(activeUserKey(userId), manifest -> userId.equals(manifest.userId()));
    }

    @Override
    public boolean hasUserRuntimeState(UserId userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(userRuntimeMarkerKey(userId)));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<RunRuntimeManifest> findActiveByServer(String linuxServerId) {
        if (linuxServerId == null || linuxServerId.isBlank()) {
            return List.of();
        }
        return findActiveFromIndex(
                activeServerKey(linuxServerId),
                manifest -> linuxServerId.equals(manifest.producerLinuxServerId()));
    }

    @Override
    public void updateStatus(RunId runId, RunStatus status, long expectedStatusVersion, String attention) {
        Objects.requireNonNull(status, "status must not be null");
        findManifest(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期"));
        Duration ttl = status.isTerminal()
                ? terminalTtl
                : (attention == null || attention.isBlank() ? activeTtl : pendingTtl);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        try {
            Long result = redisTemplate.execute(
                    UPDATE_STATUS_SCRIPT,
                    List.of(manifestKey(runId), registryKey(runId)),
                    Long.toString(expectedStatusVersion),
                    status.name(),
                    attention == null ? "" : attention,
                    now.toString(),
                    expiresAt.toString(),
                    Long.toString(ttl.toMillis()));
            if (result == null || result == -1L) {
                throw new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期");
            }
            if (result == -2L) {
                throw new PlatformException(ErrorCode.CONFLICT, "Run 状态不允许回退");
            }
            if (result == 0L) {
                throw new PlatformException(ErrorCode.CONFLICT, "Run 运行态版本冲突");
            }
            refreshRuntimeIndexes(runId);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) throw platformException;
            throw unavailable(exception);
        }
    }

    @Override
    public void touch(RunId runId) {
        RunRuntimeManifest manifest = findManifest(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期"));
        Duration ttl = runtimeTtl(manifest);
        try {
            Set<String> keys = redisTemplate.opsForSet().members(registryKey(runId));
            for (String key : keys == null ? Set.<String>of() : keys) {
                redisTemplate.expire(key, ttl);
            }
            redisTemplate.expire(registryKey(runId), ttl);
            Instant now = clock.instant();
            redisTemplate.opsForHash().put(manifestKey(runId), "updatedAt", now.toString());
            redisTemplate.opsForHash().put(manifestKey(runId), "detailsExpiresAt", now.plus(ttl).toString());
            if (manifest.active()) {
                indexActive(manifest);
            }
            indexHistory(manifest);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private List<RunRuntimeManifest> findActiveFromIndex(
            String key,
            java.util.function.Predicate<RunRuntimeManifest> ownerCheck) {
        try {
            long now = clock.millis();
            Set<String> members = redisTemplate.opsForZSet().rangeByScore(key, now, Double.POSITIVE_INFINITY);
            redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now - 1D);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<RunRuntimeManifest> active = new ArrayList<>();
            for (String member : members) {
                Optional<RunRuntimeManifest> manifest = findManifest(new RunId(member));
                if (manifest.filter(RunRuntimeManifest::active).filter(ownerCheck).isPresent()) {
                    active.add(manifest.orElseThrow());
                } else {
                    redisTemplate.opsForZSet().remove(key, member);
                }
            }
            return List.copyOf(active);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) throw platformException;
            throw unavailable(exception);
        }
    }

    private void indexActive(RunRuntimeManifest manifest) {
        if (!manifest.active()) {
            return;
        }
        Duration ttl = runtimeTtl(manifest);
        long expiresAt = clock.instant().plus(ttl).toEpochMilli();
        if (manifest.userId() != null) {
            redisTemplate.opsForZSet().add(activeUserKey(manifest.userId()), manifest.runId().value(), expiresAt);
            redisTemplate.expire(activeUserKey(manifest.userId()), ttl);
        }
        redisTemplate.opsForValue().set(activeSessionKey(manifest.sessionId()), manifest.runId().value(), ttl);
        redisTemplate.opsForZSet().add(
                activeServerKey(manifest.producerLinuxServerId()), manifest.runId().value(), expiresAt);
        redisTemplate.expire(activeServerKey(manifest.producerLinuxServerId()), ttl);
    }

    private void removeActive(RunRuntimeManifest manifest) {
        if (manifest.userId() != null) {
            redisTemplate.opsForZSet().remove(activeUserKey(manifest.userId()), manifest.runId().value());
        }
        String sessionRun = redisTemplate.opsForValue().get(activeSessionKey(manifest.sessionId()));
        if (manifest.runId().value().equals(sessionRun)) {
            redisTemplate.delete(activeSessionKey(manifest.sessionId()));
        }
        redisTemplate.opsForZSet().remove(
                activeServerKey(manifest.producerLinuxServerId()), manifest.runId().value());
    }

    private void indexHistory(RunRuntimeManifest manifest) {
        Duration ttl = runtimeTtl(manifest);
        String key = historySessionKey(manifest.sessionId());
        redisTemplate.opsForZSet().add(key, manifest.runId().value(), manifest.updatedAt().toEpochMilli());
        redisTemplate.expire(key, ttl);
    }

    private RunRuntimeManifest overlay(RunRuntimeManifest base, Map<Object, Object> fields) {
        return new RunRuntimeManifest(
                base.runId(), base.storageMode(), base.userId(), base.sessionId(), base.workspaceId(), base.agentId(),
                base.clientRequestId(), base.dispatchMessageId(), base.producerLinuxServerId(), base.backendProcessId(),
                base.executionNodeId(), base.opencodeProcessId(),
                optionalText(fields, "rootRemoteSessionId") == null
                        ? base.rootRemoteSessionId()
                        : optionalText(fields, "rootRemoteSessionId"),
                RunStatus.valueOf(text(fields, "status")),
                number(fields, "statusVersion"), number(fields, "lastSeq"), number(fields, "earliestSeq"),
                number(fields, "resetGeneration"), "1".equals(text(fields, "detailsTruncated")),
                number(fields, "durableEventCount"), number(fields, "detailBytes"),
                optionalText(fields, "attention"), optionalText(fields, "attentionEventId"),
                optionalInstant(fields, "attentionAt"),
                Instant.parse(text(fields, "detailsExpiresAt")),
                base.createdAt(), Instant.parse(text(fields, "updatedAt")));
    }

    private RunEvent event(RunEventDraft draft, long seq) {
        return new RunEvent(
                new RunEventId("evt_redis_" + draft.runId().value().substring("run_".length()) + "_" + seq),
                draft.runId(), seq, draft.type(), draft.traceId(), draft.occurredAt(), draft.payload(), draft.scopeContext());
    }

    private List<RunEventDraft> readDrafts(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(this::readDraft).toList();
    }

    private RunEventDraft readDraft(String json) {
        try {
            return objectMapper.readValue(json, RunEventDraft.class);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private long seq(String streamId) {
        int separator = streamId.indexOf('-');
        return Long.parseLong(separator < 0 ? streamId : streamId.substring(0, separator));
    }

    private Duration ttlFor(RunId runId) {
        return findManifest(runId)
                .map(this::runtimeTtl)
                .orElse(activeTtl);
    }

    private Duration runtimeTtl(RunRuntimeManifest manifest) {
        if (manifest.status().isTerminal()) {
            return terminalTtl;
        }
        return manifest.attention() == null || manifest.attention().isBlank() ? activeTtl : pendingTtl;
    }

    private void refreshRuntimeIndexes(RunId runId) {
        RunRuntimeManifest manifest = findManifest(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RUN_DETAILS_EXPIRED, "Run 详情已过期"));
        if (manifest.active()) {
            indexActive(manifest);
        } else {
            removeActive(manifest);
        }
        indexHistory(manifest);
        if (manifest.userId() != null) {
            Duration runtimeTtl = runtimeTtl(manifest);
            Duration markerTtl = runtimeTtl.compareTo(terminalTtl) > 0 ? runtimeTtl : terminalTtl;
            redisTemplate.opsForValue().set(userRuntimeMarkerKey(manifest.userId()), "1", markerTtl);
        }
    }

    private List<String> operationKeys(RunId runId) {
        return List.of(
                manifestKey(runId),
                streamKey(runId),
                runtimeStreamKey(runId),
                snapshotKey(runId),
                snapshotOrderKey(runId),
                registryKey(runId),
                inputKey(runId));
    }

    private Object[] operationArguments(
            RunEventDraft draft,
            SnapshotProjection projection,
            Instant now,
            boolean durable) {
        AttentionMutation attention = attentionMutation(draft);
        String status = statusFrom(draft.type()).map(Enum::name).orElse("");
        boolean terminal = statusFrom(draft.type()).map(RunStatus::isTerminal).orElse(false);
        return new Object[] {
                write(draft),
                projection.key(),
                projection.mode(),
                projection.cleanupKey() == null ? "" : projection.cleanupKey(),
                projection.latestOrder() ? "1" : "0",
                Integer.toString(snapshotEventLimit),
                now.toString(),
                Integer.toString(maxDurableEvents),
                Long.toString(maxDetailBytes),
                Long.toString(activeTtl.toMillis()),
                now.plus(activeTtl).toString(),
                "",
                attention.value() == null ? "" : attention.value(),
                attention.eventId() == null ? "" : attention.eventId(),
                attention.clear() ? "1" : "0",
                status,
                terminal ? "1" : "0",
                Long.toString(pendingTtl.toMillis()),
                now.plus(pendingTtl).toString(),
                Long.toString(terminalTtl.toMillis()),
                now.plus(terminalTtl).toString(),
                durable
                        ? "evt_redis_" + draft.runId().value().substring("run_".length()) + "_"
                        : "evt_runtime_" + draft.runId().value() + "_",
                draft.occurredAt().toString(),
                snapshotJson(draft),
                durable ? diffMutation(draft.type()) : ""
        };
    }

    private String diffMutation(RunEventType type) {
        return switch (type) {
            case DIFF_PROPOSED -> "PROPOSED";
            case DIFF_ACCEPTED -> "ACCEPTED";
            case DIFF_REJECTED -> "REJECTED";
            default -> "";
        };
    }

    /** 初始化时把本轮用户输入同时放入物化快照，reset 后无需依赖前端乐观消息。 */
    private RunEventDraft inputSnapshot(RunRuntimeManifest manifest, RunRuntimeInput input) {
        String messageId = input.messageId() == null || input.messageId().isBlank()
                ? "msg_input_" + input.runId().value().substring("run_".length())
                : input.messageId();
        LinkedHashMap<String, Object> message = new LinkedHashMap<>();
        message.put("id", messageId);
        message.put("role", "user");
        message.put("text", input.prompt());
        if (manifest.rootRemoteSessionId() != null) {
            message.put("sessionID", manifest.rootRemoteSessionId());
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", messageId);
        payload.put("role", "user");
        payload.put("text", input.prompt());
        payload.put("message", Map.copyOf(message));
        if (manifest.rootRemoteSessionId() != null) {
            payload.put("sessionId", manifest.rootRemoteSessionId());
        }
        return new RunEventDraft(
                input.runId(),
                RunEventType.MESSAGE_UPDATED,
                "trace_runtime_input",
                input.createdAt(),
                Map.copyOf(payload));
    }

    private String snapshotJson(RunEventDraft draft) {
        String serialized = write(draft);
        long perEntryBytes = Math.min(1024L * 1024L, Math.max(1024L, maxDetailBytes / 4L));
        if (serialized.getBytes(StandardCharsets.UTF_8).length <= perEntryBytes) {
            return serialized;
        }
        SnapshotBudget budget = new SnapshotBudget(Math.max(256, (int) Math.min(Integer.MAX_VALUE, perEntryBytes / 4L)));
        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedPayload = (Map<String, Object>) normalizeSnapshotValue(draft.payload(), budget, 0);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(normalizedPayload);
        payload.put("snapshotTruncated", true);
        RunEventDraft normalized = new RunEventDraft(
                draft.runId(), draft.type(), draft.traceId(), draft.occurredAt(), payload, draft.scopeContext());
        String result = write(normalized);
        if (result.getBytes(StandardCharsets.UTF_8).length <= perEntryBytes) {
            return result;
        }
        return write(new RunEventDraft(
                draft.runId(),
                draft.type(),
                draft.traceId(),
                draft.occurredAt(),
                minimalSnapshotPayload(draft.payload()),
                draft.scopeContext()));
    }

    private Object normalizeSnapshotValue(Object value, SnapshotBudget budget, int depth) {
        if (value == null || budget.remaining() <= 0) {
            return null;
        }
        if (value instanceof String text) {
            int allowed = Math.min(text.length(), budget.remaining());
            String normalized = text.substring(0, allowed);
            budget.consume(allowed);
            return allowed < text.length() ? normalized + "…[truncated]" : normalized;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (depth >= 8) {
            return "[nested content truncated]";
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || budget.remaining() <= 0) {
                    continue;
                }
                Object item = normalizeSnapshotValue(entry.getValue(), budget, depth + 1);
                if (item != null) {
                    normalized.put(key, item);
                }
            }
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            int count = 0;
            for (Object item : collection) {
                if (budget.remaining() <= 0 || count++ >= 500) {
                    break;
                }
                Object projected = normalizeSnapshotValue(item, budget, depth + 1);
                if (projected != null) {
                    normalized.add(projected);
                }
            }
            return List.copyOf(normalized);
        }
        return value.toString();
    }

    private Map<String, Object> minimalSnapshotPayload(Map<String, Object> payload) {
        LinkedHashMap<String, Object> minimal = new LinkedHashMap<>();
        for (String key : List.of(
                "sessionId", "sessionID", "rootSessionId", "messageId", "messageID", "partId", "partID",
                "callId", "callID", "requestId", "requestID", "status", "type", "field", "rawType")) {
            Object value = payload.get(key);
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                minimal.put(key, value);
            }
        }
        minimal.put("snapshotTruncated", true);
        return Map.copyOf(minimal);
    }

    private SnapshotProjection projection(RunEventDraft draft) {
        Map<String, Object> payload = draft.payload();
        String session = firstText(payload, "sessionId", "sessionID", "rootSessionId")
                .or(() -> nestedText(payload, "info", "sessionId", "sessionID"))
                .or(() -> nestedText(payload, "part", "sessionId", "sessionID"))
                .orElseGet(() -> draft.scopeContext() == null ? "root" : draft.scopeContext().sessionId());
        String message = firstText(payload, "messageId", "messageID")
                .or(() -> nestedText(payload, "info", "id", "messageId", "messageID"))
                .or(() -> nestedText(payload, "part", "messageId", "messageID"))
                .or(() -> nestedText(payload, "message", "id", "messageId", "messageID"))
                .orElse("message");
        String part = firstText(payload, "partId", "partID")
                .or(() -> nestedText(payload, "part", "id", "partId", "partID"))
                .orElse("part");
        String field = firstText(payload, "field").orElse("text");
        String logical;
        String projectionKey = null;
        String cleanup = null;
        boolean latest = false;
        String mode = "REPLACE";
        switch (draft.type()) {
            case MESSAGE_UPDATED -> logical = "message|" + session + '|' + message;
            case MESSAGE_REMOVED -> {
                logical = "message|" + session + '|' + message;
                latest = true;
            }
            case MESSAGE_PART_UPDATED -> {
                String partIdentity = session + '|' + message + '|' + part;
                logical = "part|" + partIdentity;
                projectionKey = "p:part:" + digest(partIdentity);
                cleanup = "p:part-delta:" + digest(partIdentity) + ":*";
            }
            case MESSAGE_PART_REMOVED -> {
                String partIdentity = session + '|' + message + '|' + part;
                logical = "part|" + partIdentity;
                projectionKey = "p:part:" + digest(partIdentity);
                cleanup = "p:part-delta:" + digest(partIdentity) + ":*";
                latest = true;
            }
            case MESSAGE_PART_DELTA -> {
                String partIdentity = session + '|' + message + '|' + part;
                logical = "part-delta|" + partIdentity + '|' + field;
                projectionKey = "p:part-delta:" + digest(partIdentity) + ':' + digest(field);
                mode = "APPEND_TEXT";
            }
            case ASSISTANT_MESSAGE_DELTA -> {
                logical = "assistant-delta|" + session + '|' + message;
                mode = "APPEND_TEXT";
            }
            case TODO_UPDATED -> logical = "todo|" + session;
            case PERMISSION_ASKED, PERMISSION_REPLIED -> {
                logical = "permission|" + session + '|' + requestId(payload);
                latest = draft.type() == RunEventType.PERMISSION_REPLIED;
            }
            case QUESTION_ASKED, QUESTION_REPLIED, QUESTION_REJECTED -> {
                logical = "question|" + session + '|' + requestId(payload);
                latest = draft.type() != RunEventType.QUESTION_ASKED;
            }
            case TOOL_STARTED, TOOL_FINISHED -> {
                logical = "tool|" + session + '|' + firstText(payload, "callId", "callID", "partId", "partID")
                        .orElse(part);
                latest = draft.type() == RunEventType.TOOL_FINISHED;
            }
            case DIFF_PROPOSED, DIFF_ACCEPTED, DIFF_REJECTED, SESSION_DIFF -> {
                logical = "diff|" + session + '|' + firstText(payload, "diffId", "id", "path", "messageId")
                        .orElse("latest");
                latest = draft.type() != RunEventType.DIFF_PROPOSED;
            }
            case SESSION_CHILD_DISCOVERED -> logical = "child|" + firstText(payload, "sessionId", "changedSessionId")
                    .orElse(session);
            case SESSION_SCOPE_UPDATED -> logical = "scope|" + session;
            case RUN_CREATED, RUN_STARTED, RUN_CANCELLING, RUN_SUCCEEDED, RUN_FAILED, RUN_CANCELLED -> {
                logical = "run-status";
                latest = true;
            }
            case SESSION_STATUS -> logical = "session-status|" + session;
            default -> logical = "latest|" + session + '|' + draft.type().wireName();
        }
        return new SnapshotProjection(
                projectionKey == null ? snapshotKey(logical) : projectionKey,
                mode,
                cleanup,
                latest);
    }

    private AttentionMutation attentionMutation(RunEventDraft draft) {
        return switch (draft.type()) {
            case QUESTION_ASKED -> new AttentionMutation("QUESTION", requestId(draft.payload()), false);
            case PERMISSION_ASKED -> new AttentionMutation("PERMISSION", requestId(draft.payload()), false);
            case QUESTION_REPLIED, QUESTION_REJECTED, PERMISSION_REPLIED ->
                    new AttentionMutation(null, requestId(draft.payload()), true);
            default -> new AttentionMutation(null, null, false);
        };
    }

    private Optional<RunStatus> statusFrom(RunEventType type) {
        return switch (type) {
            case RUN_CREATED -> Optional.of(RunStatus.PENDING);
            case RUN_STARTED -> Optional.of(RunStatus.RUNNING);
            case RUN_CANCELLING -> Optional.of(RunStatus.CANCELLING);
            case RUN_SUCCEEDED -> Optional.of(RunStatus.SUCCEEDED);
            case RUN_FAILED -> Optional.of(RunStatus.FAILED);
            case RUN_CANCELLED -> Optional.of(RunStatus.CANCELLED);
            default -> Optional.empty();
        };
    }

    private String requestId(Map<String, Object> payload) {
        return firstText(payload, "requestId", "id", "permissionId", "questionId")
                .orElse("current");
    }

    private Optional<String> firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    private Optional<String> nestedText(Map<String, Object> source, String nestedKey, String... keys) {
        Object nested = source.get(nestedKey);
        if (!(nested instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    private String snapshotKey(String logical) {
        int separator = logical.indexOf('|');
        String category = separator < 0 ? logical : logical.substring(0, separator);
        return "p:" + safe(category) + ':' + digest(logical);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private String text(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Run manifest field missing: " + key);
        }
        return value.toString();
    }

    private String optionalText(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private Instant optionalInstant(Map<Object, Object> fields, String key) {
        String value = optionalText(fields, key);
        return value == null ? null : Instant.parse(value);
    }

    private long number(Map<Object, Object> fields, String key) {
        return Long.parseLong(text(fields, key));
    }

    private int integer(Object value) {
        return value == null ? 0 : Integer.parseInt(value.toString());
    }

    private PlatformException unavailable(Throwable cause) {
        return new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run Redis 运行态不可用", Map.of(), cause);
    }

    private String runPrefix(RunId runId) { return PREFIX + "{" + runId.value() + "}:"; }
    private String manifestKey(RunId runId) { return runPrefix(runId) + "manifest"; }
    private String inputKey(RunId runId) { return runPrefix(runId) + "input"; }
    private String streamKey(RunId runId) { return runPrefix(runId) + "events"; }
    private String runtimeStreamKey(RunId runId) { return runPrefix(runId) + "runtime-events"; }
    private String snapshotKey(RunId runId) { return runPrefix(runId) + "snapshot"; }
    private String snapshotOrderKey(RunId runId) { return runPrefix(runId) + "snapshot:order"; }
    private String registryKey(RunId runId) { return runPrefix(runId) + "keys"; }
    private String scopeKey(RunId runId) { return runPrefix(runId) + "scope"; }
    private String scopeSessionsKey(RunId runId) { return runPrefix(runId) + "scope:sessions"; }
    private String scopeVersionKey(RunId runId) { return runPrefix(runId) + "scope:version"; }
    private String scopeSessionKey(RunId runId, String sessionId) { return runPrefix(runId) + "scope:session:" + sessionId; }
    private String dedupKey(RunId runId) { return runPrefix(runId) + "dedup"; }
    private String pendingKey(RunId runId, String sessionId) { return runPrefix(runId) + "pending:" + safe(sessionId); }
    private String activeUserKey(UserId userId) { return PREFIX + "active:user:{" + userId.value() + "}"; }
    private String userRuntimeMarkerKey(UserId userId) { return PREFIX + "runtime-user:{" + userId.value() + "}"; }
    private String activeSessionKey(SessionId sessionId) { return PREFIX + "active:session:{" + sessionId.value() + "}"; }
    private String historySessionKey(SessionId sessionId) { return PREFIX + "history:session:{" + sessionId.value() + "}"; }
    private String clientRequestKey(SessionId sessionId, String clientRequestId) {
        return PREFIX + "request:{" + sessionId.value() + "}:" + digest(clientRequestId);
    }
    private String activeServerKey(String serverId) { return PREFIX + "active:server:{" + safe(serverId) + "}"; }
    private String safe(String value) { return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_.-]", "_"); }

    private static Duration positive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private record SnapshotProjection(String key, String mode, String cleanupKey, boolean latestOrder) { }
    private record AttentionMutation(String value, String eventId, boolean clear) { }
    private record AppendScriptResult(
            long seq,
            long runtimeVersion,
            int ignored,
            int truncated,
            long resetGeneration,
            long earliestSeq,
            long earliestRuntimeVersion) { }
    private record ProjectScriptResult(
            long runtimeVersion,
            int ignored,
            int truncated,
            long resetGeneration,
            long earliestRuntimeVersion) { }
    private record ReplayEventRow(String id, String draft) { }
    private record ReplayScriptResult(
            long barrierSeq,
            long earliestSeq,
            long runtimeVersion,
            long resetGeneration,
            boolean reset,
            List<String> snapshot,
            List<ReplayEventRow> events) {
        private ReplayScriptResult {
            snapshot = snapshot == null ? List.of() : List.copyOf(snapshot);
            events = events == null ? List.of() : List.copyOf(events);
        }
    }
    private record TailEventRow(long runtimeVersion, boolean durable, long seq, String draft) { }
    private record TailScriptResult(
            long barrierSeq,
            long currentRuntimeVersion,
            long resetGeneration,
            boolean reset,
            List<String> snapshot,
            List<TailEventRow> events) {
        private TailScriptResult {
            snapshot = snapshot == null ? List.of() : List.copyOf(snapshot);
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    private static final class SnapshotBudget {

        private int remaining;

        private SnapshotBudget(int remaining) {
            this.remaining = remaining;
        }

        private int remaining() {
            return remaining;
        }

        private void consume(int amount) {
            remaining = Math.max(0, remaining - Math.max(0, amount));
        }
    }
}
