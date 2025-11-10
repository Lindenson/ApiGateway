package org.hormigas.ws.backpressure.metrics;


public interface PublisherMetrics {
    void recordDone();
    void recordDropped();
    void recordFailed();
    void updateQueueSize(int queueSize);
    void resetQueueSize();
    void recordProcessingTime(long nanos);
}
