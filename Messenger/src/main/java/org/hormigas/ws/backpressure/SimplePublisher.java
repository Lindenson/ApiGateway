package org.hormigas.ws.backpressure.api;

public interface SimplePublisher<T> {
    void publish(T msg);
}
