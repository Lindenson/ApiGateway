package org.hormigas.ws.ports.rest.presence.service.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.core.presence.PresenceManager;
import org.hormigas.ws.ports.rest.presence.service.Presence;
import org.hormigas.ws.core.presence.dto.Member;

import java.util.List;

@ApplicationScoped
public class PresenceManagerInMemory implements Presence {

    @Inject
    PresenceManager presenceManager;

    @Override
    public Uni<List<Member>> getPresence() {
        return presenceManager.allPresent();
    }
}
