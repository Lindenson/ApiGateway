package org.hormigas.ws.ports.tetris;

import io.smallrye.mutiny.Uni;

public interface TetrisMarker {
    Uni<Void> onSent(String recipientKey, long id);
    Uni<Void> onAck(String recipientKey, long id);
    Uni<Void> onDisconnect(String recipientKey);
    Uni<Long> computeGlobalSafeDeleteId();
}
