package org.hormigas.ws.backpressure.api;

public interface PublisherMetrics {
    void recordDone();
    void recordFailed();
    void setQueueSize(int queueSize);
}
