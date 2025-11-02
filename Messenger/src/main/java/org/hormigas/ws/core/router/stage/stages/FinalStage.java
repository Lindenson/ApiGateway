package org.hormigas.ws.core.router.stage.stages;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.core.router.PipelineResolver;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.domain.Message;


@ApplicationScoped
@RequiredArgsConstructor
public class FinalStage implements PipelineStage<MessageContext<Message>> {

    @Override
    public Uni<MessageContext<Message>> apply(MessageContext<Message> ctx) {
        PipelineResolver.PipelineType pipelineType = ctx.getPipelineType();

        return Uni.createFrom().item(() -> {

            boolean done = switch (pipelineType) {
                case PERSISTENT_OUT, PERSISTENT_ACK -> ctx.isPersisted();
                case CACHED_OUT, DIRECT_OUT -> ctx.isDelivered();
                case CACHED_ACK -> ctx.isCached();
            };

            ctx.setDone(done);
            return ctx;
        });
    }
}
