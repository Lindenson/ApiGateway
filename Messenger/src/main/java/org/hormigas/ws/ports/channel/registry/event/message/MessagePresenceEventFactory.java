package org.hormigas.ws.ports.channel.registry.event.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;
import org.hormigas.ws.ports.channel.registry.dto.ClientData;
import org.hormigas.ws.ports.channel.registry.event.PresenceEventFactory;

import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
public class MessagePresenceEventFactory implements PresenceEventFactory {

    private final ObjectMapper objectMapper;

    @Override
    public Message createInitMessage(List<ClientData> clients, String recipientId) throws Exception {
        String body = objectMapper.writeValueAsString(clients);
        return baseMessage(MessageType.PRESENT_INIT, body, recipientId);
    }

    @Override
    public Message createJoinMessage(ClientData clientData) throws Exception {
        String body = objectMapper.writeValueAsString(clientData);
        return baseMessage(MessageType.PRESENT_JOIN, body, BROADCAST);
    }

    @Override
    public Message createLeaveMessage(ClientData clientData) throws Exception {
        String body = objectMapper.writeValueAsString(clientData);
        return baseMessage(MessageType.PRESENT_LEAVE, body, BROADCAST);
    }

    private Message baseMessage(MessageType type, String body, String recipientId) {
        return Message.builder()
                .type(type)
                .payload(Message.Payload.builder().kind("presence").body(body).build())
                .recipientId(recipientId)
                .senderId("server")
                .build();
    }
}
