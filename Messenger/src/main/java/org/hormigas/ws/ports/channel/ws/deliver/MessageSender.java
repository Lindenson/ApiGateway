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

import java.util.List;
import java.util.stream.Collectors;

import static org.hormigas.ws.core.router.stage.StageStatus.*;
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
        List<Uni<Void>> jobs = (BROADCAST.equals(message.getRecipientId())
                ? registry.streamAllOnlineClients()
                : registry.streamSessionsByClientId(message.getRecipientId()))
                .map(conn -> send(conn, transformer.apply(message, conn)))
                .collect(Collectors.toList());

        if (jobs.isEmpty()) {
            log.warn("No recipient found for {}", message.getRecipientId());
            return Uni.createFrom().item(SKIPPED);
        }

        return Uni.combine().all().unis(jobs).discardItems()
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
}
