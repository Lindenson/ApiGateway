package org.hormigas.ws.core.router;

public interface PolicyResolver<T, D> {
    enum RoutePolicy {PERSISTENT_OUT, CACHED_OUT, DIRECT_OUT, CACHED_ACK, PERSISTENT_ACK}
    RoutePolicy resolvePolicy(T message);
}
