package org.hormigas.ws.ports.outbox;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.stage.StageStatus;

import java.util.List;
import java.util.function.Predicate;

public interface OutboxManager<T> {
    Uni<StageStatus> saveToOutbox(T payload);
    Uni<StageStatus> removeFromOutbox(T message);
    Uni<T> getFromOutbox();
    Uni<List<T>> getFromOutboxBatch(int batchSize);
    Uni<Long> collectGarbage(Predicate<T> filter);
}
