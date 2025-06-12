package org.hormigas.ws.websocket;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.publisher.AcknowledgePublisherMetrics;
import org.hormigas.ws.publisher.api.PublisherMetrics;
import org.hormigas.ws.publisher.api.PublisherWithBackPressure;
import org.hormigas.ws.service.api.MessagePersistence;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hormigas.ws.publisher.api.PublisherFactory.PublisherFactories;

@Slf4j
@ApplicationScoped
public class AcknowledgmentPublisher implements PublisherWithBackPressure<String> {


    @Inject
    MessagesConfig messagesConfig;

    @Inject
    MessagePersistence messagePersistence;

    @Inject
    MeterRegistry meterRegistry;

    private AcknowledgePublisherMetrics metrics;

    private final AtomicReference<MultiEmitter<? super String>> emitter = new AtomicReference<>();
    private final AtomicBoolean ready = new AtomicBoolean(Boolean.FALSE);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    @PostConstruct
    void init() {

        metrics = new AcknowledgePublisherMetrics(meterRegistry);

        PublisherFactories.<String, PublisherMetrics, Uni<Long>>getFactoryFor("acknowledge")
                .withProcessor(messagePersistence::removeAcknowledgedMessage)
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
    public void publish(String msg) {
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
        if (queueSize.incrementAndGet() > messagesConfig.persistence().queueSize()) {
            log.debug("Queue is full");
            queueSize.decrementAndGet();
            return true;
        }
        return false;
    }
}
