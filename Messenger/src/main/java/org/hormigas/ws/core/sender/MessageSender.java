package org.hormigas.ws.core.services.sender;


import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.domain.MessagePayload;

public interface MessageSender {
    Uni<MessagePayload> send(MessagePayload message);
}
