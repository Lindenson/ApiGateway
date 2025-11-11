package org.hormigas.ws.ports.channel.ws.presence;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.ports.channel.registry.ClientsRegistry;
import org.hormigas.ws.ports.channel.registry.dto.ClientData;

import java.util.Optional;


@Slf4j
@ApplicationScoped
public class PresenceCoordinator implements Coordinator<WebSocketConnection> {

    @Inject
    PresencePublisher publisher;

    @Inject
    GlobalUpdater updater;


    @Inject
    ClientsRegistry<WebSocketConnection> registry;

    @Override
    public void handleJoin(ClientData newClient, WebSocketConnection connection) {
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
    public void handleLeave(WebSocketConnection connection, long timestamp) {
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
    public void handleAbsent(String clientId, long timestamp) {
        log.debug("Absence coordinated for {}", clientId);
        updater.removePresence(clientId, timestamp);
    }
}
