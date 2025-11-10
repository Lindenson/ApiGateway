package org.hormigas.ws.backpressure.publisher;

import io.smallrye.mutiny.Uni;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.backpressure.metrics.PublisherMetrics;
import org.hormigas.ws.domain.MessageEnvelope;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor
public abstract class PublisherTemplate<T, M extends PublisherMetrics> {
    private final Function<T, Uni<MessageEnvelope<T>>> sink;
    private final M metrics;
    private final AtomicInteger queueSizeContainer;
    public abstract Uni<Void> publishMessage(T payload);
}
