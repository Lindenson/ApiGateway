package org.hormigas.ws.backpressure;

public interface PublisherWithBackPressure<T> extends SimplePublisher<T> {
    boolean queueIsNotEmpty();
    boolean queueIsFull();
}
