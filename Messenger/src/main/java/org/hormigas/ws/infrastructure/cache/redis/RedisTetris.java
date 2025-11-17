

package org.hormigas.ws.infrastructure.cache.redis;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.RedisAPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.ports.tetris.Tetris;

import java.util.List;
import java.util.UUID;

/**
 * Redis-backed implementation of Tetris pattern.
 * <p>
 * Keys used (for recipient UUID = {id}):
 * - tetris:recipient:{id}                    -> HASH with fields: highestSentId, nextExpectedId, disconnectCutoffId
 * - tetris:recipient:{id}:acks               -> ZSET of acked message ids (score = id, member = id)
 * - tetris:message:id:counter                -> STRING counter for generating monotonic message ids
 */
@ApplicationScoped
public class RedisTetris implements Tetris {


    @Inject
    RedisAPI lowLevelClient;


    private static final String RECIPIENT_KEY_PREFIX = "tetris:recipient:"; // + {uuid}
    private static final String ACKS_SUFFIX = ":acks";


    /**
     * Register that a message with given ID was sent to a recipient.
     * Ensures highestSentId and nextExpectedId are initialized atomically.
     */
    public Uni<Void> onSent(UUID recipientId, long messageId) {
        final String key = recipientKey(recipientId);

        final String SCRIPT = """
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

        List<String> args = List.of(SCRIPT, "0", key, String.valueOf(messageId));

        return lowLevelClient
                .eval(args)
                .replaceWithVoid();
    }

    /**
     * Register ack from recipient.
     * <p>
     * Behavior:
     * - if nextExpectedId == -1 (no sends recorded) -> ignore ack
     * - if ack < nextExpectedId -> ignore (already covered)
     * - otherwise: ZADD to ack set, and advance nextExpectedId while possible:
     * * while nextExpectedId <= disconnectCutoffId: remove ack entry (if any) and increment
     * * else if ZSCORE(ackSet, nextExpectedId) exists: ZREM + increment
     * * else break
     */
    public Uni<Void> onAck(UUID recipientId, long messageId) {
        final String recipientKey = recipientKey(recipientId);
        final String ackKey = recipientKey + ACKS_SUFFIX;

        final String SCRIPT = """
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

    /**
     * Mark recipient disconnected -> set disconnectCutoffId = highestSentId (if higher than current cutoff)
     * and advance nextExpectedId accordingly (cleanup acks <= cutoff).
     */
    public Uni<Void> onDisconnect(UUID recipientId) {
        final String recipientKey = recipientKey(recipientId);
        final String ackKey = recipientKey + ACKS_SUFFIX;

        final String SCRIPT = """
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

    /**
     * Compute global safe delete ID = min(nextExpectedId - 1) across provided recipientIds.
     * If a recipient has nextExpectedId == -1 (no messages ever sent to them), that recipient
     * does not limit GC (ignored).
     * If no recipient provides a limit, returns 0.
     */
    public Uni<Long> computeGlobalSafeDeleteId() {

        final String SCRIPT = """          
                local pattern = ARGV[1]
                local count = tonumber(ARGV[2]) or 100
                local cursor = "0"
                local global_min = nil
                
                repeat
                    local res = redis.call("SCAN", cursor, "MATCH", pattern, "COUNT", count)
                    cursor = res[1]
                    local keys = res[2]
                    for i, key in ipairs(keys) do
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

        List<String> args = List.of(SCRIPT, "0", "tetris:recipient:*", "100");

        return lowLevelClient.eval(args)
                .map(result -> {
                    if (result == null) return 0L;
                    try {
                        return result.toLong();
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                });
    }



    private String recipientKey(UUID id) {
        return RECIPIENT_KEY_PREFIX + id;
    }
}

