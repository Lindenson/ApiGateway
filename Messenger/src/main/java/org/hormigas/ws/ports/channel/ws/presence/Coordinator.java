package org.hormigas.ws.ports.channel.ws.presence;

import io.quarkus.websockets.next.WebSocketConnection;
import org.hormigas.ws.ports.channel.registry.dto.ClientData;

public interface Coordinator<T> {
    void handleJoin(ClientData newClient, T connection);
    void handleLeave(WebSocketConnection connection, long timestamp);
    void handleAbsent(String clientId, long timestamp);
}
