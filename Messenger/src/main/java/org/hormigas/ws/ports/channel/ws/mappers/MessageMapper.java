package org.hormigas.ws.ports.channel.ws.mappers;

import io.smallrye.common.constraint.NotNull;
import lombok.experimental.UtilityClass;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.ws.mappers.dto.SocketMessage;

@UtilityClass
public class MessageMapper {

    public static SocketMessage map(@NotNull Message message) {
        return SocketMessage.builder().content(message.getPayload().getBody()).id(message.getMessageId()).build();
    }
}
