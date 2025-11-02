package org.hormigas.ws.core.presence;

import io.smallrye.mutiny.Uni;

import java.util.List;

public interface PresenceManager {
    Uni<Void> addClient(String userId);
    Uni<Void> removeClient(String userId);
    Uni<Boolean> isPresent(String userId);
    Uni<List<String>> allPresent();
}
