package org.hormigas.ws.core.outbox.inmemory;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.infrastructure.MultiInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.outbox.OutboxButchManager;
import org.hormigas.ws.core.outbox.OutboxPublisher;
import org.hormigas.ws.domain.MessagePayload;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class BatchedOutboxInMemoryManager implements OutboxButchManager {

    private final OutboxManagerInMemory delegate;
    private final OutboxPublisher outboxPublisher;

    @Override
    public Uni<MessagePayload> saveToOutbox(MessagePayload payload) {
        return delegate.saveToOutbox( payload);
    }

    @Override
    public Uni<MessagePayload> removeFromOutbox(MessagePayload message) {
        Supplier<MessagePayload> payloadSupplier = () -> {
            outboxPublisher.publish(message);
            return message;
        };
        return Uni.createFrom().item(payloadSupplier);
    }

    @Override
    public Uni<MessagePayload> getFromOutbox() {
        return delegate.getFromOutbox();
    }

    @Override
    public Uni<List<MessagePayload>> getFromOutboxBatch(int batchSize) {
        return delegate.getFromOutboxBatch(batchSize);
    }

    @Override
    public Uni<Long> removeFromOutboxBatch(List<MessagePayload> messages) {
        log.error("Removing messages from outbox batch : {}", messages.size());
        return Multi.createFrom().iterable(messages)
                //.onItem().transformToUniAndMerge(delegate::removeFromOutbox)
                .filter(Objects::nonNull)
                .collect().asList()
                .onItem().transform(it -> (long ) it.size());
    }
}
