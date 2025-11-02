package org.hormigas.ws.feedback.regulator;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.feedback.events.OutgoingHealthEvent;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@ApplicationScoped
public class FeedbackRegulator {
    private long baseIntervalMs;
    private double adjustmentFactor;
    private double recoveryFactor;
    private final AtomicLong currentIntervalMs = new AtomicLong();

    @Inject
    MessagesConfig messagesConfig;

    @PostConstruct
    public void init() {
        adjustmentFactor = messagesConfig.feedback().adjustmentFactor();
        baseIntervalMs = messagesConfig.feedback().baseIntervalMs();
        recoveryFactor = messagesConfig.feedback().recoveryFactor();
        currentIntervalMs.set(baseIntervalMs);
    }

    public void onHealthEvent(@ObservesAsync OutgoingHealthEvent event) {
        if (event.droppedDetected()) {
            long newInterval = Math.max(100, (long) (currentIntervalMs.get() * adjustmentFactor));
            currentIntervalMs.set(newInterval);
            log.warn("⚠️ Drops detected! Adjusting outbox interval to {} ms", newInterval);
        } else {
            long newInterval = Math.min(baseIntervalMs, (long) (currentIntervalMs.get() * recoveryFactor));
            currentIntervalMs.set(newInterval);
            log.warn("✅ Stable. Restoring interval to {} ms", newInterval);
        }
    }

    public Duration getCurrentInterval() {
        return Duration.ofMillis(currentIntervalMs.get());
    }
}
