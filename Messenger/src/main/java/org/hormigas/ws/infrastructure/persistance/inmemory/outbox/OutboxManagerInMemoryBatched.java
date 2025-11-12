package org.hormigas.ws.infrastructure.persistance.inmemory.outbox;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.ports.history.History;
import org.hormigas.ws.ports.outbox.OutboxManager;
import org.hormigas.ws.domain.stage.StageStatus;
import org.hormigas.ws.domain.message.Message;

import java.util.List;
import java.util.function.Predicate;

@Slf4j
@ApplicationScoped
public class OutboxManagerInMemoryBatched implements OutboxManager<Message> {

    @Inject
    History<Message> messageHistory;

    @PostConstruct
    public void close() {
        if (batchBuffer != null) {
            batchBuffer.shutdown();
        }
    }

    OutboxManagerInMemory delegate = new OutboxManagerInMemory();
    OutboxBatchBuffer batchBuffer = new OutboxBatchBuffer(delegate);

    @Override
    public Uni<StageStatus> saveToOutbox(Message payload) {
        messageHistory.addMessage(payload.getRecipientId(), payload);
        return delegate.saveToOutbox(payload);
    }

    @Override
    public Uni<StageStatus> removeFromOutbox(Message message) {
        return Uni.createFrom().item(batchBuffer.add(message));
    }

    @Override
    public Uni<Message> getFromOutbox() {
        return delegate.getFromOutbox();
    }

    @Override
    public Uni<List<Message>> getFromOutboxBatch(int batchSize) {
        return delegate.getFromOutboxBatch(batchSize);
    }

    @Override
    public Uni<Long> collectGarbage(Predicate<Message> predicate) {
        return delegate.collectGarbage(predicate);
    }
}
