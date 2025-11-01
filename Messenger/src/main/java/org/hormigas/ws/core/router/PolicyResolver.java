package org.hormigas.ws.core.router;

import org.hormigas.ws.domain.MessagePayload;

public interface PolicyResolver {
    MessageRouter.RoutePolicy resolvePolicy(MessagePayload message);
}
