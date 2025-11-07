package org.hormigas.ws.ports.channel.ws.filter.policy;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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
    public boolean filter(@Nullable Message message, @Nonnull ClientSession<WebSocketConnection> clientSession) {
        if (message == null) {
            log.error("Message is null for a client {}", clientSession.getId());
            return false;
        }
        if (creditPolicy.test(message) && !clientSession.tryConsumeCredits()) {
            log.warn("Rate limit exceeded for client {}", clientSession.getId());
            return false;
        }
        return true;
    }
}
