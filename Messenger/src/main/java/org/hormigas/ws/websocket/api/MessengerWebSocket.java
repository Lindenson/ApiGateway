package org.hormigas.ws.websocket.api;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domen.Message;

public interface MessengerWebSocket<T> {
    Uni<Boolean> sendToClient(T msg);
}
