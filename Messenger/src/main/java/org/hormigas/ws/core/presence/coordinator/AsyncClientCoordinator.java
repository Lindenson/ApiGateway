package org.hormigas.ws.core.presence.coordinator;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.presence.AsyncPresenceCoordinator;
import org.hormigas.ws.core.presence.PresenceCoordinator;
import org.hormigas.ws.core.watermark.LeaveStamp;
import org.hormigas.ws.ports.presence.PresenceManager;

@Slf4j
@ApplicationScoped
public class AsyncClientCoordinator implements AsyncPresenceCoordinator {

    PresenceCoordinator delegate;

    @Inject
    PresenceManager presenceManager;

    @Inject
    LeaveStamp leaveStamp;

    @PostConstruct
    void init() {
        delegate = new ClientCoordinator(presenceManager, leaveStamp);
    }

    @Override
    public void addPresence(String userId, String name) {
        delegate.addPresence(userId, name)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        ignored -> log.debug("Client {} added to presence", userId),
                        failure -> log.error("Failed to add client to presence", failure)
                );
    }

    @Override
    public void removePresence(String userId, long timestamp) {
        delegate.removePresence(userId, timestamp)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        ignored -> log.debug("Client {} removed to presence", userId),
                        failure -> log.error("Failed to remove client to presence", failure)
                );
    }
}
