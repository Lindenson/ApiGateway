package org.hormigas.ws.core.router;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.domain.MessageEnvelope;


public interface PipelineRouter<T> {
    Uni<MessageEnvelope<T>> route(T message);
}
