package org.hormigas.ws.ports.channel.ws.filter.policy;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.credits.CreditPolicy;
import org.hormigas.ws.credits.policy.InboundMessageCreditPolicy;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.ws.filter.ChannelFilter;

@Slf4j
@ApplicationScoped
public class PolicyFilters implements ChannelFilter<Message, WebSocketConnection> {

    CreditPolicy<Message> creditPolicy = new InboundMessageCreditPolicy();

    @Override
    public boolean filter(Message message, ClientSession<WebSocketConnection> clientSession) {
        if (creditPolicy.test(message) && !clientSession.tryConsumeCredits()) {
            log.warn("Rate limit exceeded for client {}", clientSession.getClientId());
            return false;
        }
        return true;
    }
}
