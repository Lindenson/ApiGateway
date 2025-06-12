package org.hormigas.ws.publisher;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.hormigas.ws.publisher.api.PublisherMetrics;

public class AcknowledgePublisherMetrics implements PublisherMetrics {

    private final DistributionSummary queueSnapshot;
    private final Counter acknowledged;
    private final Counter dropped;
    private final Counter failed;

    public AcknowledgePublisherMetrics(MeterRegistry registry) {
        this.acknowledged = Counter.builder("acknowledged_published_total").register(registry);
        this.dropped = Counter.builder("acknowledged_dropped_total").register(registry);
        this.failed = Counter.builder("acknowledged_failed_total").register(registry);
        this.queueSnapshot = DistributionSummary.builder("acknowledged_queue_size").register(registry);
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
        acknowledged.increment();
    }

    public void recordDropped() {
        dropped.increment();
    }

    @Override
    public void recordFailed() {
        failed.increment();
    }
}
