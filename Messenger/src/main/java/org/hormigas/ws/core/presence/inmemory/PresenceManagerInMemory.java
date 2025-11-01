package org.hormigas.ws.core.presence;

import io.smallrye.mutiny.Uni;

import java.util.List;

public class PresenceManagerInMemory implements PresenceManager {
    @Override
    public Uni<Void> addClient(String userId) {
        return null;
    }

    @Override
    public Uni<Void> removeClient(String userId) {
        return null;
    }

    @Override
    public Uni<Boolean> isPresent(String userId) {
        return null;
    }

    @Override
    public Uni<List<String>> allPresent() {
        return null;
    }
}
