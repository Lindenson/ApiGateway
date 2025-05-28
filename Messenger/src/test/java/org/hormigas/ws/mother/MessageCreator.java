package org.hormigas.ws.mother;

import lombok.experimental.UtilityClass;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.domen.Status;

import java.time.LocalDateTime;
import java.util.UUID;

@UtilityClass
public class MessageCreator {

    public static final String HELLO_WORLD = "Hello World";

    public static Message getMessage() {
        Message message = new Message();
        UUID messageId = UUID.randomUUID();
        message.setId(messageId);
        UUID clientId = UUID.randomUUID();
        message.setClientId(clientId.toString());
        message.setStatus(Status.PENDING);
        message.setContent(HELLO_WORLD);
        message.setSendAt(LocalDateTime.now());
        return message;
    }

    public static Message getMessageMessageClientId(UUID id) {
        Message message = new Message();
        message.setId(id);
        message.setClientId(UUID.randomUUID().toString());
        message.setContent(HELLO_WORLD);
        message.setStatus(Status.PENDING);
        message.setSendAt(LocalDateTime.now());
        return message;
    }

}
