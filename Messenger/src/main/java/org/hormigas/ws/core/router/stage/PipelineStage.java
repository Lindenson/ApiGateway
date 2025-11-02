package org.hormigas.ws.core.router.stage;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.router.context.MessageContext;

public interface RouteStage<T> {
    Uni<T> apply(T ctx);
}
