package org.hormigas.ws.ports.channel.ws.publisher.presence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.presense.ClientsRegistry;
import org.hormigas.ws.ports.channel.presense.dto.ClientData;
import org.hormigas.ws.ports.channel.presense.event.PresenceEventFactory;
import org.hormigas.ws.ports.channel.ws.publisher.router.IncomingBackpressurePublisher;

import java.util.List;

@Slf4j
@ApplicationScoped
public class PresencePublisher {

    @Inject
    IncomingBackpressurePublisher incomingPublisher;

    @Inject
    PresenceEventFactory eventFactory;

    @Inject
    GlobalUpdater globalUpdater;

    public void publishInit(ClientData newClient, ClientsRegistry<?> registry) {
        try {
            List<ClientData> all = registry.getAllOnlineClients();
            Message message = eventFactory.createInitMessage(all, newClient.id());
            incomingPublisher.publish(message);
            log.debug("Published INIT presence for {}", newClient.id());
        } catch (Exception e) {
            log.error("Failed to publish INIT presence", e);
        }
    }

    public void publishJoin(ClientData client) {
        try {
            Message message = eventFactory.createJoinMessage(client);
            globalUpdater.addPresence(client.id(), client.name());
            incomingPublisher.publish(message);
            log.debug("Published JOIN presence for {}", client.id());
        } catch (Exception e) {
            log.error("Failed to publish JOIN presence", e);
        }
    }

    public void publishLeave(ClientData client) {
        try {
            Message message = eventFactory.createLeaveMessage(client);
            globalUpdater.removePresence(client.id());
            incomingPublisher.publish(message);
            log.debug("Published LEAVE presence for {}", client.id());
        } catch (Exception e) {
            log.error("Failed to publish LEAVE presence", e);
        }
    }
}
