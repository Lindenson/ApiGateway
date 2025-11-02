package org.hormigas.ws.core.outbox.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.domain.Message;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Slf4j
public class OutboxManagerInMemory implements OutboxManager<Message> {

    private final ConcurrentMap<String, Message> messages = new ConcurrentHashMap<>();

    @Override
    public Uni<Message> saveToOutbox(@Nullable Message message) {
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(message);
        return Uni.createFrom().item(messages.put(message.getMessageId(), message))
                .replaceWith(Uni.createFrom().item(message));
    }

    @Override
    public Uni<Message> removeFromOutbox(@Nullable Message message) {
        log.error("OUTBOX SIZE BEFORE: {}", messages.size());
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(message);
        Uni<Message> replacedWith = Uni.createFrom().item(messages.remove(message.getMessageId()))
                .replaceWith(message);
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
