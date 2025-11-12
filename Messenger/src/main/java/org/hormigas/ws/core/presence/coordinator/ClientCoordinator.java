package org.hormigas.ws.core.presence.coordinator;

import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.presence.PresenceCoordinator;
import org.hormigas.ws.core.watermark.LeaveStamp;
import org.hormigas.ws.domain.session.ClientData;
import org.hormigas.ws.ports.presence.PresenceManager;


@Slf4j
@RequiredArgsConstructor
public class ClientCoordinator implements PresenceCoordinator {

    private final PresenceManager presenceManager;
    private final LeaveStamp leaveStamp;


    @Override
    public Uni<Void> addPresence(String userId, String name) {
        return presenceManager.addClient(new ClientData(userId, name))
                .invoke(() -> log.debug("Client {} added to presence", userId))
                .onFailure().invoke(failure -> log.error("Failed to add client {} to presence", userId, failure));
    }

    @Override
    public Uni<Void> removePresence(String userId, long timestamp) {
        return presenceManager.removeClient(userId)
                .call(() -> leaveStamp.setLeaveStamp(userId, timestamp))
                .invoke(() -> log.debug("Client {} removed from presence", userId))
                .onFailure().invoke(failure -> log.error("Failed to remove client {} from presence", userId, failure));
    }
}
