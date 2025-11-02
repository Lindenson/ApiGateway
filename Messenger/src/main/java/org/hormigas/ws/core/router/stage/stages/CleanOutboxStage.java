package org.hormigas.ws.core.router.stage.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.domain.Message;

@ApplicationScoped
@RequiredArgsConstructor
public class CleanOutboxStage implements PipelineStage<MessageContext<Message>> {

    private final OutboxManager<Message> outboxManager;

    @Override
    public Uni<MessageContext<Message>> apply(MessageContext<Message> ctx) {
            return outboxManager.removeFromOutbox(ctx.getPayload())
                    .onItem().invoke(() -> ctx.setPersisted(true))
                    .replaceWith(ctx)
                    .onFailure().invoke(ctx::setError)
                    .onFailure().recoverWithItem(ctx);
    }
}

