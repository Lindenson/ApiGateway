package org.hormigas.ws.infrastructure.cache.redis;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.RedisAPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.ports.tetris.TetrisMarker;

import java.util.List;

/**
 * Redis-backed implementation of Tetris pattern.
 * <p>
 * Keys used (for recipient UUID = {id}):
 * - tetris:recipient:{id}    -> HASH with fields: highestSentId, nextExpectedId, disconnectCutoffId
 * - tetris:recipient:{id}:acks-> ZSET of acked message ids (score = id, member = id)
 * - tetris:message:id:counter-> STRING counter for generating monotonic message ids
 */
@Slf4j
@ApplicationScoped
@IfBuildProperty(name = "processing.messages.storage.service", stringValue = "redis")
public class RedisTetrisMarker implements TetrisMarker {

    private final static int BATCH_SIZE = 1000;

    @Inject
    RedisAPI lowLevelClient;

    private static final String RECIPIENT_KEY_PREFIX = "tetris:recipient:"; // + {uuid}
    private static final String ACKS_SUFFIX = ":acks";


    // ----------------------------
    // onSent
    // ----------------------------
    @Override
    public Uni<Void> onSent(@NotNull String recipientId, long messageId) {
        final String recipientKey = recipientKey(recipientId);
        final String SCRIPT = """
                -- ARGV[1] = key
                -- ARGV[2] = msgId
                local key = ARGV[1]
                local msgId = tonumber(ARGV[2])
                local highest = tonumber(redis.call('HGET', key, 'highestSentId') or '0')
                local nextExpected = tonumber(redis.call('HGET', key, 'nextExpectedId') or '-1')
                
                if msgId > highest then
                    highest = msgId
                end
                
                if nextExpected == -1 then
                    nextExpected = msgId
                end
                
                redis.call('HSET', key, 'highestSentId', highest, 'nextExpectedId', nextExpected)
                return nextExpected
                """;

        List<String> args = List.of(SCRIPT, "0", recipientKey, String.valueOf(messageId));
        return lowLevelClient.eval(args)
                .onFailure().invoke(er -> log.error("Error removing user {}", recipientKey, er))
                .replaceWithVoid();
    }

    // ----------------------------
    // onAck
    // ----------------------------
    @Override
    public Uni<Void> onAck(@NotNull String recipientId, long messageId) {
        final String recipientKey = recipientKey(recipientId);
        final String ackKey = recipientKey + ACKS_SUFFIX;
        final String SCRIPT = """
                -- ARGV[1] = key
                -- ARGV[2] = ackKey
                -- ARGV[3] = msgId
                local key = ARGV[1]
                local ackKey = ARGV[2]
                local msgId = tonumber(ARGV[3])
                
                local nextExpected = tonumber(redis.call('HGET', key, 'nextExpectedId') or '-1')
                local cutoff = tonumber(redis.call('HGET', key, 'disconnectCutoffId') or '0')
                
                -- if we never sent anything to this recipient, ignore the ack
                if nextExpected == -1 then
                    return -1
                end
                
                -- if ack is older than already advanced pointer, ignore
                if msgId < nextExpected then
                    return nextExpected
                end
                
                -- record ack
                redis.call('ZADD', ackKey, msgId, tostring(msgId))
                
                -- advance pointer while possible
                while true do
                    if nextExpected <= cutoff then
                        -- remove potential ack for that id (cleanup) and advance
                        redis.call('ZREM', ackKey, tostring(nextExpected))
                        nextExpected = nextExpected + 1
                    else
                        local found = redis.call('ZSCORE', ackKey, tostring(nextExpected))
                        if found then
                            redis.call('ZREM', ackKey, tostring(nextExpected))
                            nextExpected = nextExpected + 1
                        else
                            break
                        end
                    end
                end
                
                redis.call('HSET', key, 'nextExpectedId', nextExpected)
                return nextExpected
                """;

        List<String> args = List.of(SCRIPT, "0", recipientKey, ackKey, String.valueOf(messageId));
        return lowLevelClient.eval(args).replaceWithVoid();
    }

    // ----------------------------
    // onDisconnect
    // ----------------------------
    @Override
    public Uni<Void> onDisconnect(String recipientId) {
        final String recipientKey = recipientKey(recipientId);
        final String ackKey = recipientKey + ACKS_SUFFIX;
        final String SCRIPT = """
                -- ARGV[1] = key
                -- ARGV[2] = ackKey
                local key = ARGV[1]
                local ackKey = ARGV[2]
                
                local highest = tonumber(redis.call('HGET', key, 'highestSentId') or '0')
                local cutoff = tonumber(redis.call('HGET', key, 'disconnectCutoffId') or '0')
                local nextExpected = tonumber(redis.call('HGET', key, 'nextExpectedId') or '-1')
                
                if highest > cutoff then
                    cutoff = highest
                    redis.call('HSET', key, 'disconnectCutoffId', cutoff)
                end
                
                if nextExpected == -1 then
                    -- nothing sent previously; just store cutoff (done above)
                    return cutoff
                end
                
                -- advance while nextExpected <= cutoff
                while nextExpected <= cutoff do
                    redis.call('ZREM', ackKey, tostring(nextExpected))
                    nextExpected = nextExpected + 1
                end
                
                redis.call('HSET', key, 'nextExpectedId', nextExpected)
                return nextExpected
                """;

        List<String> args = List.of(SCRIPT, "0", recipientKey, ackKey);
        return lowLevelClient.eval(args).replaceWithVoid();
    }

    // ----------------------------
    // computeGlobalSafeDeleteId
    // ----------------------------

    /**
     * Scan pattern tetris:recipient:* and compute min(nextExpectedId - 1).
     * Returns 0 if none found.
     */
    @Override
    public Uni<Long> computeGlobalSafeDeleteId() {
        final String SCRIPT = """
                -- ARGV[1] = pattern
                -- ARGV[2] = count (optional)
                local pattern = ARGV[1]
                local count = tonumber(ARGV[2]) or 100
                local cursor = "0"
                local global_min = nil
                
                repeat
                    local res = redis.call("SCAN", cursor, "MATCH", pattern, "COUNT", count)
                    cursor = res[1]
                    local keys = res[2]
                    for i = 1, #keys do
                        local key = keys[i]
                        if redis.call("TYPE", key).ok == "hash" then
                            local nextExpected = tonumber(redis.call("HGET", key, "nextExpectedId") or "-1")
                            if nextExpected ~= -1 then
                                local safe = nextExpected - 1
                                if global_min == nil or safe < global_min then
                                    global_min = safe
                                end
                            end
                        end
                    end
                until cursor == "0"
                
                if global_min == nil then
                    return 0
                else
                    return global_min
                end
                """;

        List<String> args = List.of(SCRIPT, "0", "tetris:recipient:*", String.valueOf(BATCH_SIZE));
        return lowLevelClient.eval(args)
                .map(resp -> {
                    if (resp == null) return 0L;
                    try {
                        return resp.toLong();
                    } catch (Exception e) {
                        try {
                            return Long.parseLong(resp.toString());
                        } catch (Exception ex) {
                            return 0L;
                        }
                    }
                });
    }

    // ----------------------------
    // helpers
    // ----------------------------
    private String recipientKey(String id) {
        return RECIPIENT_KEY_PREFIX + id;
    }
}
