package org.hormigas.ws.core.router;

public interface PipelineResolver<T, D> {
    enum PipelineType {PERSISTENT_OUT, CACHED_OUT, DIRECT_OUT, CACHED_ACK, PERSISTENT_ACK}
    PipelineType resolvePipeline(T message);
}
