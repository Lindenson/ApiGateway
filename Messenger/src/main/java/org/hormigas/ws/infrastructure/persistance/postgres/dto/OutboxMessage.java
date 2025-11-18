package org.hormigas.ws.infrastructure.persistance.postgres.dto;


public record OutboxMessage(String senderId, String recipientId, String type, String messageId, String correlationId,
                            long senderTimestamp, String senderTimezone, long serverTimestamp, String payloadJson,
                            String metaJson) {
}