package org.hormigas.ws.websocket.dto;

import io.quarkus.websockets.next.WebSocketConnection;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
public class ClientConnection {
    private String id;
    private String clientName;

    @EqualsAndHashCode.Include
    private WebSocketConnection wsConnection;
}
