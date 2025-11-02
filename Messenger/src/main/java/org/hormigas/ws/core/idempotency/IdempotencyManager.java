package org.hormigas.ws.core.idempotency;

import io.smallrye.mutiny.Uni;

public interface IdempotencyManager<T> {
    Uni<T> addMessage(T id);
    Uni<T> removeMessage(T id);
    Uni<Boolean> inProgress(T id);
}
