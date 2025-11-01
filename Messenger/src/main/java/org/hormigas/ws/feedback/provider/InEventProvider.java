package org.hormigas.ws.feedback.events;

public interface InEventProvider<T> {
    void fireIn(T event);
}
