package org.hormigas.ws.core.outbox;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.router.stage.StageStatus;

import java.util.List;

public interface OutboxManager<T> {
    Uni<StageStatus> saveToOutbox(T payload);
    Uni<StageStatus> removeFromOutbox(T message);
    Uni<T> getFromOutbox();
    Uni<List<T>> getFromOutboxBatch(int batchSize);
}
