package org.hormigas.ws.ports.channel;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.MessagePayload;

public class WebsocketDeliveryChannel implements DeliveryChannel {
    @Override
    public Uni<Void> deliver(MessagePayload channel) {
        return null;
    }
}
