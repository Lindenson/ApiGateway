package org.hormigas.ws.core.services.outbox;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.domain.MessagePayload;

import java.util.List;

public interface OutboxManager {
    Uni<MessagePayload> saveToOutbox(MessagePayload payload);
    Uni<MessagePayload> removeFromOutbox(MessagePayload payload);
    Uni<MessagePayload> getFromOutbox();
    Uni<List<MessagePayload>> getFromOutboxBatch(int batchSize);
}
