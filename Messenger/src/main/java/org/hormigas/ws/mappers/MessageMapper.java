package org.hormigas.ws.mappers;

import io.smallrye.common.constraint.NotNull;
import lombok.experimental.UtilityClass;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.mappers.dto.SocketMessage;

@UtilityClass
public class MessageMapper {

    public static SocketMessage map(@NotNull Message message) {
        return SocketMessage.builder().content(message.getContent()).id(message.getId().toString()).build();
    }
}
