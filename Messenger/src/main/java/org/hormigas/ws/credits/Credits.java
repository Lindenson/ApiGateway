package org.hormigas.ws.credits;

public interface Credits {
    boolean tryConsume();
    double getCurrentCredits();
    void reset();
}
