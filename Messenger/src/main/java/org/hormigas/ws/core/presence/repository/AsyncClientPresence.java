package org.hormigas.ws.core.presence.repository;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.presence.AsyncPresence;
import org.hormigas.ws.core.presence.Presence;
import org.hormigas.ws.core.watermark.LeaveStamp;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.ports.presence.PresenceManager;
import org.hormigas.ws.ports.tetris.TetrisMarker;

@Slf4j
@ApplicationScoped
public class AsyncClientPresence implements AsyncPresence {

    Presence delegate;

    @Inject
    PresenceManager presenceManager;

    @Inject
    TetrisMarker<Message> tetrisMarker;

    @Inject
    LeaveStamp leaveStamp;

    @PostConstruct
    void init() {
        delegate = new ClientPresence(presenceManager, leaveStamp);
    }

    @Override
    public void add(String userId, String name, long timestamp) {
        delegate.add(userId, name, timestamp)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        ignored -> log.debug("Client {} added to presence", userId),
                        failure -> log.error("Failed to add client to presence", failure)
                );
    }


    //toDO
    @Override
    public void remove(String userId, long timestamp) {
        tetrisMarker.onDisconnect(userId)
                .onItem().call(() ->
        delegate.remove(userId, timestamp))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        ignored -> log.debug("Client {} removed to presence", userId),
                        failure -> log.error("Failed to remove client to presence", failure)
                );
    }
}
