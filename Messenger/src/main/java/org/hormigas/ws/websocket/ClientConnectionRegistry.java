package org.hormigas.ws.websocket;

import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.hormigas.ws.security.dto.ClientData;
import org.hormigas.ws.websocket.dto.ClientConnection;
import org.slf4j.LoggerFactory;

import java.util.Set;

@ApplicationScoped
@Getter
public class ClientConnectionRegistry {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ClientConnectionRegistry.class);
    private final Set<ClientConnection> connections = new ConcurrentHashSet<>();


    public void deregisterConnection(WebSocketConnection connection) {
        log.debug("Client disconnected: {}", connection);
        var sample = ClientConnection.builder().wsConnection(connection).build();
        connections.remove(sample);
    }


    public void registerClient(ClientData clientData, WebSocketConnection connection) {
        ClientConnection clientConnection = ClientConnection.builder()
                .id(clientData.getClientId())
                .clientName(clientData.getClientName())
                .wsConnection(connection)
                .build();
        log.debug("Client connected: {}", connection);
        connections.add(clientConnection);
    }

}
