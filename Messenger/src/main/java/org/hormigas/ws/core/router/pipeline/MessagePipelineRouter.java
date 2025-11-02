package org.hormigas.ws.core.router.pipeline;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.router.MessageRouter;
import org.hormigas.ws.core.router.PolicyResolver;
import org.hormigas.ws.core.router.context.MessageContext;
import org.hormigas.ws.core.router.stage.messaging.CacheStage;
import org.hormigas.ws.core.router.stage.messaging.CleanCacheStage;
import org.hormigas.ws.core.router.stage.messaging.DeliveryStage;
import org.hormigas.ws.core.router.stage.messaging.OutboxStage;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class MessageRouterPipeline implements MessageRouter<Message> {

    private final PolicyResolver<Message, MessageType> policyResolver;
    private final OutboxStage outboxStage;
    private final DeliveryStage deliveryStage;
    private final CleanCacheStage cleanCacheStage;
    private final CacheStage cacheStage;

    @Override
    public Uni<MessageContext<Message>> route(Message message) {
        var policy = policyResolver.resolvePolicy(message);
        var context = MessageContext.<Message>builder().payload(message).build();

        log.debug("Routing message {} with policy {}", message.getMessageId(), policy);

        return switch (policy) {
            case PERSISTENT_OUT -> outboxStage.apply(context)
                    .onItem().transformToUni(deliveryStage::apply)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().invoke(this::logResult);

            case CACHED_OUT -> deliveryStage.apply(context)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().invoke(this::logResult);

            case DIRECT_OUT -> deliveryStage.apply(context)
                    .onItem().invoke(this::logResult);

            case PERSISTENT_ACK -> outboxStage.apply(context);

            case CACHED_ACK -> deliveryStage.apply(context);
        };
    }

    private void logResult(MessageContext<Message> ctx) {
        if (ctx.hasError()) {
            log.error("Message {} failed: {}", ctx.getPayload().getMessageId(), ctx.getError().getMessage());
        } else if (ctx.isDelivered()) {
            log.info("Message {} successfully delivered", ctx.getPayload().getMessageId());
        } else {
            log.warn("Message {} not delivered (skipped or offline)", ctx.getPayload().getMessageId());
        }
    }
}
