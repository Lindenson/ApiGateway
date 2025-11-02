package org.hormigas.ws.ports.channel;

import io.smallrye.mutiny.Uni;

public interface DeliveryChannel<T> {
    Uni<Boolean> deliver(T channel);
}
