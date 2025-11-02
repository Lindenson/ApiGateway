package org.hormigas.ws.core.router;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.context.MessageContext;


public interface PipelineRouter<T> {
    Uni<MessageContext<T>> route(T message);
}
