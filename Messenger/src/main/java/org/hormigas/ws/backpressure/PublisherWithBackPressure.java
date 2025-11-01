package org.hormigas.ws.backpressure.api;

public interface PublisherWithBackPressure<T> extends SimplePublisher<T> {
    boolean queueIsNotEmpty();
    boolean queueIsFull();
}
