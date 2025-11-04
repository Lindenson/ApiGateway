package org.hormigas.ws.core.router.logger.inout;

import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.logger.RouterLogger;
import org.hormigas.ws.domain.Message;

import static org.hormigas.ws.core.router.stage.StageStatus.SUCCESS;

@Slf4j
public class OutboundRouterLogger implements RouterLogger<Message> {

    @Override
    public void logRoutingStart(Message message, Object pipelineType) {
        log.debug("Routing outbound message {} to pipeline {}", message.getMessageId(), pipelineType);
    }

    @Override
    public void logResult(RouterContext<Message> ctx) {
        String messageId = ctx.getPayload().getMessageId();

        if (ctx.hasError()) {
            log.error("Outbound message {} failed: {}", messageId, ctx.getError().getMessage(), ctx.getError());
            return;
        }

        switch (ctx.getPipelineType()) {
            case OUTBOUND_CACHED -> {
                logStage(ctx.getCached(), messageId, "cached");
                logStage(ctx.getDelivered(), messageId, "delivered");
            }
            case OUTBOUND_DIRECT -> logStage(ctx.getDelivered(), messageId, "delivered");
        }

        log.debug("Outbound message {} processed successfully via {}", messageId, ctx.getPipelineType());
    }

    private void logStage(Enum<?> status, String messageId, String operation) {
        if (!SUCCESS.equals(status)) {
            log.warn("Outbound message {} was not {}", messageId, operation);
        }
    }
}
