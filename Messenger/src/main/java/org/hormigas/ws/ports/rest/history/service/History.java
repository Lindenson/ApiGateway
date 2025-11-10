package org.hormigas.ws.ports.rest.history.service;

import io.smallrye.mutiny.Uni;

import java.util.List;

public interface History<T> {
    Uni<List<T>> getMessagesForClient(String clientId);
}
