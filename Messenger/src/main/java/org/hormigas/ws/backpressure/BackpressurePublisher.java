package org.hormigas.ws.backpressure;

public interface BackpressurePublisher<T> extends SimplePublisher<T> {
    boolean queueIsNotEmpty();
    boolean queueIsFull();
}
