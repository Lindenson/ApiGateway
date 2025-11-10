package org.hormigas.ws.ports.channel.presense.strategy;

import java.util.Set;
import java.util.function.Consumer;

public interface CleanupStrategy<T> {
    void clean(T tested, Set<T> opened, Consumer<T> deregister);
}