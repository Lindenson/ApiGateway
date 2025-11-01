package org.hormigas.ws.core.idempotency;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.MessagePayload;

import java.util.Map;

public class IdempotencyManagerInMemory implements IdempotencyManager {

    Map<String, MessagePayload> messages;

    @Override
    public Uni<MessagePayload> addMessage(MessagePayload payload) {
        return Uni.createFrom().item(messages.put(payload.id(), payload));
    }

    @Override
    public Uni<MessagePayload> removeMessage(MessagePayload payload) {
        return Uni.createFrom().item(messages.remove(payload.id()));
    }

    @Override
    public Uni<Boolean> inProgress(MessagePayload payload) {
        return Uni.createFrom().item(messages.containsKey(payload.id()));
    }
}
