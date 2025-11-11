package org.hormigas.ws.core.presence.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.core.presence.PresenceManager;
import org.hormigas.ws.core.presence.dto.Member;
import org.hormigas.ws.core.presence.local.LocalAdapter;

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
 *    we should track a version or registration timestamp in the Member object.
 *
 *    Example approach:
 *      - Add a `registrationTimestamp` (or version) to Member.
 *      - When adding a client, record the timestamp.
 *      - When removing a client, provide the timestamp of the registration we intend to remove.
 *      - Only remove the client if the current stored timestamp <= provided timestamp.
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
 *        to conditionally remove only if the timestamp matches.
 *      - all other methods remain mostly unchanged but should respect timestamps if needed.
 *
 * This ensures presence management is thread-safe and consistent, especially in reactive
 * and multi-threaded environments where users may reconnect quickly.
 */

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
