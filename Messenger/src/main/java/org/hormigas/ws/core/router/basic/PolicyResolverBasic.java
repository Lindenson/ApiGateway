package org.hormigas.ws.core.router;

import org.hormigas.ws.domain.MessagePayload;

import static org.hormigas.ws.core.router.MessageRouter.RoutePolicy.*;

public class PolicyResolverDefault implements PolicyResolver {

    @Override
    public MessageRouter.RoutePolicy resolvePolicy(MessagePayload message) {
        return switch (message.getType()) {
            case ASK -> switch (message.getOrigin()) {
                case OUTBOX, CLIENT -> PERSISTENT_ACK;
                case SYSTEM -> CACHED_ACK;
            };
            case CHAT -> switch (message.getOrigin()) {
                case CLIENT -> PERSISTENT_OUT;
                case OUTBOX -> CACHED_OUT;
                default -> throw new IllegalStateException("Unexpected CHAT origin: " + message.getOrigin());
            };
            case SIGNAL -> CACHED_OUT;
            case SERVICE -> DIRECT_OUT;
        };
    }
}
