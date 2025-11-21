package org.hormigas.ws.infrastructure.cache.redis;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.RedisAPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.stage.StageResult;
import org.hormigas.ws.ports.tetris.TetrisMarker;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
@IfBuildProperty(name = "processing.messages.storage.service", stringValue = "redis")
public class RedisTetrisMarker implements TetrisMarker<Message> {

    private static final String RECIPIENT_KEY_PREFIX = "tetris:re:";
    private static final String ACKS_SUFFIX = ":ack";
    private static final String GLOBAL_MIN_KEY = "tetris:minids";
    private static final String COUNTS_KEY = "tetris:re:cnt";

    @Inject
    RedisAPI redis;

    // ----------------------------
    // onSent: add messageId to per-client ZSET + increment counter
    // ----------------------------
    @Override
    public Uni<StageResult<Message>> onSent(@NotNull Message message) {
        String recipientId = message.getRecipientId();
        long id = message.getId();
        String perKey = recipientKey(recipientId) + ACKS_SUFFIX;
        String globalKey = GLOBAL_MIN_KEY;

        String lua = """
                local per = ARGV[1]
                local gmin = ARGV[2]
                local counts = ARGV[3]
                local msg = tonumber(ARGV[4])
                local client = ARGV[5]
                
                -- Add to per-client ZSET
                redis.call('ZADD', per, msg, tostring(msg))
                
                -- Increment unacked counter
                redis.call('HINCRBY', counts, client, 1)
                
                -- Update global min
                local minMember = redis.call('ZRANGE', per, 0, 0)
                if minMember[1] then
                    local safe = tonumber(minMember[1])
                    if safe ~= 0 then
                        redis.call('ZADD', gmin, safe, client)
                    else
                        redis.call('ZREM', gmin, client)
                    end
                else
                    redis.call('ZREM', gmin, client)
                end
                return 1
                """;

        List<String> args = List.of(lua, "0", perKey, globalKey, COUNTS_KEY,
                String.valueOf(id), recipientId);
        return redis.eval(args)
                .replaceWith(StageResult.<Message>passed())
                .onFailure().invoke(err -> log.error("onSent failed recipient={} msg={} : {}", recipientId, id, err.getMessage(), err))
                .onFailure().recoverWithItem(StageResult.failed());
    }

    // ----------------------------
    // onAck: remove messageId + decrement counter
    // ----------------------------
    @Override
    public Uni<StageResult<Message>> onAck(@NotNull Message message) {
        String recipientId = message.getSenderId();
        long id = message.getAckId();
        String perKey = recipientKey(recipientId) + ACKS_SUFFIX;
        String globalKey = GLOBAL_MIN_KEY;
        log.error("DENYS! onAck recipient {} ack {}", recipientId, id);

        String lua = """
                local per = ARGV[1]
                local gmin = ARGV[2]
                local counts = ARGV[3]
                local msg = tostring(ARGV[4])
                local client = ARGV[5]
                
                -- Remove acknowledged message
                redis.call('ZREM', per, msg)
                
                -- Decrement unacked counter (never below 0)
                local cnt = redis.call('HINCRBY', counts, client, -1)
                if cnt < 0 then redis.call('HSET', counts, client, 0) end
                
                -- Update global min
                local minMember = redis.call('ZRANGE', per, 0, 0)
                if minMember[1] then
                    local safe = tonumber(minMember[1])
                    if safe ~= 0 then
                        redis.call('ZADD', gmin, safe, client)
                    else
                        redis.call('ZREM', gmin, client)
                    end
                else
                    redis.call('ZREM', gmin, client)
                end
                return 1
                """;

        List<String> args = List.of(lua, "0", perKey, globalKey, COUNTS_KEY,
                String.valueOf(id), recipientId);
        return redis.eval(args)
                .replaceWith(StageResult.<Message>passed())
                .onFailure().invoke(err -> log.error("onAck failed recipient={} msg={} : {}", recipientId, id, err.getMessage(), err))
                .onFailure().recoverWithItem(StageResult.failed());
    }

    // ----------------------------
    // onDisconnect: keep only MAX messageId, reset counter to 1
    // ----------------------------
    @Override
    public Uni<StageResult<Message>> onDisconnect(@NotNull String recipientId) {
        String perKey = recipientKey(recipientId) + ACKS_SUFFIX;
        String globalKey = GLOBAL_MIN_KEY;

        String lua = """
                local per = ARGV[1]
                local gmin = ARGV[2]
                local counts = ARGV[3]
                local client = ARGV[4]
                
                local maxMember = redis.call('ZREVRANGE', per, 0, 0)
                if maxMember[1] then
                    local maxNum = tonumber(maxMember[1])
                    -- Keep only MAX
                    redis.call('ZREMRANGEBYRANK', per, 0, -2)
                    -- Update counter to 1 if maxNum !=0
                    if maxNum ~= 0 then
                        redis.call('HSET', counts, client, 1)
                        redis.call('ZADD', gmin, maxNum, client)
                    else
                        redis.call('HSET', counts, client, 0)
                        redis.call('ZREM', gmin, client)
                    end
                else
                    redis.call('HSET', counts, client, 0)
                    redis.call('ZREM', gmin, client)
                end
                return 1
                """;

        List<String> args = List.of(lua, "0", perKey, globalKey, COUNTS_KEY, recipientId);
        return redis.eval(args)
                .replaceWith(StageResult.<Message>passed())
                .onFailure().invoke(err -> log.error("onDisconnect failed recipient={} : {}", recipientId, err.getMessage(), err))
                .onFailure().recoverWithItem(StageResult.failed());
    }

    // ----------------------------
    // computeGlobalSafeDeleteId: as before
    // ----------------------------
    @Override
    public Uni<Long> computeGlobalSafeDeleteId() {
        String lua = """
                local gmin = ARGV[1]
                local vals = redis.call('ZRANGE', gmin, 0, 0, 'WITHSCORES')
                if not vals or #vals == 0 then return -1 end
                local score = tonumber(vals[2])
                if score == 0 then return -1 end
                return score
                """;

        List<String> args = List.of(lua, "0", GLOBAL_MIN_KEY);
        return redis.eval(args)
                .map(resp -> {
                    try {
                        if (resp == null) return -1L;
                        return Long.parseLong(resp.toString());
                    } catch (Exception e) {
                        log.warn("computeGlobalSafeDeleteId parse error, returning -1", e);
                        return -1L;
                    }
                });
    }

    // ----------------------------
    // findHeavyClients: fast, via hash of counters
    // ----------------------------
    @Override
    public Uni<List<String>> findHeavyClients(int threshold, int limit) {
        return redis.hgetall(COUNTS_KEY)
                .map(resp -> {
                    List<String> heavy = new ArrayList<>();
                    if (resp != null && !resp.getKeys().isEmpty()) {
                        for (String key : resp.getKeys()) {
                            long cnt = Long.parseLong(resp.get(key).toString());
                            if (cnt >= threshold) {
                                heavy.add(key);
                                if (heavy.size() >= limit) break;
                            }
                        }
                    }
                    return heavy;
                });
    }

    // ----------------------------
    // Helper
    // ----------------------------
    private String recipientKey(String id) {
        return RECIPIENT_KEY_PREFIX + id;
    }
}
