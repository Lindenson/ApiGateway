package org.hormigas.ws.infrastructure.persistance.postgres;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.HistoryRow;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.Inserted;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxMessage;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxRow;

import java.time.Duration;
import java.util.List;

public interface PostgresOutbox {
    Uni<List<Inserted>> insertBatch(List<OutboxMessage> batch);
    Uni<List<OutboxRow>> fetchBatchForProcessing(int batchSize, Duration leaseDuration);
    Uni<Integer> deleteProcessedByIds(List<String> ids);
    Uni<List<Inserted>> insertHistoryAndOutboxTransactional(List<HistoryRow> historyRows, List<OutboxMessage> outboxBatch);
}
