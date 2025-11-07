package org.hormigas.ws.ports.channel.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpClosedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.validator.Validator;
import org.hormigas.ws.ports.channel.presense.ClientsRegistry;
import org.hormigas.ws.ports.channel.presense.dto.ClientData;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.ws.filter.ChannelFilter;
import org.hormigas.ws.ports.channel.ws.publisher.presence.PresencePublisher;
import org.hormigas.ws.ports.channel.ws.publisher.router.IncomingPublisher;
import org.hormigas.ws.ports.channel.ws.transformer.Transformer;
import org.hormigas.ws.ports.channel.ws.utils.WebSocketUtils;

import java.util.Optional;

@Slf4j
@WebSocket(path = "/ws")
@ApplicationScoped
public class WebsocketService {

    @Inject
    IncomingPublisher incomingPublisher;

    @Inject
    WebSocketUtils webSocketUtils;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Validator<Message> validator;

    @Inject
    Transformer<Message, WebSocketConnection> transformer;

    @Inject
    ChannelFilter<Message, WebSocketConnection> channelFilter;

    @Inject
    PresencePublisher presencePublisher;

    @Inject
    ClientsRegistry<WebSocketConnection> registry;


    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        webSocketUtils.getValidatedClientData(connection).ifPresentOrElse(client -> {
            registry.register(client, connection);
            presencePublisher.publishInit(client, registry);
            presencePublisher.publishJoin(client);
        }, () -> connection.closeAndAwait(webSocketUtils.getCloseReason()));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        Optional.ofNullable(registry.deregister(connection))
                .map(s -> new ClientData(s.getId(), s.getName()))
                .ifPresent(presencePublisher::publishLeave);
    }


    @OnTextMessage
    public Uni<Void> onMessage(String rawJson, WebSocketConnection conn) {
        log.debug("Receiving message {}", rawJson);
        try {
            ClientSession<WebSocketConnection> clientSession = registry.getSessionByConnection(conn);
            if (clientSession == null) {
                log.warn("Received message from unregistered connection: {}", conn.id());
                return Uni.createFrom().voidItem();
            }
            Message message = objectMapper.readValue(rawJson, Message.class);
            if (!channelFilter.filter(message, clientSession)) {
                return Uni.createFrom().voidItem();
            }
            if (!validator.valid(message)) {
                log.warn("Message didn't pass validation {}", message.getMessageId());
                return Uni.createFrom().voidItem();
            }
            incomingPublisher.publish(message);
        } catch (Exception e) {
            log.error("Invalid message format: {}", rawJson, e);
        }
        return Uni.createFrom().voidItem();
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        registry.deregister(connection);
        if (throwable instanceof HttpClosedException) log.warn("WebSocket connection closed", throwable);
        log.error("WebSocket connection error ", throwable);
    }
}
