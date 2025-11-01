package org.hormigas.ws.core.router;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.MessagePayload;


public interface MessageRouter {
    enum RoutePolicy {PERSISTENT_OUT, CACHED_OUT, DIRECT_OUT, CACHED_ACK, PERSISTENT_ACK}
    Uni<Boolean> route(MessagePayload message);
}
