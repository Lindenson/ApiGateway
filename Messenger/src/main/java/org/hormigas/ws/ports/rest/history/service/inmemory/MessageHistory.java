package org.hormigas.ws.ports.rest.history.service.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.rest.history.service.History;

import java.util.List;

@ApplicationScoped
public class MessageHistory implements History<Message> {
    @Override
    public Uni<List<Message>> getMessagesForClient(String clientId) {
        return Uni.createFrom().item(List.of());
    }
}
