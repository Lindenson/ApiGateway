package org.hormigas.ws.websocket;

import io.quarkus.websockets.next.WebSocketConnection;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientConnection {
    private String id;
    private String clientName;
    private WebSocketConnection wsConnection;
}
