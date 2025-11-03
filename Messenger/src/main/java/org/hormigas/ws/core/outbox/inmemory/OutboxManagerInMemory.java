package org.hormigas.ws.core.outbox.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.hormigas.ws.core.router.stage.StageStatus.*;


@Slf4j
public class OutboxManagerInMemory implements OutboxManager<Message> {

    private final ConcurrentMap<String, Message> messages = new ConcurrentHashMap<>();

    @Override
    public Uni<StageStatus> saveToOutbox(@Nullable Message message) {
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(FAILED);

        log.debug("Saving message {}", message);
        return Uni.createFrom().item(messages.put(message.getMessageId(), message))
                .onItem().transform(it -> it == null? SUCCESS : SKIPPED);
    }

    @Override
    public Uni<StageStatus> removeFromOutbox(@Nullable Message message) {
        log.error("OUTBOX SIZE BEFORE: {}", messages.size());
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(FAILED);

        log.debug("Removing message {}", message);
        var replacedWith = Uni.createFrom().item(messages.remove(message.getMessageId()))
                .onItem().transform(it -> it != null? SUCCESS : SKIPPED);

        log.error("OUTBOX SIZE AFTER: {}", messages.size());
        return replacedWith;
    }

    @Override
    public Uni<Message> getFromOutbox() {
        return Uni.createFrom().optional(() ->
                messages.values().stream().findFirst()
        );
    }

    public Uni<List<Message>> getFromOutboxBatch(int batchSize) {
        return Uni.createFrom().item(() ->
                        messages.values().stream()
                                .limit(batchSize)
                                .toList()
                ).onItem().ifNotNull()
                .transformToUni(list ->
                        list.isEmpty()
                                ? Uni.createFrom().nothing()
                                : Uni.createFrom().item(list)
                );
    }
}
