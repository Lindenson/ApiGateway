package org.hormigas.ws.ports.channel.ws.publisher.presence;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.presence.PresenceManager;
import org.hormigas.ws.core.presence.dto.Member;

@Slf4j
@ApplicationScoped
public class GlobalUpdater {

    @Inject
    PresenceManager presenceManager;

    public void addPresence(String userId, String name) {
        presenceManager.addClient(new Member(userId, name))
                .eventually(() -> Uni.createFrom().voidItem())
                .emitOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        ignored -> log.debug("Client {} added to presence", userId),
                        failure -> log.error("Failed to add client to presence", failure)
                );
    }

    public void removePresence(String userId) {
        presenceManager.removeClient(userId)
                .eventually(() -> Uni.createFrom().voidItem())
                .emitOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        ignored -> log.debug("Client {} removed to presence", userId),
                        failure -> log.error("Failed to remove client to presence", failure)
                );
    }
}
