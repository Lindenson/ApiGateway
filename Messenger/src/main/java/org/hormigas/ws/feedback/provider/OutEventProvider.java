package org.hormigas.ws.feedback.provider;

public interface OutEventProvider<T> {
    void fireOut(T event);
}

