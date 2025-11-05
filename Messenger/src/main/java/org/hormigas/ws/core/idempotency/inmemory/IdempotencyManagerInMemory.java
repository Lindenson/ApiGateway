package org.hormigas.ws.core.idempotency.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.idempotency.IdempotencyManager;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.hormigas.ws.core.router.stage.StageStatus.*;


@Slf4j
@ApplicationScoped
public class IdempotencyManagerInMemory implements IdempotencyManager<Message> {

    private final Set<String> messages = ConcurrentHashMap.newKeySet();;

    @Override
    public Uni<StageStatus> addMessage(@Nullable Message message) {
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(FAILED);

        log.debug("Adding message {}", message);
        return Uni.createFrom().item(messages.add(message.getMessageId()))
                .onItem().transform(it -> it? SUCCESS : SKIPPED);

    }

    @Override
    public Uni<StageStatus> removeMessage(@Nullable Message message) {
        if (message == null || message.getCorrelationId() == null) return Uni.createFrom().item(FAILED);

        log.warn("IDEMP SIZE BEFORE: {}", messages.size());
        log.debug("Removing message {}", message);
        var removed = Uni.createFrom().item(messages.remove(message.getCorrelationId()))
                .onItem().transform(it -> it ? SUCCESS : SKIPPED);
        log.warn("IDEMP SIZE AFTER: {}", messages.size());
        return removed;
    }

    @Override
    public Uni<Boolean> inProgress(@Nullable Message message) {
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(Boolean.FALSE);
        return Uni.createFrom().item(messages.contains(message.getMessageId()))
                .invoke(it -> {
                    if (it) log.warn("Message: {} in progress", message.getMessageId());
                });
    }
}
