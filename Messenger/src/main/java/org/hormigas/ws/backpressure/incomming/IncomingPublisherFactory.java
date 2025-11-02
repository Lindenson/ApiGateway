package org.hormigas.ws.backpressure.incomming;

import io.smallrye.mutiny.Uni;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.backpressure.PublisherMetrics;
import org.hormigas.ws.backpressure.factory.PublisherFactoryAbstract;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.domain.Message;

@Slf4j
public class IncomingPublisherFactory extends PublisherFactoryAbstract<Message, PublisherMetrics, Uni<MessageContext<Message>>> {

    @Override
    protected Uni<Void> publishMessage(Message msg) {
        return getSink().apply(msg)
                .onItem().invoke(processed -> {
                    if (processed.isDone()) {
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

}
