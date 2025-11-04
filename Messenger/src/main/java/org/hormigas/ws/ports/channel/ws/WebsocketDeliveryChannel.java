package org.hormigas.ws.ports.channel.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpClosedException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.channel.DeliveryChannel;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.presense.ClientsRegistry;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.presense.inmemory.LocalRegistry;
import org.hormigas.ws.ports.channel.validator.ChannelValidator;
import org.hormigas.ws.ports.channel.ws.filter.ChannelFilter;
import org.hormigas.ws.ports.channel.ws.publisher.IncomingPublisher;
import org.hormigas.ws.ports.channel.ws.security.dto.ClientData;
import org.hormigas.ws.ports.channel.ws.utils.WebSocketUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hormigas.ws.core.router.stage.StageStatus.*;

@Slf4j
@WebSocket(path = "/ws")
@ApplicationScoped
public class WebsocketDeliveryChannel implements DeliveryChannel<Message> {

    @Inject
    IncomingPublisher incomingPublisher;

    @Inject
    WebSocketUtils webSocketUtils;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ChannelValidator<Message> channelValidator;

    @Inject
    ChannelFilter<Message, WebSocketConnection> channelFilter;
    
    ClientsRegistry<WebSocketConnection> connectionRegistry;

    @PostConstruct
    void init() {
        connectionRegistry = new LocalRegistry<>(meterRegistry);
    }


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
    public Uni<Void> onMessage(String rawJson, WebSocketConnection conn) {
        log.debug("Receiving message {}", rawJson);
        log.error("!!Connected {}", connectionRegistry.countAllClients());
        try {
            ClientSession<WebSocketConnection> clientSession = connectionRegistry.getClientSessionByConnection(conn);
            if (clientSession == null) {
                log.warn("Received message from unregistered connection: {}", conn.id());
                return Uni.createFrom().voidItem();
            }
            Message message = objectMapper.readValue(rawJson, Message.class);
            if (!channelValidator.valid(message)) {
                log.warn("Message didn't pass validation {}", message.getMessageId());
                return Uni.createFrom().voidItem();
            }
            if (!channelFilter.filter(message, clientSession)) {
                return Uni.createFrom().voidItem();
            }
            incomingPublisher.publish(message);
        } catch (Exception e) {
            log.error("Invalid message format: {}", rawJson, e);
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<StageStatus> deliver(Message msg) {
        log.debug("Sending message {}", msg);
        List<Uni<Void>> sessions = connectionRegistry.streamByClientId(msg.getRecipientId())
                .map(conn -> sendWithPayload(conn.getSession(), msg))
                .collect(Collectors.toList());

        if (sessions.isEmpty()) {
            log.warn("No connection found for id {}", msg.getRecipientId());
            return Uni.createFrom().item(SKIPPED);
        }

        return Uni.combine().all().unis(sessions).discardItems().replaceWith(SUCCESS)
                .onItem().invoke(() -> log.debug("Sent message {}", msg.getMessageId()))
                .onFailure().recoverWithItem(error -> {
                    log.error("Error while sending message", error);
                    return FAILED;
                });
    }

    public Uni<Void> sendWithPayload(WebSocketConnection conn, Message msg) {
        if (conn.isOpen()) {
            return webSocketUtils.encodeMessage(msg).map(em ->
                            conn.sendText(em)
                                    .onFailure(HttpClosedException.class).recoverWithUni(err -> {
                                        log.warn("WebSocket closed before send: {}", conn.id());
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
