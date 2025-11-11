package org.hormigas.ws.ports.channel.registry.event;

import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.registry.dto.ClientData;

import java.util.List;

public interface PresenceEventFactory {
    String BROADCAST = "BROADCAST";
    Message createInitMessage(List<ClientData> clients, String recipientId) throws Exception;
    Message createJoinMessage(ClientData clientData) throws Exception;
    Message createLeaveMessage(ClientData clientData) throws Exception;
}
