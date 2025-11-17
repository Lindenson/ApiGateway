package org.hormigas.ws.infrastructure.persistance.inmemory.outbox;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.ports.outbox.OutboxManager;
import org.hormigas.ws.domain.stage.StageStatus;
import org.hormigas.ws.domain.message.Message;

import java.util.List;
import java.util.function.Predicate;

import static org.hormigas.ws.domain.stage.StageStatus.*;


@Slf4j
public class OutboxManagerInMemory implements OutboxManager<Message> {


    private final TimeOrderedStringKeyMap<Message> messages = new TimeOrderedStringKeyMap<>(Message::getSenderTimestamp);
    private final int MAX_OUTBOX_SIZE = 5000;


    @Override
    public Uni<StageStatus> save(@Nullable Message message) {
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(FAILED);

        if (messages.size() > MAX_OUTBOX_SIZE) log.warn("TOO MUCH OUTBOX SIZE: {}", messages.size());
        log.debug("Saving message {}", message);
        return Uni.createFrom().item(messages.putIfAbsent(message.getMessageId(), message))
                .onItem().transform(it -> it == null? SUCCESS : SKIPPED);
    }

    @Override
    public Uni<StageStatus> remove(@Nullable Message message) {
        if (message == null || message.getCorrelationId() == null) return Uni.createFrom().item(FAILED);

        log.debug("Removing message {}", message);
        return Uni.createFrom().item(messages.remove(message.getCorrelationId()))
                .onItem().transform(it -> it != null? SUCCESS : SKIPPED);
    }

    @Override
    public Uni<Message> fetch() {
        Message first = messages.peekFirst();
        return first != null
                ? Uni.createFrom().item(first)
                : Uni.createFrom().nothing();
    }


    @Override
    public Uni<List<Message>> fetchBatch(int batchSize) {
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
