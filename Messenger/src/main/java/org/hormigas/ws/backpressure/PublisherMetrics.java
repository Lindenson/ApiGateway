package org.hormigas.ws.backpressure;

public interface PublisherMetrics {
    void recordDone();
    void recordFailed();
    void setQueueSize(int queueSize);
    void recordDropped();
    void resetQueue();
}
