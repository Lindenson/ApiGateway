package org.hormigas.ws.infrastructure.websocket.notifier;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.presence.coordinator.AsyncClientCoordinator;
import org.hormigas.ws.ports.notifier.Notifier;
import org.hormigas.ws.ports.session.SessionRegistry;
import org.hormigas.ws.domain.session.ClientData;

import java.util.Optional;


@Slf4j
@ApplicationScoped
public class PresenceNotifier implements Notifier<WebSocketConnection> {

    @Inject
    PresencePublisher publisher;

    @Inject
    AsyncClientCoordinator updater;

    @Inject
    SessionRegistry<WebSocketConnection> registry;

    @Override
    public void notifyJoin(ClientData newClient, WebSocketConnection connection) {
        try {
            registry.register(newClient, connection);
            updater.addPresence(newClient.id(), newClient.name());
            publisher.publishInit(newClient, registry);
            publisher.publishJoin(newClient);
            log.debug("Presence INIT coordinated for {}", newClient.id());
        } catch (Exception e) {
            log.error("Failed to coordinate INIT presence for {}", newClient.id(), e);
        }
    }

    @Override
    public void notifyLeave(WebSocketConnection connection, long timestamp) {
        try {
            Optional.ofNullable(registry.deregister(connection))
                    .map(s -> new ClientData(s.getId(), s.getName()))
                    .ifPresent(client -> {
                        publisher.publishLeave(client);
                        updater.removePresence(client.id(), timestamp);
                        log.debug("Presence LEAVE coordinated for {}", client.id());
                    });
        } catch (Exception e) {
            log.error("Failed to coordinate LEAVE presence for {}", connection, e);
        }
    }

    @Override
    public void notifyAbsent(String clientId, long timestamp) {
        log.debug("Absence coordinated for {}", clientId);
        updater.removePresence(clientId, timestamp);
    }
}
