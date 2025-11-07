package org.hormigas.ws.ports.channel.presense.inmemory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.credits.lazy.LazyCreditsBuket;
import org.hormigas.ws.ports.channel.presense.ClientsRegistry;
import org.hormigas.ws.ports.channel.presense.dto.ClientData;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class LocalRegistry implements ClientsRegistry<WebSocketConnection> {

    private final int MAX_CREDITS = 1500;
    private final double REFILL_RATE = 800;

    private Gauge gauge;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<WebSocketConnection, ClientSession<WebSocketConnection>> sessionIndex = new ConcurrentHashMap<>();


    @PostConstruct
    public void init() {
        this.gauge = Gauge.builder("websocket_clients_registered", this, LocalRegistry::size)
                .description("Number of currently active WebSocket client connections")
                .register(meterRegistry);
    }


    @Override
    public ClientSession<WebSocketConnection> deregister(WebSocketConnection connection) {
        ClientSession<WebSocketConnection> removed = sessionIndex.remove(connection);
        if (removed != null) {
            log.debug("Client disconnected: {}", removed.getId());
        }
        return removed;
    }

    @Override
    public void register(ClientData clientData, WebSocketConnection connection) {
        deregister(connection);
        var client = ClientSession.<WebSocketConnection>builder()
                .id(clientData.id())
                .name(clientData.name())
                .session(connection)
                .credits(new LazyCreditsBuket(MAX_CREDITS, REFILL_RATE))
                .build();
        sessionIndex.put(connection, client);
        log.debug("Client connected: {}", clientData.id());
    }


    @Override
    public Stream<ClientSession<WebSocketConnection>> streamSessionsByClientId(String id) {
        return sessionIndex.values().stream().filter(client -> client.getId().equals(id));
    }

    @Override
    public Stream<ClientSession<WebSocketConnection>> streamAllOnlineClients() {
        return sessionIndex.values().stream();
    }

    @Override
    public ClientSession<WebSocketConnection> getSessionByConnection(WebSocketConnection connection) {
        return sessionIndex.get(connection);
    }

    @Override
    public long countAllClients() {
        return sessionIndex.size();
    }

    @Override
    public List<ClientData> getAllOnlineClients() {
        return sessionIndex.values().stream()
                .map(s -> new ClientData(s.getId(), s.getName()))
                .toList();
    }

    private int size() {
        return sessionIndex.size();
    }
}
