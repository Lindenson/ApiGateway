package org.hormigas.ws.publisher.api;

public interface SimplePublisher<T> {
    void publish(T msg);
}
