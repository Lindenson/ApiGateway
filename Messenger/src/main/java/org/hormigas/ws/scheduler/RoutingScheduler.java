package org.hormigas.ws.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.config.MessengerConfig;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.core.router.publisher.RoutingBackpressurePublisher;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.feedback.Regulator;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@ApplicationScoped
public class RoutingScheduler {

    @Inject
    RoutingBackpressurePublisher publisher;

    @Inject
    Regulator regulator;

    @Inject
    OutboxManager<Message> outboxManager;

    @Inject
    MeterRegistry registry;

    @Inject
    MessengerConfig messengerConfig;


    private Counter pollCounter;
    private Counter skippedCounter;
    private Counter errorCounter;
    private Timer pollTimer;
    private final AtomicLong currentDelayMs = new AtomicLong(0);
    private final AtomicInteger lastBatchSize = new AtomicInteger(0);

    @PostConstruct
    void initMetrics() {
        pollCounter = Counter.builder("routing_scheduler_polls_total")
                .description("Total number of scheduler polls")
                .register(registry);

        skippedCounter = Counter.builder("routing_scheduler_skipped_total")
                .description("Number of skipped fetches due to non-empty queue")
                .register(registry);

        errorCounter = Counter.builder("routing_scheduler_errors_total")
                .description("Number of errors during polling")
                .register(registry);

        pollTimer = Timer.builder("routing_scheduler_poll_duration")
                .description("Duration of each polling operation")
                .register(registry);

        Gauge.builder("routing_scheduler_current_delay_ms", currentDelayMs, AtomicLong::get)
                .description("Current delay interval in milliseconds (regulated)")
                .register(registry);

        Gauge.builder("routing_scheduler_last_batch_size", lastBatchSize, AtomicInteger::get)
                .description("Number of messages processed in the last batch")
                .register(registry);
    }

    @Scheduled(every = "${processing.messages.outbound.polling-ms}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void pollOutbox() {
        long delayMs = regulator.getCurrentIntervalMs().toMillis();
        currentDelayMs.set(delayMs);
        pollCounter.increment();

        Uni.createFrom().voidItem()
                .onItem().delayIt().by(Duration.ofMillis(delayMs))
                .invoke(() -> log.debug("⏱ Polling outbox (interval={} ms)", delayMs))
                .call(this::timedFetchMessagesToProcess)
                .subscribe().with(
                        nothing -> {},
                        failure -> {
                            log.error("Error in scheduled outbox polling", failure);
                            errorCounter.increment();
                        }
                );
    }

    private Uni<Void> timedFetchMessagesToProcess() {
        return Uni.createFrom().item(System.nanoTime())
                .call(startTime -> fetchMessagesToProcess()
                        .eventually(() -> {
                            long durationNs = System.nanoTime() - startTime;
                            pollTimer.record(durationNs, java.util.concurrent.TimeUnit.NANOSECONDS);
                            return Uni.createFrom().voidItem();
                        })
                )
                .replaceWithVoid();
    }

    private Uni<Void> fetchMessagesToProcess() {
        Log.debug("Fetching messages for dispatch");
        if (publisher.queueIsNotEmpty()) {
            Log.warn("Queue is not empty. Skipping fetch.");
            skippedCounter.increment();
            return Uni.createFrom().voidItem();
        }

        return outboxManager.getFromOutboxBatch(messengerConfig.outbound().batchSize())
                .invoke(messages -> {
                    lastBatchSize.set(messages.size());
                    messages.forEach(publisher::publish);
                })
                .replaceWithVoid();
    }
}
