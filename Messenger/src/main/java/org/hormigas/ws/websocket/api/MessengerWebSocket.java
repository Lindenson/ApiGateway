package org.hormigas.ws.websocket.api;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domen.Message;

public interface MessengerWebSocket {
    Uni<Void> sendToClient(Message msg);
}
