package org.hormigas.ws.domain.session;

public record ClientData(String id, String name, long connectedAt) {
    public ClientData(String id, String name){
        this(id, name, System.currentTimeMillis());
    }
}
