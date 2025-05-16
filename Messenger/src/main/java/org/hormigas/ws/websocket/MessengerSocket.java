package org.hormigas.ws.websocket;

import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.security.ClientData;
import org.hormigas.ws.security.JwtValidator;
import org.hormigas.ws.service.MessageService;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@WebSocket(path = "/ws")
@ApplicationScoped
public class MessengerSocket {

    @Inject
    JwtValidator jwtValidator;

    @Inject
    MessageService messageService;

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(MessengerSocket.class);
    private final static CloseReason closeReason = new CloseReason(1000, "Invalid token");
    public static final String AUTHORIZATION = "Authorization";

    private final Set<ClientConnection> connections = new ConcurrentHashSet<>();


    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        String token = connection.handshakeRequest().header(AUTHORIZATION);
        log.debug("Checking token {}", token);

        if (token == null || !token.startsWith("Bearer")) {
            connection.close(closeReason);
            log.warn("Token NOT accepted");
            return;
        }

        token = token.substring(7);
        Optional<ClientData> clientData = jwtValidator.validate(token);

        if (clientData.isPresent()) {
            registerClient(clientData.get(), connection);
            log.debug("Token accepted");
        } else {
            connection.close(closeReason);
            log.warn("Token NOT accepted");
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        log.debug("Client disconnected: {}", connection);
        connections.remove(connection);
    }


    @OnTextMessage
    public Uni<Void> onMessage(String message) {
        try {
            JsonObject json = new JsonObject(message);
            if (json.containsKey("ackId")) {
                String ackId = json.getString("ackId");

                return Uni.createFrom().item(ackId)
                        .onItem().transformToUni(messageService::removeAcknowledgedMessage)
                        .onItem().invoke(deleted -> log.debug("Deleted message {} on ACK", ackId)
                        ).replaceWithVoid();
            }
        } catch (Exception e) {
            log.error("Invalid message format: {}", message, e);
        }
        return Uni.createFrom().voidItem();
    }


    public Uni<Void> sendToClient(Message msg) {
        log.debug("Sending message " + msg);

        List<Uni<Void>> tasks = connections.stream()
                .filter(clientConnection -> clientConnection.getId().equals(msg.getClientId()))
                .map(conn -> sendWithPayload(conn.getWsConnection(), msg))
                .collect(Collectors.toList());

        if (tasks.isEmpty()) {
            log.debug("No connection found");
            return Uni.createFrom().voidItem();
        }


        return Uni.combine().all().unis(tasks).discardItems();
    }


    private void registerClient(ClientData clientData, WebSocketConnection connection) {
        ClientConnection clientConnection = ClientConnection.builder()
                .id(clientData.getClientId())
                .clientName(clientData.getClientName())
                .wsConnection(connection)
                .build();
        connections.add(clientConnection);
    }

    public Uni<Void> sendWithPayload(WebSocketConnection conn, Message msg) {
        log.debug("Sending message to websocket" + msg);

        String payload = new JsonObject()
                .put("id", msg.getId())
                .put("content", msg.getContent())
                .encode();

        if (conn.isOpen()) {
            return conn.sendText(payload)
                    .onFailure(HttpClosedException.class).recoverWithUni(err -> {
                        log.error("WebSocket closed before send: {}", conn.id());
                        connections.remove(conn);
                        return Uni.createFrom().voidItem();
                    })
                    .onFailure().invoke(err -> {
                        log.error("Failed to send to {}", conn, err);
                    })
                    .replaceWithVoid();
        }

        log.debug("Sending message to websocket failed: connection closed");
        return Uni.createFrom().voidItem();
    }

}
