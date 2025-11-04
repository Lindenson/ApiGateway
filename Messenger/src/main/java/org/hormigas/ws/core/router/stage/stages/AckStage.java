package org.hormigas.ws.core.router.stage.stages;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.domain.Message;

import static org.hormigas.ws.core.router.stage.StageStatus.SUCCESS;
import static org.hormigas.ws.domain.MessageType.CHAT_ACK;

@ApplicationScoped
@RequiredArgsConstructor
public class AckStage implements PipelineStage<RouterContext<Message>> {

    private final DeliveryStage deliveryStage;

    @Override
    public Uni<RouterContext<Message>> apply(RouterContext<Message> ctx) {
        if (ctx.getPersisted() == SUCCESS) {
            Message ackMessage = createAck(ctx.getPayload());
            RouterContext<Message> ackCtx = RouterContext.<Message>builder()
                    .payload(ackMessage)
                    .pipelineType(ctx.getPipelineType())
                    .build();
            return deliveryStage.apply(ackCtx)
                    .onItem().invoke(ackResult -> ctx.setAcknowledged(ackResult.getAcknowledged()))
                    .replaceWith(ctx);
        }
        return Uni.createFrom().item(ctx);
    }

    private Message createAck(Message original) {
        return Message.builder()
                .type(CHAT_ACK)
                .senderId("server")
                .recipientId(original.getSenderId())
                .correlationId(original.getMessageId())
                .serverTimestamp(System.currentTimeMillis())
                .build();
    }
}