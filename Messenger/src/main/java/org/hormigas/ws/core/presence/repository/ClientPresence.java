package org.hormigas.ws.core.presence.repository;

import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.presence.Presence;
import org.hormigas.ws.core.watermark.LeaveStamp;
import org.hormigas.ws.domain.credentials.ClientData;
import org.hormigas.ws.ports.presence.PresenceManager;


@Slf4j
@RequiredArgsConstructor
public class ClientPresence implements Presence {

    private final PresenceManager presenceManager;
    private final LeaveStamp leaveStamp;


    @Override
    public Uni<Void> add(String id, String name, long timestamp) {
        return presenceManager.add(id, name, timestamp)
                .invoke(() -> log.debug("Client {} added to presence", id))
                .onFailure().invoke(failure -> log.error("Failed to add client {} to presence", id, failure));
    }

    @Override
    public Uni<Void> remove(String userId, long timestamp) {
        return presenceManager.remove(userId, timestamp)
                .call(() -> leaveStamp.setLeaveStamp(userId, timestamp))
                .invoke(() -> log.debug("Client {} removed from presence", userId))
                .onFailure().invoke(failure -> log.error("Failed to remove client {} from presence", userId, failure));
    }
}
