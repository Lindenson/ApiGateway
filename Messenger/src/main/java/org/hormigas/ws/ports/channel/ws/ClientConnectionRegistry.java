package org.hormigas.ws.ports.channel.ws;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.hormigas.ws.ports.channel.ws.dto.ClientConnection;
import org.hormigas.ws.security.dto.ClientData;
import org.slf4j.LoggerFactory;

import java.util.Set;

@ApplicationScoped
@Getter
public class ClientConnectionRegistry {

    @Inject
    MeterRegistry meterRegistry;

    private Gauge registeredClientsCounter;

    @PostConstruct
    void init() {
        registeredClientsCounter = Gauge.builder("websocket_clients_registered", connections, Set::size)
                .description("Current size of the message queue")
                .register(meterRegistry);
    }

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
