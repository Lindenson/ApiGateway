package org.hormigas.ws.infrastructure.cache.inmemory.presence;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hormigas.ws.domain.session.ClientData;
import org.hormigas.ws.ports.presence.PresenceManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/*
 * TODO:
 * The current implementation of PresenceManagerInMemory has a potential concurrency issue:
 *
 * 1. Currently, removeClient simply removes the Member from the map:
 *      presences.remove(userId)
 *    This is not safe if the same user reconnects immediately in another thread,
 *    because the new registration will be overwritten and removed inadvertently.
 *
 * 2. To make this robust and future-proof (for Redis or other distributed storage),
 *    we should track a version or registration connectedAt in the Member object.
 *
 *    Example approach:
 *      - Add a `registrationTimestamp` (or version) to Member.
 *      - When adding a client, record the connectedAt.
 *      - When removing a client, provide the connectedAt of the registration we intend to remove.
 *      - Only remove the client if the current stored connectedAt <= provided connectedAt.
 *
 * 3. Benefits of this approach:
 *      - Prevents accidental removal of a client that reconnected concurrently.
 *      - Enables safe replacement with Redis or any other distributed storage.
 *      - Makes garbage collection, presence cleanup, and reconnection logic deterministic.
 *
 * 4. Implementation notes:
 *      - addClient(Member user) -> record user.registrationTimestamp = System.currentTimeMillis()
 *        (or a logical version).
 *      - removeClient(String userId, long registrationTimestamp) -> use computeIfPresent
 *        to conditionally remove only if the connectedAt matches.
 *      - all other methods remain mostly unchanged but should respect timestamps if needed.
 *
 * This ensures presence management is thread-safe and consistent, especially in reactive
 * and multi-threaded environments where users may reconnect quickly.
 */

@ApplicationScoped
@IfBuildProperty(name = "processing.messages.storage.service", stringValue = "memory")
public class PresenceManagerInMemory implements PresenceManager {

    private final Map<String, ClientData> presences = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> addClient(ClientData user) {
        return Uni.createFrom().item(presences.put(user.id(), user)).replaceWithVoid();
    }

    @Override
    public Uni<Void> removeClient(String userId, long timestamp) {
        return Uni.createFrom().item(presences.remove(userId)).replaceWithVoid();
    }

    @Override
    public Uni<List<ClientData>> allPresent() {
        return Uni.createFrom().item(presences.values().stream().toList());
    }
}
