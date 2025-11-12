package org.hormigas.ws.core.presence;

import io.smallrye.mutiny.Uni;

public interface PresenceCoordinator {
    Uni<Void> addPresence(String userId, String name);
    Uni<Void> removePresence(String userId, long timestamp);
}
