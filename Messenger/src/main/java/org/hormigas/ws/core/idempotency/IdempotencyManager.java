package org.hormigas.ws.core.idempotency;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.router.stage.StageStatus;

public interface IdempotencyManager<T> {
    Uni<StageStatus> addMessage(T id);
    Uni<StageStatus> removeMessage(T id);
    Uni<Boolean> inProgress(T id);
}
