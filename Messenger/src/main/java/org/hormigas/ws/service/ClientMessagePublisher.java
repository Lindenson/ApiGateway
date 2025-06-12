package org.hormigas.ws.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.publisher.MessagePublisherMetrics;
import org.hormigas.ws.publisher.api.PublisherMetrics;
import org.hormigas.ws.publisher.api.PublisherWithBackPressure;
import org.hormigas.ws.websocket.api.MessengerWebSocket;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hormigas.ws.publisher.api.PublisherFactory.PublisherFactories;


@Slf4j
@ApplicationScoped
public class ClientMessagePublisher implements PublisherWithBackPressure<Message> {

    private final AtomicReference<MultiEmitter<? super Message>> emitter = new AtomicReference<>();
    private MessagePublisherMetrics metrics;

    private final AtomicBoolean ready = new AtomicBoolean(Boolean.FALSE);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    MessengerWebSocket<Message> messengerWebSocket;

    @Inject
    MessagesConfig messagesConfig;

    @PostConstruct
    void init() {
        this.metrics = new MessagePublisherMetrics(meterRegistry);

        PublisherFactories.<Message, PublisherMetrics, Uni<Boolean>>getFactoryFor("message")
                .withProcessor(messengerWebSocket::sendToClient)
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
    public void publish(Message msg) {
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
        if (queueSize.incrementAndGet() > messagesConfig.persistence().queueSize()) {
            log.debug("Queue is full");
            queueSize.decrementAndGet();
            return true;
        }
        return false;
    }
}
