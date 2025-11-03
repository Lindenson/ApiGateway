package org.hormigas.ws.core.channel;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.router.stage.StageStatus;

public interface DeliveryChannel<T> {
    Uni<StageStatus> deliver(T channel);
}
