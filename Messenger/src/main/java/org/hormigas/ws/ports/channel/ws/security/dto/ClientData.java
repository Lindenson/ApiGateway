package org.hormigas.ws.ports.channel.ws.security.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientData {
    private String clientId;
    private String clientName;
}
