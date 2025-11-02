package org.hormigas.ws.backpressure;

public interface SimplePublisher<T> {
    void publish(T msg);
}
