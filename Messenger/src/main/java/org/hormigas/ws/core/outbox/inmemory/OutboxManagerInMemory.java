package org.hormigas.ws.core.outbox;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.MessagePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OutboxManagerInMemory implements OutboxManager {

    private final Queue<MessagePayload> messages = new ConcurrentLinkedQueue<>();

    @Override
    public Uni<MessagePayload> saveToOutbox(MessagePayload payload) {
        messages.add(payload);
        return Uni.createFrom().item(payload);
    }

    @Override
    public Uni<MessagePayload> removeFromOutbox(MessagePayload payload) {
        messages.remove(payload);
        return Uni.createFrom().item(payload);
    }

    @Override
    public Uni<MessagePayload> getFromOutbox() {
        return Uni.createFrom().item(messages.poll()); // достаёт и удаляет первое сообщение
    }

    public Uni<List<MessagePayload>> getFromOutboxBatch(int batchSize) {
        List<MessagePayload> batch = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            MessagePayload msg = messages.poll(); // атомарно извлекает
            if (msg == null) break;
            batch.add(msg);
        }
        return Uni.createFrom().item(batch);
    }
}
