package org.hormigas.ws.infrastructure.websocket.outbound;

import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.ports.channel.DeliveryChannel;
import org.hormigas.ws.domain.stage.StageStatus;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.ports.session.SessionRegistry;
import org.hormigas.ws.domain.session.ClientSession;
import org.hormigas.ws.ports.notifier.Coordinator;
import org.hormigas.ws.infrastructure.websocket.outbound.transformers.Transformer;
import org.hormigas.ws.infrastructure.websocket.utils.WebSocketUtils;

import java.util.Optional;

import static org.hormigas.ws.domain.stage.StageStatus.FAILED;
import static org.hormigas.ws.domain.stage.StageStatus.SUCCESS;
import static org.hormigas.ws.infrastructure.websocket.coordinator.event.PresenceEventFactory.BROADCAST;

@Slf4j
@ApplicationScoped
public class Deliverer implements DeliveryChannel<Message> {

    @Inject
    SessionRegistry<WebSocketConnection> registry;

    @Inject
    Transformer<Message, WebSocketConnection> transformer;

    @Inject
    WebSocketUtils utils;

    @Inject
    Coordinator<WebSocketConnection> coordinator;

    public Uni<StageStatus> deliver(Message message) {
        if (BROADCAST.equals(message.getRecipientId())) {
            return sendBroadcast(message)
                    .replaceWith(SUCCESS)
                    .onItem().invoke(() -> log.debug("Broadcast delivered {}", message.getMessageId()))
                    .onFailure().recoverWithItem(FAILED);
        }

        // not transforming broadcast
        var unis = registry.streamSessionsByClientId(message.getRecipientId())
                .map(session -> send(session, transformer.apply(message, session)))
                .toList();

        if (unis.isEmpty()) {
            log.info("No active sessions for recipient {}", message.getRecipientId());
            coordinator.passive(message.getRecipientId());
            return Uni.createFrom().item(StageStatus.SKIPPED);
        }

        return Uni.combine().all().unis(unis)
                .discardItems()
                .replaceWith(SUCCESS)
                .onItem().invoke(() -> log.debug("Delivered {}", message.getMessageId()))
                .onFailure().recoverWithItem(FAILED);
    }


    private Uni<Void> send(ClientSession<WebSocketConnection> session, Message message) {
        WebSocketConnection conn = session.getSession();
        if (!conn.isOpen()) {
            registry.deregister(conn);
            return Uni.createFrom().voidItem();
        }

        return utils.encodeMessage(message)
                .map(encoded -> conn.sendText(encoded)
                        .onFailure().invoke(err -> log.error("Send error to {}", conn.id(), err)))
                .orElse(Uni.createFrom().voidItem());
    }


    private Uni<Void> sendBroadcast(Message message) {
        Optional<ClientSession<WebSocketConnection>> first = registry.streamAllOnlineClients()
                .filter(client -> client.getSession().isOpen())
                .findFirst();

        first.map(ClientSession::getSession).ifPresent(registry::cleanUnused);

        return first.map(ClientSession::getSession)
                .flatMap(session -> utils.encodeMessage(message)
                        .map(m -> session.broadcast().sendText(m)
                                .onFailure().invoke(err ->
                                        log.error("Broadcast send error for message {}", message.getMessageId(), err)))
                )
                .orElse(Uni.createFrom().voidItem());
    }
}
