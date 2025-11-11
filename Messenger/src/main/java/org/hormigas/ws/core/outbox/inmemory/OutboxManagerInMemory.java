package org.hormigas.ws.core.outbox.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;

import java.util.List;
import java.util.function.Predicate;

import static org.hormigas.ws.core.router.stage.StageStatus.*;


@Slf4j
public class OutboxManagerInMemory implements OutboxManager<Message> {


    private final TimeOrderedStringKeyMap<Message> messages = new TimeOrderedStringKeyMap<>(Message::getClientTimestamp);
    private final int MAX_OUTBOX_SIZE = 5000;


    @Override
    public Uni<StageStatus> saveToOutbox(@Nullable Message message) {
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(FAILED);

        if (messages.size() > MAX_OUTBOX_SIZE) log.warn("TOO MUCH OUTBOX SIZE: {}", messages.size());
        log.debug("Saving message {}", message);
        return Uni.createFrom().item(messages.putIfAbsent(message.getMessageId(), message))
                .onItem().transform(it -> it == null? SUCCESS : SKIPPED);
    }

    @Override
    public Uni<StageStatus> removeFromOutbox(@Nullable Message message) {
        if (message == null || message.getCorrelationId() == null) return Uni.createFrom().item(FAILED);

        log.debug("Removing message {}", message);
        return Uni.createFrom().item(messages.remove(message.getCorrelationId()))
                .onItem().transform(it -> it != null? SUCCESS : SKIPPED);
    }

    @Override
    public Uni<Message> getFromOutbox() {
        Message first = messages.peekFirst();
        return first != null
                ? Uni.createFrom().item(first)
                : Uni.createFrom().nothing();
    }


    @Override
    public Uni<List<Message>> getFromOutboxBatch(int batchSize) {
        List<Message> batch = messages.peekFirstN(batchSize);

        log.debug("Batched {} messages", batch.size());
        return batch.isEmpty()
                ? Uni.createFrom().nothing()
                : Uni.createFrom().item(batch);
    }


    @Override
    public Uni<Long> collectGarbage(Predicate<Message> predicate) {
        return Uni.createFrom().item(() -> {
            long collected = messages.collectGarbageOptimized(predicate);
            log.debug("Garbage collected {}", collected);
            return collected;
        });
    }
}
