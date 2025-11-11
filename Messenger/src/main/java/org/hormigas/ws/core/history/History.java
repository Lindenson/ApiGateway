package org.hormigas.ws.core.history;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.Message;

import java.util.List;

public interface History<T> {
    Uni<List<T>> getMessagesForClient(String clientId);
    Uni<List<T>> getMessagesFromClient(String clientId);
    Uni<List<T>> getAllMessagesByClient(String clientId);
    void addMessage(String clientId, Message message);
}
