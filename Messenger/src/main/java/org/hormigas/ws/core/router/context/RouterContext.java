package org.hormigas.ws.core.router.context;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.hormigas.ws.core.router.PipelineResolver;
import org.hormigas.ws.core.router.stage.StageStatus;

import static org.hormigas.ws.core.router.stage.StageStatus.FAILED;
import static org.hormigas.ws.core.router.stage.StageStatus.UNKNOWN;

@Data
@Builder
public class RouterContext<T> {
    @Getter
    private final T payload;
    @Builder.Default
    private StageStatus delivered = UNKNOWN;
    @Builder.Default
    private StageStatus persisted = UNKNOWN;
    @Builder.Default
    private StageStatus cached = UNKNOWN;
    @Builder.Default
    private StageStatus acknowledged = UNKNOWN;
    @Builder.Default
    private boolean done = false;

    private PipelineResolver.PipelineType pipelineType;
    private Throwable error;

    public boolean hasError() {
        return error != null;
    }
}