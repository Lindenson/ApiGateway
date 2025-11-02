package org.hormigas.ws.core.router.pipeline;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.core.router.PipelineResolver;
import org.hormigas.ws.core.router.PipelineRouter;
import org.hormigas.ws.core.router.stage.stages.*;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;

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
    public Uni<MessageContext<Message>> route(Message message) {
        var pipeline = PipelineResolver.resolvePipeline(message);
        var context = MessageContext.<Message>builder()
                .pipelineType(pipeline)
                .payload(message).build();

        log.debug("Routing message {} to a pipeline {}", message.getMessageId(), pipeline);

        return switch (pipeline) {
            case PERSISTENT_OUT -> outboxStage.apply(context)
                    .onItem().transformToUni(deliveryStage::apply)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().transformToUni(finalStage::apply)
                    .onItem().invoke(this::logResult);

            case CACHED_OUT -> deliveryStage.apply(context)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().transformToUni(finalStage::apply)
                    .onItem().invoke(this::logResult);

            case DIRECT_OUT -> deliveryStage.apply(context)
                    .onItem().transformToUni(finalStage::apply)
                    .onItem().invoke(this::logResult);

            case PERSISTENT_ACK -> cleanOutboxStage.apply(context)
                    .onItem().transformToUni(cleanCacheStage::apply)
                    .onItem().transformToUni(finalStage::apply)
                    .onItem().invoke(this::logResult);

            case CACHED_ACK -> cleanCacheStage.apply(context)
                    .onItem().invoke(this::logResult);
        };
    }

    private void logResult(MessageContext<Message> ctx) {
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

    private void logPersistenceFlow(MessageContext<Message> ctx, String messageId) {
        logPersistence(ctx, messageId);
        logCache(ctx, messageId);
        logDelivery(ctx, messageId);
    }

    private void logCachedFlow(MessageContext<Message> ctx, String messageId) {
        logCache(ctx, messageId);
        logDelivery(ctx, messageId);
    }

    private void logPersistenceCleanup(MessageContext<Message> ctx, String messageId) {
        logOutboxCleanup(ctx, messageId);
        logCacheCleanup(ctx, messageId);
    }

    private void logPersistence(MessageContext<Message> ctx, String messageId) {
        if (!ctx.isPersisted()) {
            log.warn("Message {} was not persisted", messageId);
        }
    }

    private void logCache(MessageContext<Message> ctx, String messageId) {
        if (!ctx.isCached()) {
            log.warn("Message {} was not cached", messageId);
        }
    }

    private void logDelivery(MessageContext<Message> ctx, String messageId) {
        if (!ctx.isDelivered()) {
            log.warn("Message {} was not delivered", messageId);
        }
    }

    private void logOutboxCleanup(MessageContext<Message> ctx, String messageId) {
        if (!ctx.isPersisted()) {
            log.warn("Message {} was not cleaned from outbox", messageId);
        }
    }

    private void logCacheCleanup(MessageContext<Message> ctx, String messageId) {
        if (!ctx.isCached()) {
            log.warn("Message {} was not cleaned from cache", messageId);
        }
    }
}
