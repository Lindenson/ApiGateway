package org.hormigas.ws.ports.channel.ws;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.backpressure.IncommingPublisherMetrics;
import org.hormigas.ws.backpressure.api.PublisherFactory;
import org.hormigas.ws.backpressure.api.PublisherMetrics;
import org.hormigas.ws.backpressure.api.PublisherWithBackPressure;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.core.router.MessageRouter;
import org.hormigas.ws.domain.MessagePayload;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
@ApplicationScoped
public class IncomingPublisher implements PublisherWithBackPressure<MessagePayload> {


    @Inject
    MessagesConfig messagesConfig;

    @Inject
    MessageRouter messageRouter;

    @Inject
    MeterRegistry meterRegistry;

    private IncommingPublisherMetrics metrics;

    private final AtomicReference<MultiEmitter<? super MessagePayload>> emitter = new AtomicReference<>();
    private final AtomicBoolean ready = new AtomicBoolean(Boolean.FALSE);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    @PostConstruct
    void init() {

        metrics = new IncommingPublisherMetrics(meterRegistry);

        PublisherFactory.PublisherFactories.<MessagePayload, PublisherMetrics, Uni<Boolean>, MessagePayload>getFactoryFor("incoming")
                .withProcessor(messageRouter::route)
                .withQueueSizeCounter(queueSize)
                .withEmitter(emitter)
                .withMetrics(metrics)
                .build()
                .subscribe().with(
                        ignored -> Log.debug("Publishing acknowledging!"),
                        failure -> {
                            metrics.resetQueue();
                            queueSize.set(0);
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
            Log.warn("Acknowledging dropped due to limit");
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
        if (queueSize.incrementAndGet() > messagesConfig.persistence().ackQueueSize()) {
            log.debug("Queue is full");
            queueSize.decrementAndGet();
            return true;
        }
        return false;
    }
}
