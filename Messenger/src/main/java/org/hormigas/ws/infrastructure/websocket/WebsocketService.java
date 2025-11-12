package org.hormigas.ws.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpClosedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.credits.filter.InboundMessageFilter;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.validator.Validator;
import org.hormigas.ws.ports.session.SessionRegistry;
import org.hormigas.ws.domain.session.ClientSession;
import org.hormigas.ws.core.credits.ChannelFilter;
import org.hormigas.ws.ports.notifier.Notifier;
import org.hormigas.ws.infrastructure.websocket.inbound.InboundPublisher;
import org.hormigas.ws.infrastructure.websocket.utils.WebSocketUtils;

@Slf4j
@WebSocket(path = "/ws")
@ApplicationScoped
public class WebsocketService {

    @Inject
    InboundPublisher incomingPublisher;

    @Inject
    WebSocketUtils webSocketUtils;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Validator<Message> validator;

    @Inject
    SessionRegistry<WebSocketConnection> registry;

    @Inject
    Notifier<WebSocketConnection> notifier;


    private final ChannelFilter<Message, WebSocketConnection> channelFilter = new InboundMessageFilter<>();


    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        webSocketUtils.getValidatedClientData(connection).ifPresentOrElse(client -> {
            notifier.notifyJoin(client, connection);
        }, () -> connection.closeAndAwait(webSocketUtils.getCloseReason()));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        notifier.notifyLeave(connection, System.currentTimeMillis());
    }


    @OnTextMessage
    public Uni<Void> onMessage(String rawJson, WebSocketConnection connection) {
        log.debug("Receiving message {}", rawJson);
        try {
            ClientSession<WebSocketConnection> clientSession = registry.getSessionByConnection(connection);
            if (clientSession == null) {
                log.warn("Received message from unregistered connection: {}", connection.id());
                return Uni.createFrom().voidItem();
            }
            Message message = objectMapper.readValue(rawJson, Message.class);
            if (!channelFilter.filter(message, clientSession)) {
                return Uni.createFrom().voidItem();
            }
            if (!validator.valid(message)) {
                log.warn("Invalid message (id={}): {}", message.getMessageId(), message);
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
        if (throwable instanceof HttpClosedException) {
            log.warn("WebSocket connection closed: {}", connection.id());
        } else {
            log.error("WebSocket connection error for {}: {}", connection.id(), throwable.getMessage(), throwable);
        }
    }
}
