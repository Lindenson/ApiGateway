package org.hormigas.ws.backpressure.policy;

import java.util.Queue;

public class EmitterDropPolicy<T> implements EmitterPolicy<T> {

    private final Queue<T> queue;

    EmitterDropPolicy(Queue<T> queue) {
        this.queue = queue;
    }

    @Override
    public void apply() {
        queue.poll();
    }
}
