package org.hormigas.ws.backpressure.factory;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import lombok.Getter;
import org.hormigas.ws.backpressure.PublisherFactory;
import org.hormigas.ws.backpressure.PublisherMetrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public abstract class PublisherFactoryAbstract<T, M extends PublisherMetrics, S> implements PublisherFactory<T, M, S> {

    @Getter
    private Function<T, S> sink;
    @Getter
    private M metrics;
    @Getter
    private AtomicInteger queueSizeContainer;


    private AtomicReference<MultiEmitter<? super T>> emitter;
    private PublisherFactory.Mode mode;

    public PublisherFactoryAbstract<T, M, S> withSink(Function<T, S> processor) {
        this.sink = processor;
        return this;
    }

    @Override
    public PublisherFactoryAbstract<T, M, S> withMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    @Override
    public PublisherFactoryAbstract<T, M, S> withMetrics(M metrics) {
        this.metrics = metrics;
        return this;
    }

    @Override
    public PublisherFactoryAbstract<T, M, S> withQueueSizeCounter(AtomicInteger queueSizeContainer) {
        this.queueSizeContainer = queueSizeContainer;
        return this;
    }

    @Override
    public PublisherFactoryAbstract<T, M, S> withEmitter(AtomicReference<MultiEmitter<? super T>> emitter) {
        this.emitter = emitter;
        return this;
    }

    @Override
    public Multi<Void> build() {
        if (sink == null || metrics == null || queueSizeContainer == null || emitter == null || mode == null) {
            throw new IllegalStateException("Dependencies not set!");
        }

        return switch (mode) {
            case PARALLEL -> Multi.createFrom().<T>emitter(em -> emitter.set(em), BackPressureStrategy.BUFFER)
                    .onOverflow().bufferUnconditionally()
                    .onItem().transformToUniAndMerge(this::publishMessage);
            case SEQUENTIAL -> Multi.createFrom().<T>emitter(em -> emitter.set(em), BackPressureStrategy.BUFFER)
                    .onOverflow().bufferUnconditionally()
                    .onItem().transformToUniAndConcatenate(this::publishMessage);
        };
    }

    protected abstract Uni<Void> publishMessage(T payload);
}
