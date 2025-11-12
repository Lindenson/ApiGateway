package org.hormigas.ws.ports.notifier;

import io.quarkus.websockets.next.WebSocketConnection;
import org.hormigas.ws.domain.session.ClientData;

public interface Notifier<T> {
    void notifyJoin(ClientData newClient, T connection);
    void notifyLeave(WebSocketConnection connection, long timestamp);
    void notifyAbsent(String clientId, long timestamp);
}
