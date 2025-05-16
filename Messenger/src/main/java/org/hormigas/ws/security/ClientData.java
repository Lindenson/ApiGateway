package org.hormigas.ws.security;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientData {
    private String clientId;
    private String clientName;
}
