package org.hormigas.ws.core.router.stage.stages;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.core.idempotency.IdempotencyManager;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.domain.Message;

@ApplicationScoped
@RequiredArgsConstructor
public class CacheStage implements PipelineStage<MessageContext<Message>> {

    private final IdempotencyManager<Message> manager;

    @Override
    public Uni<MessageContext<Message>> apply(MessageContext<Message> ctx) {
        // don't cache if not delivered
        if (!ctx.isDelivered()) return Uni.createFrom().item(ctx);
        return manager.addMessage(ctx.getPayload())
                .onItem().invoke(() -> ctx.setCached(true))
                .replaceWith(ctx)
                .onFailure().invoke(ctx::setError)
                .onFailure().recoverWithItem(ctx);
    }
}