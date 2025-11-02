package org.hormigas.ws.core.router;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.router.context.MessageContext;


public interface MessageRouter<T> {
    Uni<MessageContext<T>> route(T message);
}
