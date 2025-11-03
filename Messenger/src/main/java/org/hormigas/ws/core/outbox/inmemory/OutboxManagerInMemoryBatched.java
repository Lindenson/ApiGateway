package org.hormigas.ws.core.outbox.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;

import java.util.List;

@Slf4j
@ApplicationScoped
public class OutboxManagerInMemoryBatched implements OutboxManager<Message> {

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
}
