package org.hormigas.ws.core.publishers;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.backpressure.OutgoingPublisherMetrics;
import org.hormigas.ws.backpressure.api.PublisherFactory;
import org.hormigas.ws.backpressure.api.PublisherMetrics;
import org.hormigas.ws.backpressure.api.PublisherWithBackPressure;
import org.hormigas.ws.core.router.MessageRouter;
import org.hormigas.ws.domain.MessagePayload;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
@ApplicationScoped
public class RoutingPublisher implements PublisherWithBackPressure<MessagePayload> {

    private final AtomicReference<MultiEmitter<? super MessagePayload>> emitter = new AtomicReference<>();
    private OutgoingPublisherMetrics metrics;

    private final AtomicBoolean ready = new AtomicBoolean(Boolean.FALSE);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    MessageRouter messageRouter;

    @Inject
    MessagesConfig messagesConfig;

    @PostConstruct
    void init() {
        this.metrics = new OutgoingPublisherMetrics(meterRegistry);

        PublisherFactory.PublisherFactories.<MessagePayload, PublisherMetrics, Uni<Boolean>, MessagePayload>getFactoryFor("outgoing")
                .withProcessor(messageRouter::route)
                .withMetrics(metrics)
                .withQueueSizeCounter(queueSize)
                .withEmitter(emitter)
                .build()
                .subscribe().with(
                        ignored -> Log.debug("Publishing messages!"),
                        failure -> {
                            queueSize.set(0);
                            metrics.resetQueue();
                            Log.error("Processor terminated unexpectedly", failure);
                        }
                );
        ready.set(true);
    }

    @Override
    public void publish(MessagePayload msg) {
        if (!ready.get()) {
            Log.warn("Not initialized");
            return;
        }
        if (queueIsFull()) {
            metrics.recordDropped();
            Log.warn("Message dropped due to limit");
            return;
        }
        Log.debug("Message was published");
        emitter.get().emit(msg);
    }

    @Override
    public boolean queueIsNotEmpty() {
        if (queueSize.get() > 0) {
            log.debug("Queue is not empty");
            return true;
        }
        return false;
    }

    @Override
    public boolean queueIsFull() {
        metrics.setQueueSize(queueSize.get());
        if (queueSize.incrementAndGet() > messagesConfig.persistence().sendingQueueSize()) {
            log.debug("Queue is full");
            queueSize.decrementAndGet();
            return true;
        }
        return false;
    }
}
