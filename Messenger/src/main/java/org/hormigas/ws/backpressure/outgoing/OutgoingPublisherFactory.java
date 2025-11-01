package org.hormigas.ws.backpressure;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.hormigas.ws.backpressure.api.PublisherFactory;
import org.hormigas.ws.domain.MessagePayload;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


public class OutgoingPublisherFactory implements PublisherFactory<MessagePayload, OutgoingPublisherMetrics, Uni<Boolean>, MessagePayload> {
    private Function<MessagePayload, Uni<Boolean>> processor;
    private OutgoingPublisherMetrics metrics;
    private AtomicInteger queueSizeContainer;
    private AtomicReference<MultiEmitter<? super MessagePayload>> emitter;

    public OutgoingPublisherFactory withProcessor(Function<MessagePayload, Uni<Boolean>> processor) {
        this.processor = processor;
        return this;
    }

    @Override
    public OutgoingPublisherFactory withMetrics(OutgoingPublisherMetrics metrics) {
        this.metrics = metrics;
        return this;
    }

    @Override
    public OutgoingPublisherFactory withQueueSizeCounter(AtomicInteger queueSizeContainer) {
        this.queueSizeContainer = queueSizeContainer;
        return this;
    }

    @Override
    public OutgoingPublisherFactory withEmitter(AtomicReference<MultiEmitter<? super MessagePayload>> emitter) {
        this.emitter = emitter;
        return this;
    }

    @Override
    public Multi<Void> build() {
        if (processor == null || metrics == null || queueSizeContainer == null || emitter == null) {
            throw new IllegalStateException("Dependencies not set!");
        }

        return Multi.createFrom().<MessagePayload>emitter(em -> emitter.set(em), BackPressureStrategy.BUFFER)
                .onOverflow().bufferUnconditionally()
                .onItem().transformToUniAndConcatenate(msg -> {
                    long start = System.nanoTime();
                    return processor.apply(msg)
                            .onItem().invoke(sent -> {
                                if (sent) {
                                    metrics.recordProcessingTime(System.nanoTime() - start);
                                    metrics.recordDone();
                                }
                            })
                            .onFailure().invoke(failure -> {
                                metrics.recordFailed();
                                Log.error("Failed to publish message", failure);
                            })
                            .replaceWithVoid()
                            .eventually(() -> metrics.setQueueSize(queueSizeContainer.decrementAndGet()));
                });
    }
}
