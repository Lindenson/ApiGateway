package org.hormigas.ws.infrastructure.persistance.postgres.mappers;

import jakarta.annotation.Nullable;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.HistoryRow;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxMessage;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxRow;

public interface OutboxMapper {
    OutboxMessage toOutboxMessage(Message msg);

    HistoryRow toHistoryRow(Message msg);

    Message toDomainMessage(OutboxRow outboxRow);

    @Nullable
    Message fromHistoryRow(@Nullable HistoryRow row);
}
