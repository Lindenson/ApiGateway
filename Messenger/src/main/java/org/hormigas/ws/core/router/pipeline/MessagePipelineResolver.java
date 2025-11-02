package org.hormigas.ws.core.router.pipeline;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.router.PolicyResolver;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;

import java.util.EnumMap;
import java.util.Map;

import static org.hormigas.ws.core.router.PolicyResolver.RoutePolicy.*;
import static org.hormigas.ws.domain.MessageType.*;


@Slf4j
@ApplicationScoped
public class MessagePolicyResolver implements PolicyResolver<Message, MessageType> {

    private final Map<MessageType, RoutePolicy> routingMatrix = new EnumMap<>(MessageType.class);

    public MessagePolicyResolver() {

        // ASK routing
        routingMatrix.put(SIGNAL_ASK, CACHED_ACK);
        routingMatrix.put(CHAT_ASK, PERSISTENT_ACK);

        // CHAT routing
        routingMatrix.put(CHAT_OUT, PERSISTENT_OUT);

        // SIGNAL routing
        routingMatrix.put(SIGNAL_OUT, CACHED_OUT);

        // SERVICE routing
        routingMatrix.put(SERVICE_OUT, DIRECT_OUT);
    }

    @Override
    public RoutePolicy resolvePolicy(Message message) {
        if (message == null || message.getType() == null) {
            throw new IllegalArgumentException("Message or its type/origin cannot be null");
        }

        RoutePolicy policy = routingMatrix
                .get(message.getType());

        if (policy == null) {
            log.warn("No routing policy defined for message type={}", message.getType());
            return DIRECT_OUT;
        }

        log.debug("Resolved policy {} for message type={}", policy, message.getType());
        return policy;
    }
}
