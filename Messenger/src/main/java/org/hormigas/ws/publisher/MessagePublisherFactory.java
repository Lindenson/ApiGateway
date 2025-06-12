package org.hormigas.ws.publisher;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.publisher.api.PublisherFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


public class MessagePublisherFactory implements PublisherFactory<Message, MessagePublisherMetrics, Uni<Boolean>> {
    private Function<Message, Uni<Boolean>> processor;
    private MessagePublisherMetrics metrics;
    private AtomicInteger queueSizeContainer;
    private AtomicReference<MultiEmitter<? super Message>> emitter;

    public MessagePublisherFactory withProcessor(Function<Message, Uni<Boolean>> processor) {
        this.processor = processor;
        return this;
    }

    @Override
    public MessagePublisherFactory withMetrics(MessagePublisherMetrics metrics) {
        this.metrics = metrics;
        return this;
    }

    @Override
    public MessagePublisherFactory withQueueSizeCounter(AtomicInteger queueSizeContainer) {
        this.queueSizeContainer = queueSizeContainer;
        return this;
    }

    @Override
    public MessagePublisherFactory withEmitter(AtomicReference<MultiEmitter<? super Message>> emitter) {
        this.emitter = emitter;
        return this;
    }

    @Override
    public Multi<Void> build() {
        if (processor == null || metrics == null || queueSizeContainer == null || emitter == null) {
            throw new IllegalStateException("Dependencies not set!");
        }

        return Multi.createFrom().<Message>emitter(em -> emitter.set(em), BackPressureStrategy.BUFFER)
                .onOverflow().bufferUnconditionally()
                .onItem().transformToUniAndMerge(msg -> {
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
                                Log.error("Failed to send message", failure);
                            })
                            .replaceWithVoid()
                            .eventually(() -> metrics.setQueueSize(queueSizeContainer.decrementAndGet()));
                });
    }
}
