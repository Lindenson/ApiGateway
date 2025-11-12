package org.hormigas.ws.ports.idempotency;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.stage.StageStatus;

public interface IdempotencyManager<T> {
    Uni<StageStatus> addMessage(T id);
    Uni<StageStatus> removeMessage(T id);
    Uni<Boolean> inProgress(T id);
}
