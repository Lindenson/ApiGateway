package org.hormigas.ws.backpressure;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.hormigas.ws.backpressure.api.PublisherFactory;
import org.hormigas.ws.backpressure.api.PublisherMetrics;
import org.hormigas.ws.domain.MessagePayload;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class IncommingPublisherFactory implements PublisherFactory<MessagePayload, PublisherMetrics, Uni<Boolean>, MessagePayload> {

    private AtomicReference<MultiEmitter<? super MessagePayload>> emitter;
    private Function<MessagePayload, Uni<Boolean>> processor;
    private AtomicInteger queueSizeContainer;
    private PublisherMetrics metrics;

    @Override
    public IncommingPublisherFactory withEmitter(AtomicReference<MultiEmitter<? super MessagePayload>> emitter) {
        this.emitter = emitter;
        return this;
    }

    @Override
    public IncommingPublisherFactory withProcessor(Function<MessagePayload, Uni<Boolean>> processor) {
        this.processor = processor;
        return this;
    }

    @Override
    public IncommingPublisherFactory withMetrics(PublisherMetrics metrics) {
        this.metrics = metrics;
        return this;
    }

    @Override
    public IncommingPublisherFactory withQueueSizeCounter(AtomicInteger queueSizeContainer) {
        this.queueSizeContainer = queueSizeContainer;
        return this;
    }

    @Override
    public Multi<Void> build() {
        if (processor == null || queueSizeContainer == null || emitter == null || metrics == null) {
            throw new IllegalStateException("Dependencies not set!");
        }

        return Multi.createFrom().<MessagePayload>emitter(em -> emitter.set(em), BackPressureStrategy.BUFFER)
                .onOverflow().bufferUnconditionally()
                .onItem().transformToUniAndConcatenate(msgs -> processor.apply(msgs)
                        .onItem().invoke(removed -> {
                            if (removed) {
                                metrics.recordDone();
                                Log.debug("Message removed after acknowledgment");
                            }
                        })
                        .onFailure().invoke(failure -> {
                            metrics.recordFailed();
                            Log.error("Failed to remove message", failure);
                        })
                        .replaceWithVoid()
                        .eventually(() -> metrics.setQueueSize(queueSizeContainer.decrementAndGet())));
    }
}
