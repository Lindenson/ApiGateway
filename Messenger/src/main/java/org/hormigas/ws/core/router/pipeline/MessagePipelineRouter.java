package org.hormigas.ws.core.router.pipeline;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.router.PipelineResolver;
import org.hormigas.ws.core.router.PipelineRouter;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.stage.stages.*;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageEnvelope;
import org.hormigas.ws.domain.MessageType;

import static org.hormigas.ws.core.router.stage.StageStatus.SUCCESS;
import static org.hormigas.ws.core.router.stage.StageStatus.UNKNOWN;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class MessagePipelineRouter implements PipelineRouter<Message> {

    private final PipelineResolver<Message, MessageType> PipelineResolver;
    private final OutboxStage outboxStage;
    private final DeliveryStage deliveryStage;
    private final CleanOutboxStage cleanOutboxStage;
    private final CleanCacheStage cleanCacheStage;
    private final CacheStage cacheStage;
    private final FinalStage finalStage;

    @Override
    public Uni<MessageEnvelope<Message>> route(Message message) {
        var pipeline = PipelineResolver.resolvePipeline(message);
        var context = RouterContext.<Message>builder()
                .cached(UNKNOWN)
                .persisted(UNKNOWN)
                .delivered(UNKNOWN)
                .done(false)
                .pipelineType(pipeline)
                .payload(message).build();

        log.debug("Routing message {} to a pipeline {}", message.getMessageId(), pipeline);

        var processed = switch (pipeline) {
            case PERSISTENT_OUT -> outboxStage.apply(context)
                    .onItem().transformToUni(deliveryStage::apply)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().transformToUni(finalStage::apply);

            case CACHED_OUT -> deliveryStage.apply(context)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().transformToUni(finalStage::apply);

            case DIRECT_OUT -> deliveryStage.apply(context)
                    .onItem().transformToUni(finalStage::apply);

            case PERSISTENT_ACK -> cleanOutboxStage.apply(context)
                    .onItem().transformToUni(cleanCacheStage::apply)
                    .onItem().transformToUni(finalStage::apply);

            case CACHED_ACK -> cleanCacheStage.apply(context)
                    .onItem().transformToUni(finalStage::apply);
        };

        return
                processed.onItem().invoke(this::logResult)
                        .onItem().transform(toBeMapped -> MessageEnvelope.<Message>builder()
                                .message(toBeMapped.getPayload())
                                .processed(toBeMapped.isDone())
                                .build());
    }

    private void logResult(RouterContext<Message> ctx) {
        String messageId = ctx.getPayload().getMessageId();

        if (ctx.hasError()) {
            log.error("Message {} failed: {}", messageId, ctx.getError().getMessage(), ctx.getError());
            return;
        }

        switch (ctx.getPipelineType()) {
            case PERSISTENT_OUT -> logPersistenceFlow(ctx, messageId);
            case CACHED_OUT -> logCachedFlow(ctx, messageId);
            case DIRECT_OUT -> logDelivery(ctx, messageId);
            case PERSISTENT_ACK -> logPersistenceCleanup(ctx, messageId);
            case CACHED_ACK -> logCacheCleanup(ctx, messageId);
        }

        if (!ctx.hasError()) {
            log.debug("Message {} processed successfully via {}", messageId, ctx.getPipelineType());
        }
    }

    private void logPersistenceFlow(RouterContext<Message> ctx, String messageId) {
        logPersistence(ctx, messageId);
        logCache(ctx, messageId);
        logDelivery(ctx, messageId);
    }

    private void logCachedFlow(RouterContext<Message> ctx, String messageId) {
        logCache(ctx, messageId);
        logDelivery(ctx, messageId);
    }

    private void logPersistenceCleanup(RouterContext<Message> ctx, String messageId) {
        logOutboxCleanup(ctx, messageId);
        logCacheCleanup(ctx, messageId);
    }

    private void logPersistence(RouterContext<Message> ctx, String messageId) {
        if (!ctx.getPersisted().equals(SUCCESS)) {
            log.warn("Message {} was not persisted", messageId);
        }
    }

    private void logCache(RouterContext<Message> ctx, String messageId) {
        if (!ctx.getCached().equals(SUCCESS)) {
            log.warn("Message {} was not cached", messageId);
        }
    }

    private void logDelivery(RouterContext<Message> ctx, String messageId) {
        if (!ctx.getDelivered().equals(SUCCESS)) {
            log.warn("Message {} was not delivered", messageId);
        }
    }

    private void logOutboxCleanup(RouterContext<Message> ctx, String messageId) {
        if (!ctx.getPersisted().equals(SUCCESS)) {
            log.warn("Message {} was not cleaned from outbox", messageId);
        }
    }

    private void logCacheCleanup(RouterContext<Message> ctx, String messageId) {
        if (!ctx.getCached().equals(SUCCESS)) {
            log.warn("Message {} was not cleaned from cache", messageId);
        }
    }
}
