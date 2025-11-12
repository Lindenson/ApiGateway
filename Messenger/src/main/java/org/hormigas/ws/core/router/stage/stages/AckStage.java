package org.hormigas.ws.core.router.stage.stages;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.generator.IdGenerator;

import static org.hormigas.ws.domain.stage.StageStatus.SUCCESS;
import static org.hormigas.ws.domain.message.MessageType.CHAT_ACK;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AckStage implements PipelineStage<RouterContext<Message>> {

    private final DeliveryStage deliveryStage;
    private final IdGenerator idGenerator;

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
                    .invoke(ackResult -> {

                        if (ctx.getPayload().getPayload().getBody().equals("R")) {
                            log.error("ACKED1!! {}", ctx.getPayload());
                            log.error("ACKED2!! {}", ackCtx.getPayload());
                        }

                    })
                    .replaceWith(ctx);
        }
        return Uni.createFrom().item(ctx);
    }

    private Message createAck(Message original) {
        return Message.builder()
                .messageId(idGenerator.generateId())
                .correlationId(original.getCorrelationId())
                .type(CHAT_ACK)
                .senderId("server")
                .recipientId(original.getSenderId())
                .serverTimestamp(System.currentTimeMillis())
                .build();
    }
}