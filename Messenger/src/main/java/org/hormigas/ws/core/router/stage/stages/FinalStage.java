package org.hormigas.ws.core.router.stage.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.domain.Message;


@ApplicationScoped
@RequiredArgsConstructor
public class FinalStage implements PipelineStage<MessageContext<Message>> {

    @Override
    public Uni<MessageContext<Message>> apply(MessageContext<Message> ctx) {
        return Uni.createFrom().item(() -> {
            boolean done = ctx.isDelivered() && ctx.isPersisted() && ctx.isCached();
            ctx.setDone(done);
            return ctx;
        });
    }
}
