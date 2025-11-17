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
    public Uni<StageStatus> save(Message message) {
        messageHistory.addBySenderId(message.getRecipientId(), message);
        return delegate.save(message);
    }

    @Override
    public Uni<StageStatus> remove(Message message) {
        return Uni.createFrom().item(batchBuffer.add(message));
    }

    @Override
    public Uni<Message> fetch() {
        return delegate.fetch();
    }

    @Override
    public Uni<List<Message>> fetchBatch(int batchSize) {
        return delegate.fetchBatch(batchSize);
    }

    @Override
    public Uni<Long> collectGarbage(Predicate<Message> predicate) {
        return delegate.collectGarbage(predicate);
    }
}
