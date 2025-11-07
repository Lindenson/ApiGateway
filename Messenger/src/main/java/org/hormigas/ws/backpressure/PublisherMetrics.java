package org.hormigas.ws.backpressure;

public interface PublisherMetrics {
    void recordDone();
    void recordDropped();
    void recordFailed();
    void updateQueueSize(int queueSize);
    void resetQueueSize();
}
