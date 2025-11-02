package org.hormigas.ws.backpressure.incomming;

import io.smallrye.mutiny.Uni;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.backpressure.PublisherMetrics;
import org.hormigas.ws.backpressure.factory.PublisherFactoryAbstract;
import org.hormigas.ws.domain.MessagePayload;

@Slf4j
public class IncommingPublisherFactory extends PublisherFactoryAbstract<MessagePayload, PublisherMetrics, Uni<Boolean>, MessagePayload> {


    private Uni<Void> p(MessagePayload msg) {
        return getSink().apply(msg)
                .onItem().invoke(processed -> {
                    if (processed) {
                        getMetrics().recordDone();
                        log.debug("Incoming message processed");
                    }
                })
                .onFailure().invoke(failure -> {
                    getMetrics().recordFailed();
                    log.error("Failed to processed message", failure);
                })
                .replaceWithVoid()
                .eventually(() -> getMetrics().setQueueSize(getQueueSizeContainer().decrementAndGet()));
    }

    @Override
    protected Uni<Void> publishMessage(MessagePayload payload) {
        return null;
    }
}
