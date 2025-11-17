package org.hormigas.ws.ports.channel;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.stage.StageStatus;

public interface DeliveryChannel<T> {
    Uni<StageStatus> deliver(T message);
}
