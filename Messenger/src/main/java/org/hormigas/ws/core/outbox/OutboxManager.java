package org.hormigas.ws.core.outbox;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;

import java.util.List;
import java.util.function.Predicate;

public interface OutboxManager<T> {
    Uni<StageStatus> saveToOutbox(T payload);
    Uni<StageStatus> removeFromOutbox(T message);
    Uni<T> getFromOutbox();
    Uni<List<T>> getFromOutboxBatch(int batchSize);
    Uni<Long> collectGarbage(Predicate<T> filter);
}
