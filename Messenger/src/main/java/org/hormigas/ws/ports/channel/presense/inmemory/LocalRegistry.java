package org.hormigas.ws.ports.channel.presense.inmemory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.ports.channel.presense.ClientsRegistry;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.ws.security.dto.ClientData;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@Slf4j
public class LocalRegistry<T> implements ClientsRegistry<T> {

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
    public void deregisterConnection(T session) {
        ClientSession<T> removed = sessionIndex.remove(session);
        if (removed != null) {
            log.debug("Client disconnected: {}", removed.getClientId());
        }
    }

    @Override
    public void registerClient(ClientData clientData, T session) {
        deregisterConnection(session);
        sessionIndex.put(session, new ClientSession<>(clientData.getClientId(), session, 200, 20.0));
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

    private int size() {
        return sessionIndex.size();
    }
}
