package org.hormigas.ws.ports.channel.ws.deliver;

import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.channel.DeliveryChannel;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.presense.ClientsRegistry;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.ws.transformer.Transformer;
import org.hormigas.ws.ports.channel.ws.utils.WebSocketUtils;

import java.util.Optional;

import static org.hormigas.ws.core.router.stage.StageStatus.FAILED;
import static org.hormigas.ws.core.router.stage.StageStatus.SUCCESS;
import static org.hormigas.ws.ports.channel.presense.event.PresenceEventFactory.BROADCAST;

@Slf4j
@ApplicationScoped
public class MessageSender implements DeliveryChannel<Message> {

    @Inject
    ClientsRegistry<WebSocketConnection> registry;

    @Inject
    Transformer<Message, WebSocketConnection> transformer;

    @Inject
    WebSocketUtils utils;

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
            log.warn("No active sessions for recipient {}", message.getRecipientId());
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
