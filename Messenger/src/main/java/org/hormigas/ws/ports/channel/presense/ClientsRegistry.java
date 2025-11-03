package org.hormigas.ws.ports.channel.presense;

import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.ws.security.dto.ClientData;

import java.util.stream.Stream;

public interface ClientsRegistry<T> {
    void deregisterConnection(T connection);
    void registerClient(ClientData clientData, T connection);
    Stream<ClientSession<T>> streamByClientId(String id);
    ClientSession<T> getClientSessionByConnection(T connection);
}
