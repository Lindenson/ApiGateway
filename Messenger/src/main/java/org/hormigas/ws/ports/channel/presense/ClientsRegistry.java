package org.hormigas.ws.ports.channel.presense;

import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.presense.dto.ClientData;

import java.util.List;
import java.util.stream.Stream;

public interface ClientsRegistry<T> {
    ClientSession<T>  deregister(T connection);
    void register(ClientData clientData, T connection);
    Stream<ClientSession<T>> streamSessionsByClientId(String id);
    Stream<ClientSession<T>> streamAllOnlineClients();
    ClientSession<T> getSessionByConnection(T connection);
    long countAllClients();
    List<ClientData> getAllOnlineClients();
    boolean isClientConnected(String clientId);
    void touch(ClientSession<T> clientSession);
    void cleanUnused(T connection);
}
