package org.hormigas.ws.core.context;

import lombok.Builder;
import lombok.Data;
import org.hormigas.ws.core.router.PipelineResolver;

@Data
@Builder
public class MessageContext<T> {
    private final T payload;
    private boolean delivered;
    private boolean persisted;
    private boolean cached;
    private boolean done;
    private PipelineResolver.PipelineType pipelineType;
    private Throwable error;

    public boolean hasError() {
        return error != null;
    }
}
