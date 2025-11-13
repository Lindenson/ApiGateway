package org.hormigas.ws.infrastructure.cache.redis;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.RedisAPI;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.session.ClientData;
import org.hormigas.ws.ports.presence.PresenceManager;

import java.util.List;

@Slf4j
@ApplicationScoped
@IfBuildProperty(name = "processing.messages.storage.service", stringValue = "redis")
public class RedisPresenceManager implements PresenceManager {

    private static String SCRIPT = "local key = ARGV[1] " +
            "local ts = tonumber(ARGV[2]) " +
            "local json = redis.call('GET', key) " +
            "if not json then return 0 end " +
            "local data = cjson.decode(json) " +
            "if data.connectedAt <= ts then " +
            "   return redis.call('DEL', key) " +
            "end " +
            "return 0";

    @Inject
    ReactiveRedisDataSource dataSource;

    @Inject
    RedisAPI lowLevelClient;

    private ReactiveValueCommands<String, String> value;
    private ReactiveKeyCommands<String> keyCommands;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        value = dataSource.value(String.class);
        keyCommands = dataSource.key();
    }

    private String clientKey(String userId) {
        return "client:" + userId;
    }

    @Override
    public Uni<Void> addClient(ClientData user) {
        try {
            String json = objectMapper.writeValueAsString(user);
            return value.set(clientKey(user.id()), json)
                    .onFailure().invoke(throwable -> {
                        log.error("Error adding user {}", user, throwable);
                    })
                    .onFailure().recoverWithNull()
                    .replaceWithVoid();
        } catch (JsonProcessingException e) {
            log.error("Error adding user {}", user, e);
            return Uni.createFrom().voidItem();
        }
    }

    public Uni<Void> removeClient(String clientId, long timestamp) {
        if (clientId == null) return Uni.createFrom().voidItem();

        List<String> args = List.of(SCRIPT, "0", clientKey(clientId), String.valueOf(timestamp));
        return lowLevelClient.eval(args).onItem()
                .invoke(it -> log.debug("Removed {} clients", it.toString()))
                .onFailure().invoke(throwable -> log.error("Error removing user {}", clientId, throwable))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    @Override
    public Uni<List<ClientData>> allPresent() {
        return keyCommands.keys("client:*")
                .flatMap(keys -> {
                    List<Uni<ClientData>> unis = keys.stream()
                            .map(k -> value.get(k)
                                    .map(v -> {
                                        try {
                                            return objectMapper.readValue(v, ClientData.class);
                                        } catch (JsonProcessingException e) {
                                            log.error("Error parsing json", e);
                                        }
                                        return null;
                                    })
                            ).toList();
                    return Uni.join().all(unis).andFailFast()
                            .onFailure().invoke(throwable -> {
                                log.error("Error getting presence", throwable);
                            }).onFailure().recoverWithItem(List.of());
                });
    }
}
