package org.hormigas.ws.ports.channel.ws.presence;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.presence.PresenceManager;
import org.hormigas.ws.core.presence.dto.Member;
import org.hormigas.ws.core.watermark.LeaveStamp;

@Slf4j
@ApplicationScoped
public class GlobalUpdater {

    @Inject
    PresenceManager presenceManager;

    @Inject
    LeaveStamp leaveStamp;

    public void addPresence(String userId, String name) {
        presenceManager.addClient(new Member(userId, name))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        ignored -> log.debug("Client {} added to presence", userId),
                        failure -> log.error("Failed to add client to presence", failure)
                );
    }

    public void removePresence(String userId, long timestamp) {
        presenceManager.removeClient(userId)
                .call(() -> leaveStamp.setLeaveStamp(userId, timestamp))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        ignored -> log.debug("Client {} removed to presence", userId),
                        failure -> log.error("Failed to remove client to presence", failure)
                );
    }
}
