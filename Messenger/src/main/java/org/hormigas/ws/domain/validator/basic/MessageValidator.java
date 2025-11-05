package org.hormigas.ws.domain.validator.basic;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;
import org.hormigas.ws.domain.validator.Validator;

@Slf4j
@ApplicationScoped
public class MessageValidator implements Validator<Message> {

    @Override
    public boolean valid(Message message) {
        if (message == null) {
            log.warn("Received null message");
            return false;
        }

        if (isBlank(message.getMessageId())) {
            log.warn("Invalid message: missing messageId");
            return false;
        }
        if (message.getType() == null) {
            log.warn("Invalid message: missing type for messageId {}", message.getMessageId());
            return false;
        }
        if (isBlank(message.getSenderId())) {
            log.warn("Invalid message: missing senderId for messageId {}", message.getMessageId());
            return false;
        }
        if (isBlank(message.getRecipientId())) {
            log.warn("Invalid message: missing recipientId for messageId {}", message.getMessageId());
            return false;
        }

        if (isAckType(message.getType()) && isBlank(message.getCorrelationId())) {
            log.warn("CorrelationId is required for ACK message {}", message.getMessageId());
            return false;
        }

        if (message.getType() == MessageType.CHAT_IN) {
            if (message.getPayload() == null) {
                log.warn("Missing payload for CHAT_IN message {}", message.getMessageId());
                return false;
            }
            if (isBlank(message.getPayload().getKind()) || isBlank(message.getPayload().getBody())) {
                log.warn("Payload(kind, body) is required for CHAT_IN message {}", message.getMessageId());
                return false;
            }
        }

        if (message.getSenderId().equals(message.getRecipientId())) {
            log.warn("Message cannot be sent to yourself {}", message.getMessageId());
            return false;
        }

        return true;
    }

    private boolean isAckType(MessageType type) {
        return type == MessageType.CHAT_ACK || type == MessageType.SIGNAL_ACK;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}


