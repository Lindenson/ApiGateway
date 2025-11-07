package org.hormigas.ws.domain.validator.inout;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;
import org.hormigas.ws.domain.validator.Validator;

import java.util.Objects;

import static org.hormigas.ws.domain.MessageType.*;

@Slf4j
@ApplicationScoped
public class InboundValidator implements Validator<Message> {

    @Override
    public boolean valid(Message message) {
        if (message == null) {
            log.warn("Received null message");
            return false;
        }

        if (message.getType() == null) {
            log.warn("Invalid message: missing type for messageId {}", message.getMessageId());
            return false;
        }

        if (!isInboundType(message.getType())) {
            log.warn("Invalid inbound message type: {}", message.getType());
            return false;
        }

        if (isBlank(message.getSenderId())) {
            log.warn("Missing senderId (type={}, messageId={})", message.getType(), message.getMessageId());
            return false;
        }

        if (isBlank(message.getRecipientId())) {
            log.warn("Missing recipientId (type={}, messageId={})", message.getType(), message.getMessageId());
            return false;
        }

        if (isAckType(message.getType())) {
            if (isBlank(message.getCorrelationId())) {
                log.warn("CorrelationId is required for ACK message {}", message.getMessageId());
                return false;
            }
        } else if (message.getType() == CHAT_IN) {
            if (message.getPayload() == null) {
                log.warn("Missing payload for CHAT_IN message {}", message.getMessageId());
                return false;
            }
            if (isBlank(message.getPayload().getKind()) || isBlank(message.getPayload().getBody())) {
                log.warn("Invalid payload for CHAT_IN message {}", message.getMessageId());
                return false;
            }
        }

        if (Objects.equals(message.getSenderId(), message.getRecipientId())) {
            log.warn("Message cannot be sent to yourself {}", message.getMessageId());
            return false;
        }

        if (message.getClientTimestamp() == 0) {
            log.warn("Message should have clientTimestamp {}", message.getMessageId());
            return false;
        }

        return true;
    }

    private boolean isAckType(MessageType type) {
        return type == CHAT_ACK || type == SIGNAL_ACK;
    }

    private boolean isInboundType(MessageType type) {
        return switch (type) {
            case CHAT_IN, CHAT_ACK, SIGNAL_ACK -> true;
            default -> false;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
