package org.hormigas.ws.ports.channel.presense.inmemory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.credits.lazy.LazyCreditsBuket;
import org.hormigas.ws.ports.channel.presense.ClientsRegistry;
import org.hormigas.ws.ports.channel.presense.dto.ClientData;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.presense.strategy.CleanupStrategy;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class LocalRegistry implements ClientsRegistry<WebSocketConnection> {

    private static final int MAX_CREDITS = 1500;
    private static final double REFILL_RATE = 800;

    private final MeterRegistry meterRegistry;
    private final CleanupStrategy<WebSocketConnection> cleanupStrategy;

    private final ConcurrentMap<WebSocketConnection, ClientSession<WebSocketConnection>> connectionIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<WebSocketConnection>> clientIndex = new ConcurrentHashMap<>();

    private Gauge gauge;

    @PostConstruct
    void init() {
        this.gauge = Gauge.builder("websocket_clients_registered", this, LocalRegistry::size)
                .description("Number of currently active WebSocket client connections")
                .register(meterRegistry);
    }

    @Override
    public void register(@Nonnull ClientData clientData, @Nonnull WebSocketConnection connection) {
        Objects.requireNonNull(clientData.id(), "Client id must not be null");
        Objects.requireNonNull(clientData.name(), "Client name must not be null");

        deregister(connection);

        var clientSession = ClientSession.<WebSocketConnection>builder()
                .id(clientData.id())
                .name(clientData.name())
                .session(connection)
                .credits(new LazyCreditsBuket(MAX_CREDITS, REFILL_RATE))
                .build();

        connectionIndex.put(connection, clientSession);
        clientIndex.computeIfAbsent(clientData.id(), k -> ConcurrentHashMap.newKeySet()).add(connection);

        log.debug("Client connected: {} ({})", clientData.name(), clientData.id());
    }

    @Override
    @Nullable
    public ClientSession<WebSocketConnection> deregister(@Nonnull WebSocketConnection connection) {
        var removed = connectionIndex.remove(connection);
        if (removed == null) return null;

        clientIndex.computeIfPresent(removed.getId(), (id, connections) -> {
            connections.remove(connection);
            return connections.isEmpty() ? null : connections;
        });

        log.debug("Client disconnected: {}", removed.getId());
        return removed;
    }

    @Override
    @Nonnull
    public Stream<ClientSession<WebSocketConnection>> streamSessionsByClientId(@Nonnull String id) {
        var connections = clientIndex.get(id);
        if (connections == null || connections.isEmpty()) return Stream.empty();
        return connections.stream()
                .map(connectionIndex::get)
                .filter(Objects::nonNull);
    }

    @Override
    @Nonnull
    public Stream<ClientSession<WebSocketConnection>> streamAllOnlineClients() {
        return connectionIndex.values().stream();
    }

    @Override
    @Nullable
    public ClientSession<WebSocketConnection> getSessionByConnection(@Nonnull WebSocketConnection connection) {
        ClientSession<WebSocketConnection> clientSession = connectionIndex.get(connection);
        if (clientSession != null) touch(clientSession);
        return clientSession;
    }

    @Override
    public long countAllClients() {
        return connectionIndex.size();
    }

    @Override
    @Nonnull
    public List<ClientData> getAllOnlineClients() {
        return connectionIndex.values().stream()
                .collect(Collectors.toMap(ClientSession::getId, s ->
                        s, (a, b) -> a))
                .values().stream()
                .map(s -> new ClientData(s.getId(), s.getName()))
                .toList();
    }

    @Override
    public boolean isClientConnected(@Nonnull String clientId) {
        var set = clientIndex.get(clientId);
        return set != null && !set.isEmpty();
    }

    @Override
    public void touch(@Nonnull ClientSession<WebSocketConnection> clientSession) {
        clientSession.updateActivity();
        log.debug("Touch connection {} prolonged", clientSession);
    }

    @Override
    public void cleanUnused(WebSocketConnection connection) {
        cleanupStrategy.clean(
                connection,
                connectionIndex.keySet(),
                this::deregister
        );
    }

    private int size() {
        return connectionIndex.size();
    }
}
