package org.hormigas.ws.ports.rest.presence;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.hormigas.ws.core.presence.dto.Member;
import org.hormigas.ws.ports.rest.presence.service.Presence;

import java.util.List;

@Path("/api/presence")
@Produces(MediaType.APPLICATION_JSON)
public class PresenceResource {

    @Inject
    Presence presenceService;

    @GET
    public Uni<List<Member>> getPresence() {
        return presenceService.getPresence();
    }
}
