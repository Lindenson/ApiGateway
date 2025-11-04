package org.hormigas.ws.ports.channel.ws.publisher;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.backpressure.incomming.IncommingPublisherMetrics;
import org.hormigas.ws.backpressure.PublisherFactory;
import org.hormigas.ws.backpressure.PublisherMetrics;
import org.hormigas.ws.backpressure.PublisherWithBackPressure;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.core.router.InboundRouter;
import org.hormigas.ws.core.router.PipelineRouter;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageEnvelope;
import org.hormigas.ws.feedback.provider.InEventProvider;
import org.hormigas.ws.feedback.events.IncomingHealthEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hormigas.ws.backpressure.PublisherFactory.Mode.PARALLEL;


@Slf4j
@ApplicationScoped
public class IncomingPublisher implements PublisherWithBackPressure<Message> {


    @Inject
    MessagesConfig messagesConfig;

    @Inject
    InboundRouter<Message> pipelineRouter;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    InEventProvider<IncomingHealthEvent> eventProvider;

    private IncommingPublisherMetrics metrics;

    private final AtomicReference<MultiEmitter<? super Message>> emitter = new AtomicReference<>();
    private final AtomicBoolean ready = new AtomicBoolean(Boolean.FALSE);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    @PostConstruct
    void init() {

        metrics = new IncommingPublisherMetrics(meterRegistry, eventProvider);

        PublisherFactory.PublisherFactories.<Message, PublisherMetrics, Uni<MessageEnvelope<Message>>>getFactoryFor("incoming")
                .withSink(pipelineRouter::route)
                .withQueueSizeCounter(queueSize)
                .withEmitter(emitter)
                .withMetrics(metrics)
                .withMode(PARALLEL)
                .build()
                .subscribe().with(
                        ignored -> Log.debug("Publishing incoming messages!"),
                        failure -> {
                            metrics.resetQueue();
                            queueSize.set(0);
                            Log.error("Incoming publisher terminated unexpectedly", failure);
                        }
                );
        ready.set(true);
    }

    @Override
    public void publish(Message msg) {
        if (!ready.get()) {
            Log.warn("Incoming publisher not initialized");
            return;
        }
        if (queueIsFull()) {
            metrics.recordDropped();
            Log.warn("Incoming message dropped due to limit");
            return;
        }
        Log.debug("Incoming message was published");
        emitter.get().emit(msg);
    }

    @Override
    public boolean queueIsNotEmpty() {
        if (queueSize.get() > 0) {
            log.debug("Incoming message queue is not empty");
            return true;
        }
        return false;
    }

    @Override
    public boolean queueIsFull() {
        metrics.setQueueSize(queueSize.get());
        if (queueSize.incrementAndGet() > messagesConfig.outbox().ackQueueSize()) {
            log.debug("Incoming queue is full");
            queueSize.decrementAndGet();
            return true;
        }
        return false;
    }
}
