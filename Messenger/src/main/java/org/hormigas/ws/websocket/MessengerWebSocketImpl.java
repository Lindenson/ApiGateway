package org.hormigas.ws.websocket;

import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.security.dto.ClientData;
import org.hormigas.ws.websocket.api.MessengerWebSocket;
import org.hormigas.ws.websocket.utils.WebSocketUtils;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@WebSocket(path = "/ws")
@ApplicationScoped
public class MessengerWebSocketImpl implements MessengerWebSocket<Message> {


    @Inject
    AcknowledgmentPublisher acknowledgmentPublisher;

    @Inject
    WebSocketUtils webSocketUtils;

    @Inject
    ClientConnectionRegistry connectionRegistry;

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(MessengerWebSocketImpl.class);


    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        Optional<ClientData> clientData = webSocketUtils.getValidatedClientData(connection);

        if (clientData.isPresent()) {
            connectionRegistry.registerClient(clientData.get(), connection);
            log.debug("Token accepted");
        } else {
            log.warn("Token NOT accepted");
            connection.closeAndAwait(webSocketUtils.getCloseReason());
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        connectionRegistry.deregisterConnection(connection);
    }


    @OnTextMessage
    public Uni<Void> onMessage(String message) {
        try {
            JsonObject json = new JsonObject(message);
            if (json.containsKey("ackId")) {
                String ackId = json.getString("ackId");
                acknowledgmentPublisher.publish(ackId);
            }
        } catch (Exception e) {
            log.error("Invalid message format: {}", message, e);
        }
        return Uni.createFrom().voidItem();
    }

    public Uni<Boolean> sendToClient(Message msg) {
        log.debug("Sending message {}", msg);

        List<Uni<Void>> tasks = connectionRegistry.getConnections().stream()
                .filter(clientConnection -> clientConnection.getId().equals(msg.getClientId()))
                .map(conn -> sendWithPayload(conn.getWsConnection(), msg))
                .collect(Collectors.toList());

        if (tasks.isEmpty()) {
            log.debug("No connection found");
            return Uni.createFrom().item(Boolean.FALSE);
        }

        return Uni.combine().all().unis(tasks).discardItems().replaceWith(Boolean.TRUE)
                .onFailure().recoverWithItem(error -> Boolean.FALSE);
    }

    public Uni<Void> sendWithPayload(WebSocketConnection conn, Message msg) {
        if (conn.isOpen()) {
            return webSocketUtils.encodeMessage(msg).map(em ->
                            conn.sendText(em)
                                    .onFailure(HttpClosedException.class).recoverWithUni(err -> {
                                        log.error("WebSocket closed before send: {}", conn.id());
                                        connectionRegistry.deregisterConnection(conn);
                                        return Uni.createFrom().voidItem();
                                    })
                                    .onFailure().invoke(err -> {
                                        log.error("Failed to send to {}", conn, err);
                                    }))
                    .orElse(Uni.createFrom().voidItem());
        }

        log.debug("Sending message to websocket failed: connection closed");
        return Uni.createFrom().voidItem();
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        connectionRegistry.deregisterConnection(connection);
        if (throwable instanceof HttpClosedException) log.warn("WebSocket connection closed", throwable);
        log.error("WebSocket connection error ", throwable);
    }
}
