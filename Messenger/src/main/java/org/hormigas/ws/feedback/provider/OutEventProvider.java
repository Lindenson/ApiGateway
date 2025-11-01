package org.hormigas.ws.feedback.events;

public interface OutEventProvider<T> {
    void fireOut(T event);
}

