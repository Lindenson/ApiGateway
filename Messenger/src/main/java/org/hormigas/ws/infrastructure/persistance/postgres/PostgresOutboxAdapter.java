package org.hormigas.ws.infrastructure.persistance.postgres;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.stage.StageStatus;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.HistoryRow;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxMessage;
import org.hormigas.ws.infrastructure.persistance.postgres.mappers.MessageMapper;
import org.hormigas.ws.ports.outbox.OutboxManager;

import java.util.List;
import java.util.function.Predicate;

import static org.hormigas.ws.domain.stage.StageStatus.FAILED;

@ApplicationScoped
@IfBuildProperty(name = "processing.messages.storage.service", stringValue = "redis")
public class PostgresOutboxAdapter implements OutboxManager<Message> {

    @Inject
    OutboxRepository repo;

    @Inject
    MessageMapper mapper;

    @Override
    public Uni<StageStatus> save(Message message) {
        if (message == null) return Uni.createFrom().item(FAILED);

        OutboxMessage outboxMessage = mapper.toOutboxMessage(message);
        HistoryRow historyRow = mapper.toHistoryRow(message);

        return repo.insertHistoryAndOutboxTransactional(
                        List.of(historyRow),
                        List.of(outboxMessage)
                )
                .onItem().transform(ids -> StageStatus.SUCCESS)
                .replaceIfNullWith(StageStatus.FAILED)
                .onFailure().recoverWithItem(StageStatus.FAILED);
        //toDo  ERROR STRATEGY!
    }

    @Override
    public Uni<StageStatus> remove(Message message) {
        if (message == null || message.getCorrelationId() == null) return Uni.createFrom().item(FAILED);

        return repo.deleteProcessedByIds(List.of(message.getCorrelationId()))
                .onItem().transform(rows -> StageStatus.SUCCESS)
                .replaceIfNullWith(StageStatus.FAILED)
                .onFailure().recoverWithItem(StageStatus.FAILED);
    }

    @Override
    public Uni<Message> fetch() {
        return repo.fetchBatchForProcessing(1, java.time.Duration.ofSeconds(5))
                .onItem().transform(batch -> {
                    if (batch.isEmpty()) return null;
                    return mapper.toDomainMessage(batch.getFirst());
                });
    }

    @Override
    public Uni<List<Message>> fetchBatch(int batchSize) {
        return repo.fetchBatchForProcessing(batchSize, java.time.Duration.ofSeconds(5))
                .onItem().transform(batch -> batch.stream()
                        .map(mapper::toDomainMessage)
                        .toList())
                .replaceIfNullWith(List.of());
    }

    @Override
    public Uni<Long> collectGarbage(Predicate<Message> filter) {
        return Uni.createFrom().item(0L);
    }
}
