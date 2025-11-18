package org.hormigas.ws.infrastructure.persistance.postgres.dto;

import java.time.Instant;

public record HistoryRow(String messageId, String messageJson, Instant createdAt) {
}
