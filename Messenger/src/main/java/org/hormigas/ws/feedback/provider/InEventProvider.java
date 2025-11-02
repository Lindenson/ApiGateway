package org.hormigas.ws.feedback.provider;

public interface InEventProvider<T> {
    void fireIn(T event);
}
