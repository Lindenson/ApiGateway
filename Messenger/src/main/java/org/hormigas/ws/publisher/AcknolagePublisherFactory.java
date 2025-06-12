package org.hormigas.ws.publisher;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.hormigas.ws.publisher.api.PublisherFactory;
import org.hormigas.ws.publisher.api.PublisherMetrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class AcknolagePublisherFactory implements PublisherFactory<String, PublisherMetrics, Uni<Long>> {

    private AtomicReference<MultiEmitter<? super String>> emitter;
    private Function<String, Uni<Long>> processor;
    private AtomicInteger queueSizeContainer;
    private PublisherMetrics metrics;

    @Override
    public AcknolagePublisherFactory withEmitter(AtomicReference<MultiEmitter<? super String>> emitter) {
        this.emitter = emitter;
        return this;
    }

    @Override
    public AcknolagePublisherFactory withProcessor(Function<String, Uni<Long>> processor) {
        this.processor = processor;
        return this;
    }

    @Override
    public AcknolagePublisherFactory withMetrics(PublisherMetrics metrics) {
        this.metrics = metrics;
        return this;
    }

    @Override
    public AcknolagePublisherFactory withQueueSizeCounter(AtomicInteger queueSizeContainer) {
        this.queueSizeContainer = queueSizeContainer;
        return this;
    }

    @Override
    public Multi<Void> build() {
        if (processor == null || queueSizeContainer == null || emitter == null || metrics == null) {
            throw new IllegalStateException("Dependencies not set!");
        }

        return Multi.createFrom().<String>emitter(em -> emitter.set(em), BackPressureStrategy.BUFFER)
                .onOverflow().bufferUnconditionally()
                .onItem().transformToUniAndConcatenate(msg -> processor.apply(msg)
                        .onItem().invoke(removed -> {
                            if (removed > 0) {
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
