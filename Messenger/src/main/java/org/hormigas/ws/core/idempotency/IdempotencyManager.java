package org.hormigas.ws.core.services.idempotency;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.domain.MessagePayload;

public interface IdempotencyManager {
    Uni<MessagePayload> addMessage(MessagePayload payload);
    Uni<MessagePayload> removeMessage(MessagePayload payload);
    Uni<Boolean> inProgress(MessagePayload payload);
}
