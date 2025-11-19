package org.hormigas.ws.ports.tetris;

import io.smallrye.mutiny.Uni;

public interface TetrisMarker {
    Uni<Void> onSent(String recipientId, long id);
    Uni<Void> onAck(String recipientId, long id);
    Uni<Void> onDisconnect(String recipientId);
    Uni<Long> computeGlobalSafeDeleteId();
}
