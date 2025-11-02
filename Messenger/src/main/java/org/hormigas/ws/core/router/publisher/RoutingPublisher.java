package org.hormigas.ws.core.router.publisher;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.backpressure.PublisherFactory;
import org.hormigas.ws.backpressure.PublisherMetrics;
import org.hormigas.ws.backpressure.PublisherWithBackPressure;
import org.hormigas.ws.backpressure.outgoing.OutgoingPublisherMetrics;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.core.router.PipelineRouter;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.feedback.events.OutgoingHealthEvent;
import org.hormigas.ws.feedback.provider.OutEventProvider;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hormigas.ws.backpressure.PublisherFactory.Mode.PARALLEL;


@Slf4j
@ApplicationScoped
public class RoutingPublisher implements PublisherWithBackPressure<Message> {

    private final AtomicReference<MultiEmitter<? super Message>> emitter = new AtomicReference<>();
    private OutgoingPublisherMetrics metrics;

    private final AtomicBoolean ready = new AtomicBoolean(Boolean.FALSE);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    PipelineRouter<Message> pipelineRouter;

    @Inject
    MessagesConfig messagesConfig;

    @Inject
    OutEventProvider<OutgoingHealthEvent> eventsProvider;

    @PostConstruct
    void init() {
        this.metrics = new OutgoingPublisherMetrics(meterRegistry, eventsProvider);

        PublisherFactory.PublisherFactories.<Message, PublisherMetrics, Uni<MessageContext<Message>>>getFactoryFor("outgoing")
                .withSink(pipelineRouter::route)
                .withMetrics(metrics)
                .withQueueSizeCounter(queueSize)
                .withEmitter(emitter)
                .withMode(PARALLEL)
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
        if (queueSize.incrementAndGet() > messagesConfig.outbox().sendingQueueSize()) {
            log.debug("Queue is full");
            queueSize.decrementAndGet();
            return true;
        }
        return false;
    }
}
