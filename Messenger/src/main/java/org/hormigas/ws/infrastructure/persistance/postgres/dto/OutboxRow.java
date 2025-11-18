package org.hormigas.ws.infrastructure.persistance.postgres.dto;

import java.time.Instant;

public record OutboxRow(long id, String senderId, String recipientId, String payloadJson, String metaJson,
                        long senderTs, String senderTz, long serverTs, String type, String messageId,
                        String correlationId, Instant createdAt, Instant leaseUntil) {
}