package org.hormigas.ws.core.router.stage.stages;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.PipelineResolver;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;

import static org.hormigas.ws.core.router.stage.StageStatus.FAILED;
import static org.hormigas.ws.core.router.stage.StageStatus.SUCCESS;


@ApplicationScoped
@RequiredArgsConstructor
public class FinalStage implements PipelineStage<RouterContext<Message>> {

    @Override
    public Uni<RouterContext<Message>> apply(RouterContext<Message> ctx) {
        PipelineResolver.PipelineType pipelineType = ctx.getPipelineType();

        return Uni.createFrom().item(() -> {

            var done = switch (pipelineType) {
                case PERSISTENT_OUT, CACHED_OUT, DIRECT_OUT -> ctx.getDelivered().equals(SUCCESS);
                case PERSISTENT_ACK -> ctx.getPersisted().equals(SUCCESS);
                case CACHED_ACK -> ctx.getCached().equals(SUCCESS);
            };

            ctx.setDone(done);
            return ctx;
        });
    }
}
