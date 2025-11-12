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
import org.hormigas.ws.core.poller.AsyncBatchPoller;
import org.hormigas.ws.ports.outbox.OutboxManager;
import org.hormigas.ws.core.router.publisher.RoutingBackpressurePublisher;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.core.feedback.Regulator;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@ApplicationScoped
public class RoutingScheduler {

    @Inject
    AsyncBatchPoller asyncBatchPoller;

    @Scheduled(every = "${processing.messages.outbound.polling-ms}",
    concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void pollOutbox() {
        asyncBatchPoller.poll();
    }
}
