package org.hormigas.ws.ports.rest.presence.service;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.presence.dto.Member;

import java.util.List;

public interface Presence {
    Uni<List<Member>> getPresence();
}
