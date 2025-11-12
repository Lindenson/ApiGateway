package org.hormigas.ws.core.presence;

public interface AsyncPresenceCoordinator {
    void addPresence(String userId, String name);
    void removePresence(String userId, long timestamp);
}
