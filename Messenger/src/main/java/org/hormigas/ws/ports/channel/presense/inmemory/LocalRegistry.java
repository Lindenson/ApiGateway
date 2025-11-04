package org.hormigas.ws.ports.channel.presense.inmemory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.credits.lazy.LazyCreditsBuket;
import org.hormigas.ws.ports.channel.presense.ClientsRegistry;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.ws.security.dto.ClientData;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@Slf4j
public class LocalRegistry<T> implements ClientsRegistry<T> {

    private final int MAX_CREDITS = 1500;
    private final double REFILL_RATE = 800;

    private final Gauge gauge;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<T, ClientSession<T>> sessionIndex = new ConcurrentHashMap<>();


    public LocalRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.gauge = Gauge.builder("websocket_clients_registered", this, LocalRegistry::size)
                .description("Number of currently active WebSocket client connections")
                .register(meterRegistry);
    }


    @Override
    public void deregisterConnection(T connection) {
        ClientSession<T> removed = sessionIndex.remove(connection);
        if (removed != null) {
            log.debug("Client disconnected: {}", removed.getClientId());
        }
    }

    @Override
    public void registerClient(ClientData clientData, T connection) {
        deregisterConnection(connection);
        var client = ClientSession.<T>builder()
                .clientId(clientData.getClientId())
                .session(connection)
                .credits(new LazyCreditsBuket(MAX_CREDITS, REFILL_RATE))
                .build();
        sessionIndex.put(connection, client);
        log.debug("Client connected: {}", clientData.getClientId());
    }


    @Override
    public Stream<ClientSession<T>> streamByClientId(String id) {
        return sessionIndex.values().stream().filter(client -> client.getClientId().equals(id));
    }

    @Override
    public ClientSession<T> getClientSessionByConnection(T connection) {
        return sessionIndex.get(connection);
    }

    @Override
    public long countAllClients() {
        return sessionIndex.size();
    }

    private int size() {
        return sessionIndex.size();
    }
}
