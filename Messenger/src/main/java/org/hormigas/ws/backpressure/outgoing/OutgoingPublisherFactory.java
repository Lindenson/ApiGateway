package org.hormigas.ws.backpressure.outgoing;

import io.smallrye.mutiny.Uni;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.backpressure.factory.PublisherFactoryAbstract;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.domain.Message;

@Slf4j
public class OutgoingPublisherFactory extends PublisherFactoryAbstract<Message, OutgoingPublisherMetrics, Uni<MessageContext<Message>>> {

    @Override
    protected Uni<Void> publishMessage(Message msg) {
        long start = System.nanoTime();
        return getSink().apply(msg)
                .onItem().invoke(processed -> {
                    if (processed.isDone()) {
                        log.debug("Outgoing message processed");
                        getMetrics().recordProcessingTime(System.nanoTime() - start);
                        getMetrics().recordDone();
                    }
                })
                .onFailure().invoke(failure -> {
                    getMetrics().recordFailed();
                    log.error("Failed to process outgoing message", failure);
                })
                .replaceWithVoid()
                .eventually(() -> getMetrics().setQueueSize(getQueueSizeContainer().decrementAndGet()));
    }
}
