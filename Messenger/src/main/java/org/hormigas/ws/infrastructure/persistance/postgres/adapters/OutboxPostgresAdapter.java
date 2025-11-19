package org.hormigas.ws.infrastructure.persistance.postgres.adapters;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.stage.StageStatus;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.HistoryRow;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxMessage;
import org.hormigas.ws.infrastructure.persistance.postgres.mappers.MessageMapper;
import org.hormigas.ws.infrastructure.persistance.postgres.outbox.OutboxPostgresRepository;
import org.hormigas.ws.ports.outbox.OutboxManager;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Adapter layer between immutable domain {@link Message} and {@link OutboxPostgresRepository}.
 *
 * <p>
 * This class provides a safe, reactive bridge between domain messages and the
 * underlying PostgreSQL outbox repository. It maps messages to DTOs (OutboxMessage, HistoryRow)
 * via {@link MessageMapper} and delegates persistence and retrieval operations to the repository.
 * </p>
 *
 * <p><b>Key responsibilities and behavior:</b></p>
 * <ul>
 *     <li>Filters out invalid or incomplete domain messages before persistence to avoid null pointers
 *         or database constraint violations.</li>
 *     <li>Maps immutable {@link Message} objects to database DTOs. Mapper methods may return null
 *         if required fields are missing.</li>
 *     <li>All persistence operations return {@link StageStatus} or reactive empty results;
 *         this class never returns null.</li>
 *     <li>Reactive methods ensure safe handling of empty fetch results:
 *         <ul>
 *             <li>{@link #fetch()} returns {@code Uni<Message>} with {@code null} if no messages are available
 *                 or if mapped message is invalid.</li>
 *             <li>{@link #fetchBatch(int)} returns an empty list if no messages are available,
 *                 filtering out any null or invalid messages.</li>
 *         </ul>
 *     </li>
 *     <li>Write operations ({@link #save(Message)}, {@link #remove(Message)}) return {@link StageStatus#FAILED}
 *         if the message is null, required fields are missing, or the mapper returned null.</li>
 * </ul>
 *
 * <p><b>Minimal validation rules:</b></p>
 * <ul>
 *     <li>For {@link #save(Message)}: message must be non-null, have non-null senderId, recipientId, messageId, and payload.</li>
 *     <li>For {@link #remove(Message)}: message must be non-null and correlationId must be non-null.</li>
 * </ul>
 *
 * <p><b>Important notes and potential risks:</b></p>
 * <ul>
 *     <li>Mapper methods may return null if critical fields are missing. This class handles such cases
 *         by returning {@link StageStatus#FAILED} for write operations or null/empty for fetch operations.</li>
 *     <li>No exceptions are thrown by the mapper; null outputs are treated as invalid messages.</li>
 *     <li>Repository-level errors (SQL exceptions, connection issues) are captured reactively and
 *         mapped to {@link StageStatus#FAILED} for write operations.</li>
 *     <li>Concurrent access is managed at the repository level (leases and batch processing).</li>
 *     <li>Domain messages are immutable; this adapter does not modify any fields.</li>
 * </ul>
 *
 * <p>
 * Overall, this adapter ensures that invalid messages or null outputs are safely filtered
 * for reactive downstream consumption, providing a consistent and fault-tolerant API
 * for the outbox system.
 * </p>
 */
@Slf4j
@ApplicationScoped
@IfBuildProperty(name = "processing.messages.storage.service", stringValue = "redis")
public class OutboxPostgresAdapter implements OutboxManager<Message> {

    @Inject
    OutboxPostgresRepository repo;

    @Inject
    MessageMapper mapper;

    @Override
    public Uni<StageStatus> save(Message message) {
        if (!isValidForSave(message)) {
            log.warn("Message failed minimal validation and will not be saved: {}", message);
            return Uni.createFrom().item(StageStatus.FAILED);
        }

        OutboxMessage outboxMessage = mapper.toOutboxMessage(message);
        HistoryRow historyRow = mapper.toHistoryRow(message);

        if (outboxMessage == null || historyRow == null) {
            log.warn("Mapper returned null for message, cannot save: {}", message);
            return Uni.createFrom().item(StageStatus.FAILED);
        }

        return repo.insertHistoryAndOutboxTransactional(
                        List.of(historyRow),
                        List.of(outboxMessage)
                )
                .onItem().transform(ids -> StageStatus.SUCCESS)
                .replaceIfNullWith(StageStatus.FAILED)
                .onFailure().recoverWithItem(er -> {
                    log.error("Error saving message: {}", message, er);
                    return StageStatus.FAILED;
                });
    }

    @Override
    public Uni<StageStatus> remove(Message message) {
        if (message == null || message.getCorrelationId() == null) {
            log.warn("Cannot remove message; correlationId is null: {}", message);
            return Uni.createFrom().item(StageStatus.FAILED);
        }

        return repo.deleteProcessedByIds(List.of(message.getCorrelationId()))
                .onItem().transform(rows -> StageStatus.SUCCESS)
                .replaceIfNullWith(StageStatus.FAILED)
                .onFailure().recoverWithItem(er -> {
                    log.error("Error removing message: {}", message, er);
                    return StageStatus.FAILED;
                });
    }

    @Override
    public Uni<Message> fetch() {
        return repo.fetchBatchForProcessing(1, Duration.ofSeconds(5))
                .onItem().transformToUni(batch -> {
                    if (batch.isEmpty()) {
                        return Uni.createFrom().item((Message) null);
                    }
                    Message msg = mapper.toDomainMessage(batch.get(0));
                    if (msg == null || msg.getPayload() == null) {
                        log.warn("Fetched message is invalid or has null payload: {}", msg);
                        return Uni.createFrom().item((Message) null);
                    }
                    return Uni.createFrom().item(msg);
                })
                .onFailure().recoverWithItem(er -> {
                    log.error("Error fetching single message", er);
                    return null;
                });
    }

    @Override
    public Uni<List<Message>> fetchBatch(int batchSize) {
        return repo.fetchBatchForProcessing(batchSize, Duration.ofSeconds(5))
                .onItem().transform(batch -> {
                    if (batch.isEmpty()) return List.<Message>of();
                    return batch.stream()
                            .map(mapper::toDomainMessage)
                            .filter(m -> m != null && m.getPayload() != null)
                            .toList();
                })
                .replaceIfNullWith(List.<Message>of())
                .onFailure().recoverWithItem(er -> {
                    log.error("Error fetching batch of messages", er);
                    return List.of();
                });
    }

    @Override
    public Uni<Long> collectGarbage(Predicate<Message> filter) {
        return Uni.createFrom().item(0L);
    }

    // ----------------------------------------------------------------------
    // Minimal internal validation for save
    // ----------------------------------------------------------------------
    private boolean isValidForSave(Message msg) {
        if (msg == null) return false;
        if (msg.getMessageId() == null || msg.getSenderId() == null || msg.getRecipientId() == null) return false;
        return msg.getPayload() != null;
    }
}
