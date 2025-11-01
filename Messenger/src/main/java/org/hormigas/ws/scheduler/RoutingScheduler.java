package org.hormigas.ws.core.scheduler;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.core.router.publisher.RoutingPublisher;
import org.hormigas.ws.feedback.regulator.FeedbackRegulator;

import java.time.Duration;


@Slf4j
@ApplicationScoped
public class RoutingScheduler {

    @Inject
    FeedbackRegulator regulator;

    @Inject
    OutboxManager outboxManager;

    @Inject
    RoutingPublisher publisher;

    private final int BATCH_SIZE = 1000;

    @Scheduled(every = "0.5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void pollOutbox() {
        long delayMs = regulator.getCurrentInterval().toMillis();

        Uni.createFrom().voidItem()
                .onItem().delayIt().by(Duration.ofMillis(delayMs))
                .invoke(() -> log.debug("⏱ Polling outbox (interval={} ms)", delayMs))
                .call(this::fetchMessagesToProcess)
                .subscribe().with(
                        nothing -> {
                        },
                        failure -> log.error("Error in scheduled outbox polling", failure)
                );
    }


    private Uni<Void> fetchMessagesToProcess() {
        Log.debug("Fetching messages for dispatch");
        if (publisher.queueIsNotEmpty()) {
            Log.warn("Queue is not empty. We don't fetch messages");
            return Uni.createFrom().voidItem();
        }
        return outboxManager.getFromOutboxBatch(BATCH_SIZE)
                .chain(messages -> Uni.createFrom().item(messages)
                        .invoke(msgs -> msgs.forEach(publisher::publish))).replaceWithVoid();
    }
}
