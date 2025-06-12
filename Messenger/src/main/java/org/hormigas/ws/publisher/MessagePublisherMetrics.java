package org.hormigas.ws.publisher;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.hormigas.ws.publisher.api.PublisherMetrics;

import java.util.concurrent.TimeUnit;


public class MessagePublisherMetrics implements PublisherMetrics {

    private final DistributionSummary queueSnapshot;
    private final Timer processingTimer;
    private final Counter published;
    private final Counter dropped;
    private final Counter failed;

    public MessagePublisherMetrics(MeterRegistry registry) {
        this.processingTimer = Timer.builder("message_processing_duration").register(registry);
        this.published = Counter.builder("messages_published_total").register(registry);
        this.dropped = Counter.builder("messages_dropped_total").register(registry);
        this.failed = Counter.builder("messages_failed_total").register(registry);
        this.queueSnapshot = DistributionSummary.builder("message_queue_size").register(registry);
    }

    @Override
    public void setQueueSize(int newSize) {
        queueSnapshot.record(newSize);
    }

    public void resetQueue() {
        queueSnapshot.record(0);
    }

    @Override
    public void recordDone() {
        published.increment();
    }

    public void recordDropped() {
        dropped.increment();
    }

    @Override
    public void recordFailed() {
        failed.increment();
    }

    public void recordProcessingTime(long nanos) {
        processingTimer.record(nanos, TimeUnit.NANOSECONDS);
    }
}
