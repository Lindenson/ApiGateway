package org.hormigas.ws.ports.rest.history;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.presense.dto.ClientData;
import org.hormigas.ws.ports.channel.ws.security.JwtValidator;
import org.hormigas.ws.ports.rest.history.service.History;

import java.util.Optional;

@Path("/api/history")
@Produces(MediaType.APPLICATION_JSON)
public class MessageHistoryResource {

    @Inject
    History<Message> messageHistory;

    @Inject
    JwtValidator jwtValidator;

    @GET
    public Uni<Response> getByClientToken(@HeaderParam("Authorization") String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        String token = authorization.substring(7);

        Optional<ClientData> clientOpt = jwtValidator.validate(token);
        if (clientOpt.isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        String clientId = clientOpt.get().id();

        return messageHistory.getMessagesForClient(clientId)
                .onItem().transform(messages -> Response.ok(messages).build());
    }
}

