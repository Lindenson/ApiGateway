package org.hormigas.ws.credits.policy;

import org.hormigas.ws.credits.CreditPolicy;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;

public class InboundMessageCreditPolicy implements CreditPolicy<Message> {
    @Override
    public boolean test(Message message) {
        return message.getType() != MessageType.CHAT_ACK && message.getType() != MessageType.SIGNAL_ACK;
    }
}
