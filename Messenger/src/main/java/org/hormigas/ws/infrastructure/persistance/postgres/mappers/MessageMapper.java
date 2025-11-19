package org.hormigas.ws.infrastructure.persistance.postgres.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.constraint.NotNull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.message.MessageType;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.HistoryRow;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxMessage;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxRow;

import java.time.Instant;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class MessageMapper implements OutboxMapper {

    @Inject
    ObjectMapper mapper;

    private static final TypeReference<Map<String, String>> META_TYPE_REF =
            new TypeReference<>() {};

    private static final String UNKNOWN = "UNKNOWN";


    /**
     * Map domain Message -> OutboxMessage (what will be saved in outbox table)
     * We intentionally exclude creditsAvailable, sessionId, sequenceNumber.
     */
    @Override
    @Nullable
    public OutboxMessage toOutboxMessage(@Nullable Message msg) {
        if (msg == null) return null;
        final String payloadJson = msg.getPayload() == null? null : serialize(msg.getPayload());
        final String metaJson = msg.getMeta() == null ? null : serialize(msg.getMeta());

        long serverTs = msg.getServerTimestamp();
        if (serverTs == 0) {
            serverTs = Instant.now().toEpochMilli();
        }

        return new OutboxMessage(
                msg.getSenderId(),
                msg.getRecipientId(),
                msg.getType() == null ? UNKNOWN : msg.getType().name(),
                msg.getMessageId(),
                msg.getCorrelationId(),
                msg.getSenderTimestamp(),
                msg.getSenderTimezone(),
                serverTs,
                payloadJson,
                metaJson
        );
    }

    /**
     * Map domain Message -> HistoryRow (full JSONB)
     */
    @Override
    @Nullable
    public HistoryRow toHistoryRow(@Nullable Message msg) {
        if (msg == null) return null;
        return new HistoryRow(
                msg.getMessageId(),
                serialize(msg),
                Instant.now()
        );
    }


    /**
     * Map OutboxRow -> domain Message (full JSONB)
     */
    @Override
    @Nullable
    public Message toDomainMessage(@Nullable OutboxRow outboxRow) {
        if (outboxRow == null) return null;
        try {
            Message.Payload payload = deserializePayload(outboxRow.payloadJson());
            Map<String, String> meta = deserializeMeta(outboxRow.metaJson());

            return Message.builder()
                    .senderId(outboxRow.senderId())
                    .recipientId(outboxRow.recipientId())
                    .type(typeOrNull(outboxRow.type()))
                    .messageId(outboxRow.messageId())
                    .correlationId(outboxRow.correlationId())
                    .senderTimestamp(outboxRow.senderTs())
                    .senderTimezone(outboxRow.senderTz())
                    .serverTimestamp(outboxRow.serverTs())
                    .payload(payload)
                    .meta(meta)
                    .build();

        } catch (Exception e) {
            log.error("Failed to map OutboxRow -> Message: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Map HistoryRow -> Message
     */
    @Nullable
    @Override
    public Message fromHistoryRow(@Nullable HistoryRow row) {
        if (row == null) return null;

        try {
            return mapper.readValue(row.messageJson(), Message.class);
        } catch (Exception e) {
            log.error("Failed to map HistoryRow -> Message: {}", e.getMessage());
            return null;
        }
    }


    // ===== Helper methods =====
    @NotNull
    private String serialize(@NotNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization failed for: " + value, e);
        }
    }

    @Nullable
    private MessageType typeOrNull(@Nullable String typeName) {
        return typeName == null ? null : MessageType.valueOf(typeName);
    }

    @Nullable
    private Message.Payload deserializePayload(@Nullable String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, Message.Payload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize payload JSON: " + json, e);
        }
    }

    @Nullable
    private Map<String, String> deserializeMeta(@Nullable String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, META_TYPE_REF);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize meta JSON: " + json, e);
        }
    }
}
