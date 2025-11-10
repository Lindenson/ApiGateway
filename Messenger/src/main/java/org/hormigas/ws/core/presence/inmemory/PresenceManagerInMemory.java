package org.hormigas.ws.core.presence.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.core.presence.local.LocalAdapter;
import org.hormigas.ws.core.presence.PresenceManager;
import org.hormigas.ws.core.presence.dto.Member;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PresenceManagerInMemory implements PresenceManager {

    @Inject
    LocalAdapter localAdapter;

    private final Map<String, Member> presences = new ConcurrentHashMap<>();


    @Override
    public Uni<Void> addClient(Member user) {
        return Uni.createFrom().item(presences.put(user.id(), user)).replaceWithVoid();
    }

    @Override
    public Uni<Void> removeClient(String userId) {
        return Uni.createFrom().item(presences.remove(userId)).replaceWithVoid();
    }

    @Override
    public Uni<List<Member>> allPresent() {
        return Uni.createFrom().item(presences.values().stream().toList());
    }

    @Override
    public boolean isLocallyPresent(String userId) {
        return localAdapter.isLocallyPresent(userId);
    }
}
