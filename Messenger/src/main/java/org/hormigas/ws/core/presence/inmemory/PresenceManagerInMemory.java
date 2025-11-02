package org.hormigas.ws.core.presence.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hormigas.ws.core.presence.PresenceManager;

import java.util.List;

@ApplicationScoped
public class PresenceManagerInMemory implements PresenceManager {
    @Override
    public Uni<Void> addClient(String userId) {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> removeClient(String userId) {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Boolean> isPresent(String userId) {
        return Uni.createFrom().item(Boolean.TRUE);
    }

    @Override
    public Uni<List<String>> allPresent() {
        return null;
    }
}
