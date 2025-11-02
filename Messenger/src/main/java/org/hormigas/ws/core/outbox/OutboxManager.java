package org.hormigas.ws.core.outbox;

import io.smallrye.mutiny.Uni;

import java.util.List;

public interface OutboxManager<T> {
    Uni<T> saveToOutbox(T payload);
    Uni<T> removeFromOutbox(T message);
    Uni<T> getFromOutbox();
    Uni<List<T>> getFromOutboxBatch(int batchSize);
}
