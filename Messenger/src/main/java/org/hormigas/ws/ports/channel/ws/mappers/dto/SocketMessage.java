package org.hormigas.ws.ports.channel.ws.mappers.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SocketMessage {
    private String id;
    private String content;
}
