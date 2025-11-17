package org.hormigas.ws.ports.idempotency;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.stage.StageStatus;

public interface IdempotencyManager<T> {
    Uni<StageStatus> add(T id);
    Uni<StageStatus> remove(T id);
    Uni<Boolean> isInProgress(T id);
}
