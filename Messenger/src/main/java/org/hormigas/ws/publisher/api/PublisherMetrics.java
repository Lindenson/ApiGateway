package org.hormigas.ws.publisher.api;

public interface PublisherMetrics {
    void recordDone();
    void recordFailed();
    void setQueueSize(int queueSize);
}
