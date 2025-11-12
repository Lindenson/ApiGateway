package org.hormigas.ws.ports.presence;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.session.ClientData;

import java.util.List;

public interface PresenceManager {
    Uni<Void> addClient(ClientData user);
    Uni<Void> removeClient(String userId);
    Uni<List<ClientData>> allPresent();
}
