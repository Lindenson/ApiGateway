package org.hormigas.ws.ports.tetris;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface Tetris {
    Uni<Void> onSent(UUID recipientId, long id);
    Uni<Void> onAck(UUID recipientId, long id);
    Uni<Void> onDisconnect(UUID recipientId);
    Uni<Long> computeGlobalSafeDeleteId();
}
