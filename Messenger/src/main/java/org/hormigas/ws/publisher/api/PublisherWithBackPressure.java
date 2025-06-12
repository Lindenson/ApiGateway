package org.hormigas.ws.publisher.api;

public interface PublisherWithBackPressure<T> extends SimplePublisher<T>{
    boolean queueIsNotEmpty();
    boolean queueIsFull();
}
