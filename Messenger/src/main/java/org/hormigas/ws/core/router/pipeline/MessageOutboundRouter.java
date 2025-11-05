package org.hormigas.ws.core.router.pipeline;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.router.OutboundRouter;
import org.hormigas.ws.core.router.PipelineResolver;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.logger.RouterLogger;
import org.hormigas.ws.core.router.logger.inout.OutboundRouterLogger;
import org.hormigas.ws.core.router.stage.stages.CacheStage;
import org.hormigas.ws.core.router.stage.stages.DeliveryStage;
import org.hormigas.ws.core.router.stage.stages.FinalStage;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageEnvelope;
import org.hormigas.ws.domain.MessageType;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class MessageOutboundRouter implements OutboundRouter<Message> {

    private final PipelineResolver<Message, MessageType> pipelineResolver;
    private final DeliveryStage deliveryStage;
    private final CacheStage cacheStage;
    private final FinalStage finalStage;

    private final RouterLogger<Message> logger = new OutboundRouterLogger();

    @Override
    public Uni<MessageEnvelope<Message>> routeOut(Message message) {
        var pipeline = pipelineResolver.resolvePipeline(message);
        var context = RouterContext.<Message>builder()
                .pipelineType(pipeline)
                .payload(message).build();

        logger.logRoutingStart(message, pipeline);

        Uni<RouterContext<Message>> processed = switch (pipeline) {
            case OUTBOUND_CACHED -> deliveryStage.apply(context)
                    .onItem().transformToUni(cacheStage::apply)
                    .onItem().transformToUni(finalStage::apply);

            case OUTBOUND_DIRECT -> deliveryStage.apply(context)
                    .onItem().transformToUni(finalStage::apply);

            case SKIP -> finalStage.apply(context);

            default -> Uni.createFrom().failure(
                    new IllegalStateException("Unhandled pipeline: " + pipeline)
            );
        };

        return processed.onItem().invoke(logger::logResult).onItem().transform(ctx ->
                MessageEnvelope.<Message>builder()
                        .message(ctx.getPayload())
                        .processed(ctx.isDone())
                        .build());
    }
}
