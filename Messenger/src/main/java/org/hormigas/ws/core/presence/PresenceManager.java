package org.hormigas.ws.core.presence;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.presence.dto.Member;

import java.util.List;

public interface PresenceManager {
    Uni<Void> addClient(Member user);
    Uni<Void> removeClient(String userId);
    Uni<List<Member>> allPresent();
    boolean isLocallyPresent(String userId);
}
