package org.hormigas.ws.core.router.stage.stages;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.core.idempotency.IdempotencyManager;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;

import static org.hormigas.ws.core.router.stage.StageStatus.SUCCESS;

@ApplicationScoped
@RequiredArgsConstructor
public class CacheStage implements PipelineStage<RouterContext<Message>> {

    private final IdempotencyManager<Message> manager;

    @Override
    public Uni<RouterContext<Message>> apply(RouterContext<Message> ctx) {
        // don't save in idempotent storage if not delivered
        if (!ctx.getDelivered().equals(SUCCESS) ) return Uni.createFrom().item(ctx);

        return manager.addMessage(ctx.getPayload())
                .onItem().invoke(() -> ctx.setCached(SUCCESS))
                .replaceWith(ctx)
                .onFailure().invoke(ctx::setError)
                .onFailure().recoverWithItem(ctx);
    }
}