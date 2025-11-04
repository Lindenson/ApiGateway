package org.hormigas.ws.core.router.pipeline;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.router.InboundRouter;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.logger.RouterLogger;
import org.hormigas.ws.core.router.logger.inout.InboundRouterLogger;
import org.hormigas.ws.core.router.stage.stages.*;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageEnvelope;
import org.hormigas.ws.domain.MessageType;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class MessageInboundRouter implements InboundRouter<Message> {

    private final org.hormigas.ws.core.router.PipelineResolver<Message, MessageType> pipelineResolver;
    private final OutboxStage outboxStage;
    private final DeliveryStage deliveryStage;
    private final CleanOutboxStage cleanOutboxStage;
    private final CleanCacheStage cleanCacheStage;
    private final CacheStage cacheStage;
    private final FinalStage finalStage;

    private final RouterLogger<Message> logger = new InboundRouterLogger();

    @Override
    public Uni<MessageEnvelope<Message>> route(Message message) {
        var pipeline = pipelineResolver.resolvePipeline(message);
        var context = RouterContext.<Message>builder()
                .pipelineType(pipeline)
                .payload(message).build();

        logger.logRoutingStart(message, pipeline);

        Uni<RouterContext<Message>> processed = switch (pipeline) {
            case INBOUND_PERSISTENT -> outboxStage.apply(context)
                    .onItem().transformToUni(deliveryStage::apply)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().transformToUni(finalStage::apply);

            case INBOUND_CACHED -> deliveryStage.apply(context)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().transformToUni(finalStage::apply);

            case ACK_PERSISTENT -> cleanOutboxStage.apply(context)
                    .onItem().transformToUni(cleanCacheStage::apply)
                    .onItem().transformToUni(finalStage::apply);

            case ACK_CACHED -> cleanCacheStage.apply(context)
                    .onItem().transformToUni(finalStage::apply);

            case SKIP -> finalStage.apply(context);

            default -> Uni.createFrom().failure(
                    new IllegalStateException("Unhandled pipeline: " + pipeline)
            );
        };

        return processed.onItem().invoke(logger::logResult)
                .onItem().transform(toBeMapped -> MessageEnvelope.<Message>builder()
                        .message(toBeMapped.getPayload())
                        .processed(toBeMapped.isDone())
                        .build());
    }
}
