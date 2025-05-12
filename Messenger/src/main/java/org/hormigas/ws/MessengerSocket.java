package org.hormigas.ws;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.*;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.security.JwtValidator;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.Set;


@WebSocket(path = "/ws")
@ApplicationScoped
public class MessengerSocket {

    @Inject JwtValidator jwtValidator;

    private static final Random RANDOM = new Random();
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(MessengerSocket.class);

    private final Set<WebSocketConnection> connections = new ConcurrentHashSet<>();
    private final static CloseReason closeReason = new CloseReason(1000, "Invalid token");
    public static final String AUTHORIZATION = "Authorization";


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
        if (validateToken(token)) {
            connections.add(connection);
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


    //Dummy messages
    @Scheduled(every = "5s")
    public void broadcastMessage() {
        String message = "Привет " + RANDOM.nextInt(1000);
        for (Connection connection : connections) {
            connection.sendTextAndAwait(message);
        }
        log.debug("Broadcasted: {}", message);
    }

    private boolean validateToken(String token) {
        return jwtValidator.validate(token);
    }
}
