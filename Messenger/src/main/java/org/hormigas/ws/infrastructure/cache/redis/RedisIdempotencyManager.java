package org.hormigas.ws.infrastructure.cache.redis;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.stage.StageStatus;
import org.hormigas.ws.ports.idempotency.IdempotencyManager;

import java.time.Instant;

@Slf4j
@ApplicationScoped
@IfBuildProperty(name = "processing.messages.storage.service", stringValue = "redis")
public class RedisIdempotencyManager implements IdempotencyManager<Message> {

    @Inject
    ReactiveRedisDataSource dataSource;

    private ReactiveValueCommands<String, String> value;
    private ReactiveKeyCommands<String> keyCommands;
    private static final int TTL_SECONDS = 3;

    @PostConstruct
    void init() {
        value = dataSource.value(String.class);
        keyCommands = dataSource.key();
    }

    private String messageKey(Message message) {
        return message.getMessageId();
    }

    @Override
    public Uni<StageStatus> addMessage(Message message) {
        log.debug("Adding message {}", message);
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        return value.setex(messageKey(message), TTL_SECONDS, timestamp)
                .map(ignored -> StageStatus.SUCCESS)
                .onFailure().invoke(ignored -> log.error("Error while adding message {}", message))
                .onFailure().recoverWithItem(StageStatus.FAILED);
    }

    @Override
    public Uni<StageStatus> removeMessage(Message message) {
        log.debug("Removing message {}", message);
        return keyCommands.del(messageKey(message))
                .map(count -> count > 0 ? StageStatus.SUCCESS : StageStatus.SKIPPED)
                .onFailure().invoke(ignored -> log.error("Error while removing message {}", message))
                .onFailure().recoverWithItem(StageStatus.FAILED);
    }

    @Override
    public Uni<Boolean> inProgress(Message message) {
        log.debug("Checking message {}", message);
        return keyCommands.exists(messageKey(message)).onItem().invoke(exists -> {
           if (exists) log.debug("Message {} delivery duplication", messageKey(message));
        }).onFailure().invoke(ignored -> log.error("Error while checking progress message {}", message))
        .onFailure().recoverWithItem(Boolean.FALSE);
    }
}
